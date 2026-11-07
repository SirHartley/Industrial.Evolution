# Engineering Hub

Covers the Engineering Hub (`IndEvo_EngHub`): reverse engineering ships left in its storage into blueprint progress, and turning 100% progress into ship blueprints or retrofit templates. Read it before changing research values, timing, blueprint consumption or relic use. It does not cover the research projects system (`indevo/dialogue/research`, key `$IndEvo_ResearchProgress_<id>`), which is unrelated despite the similar memory key; see [Research](../dialogue/RESEARCH.md). The intake submarket `IndEvo_EngStorage` and its whitelist checks are owned by [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The hub adds a ship drop off submarket (`IndEvo_EngStorage`) and the shared industry storage (`IndEvo_SharedStore`). Each day it takes one eligible ship from the drop off, strips it into local storage, and works on it for 15 days (21 for capitals, less with cores). On finish it adds 35% (45% for capitals, minus a D-mod penalty) to a sector wide progress map keyed by base hull id. A hull at 100% is turned into a blueprint once the hub can consume a blueprint of the same hull size (installed in the hub or lying in shared storage) or, with the relic item installed, `10 x supplies to recover` relic components from shared storage. Tiandong and Roider hulls produce retrofit templates and need matching retrofit items.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/engineeringhub/industry/EngineeringHub.java` | Industry: daily deconstruction state machine, progress map, completion and delivery, installed blueprint slot, relic mode, AI cores, tooltip sections |
| `indevo/industries/engineeringhub/patches/RelicInstallDialogPatch.java` | PatchLib patch on the vanilla install item dialog: lists relic components, keeps the relic item out of cargo |

Neighbours used: `indevo/industries/SharedSubmarketUser.java` (base class), `indevo/items/installable/BlueprintInstallableItemPlugin.java` (install slot, [Installables](../items/INSTALLABLES.md)), `indevo/submarkets/impl/ReverseEngineeringSubmarketPlugin.java` (intake rules).

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_EngHub` (`Ids.ENGHUB`, structure, `unraidable`) | `data/campaign/industries.csv:42`, `indevo/ids/Ids.java:61` |
| Submarket | `IndEvo_EngStorage` (`Ids.ENGSTORAGE`), `IndEvo_SharedStore` (`Ids.SHAREDSTORAGE`) | `indevo/ids/Ids.java:40-41` |
| Memory key | `$IndEvo_researchProgress` (sector, `Map<String, Float>` base hull id -> progress) | `EngineeringHub.java:42` |
| Setting | `IndEvo_AutoshipBPToGatheringPoint` (deliver to gathering point) | `data/config/LunaSettings.csv:137`, read at `EngineeringHub.java:138` |
| Setting | `IndEvo_EngHubStrictWhitelist`, `IndEvo_EngHubIgnoreWhitelists` (intake) | `data/config/LunaSettings.csv:135-136`, read in `ReverseEngineeringSubmarketPlugin` |
| Setting | `IndEvo_EngHub` (build toggle) | `data/config/LunaSettings.csv:104`; read in `isAvailableToBuild()` and `showWhenUnavailable()` (`EngineeringHub.java:55-62`). A hub already built stays |
| Config CSV | `IndEvo_reverseList`: manual hull whitelist | `data/config/settings.json:53`, `data/config/indEvo/reverse_engineering_whitelist.csv` |
| Item | `ItemIds.RELIC_SPECIAL_ITEM` (relic mode slot item, never in cargo), `ItemIds.RARE_PARTS` (relic components) | `indevo/ids/ItemIds.java` |
| Item | `ship_bp`, `tiandong_retrofit_bp`, `roider_retrofit_bp` (output and consumed input) | `EngineeringHub.java:229-251`, `:711-716` |
| Sprite | category `IndEvo`: `revBP` (finish message icon) | `data/config/settings.json` |

## Registration and lifecycle

- Industry plugin from `industries.csv`. While functional and player owned, `apply()` adds the hub as a transient listener; `TimeTracker` then calls `onNewDay` ([Utils](../core/UTILS.md)). `unapply()` removes it (`EngineeringHub.java:65-93`).
- `advance()` keeps `IndEvo_EngStorage` through `IndustrySubmarkets.ensure` (`:79`), the shared storage through the `SharedSubmarketUser` base; `notifyBeingRemoved()` releases both through the base ([Submarkets](../core/SUBMARKETS.md#registration-and-lifecycle)).
- `ModPlugin.loadTransientMemory()` warms the `IndEvo_reverseList` cache (`indevo/utils/ModPlugin.java:413`).
- `ModUpdater` keeps `currentDeconShipVar` across an industry replacement on mod update (`indevo/utils/update/ModUpdater.java:94,136`).

Daily chain: `TimeTracker -> EngineeringHub.onNewDay -> (idle) initDeconstruction | (done) addProgressToList + message | daysPassed++ -> for first hull at >= 1.0: relic payment or consumeMatchingSpecial -> deliverReverseEngineered -> storeMapInMemory`.

## Flow

### Intake and deconstruction

- Intake: `ReverseEngineeringSubmarketPlugin.isIllegalOnSubmarket` allows a hull if it is on `IndEvo_reverseList` or (unless strict) has any tag containing `_bp`, is not a Prism or Vayra boss hull or tagged `threat` / `dweller`, has research value above 0 and is not printed. `IndEvo_EngHubIgnoreWhitelists` drops the list checks; dev mode allows anything (`ReverseEngineeringSubmarketPlugin.java:45-160`).
- `initDeconstruction()` takes the first ship in `IndEvo_EngStorage` whose base hull is below 100%, strips weapons and fighters into the gathering point storage (local storage if the gathering point has none) with `MiscIE.stripShipToCargoAndReturnVariant`, removes the ship and stores its variant in `currentDeconShipVar` (`EngineeringHub.java:299-319`).
- Days: `round(sizeValue * 57) - 5` = 15 (frigate to cruiser) or 21 (capital), x0.7 beta, x0.85 alpha, truncated to int (`:392-416`).
- Value: 0.35 (frigate to cruiser) or 0.45 (capital), minus `0.05n * (1 + 0.05n * n)` for `n` non built in D-mods (alpha ignores one), rounded to 0.01, floored at 0 (`:364-390`).
- Base hull: D-hull parent or `getBaseHull()` for restore to base hulls, so skins and D variants share progress (`:338-359`).

### Completion

- Per day the hub processes at most one hull at >= 100% (`:130-167`).
- Relic mode (installed item is `RELIC_SPECIAL_ITEM`): pays `round(suppliesToRecover * 10)` relic components from shared storage if enough are there (`:148-160`, `:207-219`).
- Otherwise `consumeMatchingSpecial` takes the installed item, then any shared storage item, whose hull size matches; for Tiandong or Roider hulls the item must be the matching retrofit item (`:175-216`).
- Output: `ship_bp` with the hull id, or the retrofit item, to local storage or the gathering point storage. The hull leaves the progress map; overflow above 1.0 is clamped on save (`:150-169`).

### Relic components in the install dialog

The vanilla install item dialog (obf `com.fs.starfarer.campaign.ui.marketinfo.OOoo` on 0.98a-RC8 Windows, knowledge skill `obf/campaign/ui/marketinfo/_c35.java`) lists the stacks of copies of the player fleet cargo (local only), storage and local resources that pass `isInstallableItem` (`:134-183`). Picking moves items between the real cargo and the slot: swap and uninstall put the old item back with `CargoAPI.addItems` (`:241-267`), the pick is taken out with `removeItems`, cancel puts the pending item back and takes the original out again (`:224-235`).

`RelicInstallDialogPatch` (target picked by shape: `UIPanelAPI` subtype in that package with an `InstallableIndustryItemPlugin` field and the private `(CargoAPI, List, String)` collector; inferred):
- Redirects `getStacksCopy()` inside the collector. When the dialog's plugin is `BlueprintInstallableItemPlugin` and the cargo is the hub's `Misc.getStorageCargo`, it adds one `RELIC_SPECIAL_ITEM` stack to the copied list. The row shows "In storage"; storage is not changed.
- Redirects every `CargoAPI.addItems` call in the dialog and drops it when the item is `RELIC_SPECIAL_ITEM`. Taking the relic out of storage is a no-op since it is not there.
- The relic row stays listed while installed; picking it again changes nothing.

`notifyBeingRemoved` does not refund `RELIC_SPECIAL_ITEM` (`EngineeringHub.java:481-490`). Nothing else creates it, so it exists only in the dialog and the hub's slot.

### AI cores

Gamma: upkeep x0.2 while idle. Beta: x0.7 time. Alpha: x0.85 time, one D-mod ignored (`:694-706`, `:392-413`).

## Contracts

- Progress is sector wide: every hub reads `$IndEvo_researchProgress` at the start of `onNewDay` and writes it back at the end, so several hubs share and race on one map (each writes its full copy; inferred safe because calls are sequential on the campaign thread).
- `getMapFromMemory` returns a sorted copy, not the stored map; always write back with `storeMapInMemory` (`indevo/utils/helper/MiscIE.java:335-357`).
- Saved per hub: `currentDeconShipVar`, `daysRequired`, `daysPassed`, `blueprintItem`.
- `getSpecialItem` / `setSpecialItem` are overridden to the `blueprintItem` field; removing the hub refunds it unless upgrading or it is the relic item (`:466-490`).
- Tooltip: vanilla `createTooltip`. The override only stores `expanded` in a transient field for the hooks (`:633`). Collapsed: current project after upkeep (`addPostUpkeepSection`, `:558`). Expanded (`NORMAL` mode only): progress list and current project right after the description (`addRightAfterDescriptionSection`, `:544`), vanilla body below.
- `isTiandong` / `isRoider` are also used by Privateer loot (`indevo/industries/privateer/industry/PrivateerBase.java:322-324`); see [Privateer](PRIVATEER.md).

## Known issues and dormant code

| Item | State |
|---|---|
| Dev mode | `debug = isDevMode()` finishes every deconstruction the next day (`:68`, `:121`) |
| Any item matches normal hulls | `matchesForFaction` returns true for any item on non Tiandong, non Roider hulls, so a same size retrofit template in shared storage can be consumed for a normal blueprint (`:227-233`) |
| Changelog_todo | "100% progress items in reverse engineering list should be at the top"; "check if BP creation works with relics"; "Allow using relic components to get BPs of ships in engHub" |

## Maintenance

Update this doc when research values, timing, the completion rules, the relic path or delivery change. Intake whitelist logic belongs in [Submarkets](../core/SUBMARKETS.md); the blueprint install slot in [Installables](../items/INSTALLABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
