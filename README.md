# denza-gateway (workspace)

Android workspace for reverse-engineering a Denza / BYD head unit and building
useful apps on top of it. Three concerns are kept deliberately separate:

- **Apps** — the useful end-user apps: Denza Gateway, Denza Mirrors, Denza Apps.
- **A place to poke the car** — host scripts in `tools/` and on-device research
  probes in Denza Mirrors' `dev.denza.mirrors.probe` package.
- **Knowledge of what works / what doesn't** — durable findings in `docs/` and
  parked (non-built) code in `research/`.

> The repository is still named `denza-gateway` for historical reasons. It has
> grown past a single gateway and will be renamed.

## Modules (directory name = product)

| Path | Product | What it is |
| --- | --- | --- |
| `denza-gateway/` | Denza Gateway | SSH gateway from the car LAN to local ADB endpoints on the head unit. |
| `denza-mirrors/` | Denza Mirrors | Driver-display side-camera enlargement. Product code in `dev.denza.mirrors`; research probes isolated in `dev.denza.mirrors.probe`. |
| `denza-apps/` | Denza Apps | Head app for car features. Current feature: Simulcast for Russian video apps via an accessibility overlay. |
| `dishare-bridge/` | _(library)_ | Shared raw DiShare binder bridge used by `denza-apps`. |

Supporting areas: `docs/` (durable knowledge), `tools/` (host-side probe
scripts), `research/` (parked/non-built code and experiments), `reverse/` (local
reverse-engineering inputs/outputs, untracked).

## Start here

- [Project map](docs/project-map.md) — structure, component status, build outputs.
- [Repository governance](docs/governance.md) — product/prototype/research lanes.
- [Docs index](docs/README.md) — where each kind of knowledge lives.
- [Side camera findings](docs/side-camera-findings.md) — Denza Mirrors status.
- [DiShare API notes](docs/dishare-api-notes.md) — DiShare/HUD reverse-engineering.

## Build

This workspace uses:

- Android Gradle Plugin `9.2.1`
- Gradle `9.4.1`
- Kotlin `2.3.21`
- compile SDK `37`, target SDK `36`/`33`

On this machine, Homebrew OpenJDK is available but not registered as the system
Java. Use:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./gradlew :denza-gateway:testDebugUnitTest :denza-gateway:assembleDebug
./gradlew :denza-mirrors:assembleDebug
./gradlew :denza-apps:assembleDebug
```

If Android platforms are missing, install them with:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk sdkmanager "platforms;android-37" "platforms;android-36"
```

Local APK paths (generated APKs are intentionally ignored by Git):

```text
denza-gateway/build/outputs/apk/debug/denza-gateway.apk
denza-mirrors/build/outputs/apk/debug/denza-mirrors.apk
denza-apps/build/outputs/apk/debug/denza-apps.apk
```

---

## Denza Gateway

`denza-gateway` is an Android LAN gateway for a car head unit where ADB is
available locally to apps, but not exposed over USB.

The app starts an embedded SSH server on the car's active Wi-Fi IPv4 address.
Remote access uses standard SSH local port forwarding (`ssh -L`) and standard
ADB protocols:

- ADB smart socket on `127.0.0.1:5037`, used with `adb -H 127.0.0.1 -P 5038 ...`
- raw adbd TCP on `127.0.0.1:5555`, used with `adb connect 127.0.0.1:5555`

The app does not expose a raw ADB port on the LAN. SSH accepts only the `denza`
user with the current 8-digit pairing code shown on screen, and forwarding is
allowed only to the selected local ADB endpoint.

Package the gateway debug APK for distribution:

```bash
./gradlew :denza-gateway:assembleDebug
mkdir -p dist
cp denza-gateway/build/outputs/apk/debug/denza-gateway.apk dist/denza-gateway-debug.apk
```

### Gateway LAN Usage

1. Install and open the app on the Denza head unit.
2. Tap `Test ADB`.
3. Tap `Start`.
4. On your laptop, use the command shown in the app.

For ADB server mode:

```bash
ssh -p 2222 -N -L 5038:127.0.0.1:5037 denza@<car-wifi-ip>
adb -H 127.0.0.1 -P 5038 devices
```

For raw adbd mode:

```bash
ssh -p 2222 -N -L 5555:127.0.0.1:5555 denza@<car-wifi-ip>
adb connect 127.0.0.1:5555
adb devices
```

### Gateway Security Model

- SSH binds to the active Wi-Fi IPv4 address, not to all interfaces.
- Password auth requires the current on-screen pairing code.
- Public-key auth, keyboard-interactive auth, shell, command execution, SFTP,
  X11, agent forwarding, and remote forwarding are disabled.
- Local forwarding is allowed only to the detected/selected ADB endpoint.
- Peers outside the active Wi-Fi subnet are denied and logged.
