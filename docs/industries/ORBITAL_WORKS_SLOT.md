# Relic Lattice Work

The Orbital Works industry option "Relic Lattice Work": a ship stays at the colony for 93 days and comes back with one extra S-mod slot, paid with Relic Components. Read it before changing the option, the work timer or the `IndEvo_relicLattice` hullmod. It does not cover vanilla Orbital Works production or other relic sinks; see [Economy](../core/ECONOMY.md#relic-sinks).

Checked against Starsector 0.98a-RC8 and IE `85b9135`. Recheck engine facts after a game update.

## Overview

The option shows on vanilla Orbital Works (`Industries.ORBITALWORKS`) on player owned colonies. It opens a ship picker: ships in local storage, plus fleet ships when the player is docked at the colony. A `HelpIcon` by the heading holds the effect, time and relic components per deployment point; remote, a gray line says to dock for fleet ships. Confirm takes the relics and moves the ship into the work. One ship per colony at a time. The work counts days while Orbital Works is functional; at 93 days the ship gets the permanent hidden hullmod `IndEvo_relicLattice` (+1 S-mod cap) and goes to local storage. With a ship in the work, the same option aborts it (ship to local storage, no refund), and the Orbital Works tooltip shows a "Current Project" section in the Engineering Hub format (heading, hull sprite, ship, time remaining, added slot).

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/orbitalworks/plugins/OrbitalWorksSlotOptionProvider.java` | `BaseSimpleBaseIndustryOptionProvider`: option, option tooltip with the lore text, abort dialog, Orbital Works "Current Project" tooltip section (normal tooltip mode only; vanilla `ListenerUtil.addToIndustryTooltip` places it after the installed items; the tooltip runs on a market clone, so `OrbitalWorksSlotManager.getJob` matches by market id) |
| `indevo/industries/orbitalworks/plugins/OrbitalWorksSlotPanelDelegate.java` | Ship picker dialog (`ButtonReportingDialogueDelegate`), cost (`COST_PER_DP` 20 per hull DP), relic count and payment, start of the work |
| `indevo/industries/orbitalworks/scripts/OrbitalWorksSlotManager.java` | Persistent `NewDayListener`: job list, day count, finish, abort, colony loss, messages; `DAYS_REQUIRED` 93 |
| `indevo/industries/orbitalworks/data/OrbitalWorksSlotJob.java` | Saved job: `market`, `member`, `daysDone` |
| `indevo/industries/orbitalworks/hullmods/RelicLattice.java` | Hullmod plugin: `Stats.MAX_PERMANENT_HULLMODS_MOD` +1 in `applyEffectsBeforeShipCreation` |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Hullmod | `IndEvo_relicLattice` (`RelicLattice.ID`), hidden, tags `no_build_in, hide_in_codex` | `data/hullmods/hull_mods.csv:15` |
| Commodity | `IndEvo_rare_parts` (`ItemIds.RARE_PARTS`) | payment |
| Saved listener | `OrbitalWorksSlotManager` with its `OrbitalWorksSlotJob` list | listener manager |

## Registration and lifecycle

- `OrbitalWorksSlotManager.getInstanceOrRegister()` (`indevo/utils/ModPlugin.java:492`): persistent listener, saved with the sector.
- `OrbitalWorksSlotOptionProvider.register()` (`:493`): transient listener, added on every load.
- `TimeTracker` calls `OrbitalWorksSlotManager.onNewDay()` once per day ([UTILS](../core/UTILS.md)).

## Flow

Option `isSuitable`: Orbital Works, player owned, not under construction. Enabled when functional or a job runs.

No job: `onClick` -> `OrbitalWorksSlotPanelDelegate` -> row select -> Confirm (left of the dialog's Return, placed below the panel in the button row; enabled when the relics cover the cost; the cost line sits right aligned in the lower right of the panel) -> relics from local storage first, then fleet cargo (`RelicHelper.take`) -> fleet ship: officer unassigned, removed from the fleet; storage ship: removed from storage -> `OrbitalWorksSlotManager.start`.

Job: `onClick` -> abort dialog -> `OrbitalWorksSlotManager.abort` -> ship to local storage.

`onNewDay` per job:

| State | Result |
|---|---|
| Colony not player owned or not in the economy | Ship to the nearest player colony storage (hyperspace distance), or the fleet if there is none; message |
| Orbital Works removed | Ship to local storage, work lost; message |
| Orbital Works not functional (disrupted) | Paused |
| Functional | `daysDone + 1`; at 93: variant cloned, `addPermaMod(IndEvo_relicLattice, false)`, `setVariant`, ship to local storage; message |

## Contracts

- Service help states the added slot, duration, ship unavailability and return to local storage. Abort help states that relic components are not refunded.

- The hullmod is a perma mod, not an S-mod: hidden mods never count as S-mods (`Misc.isSpecialMod`, knowledge skill `api/util/Misc.java:5594`). It should show in the ship's installed hullmod list with its description, like `IndEvo_petHullmod` (inferred, not seen in game).
- The S-mod cap read is `Misc.getMaxPermanentMods`, which reads `Stats.MAX_PERMANENT_HULLMODS_MOD` from the member or ship stats (`Misc.java:5523-5536`); same stat as vanilla Best of the Best (`BestOfTheBest.java:45`).
- The picker skips fighter wings, stations, the flagship and ships that already have the hullmod.
- Cost uses hull DP (`ShipHullSpecAPI.getSuppliesToRecover`), at least 1.
- The job holds the `FleetMemberAPI` while the ship is out of fleet and storage, so the ship only exists in the save through the job.

## Known issues and dormant code

| Item | Note |
|---|---|
| Not tested in game | Cap +1 in the refit, picker layout and the Confirm button left of Return, tooltip section, abort, colony loss, vanilla Restore and IE Restoration Docks keeping the hullmod |

## Maintenance

Update this doc with every change to the option, the picker, the job timer or the hullmod. Keep the relic sink row in [ECONOMY.md](../core/ECONOMY.md#relic-sinks) in step.
