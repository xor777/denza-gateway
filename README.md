# denza-gateway (workspace)

Android workspace for reverse-engineering a Denza / BYD head unit and building
useful apps on top of it. Three concerns are kept deliberately separate:

- **Apps** — the useful end-user apps: Car ADB Gateway, Denza Gateway, Denza Mirrors, Denza Apps.
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
| `car-adb-gateway/` | Car ADB Gateway | Generic relay-only remote ADB gateway with one trusted computer and self-healing background service. |
| `cli/` | `cag` | Go developer CLI for macOS/Linux. |
| `relay/`, `ops/ansible/` | Relay control plane | Atomic enrollment/pairing state and repeatable restricted OpenSSH/PAM host configuration. |

Supporting areas: `docs/` (durable knowledge), `tools/` (host-side probe
scripts), `research/` (parked/non-built code and experiments), `reverse/` (local
reverse-engineering inputs/outputs, untracked).

## Start here

- [Project map](docs/project-map.md) — structure, component status, build outputs.
- [Repository governance](docs/governance.md) — product/prototype/research lanes.
- [Docs index](docs/README.md) — where each kind of knowledge lives.
- [Side camera findings](docs/side-camera-findings.md) — Denza Mirrors status.
- [DiShare API notes](docs/dishare-api-notes.md) — DiShare/HUD reverse-engineering.
- [Car ADB Gateway architecture](docs/CLOUD-ARCHITECTURE.md) — normative relay-only design.
- [Car ADB Gateway decisions](docs/CAR-ADB-GATEWAY-DECISIONS.md) — rationale and evidence log.

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
./gradlew :car-adb-gateway:testDebugUnitTest :car-adb-gateway:assembleDebug
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
car-adb-gateway/build/outputs/apk/debug/car-adb-gateway.apk
```

Build the cross-platform developer CLI:

```bash
cd cli
go test ./...
go build -o cag ./cmd/cag
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

---

## Car ADB Gateway

`car-adb-gateway` is a separate APK for relay-only access through the fixed
`adbgw.ru:443` service. It never exposes ADB or SSH on a vehicle network
interface. An administrator first creates a vehicle invite; after enrollment, a
person at the vehicle can give one developer computer a ten-minute pairing code.

The app deliberately supports one trusted computer. Pairing a replacement keeps
the old access until the new computer completes end-to-end authentication, then
revokes the old key and closes its session.

Developer workflow:

```bash
cag pair XXXX-XXXX
cag connect -- adb devices
cag connect -- adb shell
cag status
cag disconnect
```

See the architecture document for access roles, background behavior, security
boundaries, and pending live verification. Do not deploy `relay/` scripts by
hand; use and verify `ops/ansible` so SSH, PAM, account, and file restrictions
stay consistent.
