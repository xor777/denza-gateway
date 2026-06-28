# Side Camera Findings

This is the durable status page for `denza-mirrors`.

## Working Path

- The active product direction is dashboard/instrument display enlargement, not HUD camera streaming.
- `SideCameraOverlayMonitorService` monitors stock AVC camera windows through local ADB and starts `AvcAidlDashActivity`.
- `AvcAidlDashActivity` can display the AVC AIDL side-camera feed on display `4`.
- Left and right camera overlays work as standalone turn-signal scenarios.
- The UI supports monitor start/stop, ready diagnostics, camera position mode, and image processing strength.
- Edge dimming and image-processing controls are part of the current prototype.

## Known Limitations

- Fast `left -> right` turn-signal switching is not solved. Right works standalone, but after left the stock AVC path can fail to start immediately.
- Some attempted changes caused native crashes in stock `com.byd.avc`:
  - delaying left overlay start did not solve the quick switch issue and made UX worse;
  - changing the overlay to a non-overlay presentation did not solve right startup;
  - launching or layering in ways that conflict with stock `PIP2MeterActivity` can crash `com.byd.avc` in native `TSAPI.initDisplay/setSurface`.
- If `com.byd.avc` crashes, stop the monitor and collect:

```bash
adb logcat -b crash -d -v time
adb logcat -d -v time | rg "DenzaProjectionProbe|PIP2MeterActivity|CompactAlertActivity|onTurnLightStateChanged|Fatal signal"
```

## Failed or Blocked Paths

- Direct BYD light/vehicle getters from a normal debug APK are blocked by permissions. The old `AvcTurnSignalMonitorService` path reported permission failures and is not a production trigger.
- Legacy vehicle listeners registered but did not deliver useful app-level turn-signal callbacks in tests.
- HUD camera streaming through DiShare is not product-ready:
  - generated frames can render to HUD;
  - app-accessible Camera2 ids `0` and `1` can render;
  - real side/AVC feeds are protected or black when routed through the DiShare encoder path;
  - Camera2 ids such as `2` and `10` failed from the normal app.

## Research Leads

- System `logcat` contains useful vehicle/camera events, including turn-light state lines from AVC. A host-side or local-ADB event monitor may be a better trigger than polling windows, but it must be implemented without noisy long-running ADB sessions or battery-heavy polling.
- The safest next investigation for `left -> right` is to avoid competing with stock `PIP2MeterActivity` during its surface initialization. Prototype outside product first, then promote only if stock `com.byd.avc` remains stable.

## Last Known Safe Behavior

- Basic restored behavior: left and right standalone overlays work as before.
- The quick `left -> right` bug remains open.
- Do not reintroduce the delayed-left or `overlay_window=false` experiments without documenting a new reason and a clean crash-free test.
