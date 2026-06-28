# Repository Governance

This file is the lightweight operating manual for future work in this repo.

## Change Lanes

Use one of these lanes before editing code:

| Lane | Allowed paths | Promotion requirement |
| --- | --- | --- |
| Product | `app/`, production parts of `projection-probe/` | Build succeeds, behavior is tested on the car or covered by tests, docs updated. |
| Prototype | `projection-probe/`, `tools/` | Must be isolated behind flags, settings, or explicit commands. Document live-test result. |
| Research | `research/`, `docs/*notes*`, host-only scripts | Must not be compiled into product APKs unless promoted. |

If an experiment fails, keep the finding in docs or `research/`; do not leave dead services, manifest entries, or permissions in the product app.

## Knowledge Rules

Record durable knowledge in the repo, not only in chat.

- Product behavior and user-facing workflows: update `README.md` or a focused doc under `docs/`.
- Reverse-engineering findings: update `docs/*notes*.md`.
- Camera/turn-signal findings: update `docs/side-camera-findings.md`.
- Research code that may be useful later: move it under `research/<topic>/` with a README explaining why it is not built.
- One-off host scripts: keep under `tools/` and state whether they are production candidates or probes.

Every durable note should include:

- date or firmware context when known;
- exact working command, component, or API name;
- result: working, blocked, flaky, or unknown;
- next action or reason to stop.

## Promotion Checklist

Before moving research/prototype code into a product APK:

- The code path has been tested on the car in the target scenario.
- The failure mode is understood and documented.
- Required permissions are available to a normal `/data/app` APK, or the limitation is explicit.
- The feature can be disabled from the UI or by stopping the service.
- It does not crash or restart stock components such as `com.byd.avc`.
- The README or relevant doc says how to build, install, start, stop, and diagnose it.

## Live Car Debugging Rules

- Treat `com.byd.avc` crashes as a hard stop. Capture `logcat -b crash -v time`, document the trigger, then revert or isolate the change.
- Keep the last known working APK behavior easy to restore before trying a risky experiment.
- Prefer host-side scripts in `tools/` for speculative probes before adding code to the Android app.
- Do not add BYD/system permissions to `AndroidManifest.xml` unless the car has proven they are granted to this APK.
- Do not commit generated APKs, reverse-engineered APKs, or large extracted binaries.

## Git Hygiene

- Keep unrelated product changes and research changes in separate commits.
- If code is intentionally parked for later, put it under `research/` and document it.
- If a feature is not working, mark it as blocked or experimental in docs before pushing.
- Run at least the relevant Gradle build before publishing code changes:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :projection-probe:assembleDebug
```
