# Denza Apps Smart Task Handoff Design

## Problem

Simulcast and Navigation can currently move the same Android application task
without coordinating ownership. The default Simulcast row includes
`ru.yandex.yandexnavi`, which is also the default Navigation package.

When Navigation projects a task that DiShare is using on `BYD-Mirror`, DiShare
and Denza Apps can move the same task between virtual displays. Navigation then
silently tears down its projection when its five-second verifier observes the
task on another display. The resulting user-visible behavior can be a brief
instrument window followed by a return to the IVI. Rapid display and bounds
changes are also unsafe for applications such as 2GIS.

The fix must not interpret the Simulcast feature being enabled as a conflict.
Only an active share of the same package conflicts with Navigation.

## Product Contract

### Navigation while Simulcast is active

- If Simulcast is disabled, enabled without an active target, or actively
  sharing another package, Navigation proceeds normally.
- An active share of another package is never stopped or restarted.
- If the selected navigator is the active Simulcast target, the first
  **На приборку** action does not stop anything. The Navigation card becomes an
  amber attention state:
  `Навигатор сейчас используется в трансляции`.
- The existing Navigation primary action becomes **Перенести**. This second,
  explicit action is the confirmation that the active share of that navigator
  may end.
- Simulcast remains enabled after the share ends. Only the conflicting active
  session is stopped.
- No new permanent button is added and the `314.dp` card height is unchanged.

### Reverse direction

Starting Simulcast for the same package while Navigation owns its projected
task must never move the task out from under Navigation. This release fails
closed with a short user message telling the user to return the navigator
first. Automatic cluster-to-Simulcast handoff is outside this change because
the drag UI has no accepted confirmation treatment and needs a separate
live-car UX decision.

Starting Simulcast for any other package remains unchanged.

## Considered Approaches

### 1. Stop Simulcast whenever Navigation starts

Rejected. It interrupts unrelated content and mistakes a feature setting for
ownership of the selected task.

### 2. Stop a matching share immediately on the first Navigation action

Rejected. It is package-aware but still changes the user's active output
without warning. It also races DiShare teardown because `stop` completion and
task/display settling are not the same event.

### 3. Two-step, package-aware handoff

Selected. The first action detects and explains the conflict. The second action
confirms the transfer, stops only the matching session, waits for ownership to
settle, and then projects. It reuses the current card action and preserves
unrelated shares.

## Architecture

### Pure ownership policy

Add a pure policy under `feature.navigation`:

```kotlin
data class NavigationTaskObservation(
    val packageName: String,
    val taskId: Int?,
    val displayId: Int?,
    val displayName: String?,
    val activeSimulcastPackage: String?,
    val simulcastRuntimeActive: Boolean,
    val simulcastDisplayPresent: Boolean,
)

sealed interface NavigationTransferDecision {
    data object Proceed : NavigationTransferDecision
    data object ConfirmMatchingSimulcast : NavigationTransferDecision
    data class BlockedExternalDisplay(val details: String) :
        NavigationTransferDecision
}
```

`NavigationTaskOwnershipPolicy.decide(observation)` follows these rules:

- no matching active Simulcast package and a central/absent task → `Proceed`;
- matching Simulcast package with either an active in-process session or the
  task observed on a DiShare display → `ConfirmMatchingSimulcast`;
- a matching live start/stop transition whose task location is temporarily
  unknown → `ConfirmMatchingSimulcast`;
- a task on another non-Denza, non-DiShare display → fail closed with
  `BlockedExternalDisplay`;
- a matching package found only in persisted metadata is not live evidence and
  does not block a central/absent task when `simulcastRuntimeActive` and
  `simulcastDisplayPresent` are both false.

The policy is independent of user-facing strings and Android services.

### Handoff coordinator

Add a narrow `NavigationTaskHandoff` component. It owns one monotonic
generation and the phases:

```text
IDLE → STOPPING_SIMULCAST → WAITING_FOR_TASK → READY
                                  ↘ FAILED
```

It accepts injected operations for unit tests:

- stop the current matching Simulcast session;
- observe the navigator task and DiShare display;
- schedule the next observation;
- publish phase/details;
- continue the pending Navigation projection.

The coordinator:

- starts only after the explicit second Navigation action;
- re-observes ownership on that second action and calls stop only if the same
  package still has live DiShare evidence; if the conflict has already ended,
  it proceeds without touching Simulcast;
- ignores duplicate taps while a generation is active;
- treats the DiShare stop callback as acknowledgement, not proof that the task
  has settled;
- requires two consecutive equivalent non-DiShare observations at least
  100 ms apart before continuing;
- allows either a task on display `0` or an absent task after `BYD-Mirror` has
  disappeared; the existing Navigation path can reopen an absent task;
- times out after 5 seconds;
- never projects after stop failure, timeout, or an unexpected external
  display;
- has no automatic retry after failure.

These constants are local safety defaults. Their absence of a vendor race must
be verified on the car before the behavior is described as hardware accepted.

### Simulcast lifecycle boundary

Extend the internal Simulcast stop path with a result callback suitable for the
handoff coordinator. The callback reports accepted stop, failure, and details.
It does not expose a new Binder, broadcast, or exported component.

`SimulcastIntegration` remains the persisted UI hint, but ownership decisions
must also inspect the current task/display. Persisted metadata alone never
authorizes stopping a share. Every successful `onStopped` path clears the
stored package and receiver. A failed stop keeps the active target so the UI
does not claim that the session ended.

### Navigation integration

Before `projectToCluster()` mutates a task:

1. observe the selected package, task, current display, and active Simulcast
   target;
2. run the pure ownership policy;
3. proceed immediately for no conflict;
4. publish `NEEDS_ACTION + TRANSFER_FROM_SIMULCAST` for a matching share;
5. on the second action, run `NavigationTaskHandoff`;
6. call the existing projection path only after the coordinator reports ready.

`verifyActiveSession()` no longer turns an unexpected move into an empty
`READY` state. It records expected and actual display IDs. If the same package
is now the active Simulcast target, the card explains that it is in
transmission; otherwise it reports that the navigator moved to another screen.

### Reverse guard

Before `SimulcastAccessibilityService` starts a target, a pure guard checks the
current `NavigationSession`:

- a different package or non-projected Navigation session → start normally;
- the same projected package → do not call DiShare and show
  `Сначала верните навигатор с приборки`.

This guard prevents the reverse task fight without adding a new overlay dialog.

## UI

Add `FeatureResolution.TRANSFER_FROM_SIMULCAST`.

`FeatureActionPolicy.navigation()` maps it to:

```text
label = "Перенести"
target = RETRY
```

The existing two-line attention row renders the explanation. While the handoff
is active, Navigation uses `RECOVERING` with
`Завершаю трансляцию навигатора`; the existing primary control is disabled.

On failure the card uses `NEEDS_ACTION` with a concrete message:

- `Не удалось завершить трансляцию навигатора`;
- `Навигатор остался на другом экране`.

Technical stop results, generation, task ID, expected display, actual display,
and timeout cause remain in `details`.

## Diagnostics

The hidden support report adds:

- active Simulcast package and receiver;
- Navigation task ID, phase, expected display, and last observed display;
- task-handoff phase, generation, and details;
- unexpected-display events counted in process memory.

No user content or route data is collected.

## Tests

Pure/local tests cover:

- Simulcast enabled with no active target → Navigation proceeds;
- another package actively shared → Navigation proceeds and stop is never
  called;
- matching package → first action requests confirmation and does not stop;
- explicit confirmation → exactly one stop;
- stop acknowledgement alone does not project;
- two stable central observations continue exactly one projection;
- absent task plus absent `BYD-Mirror` continues through the existing reopen
  path;
- stop failure, unexpected display, timeout, and duplicate tap never project;
- stale persisted target with a confirmed central task does not block;
- same projected package is rejected by the reverse Simulcast guard;
- different projected package does not block Simulcast;
- an unexpected Navigation display produces a non-empty user message and
  technical details;
- successful Simulcast stop clears stored target metadata.

The complete local gate remains:

```bash
./gradlew --rerun-tasks \
  :denza-apps:testDebugUnitTest \
  :denza-apps:assembleDebug \
  :denza-apps:assembleRelease \
  :denza-apps:lintDebug
git diff --check
```

## Vehicle Acceptance Gate

Run only after the user separately enables the car connection.

1. Record APK hash, firmware fingerprint, active Simulcast target/receiver,
   task/display IDs, and clear the crash buffer.
2. Share VK Video, then project Yandex Navigator. VK must continue
   uninterrupted while Navigation reaches `PROJECTED`.
3. Share Yandex Navigator, then press **На приборку** once. The share must
   continue and the card must show the confirmation state.
4. Press **Перенести**. The Yandex share must stop once; Navigation must appear
   on the instrument display without returning for at least 15 seconds.
5. While Navigation projects Yandex, try to cast Yandex. The cast must be
   rejected without moving the task. Casting another app must still work.
6. Inspect `DenzaNavigation`, `DenzaSimulcastOverlay`,
   `DenzaDiShareBridge`, task/display state, and the crash buffer.

Any application crash, repeated display ping-pong, unrelated share
interruption, or `com.byd.avc` crash stops the gate. Until this passes, the
change is locally verified but not hardware accepted.
