# Changeling industries (governments, specialized mining and refining)

Covers governments and mining or refining specializations: a selectable variant ("sub industry") stored on the market and applied on top of the host industry, plus the hullmods, listeners and immigration plugin the government variants use. Read it before touching a government, a mining or refining specialization, or the switch dialog. It does not cover Privateer raids that feed Managed Democracy; see [Privateer](PRIVATEER.md).

Checked against Starsector 0.98.5a and IE dev (AOTD compat rework, 2026-11-21). Recheck engine facts after a game update.

## Overview

IE never replaces the host industry. A government is the market condition `IndEvo_government` (`GovernmentCondition`), which holds the variant object, the lock timer and the overrides it set. A specialization is a market memory key (`$IndEvo_miningFocus`, `$IndEvo_refiningFocus`) read by `Specialization`. One `IndustryApplyListener` (`ChangelingApplyListener`) applies both as deltas around the host's own apply, so the host stays whatever the game has: vanilla, ToT's population, SoP's capital, any VoK mining or refining tier. The player switches variant through an industry option ("Change government...", "Change Specialization...") that opens `ChangelingIndustryDialogueDelegate`. Governments lock 7 days after a non-base, non-Anchorage pick and are only offered up to colony size `IndEvo_GovernmentMaxSize`.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/changeling/industry/ChangelingTarget.java` | Contract the dialog switches through: `getOptions`, `getCurrentId`, `setCurrent` |
| `indevo/industries/changeling/industry/SubIndustryAPI.java` | Contract for one variant: `apply`/`unapply` on the host, `preApplyToOther`/`applyToOther`/`unapplyFromOther` on every industry of the market, image, cost, availability, pather interest, tooltip hook |
| `indevo/industries/changeling/industry/SubIndustry.java` | Base variant. Defaults: cost 50000, build time 31, always available, pather interest `Float.MAX_VALUE` (means "use the host"). `modId()`/`modId(i)` give the host's own `BaseIndustry` mod ids (`ind_<getId()>`, so the SoP capital, which reports `population`, matches); `ensureTexture` loads a variant sprite once per session |
| `indevo/industries/changeling/industry/SubIndustryDataAPI.java`, `SubIndustryData.java` | List entry (id, name, sprite, description id, cost); `newInstance()` builds the variant |
| `indevo/industries/changeling/industry/Specialization.java` | `MINING` and `REFINING` targets: memory key, host tag, delta key `<id>_IndEvo_specialized`, name and image override on the vanilla host |
| `indevo/industries/changeling/industry/mining/MiningSpecializations.java` | Mining variant list, `applyOutputDeltas` |
| `indevo/industries/changeling/industry/refining/RefiningSpecializations.java` | Refining variant list, `applyOutputDeltas` |
| `indevo/industries/changeling/industry/population/Governments.java` | Government variant list, `get(id)` |
| `indevo/industries/changeling/conditions/GovernmentCondition.java` | Condition `IndEvo_government` (hidden, no icon): current variant, 7 day lock, `set`, `canChange`, overrides, population tooltip parts, pather interest lookup |
| `indevo/industries/changeling/listener/ChangelingApplyListener.java` | `IndustryApplyListener` that applies specializations and the government |
| `indevo/industries/changeling/patches/GovernmentPatherInterestPatch.java` | PatchLib redirect: the government's pather interest stands in for the population's in `LuddicPathBaseManager.getLuddicPathMarketInterest` |
| `.../population/UnderworldSubIndustry.java` | Underworld Governance: inverted stability to income, illegal output +1, extorts trader cargo into storage |
| `.../population/RuralPolitySubIndustry.java` | Rural Polity: tag based upkeep and stability, farming and light industry extra output; inner `RuralPolityTooltipAdder`, `RuralPolityImageChanger` |
| `.../population/HiddenArcologiesSubIndustry.java` | Hidden Arcology: suppresses surface conditions, -2 output, -30% access, x2 ground defense |
| `.../population/OutpostSubIndustry.java` | Anchorage: max size 3, no industry slots, upkeep x0.7, never locks |
| `.../population/HelldiversSubIndustry.java` | Managed Democracy: stability clamp 3..7, -50% income, raid income bonuses, marine XP, Hellpods refit; inner `HelldiversTooltipAdder` |
| `.../population/WarhammerSubIndustry.java` | Forge World: income and production budget per ship hull, monthly hand built ship, unrest on shortage; inner `WarhammerTooltipAdder` |
| `.../population/CorporateGovernanceSubIndustry.java` | Corporate Governance: income to stability, x3 military upkeep, can shrink; inner `CorpoPolityTooltipAdder` |
| `.../population/MonasticOrderSubIndustry.java` | Monastic Order. Not in the variant list (dormant) |
| `.../population/ResortSubIndustry.java` | Resort Planet stub. Not in the variant list (dormant) |
| `.../population/Concept` | Plain text design notes, not code |
| `indevo/industries/changeling/dialogue/ChangelingIndustryDialogueDelegate.java` | Variant picker (`CustomDialogDelegate`) over a `ChangelingTarget`; hides the current variant, charges credits |
| `indevo/industries/changeling/plugins/ChangelingPopulationOptionProvider.java`, `ChangelingMiningOptionProvider.java`, `ChangelingRefiningOptionProvider.java` | Industry option that opens the picker; tooltip section on the host (Governance Type, Specialized Mine/Refinery) |
| `indevo/industries/changeling/plugins/CorporateGovernanceCampaignPlugin.java` | `pickImmigrationPlugin`: Corporate markets get `CorpoImmigrationPlugin` |
| `indevo/industries/changeling/plugins/CorpoImmigrationPlugin.java` | `CoreImmigrationPluginImpl` copy that can shrink a market and blocks growth on `IndEvo_CorpoGov_MaxSize` |
| `indevo/industries/changeling/hullmods/HandBuiltHullmod.java` | `IndEvo_handBuilt`: random stat multipliers per fleet member |
| `indevo/industries/changeling/hullmods/HandBuiltEffect.java`, `HandBuiltEffectMemoryRepo.java` | Rolled effects per member id, stored in sector memory |
| `indevo/industries/changeling/hullmods/SimpleHullmodEffectPlugin.java`, `SimpleHullmodEffectPluginRepo.java` | 120 stat effects, each `modifyMult(IndEvo_HandMade, amt)` on one `MutableShipStatsAPI` stat |
| `indevo/industries/changeling/hullmods/Hellpods.java` | `IndEvo_hellpods`: +5% `FLEET_GROUND_SUPPORT` |
| `indevo/industries/changeling/listener/MarineLossAmplifcationHullmodEffectListener.java` | `MarineLossesStatModifier`: x1.1 marine losses per Hellpods ship in the player fleet |
| `indevo/industries/changeling/listener/ManagedDemocracyNexerelinListenerPlugin.java` | Nexerelin `InvasionListener`: player captures feed Managed Democracy raid bonus |
| `indevo/industries/changeling/listener/ShipProductionSummaryMessageHandler.java` | Collects Forge World ship deliveries, sends one message per month |
| `indevo/industries/changeling/script/CampaignNotificationMessager.java`, `DelayedCampaignNotificationScript.java` | Message after a delay in days (2 days for the production summary) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Condition | `IndEvo_government` (`showIcon` false, tag `hide_in_codex`, order 20.5, removed on decivilization) | `data/campaign/market_conditions.csv` |
| Memory key | `$IndEvo_miningFocus`, `$IndEvo_refiningFocus` (market, variant id, unset for base) | `Specialization.java:20-21` |
| Mod key | `<host id>_IndEvo_specialized` on the host's supply and demand | `Specialization.java` |
| Variant ids | `base_population_and_infrastructure`, `underworld`, `rural`, `hidden`, `outpost`, `helldivers`, `warhammer`, `corpos` (dormant `monks`, `resort`) | `Governments.java` |
| Variant ids | `base_mining`, `ore_mining`, `rare_mining`, `volatile_mining`, `organics_mining` | `MiningSpecializations.java` |
| Variant ids | `base_refining`, `ore_refining`, `rare_refining` | `RefiningSpecializations.java` |
| Descriptions | `IndEvo_pop_*`, `IndEvo_*_mining`, `IndEvo_*_refining` (CUSTOM; text1 = description, text2 = short effect) | `data/strings/descriptions.csv:32-49` |
| Sprites | category `IndEvo`: `pop_*`, `ore_mining`, `rare_mining`, `volatile_mining`, `organics_mining`, `ore_refining`, `rare_ore_refining` | `data/config/settings.json:104-121` |
| Setting | `IndEvo_GovernmentLarpMode` (all governments act as vanilla), `IndEvo_GovernmentMaxSize` (default 3) | `data/config/LunaSettings.csv:112-113` |
| Setting | `IndEvo_SwitchableMining`, `IndEvo_SwitchableRefining` (show the option; ids kept from the old industries) | `data/config/LunaSettings.csv:116-117` |
| Config CSV | `IndEvo_ruralList` (planet types blocked for Rural), `IndEvo_arcologiesList` (blocked for Hidden Arcology), `IndEvo_arcologiesCondList` (conditions suppressed) | `data/config/settings.json:21-23`, `data/config/indEvo/*.csv` |
| Hullmod | `IndEvo_handBuilt`, `IndEvo_hellpods` | `data/hullmods/hull_mods.csv:14-15` |
| Memory key | `$IndEvo_HandBuiltMemoryRepo` (sector) | `HandBuiltEffectMemoryRepo.java:10` |
| Memory key | `$HasBeenExtortedByUnderworld` (fleet, never unset) | `UnderworldSubIndustry.java:56` |
| Market tag | `IndEvo_CorpoGov_MaxSize` | `CorporateGovernanceSubIndustry.java:42` |
| Memory key | `MemFlags.MARKET_CAN_ALWAYS_INCENTIVIZE_GROWTH` set by Corporate | `CorporateGovernanceSubIndustry.java:114` |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded()` registers, transient and deduped by class: `ChangelingApplyListener`, the three option providers, `RuralPolityTooltipAdder`, `RuralPolityImageChanger`, `MarineLossAmplifcationHullmodEffectListener`. `ManagedDemocracyNexerelinListenerPlugin` only if `nexerelin` is enabled.
- `ModPlugin.setScriptsIfNeeded()` registers `CorporateGovernanceCampaignPlugin` every load. `BaseCampaignPlugin` is transient with a null id (`api/campaign/BaseCampaignPlugin.java:31-37` in the knowledge base).
- The other tooltip adders (`HelldiversTooltipAdder`, `WarhammerTooltipAdder`, `CorpoPolityTooltipAdder`) register themselves from their variant's `apply()`.
- `HelldiversSubIndustry` and `WarhammerSubIndustry` add themselves as persistent listeners in `apply()` and remove themselves in `unapply()`. `ShipProductionSummaryMessageHandler.getInstanceOrRegister()` adds a persistent listener on first use.
- `GovernmentPatherInterestPatch` is found by PatchLib from the jar, no registration.

Switch chain: `Changeling*OptionProvider.optionSelected -> ChangelingIndustryDialogueDelegate -> customDialogConfirm -> ChangelingTarget.setCurrent -> (GovernmentCondition.set | Specialization.setCurrent) -> market.reapplyIndustries() -> credits -= SubIndustryData.cost`.

Apply chain, per industry, from `Market.reapplyIndustries` and `BaseIndustry.reapply()`: `host.unapply -> reportIndustryAboutToBeApplied (pop: gov.unapply; every industry: gov.preApplyToOther) -> host.apply -> reportIndustryApplied (specialization deltas; pop: overrides + gov.apply; every industry: gov.applyToOther)`.

## Flow

### Condition and listener

- The listener fires for every industry apply in the sector, also for some tooltips and build menu previews (javadoc on `IndustryApplyListener`). Variants must be idempotent: every mod is keyed, and anything conditional is unmodified when the condition is false.
- `gov.unapply()` runs in the pop's `aboutToBeApplied`, so it may only touch market and pop level mods. Per industry effects live in `preApplyToOther` (before that industry's apply, for `getSupplyBonusFromOther` and other inputs its apply reads) and `applyToOther` (after it, for anything that reads its fresh output). `unapplyFromOther` runs only when the government changes.
- `GovernmentCondition.set` unapplies the old variant from the pop and every industry, clears its overrides, then adds or removes the condition (base removes it) and reapplies the market. Switching between two non-base variants keeps `daysPassed`; going to base resets it.
- Name and image overrides go on the pop only when its spec id is `population` (not the SoP capital) and the current override is null or the one the condition set last. Specializations do the same on the vanilla `mining` and `refining` hosts only.
- LARP mode (`IndEvo_GovernmentLarpMode`): names, images and descriptions stay, effects are skipped and removed.
- Images: vanilla `PopulationAndInfrastructure.getCurrentImage` (small and large sizes) and `Mining.getCurrentImage` (gas giant, small size) skip `imageOverride`. `ChangelingImagePatch` (`@After getCurrentImage` on every `BaseIndustry` subtype, most derived call only) returns the override again when it is the one the condition or specialization set.
- Tooltip: `ChangelingTooltipPatch` redirects two calls in `BaseIndustry.createTooltip`. `getDescriptionOverride()` returns the variant's description text1 for a government pop (spec id `population`; the SoP capital keeps its own, like its name and image) or a specialized vanilla host (same as the old Switchable plugins); `addRightAfterDescriptionSection` is followed by the government's `addRightAfterDescription` unless LARP. The Governance Type section stays in `ChangelingPopulationOptionProvider.addToIndustryTooltip`, which vanilla calls right after `addPostDemandSection`. The "Governance Effects" sections of Rural, Corporate, Forge World and Managed Democracy go on every industry but the pop itself.
- Pather interest has no market hook: `LuddicPathBaseManager.getLuddicPathMarketInterest` sums `ind.getPatherInterest()`. `GovernmentPatherInterestPatch` redirects that one call for the pop and returns the variant's value unless it is above 1e8. Other callers of `getPatherInterest()` see the vanilla pop value.

### Mining and refining

- The deltas apply to every industry whose spec carries the `mining` or `refining` tag (vanilla and every VoK tier), only while it is functional. The host keeps its own demand and deficit logic.
- Mining variants add a flat `_IndEvo_specialized` supply mod per commodity (`MiningSpecializations.applyOutputDeltas`). Ore: +2/-2/-2/-2. Transplutonics: -3/+2/-3/-3. Volatiles: -3/-3/-3/+2. Organics: -3/-3/+2/-3.
- Refining variants shift metals and transplutonics supply, and ore and rare ore demand, by +-2 (`RefiningSpecializations.applyOutputDeltas`).
- `Specialization.removeDeltas` drops the key from every supply and demand, and the `ind_sb` supply bonus a positive `supply()` call left on a commodity that has nothing else.
- Option providers show on any tagged host on player markets, only while the matching setting is on.

### Governments

- Every variant runs after the host pop applied and writes deltas. Corporate and Underworld re-set the pop's base demand and supply and its `modId(3)` income mult and `_ms` base stability under the host's own keys; ToT's scaling sits on its own keys and stays on top. Underworld also drops the domestic goods stability bonus (`modId(0)`) and recomputes the luxury one; the host's own import upkeep, mismanagement and comm relay code stays.
- Lock: `GovernmentCondition.advance()` counts days for a non-Anchorage variant; at `DAYS_TO_LOCK = 7` it sets `locked` and posts a message. `canChange(market)` is dev mode or (size <= `IndEvo_GovernmentMaxSize` and not locked). `ChangelingPopulationOptionProvider.isSuitable` offers the switch on a player `population` that `canChange`.
- Pather interest: the variant value replaces the pop's unless it is above 1e8 (the `SubIndustry` default), see the patch above. Rural returns -10; Managed Democracy and Monastic return minus the market's total interest from other industries.
- Availability per variant (`isAvailableToBuild` / `getUnavailableReason`):

| Variant | Cost | Rule |
|---|---|---|
| Underworld | 45000 | always |
| Rural Polity | 80000 | planet, rich or bountiful farmland, type not in `IndEvo_ruralList` (`RuralPolitySubIndustry.java:251-274`) |
| Hidden Arcology | 125000 | planet, not gas giant, type not in `IndEvo_arcologiesList` (`HiddenArcologiesSubIndustry.java:95-111`) |
| Anchorage | 30000 | always (size reason text only, inferred missing check) |
| Managed Democracy | 100000 | dev mode or (size <= 5 and planet) (`HelldiversSubIndustry.java:350-356`) |
| Forge World | 150000 | `very_hot`, planet, not gas giant (`WarhammerSubIndustry.java:187-189`) |
| Corporate | 50000 | always |

- Underworld: base stability 2, income mult `5/stability` above 5, `1.2^(5-stability)` below; +1 output on every industry that makes an illegal commodity on its own; every day (`IntervalUtil(10,10)` seconds) takes 5% of each commodity stack from trader, smuggler and scavenger fleets in the system into storage, once per fleet (`:97-141`).
- Rural: `industrial` tag x2 upkeep and -3 stability, `rural` tag x0.5 upkeep and +1 stability and +2 growth, -1 stability per non-`rural` special item and per AI core; Farming gets +1 output, organics and (free port) drugs from its food minus ToT's `aotd_toolbox_food_prod` flat mod; Light Industry gets hand weapons and supplies. The farming and light industry output is set after that industry applied. `RuralPolityImageChanger` cycles `rural_polities_1..5.png` (`:290-309`).
- Hidden Arcology: suppresses every condition in `IndEvo_arcologiesCondList` on each apply; the list holds `hot` and `very_hot`, so the fusion lamp heat condition stays suppressed too. The -2 output is a `getSupplyBonusFromOther` mod set before each industry applies.
- Managed Democracy: extra patrols by size: 1 light, plus 1 medium above size 3, plus 1 light and 1 heavy above size 4 (`HelldiversSubIndustry.java:312-314`); `correctStability()` adds a flat mod to clamp stability to 3..7 (`HelldiversSubIndustry.java:396-401`); `RaidMod` entries add income percent for raids, bombardments, Privateer raids (`PrivateerBase.java:194,203`) and Nexerelin captures; `reportEconomyTick` adds 3 XP per tick to marines stored here; `onNewDay` (via `TimeTracker`) gives destroyers in storage `IndEvo_hellpods` after 62 days.
- Forge World: stability +1 while metals and transplutonics demand is met, -1 on a shortage (`WarhammerSubIndustry.java:130-138`); +2000 income per ship hull supplied, +5000 to `Stats.CUSTOM_PRODUCTION_MOD` per hull (`WarhammerSubIndustry.java:174-184`); month end: unrest +1 on any deficit (cap 5), monthly DP budget `size - 2`, 30% chance to build a known hull with `getSuppliesToRecover() <= budget` into storage with `IndEvo_handBuilt` (`:211-257`). Adds `pollution` once locked on habitable worlds.
- Corporate: base stability 0, +1 stability per 20000 net income (max 10), base income x1.3, military upkeep x3, disruption capped at 62 days, full industry slots once locked, growth -20 and -1 per 20000 income (`CorporateGovernanceSubIndustry.java:95-217`). `CorpoImmigrationPlugin` shrinks the market when weight drops below the size minimum (size > 3) and skips growth when tagged `IndEvo_CorpoGov_MaxSize` (`CorpoImmigrationPlugin.java:44-136`).
- Anchorage: sets max size to 3, removes all industry slots, upkeep x0.7 (`OutpostSubIndustry.java:21-32`).

### Hand built hullmod

`HandBuiltHullmod.applyEffectsBeforeShipCreation` keys on `stats.getFleetMember().getId()`, rolls effects once with `Random(id.hashCode())` and stores them in `HandBuiltEffectMemoryRepo` (`HandBuiltHullmod.java:25-50`). Each effect is a mult in 0.7..1.3. Both hullmods extend `SelfRepairingBuiltInHullmod`, which re-adds itself as a perma mod ([Pets](PETS.md)).

## Contracts

- Save data: `GovernmentCondition` is not transient, so the condition plugin and its variant object are saved with the market. Base is never stored (the condition is removed). Specializations store only the variant id in memory. Saves from before this rework that hold `IndEvo_Switchable*` industries do not load (save break on dev).
- Governments persist state in the condition (`locked`, `daysPassed`, last overrides) and the variant object (`stolenGoodsList`, `raidMods`, `daysToApplicationForFleetMember`, `currentDpBudget`, Rural image).
- Persistent listener dedupe: `ListenerManager.addListener` stores into an `ObjectRepository` that ignores an instance already present (`obf/campaign/ListenerManager.java:29-35`, `obf/_common/util/container/repo/ObjectRepository.java:87-90`), so repeated `addListener(this)` from `apply()` is safe.
- `market.addImmigrationModifier` keeps a persistent `LinkedHashSet` (`obf/campaign/econ/Market.java:98`); the variant object is saved with the market.
- Other features read the current government with `GovernmentCondition.getGovernment(market) instanceof X`: Privateer (`PrivateerBase.java:71`, `PrivateerBaseRaidIntel.java:76`), the Nexerelin listener, `CorporateGovernanceCampaignPlugin`.
- Rural, Forge World, Corporate and Monastic rules key on industry spec tags `industrial`, `rural`, `military` from `data/campaign/industries.csv`; Managed Democracy uses `MiscIE.isMilitary`. `ModPlugin.addTypePrefaceToIndustrySpecs` only shows those tags in descriptions (`ModPlugin.java:250`). See [Economy](../core/ECONOMY.md).
- Storage access goes through `MiscIE.getStorageCargo`; see [Utils](../core/UTILS.md). Setting reads: [Settings](../core/SETTINGS.md).

## Known issues and dormant code

| Item | State |
|---|---|
| `MonasticOrderSubIndustry`, `ResortSubIndustry` | Dormant: not in `Governments.LIST`, still on the old `apply()` loops. Monastic `isAvailableToBuild` returns dev mode only; its build check `iterIndex != lastIterInMonth` (`:134`) runs on every tick but the last |
| `Concept` file | Design notes only |
| `RuralPolitySubIndustry.fixClosest` | Debug helper, only referenced from a comment |
| Forge World without spaceport | Suspected: `return` in the unrest branch also skips ship building (`:213`) |
| Decivilization | Removes the condition without `unapply`, so a Forge World or Managed Democracy listener stays registered (inferred, not tested) |
| `HandBuiltHullmod` | Repo entries are never pruned, one per hand built ship; ships built before 4.2.a keep their 5 stored effects |
| Changelog_todo | "Choose a government popup on colony establishment", "add some more info/tt/notifications to governments" |

## Maintenance

Update this doc when a variant, its numbers, its availability rule or the switch flow changes, or when another feature starts reading the current government. Privateer raid logic belongs in [Privateer](PRIVATEER.md); storage helpers in [Utils](../core/UTILS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
