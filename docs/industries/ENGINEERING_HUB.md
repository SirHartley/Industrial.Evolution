# Engineering Hub

Covers the Engineering Hub (`IndEvo_EngHub`): reverse engineering ships left in its storage into blueprint progress, turning 100% progress into ship blueprints or retrofit templates, and learning hullmods recovered from stripped ships for relic components. Read it before changing research values, timing, blueprint consumption, hullmod extraction or relic use. It does not cover the research projects system (`indevo/dialogue/research`, key `$IndEvo_ResearchProgress_<id>`), which is unrelated despite the similar memory key; see [Research](../dialogue/RESEARCH.md). The intake submarket `IndEvo_EngStorage` and its whitelist checks are owned by [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The hub adds a ship drop off submarket (`IndEvo_EngStorage`) and the shared industry storage (`IndEvo_SharedStore`). Each day it takes one eligible ship from the drop off, strips it into local storage, and works on it for 15 days (21 for capitals, less with cores). On finish it adds 35% (45% for capitals, minus a D-mod penalty) to a sector wide progress map keyed by base hull id. A hull at 100% waits until the player finishes it in the hub panel, paying with a ship blueprint of the same hull size (installed in the hub or in shared storage) or `10 x supplies to recover` relic components from shared storage. With "Finish on its own" on, the daily step finishes it with the first matching blueprint. Tiandong and Roider hulls produce retrofit templates and need matching retrofit items.

When the hub strips a ship it also records the ship's learnable hullmods. The panel's Hullmod data tab lists them; paying relic components teaches one to the player faction at once.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/engineeringhub/industry/EngineeringHub.java` | Industry: daily deconstruction state machine, progress map, payment sources (`getBlueprintSources`, `paysFor`), `finish`, delivery, installed blueprint slot, "Finish on its own" flag, AI cores, tooltip sections. `RelicSpeedUpTarget` (Accelerate Research) |
| `indevo/industries/engineeringhub/memory/RecoveredHullmods.java` | Hullmod extraction: record on strip, learnable filter, cost, payment, learning |
| `indevo/industries/engineeringhub/plugins/EngineeringHubOptionProvider.java` | `BaseSimpleBaseIndustryOptionProvider`: "Visit the Engineering Hub..." option, opens the panel |
| `indevo/industries/engineeringhub/dialogue/EngineeringHubPanelDelegate.java` | Hub panel (`BaseCustomDialogDelegate` on the [UI kit](../core/UI.md)): header, tabs, Hullmod data tab. Static `show(market, view)` opens it in its own interaction dialog (nested `Host`) |
| `indevo/industries/engineeringhub/dialogue/ReverseEngineeringTab.java` | Reverse engineering tab: ship in work with Accelerate, drop off, progress table, payment list and Finish |
| `indevo/industries/engineeringhub/dialogue/EngineeringHubView.java` | View state: tab (`hullmods`), selected hullmod id, selected hull id, picked payment (`INSTALLED`, `RELICS` or `itemId|data`), scroll offsets. Null selections pick the first row, a null payment the first blueprint, else relics |

Neighbours used: `indevo/industries/SharedSubmarketUser.java` (base class), `indevo/items/installable/BlueprintInstallableItemPlugin.java` (install slot, [Installables](../items/INSTALLABLES.md)), `indevo/submarkets/impl/ReverseEngineeringSubmarketPlugin.java` (intake rules).

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_EngHub` (`Ids.ENGHUB`, structure, `unraidable`) | `data/campaign/industries.csv:42`, `indevo/ids/Ids.java:65` |
| Submarket | `IndEvo_EngStorage` (`Ids.ENGSTORAGE`), `IndEvo_SharedStore` (`Ids.SHAREDSTORAGE`) | `indevo/ids/Ids.java:44-45` |
| Memory key | `$IndEvo_researchProgress` (sector, `Map<String, Float>` base hull id -> progress) | `EngineeringHub.java:44` |
| Memory key | `$IndEvo_engHubHullmodData` (market memory, `LinkedHashMap<String, String>` hullmod id -> source hull id) | `RecoveredHullmods.MEM_KEY` |
| Setting | `IndEvo_AutoshipBPToGatheringPoint` (deliver to gathering point) | `data/config/LunaSettings.csv:137`, read in `EngineeringHub.getDeliveryMarket` |
| Setting | `IndEvo_EngHubStrictWhitelist`, `IndEvo_EngHubIgnoreWhitelists` (intake) | `data/config/LunaSettings.csv:135-136`, read in `ReverseEngineeringSubmarketPlugin` |
| Setting | `IndEvo_EngHub` (build toggle) | `data/config/LunaSettings.csv:104`; read in `isAvailableToBuild()` and `showWhenUnavailable()` (`EngineeringHub.java:68-75`). A hub already built stays |
| Config CSV | `IndEvo_reverseList`: manual hull whitelist | `data/config/settings.json:12`, `data/config/indEvo/reverse_engineering_whitelist.csv` |
| Item | `ItemIds.RARE_PARTS` (relic components) | `indevo/ids/ItemIds.java` |
| Item | `ship_bp`, `tiandong_retrofit_bp`, `roider_retrofit_bp` (output and consumed input) | `EngineeringHub.java:276-298` (`deliverReverseEngineered`), `getPaymentItemId`, `:746-751` |
| Sprite | category `IndEvo`: `revBP` (finish message icon) | `data/config/settings.json` |

## Registration and lifecycle

- Industry plugin from `industries.csv`. While functional and player owned, `apply()` adds the hub as a transient listener; `TimeTracker` then calls `onNewDay` ([Utils](../core/UTILS.md)). `unapply()` removes it (`EngineeringHub.java:78-99`).
- `advance()` keeps `IndEvo_EngStorage` through `IndustrySubmarkets.ensure` (`:91`), the shared storage through the `SharedSubmarketUser` base; `notifyBeingRemoved()` releases both through the base ([Submarkets](../core/SUBMARKETS.md#registration-and-lifecycle)).
- `EngineeringHubOptionProvider.register()` (`indevo/utils/ModPlugin.java`, `setListenersIfNeeded`) adds a transient listener once.
- `ModPlugin.loadTransientMemory()` warms the `IndEvo_reverseList` cache (`indevo/utils/ModPlugin.java:413`).
- `ModUpdater` keeps `currentDeconShipVar` across an industry replacement on mod update (`indevo/utils/update/ModUpdater.java:94,136`).

Daily chain: `TimeTracker -> EngineeringHub.onNewDay -> (idle) initDeconstruction | (done) addProgressToList + message | daysPassed++ -> (finishOnOwn) first hull at >= 1.0 with a blueprint source: consume -> deliver -> storeMapInMemory`.

Panel chain: "Visit the Engineering Hub..." -> `EngineeringHubPanelDelegate.show(ui, hub, new EngineeringHubView())` -> `DialogCreatorUI.showDialog`; Finish -> `EngineeringHub.finish(hullId, source)`.

## Flow

### Intake and deconstruction

- Intake: `ReverseEngineeringSubmarketPlugin.isIllegalOnSubmarket` allows a hull if it is on `IndEvo_reverseList` or (unless strict) has any tag containing `_bp`, is not a Prism or Vayra boss hull or tagged `threat` / `dweller`, has research value above 0 and is not printed. `IndEvo_EngHubIgnoreWhitelists` drops the list checks; dev mode allows anything (`ReverseEngineeringSubmarketPlugin.java:42-164`). The list checks are the static `getAllowedHulls`, `getRestrictedHulls` and `isWhitelisted`, also used by the Salvage Yards Wreck Registry ([Salvage Yards](SALVAGE_YARDS.md#wreck-registry)).
- `initDeconstruction()` takes the first ship in `IndEvo_EngStorage` whose base hull is below 100%, strips weapons and fighters into the gathering point storage (local storage if the gathering point has none) with `MiscIE.stripShipToCargoAndReturnVariant`, removes the ship and stores its variant in `currentDeconShipVar` (`EngineeringHub.java:340-361`).
- Days: `getBaseDays`, `round(sizeValue * 57) - 5` = 15 (frigate to cruiser) or 21 (capital), x0.7 beta, x0.85 alpha, truncated to int.
- Accelerate Research: `EngineeringHub` is a `RelicSpeedUpTarget`. The industry option and the panel's Accelerate button halve the days left of the current hull (adds to `daysPassed`) for 1 Relic Component per remaining day, once per 60 days. See [Economy](../core/ECONOMY.md#relic-speed-up).
- Value: 0.35 (frigate to cruiser) or 0.45 (capital), minus `0.05n * (1 + 0.05n * n)` for `n` non built in D-mods (alpha ignores one), rounded to 0.01, floored at 0 (`:406-432`).
- Base hull: D-hull parent or `getBaseHull()` for restore to base hulls, so skins and D variants share progress (`:380-400`).

### Completion

- Payment sources (`getBlueprintSources`): the item installed in the hub, then every stack in `IndEvo_SharedStore`, that is a `ship_bp` (or `tiandong_retrofit_bp` / `roider_retrofit_bp` for Tiandong / Roider hulls, `getPaymentItemId`) whose written hull has the same hull size. No other special item pays.
- Relic payment: `getRelicCost` = `round(suppliesToRecover * 10)` relic components from `IndEvo_SharedStore` only (`getRelicStock`); not `RelicHelper`, which reads local storage and fleet cargo.
- `finish(hullId, source)` (panel Finish): reloads the progress map, checks the hull is at >= 100%, takes one item from the source (null: the relic cost), removes the hull from the map, delivers and stores the map. Returns false and takes nothing when the payment is gone.
- "Finish on its own" (`finishOnOwn`, saved per hub, off by default; panel checkbox): the daily step finishes at most one hull at >= 100% with its first payment source. Never pays with relic components. Off: a hull stays at 100% until finished in the panel.
- Output (`deliverReverseEngineered`, unchanged): `ship_bp` with the hull id, or the retrofit item, to the storage of `getDeliveryMarket()` (the gathering point when `IndEvo_AutoshipBPToGatheringPoint` is on and it has storage, else the hub's market), with a campaign message. Overflow above 1.0 is clamped on save.

### Panel

Board 13 and 14, 1004 x 640 or less on small screens; the title `?` holds the rules and rates of the open tab.

- Reverse engineering tab, left: "In work" card with the hull, what it adds (`getResearchValue`, D-mods counted), a day bar, days left and Accelerate (relic icon and cost; grey with the reason in red under it: "Unavailable for another N days.", "Too few days left.", "Not enough Relic Components."; tooltip `RelicSpeedUpDialog.addTooltip`). "Waiting in the drop off": the `IndEvo_EngStorage` ships as one icon row ending in `+N`. "Blueprint progress": scrolling rows with hull, size, a progress bar (green at 100%) and Next: "Ready to finish", "In work" or "N more ships" (`ceil((1 - progress) / clean value)`, ships without D-mods). Ready hulls sort on top, then by progress.
- Right, hull at 100%: "Blueprint to overwrite" (or "Retrofit item to overwrite") as a scrolling list of the payment sources with item icon, written hull and where it is; one fixed row "Relic Components instead" with cost and stock. One row is picked, nothing is taken before Finish. Below: You get, You lose, "Delivered to", a Finish (grey with the reason when nothing can pay) and the "Finish on its own" checkbox with its tooltip.
- Right, hull below 100%: progress, what a clean ship adds, ships needed, status, and the checkbox.

### Hullmod extraction

- Record: `initDeconstruction()` calls `RecoveredHullmods.record(market, variant)` before stripping. It adds every hullmod of the ship and its station modules that is learnable and not built into that hull, keyed by hullmod id with the source hull id; an id already recorded keeps its first source.
- Learnable: the `FleetEncounterContext.lootHullMods` filter (not known to the player faction, not hidden, not hidden everywhere, no `no_drop` tag) plus no `dmod` tag (`RecoveredHullmods.isLearnable`).
- `getLearnable` drops ids that stopped being learnable (learned elsewhere, mod removed) and writes the map back. The option tooltip and the panel both call it.
- Cost: `max(25, round(baseValue / 100))` relic components (`MIN_COST`, `BASE_VALUE_PER_RELIC`); vanilla learnable hullmods cost 25 to 100.
- Payment: colony storage first, then fleet cargo, also when the option is used from the remote colony screen. Learning an id that is not learnable or not affordable throws (the panel never offers it). Learning calls `FactionAPI.addKnownHullMod`, removes the entry and shows a campaign message. No processing time.
- Option: "Visit the Engineering Hub..." on a functional, player owned hub, always enabled. Tooltip: what the panel does, recovered count, lore text in gray.
- Panel, Hullmod data tab (board 13): title with `?` (where the data comes from, minimum cost, where relics are taken), the Relic Components stock right. Left: scrolling rows with the hullmod icon, name, OP per hull size (`4 / 8 / 15 / 25 OP`), the source hull sprite and name, cost with the relic icon (red when short). Right: scrolling detail with the vanilla description (`HullModSpecAPI.getDescription` for the source hull size; empty if the effect throws with a null ship), the OP table, the source hull; pinned below it and never scrolling, the cost row, a Learn (grey with "Not enough Relic Components." when short) and the lore text in grey. Empty list: heading and "No recovered hullmod data.". The panel is rebuilt after each learn and keeps its scroll offsets.

### AI cores

Gamma: upkeep x0.2 while idle. Beta: x0.7 time. Alpha: x0.85 time, one D-mod ignored (`:729-740`, `:443-457`).

## Contracts

- Progress is sector wide: every hub reads `$IndEvo_researchProgress` at the start of `onNewDay` and writes it back at the end, so several hubs share and race on one map (each writes its full copy; inferred safe because calls are sequential on the campaign thread).
- `getMapFromMemory` returns a sorted copy, not the stored map; always write back with `storeMapInMemory` (`indevo/utils/helper/MiscIE.java:320-342`).
- Saved per hub: `currentDeconShipVar`, `daysRequired`, `daysPassed`, `blueprintItem`, `finishOnOwn`. Recovered hullmods are saved per market in `$IndEvo_engHubHullmodData` and stay when the hub is removed; the option only shows on a hub.
- `getSpecialItem` / `setSpecialItem` are overridden to the `blueprintItem` field; removing the hub refunds it unless upgrading.
- Tooltip: vanilla `createTooltip`. The override only stores `expanded` in a transient field for the hooks (`:668`). Collapsed: current project after upkeep (`addPostUpkeepSection`, `:598`). Expanded (`NORMAL` mode only): progress list and current project right after the description (`addRightAfterDescriptionSection`, `:584`), vanilla body below.
- `isTiandong` / `isRoider` are also used by Privateer loot (`indevo/industries/privateer/industry/PrivateerBase.java:322-324`); see [Privateer](PRIVATEER.md).

## Known issues and dormant code

| Item | State |
|---|---|
| Dev mode | `debug = isDevMode()` finishes every deconstruction the next day (`:80`, `:121`) |
| Changelog_todo | "check if BP creation works with relics" (the panel shows ready hulls on top and pays with relic components, not tested in game) |

## Maintenance

Update this doc when research values, timing, the completion rules, the relic path, hullmod extraction or delivery change. Intake whitelist logic belongs in [Submarkets](../core/SUBMARKETS.md); the blueprint install slot in [Installables](../items/INSTALLABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
