# Project Map

This repository has two Android apps and a small research/tooling area.

## Apps

| Path | APK | Purpose | Status |
| --- | --- | --- | --- |
| `app/` | `denza-gateway` | SSH gateway from the car LAN to local ADB endpoints on the head unit. | Product app. Keep changes conservative and test with unit tests. |
| `projection-probe/` | `denza-mirrors` | Driver-display side-camera enlargement for turn-signal camera windows. Also still contains older probe activities. | Prototype/product app. Working dashboard path is active; HUD and low-level vehicle APIs remain research. |

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

## Current Product Direction

- `denza-gateway` is the connectivity app. It should not contain camera or HUD experiments.
- `denza-mirrors` is the camera app. The supported path is dashboard/instrument display enlargement via the AVC AIDL dashboard overlay.
- HUD camera output is not a supported product path from a normal debug APK. DiShare can show generated frames and some app-accessible Camera2 feeds, but protected side/AVC feeds remain blocked.
- Vehicle event APIs are research-only for now. Normal app uid access to direct BYD getters/listeners was permission-blocked or did not deliver useful callbacks.

## Build Outputs

Generated APKs are intentionally ignored by Git.

```bash
./gradlew :app:assembleDebug
./gradlew :projection-probe:assembleDebug
```

Useful local APK paths:

```text
app/build/outputs/apk/debug/app-debug.apk
projection-probe/build/outputs/apk/debug/denza-mirrors.apk
```

Do not stage APK files. If a large APK appears in `git status`, fix `.gitignore` first.
