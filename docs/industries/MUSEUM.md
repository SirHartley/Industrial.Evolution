# Museum

The Museum industry (`IndEvo_Museum`): ship exhibition storage, exhibition income, parade fleets and the parade management panel. Read it before changing museum income, parades or the museum submarket. It does not cover the removable storage submarket base class; see [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

On player markets the Museum adds a hidden ship storage submarket (`IndEvo_MuseumStorage`). Each distinct hull in it adds monthly income scaled by how far its base value sits above the average for its hull size. Once a month the Museum spawns parade fleets made of clones of the stored ships; a parade flies to a same-faction market in the system, orbits it while that market has the `IndEvo_Parade` condition (stability and growth bonus), and returns. Parade profiles (name, ships, duration, enabled) are configured in a custom panel. NPC Museums do nothing beyond the base industry.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/museum/industry/Museum.java` | Industry plugin. Owns `submarket`, `paradeFleetProfiles`, `maxParades`, `flyParades`, `lastParadeCheck`, `hullSizeValueMap`, `incomeMult`. Income, value curve, monthly parade spawning, next parade tooltip, AI cores. `EconomyTickListener` + `MarketImmigrationModifier` |
| `data/MuseumConstants.java` | Fixed constants (submarket colour, parade day options, parade check interval, tags, names CSV) |
| `data/ParadeFleetProfile.java` | One parade slot: presets, current fleet, member ids on parade. Launch checks (`getLaunchBlocker()`), builds and spawns the fleet; `FleetEventListener` resets state on despawn |
| `fleet/ParadeFleetAssignmentAI.java` | `BaseAssignmentAI` script on the parade fleet: prepare, travel, parade (adds and removes the condition), return and despawn. `forceReturnToSource()` for recall |
| `fleet/ParadeConditionScript.java` | Saved `Script` for the parade assignment start and end: adds or removes `IndEvo_Parade` on the target market by id |
| `conditions/MuseumParadeCondition.java` | `IndEvo_Parade` market condition: stability and growth bonus |
| `submarket/MuseumSubmarketPlugin.java` | Storage submarket: ships only, hidden from the cargo screen, ships on parade cannot be taken out |
| `plugins/MuseumManageParadeOptionProvider.java` | Industry button "Manage Parades..."; fills profiles up to `getMaxParades()` and opens `ManageParadeDialoguePlugin` |
| `plugins/ManageParadeDialoguePlugin.java` | `InteractionDialogPlugin` host for the panel and the fleet member picker; Escape handling via `EscapeInterceptListener` |
| `plugins/ParadeManagementDialogueDelegate.java` | The custom panel: global on/off, add profile, per profile name, ships, duration (1 to 4 months, the orbit time at the target, `ParadeFleetAssignmentAI.java:63-69`), recall, enable, delete, and a "Cannot launch" line for an idle profile with a launch blocker |

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
| Settings | `IndEvo_museum_maxAdditionalCredits` 1950, `_minAdditionalCredits` 50, `_defaultMaxParades` 1, `_defaultParadeMembersMax` 15, `_minShipsForParade` 3, `_topShipPoolAmtForParadeSelection` 20, `_paradeFleetImmigrationBonus` 5, `_paradeFleetStabilityBonus` 1, `_improveExtraParades` 1, `_alphaCoreExtraParades` 1, `_betaCoreIncomePerStability` 20000, `_betaCoreIncomePerPointImmigration` 3000, `_gammaCoreIncomeMult` 1.3, `_uniqueShipValueMult` 2 | `data/config/LunaSettings.csv:175-188`; read at use time |
| Setting | `IndEvo_museum_enabled` | `LunaSettings.csv:109`; read at use time in `Museum.isAvailableToBuild()` and `showWhenUnavailable()` (`Museum.java:140`, `:145`) |
| Setting | `IndEvo_shipPickerRowCount` | ship picker layout |

## Registration and lifecycle

- Industry, submarket and condition plugins from CSV plugin columns.
- `MuseumManageParadeOptionProvider.register()` from `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:484`), transient.
- `Museum.apply()` (player markets only) refreshes the hull value table, adds itself as a persistent listener (`addListener(this)`, `Museum.java:77`) and as a transient immigration modifier, and sets income. `unapply()` removes all three.
- `Museum.advance()` adds the submarket through `IndustrySubmarkets.ensure` once functional and player owned (`:133`). `notifyBeingRemoved()` despawns parades, clears the parade tags and calls `IndustrySubmarkets.release`, which moves the stored ships to the market storage and removes the submarket (`:403`); `advance()` also calls it when the market changes owner.
- Parade fleets carry the `ParadeFleetAssignmentAI` script and two fleet event listeners (the profile and the AI).

Monthly chain: `Museum.reportEconomyTick(iterIndex == 5) -> store lastParadeCheck -> fill profiles -> pick enabled idle profiles -> ParadeFleetProfile.spawnFleet -> getNewParadeFleetFromPresetData -> getLaunchBlocker -> FleetFactoryV3 + cloned members -> ParadeFleetAssignmentAI -> addParadeConditionToMarket -> ... -> GO_TO_LOCATION_AND_DESPAWN -> reportFleetDespawnedToListener -> clear tags and condition`.

## Flow

### Income

- `updateShipValueStatistics()` (`:356`): per hull size, max and average `getBaseValue()` over `MiscIE.getAllLearnableShipHulls()`, ignoring hulls above 2,000,000.
- `getValueForShip()` (`:306`): 1 for `Tags.SHIP_UNIQUE_SIGNATURE` variants; else `(value - avg)/(max - avg)` clamped to 0..1, value x `uniqueShipValueMult` for hulls tagged both `no_sell` and `no_dealer`.
- `getCreditsForValue()` = `(min + max x value) x incomeMult`. `getTotalShipValue()` sums one entry per hull spec (duplicates ignored). Applied as `income.modifyFlat(modId, total, "Exhibition Income")`.

### Parades

- Launch checks: `ParadeFleetProfile.getLaunchBlocker()` (`:84`) returns the first failed check as player text, or null: functional, submarket present, a random profile has at least `minShipsForParade` (at least 1) free ships, a preset profile has at least one listed member stored and free, a same-faction market in the location without the parade tag. Free means not tagged `indEvo_on_parade`. `getNewParadeFleetFromPresetData()` calls it first, so a spawn that aborts leaves no ship tagged.
- Random profile: top `topShipPoolAmt` free ships by value, pick up to `defaultParadeMembersMax + 1` (loop is `<=`, `ParadeFleetProfile.java:175`). Preset profile: listed member ids not on parade.
- Members are tagged `indEvo_on_parade` in storage; the fleet gets clones (`variant.clone()`, original variant cleared, `TAG_NO_AUTOFIT`). Base fleet: `FleetFactoryV3` `TRADE_SMALL` with 10 freighter and 10 tanker points, quality 1. Fleet flags: ignore player comms, ignored by and ignores other fleets, no auto despawn.
- Assignments (`ParadeFleetAssignmentAI.giveInitialAssignments()`, `:48`): orbit source 7 days, go to target 31, orbit target for the profile duration (31 to 124 days) with start/end scripts adding and removing `IndEvo_Parade`, return and despawn 31.
- Despawn: profile removes the market tag, clears member tags (also when the target market is gone), removes the condition, clears `fleet` (`ParadeFleetProfile.reportFleetDespawnedToListener()`, `:221`). A fleet on its way home (`ParadeFleetAssignmentAI.isReturning()`) leaves tag and condition alone: it gave the target back when it left, and the next parade can sit there by now. The AI's own despawn listener removes the condition from the target market only (`ParadeFleetAssignmentAI.java:112-119`).

### Launch feedback

- `reportEconomyTick(5)` stores the clock timestamp in `lastParadeCheck` on every tick 5, before the functional check (`Museum.java:96`). Tick 5 of 10 lands about mid month: ticks are spread over the days left in the month (`com.fs.starfarer.campaign.econ.reach.ReachEconomyStepper.nextFrame`, game internals).
- Industry tooltip, Parade Fleets section (`addNextParadeTooltip()`, `:196`): "Parades are turned off" while `flyParades` is false. With a free slot: the distinct blockers of the enabled idle profiles when none can launch (a fresh random profile stands in when no enabled profile is idle, as the next check adds one), else "Next parade in about N days" (`PARADE_CHECK_DAYS` 31 minus days since the last check, at least 1), or "Next parade: mid month" before the first check or when the last check is older than 31 days.
- Manager panel: an idle profile with a blocker gets a "Cannot launch: <reason>" line below Options; its entry grows by `BLOCKER_HEIGHT` (`ParadeManagementDialogueDelegate.java:147`, `:427`). Disabled profiles show it too.

### AI cores and improvement

| Source | Effect | Where |
|---|---|---|
| Alpha | `maxParades` +`alphaCoreExtraParades` | `applyAlphaCoreModifiers()` |
| Beta | stability +floor(total/20000), growth +floor(total/3000) | `applyBetaCoreModifiers()`, `modifyIncoming()` |
| Gamma | `incomeMult` = 1.3 | `applyGammaCoreModifiers()` |
| Improved | +`improveExtraParades` | `getMaxParades()` |

## Contracts

- Museum settings are read at use time: income, AI core and bonus values on the next `apply` or tooltip, parade size and selection when a parade spawns. A parade already out keeps its ships ([Settings](../core/SETTINGS.md)).
- Parade members in the fleet are copies. The real ships never leave storage; the variant tag blocks `PLAYER_BUY` from the submarket (`MuseumSubmarketPlugin.java:28`). Any code that moves museum ships must clear `indEvo_on_parade`.
- `addAssignment(..., onStart, onCompletion)` takes `Script` callbacks (`api/campaign/CampaignFleetAPI.java:261`) that are saved with the assignment. Use `ParadeConditionScript`, not lambdas: lambdas come back from the save as null.
- `BaseAssignmentAI.advance()` calls `pickNext()` when the fleet has no assignment (`api/impl/campaign/procgen/themes/BaseAssignmentAI.java`); `pickNext()` sends the fleet home.
- Profile state, presets and the active fleet reference persist as fields on the industry. `lastParadeCheck` (long) is saved too; 0, also on saves from before it existed, reads as no check yet.
- Launch checks have one home, `ParadeFleetProfile.getLaunchBlocker()`. The spawn path, the panel and the tooltip all use it; add a new spawn condition there.

## Known issues and dormant code

| Item | State |
|---|---|
| Profile growth | `reportEconomyTick()` adds one new profile per month while enabled profiles < max (`:105`), so disabled profiles pile up |
| `getValueForShip()` | Logs on every call (`:320`), including every tooltip row |
| Comment blocks in `Museum.java:42-50`, `:437-439` | Design notes, partly outdated (gamma "3000 max") |
| Next parade estimate | A flat 31 day interval; the real gap between two tick 5s is about 30 to 31 days (28 day months less), so the tooltip says "about" |
| Changelog_todo | "Add settings to museum" |

## Maintenance

Update this doc when museum income, parade behaviour, settings or the panel change. Shared storage submarket behaviour lives in [Submarkets](../core/SUBMARKETS.md); the Escape blocking helpers in [Pets](PETS.md) and [Side panel](../dialogue/SIDE_PANEL.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
