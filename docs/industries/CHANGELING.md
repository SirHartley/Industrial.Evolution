# Changeling industries (governments, specialized mining and refining)

Covers governments and mining or refining specializations: a selectable variant ("sub industry") stored on the market and applied on top of the host industry, plus the hullmods, listeners and immigration plugin the government variants use. Read it before touching a government, a mining or refining specialization, or the switch dialog. It does not cover Privateer raids that feed Managed Democracy; see [Privateer](PRIVATEER.md).

Checked against Starsector 0.98.5a and IE dev (AOTD compat rework and picker redesign, 2026-11-21). Recheck engine facts after a game update.

## Overview

IE never replaces the host industry. A government is the market condition `IndEvo_government` (`GovernmentCondition`), which holds the variant object, the lock timer and the overrides it set. A specialization is a market memory key (`$IndEvo_miningFocus`, `$IndEvo_refiningFocus`) read by `Specialization`. One `IndustryApplyListener` (`ChangelingApplyListener`) applies both as deltas around the host's own apply, so the host stays whatever the game has: vanilla, ToT's population, SoP's capital, any VoK mining or refining tier. The player switches variant through an industry option ("Change government...", "Change Specialization...") that opens `ChangelingIndustryDialogueDelegate`. Governments lock 7 days after a non-base, non-Anchorage pick and are only offered up to colony size `IndEvo_GovernmentMaxSize`.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/changeling/industry/ChangelingTarget.java` | Contract the dialog switches through: `getOptions`, `getCurrentId`, `setCurrent`, plus what the picker shows per target: `getTitle(industry)` (one line in the heading colour: "Change government on <colony>", "Change <industry> specialization on <colony>"), `getHelp` (text behind the ? icon, null for none), `getPermanence` (value of the Permanent row, null for none), `getEffects` |
| `indevo/industries/changeling/industry/SubIndustryAPI.java` | Contract for one variant: `apply`/`unapply` on the host, `preApplyToOther`/`applyToOther`/`unapplyFromOther` on every industry of the market, image, cost, availability, pather interest, and the population tooltip parts `getWarning`, `hasOngoing`, `addOngoing` |
| `indevo/industries/changeling/industry/SubIndustry.java` | Base variant. Defaults: cost 50000, build time 31, always available, pather interest `Float.MAX_VALUE` (means "use the host"). `modId()`/`modId(i)` give the host's own `BaseIndustry` mod ids (`ind_<getId()>`, so the SoP capital, which reports `population`, matches); `ensureTexture` loads a variant sprite once per session |
| `indevo/industries/changeling/industry/SubIndustryDataAPI.java`, `SubIndustryData.java` | List entry (id, name, sprite, description id, cost, `effects`, `requires`) and a factory; `newInstance()` builds the variant through it. `gain(text, highlights...)`, `cost(...)` and `requires(text)` chain on the list entry. Lists are plain `List.of` with named variant classes, no anonymous classes: a saved government is its named class, a specialization saves only its id, so order changes never break saves |
| `indevo/industries/changeling/industry/EffectLine.java` | One gain or cost line: a format text and the highlights that fill its `%s` |
| `indevo/industries/changeling/industry/Specialization.java` | `MINING` and `REFINING` targets: memory key, host tag, delta key `<id>_IndEvo_specialized`, name and image override on the vanilla host |
| `indevo/industries/changeling/industry/BaseSubIndustry.java` | Base variant of every list (no effect, `isBase`); `population/BaseGovernmentSubIndustry` adds the vanilla size images |
| `indevo/industries/changeling/industry/mining/MiningSpecializations.java`, `MiningSubIndustry.java` | Mining variant list and percents; the variant takes its focus commodity and malus |
| `indevo/industries/changeling/industry/refining/RefiningSpecializations.java`, `RefiningSubIndustry.java` | Refining variant list and percents; the variant takes its transplutonics and metals percent |
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
| `indevo/industries/changeling/dialogue/ChangelingIndustryDialogueDelegate.java` | Variant picker over a `ChangelingTarget`, a kit panel ([UI](../core/UI.md)): `show(ui, industry, target)` from the option, `show(industry, target, picked)` on its own for code and tests, picture tiles, card of the picked variant, charges credits. See [Picker](#picker) |
| `indevo/industries/changeling/dialogue/EffectLines.java` | Draws a variant's effect lines on a kit panel (`draw`) or into a vanilla tooltip (`add`) |
| `indevo/industries/changeling/plugins/ChangelingPopulationOptionProvider.java`, `ChangelingMiningOptionProvider.java`, `ChangelingRefiningOptionProvider.java` | Industry option that opens the picker; tooltip section on the host (Government, Specialized Mine/Refinery) |
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
| Memory key | `$HasBeenExtortedByUnderworld` (fleet, never unset) | `UnderworldSubIndustry.java:36` |
| Market tag | `IndEvo_CorpoGov_MaxSize` | `CorporateGovernanceSubIndustry.java:42` |
| Memory key | `MemFlags.MARKET_CAN_ALWAYS_INCENTIVIZE_GROWTH` set by Corporate | `CorporateGovernanceSubIndustry.java:114` |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded()` registers, transient and deduped by class: `ChangelingApplyListener`, the three option providers, `RuralPolityTooltipAdder`, `RuralPolityImageChanger`, `MarineLossAmplifcationHullmodEffectListener`. `ManagedDemocracyNexerelinListenerPlugin` only if `nexerelin` is enabled.
- `ModPlugin.setScriptsIfNeeded()` registers `CorporateGovernanceCampaignPlugin` every load. `BaseCampaignPlugin` is transient with a null id (`api/campaign/BaseCampaignPlugin.java:31-37` in the knowledge base).
- The other tooltip adders (`HelldiversTooltipAdder`, `WarhammerTooltipAdder`, `CorpoPolityTooltipAdder`) register themselves from their variant's `apply()`.
- `HelldiversSubIndustry` and `WarhammerSubIndustry` add themselves as persistent listeners in `apply()` and remove themselves in `unapply()`. `ShipProductionSummaryMessageHandler.getInstanceOrRegister()` adds a persistent listener on first use.
- `GovernmentPatherInterestPatch` is found by PatchLib from the jar, no registration.

Switch chain: `Changeling*OptionProvider.optionSelected -> ChangelingIndustryDialogueDelegate.show -> customDialogConfirm -> ChangelingTarget.setCurrent -> (GovernmentCondition.set | Specialization.setCurrent) -> market.reapplyIndustries() -> credits -= SubIndustryData.cost`.

Apply chain, per industry, from `Market.reapplyIndustries` and `BaseIndustry.reapply()`: `host.unapply -> reportIndustryAboutToBeApplied (pop: gov.unapply; every industry: gov.preApplyToOther) -> host.apply -> reportIndustryApplied (specialization deltas; pop: overrides + gov.apply; every industry: gov.applyToOther)`.

## Flow

### Condition and listener

- The listener fires for every industry apply in the sector, also for some tooltips and build menu previews (javadoc on `IndustryApplyListener`). Variants must be idempotent: every mod is keyed, and anything conditional is unmodified when the condition is false.
- `gov.unapply()` runs in the pop's `aboutToBeApplied`, so it may only touch market and pop level mods. Per industry effects live in `preApplyToOther` (before that industry's apply, for `getSupplyBonusFromOther` and other inputs its apply reads) and `applyToOther` (after it, for anything that reads its fresh output). `unapplyFromOther` runs only when the government changes.
- `GovernmentCondition.set` unapplies the old variant from the pop and every industry, clears its overrides, then adds or removes the condition (base removes it) and reapplies the market. Switching between two non-base variants keeps `daysPassed`; going to base resets it.
- Name and image overrides go on the pop only when its spec id is `population` (not the SoP capital) and the current override is null or the one the condition set last. Specializations do the same on the vanilla `mining` and `refining` hosts only.
- LARP mode (`IndEvo_GovernmentLarpMode`): names, images and descriptions stay, effects are skipped and removed.
- Images: vanilla `PopulationAndInfrastructure.getCurrentImage` (small and large sizes) and `Mining.getCurrentImage` (gas giant, small size) skip `imageOverride`. `ChangelingImagePatch` (`@After getCurrentImage` on every `BaseIndustry` subtype, most derived call only) returns the override again when it is the one the condition or specialization set.
- Tooltip of a governed population, two forms. `ChangelingTooltipPatch` makes it expandable: `@After BaseIndustry.isTooltipExpandable()` turns false into true when `GovernmentCondition.isExpandable(ind)` (id `population`, a government, not LARP). The colony screen builds the industry tooltip from that value and passes `expanded` to `createTooltip` (`obf/campaign/ui/marketinfo/intnew.java:441-447`, the only builder of industry tooltips).
  - Hover: the description is the vanilla size line (`population_<size>` condition text); F1: the government's description text1. The patch redirects the `getDescriptionOverride()` call in `BaseIndustry.createTooltip` and reads `expanded` from the host method's third argument (`GovernmentCondition.getDescriptionOverride(ind, expanded)`). The SoP capital (spec id not `population`) keeps its own description in both forms. A specialized vanilla host gets its variant's text1 in both forms.
  - Government section: `ChangelingPopulationOptionProvider.addToIndustryTooltip -> GovernmentCondition.addGovernanceSection(tooltip, pop, width, expanded)`, which vanilla calls after the installed items and improvements sections. Heading "Government", then on F1 the effect lines of the variant (`EffectLines.add`), then in both forms `getWarning()` in red (Forge World shortage), "Permanent in N days" while not locked (never for Anchorage), and `addOngoing(tooltip, width, expanded)` (Managed Democracy: extra patrols and the Hellpod refit table, on F1 one line with the ship count because the F1 form with the table is taller than a 900 px screen; Underworld: extorted goods of the past 30 days as cargo icons in 44 px cells, amounts from 10,000 as `12K`). An effect line must fit `width - 10 - EffectLines.TEXT_X` (374 px) in one line; the `uigov` smoke group measures every line. On hover the section is left out when it would hold nothing; a government with `hasOngoing()` keeps its part with its empty line.
  - Where the tooltip is not expandable (LARP mode, a population plugin that overrides `isTooltipExpandable` without `super`), the hover form is the full form; in LARP mode there is no Government section.
  - The "Governance Effects" sections of Rural, Corporate, Forge World and Managed Democracy go on every industry but the pop itself.
- Pather interest has no market hook: `LuddicPathBaseManager.getLuddicPathMarketInterest` sums `ind.getPatherInterest()`. `GovernmentPatherInterestPatch` redirects that one call for the pop and returns the variant's value unless it is above 1e8. Other callers of `getPatherInterest()` see the vanilla pop value.

### Picker

`ChangelingIndustryDialogueDelegate` is one kit panel for all three targets, board width 840. The window height comes from the tile rows and the tallest card of all options (`height`), so the card area keeps its size when another tile is picked.

- Tiles, 4 per row, 198 x 145: name on a left aligned heading bar in `INS15` ("Population & Infrastructure" does not fit the 174 px in `ORB12`), 3 px, picture, bottom line (cost, "Current", the red unavailable reason, or grey "Requires research"). The current variant is in the grid and picked at open.
- A tile that can not be picked is disabled: missing AOTD research (grey picture and text), `isAvailableToBuild()` false (dim picture, red), or too expensive (dim picture, red cost). Its tooltip holds the full reason, the effect lines and the cost. A mod `CustomDialogDelegate` can not disable the host's Confirm button, so the selection can only ever be a tile that may be confirmed; Confirm on the current variant does nothing.
- Card of the picked tile, two columns with a heading each: left the variant name over Cost ("100,000¢ (Available: 110,000¢)", the price in highlight, the rest grey, no icon; or Status: Current), Requires (`SubIndustryData.requires`, red while not met), Permanent (`ChangelingTarget.getPermanence`) and the description text1; right "Effects" over the gain and cost lines (`ChangelingTarget.getEffects`, grey "No special rules" when empty; governments return none in LARP mode). The description is only cut, with a tooltip, where the screen is too small for the full window.
- The ? icon on the title shows `ChangelingTarget.getHelp()`.
- Effect lines live on the list entries in `Governments.LIST`, `MiningSpecializations.LIST` and `RefiningSpecializations.LIST`, with numbers from the variant constants where one exists. They are player text: change a line together with the rule it describes. Governments do not show description text2; the mining and refining tooltip sections do.

### Mining and refining

- The deltas apply to every industry whose spec carries the `mining` or `refining` tag (vanilla and every VoK tier), only while it is functional. The host keeps its own demand and deficit logic.
- Variants change each line by a percent of what the host left on it after its own apply, written as one flat `_IndEvo_specialized` mod (rounded half away from zero) because the vanilla commodity tooltip only lists flat mods. A line at 0 or below gets nothing, so a focus never makes a commodity the planet does not produce.
- Mining (`MiningSubIndustry`, ore/rare ore/organics/volatiles): Ore +40/-40/-40/-40. Transplutonics -50/+40/-50/-50. Organics -50/-50/+40/-50. Volatiles -50/-50/-50/+40. On a size 6 colony this is close to the old flat +2 and -2 or -3; small AOTD tier lines now scale instead of dropping to 0.
- Refining (`RefiningSubIndustry`): focus supply +40%, the other -40%; ore and rare ore demand move by 3/4 of that (+-30%).
- `Specialization.removeDeltas` drops the key from every supply and demand, and the `ind_sb` supply bonus a positive `supply()` call left on a commodity that has nothing else.
- Option providers show on any tagged host on player markets, only while the matching setting is on.
- Tooltip: the providers add a "Specialized Mine" / "Specialized Refinery" section (`Specialization.addTooltipSection`). The vanilla host shows the variant's text2 only, since its name, image and description already swapped; a modded tier keeps its own, so the section shows the variant image (190 x 95) with its name and text2 beside it. Both end with the variant's effect lines (`EffectLines`, as in the picker).

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
| Managed Democracy | 100000 | dev mode or (size <= 5 and planet) (`HelldiversSubIndustry.java:362-367`) |
| Forge World | 150000 | `very_hot`, planet, not gas giant (`WarhammerSubIndustry.java:188-190`) |
| Corporate | 50000 | always |

- Underworld: base stability 2, income mult `5/stability` above 5, `1.2^(5-stability)` below; +1 output on every industry that makes an illegal commodity on its own; every day (`IntervalUtil(10,10)` seconds) takes 5% of each commodity stack from trader, smuggler and scavenger fleets in the system into storage, once per fleet (`:80-123`).
- Rural: `industrial` tag x2 upkeep and -3 stability, `rural` tag x0.5 upkeep and +1 stability and +2 growth, -1 stability per non-`rural` special item and per AI core; Farming gets +1 output, organics and (free port) drugs from its food minus ToT's `aotd_toolbox_food_prod` flat mod; Light Industry gets hand weapons and supplies. The farming and light industry output is set after that industry applied. `RuralPolityImageChanger` cycles `rural_polities_1..5.png` (`:290-309`).
- Hidden Arcology: suppresses every condition in `IndEvo_arcologiesCondList` on each apply; the list holds `hot` and `very_hot`, so the fusion lamp heat condition stays suppressed too. The -2 output is a `getSupplyBonusFromOther` mod set before each industry applies.
- Managed Democracy: extra patrols by size: 1 light, plus 1 medium above size 3, plus 1 light and 1 heavy above size 4 (`HelldiversSubIndustry.java:319-321`); `correctStability()` adds a flat mod to clamp stability to 3..7 (`HelldiversSubIndustry.java:401-406`); `RaidMod` entries add income percent for raids, bombardments, Privateer raids (`PrivateerBase.java:194,203`) and Nexerelin captures; `reportEconomyTick` adds 3 XP per tick to marines stored here; `onNewDay` (via `TimeTracker`) gives destroyers in storage `IndEvo_hellpods` after 62 days.
- Forge World: stability +1 while metals and transplutonics demand is met, -1 on a shortage (`WarhammerSubIndustry.java:130-138`); +2000 income per ship hull supplied, +5000 to `Stats.CUSTOM_PRODUCTION_MOD` per hull (`WarhammerSubIndustry.java:174-184`); month end: unrest +1 on any deficit (cap 5), monthly DP budget `size - 2`, 30% chance to build a known hull with `getSuppliesToRecover() <= budget` into storage with `IndEvo_handBuilt` (`:210-256`). Adds `pollution` once locked on habitable worlds.
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
- Rural, Forge World, Corporate and Monastic rules key on industry spec tags `industrial`, `rural`, `military` from `data/campaign/industries.csv`; Managed Democracy uses `MiscIE.isMilitary`. `ModPlugin.addTypePrefaceToIndustrySpecs` only shows those tags in descriptions (`ModPlugin.java:246`). See [Economy](../core/ECONOMY.md).
- Storage access goes through `MiscIE.getStorageCargo`; see [Utils](../core/UTILS.md). Setting reads: [Settings](../core/SETTINGS.md).

## Known issues and dormant code

| Item | State |
|---|---|
| `MonasticOrderSubIndustry`, `ResortSubIndustry` | Dormant: not in `Governments.LIST`, still on the old `apply()` loops. Monastic `isAvailableToBuild` returns dev mode only; its build check `iterIndex != lastIterInMonth` (`:134`) runs on every tick but the last |
| `Concept` file | Design notes only |
| `RuralPolitySubIndustry.fixClosest` | Debug helper, only referenced from a comment |
| Forge World without spaceport | Suspected: `return` in the unrest branch also skips ship building (`:212`) |
| Decivilization | Removes the condition without `unapply`, so a Forge World or Managed Democracy listener stays registered (inferred, not tested) |
| `HandBuiltHullmod` | Repo entries are never pruned, one per hand built ship; ships built before 4.2.a keep their 5 stored effects |
| Changelog_todo | "Choose a government popup on colony establishment", "add some more info/tt/notifications to governments" |

## Maintenance

Update this doc when a variant, its numbers, its effect lines, its availability rule or the switch flow changes, or when another feature starts reading the current government. Privateer raid logic belongs in [Privateer](PRIVATEER.md); storage helpers in [Utils](../core/UTILS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
