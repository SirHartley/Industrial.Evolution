# Automated Shipyard (gacha station)

The Automated Shipyard exploration station: placement, repair, the ship and component sacrifice that returns a random hull, its storage and intel. Read it before touching `indevo/exploration/gacha/`. It does not cover deliveries to the station; see [Courier Port](../industries/COURIER_PORT.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Derelict stations (`IndEvo_GachaStation`, display name "Automated Shipyard") orbit stars in uninhabited systems, preferring red giants. The player repairs one with metals, heavy machinery and ship components, then can sacrifice ships and ship components plus 100 relic components to get one random learnable hull whose deployment points are just under the offered total. The station has an abandoned station market with player storage.

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/gacha/GachaStationSpawner.java` | New game placement, count from coronal taps. Owns `$IndEvo_hasPlacedStations` |
| `indevo/exploration/gacha/GachaStationCampaignPlugin.java` | `BaseCampaignPlugin`: picks `GachaStationDialoguePlugin` for entities tagged `IndEvo_GachaStation` at `MOD_SPECIFIC` |
| `indevo/exploration/gacha/GachaStationDialoguePlugin.java` | Whole interaction: repair, ship picker, component selector, roll, hull creation. Owns `$IndEvo_stationRepaired`, the costs and `$IndEvo_randomSeed` |
| `indevo/exploration/gacha/rules/OpenStorageTabOnGachaStation.java` | Rule command: opens the core UI (`CoreUITabId.FLEET`, trade mode `OPEN`) on the station, returns to the dialog on close |
| `indevo/exploration/gacha/intel/GachaStationIntel.java` | `MapMarkerIntel`: active/inactive bullet, repair cost when inactive; one per station, added on first visit |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Custom entity | `IndEvo_GachaStation` (tag `IndEvo_GachaStation`, no plugin class) | `data/config/custom_entities.json:860` |
| Constant | `Ids.GACHA_STATION` = `IndEvo_GachaStation` | `indevo/ids/Ids.java:129` |
| Description | `IndEvo_GachaStation` | `data/strings/descriptions.csv:23` |
| Memory key | `$IndEvo_stationRepaired` (`HAS_RESTORED_STATION`, entity) | `GachaStationDialoguePlugin.java:40` |
| Memory key | `$IndEvo_randomSeed` (`RANDOM`, entity, a `java.util.Random` seeded with the entity hash) | `GachaStationDialoguePlugin.java:51`, set in `GachaStationSpawner.java:65` |
| Persistent data | `$IndEvo_hasPlacedStations` | `GachaStationSpawner.java:22` |
| Setting | `IndEvo_AutomatedShipyardNum` (Double, default 1.5, multiplier on coronal tap count) | `data/config/LunaSettings.csv:12`, `data/config/settings.json:49`, read in `GachaStationSpawner.java:23` |
| Setting | `IndEvo_shipPickerRowCount` | `LunaSettings.csv:7`, read in `GachaStationDialoguePlugin.java:408` |
| Rules | `IndEvo_GachaStationCargoOpen` -> `OpenStorageTabOnGachaStation` | `data/campaign/rules.csv:266` |
| Rule command package | `indevo.exploration.gacha.rules` | `data/config/settings.json:248` |
| Sprite | `IndEvo/log_gachastation` (intel icon, `graphics/icons/intel/IndEvo_gachastation.png`) | `data/config/settings.json:368` |
| Music | `IndEvo_Haplogynae_derelict_theme` on entity and repaired market | `GachaStationSpawner.java:66`, `GachaStationDialoguePlugin.java:208` |

Tuning constants (`GachaStationDialoguePlugin.java:41-49`): repair `METALS_REPAIR_COST` 10000, `MACHINERY_REPAIR_COST` 1000, `PARTS_REPAIR_COST` 4000; `RARE_PART_COST_AMT` 100 per roll; `PARTS_PER_DP` 100; `AUTOMATED_CHANCE` 0.1.

## Registration and lifecycle

- Placement: `ModPlugin.onNewGameAfterEconomyLoad() -> GachaStationSpawner.spawn()` (`indevo/utils/ModPlugin.java:307`). New games only; existing saves never get stations.
- Dialog: `ModPlugin.onGameLoad() -> setScriptsIfNeeded() -> registerPlugin(new GachaStationCampaignPlugin())` (`ModPlugin.java:506`). `BaseCampaignPlugin.isTransient()` returns true, so it is stripped from saves and re-added each load (starsector-knowledge `api/campaign/BaseCampaignPlugin.java:35`, `api/campaign/CampaignPlugin.java:76-86`).
- Market: `Misc.setAbandonedStationMarket(id, station)` at spawn gives a size 0 market with paid-unlocked storage (starsector-knowledge `api/util/Misc.java:4177`).
- Intel: `new GachaStationIntel(...)` in `displayDefaultOptions()` if none exists for the entity; `MapMarkerIntel.init()` adds it to the intel manager (`api/impl/campaign/intel/misc/MapMarkerIntel.java:85`).

## Flow

### Placement

`GachaStationSpawner.spawn()` (`:25`): count = `ceil(entitiesWithTag(CORONAL_TAP) * IndEvo_AutomatedShipyardNum)`. Candidate systems have no market, no coronal tap and a star; weight 100 `star_red_supergiant`, 50 `star_red_giant`, 10 `star_red_dwarf`, 0.01 anything else. `placeStation()` orbits the star at `radius + 200`, period 31 days.

### Repair

Not repaired: pre-restore text and cost; option enabled with enough metals, machinery and components in fleet cargo (always in dev mode). `INITIAL_PAY_TO_RESTORE` removes the goods (not in dev mode), sets `$IndEvo_stationRepaired` and calls `setAbandonedStationMarket` again (`:189-210`).

### Roll

Repaired menu (`displayDefaultOptions`, `:110-152`): cargo areas (`FireBest IndEvo_GachaStationCargoOpen`), ship picker (fleet plus storage mothballed ships, excluding `SHIP_CAN_NOT_SCUTTLE`, needs more than one ship total), component selector (fleet plus storage), start (needs a sacrifice and 100 relic components in fleet plus storage).

`cycleMember()` (`:297`):
1. Sum DP of sacrificed ships, strip their fittings and AI core captains to fleet cargo (`MiscIE.stripShipToCargoAndReturnVariant`), remove them from fleet or storage. Add `round(parts / 100)`.
2. `createHullIdSelection()`: one random hull per size (frigate to capital) from `MiscIE.getAllLearnableShipHulls()`, picked with the station's saved `Random`, valued at empty variant DP.
3. Best = highest DP strictly below the total. Components and relic components are taken from storage first, then cargo.
4. `createAndPrepareMember(best, 3)`: random variant of the hull, `0..2` minus existing d-mods added, 10% chance of `automated` hullmod plus `TAG_AUTOMATED_NO_PENALTY` (always in dev mode), `FleetEncounterContext.prepareShipForRecovery` with retain `1/3`. Added to the player fleet.
5. No hull below the total: everything is consumed, nothing returned.

## Contracts

- The station `Random` lives in entity memory and is saved, so rolls are deterministic per station and save history.
- Courier deliveries: `ShippingTargetHelper` lists repaired stations (`indevo/industries/courierport/ShippingTargetHelper.java:56-57`), `ShippingContract` resolves the market by tag (`ShippingContract.java:93`). Renaming the tag or `HAS_RESTORED_STATION` breaks the [Courier Port](../industries/COURIER_PORT.md).
- `AMOUNT_MULT` is a `static final` read at class init (`GachaStationSpawner.java:23`).

## Known issues and dormant code

| Item | State |
|---|---|
| `GachaStationSpawner.spawn()` | `pickAndRemove()` returns null when fewer candidate systems than stations; `placeStation(null)` would NPE (`:54-56`, inferred) |
| Repair re-creates the market | Second `setAbandonedStationMarket` replaces the market and its storage (`:207`); anything in storage before repair is lost (inferred) |
| `GachaStationSpawner.placeStation()` | Local `id` used as market id, entity gets a second `genUID` (`:62-63`); harmless |

## Maintenance

Update this doc when costs, hull selection, placement or the station id or memory keys change. Courier delivery rules belong in [Courier Port](../industries/COURIER_PORT.md), shared helpers in [Utils](../core/UTILS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
