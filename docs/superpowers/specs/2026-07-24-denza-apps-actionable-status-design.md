# Denza Apps Actionable Status Design

## Goal

Make every user-visible attention state explain one concrete next step without
adding permanent controls or increasing the height of the existing cards.

## Constraints

- The normal Denza Apps layout and control count stay unchanged.
- Technical terms and exception text remain in diagnostics, not card copy.
- `RECOVERING` means the app is actively repairing and needs no tap.
- `NEEDS_ACTION` is used only when a known human action is required.
- A card may repurpose an existing action slot while attention is required.
- The three 314 dp feature cards and three 96 dp compact cards keep their
  current fixed heights.
- Hardware-dependent behavior is not claimed as verified until a later car run.

## State and resolution model

`FeatureSnapshot` gains a nullable typed `FeatureResolution`. It describes how
the observed problem is resolved without dictating that the UI must draw a new
button:

- `SELECT_APPS`
- `SELECT_CLUSTER_DISPLAY`
- `SELECT_NAVIGATION_APP`
- `CONFIRM_ON_CAR`
- `ENABLE_CAR_DEBUGGING`
- `RETRY`

No resolution means that the feature is working, recovering automatically, or
unavailable without a known in-app fix. Reducers clear stale resolutions when a
feature returns to `OFF`, `STARTING`, `READY`, `ACTIVE`, or `RECOVERING`.

## Card presentation

For the three large cards, a `NEEDS_ACTION` snapshot with a non-empty message
replaces the existing subtitle/status row plus muted one-line message. The
replacement is one full-width amber instruction row with a status dot and up
to two lines of 13 sp text. This reuses existing vertical space instead of
making the card taller.

Other states retain the current subtitle/status row. Recovery copy stays a
single muted line under it. `NEEDS_ACTION` uses amber; red is reserved for
`ERROR`.

Compact toggle cards use the snapshot message as their subtitle for
`NEEDS_ACTION`, `UNAVAILABLE`, or `ERROR`. A temporary small retry action may
appear only while a known retry is possible; the switch remains available so
the feature can still be disabled.

## Existing controls

The normal control count does not change.

- Simulcast `SELECT_APPS`: emphasize the existing `Выбрать` control and disable
  `Запустить`.
- Simulcast access failure: the existing primary control becomes `Повторить` or
  `Проверить`.
- Simulcast package absent: show `UNAVAILABLE`; do not offer `Исправить`.
- Mirrors `SELECT_CLUSTER_DISPLAY`: the existing `Проверить камеры` control
  becomes `Выбрать экран` and opens a simple display picker.
- Mirrors display absent: show a neutral unavailable/retry state; the same
  existing control becomes `Повторить поиск`.
- Navigation `SELECT_NAVIGATION_APP`: emphasize the existing `Выбрать` control.
- Navigation display ambiguity: the existing primary control becomes
  `Выбрать экран`.
- Navigation retry or car confirmation: the existing primary control becomes
  `Повторить` or `Проверить`.
- HUD access failure: show the instruction as the compact subtitle and expose
  only a temporary inline retry action.

## Copy

Instructions start with a verb and avoid internal product names:

- `Выберите приложения для трансляции`
- `Выберите приборный экран`
- `Подтвердите запрос на экране автомобиля`
- `Включите отладку USB в настройках автомобиля`
- `Выберите установленный навигатор`

`Simulcast не найден` becomes the non-actionable
`Трансляция недоступна на этой системе`. A timeout is described as
`Система автомобиля не отвечает`; the technical ADB cause remains in
`FeatureSnapshot.details`.

## Verification

Pure unit tests cover resolution propagation, blocker classification, and every
existing-control policy. The Android build and lint verify Compose integration.
The final review checks that card heights remain exactly 314 dp and 96 dp, the
large-card attention path does not render the old extra message row, and no
permanent control was added.

