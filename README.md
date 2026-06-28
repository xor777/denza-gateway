# denza-gateway

Android workspace for Denza head-unit tools.

The repo currently contains two apps:

- `app/`: `denza-gateway`, an SSH gateway from the car LAN to local ADB endpoints.
- `projection-probe/`: `denza-mirrors`, a dashboard side-camera enlargement prototype.

Start here before changing code:

- [Project map](docs/project-map.md)
- [Repository governance](docs/governance.md)
- [Side camera findings](docs/side-camera-findings.md)
- [DiShare API notes](docs/dishare-api-notes.md)
- [Vehicle event probe archive](research/vehicle-events/README.md)

## Denza Gateway

`denza-gateway` is an Android LAN gateway for a car head unit where ADB is available locally to apps, but not exposed over USB.

The app starts an embedded SSH server on the car's active Wi-Fi IPv4 address. Remote access uses standard SSH local port forwarding (`ssh -L`) and standard ADB protocols:

- ADB smart socket on `127.0.0.1:5037`, used with `adb -H 127.0.0.1 -P 5038 ...`
- raw adbd TCP on `127.0.0.1:5555`, used with `adb connect 127.0.0.1:5555`

The app does not expose a raw ADB port on the LAN. SSH accepts only the `denza` user with the current 8-digit pairing code shown on screen, and forwarding is allowed only to the selected local ADB endpoint.

## Build Gateway

This workspace uses:

- Android Gradle Plugin `9.2.1`
- Gradle `9.4.1`
- Kotlin `2.3.21`
- compile SDK `37`, target SDK `36`

On this machine, Homebrew OpenJDK is available but not registered as the system Java. Use:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew testDebugUnitTest assembleDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/denza-gateway-debug.apk
```

If Android platforms are missing, install them with:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk sdkmanager "platforms;android-37" "platforms;android-36"
```

## Build Denza Mirrors

```bash
./gradlew :projection-probe:assembleDebug
```

Local APK path:

```text
projection-probe/build/outputs/apk/debug/denza-mirrors.apk
```

Generated APKs are intentionally ignored by Git.

## Gateway LAN Usage

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

## Security Model

- SSH binds to the active Wi-Fi IPv4 address, not to all interfaces.
- Password auth requires the current on-screen pairing code.
- Public-key auth, keyboard-interactive auth, shell, command execution, SFTP, X11, agent forwarding, and remote forwarding are disabled.
- Local forwarding is allowed only to the detected/selected ADB endpoint.
- Peers outside the active Wi-Fi subnet are denied and logged.
