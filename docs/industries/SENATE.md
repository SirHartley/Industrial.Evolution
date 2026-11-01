# Senate

The Senate industry (`IndEvo_senate`) and Edicts: market conditions the player enacts on own colonies, their runtime and removal penalties, and the Edict dialogue. Read it before adding or changing an Edict or the Senate menu. It does not cover the Neuroconditioning Compounds item effect itself; see [Installables](../items/INSTALLABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

A Senate allows one Edict per player colony in its star system (one Senate per system per faction). With Neuroconditioning Compounds (`IndEvo_neurals`) installed, a Senate also allows Edicts on player colonies within 12 LY and halves removal unrest. An Edict is a market condition (`IndEvo_edict_*`) with a minimum runtime (default 186 days); removing it earlier adds unrest equal to the remaining months. Some Edicts end themselves and apply a finish penalty. Edicts are enacted and removed through rules.csv menus.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/senate/industry/Senate.java` | Industry plugin: build limit, AI core effects, `EDICT_RUNTIME_DAY_RED` 31, `getNearestSenateWithItem()`. Nested `SenateFactor` (`ColonyOtherFactorsListener`, colony info text) |
| `conditions/EdictAPI.java` | Interface used by the dialogue: flavour, effect, runtime and penalty text, `isPresenceConditionMet`, removal |
| `conditions/BaseEdict.java` | Base condition: runtime tracking via market time tags, daily presence check, `removeWithPenalty`/`removeWithoutPenalty`, `senateWithItemInRange`, beta core runtime reduction. `NewDayListener` |
| `conditions/Edict_*.java` (12 active) | One class per Edict, see table below |
| `conditions/Edict_GrowthReinvestment.java` | `@Deprecated`, no CSV row: dead |
| `plugins/SenateOptionProvider.java` | Industry "Visit the Senate..." button (player markets only), opens `RuleBasedInteractionDialogPluginImpl("IndEvo_FireBaseSenateOptionList")` |
| `rules/IndEvo_EdictVariables.java` | Memory keys and constants |
| `rules/IndEvo_CreateEdictSelectionList.java` | All condition specs whose id contains `IndEvo_edict`, cached in `SessionTransientMemory` key `$IndEvo_EdictKey`, paged by 4 |
| `rules/IndEvo_DesignateSubListPageToLoad_Edict.java`, `rules/IndEvo_DisplaySubListPage_Edict.java` | Paging and option list. Builds a plugin on a dummy market per Edict to read name, short desc and presence check |
| `rules/IndEvo_OptionStartsWith_Edict.java` | Stores the picked id in `$IndEvo_selectedEdictID` |
| `rules/IndEvo_DisplayActionPanel_Edict.java` | Edict description panel |
| `rules/IndEvo_applyEdict.java`, `rules/IndEvo_removeEdict.java` | `market.addCondition(id)`; `BaseEdict.removeWithPenalty()` |
| `rules/IndEvo_marketHasEdict.java`, `rules/IndEvo_systemHasSenate.java` | Rule conditions |
| `rules/IndEvo_PrintActiveEdictInfo.java`, `rules/IndEvo_PrintPenaltyText.java` | Removal screen text |

### Edicts

| Condition id | Class | Requires (besides Senate presence) | Effect | Runtime / end |
|---|---|---|---|---|
| `IndEvo_edict_forced_reloc` | `Edict_ForcedRelocation` | military, unique in system, 2+ colonies, size 4 to max-1 | stability -3 in system, growth -10 x size here, + ceil(size / others x 10) elsewhere | auto-ends at min runtime or max size, +3 unrest |
| `IndEvo_edict_ind_incent` | `Edict_Incentives` | none | industries supply and demand +1, upkeep x2 | 186 days |
| `IndEvo_edict_labour_rest` | `Edict_LabourRestrictions` | not free port | stability +4, industries supply and demand -1 | 186 days |
| `IndEvo_edict_cleanse` | `Edict_Cleanse` | military, a condition named "...ubpopulation" | income x0.7, stability -(size-1), growth -size | size x 31 days, then removes subpopulation conditions, unrest ceil(size/3) |
| `IndEvo_edict_lockdown` | `Edict_Lockdown` | military | stability +5, fleet spawn rate x2, fleet size +1 flat, ground defense x3, all supply and non-military demand x0.3 | 93 days, auto-ends, +1 unrest; early end +3 extra |
| `IndEvo_edict_customs` | `Edict_Customs` | Megaport, unique in system, 2+ colonies | accessibility +5% per other colony, -7% on the others | 186 days |
| `IndEvo_edict_conscription` | `Edict_Conscription` | military | upkeep x0.4, growth -2 x size, stability -4 dropping 1 per month (tag `edict_stab_penalty<n>`) | 186 days |
| `IndEvo_edict_deadlines` | `Edict_Deadlines` | ship hull supply | ship supply +30% per industry, production quality -0.2 on all player markets | 186 days |
| `IndEvo_edict_clearances` | `Edict_Clearances` | ship hull supply | ship supply -30% (if > 1), production quality +0.2 on all player markets | 186 days |
| `IndEvo_edict_lockColonySize` | `Edict_LockColonySize` | none | population weight pinned to the current size weight | 186 days |
| `IndEvo_edict_open_market` | `Edict_OpenMarket` | none | adds an open market submarket (if no Commerce), stability -2; on removal takes the open market away only if there is no Commerce | 186 days |
| `IndEvo_edict_fastBuild` | `Edict_FastBuild` | none | building and upgrade time halved via reflection on `BaseIndustry.buildTime`, supply and demand x0.5 | 186 days |

Rows: `data/campaign/market_conditions.csv:2-14`. Military presence is `MiscIE.marketHasMilitaryIncludeRelays()`.

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_senate` (`Ids.SENATE`) | `data/campaign/industries.csv:31` |
| Conditions | `IndEvo_edict_*` (12) | `data/campaign/market_conditions.csv` |
| Item | `IndEvo_neurals` (Neuroconditioning Compounds) | `data/campaign/special_items.csv:41`, effect in `SpecialItemEffectsRepo` |
| Constants | `RANGE_LY_TWELVE` 12, `NEURAL_COMPOUNDS_UNREST_RED` 0.5 | `indevo/items/installable/SpecialItemEffectsRepo.java:57-58` |
| Setting | `IndEvo_senate` (toggle) | `data/config/LunaSettings.csv:93`, `Senate.isAvailableToBuild()` |
| Market tags | `<conditionModId><days>` runtime counter; `edict_stab_penalty<n>` | `TimeTracker`, `Edict_Conscription` |
| Market memory | `$IndEvo_WeightMemory` (saved population weight) | `Edict_LockColonySize.java:13` |
| Dialogue memory | `$IndEvo_EdictList`, `$IndEvo_EdictSubListPage`, `$IndEvo_selectedEdictID` (local, expire 0); option prefix `IndEvo_SelectEdict_` | `IndEvo_EdictVariables` |
| Rules | `IndEvo_FireBaseSenateOptionList`, `IndEvo_SenateOptions*`, `IndEvo_NoEdictMenuPoint(2)(_fb)`, `IndEvo_HasEdictMenuPoint(2)(_fb)`, `IndEvo_chooseEdict`, `IndEvo_removeEdict`, `IndEvo_EdictRemovalConfirm`, `IndEvo_ApplyEdict`, `IndEvo_SenateOptionLeave*`, `IndEvo_EdictSelector_*`, `IndEvo_EdictWasSelected` | `data/campaign/rules.csv` block `# Senate` |

## Registration and lifecycle

- Industry and condition plugins from CSV plugin columns; rule commands from `ruleCommandPackages` `indevo.industries.senate.rules` (`data/config/settings.json:240`).
- `SenateOptionProvider.register()` from `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:484`). `Senate.SenateFactor` is registered by `SpecialItemEffectsRepo.initEffectListeners()` (`SpecialItemEffectsRepo.java:43`), called at `ModPlugin.java:442`.
- `BaseEdict.apply()` adds the condition as a transient listener (deduped) and a `TimeTracker` market tag tracker; skips both on markets without a primary entity and in the codex (`BaseEdict.java:36`). `TimeTracker` increments the tag daily and calls `onNewDay()`.

Chain: `IndEvo_ApplyEdict -> IndEvo_applyEdict -> market.addCondition -> BaseEdict.apply -> TimeTracker tag -> onNewDay (presence check, auto end) -> removeWithPenalty/removeWithoutPenalty -> market.removeSpecificCondition`.

## Flow

### Menu

- Market menu (`IndEvo_NoEdictMenuPoint` / `IndEvo_HasEdictMenuPoint`, player owned, `IndEvo_systemHasSenate`) or the colony option point or the Senate button (`IndEvo_SenateOptions`).
- `IndEvo_systemHasSenate`: player owned and (Senate with item within 12 LY or a Senate in the system) (`IndEvo_systemHasSenate.java:23`).
- `IndEvo_chooseEdict -> IndEvo_CreateEdictSelectionList -> IndEvo_DisplaySubListPage_Edict`. Options whose `isPresenceConditionMet()` fails are disabled with `getUnavailableReason()`.
- `IndEvo_EdictWasSelected -> IndEvo_DisplayActionPanel_Edict` (adds and removes the condition on the real market to get a plugin) `-> IndEvo_ApplyEdict`.
- Removal: `IndEvo_removeEdict` shows active info and `printRemovalPenaltyText()`, then `IndEvo_EdictRemovalConfirm -> IndEvo_removeEdict` command.

### Runtime and penalties

- `daysPassed` = time tag value (`BaseEdict.java:44`, `:59`). `getMinRuntime()` = 186, -31 if the system Senate has a beta core. `Edict_Lockdown` and `Edict_Cleanse` override it.
- Daily: if `isPresenceConditionMet()` fails, `removeWithPenalty()` (`:61`).
- `removeWithPenalty()`: `applyFinishPenalty()`, then if runtime not passed, `RecentUnrest +ceil(remainingDays/31 x itemRed)`, where `itemRed` is 0.5 with a Compounds Senate in range (`getRemovalPenaltyUnrestDays()`, `:169`).

### Senate AI cores

| Core | Effect |
|---|---|
| Alpha | stability +1 on the Senate market |
| Beta | Edict minimum runtime -31 days |
| Gamma | upkeep x0.9 |

## Contracts

- Edict ids must contain `IndEvo_edict` to appear in the menu (`IndEvo_EdictVariables.EDICT_IDENT_STR`), and the plugin must implement `EdictAPI` (the dialogue casts). Use `BaseEdict` and guard `apply`/`unapply` with `market.getPrimaryEntity() == null` for the dummy market used by the list.
- `Market.addCondition()` calls `plugin.apply()` immediately and `removeCondition()` calls `unapply()` (`obf/campaign/econ/Market.java:1088`, `:1160`). The preview in `IndEvo_DisplayActionPanel_Edict` therefore runs a full apply/unapply on the live market and leaves the time tag (and for Forced Labour the `edict_stab_penalty4` tag) behind.
- `IndEvo_marketHasEdict` is the "one Edict per market" rule; nothing in Java enforces it.
- Production quality changes from Deadlines and Clearances go on every player market, not only the local one.
- `Edict_FastBuild` writes `BaseIndustry.buildTime` (`api/impl/campaign/econ/impl/BaseIndustry.java:103`) through `ReflectionUtils.setWithSuper` ([Utils](../core/UTILS.md)). `unapply()` restores the spec build time, or the upgrade spec build time while upgrading, on every building industry.

## Known issues and dormant code

| Item | State |
|---|---|
| `Edict_GrowthReinvestment` | `@Deprecated`, no `market_conditions.csv` row: dead |
| `Edict_LockColonySize.unapply()` | No primary entity guard: sets weight 0 on the dummy market (`:28`) |
| Stale class comments | `Edict_LabourRestrictions` and `Edict_OpenMarket` carry the "Substance Legalization" design note |
| Changelog_todo | "Rework Edict UI"; "Add a proper edict selection UI, for remote operation as well" |

## Maintenance

Update this doc when an Edict is added, removed or retuned, or the Senate menu rules change. Time tags and `TimeTracker` live in [Utils](../core/UTILS.md); the Compounds item in [Installables](../items/INSTALLABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
