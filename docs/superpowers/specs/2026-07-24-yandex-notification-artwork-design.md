# Yandex notification maneuver artwork

## Goal

Improve Denza Apps HUD maneuver graphics by using the maneuver drawable that
Yandex Navigator publishes in its active navigation notification. The existing
Accessibility reader remains the source of route text and distances, and the
existing Canvas renderer remains a complete fallback.

The enhancement is optional. Missing notification access, an incompatible
notification layout, an extraction error, or a disabled internal flag must
never reduce the current HUD functionality or turn the HUD card into an
action-required state.

## Scope

This change adds:

- a notification listener limited to `ru.yandex.yandexnavi`;
- public-API extraction from Yandex `RemoteViews`;
- validation and normalization of a maneuver drawable for the stock HUD;
- synchronization between the notification artwork and Accessibility maneuver;
- an internal kill switch enabled by default;
- source and fallback details in support diagnostics;
- unit tests for source selection, freshness, and fallback behavior.

This change does not add:

- a visible notification-access setting or permission warning;
- reflection into `RemoteViews.mActions`;
- OCR, screenshots, or Yandex code injection;
- notification-derived route distance, street, ETA, or lane data;
- changes to the stock SOME/IP HUD contract.

## Sources and ownership

Accessibility remains authoritative for:

- maneuver semantics;
- roundabout exit number;
- distance to maneuver;
- next-road text;
- remaining route distance and time.

The notification listener supplies only optional artwork. `HudGuidance` stays a
semantic model and does not own bitmap bytes. A small process-local artwork
store owns the latest validated notification image and its lifecycle metadata.

## Notification listener

`YandexNotificationArtworkListener` is registered with
`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`. It ignores every
package except `ru.yandex.yandexnavi`.

On listener connection it inspects active Yandex notifications. On a posted or
updated Yandex notification it attempts extraction. On notification removal or
listener disconnection it clears the corresponding cached image.

Failure is local and silent: exceptions are recorded for diagnostics, the
candidate is discarded, and the current Canvas renderer remains available.

## RemoteViews extraction

The extractor uses only public Android APIs:

1. Try `bigContentView`, `contentView`, and `headsUpContentView`.
2. Apply each `RemoteViews` with the Yandex package context.
3. Walk the resulting view hierarchy.
4. Prefer `ImageView` nodes whose resource names identify maneuver or
   next-maneuver artwork.
5. Reject the application icon, tiny status icons, empty drawables, and
   rectangular opaque backgrounds.
6. Render the selected drawable on a transparent bitmap.
7. Trim transparent margins, preserve its aspect ratio, center it in the HUD
   canvas, and normalize visible pixels to the proven white/alpha HUD style.

Known resource names are hints rather than the only acceptance path. A
conservative scorer may accept a future Yandex resource name when its view and
drawable characteristics match a maneuver icon. Ambiguous candidates are
rejected instead of guessed.

Reflection into Android or Yandex internals is deliberately excluded from the
first implementation. It can be researched separately if public `RemoteViews`
does not expose the artwork on the tested firmware.

## Artwork synchronization

The HUD monitor tracks a maneuver identity made from the normalized
Accessibility instruction, parsed maneuver, and roundabout exit number.
Distance-only changes do not create a new identity.

When the identity changes, notification artwork captured before that change is
temporarily ineligible. A newly posted or re-extracted active notification can
be used once its capture time is consistent with the new identity. A small
scheduling tolerance accounts for notification and Accessibility callbacks
arriving in either order.

This policy prevents a previous turn arrow from surviving into the next
maneuver while allowing one notification image to remain valid as the distance
counts down.

## Selection and fallback

Before publishing a HUD update:

1. If the internal notification-artwork flag is off, render the existing
   Canvas icon.
2. Otherwise request an eligible validated notification image.
3. If one is available, publish it in field 8.
4. For every other condition, render the existing Canvas icon.

Unknown semantic maneuvers retain the existing fail-closed behavior. Notification
artwork does not make an otherwise invalid guidance model valid.

The notification path must not delay publishing. Extraction happens on
notification callbacks; HUD publishing performs only a bounded in-memory
selection and byte lookup.

## Internal kill switch

One internal constant owns the feature state and defaults to enabled. The
listener, extractor, and source resolver check the same flag. Switching the
constant off and rebuilding fully removes notification artwork from the runtime
path without changing stored HUD preferences or user-facing UI.

The automatic runtime fallback still applies while the flag is enabled. The
kill switch is for a rapid release rollback if a firmware or Yandex update
reveals an unexpected interaction. A runtime or user-facing toggle is outside
this change.

## User experience

Notification access is an optional enhancement:

- the HUD card remains ready when access is absent;
- enabling HUD guidance continues to configure only the existing Accessibility
  path;
- Denza Apps does not open notification-access settings automatically;
- no yellow or red action state is produced for this optional permission.

Support diagnostics report:

- whether the internal artwork flag is enabled;
- whether the notification listener is connected;
- whether the current source is `notification` or `built-in`;
- the last rejection or extraction failure in compact technical text.

## Tests

Local unit tests cover:

- a fresh validated image is selected;
- no listener or no image selects the built-in renderer;
- disabling the internal flag always selects the built-in renderer;
- distance-only updates keep an eligible image;
- a changed maneuver rejects older artwork;
- a matching post/change timing tolerance accepts callbacks delivered in either
  order;
- notification removal and invalid candidate metadata leave no eligible image;
- ambiguous candidate scoring fails closed.

The Android build verifies the manifest service and platform API usage. Actual
`RemoteViews` contents are vendor application state, so extraction also needs a
live-car smoke test.

## Live verification

With an active Yandex route:

1. Confirm current HUD guidance before granting notification access.
2. Install the new build without granting access and verify the same built-in
   arrows continue to work.
3. Grant notification-listener access and restart the Yandex route if needed.
4. Confirm diagnostics reports `notification` and compare the HUD arrow with
   the Yandex maneuver.
5. Cross at least one normal turn and one roundabout transition, checking that
   an old arrow is never retained.
6. Revoke notification access and verify immediate automatic fallback.
7. Confirm there is no `com.byd.avc` crash and no new crash in Denza Apps or
   Yandex Navigator.
