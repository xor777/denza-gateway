# Denza Apps Actionable Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace generic Denza Apps attention states with clear instructions and context-aware reuse of existing controls without changing card heights.

**Architecture:** Add a typed resolution to `FeatureSnapshot`, map runtime failures to that resolution, and keep button-label/routing decisions in a pure UI policy. Compose consumes the policy, replaces rather than appends attention content, and opens a dedicated cluster picker when required.

**Tech Stack:** Kotlin 2.x, Android 13 contract, Jetpack Compose Material 3, JUnit 4, Gradle.

## Global Constraints

- Normal-state layout and permanent control count remain unchanged.
- Large cards remain `314.dp`; compact cards remain `96.dp`.
- `NEEDS_ACTION` is amber and contains a concrete instruction; red remains reserved for `ERROR`.
- No string matching may be used to choose an action.
- Technical diagnostics remain in `details`.
- No live-car verification claim is added.

---

### Task 1: Typed feature resolutions

**Files:**
- Modify: `apps/denza-apps/src/main/java/dev/denza/apps/core/FeatureModels.kt`
- Modify: `apps/denza-apps/src/test/java/dev/denza/apps/core/FeatureModelsTest.kt`

**Interfaces:**
- Produces: `enum class FeatureResolution`
- Produces: `FeatureSnapshot.resolution: FeatureResolution?`
- Produces: `FeatureReducer.needsAction(..., resolution: FeatureResolution?)`

- [ ] **Step 1: Write the failing reducer tests**

Add tests proving `needsAction` carries `SELECT_APPS` and that `recovering`
clears a stale resolution.

- [ ] **Step 2: Verify red**

Run:

```bash
./gradlew :denza-apps:testDebugUnitTest \
  --tests dev.denza.apps.core.FeatureModelsTest
```

Expected: compilation fails because `FeatureResolution` and `resolution` do not
exist.

- [ ] **Step 3: Implement the minimal model**

Add the six resolution values from the design, a nullable snapshot property,
and reducer behavior that clears stale resolutions outside `NEEDS_ACTION`.

- [ ] **Step 4: Verify green and commit**

Run the focused test, then commit:

```bash
git add apps/denza-apps/src/main/java/dev/denza/apps/core/FeatureModels.kt \
  apps/denza-apps/src/test/java/dev/denza/apps/core/FeatureModelsTest.kt
git commit -m "feat(apps): type user resolution states"
```

### Task 2: Runtime classification and nontechnical copy

**Files:**
- Modify: `apps/denza-apps/src/main/java/dev/denza/apps/SimulcastCoordinator.kt`
- Modify: `apps/denza-apps/src/main/java/dev/denza/apps/DenzaAppRepository.kt`
- Modify: `apps/denza-apps/src/main/java/dev/denza/apps/feature/navigation/NavigationModels.kt`
- Modify: `apps/denza-apps/src/main/java/dev/denza/apps/feature/navigation/NavigationCoordinator.kt`
- Modify: `apps/denza-apps/src/test/java/dev/denza/apps/SimulcastCoordinatorTest.kt`
- Modify: `apps/denza-apps/src/test/java/dev/denza/apps/feature/navigation/NavigationModelsTest.kt`

**Interfaces:**
- Produces: `SimulcastBlocker`
- Produces: `NavigationSession.resolution`
- Consumes: `FeatureResolution`

- [ ] **Step 1: Write failing Simulcast classification tests**

Cover:

- no selected apps → `NEEDS_ACTION`, `SELECT_APPS`, clear instruction;
- missing DiShare package → `UNAVAILABLE`, no resolution;
- missing access → `NEEDS_ACTION`, `RETRY`;
- authorization pending → `CONFIRM_ON_CAR`.

- [ ] **Step 2: Write failing navigation model tests**

Prove a session can carry `SELECT_CLUSTER_DISPLAY` and that its default primary
label remains unchanged until the UI policy interprets the resolution.

- [ ] **Step 3: Verify red**

Run both focused test classes and confirm failures are caused by missing typed
blockers/resolutions.

- [ ] **Step 4: Implement typed runtime mapping**

Replace Simulcast's free-form blocker with `SimulcastBlocker`, propagate typed
resolutions through reconciliation events, and assign explicit resolutions to
Mirrors, HUD, and Navigation terminal states. Mark missing Simulcast as
`UNAVAILABLE`. Make missing mirror display consistent across evaluation and
reconciliation by checking the dedicated camera-overlay target rather than the
base navigation display.

- [ ] **Step 5: Replace technical copy**

Use the exact user strings from the design while preserving original exception
text in `details`.

- [ ] **Step 6: Verify green and commit**

Run the focused tests and the full unit suite, then commit:

```bash
git add apps/denza-apps/src/main/java/dev/denza/apps \
  apps/denza-apps/src/test/java/dev/denza/apps
git commit -m "fix(apps): classify actionable feature states"
```

### Task 3: Pure existing-control policy

**Files:**
- Create: `apps/denza-apps/src/main/java/dev/denza/apps/ui/FeatureActionPolicy.kt`
- Create: `apps/denza-apps/src/test/java/dev/denza/apps/ui/FeatureActionPolicyTest.kt`

**Interfaces:**
- Produces: `FeatureActionTarget`
- Produces: `DualActionPolicy`
- Produces: `SingleActionPolicy`
- Produces: `FeatureActionPolicy.simulcast(snapshot)`
- Produces: `FeatureActionPolicy.navigation(snapshot, defaultLabel)`
- Produces: `FeatureActionPolicy.mirrors(snapshot)`

- [ ] **Step 1: Write failing policy tests**

Cover all mappings:

- select apps/navigation app emphasizes the existing chooser;
- select display routes the existing primary action to the picker;
- Mirrors never routes to the base-display picker and retries discovery of its
  dedicated camera-overlay target;
- retry and confirmation relabel the primary action;
- unavailable Simulcast disables its primary control;
- normal states retain existing labels and targets.

- [ ] **Step 2: Verify red**

Run the new test class and confirm compilation fails because the policy is
missing.

- [ ] **Step 3: Implement the policy**

Use exhaustive `when` expressions on `FeatureResolution`; do not inspect
message text.

- [ ] **Step 4: Verify green and commit**

Run the policy tests and commit the new pure unit:

```bash
git add apps/denza-apps/src/main/java/dev/denza/apps/ui/FeatureActionPolicy.kt \
  apps/denza-apps/src/test/java/dev/denza/apps/ui/FeatureActionPolicyTest.kt
git commit -m "feat(ui): map attention states to existing controls"
```

### Task 4: Layout-safe Compose integration

**Files:**
- Modify: `apps/denza-apps/src/main/java/dev/denza/apps/ui/DenzaAppsScreen.kt`
- Modify: `apps/denza-apps/src/main/java/dev/denza/apps/MainActivity.kt`

**Interfaces:**
- Consumes: `FeatureActionPolicy`
- Adds callback routing for the cluster picker without changing repository API.

- [ ] **Step 1: Add the dedicated cluster picker**

Keep picker visibility as local Compose state. Show only non-default,
non-virtual candidates and the automatic option. Do not expose diagnostics.
After a navigation selection, immediately retry the pending projection.

- [ ] **Step 2: Replace the large-card attention row**

When `NEEDS_ACTION` has a message, render one amber full-width row with up to
two lines. Do not also render the normal subtitle/status or old extra message.
Leave `FeatureCard` at `314.dp`.

- [ ] **Step 3: Reuse existing actions**

Use the pure policy to emphasize the current chooser, relabel the current
primary control, disable meaningless controls, and route display selection to
the picker.

- [ ] **Step 4: Adapt compact HUD**

Use its snapshot message as the compact subtitle for terminal attention states.
Add only a temporary inline retry text action when the typed resolution permits
retry; preserve the switch and `96.dp` height.

- [ ] **Step 5: Compile and inspect**

Run:

```bash
./gradlew :denza-apps:compileDebugKotlin :denza-apps:assembleDebug
rg -n 'height\\(314\\.dp\\)|height\\(96\\.dp\\)' \
  apps/denza-apps/src/main/java/dev/denza/apps/ui/DenzaAppsScreen.kt
```

Expected: build succeeds and both fixed heights remain.

- [ ] **Step 6: Commit**

```bash
git add apps/denza-apps/src/main/java/dev/denza/apps/ui/DenzaAppsScreen.kt \
  apps/denza-apps/src/main/java/dev/denza/apps/MainActivity.kt
git commit -m "feat(ui): clarify required actions without card clutter"
```

### Task 5: Documentation and acceptance

**Files:**
- Modify: `docs/project-map.md`
- Modify: `docs/superpowers/specs/2026-07-24-denza-apps-actionable-status-design.md`
- Modify: `docs/superpowers/plans/2026-07-24-denza-apps-actionable-status.md`

- [ ] **Step 1: Update durable product description**

State that attention instructions reuse current controls and keep technical
details hidden. Do not claim car verification.

- [ ] **Step 2: Run full acceptance**

```bash
./gradlew --rerun-tasks \
  :denza-apps:testDebugUnitTest \
  :denza-apps:assembleDebug \
  :denza-apps:assembleRelease \
  :denza-apps:lintDebug
git diff --check
```

Expected: all tests pass, both APK variants assemble, and lint reports no
issues.

- [ ] **Step 3: Review layout and scope**

Confirm:

- no permanent button was added;
- attention replaces existing rows;
- 314 dp and 96 dp heights are unchanged;
- no action routing depends on user-facing strings;
- repository status and worktree are clean after commits.

- [ ] **Step 4: Commit**

```bash
git add docs/project-map.md \
  docs/superpowers/specs/2026-07-24-denza-apps-actionable-status-design.md \
  docs/superpowers/plans/2026-07-24-denza-apps-actionable-status.md
git commit -m "docs(apps): describe actionable status UX"
```
