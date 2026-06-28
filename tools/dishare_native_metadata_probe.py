#!/usr/bin/env python3
"""Host-side probe for DiShare native App Change metadata.

This script is intentionally not product code. It prepares and runs a controlled
experiment for the hypothesis that DiShare's native App Change row can be made
to show Russian app names/icons by controlling the cloud videoList response.
Default commands are read-only. Commands that change the car proxy or clear
DiShare data require explicit subcommands/flags.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import textwrap
import time
import zipfile
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = REPO_ROOT / "captures" / "dishare-native-metadata"
DEFAULT_SERIAL = "127.0.0.1:5555"
VIDEO_LIST_HOST = "video-cn.denzacloud.com"
VIDEO_LIST_PATH = "/apiService/video/manager/videoList"
ICON_HOST = "dishare-probe.local"


@dataclass(frozen=True)
class AppSlot:
    slot_package: str
    target_package: str
    app_name: str
    color: tuple[int, int, int]


APP_SLOTS = [
    AppSlot("com.tencent.qqlive.audiobox", "com.vk.vkvideo", "VK Video", (0x21, 0x72, 0xff)),
    AppSlot("com.mgtv.auto", "ru.rutube.app", "Rutube", (0x19, 0x1f, 0x2e)),
    AppSlot("cn.cmvideo.car.play", "ru.yandex.yandexnavi", "Yandex Navi", (0xff, 0xcc, 0x00)),
    AppSlot("com.youku.car", "ru.yandex.music", "Yandex Music", (0xff, 0x33, 0x44)),
]


def run(cmd: list[str], *, check: bool = False, timeout: int = 30) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        check=check,
        timeout=timeout,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )


def adb(serial: str, args: Iterable[str], *, check: bool = False, timeout: int = 30) -> str:
    proc = run(["adb", "-s", serial, *args], check=check, timeout=timeout)
    return proc.stdout


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def find_aapt() -> str:
    for name in ("aapt", "aapt2"):
        found = shutil.which(name)
        if found:
            return found
    roots = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        "/opt/homebrew/share/android-commandlinetools",
        str(Path.home() / "Library" / "Android" / "sdk"),
    ]
    candidates: list[Path] = []
    for root in roots:
        if not root:
            continue
        candidates.extend(Path(root).glob("build-tools/*/aapt"))
    if candidates:
        return str(sorted(candidates)[-1])
    raise RuntimeError("aapt not found. Set ANDROID_HOME or install Android build-tools.")


def density_score(path: str) -> int:
    scores = {
        "xxxhdpi": 640,
        "xxhdpi": 480,
        "xhdpi": 320,
        "hdpi": 240,
        "mdpi": 160,
        "nodpi": 1,
    }
    for key, value in scores.items():
        if key in path:
            return value
    return 0


def parse_badging_icon(aapt: str, apk: Path) -> str | None:
    output = run([aapt, "dump", "badging", str(apk)], timeout=60).stdout
    best_path: str | None = None
    best_score = -1
    for line in output.splitlines():
        match = re.match(r"application-icon-(\d+):'([^']+)'", line)
        if match:
            score = int(match.group(1))
            if score > best_score:
                best_score = score
                best_path = match.group(2)
            continue
        match = re.match(r"application-icon:'([^']+)'", line)
        if match and best_path is None:
            best_score = 0
            best_path = match.group(1)
    return best_path


def choose_icon_from_zip(apk: Path, preferred: str | None) -> str | None:
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if preferred and preferred in names and preferred.lower().endswith((".png", ".webp")):
            return preferred
        icon_like = [
            name
            for name in names
            if name.startswith("res/")
            and name.lower().endswith((".png", ".webp"))
            and ("launcher" in name.lower() or "icon" in name.lower())
        ]
        if not icon_like:
            return None
        return sorted(icon_like, key=lambda item: (density_score(item), len(item)), reverse=True)[0]


def extract_icon_from_apk(apk: Path, icon_path: str, destination: Path) -> bool:
    with zipfile.ZipFile(apk) as archive:
        if icon_path not in archive.namelist():
            return False
        destination.parent.mkdir(parents=True, exist_ok=True)
        with archive.open(icon_path) as src, destination.open("wb") as dst:
            shutil.copyfileobj(src, dst)
        return True


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def write_placeholder_png(path: Path, rgb: tuple[int, int, int], size: int = 128) -> None:
    rows = [b"\x00" + bytes(rgb) * size for _ in range(size)]
    data = zlib.compress(b"".join(rows), 9)
    payload = b"\x89PNG\r\n\x1a\n"
    payload += png_chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    payload += png_chunk(b"IDAT", data)
    payload += png_chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


def remote_apk_path(serial: str, package_name: str) -> str | None:
    output = adb(serial, ["shell", "pm", "path", package_name], timeout=15)
    for line in output.splitlines():
        if line.startswith("package:"):
            return line.removeprefix("package:").strip()
    return None


def export_icons(args: argparse.Namespace) -> None:
    out_dir = Path(args.out_dir).resolve()
    icon_dir = out_dir / "icons"
    tmp_root = Path(tempfile.mkdtemp(prefix="dishare-icons-"))
    aapt = find_aapt()
    manifest: dict[str, dict[str, str]] = {}
    try:
        for slot in APP_SLOTS:
            remote = remote_apk_path(args.serial, slot.target_package)
            suffix = ".png"
            destination = icon_dir / f"{slot.slot_package}{suffix}"
            status = "placeholder"
            source = ""
            if remote:
                local_apk = tmp_root / f"{slot.target_package}.apk"
                pull = run(["adb", "-s", args.serial, "pull", remote, str(local_apk)], timeout=90)
                if pull.returncode == 0 and local_apk.exists():
                    preferred = parse_badging_icon(aapt, local_apk)
                    chosen = choose_icon_from_zip(local_apk, preferred)
                    if chosen:
                        suffix = ".webp" if chosen.lower().endswith(".webp") else ".png"
                        destination = icon_dir / f"{slot.slot_package}{suffix}"
                        if extract_icon_from_apk(local_apk, chosen, destination):
                            status = "extracted"
                            source = f"{slot.target_package}:{chosen}"
            if status != "extracted":
                destination = icon_dir / f"{slot.slot_package}.png"
                write_placeholder_png(destination, slot.color)
            manifest[slot.slot_package] = {
                "appName": slot.app_name,
                "targetPackage": slot.target_package,
                "iconFile": destination.name,
                "status": status,
                "source": source,
            }
            print(f"{slot.slot_package}: {slot.app_name}: {status} -> {destination}")
    finally:
        shutil.rmtree(tmp_root, ignore_errors=True)
    write_text(out_dir / "icons-manifest.json", json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")


def make_mitm(args: argparse.Namespace) -> None:
    out_dir = Path(args.out_dir).resolve()
    icon_dir = out_dir / "icons"
    icon_dir.mkdir(parents=True, exist_ok=True)

    result_data = []
    icon_map = {}
    for slot in APP_SLOTS:
        icon_file = None
        for suffix in (".png", ".webp"):
            candidate = icon_dir / f"{slot.slot_package}{suffix}"
            if candidate.exists():
                icon_file = candidate
                break
        if icon_file is None:
            icon_file = icon_dir / f"{slot.slot_package}.png"
            write_placeholder_png(icon_file, slot.color)
        icon_map[icon_file.name] = str(icon_file)
        icon_url = f"http://{ICON_HOST}/icons/{icon_file.name}"
        result_data.append(
            {
                "packageName": slot.slot_package,
                "appName": slot.app_name,
                "appIconUrl": icon_url,
                "backgroundImgUrl": icon_url,
            }
        )

    addon = f'''# Generated by tools/dishare_native_metadata_probe.py
import json
from pathlib import Path
from mitmproxy import http

VIDEO_LIST_HOST = {VIDEO_LIST_HOST!r}
VIDEO_LIST_PATH = {VIDEO_LIST_PATH!r}
ICON_HOST = {ICON_HOST!r}
ICON_MAP = {icon_map!r}
RESPONSE = {{"resultCode": "0", "resultDesc": "success", "resultData": {result_data!r}}}


def request(flow):
    host = flow.request.pretty_host
    path = flow.request.path.split("?", 1)[0]
    if host == VIDEO_LIST_HOST and path == VIDEO_LIST_PATH:
        body = json.dumps(RESPONSE, ensure_ascii=False).encode("utf-8")
        flow.response = http.Response.make(
            200,
            body,
            {{"Content-Type": "application/json;charset=UTF-8"}},
        )
        return

    if host == ICON_HOST and path.startswith("/icons/"):
        name = Path(path).name
        local = ICON_MAP.get(name)
        if local and Path(local).exists():
            content_type = "image/webp" if local.endswith(".webp") else "image/png"
            flow.response = http.Response.make(
                200,
                Path(local).read_bytes(),
                {{"Content-Type": content_type, "Cache-Control": "no-store"}},
            )
        else:
            flow.response = http.Response.make(404, b"missing icon")
'''
    addon_path = out_dir / "dishare_video_list_mitm.py"
    response_path = out_dir / "videoList-response.json"
    write_text(addon_path, addon)
    write_text(response_path, json.dumps({"resultCode": "0", "resultDesc": "success", "resultData": result_data}, indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {addon_path}")
    print(f"wrote {response_path}")
    print("run:")
    print(f"  mitmdump --listen-host 0.0.0.0 --listen-port {args.port} -s {addon_path}")


def collect(args: argparse.Namespace) -> None:
    out_dir = Path(args.out_dir).resolve()
    stamp = time.strftime("%Y%m%d-%H%M%S")
    report = out_dir / f"collect-{stamp}.txt"
    sections = [
        ("adb devices", ["adb", "devices", "-l"]),
        ("dishare package", ["adb", "-s", args.serial, "shell", "dumpsys", "package", "com.byd.dishare"]),
        ("denza apps package", ["adb", "-s", args.serial, "shell", "dumpsys", "package", "dev.denza.apps"]),
        ("installed target packages", ["adb", "-s", args.serial, "shell", "pm", "list", "packages"]),
        ("global proxy", ["adb", "-s", args.serial, "shell", "settings", "get", "global", "http_proxy"]),
        ("dishare services", ["adb", "-s", args.serial, "shell", "dumpsys", "activity", "services", "com.byd.dishare"]),
    ]
    lines: list[str] = []
    for title, cmd in sections:
        lines.append(f"\n## {title}\n$ {' '.join(cmd)}\n")
        try:
            lines.append(run(cmd, timeout=45).stdout)
        except Exception as exc:  # noqa: BLE001 - probe should keep collecting.
            lines.append(f"ERROR: {exc}\n")
    target_names = "|".join(re.escape(slot.slot_package) for slot in APP_SLOTS)
    filtered = []
    for line in "".join(lines).splitlines():
        if re.search(target_names, line) or any(token in line for token in [
            "com.byd.dishare",
            "CloudRequestService",
            "DynaConfigContentProvider",
            "SYSTEM_ALERT_WINDOW",
            "http_proxy",
            "stopped=",
            "versionName",
        ]):
            filtered.append(line)
    lines.append("\n## filtered highlights\n")
    lines.extend(item + "\n" for item in filtered)
    write_text(report, "".join(lines))
    print(report)


def proxy_on(args: argparse.Namespace) -> None:
    print(adb(args.serial, ["shell", "settings", "put", "global", "http_proxy", args.proxy], timeout=15), end="")
    print(adb(args.serial, ["shell", "settings", "get", "global", "http_proxy"], timeout=15), end="")


def proxy_off(args: argparse.Namespace) -> None:
    for key in ("http_proxy", "global_http_proxy_host", "global_http_proxy_port"):
        adb(args.serial, ["shell", "settings", "delete", "global", key], timeout=15)
    print(adb(args.serial, ["shell", "settings", "get", "global", "http_proxy"], timeout=15), end="")


def trigger_refresh(args: argparse.Namespace) -> None:
    if args.danger_clear_dishare_data:
        if args.confirm != "CLEAR_DISHARE_DATA":
            raise SystemExit("--danger-clear-dishare-data requires --confirm CLEAR_DISHARE_DATA")
        print(adb(args.serial, ["shell", "pm", "clear", "com.byd.dishare"], timeout=30), end="")
    print(adb(args.serial, ["shell", "am", "startservice", "-n", "com.byd.dishare/.app.service.CloudRequestService"], timeout=15), end="")
    time.sleep(args.wait)
    logs = adb(
        args.serial,
        ["logcat", "-d", "-v", "time"],
        timeout=30,
    )
    pattern = re.compile(
        r"CloudRequestService|UpdateShareAppUseCase|DefaultShareAppNetworkDataSource|"
        r"ShareAppRepository|videoList|denzacloud|SSL|Handshake|Cert|proxy",
        re.IGNORECASE,
    )
    for line in logs.splitlines():
        if pattern.search(line):
            print(line)


def main() -> int:
    parser = argparse.ArgumentParser(
        formatter_class=argparse.RawDescriptionHelpFormatter,
        description=__doc__,
        epilog=textwrap.dedent(
            f"""
            Typical controlled experiment:
              1. export icons from installed Russian apps:
                 {Path(__file__).name} export-icons --serial {DEFAULT_SERIAL}
              2. generate mitmproxy addon:
                 {Path(__file__).name} make-mitm --port 8888
              3. run mitmproxy manually with the printed command
              4. only after confirming routing/trust plan, set proxy:
                 {Path(__file__).name} proxy-on --serial {DEFAULT_SERIAL} --proxy <mac-ip>:8888
              5. trigger DiShare refresh:
                 {Path(__file__).name} trigger-refresh --serial {DEFAULT_SERIAL}
              6. always restore proxy:
                 {Path(__file__).name} proxy-off --serial {DEFAULT_SERIAL}
            """
        ),
    )
    parser.add_argument("--serial", default=DEFAULT_SERIAL)
    parser.add_argument("--out-dir", default=str(DEFAULT_OUT))
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("collect")
    sub.add_parser("export-icons")
    mitm = sub.add_parser("make-mitm")
    mitm.add_argument("--port", type=int, default=8888)
    on = sub.add_parser("proxy-on")
    on.add_argument("--proxy", required=True, help="host:port for Android global proxy")
    sub.add_parser("proxy-off")
    refresh = sub.add_parser("trigger-refresh")
    refresh.add_argument("--wait", type=float, default=5.0)
    refresh.add_argument("--danger-clear-dishare-data", action="store_true")
    refresh.add_argument("--confirm", default="")

    args = parser.parse_args()
    if args.command == "collect":
        collect(args)
    elif args.command == "export-icons":
        export_icons(args)
    elif args.command == "make-mitm":
        make_mitm(args)
    elif args.command == "proxy-on":
        proxy_on(args)
    elif args.command == "proxy-off":
        proxy_off(args)
    elif args.command == "trigger-refresh":
        trigger_refresh(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
