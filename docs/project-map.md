# Project Map

This repository has several Android apps/modules and a small research/tooling area.

## Apps

| Path | APK | Purpose | Status |
| --- | --- | --- | --- |
| `app/` | `denza-gateway` | SSH gateway from the car LAN to local ADB endpoints on the head unit. | Product app. Keep changes conservative and test with unit tests. |
| `projection-probe/` | `denza-mirrors` | Driver-display side-camera enlargement for turn-signal camera windows. Also still contains older probe activities. | Prototype/product app. Working dashboard path is active; HUD and low-level vehicle APIs remain research. |
| `denza-apps/` | `denza-apps` | Head app for future Denza features. Currently toggles Simulcast support with one Start/Stop control. | Prototype app. Verified on car for Simulcast alias support. |

## Shared Android Modules

| Path | Purpose | Rules |
| --- | --- | --- |
| `dishare-bridge/` | Raw DiShare binder bridge used by `denza-apps` and Simulcast aliases, including runtime screen discovery. | Keep API notes in `docs/dishare-api-notes.md` aligned with transaction behavior. |
| `simulcast-aliases/` | Tiny APK flavors that occupy DiShare-whitelisted package names and launch Russian video apps through DiShare. | Build/install as helper APKs; do not treat stock list icons as proof of target mapping. |

## Supporting Areas

| Path | Purpose | Rules |
| --- | --- | --- |
| `docs/` | Stable project knowledge, decisions, and investigation summaries. | Update when behavior, commands, or known limitations change. |
| `research/` | Code that is not built into product APKs. | Keep failed or permission-blocked probes here, not in app source. |
| `tools/` | Host-side scripts for one-off live experiments. | Scripts are not production paths until promoted through `docs/governance.md`. |
| `reverse/` | Local reverse-engineering input/output, often large. | APKs and extracted binaries must stay untracked. |

## Component Inventory

### `app/` (`denza-gateway`)

| Component | Status |
| --- | --- |
| `MainActivity`, `GatewayService`, `SshGatewayServer` | Product path for LAN SSH forwarding to local ADB. |
| `AdbProbe`, `ProbePlan`, `ForwardingPolicy` | Product support code with unit tests. |

### `projection-probe/` (`denza-mirrors`)

| Component | Status |
| --- | --- |
| `ProjectionProbeActivity` | Product/prototype UI for Denza Mirrors. |
| `SideCameraOverlayMonitorService`, `SideCameraBootReceiver` | Active dashboard camera monitor path. |
| `AvcAidlDashActivity` | Active dashboard AVC display path. |
| `LocalAdbClient`, `AdbKeyStore` | Required support for local ADB commands from the app. |
| `HudDiShareActivity`, `HudImageActivity`, `DiShareProbeActivity`, `MediaStreamProbeActivity`, `HudSomeIpProbeActivity` | Research/probe components. Do not treat as product without promotion. |
| `AvcTurnSignalMonitorService`, `AvcTurnSignalMonitorActivity` | Legacy direct BYD light API probe. Permission-blocked in normal app tests; not a production trigger. |
| `AvcPipHookActivity`, `DashCameraActivity`, `DashPresentationActivity`, map demo activities | Historical probes/demos. Confirm live value before editing or invoking. |

### `denza-apps/`

| Component | Status |
| --- | --- |
| `MainActivity` | One-button Start/Stop controller for the Simulcast support services. |
| `SourceKeeperService` | Active Simulcast support path. Registers whitelisted source-only slots for App Change. |
| `SimulcastOverlayService` | Active no-root visual UX path. Runs a Simulcast monitor, places an invisible hotspot over App Change, then draws real Russian app icons and launches targets through `dishare-bridge` on drop. |
| `SimulcastBootReceiver` | Best-effort startup hook. Starts source registration after boot/package replace and forwards explicit debug actions without crashing on background-start limits. |

### `dishare-bridge/`

| Component | Status |
| --- | --- |
| `DiShareProjectionBridge` | Active raw binder wrapper for DiShare API/control services. Source-only slots use `2560x1440`; direct target shares use the verified `1024x576` path. |
| `DiShareScreens` | Active screen-discovery wrapper for `getScreens`; used by the custom Simulcast overlay to accept only runtime-available receivers. |

### `simulcast-aliases/`

| Component | Status |
| --- | --- |
| `launcher` flavors | Active Simulcast helper APKs. Verified slots include VK via `com.tencent.qqlive.audiobox` and Rutube via `com.mgtv.auto`. Native icons/text may remain stock DiShare metadata. |

## Current Product Direction

- `denza-gateway` is the connectivity app. It should not contain camera or HUD experiments.
- `denza-mirrors` is the camera app. The supported path is dashboard/instrument display enlargement via the AVC AIDL dashboard overlay.
- `denza-apps` is the head app for miscellaneous car features. The first working
  feature path is Simulcast alias support for Russian video apps.
- For Simulcast, normal app uid is enough for direct DiShare launches. The open
  research problem is native `ShareApp` visual metadata, not system permission.
- HUD camera output is not a supported product path from a normal debug APK. DiShare can show generated frames and some app-accessible Camera2 feeds, but protected side/AVC feeds remain blocked.
- Vehicle event APIs are research-only for now. Normal app uid access to direct BYD getters/listeners was permission-blocked or did not deliver useful callbacks.

## Build Outputs

Generated APKs are intentionally ignored by Git.

```bash
./gradlew :app:assembleDebug
./gradlew :projection-probe:assembleDebug
./gradlew :denza-apps:assembleDebug
./gradlew :simulcast-aliases:launcher:assembleDebug
```

Useful local APK paths:

```text
app/build/outputs/apk/debug/app-debug.apk
projection-probe/build/outputs/apk/debug/denza-mirrors.apk
denza-apps/build/outputs/apk/debug/denza-apps.apk
simulcast-aliases/launcher/build/outputs/apk/*/debug/simulcast-alias-*.apk
```

Do not stage APK files. If a large APK appears in `git status`, fix `.gitignore` first.
