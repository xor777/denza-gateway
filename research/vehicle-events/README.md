# Vehicle Event Probe Archive

This folder keeps the research-only vehicle event probe outside the Android app source tree.
It is not compiled into `denza-mirrors.apk`.

## Last Findings

- Tested on the car on 2026-06-27.
- `BYDAUTO_*_COMMON` permissions were granted to the debug app.
- `BYDAUTO_*_GET` permissions were not granted.
- Legacy BYD listeners could be registered with `registerListener(IBYDAutoListener, int[])`, but no app-level callbacks arrived for turn-signal tests.
- Direct getters for `setting`, `bodywork`, `light`, `gearbox`, `instrument`, and others failed with `SecurityException`.
- System `logcat` did show vehicle/camera activity during turn-signal tests, including:
  - `BYDAutoSettingDevice postEvent event_type=28600009 value=0/1`
  - `BYDAutoBodyworkDevice postEvent ...`
  - active camera frames for camera ids `2`, `3`, and `10`
  - `com.byd.sr/.cluster.ClusterActivity` rendering on the dashboard
- Practical production path should stay with the working SR/camera-window monitor, not these app-level vehicle listeners.

## If Research Resumes

Copy `VehicleEventProbeService.java` back into:

```text
projection-probe/src/main/java/com/byd/cluster/projection/mapdemo/VehicleEventProbeService.java
```

Then add the service and experimental permissions back to `projection-probe/src/main/AndroidManifest.xml`.

The probe action names were:

```text
com.byd.cluster.projection.mapdemo.START_VEHICLE_EVENT_PROBE
com.byd.cluster.projection.mapdemo.STOP_VEHICLE_EVENT_PROBE
```

The app data files were:

```text
vehicle_event_probe_status.txt
vehicle_event_probe.log
```
