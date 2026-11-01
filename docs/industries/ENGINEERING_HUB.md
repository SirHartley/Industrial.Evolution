# Engineering Hub

Covers the Engineering Hub (`IndEvo_EngHub`): reverse engineering ships left in its storage into blueprint progress, and turning 100% progress into ship blueprints or retrofit templates. Read it before changing research values, timing, blueprint consumption or relic use. It does not cover the research projects system (`indevo/dialogue/research`, key `$IndEvo_ResearchProgress_<id>`), which is unrelated despite the similar memory key; see [Research](../dialogue/RESEARCH.md). The intake submarket `IndEvo_EngStorage` and its whitelist checks are owned by [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The hub adds a ship drop off submarket (`IndEvo_EngStorage`) and the shared industry storage (`IndEvo_SharedStore`). Each day it takes one eligible ship from the drop off, strips it into local storage, and works on it for 15 days (21 for capitals, less with cores). On finish it adds 35% (45% for capitals, minus a D-mod penalty) to a sector wide progress map keyed by base hull id. A hull at 100% is turned into a blueprint once the hub can consume a blueprint of the same hull size (installed in the hub or lying in shared storage) or, with the relic item installed, `10 x supplies to recover` relic components from shared storage. Tiandong and Roider hulls produce retrofit templates and need matching retrofit items.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/engineeringhub/industry/EngineeringHub.java` | Industry: daily deconstruction state machine, progress map, completion and delivery, installed blueprint slot, relic hack, AI cores, custom tooltip |
| `indevo/industries/engineeringhub/listener/EngineeringHubRelicItemAdder.java` | `@Deprecated` `CoreUITabListener` that adds or removes the relic dummy item on tab open. Not registered anywhere (dead) |

Neighbours used: `indevo/industries/SharedSubmarketUser.java` (base class), `indevo/items/installable/BlueprintInstallableItemPlugin.java` (install slot, [Installables](../items/INSTALLABLES.md)), `indevo/submarkets/impl/ReverseEngineeringSubmarketPlugin.java` (intake rules).

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_EngHub` (`Ids.ENGHUB`, structure, `unraidable`) | `data/campaign/industries.csv:42`, `indevo/ids/Ids.java:61` |
| Submarket | `IndEvo_EngStorage` (`Ids.ENGSTORAGE`), `IndEvo_SharedStore` (`Ids.SHAREDSTORAGE`) | `indevo/ids/Ids.java:40-41` |
| Memory key | `$IndEvo_researchProgress` (sector, `Map<String, Float>` base hull id -> progress) | `EngineeringHub.java:46` |
| Setting | `IndEvo_AutoshipBPToGatheringPoint` (deliver to gathering point) | `data/config/LunaSettings.csv:112`, read at `EngineeringHub.java:148` |
| Setting | `IndEvo_EngHubStrictWhitelist`, `IndEvo_EngHubIgnoreWhitelists` (intake) | `data/config/LunaSettings.csv:110-111`, read in `ReverseEngineeringSubmarketPlugin` |
| Setting | `IndEvo_EngHub` (build toggle) | `data/config/LunaSettings.csv:97`; read in `isAvailableToBuild()` and `showWhenUnavailable()` (`EngineeringHub.java:57-64`). A hub already built stays |
| Config CSV | `IndEvo_reverseList`: manual hull whitelist | `data/config/settings.json:219`, `data/config/indEvo/reverse_engineering_whitelist.csv` |
| Item | `ItemIds.RELIC_SPECIAL_ITEM` (relic mode slot item), `ItemIds.RARE_PARTS` (relic components) | `indevo/ids/ItemIds.java` |
| Item | `ship_bp`, `tiandong_retrofit_bp`, `roider_retrofit_bp` (output and consumed input) | `EngineeringHub.java:235-257`, `:1096-1101` |
| Sprite | category `IndEvo`: `revBP` (finish message icon) | `data/config/settings.json` |

## Registration and lifecycle

- Industry plugin from `industries.csv`. While functional and player owned, `apply()` adds the hub as a transient listener; `TimeTracker` then calls `onNewDay` ([Utils](../core/UTILS.md)). `unapply()` removes it (`EngineeringHub.java:67-103`).
- `advance()` adds `IndEvo_EngStorage` and the shared storage through `SubMarketAddOrRemovePlugin` while functional; `unapply()` queues their removal (`:81-103`).
- `ModPlugin.loadTransientMemory()` warms the `IndEvo_reverseList` cache (`indevo/utils/ModPlugin.java:424`).
- `ModUpdater` keeps `currentDeconShipVar` across an industry replacement on mod update (`indevo/utils/update/ModUpdater.java:94,136`).

Daily chain: `TimeTracker -> EngineeringHub.onNewDay -> (idle) initDeconstruction | (done) addProgressToList + message | daysPassed++ -> for first hull at >= 1.0: relic payment or consumeMatchingSpecial -> deliverReverseEngineered -> storeMapInMemory`.

## Flow

### Intake and deconstruction

- Intake: `ReverseEngineeringSubmarketPlugin.isIllegalOnSubmarket` allows a hull if it is on `IndEvo_reverseList` or (unless strict) has any tag containing `_bp`, is not a Prism or Vayra boss hull or tagged `threat` / `dweller`, has research value above 0 and is not printed. `IndEvo_EngHubIgnoreWhitelists` drops the list checks; dev mode allows anything (`ReverseEngineeringSubmarketPlugin.java:45-160`).
- `initDeconstruction()` takes the first ship in `IndEvo_EngStorage` whose base hull is below 100%, strips weapons and fighters into the gathering point storage (local storage if the gathering point has none) with `MiscIE.stripShipToCargoAndReturnVariant`, removes the ship and stores its variant in `currentDeconShipVar` (`EngineeringHub.java:323-343`).
- Days: `round(sizeValue * 57) - 5` = 15 (frigate to cruiser) or 21 (capital), x0.7 beta, x0.85 alpha, truncated to int (`:416-440`).
- Value: 0.35 (frigate to cruiser) or 0.45 (capital), minus `0.05n * (1 + 0.05n * n)` for `n` non built in D-mods (alpha ignores one), rounded to 0.01, floored at 0 (`:388-414`).
- Base hull: D-hull parent or `getBaseHull()` for restore to base hulls, so skins and D variants share progress (`:362-383`).

### Completion

- Per day the hub processes at most one hull at >= 100% (`:140-177`).
- Relic mode (installed item is `RELIC_SPECIAL_ITEM`): pays `round(suppliesToRecover * 10)` relic components from shared storage if enough are there (`:146-158`, `:207-215`).
- Otherwise `consumeMatchingSpecial` takes the installed item, then any shared storage item, whose hull size matches; for Tiandong or Roider hulls the item must be the matching retrofit item (`:185-222`).
- Output: `ship_bp` with the hull id, or the retrofit item, to local storage or the gathering point storage. The hull leaves the progress map; overflow above 1.0 is clamped on save (`:160-179`).

### Relic dummy item

To show relic components in the vanilla install dialog, `BlueprintInstallableItemPlugin.getMenuItemTitle()` puts one `RELIC_SPECIAL_ITEM` into local storage, and `EngineeringHub.apply()` removes all of them from local storage and the player fleet afterwards (`BlueprintInstallableItemPlugin.java:168-180`, `EngineeringHub.java:258-276`).

### AI cores

Gamma: upkeep x0.2 while idle. Beta: x0.7 time. Alpha: x0.85 time, one D-mod ignored (`:1079-1091`, `:416-437`).

## Contracts

- Progress is sector wide: every hub reads `$IndEvo_researchProgress` at the start of `onNewDay` and writes it back at the end, so several hubs share and race on one map (each writes its full copy; inferred safe because calls are sequential on the campaign thread).
- `getMapFromMemory` returns a sorted copy, not the stored map; always write back with `storeMapInMemory` (`indevo/utils/helper/MiscIE.java:385-407`).
- Saved per hub: `currentDeconShipVar`, `daysRequired`, `daysPassed`, `blueprintItem`.
- `getSpecialItem` / `setSpecialItem` are overridden to the `blueprintItem` field; removing the hub refunds it unless upgrading (`:480-505`).
- `createTooltip` is a full copy of the vanilla industry tooltip with the progress sections added (`:640-1004`); vanilla tooltip changes do not reach it.
- `isTiandong` / `isRoider` are also used by Privateer loot (`indevo/industries/privateer/industry/PrivateerBase.java:324-326`); see [Privateer](PRIVATEER.md).

## Known issues and dormant code

| Item | State |
|---|---|
| `EngineeringHubRelicItemAdder` | `@Deprecated`, never registered (dead) |
| Dev mode | `debug = isDevMode()` finishes every deconstruction the next day (`:70`, `:125`) |
| Any item matches normal hulls | `matchesForFaction` returns true for any item on non Tiandong, non Roider hulls, so a same size retrofit template in shared storage can be consumed for a normal blueprint (`:227-233`) |
| Changelog_todo | "100% progress items in reverse engineering list should be at the top"; "check if BP creation works with relics"; "Allow using relic components to get BPs of ships in engHub" |

## Maintenance

Update this doc when research values, timing, the completion rules, the relic path or delivery change. Intake whitelist logic belongs in [Submarkets](../core/SUBMARKETS.md); the blueprint install slot in [Installables](../items/INSTALLABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
