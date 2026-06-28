# Tools

Host-side scripts for live experiments against the car.

These scripts are probes, not product code. A script can only become part of an Android app after the promotion checklist in `docs/governance.md` is satisfied.

Current scripts:

- `side_camera_overlay_monitor.sh`: older host-side monitor for side-camera windows and turn-light logcat events.
- `turn_signal_overlay_monitor.sh`: older PIP/turn-signal overlay experiment.
- `avc_alert_overlay_monitor.sh`: older AVC alert/window monitor experiment.

When adding a tool, include:

- expected ADB serial or tunnel;
- exact scenario it tests;
- known side effects;
- whether the result should update `docs/side-camera-findings.md`.
