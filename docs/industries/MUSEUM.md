# Museum

The Museum industry (`IndEvo_Museum`): ship exhibition storage, exhibition income, parade fleets and the parade management panel. Read it before changing museum income, parades or the museum submarket. It does not cover the removable storage submarket base class; see [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

On player markets the Museum adds a hidden ship storage submarket (`IndEvo_MuseumStorage`). Each distinct hull in it adds monthly income scaled by how far its base value sits above the average for its hull size. Once a month the Museum spawns parade fleets made of clones of the stored ships; a parade flies to a same-faction market in the system, orbits it while that market has the `IndEvo_Parade` condition (stability and growth bonus), and returns. Parade profiles (name, ships, duration, enabled) are configured in a custom panel. NPC Museums do nothing beyond the base industry.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/museum/industry/Museum.java` | Industry plugin. Owns `submarket`, `paradeFleetProfiles`, `maxParades`, `flyParades`, `hullSizeValueMap`, `incomeMult`. Income, value curve, monthly parade spawning, AI cores. `EconomyTickListener` + `MarketImmigrationModifier` |
| `data/MuseumConstants.java` | Static finals read from settings at class load |
| `data/ParadeFleetProfile.java` | One parade slot: presets, current fleet, member ids on parade. Builds and spawns the fleet; `FleetEventListener` resets state on despawn |
| `fleet/ParadeFleetAssignmentAI.java` | `BaseAssignmentAI` script on the parade fleet: prepare, travel, parade (adds and removes the condition), return and despawn. `forceReturnToSource()` for recall |
| `conditions/MuseumParadeCondition.java` | `IndEvo_Parade` market condition: stability and growth bonus |
| `submarket/MuseumSubmarketPlugin.java` | Storage submarket: ships only, hidden from the cargo screen, ships on parade cannot be taken out |
| `plugins/MuseumManageParadeOptionProvider.java` | Industry button "Manage Parades..."; fills profiles up to `getMaxParades()` and opens `ManageParadeDialoguePlugin` |
| `plugins/ManageParadeDialoguePlugin.java` | `InteractionDialogPlugin` host for the panel and the fleet member picker; Escape handling via `EscapeInterceptListener` |
| `plugins/ParadeManagementDialogueDelegate.java` | The custom panel: global on/off, add profile, per profile name, ships, duration (1 to 4 months), recall, enable, delete |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_Museum` (`Ids.MUSEUM`) | `data/campaign/industries.csv:55` |
| Submarket | `IndEvo_MuseumStorage` (`Ids.MUSEUM_SUBMARKET`) | `data/campaign/submarkets.csv:12` |
| Condition | `IndEvo_Parade` (`Ids.COND_PARADE`) | `data/campaign/market_conditions.csv:21` |
| Faction (colour only) | `museumColour` | `data/world/factions/submarkets/museumColour.faction` |
| Names | `data/strings/parade_fleet_names.csv` (48 names, column `id`) | `MuseumConstants.PARADE_FLEET_NAMES` |
| Variant tag | `indEvo_on_parade` on stored ships out on parade | `MuseumConstants.ON_PARADE_TAG` |
| Market tag | `indEvo_on_parade` on the parade target market | `ParadeFleetProfile.spawnFleet()` |
| Fleet tag | `indEvo_parade_returning` after recall | `ParadeFleetAssignmentAI.forceReturnToSource()` |
| Settings | `IndEvo_museum_maxAdditionalCredits` 1950, `_minAdditionalCredits` 50, `_defaultMaxParades` 1, `_defaultParadeMembersMax` 15, `_minShipsForParade` 3, `_topShipPoolAmtForParadeSelection` 20, `_paradeFleetImmigrationBonus` 5, `_paradeFleetStabilityBonus` 1, `_improveExtraParades` 1, `_alphaCoreExtraParades` 1, `_betaCoreIncomePerStability` 20000, `_betaCoreIncomePerPointImmigration` 3000, `_gammaCoreIncomeMult` 1.3, `_uniqueShipValueMult` 2 | `data/config/LunaSettings.csv:178-192`. No `settings.json` fallback |
| Setting | `IndEvo_museum_enabled` | `LunaSettings.csv:102`; read at use time in `Museum.isAvailableToBuild()` and `showWhenUnavailable()` (`Museum.java:134`, `:139`) |
| Setting | `IndEvo_shipPickerRowCount` | ship picker layout |

## Registration and lifecycle

- Industry, submarket and condition plugins from CSV plugin columns.
- `MuseumManageParadeOptionProvider.register()` from `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:507`), transient.
- `Museum.apply()` (player markets only) refreshes the hull value table, adds itself as a persistent listener (`addListener(this)`, `Museum.java:74`) and as a transient immigration modifier, and sets income. `unapply()` removes all three.
- `Museum.advance()` adds the submarket once functional and player owned (`:124`).
- Parade fleets carry the `ParadeFleetAssignmentAI` script and two fleet event listeners (the profile and the AI).

Monthly chain: `Museum.reportEconomyTick(iterIndex == 5) -> fill profiles -> pick enabled idle profiles -> ParadeFleetProfile.spawnFleet -> getNewParadeFleetFromPresetData -> FleetFactoryV3 + cloned members -> ParadeFleetAssignmentAI -> addParadeConditionToMarket -> ... -> GO_TO_LOCATION_AND_DESPAWN -> reportFleetDespawnedToListener -> clear tags and condition`.

## Flow

### Income

- `updateShipValueStatistics()` (`:303`): per hull size, max and average `getBaseValue()` over `MiscIE.getAllLearnableShipHulls()`, ignoring hulls above 2,000,000.
- `getValueForShip()` (`:253`): 1 for `Tags.SHIP_UNIQUE_SIGNATURE` variants; else `(value - avg)/(max - avg)` clamped to 0..1, value x `uniqueShipValueMult` for hulls tagged both `no_sell` and `no_dealer`.
- `getCreditsForValue()` = `(min + max x value) x incomeMult`. `getTotalShipValue()` sums one entry per hull spec (duplicates ignored). Applied as `income.modifyFlat(modId, total, "Exhibition Income")`.

### Parades

- Spawn needs: functional, submarket present, at least `minShipsForParade` stored ships for a random profile, a same-faction market in the location without the parade tag.
- Random profile: top `topShipPoolAmt` stored ships by value not already on parade, pick up to `defaultParadeMembersMax + 1` (loop is `<=`, `ParadeFleetProfile.java:127`). Preset profile: listed member ids not on parade.
- Members are tagged `indEvo_on_parade` in storage; the fleet gets clones (`variant.clone()`, original variant cleared, `TAG_NO_AUTOFIT`). Base fleet: `FleetFactoryV3` `TRADE_SMALL` with 10 freighter and 10 tanker points, quality 1. Fleet flags: ignore player comms, ignored by and ignores other fleets, no auto despawn.
- Assignments (`ParadeFleetAssignmentAI.giveInitialAssignments()`, `:48`): orbit source 7 days, go to target 31, orbit target 31 with start/end scripts adding and removing `IndEvo_Parade`, return and despawn 31.
- Despawn: profile removes the market tag, clears member tags, removes the condition, clears `fleet` (`ParadeFleetProfile.reportFleetDespawnedToListener()`, `:188`).

### AI cores and improvement

| Source | Effect | Where |
|---|---|---|
| Alpha | `maxParades` +`alphaCoreExtraParades` | `applyAlphaCoreModifiers()` |
| Beta | stability +floor(total/20000), growth +floor(total/3000) | `applyBetaCoreModifiers()`, `modifyIncoming()` |
| Gamma | `incomeMult` = 1.3 | `applyGammaCoreModifiers()` |
| Improved | +`improveExtraParades` | `getMaxParades()` |

## Contracts

- `MuseumConstants` values are `static final` and read once at class load, so LunaLib changes need a restart ([Settings](../core/SETTINGS.md)).
- Parade members in the fleet are copies. The real ships never leave storage; the variant tag blocks `PLAYER_BUY` from the submarket (`MuseumSubmarketPlugin.java:28`). Any code that moves museum ships must clear `indEvo_on_parade`.
- `addAssignment(..., onStart, onCompletion)` takes `Script` callbacks (`api/campaign/CampaignFleetAPI.java:261`); here they are lambdas stored on the fleet. Could not verify that the save serializer handles these lambdas.
- `BaseAssignmentAI.advance()` calls `pickNext()` when the fleet has no assignment (`api/impl/campaign/procgen/themes/BaseAssignmentAI.java`); `pickNext()` sends the fleet home.
- Profile state, presets and the active fleet reference persist as fields on the industry.

## Known issues and dormant code

| Item | State |
|---|---|
| Parade duration | `paradeOrbitTimeDays` from the profile is stored but unused; orbit is always 31 days (`ParadeFleetAssignmentAI.java:63-66`) |
| `notifyBeingRemoved()` | Does not remove the submarket; NPEs when `submarket` is null (`Museum.java:341`). `advance()` calls it every frame once the market is no longer player owned (`:127`). Changelog_todo: "museum storage doesn't remove properly", "museum storage gets placed before building complete" |
| Profile growth | `reportEconomyTick()` adds one new profile per month while enabled profiles < max (`:99`), so disabled profiles pile up |
| `getValueForShip()` | Logs on every call (`:267`), including every tooltip row |
| Comment blocks in `Museum.java:40-48`, `:377-379` | Design notes, partly outdated (gamma "3000 max") |
| Changelog_todo | "Add settings to museum" |

## Maintenance

Update this doc when museum income, parade behaviour, settings or the panel change. Shared storage submarket behaviour lives in [Submarkets](../core/SUBMARKETS.md); the Escape blocking helpers in [Pets](PETS.md) and [Side panel](../dialogue/SIDE_PANEL.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
