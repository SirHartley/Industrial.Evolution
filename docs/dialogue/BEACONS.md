# Warning beacon dismantling

Covers the "Dismantle the warning beacon" option on warning beacons and the two rule commands behind it. Read it when the beacon dialog or beacon removal misbehaves. It does not cover placing IE beacons with the consumable beacon item; see [Consumables](../items/CONSUMABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Any warning beacon (vanilla or IE) in hyperspace gets an extra option to dismantle it. Confirming removes the beacon entity with a fade and deletes the vanilla `WarningBeaconIntel` that points at it. IE placed beacons (`IndEvo_warning_beacon`) also get their own opening text via rules.

## Files

| File | Owner / role |
|---|---|
| `indevo/dialogue/beacons/dialogue/BeaconDialogueListener.java` | Transient `BaseCampaignEventListener`. On `reportShownInteractionDialog`, for targets tagged `warning_beacon` in hyperspace, inserts option `IndEvo_beacon_destroyConfirm` before `beaconLeave` (`:20`). |
| `indevo/dialogue/beacons/dialogue/IndEvo_RemoveCurrentInteractionTarget.java` | Rule command: removes matching `WarningBeaconIntel`, then `Misc.fadeAndExpire(target, 1f)` (`:19`). |
| `indevo/dialogue/beacons/dialogue/IndEvo_IsInHyperspace.java` | Rule condition: interaction target is in hyperspace. No rule uses it. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Custom entity | `IndEvo_warning_beacon` (tags `warning_beacon`, `IndEvo_beacon`) | `data/config/custom_entities.json:333` |
| Tag | `warning_beacon` (`Tags.WARNING_BEACON`) | `api/impl/campaign/ids/Tags.java:380` |
| Option ids | `IndEvo_beacon_destroyConfirm`, `IndEvo_beacon_destroyLeave`, vanilla `beaconLeave` | listener, rules |
| Rules | `IndEvo_beacon_OpenDialogCustom` (`:257`), `IndEvo_beacon_destroyConfirm` (`:261`), `IndEvo_beacon_destroyLeave` (`:263`) | `data/campaign/rules.csv` "#Beacons" block, lines 256 to 264 |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded` -> `BeaconDialogueListener.register()` (`ModPlugin.java:486`): `addTransientListener`, so one instance per load and nothing saved.
- Rule commands resolve from `ruleCommandPackages` entry `indevo.dialogue.beacons.dialogue` (`data/config/settings.json:249`).

Order: `OpenInteractionDialog rules -> dialog shown -> BeaconDialogueListener adds option -> IndEvo_beacon_destroyConfirm -> IndEvo_beacon_destroyLeave -> IndEvo_RemoveCurrentInteractionTarget + DismissDialog`.

## Flow

1. `IndEvo_beacon_OpenDialogCustom` (`$tag:warning_beacon`, `$tag:IndEvo_beacon`) shows "placed here by your fleet" with `beaconLeave`. Vanilla beacons use vanilla rules.
2. The listener removes `beaconLeave` if present, adds "Dismantle the warning beacon", re-adds "Leave" (`BeaconDialogueListener.java:28`).
3. `IndEvo_beacon_destroyConfirm` asks for confirmation (Confirm -> `IndEvo_beacon_destroyLeave`).
4. `IndEvo_beacon_destroyLeave` runs `IndEvo_RemoveCurrentInteractionTarget` then `DismissDialog`.

## Contracts

- `reportShowInteractionDialog` is sent after the dialog is created and shown (`obf/campaign/CampaignState.java:2316`); the listener relies on the opening rules having added `beaconLeave` by then (inferred from the call order, not traced into the dialog constructor).
- The in-system check is only `isInHyperspace()`; beacons inside star systems never get the option.
- Intel removal matches `WarningBeaconIntel.getMapLocation(null) == target`; vanilla returns the beacon itself there (`api/impl/campaign/intel/misc/WarningBeaconIntel.java:175`, hyperspace remap branch disabled with `&& false`).
- `IndEvo_RemoveCurrentInteractionTarget` fades any target, not only beacons; keep it on beacon rules only.

## Known issues and dormant code

| Item | State |
|---|---|
| `IndEvo_IsInHyperspace` | unused rule command |

## Maintenance

Update this doc when beacon rules, the option ids or the removal command change. Beacon placement and the beacon entity plugin belong in [Consumables](../items/CONSUMABLES.md). Rule command lookup is in [Rule commands](RULE_COMMANDS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
