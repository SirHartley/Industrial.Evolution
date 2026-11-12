# Warehouses

The Warehouse Complex industry (`IndEvo_Warehouse`): up to five extra named storage submarkets per colony, plus the unfinished Wormhole Complex upgrade and linked (sector shared) storage. Read it before changing the extra storages or picking up the wormhole anchor work. It does not cover the base removable storage submarket; see [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The industry is unreleased: it only shows in the build list if the player faction already knows `IndEvo_Warehouse`. Once built, two industry options add or remove storage submarkets `IndEvo_WarehouseStorage_1..5`, each with a player chosen name and flags for cargo and fleet use. The planned upgrade is the Wormhole Complex (`IndEvo_Warphouse`), started by installing a Wormhole Anchor, which would link local storage into one cargo shared by every player colony. The CSV row, item row and anchor spec hook for that upgrade are commented out.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/warehouses/industry/Warehouses.java` | Industry plugin for both ids. Owns the `warehouseSubMarkets` list, add/remove submarket, item driven upgrade/downgrade, `updateLinkedStorages`, static `adjustWormholeAnchorSpec` |
| `indevo/industries/warehouses/submarket/WarehouseSubmarketPlugin.java` | Submarket plugin for `IndEvo_WarehouseStorage_*`; extends `BaseRemovableStorageSubmarketPlugin`; reads its `WarehouseSubmarketData` from the market's `Warehouses` on `init` |
| `indevo/industries/warehouses/data/WarehouseSubmarketData.java` | Saved per storage: `submarketID`, `submarketName`, `showInFleetScreen`, `showInCargoScreen`, `linked` |
| `indevo/industries/warehouses/data/WarehouseConstants.java` | `MAX_ADDITIONAL_SUBMARKETS` 5, `DAYS_UNTIL_ITEM_ACTIVE` 60 (unused) |
| `indevo/industries/warehouses/plugin/WarehouseAddSubmarketOptionProvider.java` | Industry option "Add storage...": custom dialog with name field and two checkboxes; `formatName` wraps the name to two lines of 16 chars |
| `indevo/industries/warehouses/plugin/WarehouseRemoveSubmarketOptionProvider.java` | Industry option that removes the last added storage after a confirm dialog |
| `indevo/industries/warehouses/plugin/LinkedStorageManager.java` | Sector singleton: one shared `CargoAPI` per submarket spec id; swaps a submarket's cargo between local and shared |
| `indevo/industries/warehouses/item/WormholeAnchorItemPlugin.java` | Dormant item plugin for `wormhole_anchor` (extends vanilla `WormholeAnchorPlugin`); tooltip lists installable industries |
| `indevo/industries/warehouses/plugin/wi/WarehouseManagementDialogueDelegate.java` | Excluded by `IndEvo.iml:19`; see Known issues |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_Warehouse` (`Ids.WAREHOUSES`) | `data/campaign/industries.csv:58`, tags `structure, unraidable, IndEvo_warehouse`, upkeep 2, build 30 days |
| Industry | `IndEvo_Warphouse` (`Ids.WARPHOUSE`) | `data/campaign/industries.csv:61`, commented out (`#`) |
| Submarkets | `IndEvo_WarehouseStorage_1` to `_5` (`Ids.WAREHOUSE_SUBMARKET` + `_n`) | `data/campaign/submarkets.csv:13-17`; also `data/config/indEvo/shipping_submarkets_whitelist.csv:15-19` so courier contracts can use them |
| Tag | `IndEvo_warehouse` | industry tag that both option providers match |
| Memory key | `$IndEvo_LinkedStoragePluginInstance` (`LinkedStorageManager.MEMORY_KEY`) | sector memory |
| Item | `wormhole_anchor` row with `WormholeAnchorItemPlugin` | `data/campaign/special_items.csv:72`, commented out |

CSV rows starting with `#` are skipped by the loader (skill `docs/concepts/spec-loading.md:14`).

## Registration and lifecycle

- CSV plugin column `indevo.industries.warehouses.industry.Warehouses`.
- `ModPlugin.setListenersIfNeeded()` registers `WarehouseAddSubmarketOptionProvider` and `WarehouseRemoveSubmarketOptionProvider` (`ModPlugin.java:481-482`) via `ListenerManager.addListener(new ..., true)` (transient). Base class `TagBasedSimplifiedIndustryOptionProvider` is in [Utils](../core/UTILS.md).
- `Warehouses.adjustWormholeAnchorSpec()` is commented out (`ModPlugin.java:128`).
- Saved state: `warehouseSubMarkets` and `shouldLinkLocalStorage` on the industry; `LinkedStorageManager` in sector memory.

Add chain: `option click -> dialog confirm -> Warehouses.addSubmarket(data) -> market.addSubmarket(id) -> WarehouseSubmarketPlugin.init -> Warehouses.getData(this)`.

## Flow

### Extra storages

- `optionEnabled` allows adding while fewer than 5 exist (`WarehouseAddSubmarketOptionProvider.java:33`). New id is `IndEvo_WarehouseStorage_<count + 1>` (`:39`), default name "Warehouse n".
- `addSubmarket` adds the data before `market.addSubmarket`, because the plugin looks its data up on `init` (`Warehouses.java:151`).
- `WarehouseSubmarketPlugin`: name from data, free transfer, cargo stacks illegal when `showInCargoScreen` is off, ships illegal when `showInFleetScreen` is off.
- Removal always takes the last list entry (`WarehouseRemoveSubmarketOptionProvider.java:51`); `removeSubmarket` calls `IndustrySubmarkets.remove`, which moves the content to colony storage, and drops the list entry only when that succeeded (`Warehouses.java:159`; implementation in [Submarkets](../core/SUBMARKETS.md)).
- `Warehouses.notifyBeingRemoved` unlinks storage first ("or it dumps the cores/items into the linked cargo"), then calls super, then removes all extra storages (`:177`).

### Wormhole Complex (dormant)

`apply()` (`Warehouses.java:29`) returns while building. Otherwise:

| State | Action |
|---|---|
| `IndEvo_Warehouse`, item installed, not upgrading | `startUpgrading(IndEvo_Warphouse)` with the spec build time |
| `IndEvo_Warehouse`, no item, upgrading | `cancelUpgrade()` |
| `IndEvo_Warphouse`, no item | `startUpgrading(IndEvo_Warehouse, 1)` (downgrade) |
| `IndEvo_Warphouse`, item, upgrading | `cancelUpgrade()` |

Then `updateLinkedStorages()`: on `IndEvo_Warphouse` with `shouldLinkLocalStorage` the vanilla `storage` submarket is converted to shared cargo; on any other id all storages are unlinked. Warehouse storage linking is a `todo` in both `updateLinkedStorages` and `unlinkAllSubmarkets`.

### LinkedStorageManager

- `convertToSharedCargo`: moves local content into the shared cargo for that spec id and calls `BaseSubmarketPlugin.setCargo(shared)`.
- `convertToLocalCargo`: gives the submarket a new empty cargo; the last linked submarket of that spec id takes all shared content.
- `isSharedCargo` compares cargo identity.

## Contracts

- `apply()` does not call `BaseIndustry.apply`, so CSV upkeep, AI cores and improve are not applied (inferred from `Warehouses.java:29`; `canInstallAICores` is false anyway).
- `isAvailableToBuild` needs `FactionAPI.knowsIndustry` (`Warehouses.java:78`), which checks the faction's known industry set (skill `obf/campaign/Faction.java:615`). Nothing in IE adds `IndEvo_Warehouse` to the player faction (grep), so the industry is unbuildable in normal play; `showWhenUnavailable` is false.
- Submarket ids are positional. Removal always takes the last one so ids stay contiguous.
- `WarehouseSubmarketPlugin.data` is set only on `init`; a storage whose data is missing NPEs in `getName` (inferred).

## Known issues and dormant code

| Item | State |
|---|---|
| Wormhole Complex | dormant: `#IndEvo_Warphouse` row, `#wormhole_anchor` row and `adjustWormholeAnchorSpec` call are all commented out; `startUpgrading(Ids.WARPHOUSE)` would look up a missing spec |
| `SpecialItemEffectsRepo` `wormhole_anchor` effect | still added to vanilla `ItemEffectsRepo` with "linked storages" text; see [Installables](../items/INSTALLABLES.md) |
| `WormholeAnchorItemPlugin.getName` (`WormholeAnchorItemPlugin.java:25`) | null guard calls `super.getName()` without `return`, then NPEs |
| `WarehouseManagementDialogueDelegate` | excluded by `IndEvo.iml:19`; copy of the museum parade delegate calling methods `Warehouses` lacks (`setFlyParades`, `getParadeFleetProfiles`, `getMaxParades`, `getSubmarket`); does not compile |
| `WarehouseSubmarketData.linked`, `DAYS_UNTIL_ITEM_ACTIVE` | unused |
| Open issue | `Changelog_todo:11-12`: "warehouse storage linking", "implement 5 more shared storage variants that can be linked, no switching default ones (too many issues)" |

## Maintenance

Update this doc when storage count, the option dialogs, the CSV rows or the linking logic change. Removable storage behaviour belongs in [Submarkets](../core/SUBMARKETS.md), item effects in [Installables](../items/INSTALLABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
