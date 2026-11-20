# RAT settlements

IE facilities for the settlements of Random Assortment of Things (RAT, `assortment_of_things`, 3.4.1): small versions of IE industries built in a settlement's facility slots. Read it before changing a facility, the settlement option and return flow, or the Engineering Annex panel. It does not cover the parent industries; each facility links its parent doc. Other RAT touch points (Courier Port targets, storage fallback, loot ids) are in [Compatibility](../core/COMPATIBILITY.md).

Checked against RAT 3.4.1 (`lib/RandomAssortmentofThings.jar`; its Frontiers code and data match 3.3.1) and Starsector 0.98.5a-RC7. Not tested in game.

## Overview

RAT merges `data/campaign/frontiers/rat_frontiers_facilities.csv` from every mod and offers the rows in the settlement's facility picker. Each IE row names a class that extends RAT's Kotlin `BaseSettlementFacility` through `SettlementFacility`. A facility shows in the picker only while its parent industry's setting is on (`shouldShowInPicker`), never with its own switch. The settlement market (`rat_station_commander_market`) is player faction but not player owned and not in the economy; its storage is `rat_settlement_storage`.

| Facility | Id | Parent ([doc](SMALL_INDUSTRIES.md)) | Setting | Cost, days | What it does |
|---|---|---|---|---|---|
| Consulate | `IndEvo_rat_consulate` | [Embassy](EMBASSY.md) | `EMBASSY` | 75000, 45 | +3 relations a month with the faction owning the settlement's planet, up to 25 (`Consulate.java:15-16`). Greyed in the picker on an unowned or player owned planet |
| Requisition Office | `IndEvo_rat_requisition` | Requisitions Center ([Small industries](SMALL_INDUSTRIES.md)) | `REQCENTER` | 90000, 60 | Adds the `IndEvo_ReqMarket` submarket at player rules (40% tariff) and stocks consumable missiles |
| Fabricator Bay | `IndEvo_rat_fabricator` | [Variable Assembler](ASSEMBLER.md) | `ADASSEM` | 100000, 60 | Holds one VPC; month end puts half the Assembler deposit of its commodities into settlement storage |
| Exhibition Hangar | `IndEvo_rat_exhibition` | [Museum](MUSEUM.md) | `MUSEUM_ENABLED` | 50000, 45 | Adds the Museum submarket; settlement income at half the Museum rate. No parades |
| Courier Office | `IndEvo_rat_courier` | [Courier Port](COURIER_PORT.md) | `PRIVATEPORT` | 65000, 45 | Courier contracts without a Courier Port, billed to the settlement; option opens the Courier panel |
| Restoration Bay | `IndEvo_rat_restoration` | Restoration Docks ([Small industries](SMALL_INDUSTRIES.md)) | `DRYDOCK` | 100000, 60 | One ship in settlement storage loses a D-mod every 31 days, fewest D-mods first, billed at the Docks price; leaving storage resets the timer |
| Refit Gantry | `IndEvo_rat_gantry` | [Salvage Yards](SALVAGE_YARDS.md) | `SCRAPYARD` | 75000, 60 | Opens the Salvage Yards panel with only the D-mod and S-mod transfer tabs |
| Officer Quarters | `IndEvo_rat_quarters` | [Academy](ACADEMY.md) | `ACADEMY` | 75000, 45 | Officer and admin storage and training at own Academy rules through an embedded `QuartersAcademy`; no hireables |
| Engineering Annex | `IndEvo_rat_annex` | [Engineering Hub](ENGINEERING_HUB.md) | `ENGHUB` | 100000, 60 | One hull class at a time at hub speed, own panel ([below](#engineering-annex)) |
| Pet Habitat | `IndEvo_rat_habitat` | [Animal Center](PETS.md) | `PETS` | 50000, 30 | Pet storage and 1 habitat at player Care (40) through an embedded `HabitatPetShop`; no sales or adoption; mutation x2 when the settlement's planet is irradiated ([below](#pet-habitat)) |

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/ratsettlement/RatSettlements.java` | Only IE side entry to RAT: `isEnabled()`, `isSettlementMarket(market)`, `getSettlementMarket()`, `hasFunctionalFacility(id)`, `getFacility(id)`, `getHabitat()`, `returnTo(dialog, ratPlugin)`. Every RAT call sits behind `isEnabled` (`:56`) |
| `ratsettlement/facility/SettlementFacility.java` | Base class: settings gate, day tick, option keys, panel swap, storage and billing helpers |
| `ratsettlement/facility/Consulate.java`, `RequisitionOffice`, `FabricatorBay`, `ExhibitionHangar`, `CourierOffice`, `RestorationBay`, `RefitGantry`, `OfficerQuarters`, `EngineeringAnnex`, `PetHabitat` | The facilities |
| `ratsettlement/facility/QuartersAcademy.java` | `Academy` subclass for the Quarters: `spawnsHireables()` false, `ownedByPlayer()` true, name "Officer Quarters" |
| `ratsettlement/facility/HabitatPetShop.java` | `PetShop` subclass for the Habitat: `ownedByPlayer()` true, 1 habitat, `isIrradiated()` from the settlement's primary planet, name "Pet Habitat" |
| `ratsettlement/dialogue/AnnexDialogPlugin.java`, `AnnexPanelDelegate`, `AnnexShipList`, `AnnexView` | Engineering Annex panel |
| `data/campaign/frontiers/rat_frontiers_facilities.csv` | Rows: `id,name,buildTime,shortDesc,icon,cost,plugin` |
| `graphics/icons/ratsettlement/*.png` | 95 x 95 picker icons (crops of the parent industry images, placeholders) |

Changes outside the package: `BillingHelper.charge/pay(market, ownerId, ...)` ([UTILS](../core/UTILS.md#billinghelper)); `ShippingManager` (contracts with the office, billing); `RequisitionsCenterSubmarketPlugin.ownedByPlayer()` (`:96`); `ConsumableItemMarketAdder.updateSubmarketCargo` static; `Museum` static value helpers; `RestorationDocks` static D-mod helpers; `Academy.spawnsHireables()` and `ownedByPlayer()`; `onReturn` constructors on `SalvageYardsDialogPlugin`, `AcademyDialogPlugin`, `CourierPortDialoguePlugin`, `PetsDialogPlugin`; `PetShop.ownedByPlayer()`, `isIrradiated()`, `advanceBroods()`, `planCenter` and `PetsDialogPlugin.dockedShop` ask `RatSettlements.getHabitat()`; `SalvageYardsPanel` transfer only mode; `engineeringhub/ReverseEngineering.java`.

## Registration and lifecycle

- Nothing is registered in `ModPlugin`. RAT reads the CSV and creates the plugin by class name for the picker and for every built slot; IE classes load only with RAT.
- RAT calls `apply()` when construction finishes and `unapply()` on demolish (inferred from the slot code, untested). Picker instances have no facility slot: `canBeBuild`, `shouldShowInPicker`, the cost and the tooltip must never touch `getFacilitySlot()`.
- RAT has no day callback. `SettlementFacility.advance` (`:39`) runs every frame for every slot, also while paused, and calls `onNewDay()` once per game day: the day index is `getTimestamp() / 86400000` (timestamps are game milliseconds; `getElapsedDaysSince(0L)` returns `Float.MAX_VALUE`, `obf/campaign/CampaignClock.java:92-94`). The saved `lastDay` survives loads.
- `reportEconomyTick`, `reportEconomyMonthEnd` and `addToMonthlyCargo` come from RAT.

## Flow

### Options and panels

`populateSettlementDialog` adds the facility's options with an `OptionKey(facilityId, action)` as data. RAT asks every facility in turn on a press; `optionPressDetected` (`SettlementFacility.java:64`) takes only its own keys and calls `onOption` with the current dialog. A panel opens through `openPanel` (`:78`): the IE dialog plugin replaces RAT's in the same dialog and gets an `onReturn` that runs `RatSettlements.returnTo` (`RatSettlements.java:47`): plugin back, text and visual panels shown, text cleared, then RAT's `init(dialog)` redraws the settlement menu. The IE plugin calls `onReturn` a frame after its custom dialog closed.

| Facility | Option | Opens |
|---|---|---|
| Courier Office | Manage shipping contracts | Courier panel |
| Refit Gantry | Transfer D-mods and S-mods | `SalvageYardsPanel` in transfer only mode, title "Refit Gantry" |
| Officer Quarters | Visit the Officer Quarters | Academy panel on the embedded `QuartersAcademy` |
| Engineering Annex | Visit the Engineering Annex | Annex panel |
| Fabricator Bay | Load a VPC into the Fabricator Bay (Swap the VPC ... once loaded) | Vanilla cargo picker over settlement storage and fleet cargo; a swap returns the old VPC to storage |
| Restoration Bay | Check the Restoration Bay | Status line in the text panel, also as option tooltip |

### Billing

Restoration Bay repairs and Courier contracts go through `BillingHelper.charge(settlementMarket, facilityId, ...)`; the Quarters storage fee through the embedded academy's `BillingHelper.charge(ind, ...)`; Pet Habitat storage on the last economy tick of the month (`PetHabitat.reportEconomyTick`). The settlement market is not player owned, so the lines land under "Services". Exhibition Hangar income is a flat mod on RAT's settlement income stat, not a report line.

### Pet Habitat

The embedded `HabitatPetShop` is an `Industry` that is never added to the market or applied, so `Pet.storage` (type `Industry`) holds it like a real center and no save field changes. It has no pet market, no upkeep and no listener: `PetHabitat.advance` calls `advanceBroods()` every frame, `reportEconomyTick` bills storage at `getTotalStorageCost()`. Pet code that finds a center by market also finds the habitat: `PetShop.planCenter` (player center with the most Care, the habitat only when it has more) and `PetsDialogPlugin.dockedShop` (the habitat while docked at the settlement). The option opens the Pets panel with storage and Husbandry; Adoption is off because `hasAdoption()` needs a center that is not player owned.

- Demolish (`unapply`): running broods end and their parents go to storage, young of finished broods are registered; every living pet then goes into a Cryochamber in settlement storage (a free transfer submarket, so the pet is kept).

### Engineering Annex

State (`EngineeringAnnex`): `hullId` (base hull of the locked class), `inWork` (stripped variant) and its ship name, `daysPassed`, `daysRequired`, `dropOff` (factory cargo with mothballed ships, not a submarket, so it stays out of the cargo screen), `fromFleet` (member ids that came from the fleet).

- Progress is the hub's shared map `$IndEvo_researchProgress` through `ReverseEngineering` (`engineeringhub/ReverseEngineering.java`), so a class half done at an Engineering Hub continues here and the other way round. Values and days are the hub's without AI cores: 35% a ship (45% capital) less the D-mod cut, 15 days (21 capital).
- `onNewDay` (`:57`): with a ship in work, count a day; when done, add its value to the map. Then at 100% the drop off goes back to settlement storage, else the next drop off ship is stripped into settlement storage (`MiscIE.stripShipToCargoAndReturnVariant`) and started. A class whose hull spec is gone clears itself.
- Ship list: ticking puts a ship from the fleet or settlement storage into the drop off at once (`put`, `:211`); a fleet ship loses its human officer. The first ship sets the class. Unticking (`takeBack`, `:227`) returns it where it came from; when that leaves nothing in work, nothing in the drop off and 0% data, the class is free again. Never the flagship, never a class at 100%.
- Finish (`finish`, `:256`): a ship blueprint of the same hull size (Tiandong and Roider: their retrofit item) from settlement storage or fleet cargo, or the hull's relic cost (supplies to recover x 10) through `RelicHelper` (settlement storage first). The blueprint goes to settlement storage, the class leaves the map. No confirm.
- Abandon (`abandon`, `:278`, vanilla popup through `UiConfirm`): the ship in work is lost, the drop off goes back to storage, the class is cleared. Its data leaves the map only while the player owns no functional Engineering Hub (`ownsHub`, `:288`).
- Accelerate: the facility is a `RelicSpeedUpTarget` ([Economy](../core/ECONOMY.md#relic-speed-up)) with the settlement market as market: 1 relic per day left, halves the days, 60 day cooldown in the settlement market's memory.
- Demolish (`unapply`, `:118`): ship in work lost, drop off back to storage.

Panel (approved boards `/mnt/project-files/ie/annex_ui/`, kit [UI](../core/UI.md)): 540 x 371 while collecting (class card, In work strip with the bar and Accelerate, Drop off strip with what each ship adds and Add ships... or Change...), 540 x 423 at 100% (Blueprint to overwrite list of three whole rows, Relic Components row, Finish strip). The window height belongs to the phase: Finish or Abandon at 100% closes the custom dialog and `AnnexDialogPlugin` shows it again a frame later. The ship list (Add ships... and Change...) uses the small size: filter bar with sort keys Adds and Name, ticked ships first, the game's ship icons with their tooltips, Back, and the data now and with the ticked ships. Opened with no class it shows every class; after the first tick the others grey out.

## Contracts

- Facility classes and their field names are saved inside RAT's slots: renaming or moving a class, or changing a saved field type, breaks saves that have it built ([Save data](../ARCHITECTURE.md#save-data)).
- Every call into RAT classes goes through `RatSettlements` or a facility class; code outside the package calls only `RatSettlements` and checks `isEnabled` first ([COMPATIBILITY](../core/COMPATIBILITY.md)).
- RAT puts `shortDesc` into `addPara` as a format string: no `%` in a CSV `shortDesc`.
- Settlement storage is `MiscIE.getStorageCargo(settlementMarket)`; `SettlementFacility.getStorageCargo()` (`:89`) throws when it is missing.
- Officer Quarters: `apply()` builds a fresh `QuartersAcademy`; `unapply()` aborts training and moves officers and admins to the fleet even over the cap.
- Exhibition Hangar and Requisition Office remove their submarket through `IndustrySubmarkets.remove`, which moves held cargo and ships to settlement storage first.

## Known issues and dormant code

| Item | State |
|---|---|
| Icons | Placeholders cut from the parent industry images |
| `apply`/`unapply` timing | Read from RAT's slot code, not tested in game |
| Smoke | Needs RAT in the smoke mod set |

## Maintenance

Update this doc with every change to a facility, the CSV, the option and return flow or the Annex panel. Recheck `BaseSettlementFacility` and the CSV columns on every RAT update; IE compiles against the jar in `lib/`, so a mismatch shows only at runtime. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
