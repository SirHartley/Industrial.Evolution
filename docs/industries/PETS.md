# Pets

The Animal Center industry (`IndEvo_PetStore`), the pet submarket, pet items, pet hullmods, pet life cycle and the Pet Management ability. Read it before changing pets, pet food, pet storage or `data/campaign/pets.csv`. It does not cover the abandoned pet center derelict ([Stations](../exploration/STATIONS.md)), the submarket add/remove script ([Submarkets](../core/SUBMARKETS.md)) or the ghost ship hullmods that reuse `PetHullmod` ([Distress](../exploration/DISTRESS.md)).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

A pet is a `Pet` object bound to one fleet member (perma hullmod plus variant tag) or stored in an Animal Center. Pets age, eat food commodities from fleet cargo (or from the storage their ship sits in) once a month, and die of old age, starvation, combat loss, sale or euthanasia. The pet's hullmod effect grows with time served. The Animal Center sells pets as `IndEvo_PetBox` special items, stores pets and opens the management panel. The `pet_management` ability opens the same panel from anywhere, fleet pets only.

## Files

Code paths relative to `jars/src/indevo/`.

| File | Owner / role |
|---|---|
| `industries/petshop/industry/PetShop.java` | Industry plugin. Owns `storedPets`, `petStorageCostMult`. Food demand, submarket add/remove, storage fee, AI cores. `EconomyTickListener` |
| `industries/petshop/memory/Pet.java` | Pet state: `typeID`, `id`, `name`, `age`, `lifetime` (0.9 to 1.1 x `maxLife`), `assignedFleetMember`, `storage`, `personality`, `serveDuration`, `deathData`. `assign()`, `store()`, `advance()`, feeding, hullmod tooltip section |
| `industries/petshop/memory/PetData.java` | One `pets.csv` row. Tags `no_sell`, `no_drop` |
| `industries/petshop/memory/PetDataRepo.java` | Loads `pets.csv` with `getMergedSpreadsheetDataForMod`, cached in session transient memory |
| `industries/petshop/memory/DeathData.java` | Cause, date, location of a dead pet |
| `industries/petshop/listener/PetStatusManager.java` | Singleton registry of all pets. `BaseCampaignEventListener(true)`, `EconomyTickListener`, `FleetEventListener`, `EveryFrameScript`. Feeding, death reports, sale and combat loss detection |
| `industries/petshop/listener/PetStatusListenerAPI.java` | Interface, no implementers or callers |
| `industries/petshop/listener/EscapeInterceptListener.java` | `CampaignInputListener` (priority 99999): eats Escape while `$IndEvo_BlockESC` is set and calls `EscapeBlockingDialoguePluginAPI.close()` |
| `industries/petshop/script/EscBlockerMemoryIterator.java` | Transient script that keeps `$IndEvo_BlockESC` alive while the host dialog is open |
| `industries/petshop/item/PetItemPlugin.java` | `IndEvo_PetBox` special item. Item data = pet type id. Icon render, drop tiers, price, right-click use |
| `industries/petshop/dialogue/PetPickerInteractionDialoguePlugin.java` | Opened by the item: name dialog, then ship picker, then creates and assigns the pet |
| `industries/petshop/dialogue/PetNameAndAssignDialogueDelegate.java`, `PetRenameDialogueDelegate.java` | Name entry panels |
| `industries/petshop/dialogue/PetShopDialogPlugin.java` | Escape blocking host for the manager panel and the reassign ship picker |
| `industries/petshop/dialogue/PetManagerDialogueDelegate.java` | Manager panel: filter ALL / FLEET / CARGO / STORAGE; Assign, Store, Rename, Euthanize |
| `industries/petshop/submarket/PetSubMarket.java` | Pet shop submarket. Sells pet boxes, buys nothing |
| `industries/petshop/hullmods/PetHullmod.java` | Base pet hullmod: CR loss -5% x effect fraction, tooltip from `Pet` |
| `industries/petshop/hullmods/SelfRepairingBuiltInHullmod.java` | Base class that re-adds itself as a perma mod. Also used outside pets (see Contracts) |
| `industries/petshop/plugins/PetCenterOptionProvider.java` | Industry button "Manage Pets..." on a functional Animal Center at the current market |
| `industries/petshop/plugins/PetShopCampaignPlugin.java` | Picks `PetShopDialogPlugin` for entities tagged `IndEvo_PetShop` (legacy station entity) |
| `abilities/pets/PetManagementAbilityPlugin.java` | Ability `pet_management`; opens `PetShopDialogPlugin(null)`. `applySetting()` adds or removes the ability for `IndEvo_pets` |
| `other/YourMod_PetName_PetHullmod.java` | Copy template for other mods adding pets (see issues) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_PetStore` "Animal Center" (`Ids.PET_STORE`) | `data/campaign/industries.csv:54` |
| Submarket | `IndEvo_PetSubMarket` (`Ids.PETMARKET`) | `data/campaign/submarkets.csv:10` |
| Special item | `IndEvo_PetBox` (`ItemIds.PET_CHAMBER`) | `data/campaign/special_items.csv:34` |
| Hullmod | `IndEvo_petHullmod` | `data/hullmods/hull_mods.csv:13` |
| Ability | `pet_management` | `data/campaign/abilities.csv:3` |
| Pets | 38 rows: `id,name,value,maxlife,tags,foodCommodities,rarity,foodPerMonth,desc,natural_death,icon,hullmod` | `data/campaign/pets.csv` |
| Death texts | `hamster_death_causes.csv` (natural death of hamsters), `combat_death_causes.csv` | `data/strings/` |
| Sprites | `IndEvo/pets_large`, `IndEvo/pets_small`; icons in `graphics/pets` | `data/config/settings.json:141-142` |
| Setting | `IndEvo_pets` | `LunaSettings.csv:11` |
| Faction (colour only) | `petStoreColour` | `data/world/factions/submarkets/` |
| Entities | `IndEvo_PetShop` (`Ids.PET_SHOP_ENTITY`, legacy), `IndEvo_abandonedPetCenter` | `data/config/custom_entities.json:791`, `:1142` |
| Variant tag | `PET_DATA_ID_<pet id>` on the pet's ship | `Pet.assign()` |
| Memory | `$IndEvo_BlockESC` | sector memory |
| Person | `foogAristo` at `athulf`, sells the `lordandsaviour` pet box for 300000 at cooperative (rules `IndEvo_foog_*`) | `ModPlugin.addLordFoogRep()` (`utils/ModPlugin.java:225`) |

## Registration and lifecycle

- Industry, submarket, item and ability plugins from CSV plugin columns.
- `PetManagementAbilityPlugin.applySetting()` (`abilities/pets/PetManagementAbilityPlugin.java:19-43`): with `IndEvo_pets` on, adds `pet_management` (`Ids.ABILITY_PET_MANAGEMENT`) to the player fleet and character, clears `no_sell` on `fairy` once research `Ids.PROJ_NAVI` is redeemed ([Research](../dialogue/RESEARCH.md)) and loads pet textures; a texture that fails to load throws `IllegalStateException`. With the setting off, removes the ability. `PlayerSettings.applyCampaignSettings()` runs it from `ModPlugin.onGameLoad()` (`utils/ModPlugin.java:167`) and after a Save in the LunaLib menu in the campaign ([Settings](../core/SETTINGS.md#reading-a-setting)). On new game with pets on, `onGameLoad` also adds Lord Foog (`:169`).
- `PetCenterOptionProvider.register()` from `setListenersIfNeeded()` (`ModPlugin.java:451`). `PetShopCampaignPlugin` via `registerPlugin` on every load (`ModPlugin.java:494`).
- `PetStatusManager.getInstance()` (`PetStatusManager.java:58`) creates the manager on first use: persistent listener, sector script, player fleet event listener. Found again later by `hasListenerOfClass`.
- `PetShop.apply()` adds itself as a persistent listener if missing (`PetShop.java:218`). Storage fee: `getTotalStorageCost()` sums `getStorageCost()` over live pets (food x 500 x `petStorageCostMult`, plus 2000 on NPC markets). Player markets pay it as Animal Center upkeep (`IndEvo_petStorage`, `:204-208`), so the hazard and AI core upkeep mults apply; the tooltip total includes them. NPC markets bill it in `reportEconomyTick()` through the monthly report. `advance()` queues `SubMarketAddOrRemovePlugin(market, PETMARKET, false)` every frame while functional, pets enabled and the submarket missing (`:182`). `unapply()` queues the removal (`:243`), which runs once the Animal Center is gone or pets are disabled.
- Animal Centers are placed at game start at `jangala`, `gilead`, `fikenhild` ([Utils](../core/UTILS.md)).

## Flow

### Getting a pet

`PetSubMarket` restock -> `IndEvo_PetBox` in cargo -> right-click in fleet cargo tab (`PetItemPlugin.performRightClickAction(RightClickActionHelper)`) -> `showInteractionDialogFromCargo` opens `PetPickerInteractionDialoguePlugin` over the cargo screen -> name -> ship picker -> `new Pet` -> `PetStatusManager.register` -> `Pet.assign(member)` -> box removed through `helper.removeFromClickedStackFirst(1)`. Every dismiss (assigned, cancelled) returns to the cargo screen.

- Restock: weighted picker over pets without `no_sell`, weight = rarity (x1.3 with an alpha core when rarity < 0.5). Picks = market size; amount per pick = floor(rarity x 3), at least 1.
- Tariff 0.2, 0 with a beta core. Selling to the submarket is illegal.
- Drops (`resolveDropParams`): tier rare rarity <= 0.4, uncommon 0.4 to 0.8, common >= 0.8; `no_drop` pets skipped. Price = pet `value`.

### Assigned pet

- `assign()` adds `PetData.hullmod` as a perma mod and the `PET_DATA_ID_` tag; switches to a custom variant unless called from refit.
- Effect fraction = `serveDuration / 365`, capped at 1. Pet hullmods look up their pet through `PetStatusManager.getPet(variant)`.

### Daily and monthly

- `Pet.advance()` ages the pet (not in storage under a beta core, `Pet.java:127`) and feeds it every 31 days. Feeding takes `foodPerMonth` of a listed food commodity from the fleet cargo; for a ship in a submarket, from the storage cargo of that market (`PetStatusManager.getFoodCargo`, `PetStatusManager.java:137`); no food -> starving -> `STARVED` death.
- Age past `lifetime` -> `NATURAL` death, message from `natural_death` or the hamster CSV.
- `PetStatusManager`: month end food report; month end alive check kills a pet (`UNKNOWN`) whose ship has no fleet data or sits in a dead fleet, but not one in storage, whose fleet data has no fleet (`PetStatusManager.java:153-165`); `reportPlayerMarketTransaction` -> pet ship sold -> `SOLD` (warns when sold into a free transfer submarket); battle losses via `Misc.getSnapshotMembersLost` -> `COMBAT`.
- `reportPetDied()` removes the hullmod and tag, posts a message, keeps `DeathData`.

### Storage

- Store from the manager panel: pet leaves the ship, `storage` = the PetShop.
- `PetShop.reportEconomyTick()` on the last iteration of the month charges non player markets `foodPerMonth x 500` plus 2000 per live pet through `MiscIE.createMonthlyReportNode` ([Economy](../core/ECONOMY.md)).

### AI cores

| Core | Effect | Where |
|---|---|---|
| Alpha | rare pet weight x1.3 in restock | `PetSubMarket.java:39` |
| Beta | pets stop aging in storage; tariff 0 | `Pet.java:127`, `PetSubMarket.java:68` |
| Gamma | `petStorageCostMult` 0.7 | `PetShop.java:323` |

## Contracts

- `Pet.personality` uses `Academy.COLOURS_BY_PERSONALITY` (`Pet.java:56`, `:213`); see [Academy](ACADEMY.md).
- A box whose data has no `pets.csv` row (unknown id, or null data in a real cargo) is invalid (`PetItemPlugin.isInvalid()`): no icon, the tooltip says so, a right-click throws `IllegalStateException`. Codex stacks have null data and no cargo (`obf/codex2/CodexDetailPanel.java:935`) and show the `hampter` fallback.
- Dialogs from `showInteractionDialogFromCargo` do not become `getCurrentInteractionDialog()` (`obf/campaign/CampaignState.java:2298-2318`, `:2372`), so `isFleetCargo()` stays true while the picker is open. A second box still cannot be clicked: the dialog opens inside the right-click and its full screen backing panel absorbs all input (`com.fs.starfarer.ui.oo0O`, `absorbOutsideEvents` defaults to true, `obf/ui/_c199.java:18`, `:44-63`; input order inferred from vanilla dialogs being modal).
- `EscapeInterceptListener` and `EscBlockerMemoryIterator` are also used by `industries/museum/plugins/ManageParadeDialoguePlugin.java` ([Museum](MUSEUM.md)).
- `SelfRepairingBuiltInHullmod` is the base for changeling `HandBuiltHullmod`, Hellpods and distress hullmods ([Changeling](CHANGELING.md), [Distress](../exploration/DISTRESS.md)). `PetHullmod` is the plugin class for the ghost ship rows in `hull_mods.csv:20-59`; those ships have no pet, so `getPet()` returns null there.
- Any code that removes ships from the player fleet without a market transaction or battle does not report a pet death; the pet stays registered with a stale member (inferred).
- The manager only adds the player fleet event listener on creation; a replaced player fleet object loses it (inferred).
- `MiscIE.createMonthlyReportNode` is shared with Academy and Repair Docks.
- A stored ship's `getFleetData()` is the `getMothballedShips()` of the submarket cargo holding it, with no fleet (`obf/campaign/fleet/FleetData.java:482`, `addFleetMember` sets it). `getFoodCargo` finds that cargo by identity over `getEconomy().getMarketsCopy()`, the same markets the vanilla storage list shows (`obf/campaign/command/OutpostListPanel.java:271`); a ship stored on a market outside the economy is not found and its pet starves.

## Known issues and dormant code

| Item | State |
|---|---|
| Storage fee report | Changelog_todo: "Fix the -1c pet food cost in monthly report"; not explained by the storage fee code |
| `PetPickerInteractionDialoguePlugin` | `hasShownPicker` is never set to true; `advance()` calls `showShipPicker()` every frame once named (`:122`) |
| `reportPetDied()` | NPE when both `assignedFleetMember` and `storage` are null (`PetStatusManager.java:174`) |
| `YourMod_PetName_PetHullmod` | Checks `isModEnabled("indEvo")` (`:31`, `:48`); the mod id is `IndEvo` and `ModManager.isModEnabled` compares exactly (`obf/launcher/ModManager.java:196`), so a copy of the template never applies effects or tooltip |
| `PetStatusListenerAPI` | Dead |
| `PetShop.ensurePetShopCreatedOrAssigned()`, `removeStationEntityAndFleetIfNeeded()`, entity `IndEvo_PetShop`, `PetShopCampaignPlugin` | Legacy station version, deprecated, not called |
| `PetStatusManager.dev()` | Runcode helper |
| `graphics/pets/alt/`, `gammacore.png`, `glowjar.png`, `mycojar.png` | Not referenced by `pets.csv` (inferred unused) |
| Changelog_todo | "Drop some pets from fleet battles (rarely)", "Pet stations: Add main menu dialogue" |

## Maintenance

Update this doc when pets, pet storage, the pet submarket or `pets.csv` columns change. Settings live in [Settings](../core/SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
