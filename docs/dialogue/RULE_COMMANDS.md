# Rule commands and rules.csv layout

Covers the shared rule commands in `indevo/dialogue/rules/`, a lookup table of every IE rule command class, and how `data/campaign/rules.csv` is organised. Read it before adding a rule command or a rules block, or when a rules script cell fails to resolve. Each feature's own commands are explained in that feature's doc; this file only lists them.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The engine resolves the first word of a rules condition or script line to a `CommandPlugin` class by simple name, searched in the `ruleCommandPackages` list of `settings.json` (skill `docs/concepts/rules-engine.md:9`; vanilla list at game `data/config/settings.json:1040`). IE adds 15 packages. `indevo/dialogue/rules/` holds the commands that several features share: colony option menu checks, market checks, the embassy war check, and the impoverished spacer fine.

## Files

| File | Owner / role |
|---|---|
| `indevo/dialogue/rules/IndEvo_requiresColonyMenuPoint.java` | Condition: market is a real, non-neutral colony with at least two of: Senate in system (player owned), Academy, Rift Generator (player owned), Courier Port (player owned), Salvage Yards (`:20`). True means the features collapse into the shared "colony options" submenu. |
| `IndEvo_marketHasIndustry.java` | Condition `IndEvo_marketHasIndustry <industryId>`: `$market.id` has the industry and it is functional. |
| `IndEvo_marketIsPlayerOwned.java` | Condition: `$market.id` is player owned; falls back to `$faction.id == player`. |
| `IndEvo_notAtWar.java` | Condition: player relation to the faction of the market's ambassador > -0.5 (`AmbassadorPersonManager.getAmbassador`, see [Embassy](../industries/EMBASSY.md)). |
| `IndEvo_AddSpacerFineMenuPoint.java` | Adds option `IndEvo_PaySpacerFine` with the stolen amount plus 20% (`PENALTY_AMT_FRACTION`), disabled when the player cannot pay. |
| `IndEvo_CPCExpress.java` | Express Delivery on vanilla custom production: condition `valid $cpc_ref`, scripts `options` and `apply $cpc_ref`. See [Economy](../core/ECONOMY.md#custom-production-express-delivery). |
| `IndEvo_DeductSpacerCreditsAndSetNeutral.java` | Takes amount + 20% from player credits, renames the fleet "No longer ...", clears hostile flags, sets non-hostile and non-aggressive, drops its intercept. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Setting | `ruleCommandPackages` (15 IE packages) | `data/config/settings.json:30` to `:46` |
| Memory, fleet | `$IndEvo_stolen_credit_amt`, `$IndEvo_poor_spacer` | `indevo/exploration/salvage/specials/CreditStashSpecial.java:103`, `:118` (see [Salvage](../exploration/SALVAGE.md)) |
| Option ids | `IndEvo_PaySpacerFine`, `IndEvo_NoPaySpacerFine` | `IndEvo_AddSpacerFineMenuPoint.OPTION_PAY`, rules |
| Rules | `IndEvo_ImpoverishedSpacerGreeting` (`:474`), `IndEvo_PaySpacerFine` (`:493`), `IndEvo_NoPaySpacerFine` (`:511`) | `data/campaign/rules.csv` |

## Registration and lifecycle

No Java registration. A class is usable from rules only if its package is in `ruleCommandPackages`; it is created per execution, so commands hold no state. Rules load from `data/campaign/rules.csv` (`#RULESET_NAME IndEvo`, line 2).

## Rule command lookup

All classes that extend `BaseCommandPlugin` in `jars/src/` (grep `extends BaseCommandPlugin`). "Rows" counts rules rows that use it in a condition or script cell; first use shown as `rule id:line`.

| Class | Package | Rows | First use | Doc |
|---|---|---|---|---|
| `IndEvo_requiresColonyMenuPoint` | `indevo.dialogue.rules` | 9 | `IndEvo_ColonyOptionPoint:533` | this doc |
| `IndEvo_marketHasIndustry` | `indevo.dialogue.rules` | 14 | `IndEvo_SalvageYardsBaseMenu:551` | this doc |
| `IndEvo_marketIsPlayerOwned` | `indevo.dialogue.rules` | 16 | `IndEvo_ambassadorGreeting:571` | this doc |
| `IndEvo_notAtWar` | `indevo.dialogue.rules` | 2 | `IndEvo_ambassadorGreeting:571` | this doc |
| `IndEvo_AddSpacerFineMenuPoint` | `indevo.dialogue.rules` | 1 | `IndEvo_ImpoverishedSpacerGreeting:474` | this doc |
| `IndEvo_CPCExpress` | `indevo.dialogue.rules` | 3 | `IndEvo_cpcExpressOffer:861` | [Economy](../core/ECONOMY.md) |
| `IndEvo_DeductSpacerCreditsAndSetNeutral` | `indevo.dialogue.rules` | 1 | `IndEvo_PaySpacerFine:493` | this doc |
| `IndEvo_RemoveCurrentInteractionTarget` | `indevo.dialogue.beacons.dialogue` | 1 | `IndEvo_beacon_destroyLeave:263` | [Beacons](BEACONS.md) |
| `IndEvo_IsInHyperspace` | `indevo.dialogue.beacons.dialogue` | 0 | | [Beacons](BEACONS.md) |
| `ResearchProjectDialoguePlugin` | `indevo.dialogue.research.dialogue` | 1 | `IndEvo_JobMenuInit:419` | [Research](RESEARCH.md) |
| `IndEvo_ColonizeCrucible` | `indevo.exploration.crucible.dialogue` | 1 | `IndEvo_crucible_finalizeColonize:213` | [Crucible](../exploration/CRUCIBLE.md) |
| `IndEvo_HasGenericSpecialItemInCargo` | `indevo.exploration.crucible.dialogue` | 4 | `IndEvo_crucible_unlockWormhole:151` | [Crucible](../exploration/CRUCIBLE.md) |
| `IndEvo_StartCrucible` | `indevo.exploration.crucible.dialogue` | 1 | `IndEvo_crucible_startActivate:251` | [Crucible](../exploration/CRUCIBLE.md) |
| `OpenStorageTabOnGachaStation` | `indevo.exploration.gacha.rules` | 1 | `IndEvo_GachaStationCargoOpen:266` | [Gacha](../exploration/GACHA.md) |
| `IndEvo_RegisterTutorialMeteorIntel` | `indevo.exploration.meteor.rules` | 1 | `IndEvo_roiderTutorial_acceptOffer:40` | [Meteor](../exploration/METEOR.md) |
| `SurveyStoryReader` | `indevo.exploration.surveystories.dialogue.rules` | 6 | `oldstory_onSurveyIntercept:844` | [Survey stories](../exploration/SURVEY_STORIES.md) |
| `HyperlostGateInteraction` | `indevo.exploration.distress.ruleCMD` | 0 | not in packages, folder excluded by `IndEvo.iml` | [Distress](../exploration/DISTRESS.md) |
| `IndEvo_SalvageDefenderInteraction` | `indevo.industries.artillery.rules` | 1 | `IndEvo_ArtilleryCombatInteraction:377` | [Artillery](../industries/ARTILLERY.md) |
| `CourierPortDialoguePlugin` | `indevo.industries.courierport.dialogue` | 3 | `IndEvo_OpenShippingPortFromIndustryOption:816` | [Courier port](../industries/COURIER_PORT.md) |
| `CourierPortIsDisplayInRelay` | `indevo.industries.courierport.dialogue` | 1 | `IndEvo_CommrelayShippingPoint:825` | [Courier port](../industries/COURIER_PORT.md) |
| `IndEvo_GeneratorAllowedToFire` | `indevo.industries.derelicts.rules` | 6 | `IndEvo_RiftControlMenuPoint:773` | [Derelicts](../industries/DERELICTS.md) |
| `IndEvo_GeneratorReadyToFire` | `indevo.industries.derelicts.rules` | 4 | `IndEvo_RiftControlMenuPoint:773` | [Derelicts](../industries/DERELICTS.md) |
| `IndEvo_MoreThanTwoColonies` | `indevo.industries.derelicts.rules` | 2 | `IndEvo_RiftTarget_COLONY:731` | [Derelicts](../industries/DERELICTS.md) |
| `IndEvo_RiftInit` | `indevo.industries.derelicts.rules` | 4 | `IndEvo_RiftTarget_COLONY_init:755` | [Derelicts](../industries/DERELICTS.md) |
| `IndEvo_ShowSalvageYardsPanel` | `indevo.industries.salvageyards.rules` | 3 | `IndEvo_SYCustomProductionPanel_init:564`, `IndEvo_SYMoveDMods_init:565`, `IndEvo_SYMoveSMods_init:566` | [Salvage yards](../industries/SALVAGE_YARDS.md) |
| `IndEvo_addAmbassadorToCargo` | `indevo.industries.embassy.rules` | 1 | `IndEvo_ambassadorConfirm:610` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_ambFactionMismatch` | `indevo.industries.embassy.rules` | 2 | `IndEvo_ambassadorGreetingWar:578` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_ambassadorRemoval` | `indevo.industries.embassy.rules` | 1 | `IndEvo_ambassadorRemovalConfirm:662` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_checkForRepResponse` | `indevo.industries.embassy.rules` | 6 | `IndEvo_ambassadorRemove1:637` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_displayAmbassadorMoveOption` | `indevo.industries.embassy.rules` | 1 | `IndEvo_ambassadorGreetingInstalled:616` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_isAmbassador` | `indevo.industries.embassy.rules` | 5 | `IndEvo_ambassadorGreeting:571` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_moveAmbassadorToClosestEmbassy` | `indevo.industries.embassy.rules` | 1 | `IndEvo_ambMoveOption:665` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_noActiveAmbInCargo` | `indevo.industries.embassy.rules` | 3 | `IndEvo_ambassadorHireFail:594` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_noInstalledAmbOfFaction` | `indevo.industries.embassy.rules` | 2 | `IndEvo_ambassadorAlreadyInstalled:597` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_playerHasFreeEmbassy` | `indevo.industries.embassy.rules` | 2 | `IndEvo_ambassadorNoFreeEmbassy:601` | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_playerHasFreeEmbassyNoCargo` | `indevo.industries.embassy.rules` | 0 | | [Embassy](../industries/EMBASSY.md) |
| `IndEvo_OpenEdictPanel` | `indevo.industries.senate.rules` | 1 | `IndEvo_openEdictPanel:714` | [Senate](../industries/SENATE.md) |
| `IndEvo_systemHasSenate` | `indevo.industries.senate.rules` | 4 | `IndEvo_SenateMenuPoint:698` | [Senate](../industries/SENATE.md) |
| `IndEvo_ShowAcademyPanel` | `indevo.industries.academy.rules` | 2 | `IndEvo_IntitiateAcademyMenu:726` | [Academy](../industries/ACADEMY.md) |
| `IndEvo_InitPersonalityTransferMenu` | `indevo.industries.academy.rules.WIP` | 0 | not in packages, folder excluded by `IndEvo.iml` | [Academy](../industries/ACADEMY.md) |
| `HasItemAmountAnywhere`, `RemoveItemAmountAnywhere` | `indevo.other` | 0 | not in packages, unresolvable from rules | [Misc](../core/MISC.md) |

`indevo.abilities.splitfleet.dialogue` is listed in `ruleCommandPackages` (`settings.json:39`) but holds no command class. Rules also call vanilla commands (`FireAll`, `FireBest`, `AddRemoveAnyItem`, `MakeHostileWhileTOff`, `DoCanAffordCheck`, `ShowResCost`, `SetOptionColor`, `SalvageEntity`, ...), all in `api/impl/campaign/rulecmd/`.

## rules.csv layout

`data/campaign/rules.csv`, 885 lines, 299 rows. Blocks start with a `#` comment row in the id column and are separated by empty rows; most rule ids start with `IndEvo_`. Line ranges are physical file lines (cells span lines).

| Lines | Block (header) | Id prefix | Doc |
|---|---|---|---|
| 1 to 2 | header, `#RULESET_NAME IndEvo` | | |
| 4 to 66 | `#Roider Tutorial` | `IndEvo_roiderTutorial_` | [Meteor](../exploration/METEOR.md) |
| 68 to 98 | `#missiles`, `#Caught after use without transponder` | `IndEvo_missile_` | [Consumables](../items/CONSUMABLES.md) |
| 99 to 255 | `#Crucible with colony` ... `#Activating the crucible` (9 sub headers) | `IndEvo_crucible_`, `IndEvo_c_skip` | [Crucible](../exploration/CRUCIBLE.md) |
| 256 to 264 | `#Beacons` | `IndEvo_beacon_` | [Beacons](BEACONS.md) |
| 266 | (no header) gacha storage | `IndEvo_GachaStationCargoOpen` | [Gacha](../exploration/GACHA.md) |
| 268 to 348 | (no header) Lord Foog aristocrat | `IndEvo_foog_`, `IndEvo_foog*` | [Pets](../industries/PETS.md) |
| 350 to 360 | `#planetoid` | `IndEvo_isPlanetoid` | [Meteor](../exploration/METEOR.md) |
| 362 to 399 | `#artillery` | `IndEvo_Artillery` | [Artillery](../industries/ARTILLERY.md) |
| 400 to 415 | `#watchtower` | `IndEvo_derelictWatchtower_`, `cob_hackPrompt_watchtower` | [Artillery](../industries/ARTILLERY.md) |
| 417 to 419 | `#Galatia Job List` | `IndEvo_JobMenu` | [Research](RESEARCH.md) |
| 421 to 472 | `#Converters` | `IndEvo_DysonSwarmMenu`, `greetingConverter` | [WIP](../core/WIP.md) |
| 473 to 530 | `#Spacer Event` | `IndEvo_ImpoverishedSpacerGreeting`, `IndEvo_*PaySpacerFine` | this doc, [Salvage](../exploration/SALVAGE.md) |
| 532 to 544 | `# Colony options base menu` | `IndEvo_ColonyOptionPoint`, `IndEvo_fireColonyOptions`, `IndEvo_SelectreturnToMenu` | this doc |
| 546 to 566 | `#Salvage Yards` | `IndEvo_SY`, `IndEvo_SalvageYards` | [Salvage yards](../industries/SALVAGE_YARDS.md) |
| 568 to 689 | `# Embassy` (foreign, player market, convEnd sub blocks) | `IndEvo_ambassador`, `IndEvo_amb`, `IndEvo_convEnd` | [Embassy](../industries/EMBASSY.md) |
| 697 to 715 | `# Senate` | `IndEvo_SenateMenuPoint`, `IndEvo_EdictMenuPoint`, `IndEvo_openEdictPanel` | [Senate](../industries/SENATE.md) |
| 717 to 728 | `# Academy` (entry options, panel) | `IndEvo_AcademyBase`, `IndEvo_IntitiateAcademyMenu` | [Academy](../industries/ACADEMY.md) |
| 730 to 813 | `# Rift Generator`, `# Rift Base Menu` | `IndEvo_RiftTarget_`, `IndEvo_RiftControlMenuPoint` | [Derelicts](../industries/DERELICTS.md) |
| 815 to 829 | `# Shipping Port` | `IndEvo_Shipping`, `IndEvo_COB_shipping` | [Courier port](../industries/COURIER_PORT.md) |
| 831 to 838 | `# Ambassador Murdered` | `IndEvo_bountyHunter` | [Embassy](../industries/EMBASSY.md) |
| 840 to 851 | (no header) survey stories | `oldstory_` | [Survey stories](../exploration/SURVEY_STORIES.md) |
| 853 to 859 | `# Ghost ship interaction rules` | `indevo_ghostShip` | [Distress](../exploration/DISTRESS.md) |
| 860 to 884 | `# Custom production express delivery` | `IndEvo_cpc` | [Economy](../core/ECONOMY.md#custom-production-express-delivery) |

### Colony options menu

When a player market has two or more of Senate, Academy, Rift Generator, Courier Port, Salvage Yards, `IndEvo_ColonyOptionPoint` (`:533`, condition `IndEvo_requiresColonyMenuPoint`) adds one menu option; `IndEvo_fireColonyOptions` fires the custom trigger `IndEvo_ColonyOptionPoint`, and each feature has a `...2` or `...ColonyOptionMenu` rule on that trigger. With fewer than two, each feature's own `PopulateOptions` rule (condition `!IndEvo_requiresColonyMenuPoint`) adds its option directly. Features also have `IndEvo_FireBase<X>OptionList` triggers, fired by `new RuleBasedInteractionDialogPluginImpl(trigger)` from each feature's industry option provider (e.g. `indevo/industries/courierport/plugin/CourierPortOptionProvider.java:38`). The Academy and Senate options open their panels directly.

## Contracts

- Command lookup is by simple class name across all listed packages; two classes with the same simple name in different listed packages collide (inferred, not traced in `obf/`). Keep the `IndEvo_` prefix for new commands.
- Rules memory keys need `$`; commands read market data through `memoryMap.get(MemKeys.MARKET).getString("$id")`, so they need a market in the dialog memory map.
- `IndEvo_requiresColonyMenuPoint` counts Senate only via `MiscIE.systemHasIndustry(Ids.SENATE, system, faction)`, Academy and Salvage Yards also on non-player markets.
- A rule whose script names a missing command fails when fired, not at load (inferred from per-call resolution).

## Known issues and dormant code

| Item | State |
|---|---|
| `IndEvo_DeductSpacerCreditsAndSetNeutral` | casts the target to `CampaignFleetAPI` before the `instanceof` check (`:25`); unreachable, the rule only fires in the spacer fleet dialog |
| `IndEvo_notAtWar` | NPE if the market has no ambassador (`AmbassadorPersonManager.getAmbassador` returns null, unchecked at `:20`); both rows list `IndEvo_isAmbassador` first |
| `IndEvo_IsInHyperspace`, `IndEvo_playerHasFreeEmbassyNoCargo` | unused by rules |
| `HasItemAmountAnywhere`, `RemoveItemAmountAnywhere`, `HyperlostGateInteraction`, `IndEvo_InitPersonalityTransferMenu` | not in a listed package |

## Maintenance

Update this doc when a command class is added, moved or removed, when `ruleCommandPackages` changes, or when a rules block is added or moves. Regenerate the tables with a csv parser over `rules.csv` (cells span lines; do not use `grep -n` for row starts). Feature behaviour belongs in each feature's doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
