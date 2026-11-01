# Changeling industries (governments, specialized mining and refining)

Covers the switchable industries that replace vanilla Population & Infrastructure, Mining and Refining with a selectable variant ("sub industry"), plus the hullmods, listeners and immigration plugin the government variants use. Read it before touching a government, a mining or refining specialization, or the switch dialog. It does not cover Privateer raids that feed Managed Democracy; see [Privateer](PRIVATEER.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

`IndEvo_SwitchablePopulation`, `IndEvo_SwitchableMining` and `IndEvo_SwitchableRefining` subclass the vanilla industries and return the vanilla id from `getId()` (`population`, `mining`, `refining`), so every `market.getIndustry("mining")` lookup still finds them. Each holds one `SubIndustryAPI` (`current`) picked from a static list of `SubIndustryData`; the industry delegates `apply`, image, name and description to it. The player switches variant through an industry option ("Change government...", "Change Specialization...") that opens `ChangelingIndustryDialogueDelegate`; the first switch on a vanilla industry removes it and adds the switchable one. Governments lock 7 days after a non-base, non-Anchorage pick and are only offered up to colony size `IndEvo_GovernmentMaxSize`.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/changeling/industry/SwitchableIndustryAPI.java` | Contract for switchables: `getIndustryList`, `setCurrent`, `getCurrent`, `canChange` |
| `indevo/industries/changeling/industry/SubIndustryAPI.java` | Contract for one variant: apply/unapply, image, cost, build time, availability, pather interest, tooltip hook |
| `indevo/industries/changeling/industry/SubIndustry.java` | Base variant. Defaults: cost 50000, build time 31, always available, pather interest `Float.MAX_VALUE` (means "use parent") |
| `indevo/industries/changeling/industry/SubIndustryDataAPI.java`, `SubIndustryData.java` | List entry (id, name, sprite, description id, cost); `newInstance()` builds the variant |
| `indevo/industries/changeling/industry/mining/SwitchableMining.java` | Mining switchable, 5 variants, `applySupplyAndStandardDemandWithModifiers` |
| `indevo/industries/changeling/industry/refining/SwitchableRefining.java` | Refining switchable, 3 variants |
| `indevo/industries/changeling/industry/population/SwitchablePopulation.java` | Government switchable: variant list, 7 day lock, LARP mode, size limit, tooltip |
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
| `indevo/industries/changeling/dialogue/ChangelingIndustryDialogueDelegate.java` | Variant picker (`CustomDialogDelegate`); charges credits, replaces vanilla industry, moves item/core/improvement |
| `indevo/industries/changeling/plugins/ChangelingPopulationOptionProvider.java`, `ChangelingMiningOptionProvider.java`, `ChangelingRefiningOptionProvider.java` | Industry option that opens the picker |
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
| Industry | `IndEvo_SwitchablePopulation`, `IndEvo_SwitchableMining`, `IndEvo_SwitchableRefining` | `data/campaign/industries.csv:51-53`; ids in `indevo/ids/Ids.java:75-77` |
| Variant ids | `base_population_and_infrastructure`, `underworld`, `rural`, `hidden`, `outpost`, `helldivers`, `warhammer`, `corpos` (dormant `monks`, `resort`) | `SwitchablePopulation.java:35-128` |
| Variant ids | `base_mining`, `ore_mining`, `rare_mining`, `volatile_mining`, `organics_mining` | `SwitchableMining.java:22-83` |
| Variant ids | `base_refining`, `ore_refining`, `rare_refining` | `SwitchableRefining.java:19-80` |
| Descriptions | `IndEvo_pop_*`, `IndEvo_*_mining`, `IndEvo_*_refining` (CUSTOM; text1 = description, text2 = short effect) | `data/strings/descriptions.csv:32-49` |
| Sprites | category `IndEvo`: `pop_*`, `ore_mining`, `rare_mining`, `volatile_mining`, `organics_mining`, `ore_refining`, `rare_ore_refining` | `data/config/settings.json:309-326` |
| Setting | `IndEvo_GovernmentLarpMode` (all governments act as vanilla), `IndEvo_GovernmentMaxSize` (default 3) | `data/config/LunaSettings.csv:106-107` |
| Setting | `IndEvo_SwitchableMining`, `IndEvo_SwitchableRefining` (show the option) | `data/config/LunaSettings.csv:100-101` |
| Config CSV | `IndEvo_ruralList` (planet types blocked for Rural), `IndEvo_arcologiesList` (blocked for Hidden Arcology), `IndEvo_arcologiesCondList` (conditions suppressed) | `data/config/settings.json:228-230`, `data/config/indEvo/*.csv` |
| Hullmod | `IndEvo_handBuilt`, `IndEvo_hellpods` | `data/hullmods/hull_mods.csv:14-15` |
| Memory key | `$IndEvo_HandBuiltMemoryRepo` (sector) | `HandBuiltEffectMemoryRepo.java:10` |
| Memory key | `$HasBeenExtortedByUnderworld` (fleet, never unset) | `UnderworldSubIndustry.java:56` |
| Market tag | `IndEvo_CorpoGov_MaxSize` | `CorporateGovernanceSubIndustry.java:42` |
| Memory key | `MemFlags.MARKET_CAN_ALWAYS_INCENTIVIZE_GROWTH` set by Corporate | `CorporateGovernanceSubIndustry.java:114` |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded()` (`indevo/utils/ModPlugin.java:471-479`) registers, transient and deduped by class: the three option providers, `RuralPolityTooltipAdder`, `RuralPolityImageChanger`, `MarineLossAmplifcationHullmodEffectListener`. `ManagedDemocracyNexerelinListenerPlugin` only if `nexerelin` is enabled (`ModPlugin.java:498`).
- `ModPlugin.setScriptsIfNeeded()` registers `CorporateGovernanceCampaignPlugin` every load (`ModPlugin.java:523`). `BaseCampaignPlugin` is transient with a null id (`api/campaign/BaseCampaignPlugin.java:31-37` in the knowledge base).
- The other tooltip adders (`HelldiversTooltipAdder`, `WarhammerTooltipAdder`, `CorpoPolityTooltipAdder`) register themselves from their variant's `apply()`.
- `HelldiversSubIndustry` and `WarhammerSubIndustry` add themselves as persistent listeners in `apply()` and remove themselves in `unapply()`. `ShipProductionSummaryMessageHandler.getInstanceOrRegister()` adds a persistent listener on first use.
- Nothing adds a switchable to a market at game start. The first switch on a vanilla industry replaces it: `ChangelingIndustryDialogueDelegate.customDialogConfirm()` (`:139-187`).

Switch chain: `Changeling*OptionProvider.optionSelected -> ChangelingIndustryDialogueDelegate -> customDialogConfirm -> (vanilla: market.removeIndustry + addIndustry(IndEvo_Switchable*)) -> SwitchableIndustryAPI.setCurrent(newInstance, false) -> credits -= SubIndustryData.cost`.

Apply chain: `SwitchableX.init -> setCurrent(list[0])` on first init, then `SwitchableX.apply -> current.init/apply -> (variant calls superApply() for vanilla behaviour)`.

## Flow

### Switchable shell

- `getId()` returns the vanilla id (`SwitchableMining.java:165`, `SwitchableRefining.java:140`, `SwitchablePopulation.java:195`). `Market.getIndustry` and `hasIndustry` match on `getId()` (`obf/campaign/econ/Market.java:200-218` in the knowledge base). The delegate finds the new industry by `getSpec().getId()` for that reason (`ChangelingIndustryDialogueDelegate.java:159-165`).
- `setCurrent` throws `IllegalArgumentException` if the variant id is not in the list; it unapplies the old variant first (`SwitchablePopulation.java:165-184`).
- Mining and refining are not buildable directly (`isAvailableToBuild` false); they exist only by replacement.
- `canInstallItem` in the delegate re-slots the special item only if the item spec params list the vanilla id and `ItemEffectsRepo` reports no unmet requirement; otherwise the item goes to storage (`:175-196`).

### Mining and refining

- Mining variants call `applySupplyAndStandardDemandWithModifiers(ind, ore, rareOre, organics, volatiles)` which adds a flat `_IndEvo_specialized` supply mod per commodity, then the vanilla demand and heavy machinery deficit (`SwitchableMining.java:85-102`). Ore: +2/-2/-2/-2. Transplutonics: -3/+2/-3/-3. Volatiles: -3/-3/-3/+2. Organics: -3/-3/+2/-3.
- Refining variants call vanilla `Refining.apply()` then shift metals and transplutonics supply and demand by +-2 (`SwitchableRefining.java:38-79`).
- Option providers show only on player markets and only while the matching setting is on (`ChangelingMiningOptionProvider.java:39-46`, `ChangelingRefiningOptionProvider.java:39-46`).

### Governments

- `SwitchablePopulation.apply()` clears supply and demand, then runs `superApply()` (vanilla) in LARP mode or `current.apply()` otherwise (`:143-163`). Most variants call `superApply()` first and add their changes; Underworld rebuilds the vanilla pop apply by hand (`UnderworldSubIndustry.java:172-318`).
- Lock: `advance()` counts days for a non-base, non-Anchorage variant; at `DAYS_TO_LOCK = 7` it sets `locked` and posts a message (`:239-261`). `canChange()` is dev mode or (size <= `IndEvo_GovernmentMaxSize` and not locked) (`:272-274`). `ChangelingPopulationOptionProvider.isSuitable` offers the switch on a vanilla population or a `SwitchablePopulation` that `canChange()`.
- `getPatherInterest()` uses the variant value unless it is above 1e8 (the `SubIndustry` default) (`:288-293`). Rural returns -10; Managed Democracy and Monastic return minus the market's total interest from other industries.
- Availability per variant (`isAvailableToBuild` / `getUnavailableReason`):

| Variant | Cost | Rule |
|---|---|---|
| Underworld | 45000 | always |
| Rural Polity | 80000 | planet, rich or bountiful farmland, type not in `IndEvo_ruralList` (`RuralPolitySubIndustry.java:248-271`) |
| Hidden Arcology | 125000 | planet, not gas giant, type not in `IndEvo_arcologiesList` (`HiddenArcologiesSubIndustry.java:95-111`) |
| Anchorage | 30000 | always (size reason text only, inferred missing check) |
| Managed Democracy | 100000 | dev mode or (size <= 5 and planet) (`HelldiversSubIndustry.java:342-348`) |
| Forge World | 150000 | `very_hot`, planet, not gas giant (`WarhammerSubIndustry.java:187-189`) |
| Corporate | 50000 | always |

- Underworld: income mult `5/stability` above 5, `1.2^(5-stability)` below (`UnderworldSubIndustry.java:321-333`); every day (`IntervalUtil(10,10)` seconds) takes 5% of each commodity stack from trader, smuggler and scavenger fleets in the system into storage, once per fleet (`:97-141`).
- Rural: `industrial` tag x2 upkeep and -3 stability, `rural` tag x0.5 upkeep and +1 stability and +2 growth, -1 stability per non-`rural` special item and per AI core; Farming gets +1 output, organics and (free port) drugs; Light Industry gets hand weapons and supplies (`RuralPolitySubIndustry.java:163-194`). `RuralPolityImageChanger` cycles `rural_polities_1..5.png` (`:287-306`).
- Hidden Arcology: suppresses every condition in `IndEvo_arcologiesCondList` and re-applies the suppression in `SwitchablePopulation.setSpecialItem` for the item heat condition (`SwitchablePopulation.java:220-224`).
- Managed Democracy: extra patrols by size: 1 light, plus 1 medium above size 3, plus 1 light and 1 heavy above size 4 (`HelldiversSubIndustry.java:304-306`); `correctStability()` adds a flat mod to clamp stability to 3..7 (`HelldiversSubIndustry.java:388-393`); `RaidMod` entries add income percent for raids, bombardments, Privateer raids (`PrivateerBase.java:196,205`) and Nexerelin captures; `reportEconomyTick` adds 3 XP per tick to marines stored here; `onNewDay` (via `TimeTracker`) gives destroyers in storage `IndEvo_hellpods` after 62 days.
- Forge World: +2000 income per ship hull supplied, +5000 to `Stats.CUSTOM_PRODUCTION_MOD` per hull (`WarhammerSubIndustry.java:174-184`); month end: unrest +1 on any deficit (cap 5), monthly DP budget `size - 2`, 30% chance to build a known hull with `getSuppliesToRecover() <= budget` into storage with `IndEvo_handBuilt` (`:211-257`). Adds `pollution` once locked on habitable worlds.
- Corporate: base stability 0, +1 stability per 20000 net income (max 10), base income x1.3, military upkeep x3, disruption capped at 62 days, full industry slots once locked, growth -20 and -1 per 20000 income (`CorporateGovernanceSubIndustry.java:95-217`). `CorpoImmigrationPlugin` shrinks the market when weight drops below the size minimum (size > 3) and skips growth when tagged `IndEvo_CorpoGov_MaxSize` (`CorpoImmigrationPlugin.java:44-136`).
- Anchorage: sets max size to 3, removes all industry slots, upkeep x0.7 (`OutpostSubIndustry.java:21-32`).

### Hand built hullmod

`HandBuiltHullmod.applyEffectsBeforeShipCreation` keys on `stats.getFleetMember().getId()`, rolls effects once with `Random(id.hashCode())` and stores them in `HandBuiltEffectMemoryRepo` (`HandBuiltHullmod.java:25-50`). Each effect is a mult in 0.7..1.3. Both hullmods extend `SelfRepairingBuiltInHullmod`, which re-adds itself as a perma mod ([Pets](PETS.md)).

## Contracts

- Save data: the switchable stores `current` (an anonymous inner class instance of `SwitchableX$1$1` style) in the save. Reordering or removing entries in the static `industryList` changes those class names and breaks old saves (inferred from Java anonymous class naming).
- Governments persist state in the variant object: `locked`, `daysPassed` (on the industry), `stolenGoodsList`, `raidMods`, `daysToApplicationForFleetMember`, `currentDpBudget`.
- Persistent listener dedupe: `ListenerManager.addListener` stores into an `ObjectRepository` that ignores an instance already present (`obf/campaign/ListenerManager.java:29-35`, `obf/_common/util/container/repo/ObjectRepository.java:87-90`), so repeated `addListener(this)` from `apply()` is safe.
- `market.addImmigrationModifier` keeps a persistent `LinkedHashSet` (`obf/campaign/econ/Market.java:98`); the variant object is saved with the market.
- Other features read the current government by `instanceof` on `getCurrent()`: Privateer (`PrivateerBase.java:71`, `PrivateerBaseRaidIntel.java:76`).
- Rural, Forge World, Corporate and Monastic rules key on industry spec tags `industrial`, `rural`, `military` from `data/campaign/industries.csv`; Managed Democracy uses `MiscIE.isMilitary`. `ModPlugin.addTypePrefaceToIndustrySpecs` only shows those tags in descriptions (`ModPlugin.java:281`). See [Economy](../core/ECONOMY.md).
- Storage access goes through `MiscIE.getStorageCargo`; see [Utils](../core/UTILS.md). Setting reads: [Settings](../core/SETTINGS.md).

## Known issues and dormant code

| Item | State |
|---|---|
| `MonasticOrderSubIndustry`, `ResortSubIndustry` | Dormant: commented out of `SwitchablePopulation.industryList` (`:95-100`, `:115-120`). Monastic `isAvailableToBuild` returns dev mode only; its build check `iterIndex != lastIterInMonth` (`:134`) runs on every tick but the last |
| `Concept` file | Design notes only |
| `UnderworldSubIndustry.increaseFleetSize` | Empty, body commented out (`:143-145`) |
| `RuralPolitySubIndustry.fixClosest` | Debug helper, only referenced from a comment |
| Managed Democracy raid mods | Expired `RaidMod` entries are skipped, never removed |
| Forge World shortage check | Suspected: "construction material" stability uses `LUXURY_GOODS` deficit, not metals (`WarhammerSubIndustry.java:133`) |
| Forge World without spaceport | Suspected: `return` in the unrest branch also skips ship building (`:213`) |
| Rural unapply | Suspected: apply adds organics, drugs (Farming) and supplies (Light Industry); unapply resets only Farming supplies and LI hand weapons (`RuralPolitySubIndustry.java:232-239`) |
| Underworld mismanagement unmodify | Key mismatch `modId + "_mm"` vs `"_" + modId + "_mm"` (`:378-385`) |
| `_ms` stability cleanup | Checks `mod.desc`, which is "Base value", so it never matches (`UnderworldSubIndustry.java:335`, `CorporateGovernanceSubIndustry.java:206`) |
| `HandBuiltHullmod` | Loop `i <= EFFECT_COUNT` rolls 5 effects, not 4 (`:45`); repo entries are never pruned |
| Changelog_todo | "Choose a government popup on colony establishment", "add some more info/tt/notifications to governments" |

## Maintenance

Update this doc when a variant, its numbers, its availability rule or the switch flow changes, or when another feature starts reading `getCurrent()`. Privateer raid logic belongs in [Privateer](PRIVATEER.md); storage helpers in [Utils](../core/UTILS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
