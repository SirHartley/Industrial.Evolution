# Embassy

The Embassy industry (`IndEvo_embassy`) and the ambassador system: hiring ambassadors from NPC markets, transporting them as cargo items, monthly reputation gain, penalties, and the Hostile Activity factor. Read it before changing ambassadors, the embassy whitelist or the ambassador dialogue. It does not cover the generic installable item framework; see [Installables](../items/INSTALLABLES.md). The Requisition Center reading the ambassador faction is in [Small industries](SMALL_INDUSTRIES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

NPC markets of whitelisted factions at size 5+ get an ambassador person (post `ambassador`) in their comm directory. The player hires one through rules dialogue; the ambassador becomes an `IndEvo_amb` special item (`AmbassadorItemData` holding the `PersonAPI`) in fleet cargo and must be installed into a player Embassy within about 2 to 4 months. An installed ambassador raises relations with their faction by 10 per month up to a cap, halves the player's daily reputation losses into extra penalties, and reduces (or, in sabotage mode, increases) that faction's Hostile Activity progress. Losing, selling or spacing the item costs reputation and can spawn a bounty hunter fleet.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/embassy/industry/Embassy.java` | Industry plugin. Owns `ambassadorItemData`, `alignedFaction`, `maxRelation`, `monthsPassed`, beta core penalty. Monthly rep gain. `EconomyTickListener` + `NewDayListener` |
| `indevo/industries/embassy/AmbassadorItemHelper.java` | Rank, post, `$deconomics_ambAllowed_<faction>` key; allowed faction set; item to faction/person |
| `listeners/AmbassadorPersonManager.java` | Ambassador person lifecycle on markets (create, add, remove, delete, return home), daily rep loss tracking, monthly penalty application and report, war and recall checks, `adjustRelationship()` (Nexerelin aware). Nested `checkAmbassadorPresence` (transient campaign listener) and `moveAmbassadorToMarket` (transient one-shot script) |
| `listeners/AmbassadorItemTrackerPlugin.java` | One per ambassador in transit. Daily: locates the item, detects selling or jettison, applies delay penalties at day 62/93/124, returns ambassador home on war, recall or timeout |
| `listeners/LostAmbassadorRemovalChecker.java` | After a lost ambassador: removes them from any Embassy where they reappear |
| `scripts/AmbassadorMurderConsequences.java` | One-shot `EveryFrameScript`: spawns a `MERC_BOUNTY_HUNTER` fleet of the faction when the player lingers 7+ days in a core system |
| `scripts/HAAmbassadorEventFactor.java` | Hostile Activity factor "Political Influence" |
| `scripts/HostileActivityEventSubRegisterListener.java` | `ColonyCrisesSetupListener`: adds the factor at index 0 |
| `scripts/HostileActivityEventSubRegisterScript.java` | Deprecated, unreferenced |
| `rules/IndEvo_isAmbassador`, `IndEvo_ambFactionMismatch`, `IndEvo_noActiveAmbInCargo`, `IndEvo_playerHasFreeEmbassy`, `IndEvo_playerHasFreeEmbassyNoCargo`, `IndEvo_checkForRepResponse` | Rule conditions |
| `rules/IndEvo_addAmbassadorToCargo` | Hire: item into fleet cargo, starts tracker, removes person from the market |
| `rules/IndEvo_ambassadorRemoval` | Dismiss with rep penalty, return person home. `removeAmbassadorWithPenalty()` also called by `Embassy.notifyBeingRemoved()` |
| `rules/IndEvo_displayAmbassadorMoveOption`, `rules/IndEvo_moveAmbassadorToClosestEmbassy` | Relocate to nearest empty Embassy for a credit fee |

Outside the package: `indevo/items/specialitemdata/AmbassadorItemData.java`, `indevo/items/AmbassadorItemPlugin.java`, `indevo/items/installable/AmbassadorInstallableItemPlugin.java` (see [Installables](../items/INSTALLABLES.md)); `indevo/dialogue/rules/IndEvo_notAtWar.java` (ambassador relation <= -0.5 check, see [Rule commands](../dialogue/RULE_COMMANDS.md)).

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_embassy` (`Ids.EMBASSY`) | `data/campaign/industries.csv:28` |
| Special item | `IndEvo_amb` (`ItemIds.AMBASSADOR`), tags `no_drop, hide_in_codex` | `data/campaign/special_items.csv:23` |
| Whitelist | `data/config/indEvo/embassy_whitelist.csv` (35 faction ids), path key `IndEvo_embassyList` (`Ids.EMBASSY_LIST`) | `data/config/settings.json:218` |
| Setting | `IndEvo_embassy` (toggle, also disables ambassador scripts) | `data/config/LunaSettings.csv:92` |
| Sector memory | `$deconomics_ambAllowed_<factionId>`: true adds a faction, false bans and recalls | `AmbassadorItemHelper.java:18` |
| Sector memory | `$IndEvo_HAASabotage` (sabotage mode) | `HAAmbassadorEventFactor.java:26`, set by rules |
| Sector memory | `$IndEvo_HAE_ScriptCleanupDone` | dead script only |
| Person | rank `factionRep`, post `ambassador`, tag `amb_<originMarketId>` | `AmbassadorPersonManager.createAmbassador()` (`:340`) |
| Fleet memory | `$IndEvo_bountyHunter`, `$IndEvo_himOrHer`, `$IndEvo_name`, `$IndEvo_theFaction` | `AmbassadorMurderConsequences.java:146` |
| Rules | `IndEvo_ambassadorGreeting*`, `IndEvo_ambassadorHire*`, `IndEvo_ambassadorNoFreeEmbassy`, `IndEvo_ambassadorConfirm`, `IndEvo_ambassadorMenu`, `IndEvo_ambassadorMenuSabotage` and `IndEvo_ambassadorMenuStopSabotage` (sabotage toggle by `$global.IndEvo_HAASabotage`), `IndEvo_ambassadorRemove1..6`, `IndEvo_ambassadorRemovalConfirm`, `IndEvo_ambMoveOption`, `IndEvo_amb(Stop)SabotageRep(_confirm)`, `IndEvo_convEnd(Polite)`, `IndEvo_bountyHunterHail`, `IndEvo_bountyHunterText` | `data/campaign/rules.csv` block `# Embassy`, `# Ambassador Murdered` |
| Strings | `IndEvo_embassy`, `IndEvo_AmbassadorItem` sections | `data/strings/strings.json:171`, `:69` |
| Sprites | `IndEvo/amb_pers`, `amb_heg`, `amb_tt`, `amb_church`, `amb_sind`, `amb_mod`, `IndEvo/reputation` | `data/config/settings.json:330-335`, picked in `Embassy.getCurrentImage()` |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded()`: `AmbassadorPersonManager.register()` (`ModPlugin.java:455`, transient, no dedupe needed since transient listeners are dropped on save), `checkAmbassadorPresence.register()` (`:461`, `addTransientListener`), `HostileActivityEventSubRegisterListener.register()` (`:502`, transient).
- `ModPlugin.loadTransientMemory()` warms the whitelist cache (`ModPlugin.java:436`).
- `Embassy.apply()` adds itself as a transient listener only when functional and player owned (`Embassy.java:61`).
- `AmbassadorItemTrackerPlugin` and `LostAmbassadorRemovalChecker` are persistent listeners (`addListener(obj)`), removed by `setDone()`. `AmbassadorMurderConsequences` is a persistent sector script; `moveAmbassadorToMarket` is a transient script.
- Rule commands from `ruleCommandPackages` `indevo.industries.embassy.rules` (`settings.json:239`).

## Flow

### Ambassador on NPC markets

`checkAmbassadorPresence.reportPlayerOpenedMarket -> ambassadorPresenceAllowedCheck -> createAmbassadorsOnMarkets`. A market gets one ambassador if its faction is in `getAllowedFactionID()` (whitelist plus `$deconomics_ambAllowed_*` true keys), size >= 5, not player owned, not banned. Faction mismatch, size < 5 or a ban deletes it. Player markets lose stray ambassadors with no Embassy or no item.

### Hire and transport

1. Greeting `IndEvo_ambassadorGreeting` (`IndEvo_isAmbassador`, `IndEvo_notAtWar`, not player market).
2. Hire needs `IndEvo_noActiveAmbInCargo` (no tracker for this faction) and `IndEvo_playerHasFreeEmbassy` (free embassies, those without an installed ambassador, must outnumber ambassadors in transit, counted by `AmbassadorItemTrackerPlugin` listeners).
3. `IndEvo_addAmbassadorToCargo`: new `AmbassadorItemData(IndEvo_amb, null, person)` in fleet cargo, new `AmbassadorItemTrackerPlugin`, person removed from the market (still in `ImportantPeople`).
4. Install through the industry item menu (`Embassy.getInstallableItems()`, `wantsToUseSpecialItem()`). The tracker sees the item on an Embassy with no ambassador person (`getEmptyEmbassyMarket()`), adds the person there and finishes.

Tracker daily order (`AmbassadorItemTrackerPlugin.onNewDay()`, `:47`): `findAmbassadorItem` (last location, fleet, installed, station cargo, storage submarkets) `-> checkForIllegalPresence` (any non-storage submarket = sold; any entity cargo in any location = suffocated; both -30 rep, murder script; else lost) `-> checkForWarConsequences` (faction gone, relation <= -0.5, or banned: item removed, person home) `-> checkForTimeConsequences` (day 31 warning, 62 -10, 93 -15, 124 -20 and return home).

### Installed ambassador

- `Embassy.reportEconomyTick()` last iteration: `monthlyRepIncrease()` adds min(0.1, cap - current) up to `maxRelation` (0.40, beta core 0.55) and reports it to `AmbassadorPersonManager`. `monthsPassed` counts to 3; relocation unlocks at 3.
- `AmbassadorPersonManager.onNewDay()`: `logRepChange()` records `ceil(delta x mult x 0.5)` of any daily rep drop as an extra penalty per embassy faction (alpha core embassy counts 0.5 instead of 1); `getWarConsequences()` removes ambassadors of dead factions, sends home ambassadors of hostile factions, removes banned ones.
- `reportEconomyMonthEnd()`: applies the summed penalties and shows one rep change `MessageIntel`.
- Dismiss: `IndEvo_ambassadorRemove1..6` pick text by relation band (`IndEvo_checkForRepResponse.getRepBand`: below -25 inhospitable, below -10 suspicious, up to 10 neutral, up to 25 favorable, up to 50 welcoming, above friendly), `IndEvo_ambassadorRemoval` applies `getPenaltyFor()` (-10 to -60) and returns the person to the origin market.
- Relocate: option order from param, cost `5000 x lyMult` booked as Embassy upkeep, move done by `moveAmbassadorToMarket` next frame.
- Removing the Embassy (not for upgrade) runs `removeAmbassadorWithPenalty()` (`Embassy.java:316`). Removing a beta core after it was seen at month end costs -20 (`onNewDay()`, `:129`).

### Hostile Activity

`HostileActivityEventIntel` setup ends with `ListenerUtil.finishedAddingCrisisFactors()` (`api/impl/campaign/intel/events/HostileActivityEventIntel.java:173`), which calls `HostileActivityEventSubRegisterListener`. `HAAmbassadorEventFactor.getReductionAmtForFaction()` (`:120`) finds the faction's `BaseHostileActivityFactor` by matching `getNameForThreatList(false)` to the faction display name (Luddic Church matches `LuddicChurchHostileActivityFactor`, listed as "Knights of Ludd"), then returns `progress x min(rel, 0.4)/0.4 x 0.4`. The tooltip shows the counted relation as `[min(rel, 40)/40]` (`HAAmbassadorEventFactor.java:59`). `getProgress()` sums this for all installed ambassadors, negative normally, positive with `$IndEvo_HAASabotage`.

### AI cores

| Core | Effect |
|---|---|
| Alpha | daily rep loss penalty mult 0.5 for that embassy |
| Beta | rep cap 0.55; removal later costs -20 |
| Gamma | upkeep x0.9 |

## Contracts

- The ambassador item is matched by `AmbassadorItemData.equals()` (id, data and `PersonAPI`), so `CargoAPI.getQuantity(SPECIAL, data)` finds the exact person (`indevo/items/specialitemdata/AmbassadorItemData.java:33`).
- `getAmbassador(market)` scans the comm directory for post `ambassador` (`AmbassadorPersonManager.java:290`). Any other code that reads the ambassador relies on this.
- `AmbassadorPersonManager` is transient: `lastDayRep` and the monthly penalty and gain maps reset on every load (inferred from `ListenerManager.transientListeners`, `obf/campaign/ListenerManager.java:11`).
- `getAllowedFactionID()` adds memory-allowed factions into the cached whitelist set itself, so they stay allowed for the session even after the key turns false (cache in `MiscIE.getCSVSetFromMemory()`, `indevo/utils/helper/MiscIE.java:578`).
- With Nexerelin enabled, relation changes go through `DiplomacyManager.adjustRelations()` (`AmbassadorPersonManager.java:111`).
- Users outside the package: `RequisitionCenter`, `RequisitionsCenterSubmarketPlugin`, `JuicyRumorsSpecial`, `IndEvo_SalvageSpecialAssigner` call `getAmbassador`, `hasAmbassador`, `getListOfIncativeFactions` or `adjustRelationship`. Keep these signatures.

## Known issues and dormant code

| Item | State |
|---|---|
| `HostileActivityEventSubRegisterScript` | `@Deprecated`, not registered anywhere: dead |
| Blank rules row after `#END IndEvo PlayerMarketAmbassadorDialogue` | Row with empty id and script `$global.IndEvo_HAASabotage = true` (`rules.csv:681-683`) |
| `moveAmbassadorToClosestEmbassy` | Fee is booked even if the move script later fails its checks |
| Changelog_todo | "embassy rep limit"; "embassy rep impact rolling number -> fixed num"; "Add ambassadors as contacts" |

## Maintenance

Update this doc when ambassador rules, penalties, the whitelist format or the HA factor change. Whitelist loading lives in [Settings](../core/SETTINGS.md); item plugins in [Installables](../items/INSTALLABLES.md); shared rule conditions in [Rule commands](../dialogue/RULE_COMMANDS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
