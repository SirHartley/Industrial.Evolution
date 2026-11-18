# Pets

The Animal Center industry (`IndEvo_PetStore`), the pet submarket, pet items, pet hullmods, pet life cycle and the Pet Management ability. Read it before changing pets, pet food, pet storage or `data/campaign/pets.csv`. It does not cover the abandoned pet center derelict ([Stations](../exploration/STATIONS.md)), how submarkets are added and removed ([Submarkets](../core/SUBMARKETS.md)) or the ghost ship hullmods that reuse `PetHullmod` ([Distress](../exploration/DISTRESS.md)).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

A pet is a `Pet` object bound to one fleet member (perma hullmod plus variant tag) or stored in an Animal Center. Pets age, eat food commodities from fleet cargo (or from the storage their ship sits in) once a month, and die of old age, starvation, combat loss, sale or euthanasia. The pet's hullmod effect grows with time served. The Animal Center sells pets as `IndEvo_PetBox` special items, stores pets and opens the Pets panel. The `pet_management` ability opens the same panel in flight, without storage and Rehome. Pets in the fleet post short lines about what they are up to, in flight and when docking.

## Files

Code paths relative to `jars/src/indevo/`.

| File | Owner / role |
|---|---|
| `industries/petshop/industry/PetShop.java` | Industry plugin. Owns `storedPets`, `petStorageCostMult`. Food demand, submarket add/remove, storage fee (`getMonthlyStorageCost`), `rehome`, AI cores. `EconomyTickListener` |
| `industries/petshop/memory/Pet.java` | Pet state: `typeID`, `id`, `name`, `age`, `lifetime` (0.9 to 1.1 x `maxLife`), `assignedFleetMember`, `storage`, `personality`, `deathData`, `unsettledDays`, `shellShockedDays`, `happyDays`, `lastMealSupplies`, `homeShipId`, `bondDays`, `nestRebuildDays`, `favourite`, `want`, `wantDaysLeft`, `wantCooldownDays`, `lapsedWants`. `place()`, `advance()`, `pet()`, `play()`, `addBond()`, `answerWant()`, `getMood()`, feeding, hullmod tooltip section |
| `industries/petshop/PetMood.java` | Display only moods with label, colour and the line id (`id()`, lower case name) |
| `industries/petshop/PetWant.java` | The six wants: label, card button or hint, line event (`event()`, `want_` + lower case name) |
| `industries/petshop/PetConstants.java` | Tuning numbers that are not per species: Unsettled, Shell shocked and Happy days, Sleepy age, line weights, petting clicks, Rehome fee, name length, hearts, nest, escape pods, favourites and wants |
| `industries/petshop/memory/PetData.java` | One `pets.csv` row. Tags `no_sell`, `no_drop`; `getClassTag()` (`c_` tag, `c_alien` when none); `body`; `pace` (`fast`, `medium`, `slow`, `hamster`; blank follows `maxLife`: under 1800 days fast, under 3000 medium, else slow) |
| `industries/petshop/memory/PetDataRepo.java` | Loads `pets.csv` and `pet_lines.csv` with `getMergedSpreadsheetDataForMod`, cached in session transient memory; a load failure throws `IllegalStateException`. `parsePets`, `parseLines` take a `JSONArray` and use no `Global`. Line rows are the nested record `Line`; `pickLine` ([Lines](#lines)) |
| `industries/petshop/memory/DeathData.java` | Cause, date, location of a dead pet |
| `industries/petshop/listener/PetStatusManager.java` | Singleton registry of all pets. `BaseCampaignEventListener(true)`, `EconomyTickListener`, `FleetEventListener`, `EveryFrameScript`, `ShowLootListener`, `SurveyPlanetListener`, `DiscoverEntityListener`. Favourites, the want timer and answers, shared events, feeding, `hasFood`, death reports, sale and combat loss detection, `getFreeShip`, `getFreeShips`, `handOver`, `reportScuttled`, `getMenageriePets`, `holdMessages`, `releaseMessages`, the line cadence, `say`, `getLine`, `sayDocking` |
| `industries/petshop/listener/EscapeInterceptListener.java` | `CampaignInputListener` (priority 99999): eats Escape while `$IndEvo_BlockESC` is set and calls `EscapeBlockingDialoguePluginAPI.close()` |
| `industries/petshop/script/EscBlockerMemoryIterator.java` | Transient script that keeps `$IndEvo_BlockESC` alive while the host dialog is open |
| `industries/petshop/patches/PetScuttlePatch.java` | PatchLib `@After` on `FleetDataAPI.scuttle(FleetMemberAPI)` (vanilla `FleetData`, `obf/campaign/fleet/FleetData.java:1172`; only caller the fleet screen ship card). Calls `PetStatusManager.reportScuttled` |
| `industries/petshop/patches/PetFleetCardPatch.java` | PatchLib `@After` on `addTooltipsForPips` of the fleet screen ship card ([Pet on its ship](#pet-on-its-ship)) |
| `industries/petshop/patches/PetShipTooltipPatch.java` | PatchLib `@RedirectMethodCall` on `sendToBottomWithinItself` in the full ship tooltip's `beforeShown` ([Pet on its ship](#pet-on-its-ship)) |
| `industries/petshop/patches/PetRefitRowPatch.java` | PatchLib `@RedirectMethodCall` on `createHullModTooltip` in the refit hull mod row constructor ([Pet on its ship](#pet-on-its-ship)) |
| `industries/petshop/rules/IndEvo_PetDockingLine.java` | Rule command on `PopulateOptions` (row `IndEvo_petDockingLine`): calls `PetStatusManager.sayDocking` |
| `tools/PetChecks.java` | Headless checks, never shipped ([Checks](#checks)) |
| `console/PetCommand.java` | `indevo_pet list`, `give <species> [name]`, `age <pet> <days>`, `hearts <pet> <0-5>`, `want <pet> <want>` ([Console](../core/CONSOLE.md)) |
| `industries/petshop/item/PetItemPlugin.java` | `IndEvo_PetBox` special item. Item data = pet type id. Icon render, drop tiers, price; a right-click opens `PetPlaceDialog.chamber` |
| `industries/petshop/plugins/PetsDialogPlugin.java` | Escape blocking host of the Pets panel. `show(shop, view)`; `shop` is the docked Animal Center, null in flight. `over(pet, onClose)` from a vanilla screen ([Pet on its ship](#pet-on-its-ship)) |
| `industries/petshop/plugins/PetsPanel.java` | Pets panel ([Pets panel](#pets-panel)): groups, tile list, header, name commit, popups |
| `industries/petshop/plugins/PetCard.java` | Tile and card of one pet: markers, petting, ship strip, Rehome, Euthanize; name field helpers; the hover block and ship icons ([Pet on its ship](#pet-on-its-ship)) |
| `industries/petshop/plugins/PetPlaceDialog.java` | Where to popup of Move... and Assign..., and the Cryochamber popup (`chamber`) |
| `industries/petshop/plugins/PetsView.java` | Panel state kept over rebuilds: selection, scroll, petting, name field |
| `industries/petshop/submarket/PetSubMarket.java` | Pet shop submarket. Sells pet boxes, buys nothing |
| `industries/petshop/hullmods/PetHullmod.java` | Base pet hullmod: CR loss -5% x `Pet.getMascotMult()`, tooltip `PetCard.hover` |
| `industries/petshop/hullmods/SelfRepairingBuiltInHullmod.java` | Base class that re-adds itself as a perma mod. Also used outside pets (see Contracts) |
| `industries/petshop/plugins/PetCenterOptionProvider.java` | Industry button "Manage Pets..." on a functional Animal Center at the current market |
| `abilities/pets/PetManagementAbilityPlugin.java` | Ability `pet_management`; opens `PetsDialogPlugin.show(null, ...)`. `applySetting()` adds or removes the ability for `IndEvo_pets` |
| `other/YourMod_PetName_PetHullmod.java` | Copy template for other mods adding pets (see issues) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_PetStore` "Animal Center" (`Ids.PET_STORE`) | `data/campaign/industries.csv:54` |
| Submarket | `IndEvo_PetSubMarket` (`Ids.PETMARKET`) | `data/campaign/submarkets.csv:10` |
| Special item | `IndEvo_PetBox` (`ItemIds.PET_CHAMBER`) | `data/campaign/special_items.csv:17` |
| Hullmod | `IndEvo_petHullmod` | `data/hullmods/hull_mods.csv:13` |
| Ability | `pet_management` | `data/campaign/abilities.csv:3` |
| Pets | 38 rows: `id,name,value,maxlife,tags,foodCommodities,rarity,foodPerMonth,desc,natural_death,icon,hullmod,body,pace` | `data/campaign/pets.csv` |
| Pet lines | `id,event,match,mood,disposition,context,text` ([Lines](#lines)) | `data/strings/pet_lines.csv` |
| Rule | `IndEvo_petDockingLine` (block `# Pets`) | `data/campaign/rules.csv` |
| Death texts | `hamster_death_causes.csv` (natural death of hamsters), `combat_death_causes.csv` | `data/strings/` |
| Sprites | `IndEvo/pets_large`, `IndEvo/pets_small`, tile markers `pet_marker_hungry`, `pet_marker_starving`, `pet_marker_shock`, `pet_marker_want` (`graphics/icons/pets/`); pet icons in `graphics/pets` | `data/config/settings.json:103-108` |
| Setting | `IndEvo_pets` | `LunaSettings.csv:10` |
| Setting | `IndEvo_petLineDays` (`Setting.PET_LINE_DAYS`): minimum days between two ambient or docking lines, default 14, 0 off | `LunaSettings.csv:35` |
| Setting | `IndEvo_petMotion` (`Setting.PET_MOTION`): petting motion and hearts in the Pets panel, default on | `LunaSettings.csv:36` |
| Faction (colour only) | `petStoreColour` | `data/world/factions/submarkets/` |
| Entity | `IndEvo_abandonedPetCenter` | `data/config/custom_entities.json:1220` |
| Variant tag | `PET_DATA_ID_<pet id>` on the pet's ship | `Pet.place()` |
| Memory | `$IndEvo_BlockESC` | sector memory |
| Person | `foogAristo` at `athulf`, sells the `lordandsaviour` pet box for 300000 at cooperative (rules `IndEvo_foog_*`) | `ModPlugin.addLordFoogRep()` (`utils/ModPlugin.java:226`) |

## Registration and lifecycle

- Industry, submarket, item and ability plugins from CSV plugin columns.
- `PetManagementAbilityPlugin.applySetting()` (`abilities/pets/PetManagementAbilityPlugin.java:19-43`): with `IndEvo_pets` on, adds `pet_management` (`Ids.ABILITY_PET_MANAGEMENT`) to the player fleet and character, clears `no_sell` on `fairy` once research `Ids.PROJ_NAVI` is redeemed ([Research](../dialogue/RESEARCH.md)) and loads pet textures; a texture that fails to load throws `IllegalStateException`. With the setting off, removes the ability. `PlayerSettings.applyCampaignSettings()` runs it from `ModPlugin.onGameLoad()` (`utils/ModPlugin.java:171`) and after a Save in the LunaLib menu in the campaign ([Settings](../core/SETTINGS.md#reading-a-setting)). On new game with pets on, `onGameLoad` also adds Lord Foog (`:173`).
- `IndEvo_PetDockingLine` is found through the `ruleCommandPackages` entry `indevo.industries.petshop.rules` ([Rule commands](../dialogue/RULE_COMMANDS.md)).
- `PetCenterOptionProvider.register()` from `setListenersIfNeeded()` (`ModPlugin.java:466`).
- `PetStatusManager.getInstance()` (`PetStatusManager.java:75`) creates the manager on first use: persistent listener, sector script, player fleet event listener. Found again later by `hasListenerOfClass`.
- `PetShop.apply()` adds itself as a persistent listener if missing (`PetShop.java:174`). Storage fee: `getTotalStorageCost()` sums `getStorageCost()` over live pets (`STORAGE_FEE_FRACT` 10% of the species `value` a month, at least `MIN_STORAGE_FEE` 100, x `NPC_STORAGE_FEE_MULT` 1.5 on NPC markets, then x `petStorageCostMult`; the species value, never the pet's own, so the fee shows no unread traits). Player markets pay it as Animal Center upkeep (`IndEvo_petStorage`, `:163`), so the hazard and AI core upkeep mults apply; the tooltip total includes them. NPC markets bill it in `reportEconomyTick()` on the "Pet storage" report line ([BillingHelper](../core/UTILS.md#billinghelper)). `advance()` keeps the pet submarket through `IndustrySubmarkets.ensure` while pets are enabled and removes it while they are off (`:146-147`); `notifyBeingRemoved()` releases it (`:153`).
- Animal Centers are placed at game start at `jangala`, `gilead`, `fikenhild` ([Utils](../core/UTILS.md)).

## Flow

### Getting a pet

`PetSubMarket` restock -> `IndEvo_PetBox` (Cryochamber) in cargo -> right-click in fleet cargo tab (`PetItemPlugin.performRightClickAction(RightClickActionHelper)`) -> `PetPlaceDialog.chamber(data, helper).show` opens the popup over the cargo screen through `UiHost.over` (`showInteractionDialogFromCargo`).

- Popup: "Open Cryochamber", the portrait, a name field with a suggested name (`PetDataRepo.suggestName`), Species and Life span ("About 5 cycles", days under a cycle, from `maxlife`); "To a ship without a pet" (`PetStatusManager.getFreeShips`, the first picked) and "To the menagerie" (picked when no ship is free). No storage row: the cargo screen opens anywhere.
- Confirm: `new Pet` with the typed name (an empty field keeps the suggested one, at most `NAME_MAX_CHARS`) -> `PetStatusManager.register` -> `Pet.place(ship or null, null)` -> one box removed through `helper.removeFromClickedStackFirst(1)`. Cancel: nothing changes, the box stays. Both return to the cargo screen.

- Restock: weighted picker over pets without `no_sell`, weight = rarity (x1.3 with an alpha core when rarity < 0.5). Picks = market size; amount per pick = floor(rarity x 3), at least 1.
- Tariff 0.2, 0 with a beta core. Selling to the submarket is illegal.
- Drops (`resolveDropParams`): tier rare rarity <= 0.4, uncommon 0.4 to 0.8, common >= 0.8; `no_drop` pets skipped. Price = pet `value`.

### Assigned pet

- `place(member, storage)` is the only method that moves a pet: it takes the pet off its ship and out of storage, then puts it on the member or into the Animal Center storage (both null: on no ship and in no storage). On a ship it adds `PetData.hullmod` as a perma mod and the `PET_DATA_ID_` tag and switches to a custom variant unless called from refit.
- Hearts (`Pet.getHearts()`, 0 to 5) are read from `bondDays` against `HEART_DAYS` 14, 60, 180, 300, 480 and never stored. `Pet.advance()` adds a day of `bondDays` for every day aboard (`isAboard()`: on a ship of a live fleet, the player's or a detachment's), a quarter on Supplies (`SUPPLIES_HEART_RATE`, last meal was Supplies), none while starving or Unsettled, and stops at 180, the third heart (`TIME_HEARTS`). Hearts 4 and 5 come only from answered wants and shared events, and only for a favourite ([Favourites and wants](#favourites-and-wants)), and from `indevo_pet hearts`. A move keeps hearts.
- Mascot effect (`Pet.getMascotMult()`): 0 under 1 heart (`MASCOT_HEARTS`) or while Unsettled, else 1, x2 for the hamster pace (`HAMSTER_MASCOT_MULT`), x1.3 with a nest (`NEST_MULT`). `PetHullmod` applies CR loss -5% x that. `getEffectFract()` returns the same for copies of `YourMod_PetName_PetHullmod`. Pet hullmods look up their pet through `PetStatusManager.getPet(variant)`.
- Nest (`Pet.hasNest()`): aboard with at least 4 hearts (`NEST_HEARTS`) and `nestRebuildDays` at 0. `nestRebuildDays` counts down only aboard.
- A pet that arrives on a ship other than `homeShipId`, the last ship it lived on, is Unsettled for `UNSETTLED_DAYS` (30), `NEST_UNSETTLED_DAYS` (90) when it had a nest, which also sets `nestRebuildDays` to `NEST_REBUILD_DAYS` (120): Move... and Assign... in the Pets panel, a hand over, an escape pod to another ship. Its first ship (from a Cryochamber) and a return to the same ship after storage are no move. While Unsettled, `PetHullmod` applies no effect and its tooltip shows the days left.
- `reportPlayerEngagement` sets every pet on a deployed or retreated player ship Shell shocked for `SHELL_SHOCKED_DAYS` (7).
- Mood (`Pet.getMood()`, first match wins): Hungry (starving, or not stored and `PetStatusManager.hasFood` false: no food for its next meal), Unsettled, Shell shocked, Sulking (`isSulking()`: `SULK_LAPSES`, 3, wants lapsed in a row), Unhappy with its food (last meal was Supplies), Happy (petted or a want answered in the last `HAPPY_DAYS`, 3), Sleepy (age over 80% of `lifetime`), Content. Display only, shown in the hullmod tooltip and on the card. Sulking ends with the next answered want or an unstar.

### Favourites and wants

- Favourite (`Pet.favourite`, the star on the card): at most `MAX_FAVOURITES` (3) living pets (`PetStatusManager.canFavourite`). `register()` stars a new pet when there is no favourite. Unstarring ends its want (cooldown and fleet gap, no lapse) and clears its lapses.
- `Pet.addBond(days)` adds bond days up to the third heart, up to the fifth for a favourite. Shared events add `EVENT_BOND_DAYS` (3) to every pet they include: a jump of the player fleet to the pets aboard its ships (`reportFleetJumped`), a fight to the pets on deployed or retreated ships (`reportPlayerEngagement`).
- Want timer (`PetStatusManager.advanceWants`, every frame of the sector script): one want open in the whole fleet. When none is open and `wantGapDays` is 0, a random favourite with the player (`Pet.isWithPlayer()`: on a ship of the player fleet or in the menagerie) whose `wantCooldownDays` is 0 opens a random want: Attention, Play, Shore leave, Come along, and Treat when it has a favourite food (`PetData.getFavouriteFood()`, the first food that is not Supplies). Comfort opens after a fight instead (`reportPlayerEngagement`), under the same rules; in a battle the member sits in the battle's combined fleet, so the handler takes "with the player" from `BattleAPI.getSourceFleet(member)`. `openWant` says the `want_<id>` line.
- A want stays open `WANT_DAYS` (30) while the pet is with the player; at 0 it lapses (`lapsedWants` +1). A pet that leaves the player fleet (stored, out with a detachment) drops its want, no lapse. Every end sets the pet's cooldown to `WANT_COOLDOWN_DAYS` (60) and the fleet gap to `WANT_GAP_DAYS` (5).
- Answers (`Pet.answerWant()`): `WANT_BOND_DAYS` (30) bond days, Happy, lapses back to 0. Attention and Comfort: petting (`Pet.pet()`). Play: the Play button on the card (`Pet.play()`). Treat: the Treat button takes 1 of the favourite food from the cargo it eats from (`PetStatusManager.treat`). Shore leave: opening a real market (`reportPlayerOpenedMarket`). Come along: loot shown (`reportAboutToShowLootToPlayer`), a planet surveyed (`reportPlayerSurveyedPlanet`) or an entity discovered (`reportEntityDiscovered`). The last three answer every open want of that kind with the player.
- A favourite weighs x3 as a line speaker (`FAVOURITE_LINE_WEIGHT`), on top of the flagship weight ([Lines](#lines)). The card's Play and Treat buttons show a `play` or `treat` line in the tagline.

### Daily and monthly

- `Pet.advance()` ages the pet (not in storage under a beta core, `Pet.java:202`), counts down the Unsettled and Shell shocked timers and feeds it every 31 days. Feeding takes `foodPerMonth` of the first listed food commodity the cargo holds enough of, Supplies only when no other listed food is there (`lastMealSupplies` then set), from the fleet cargo; for a ship in a submarket, from the storage cargo of that market (`PetStatusManager.getFoodCargo`, `PetStatusManager.java:381`); no food -> starving -> `STARVED` death.
- Age past `lifetime` -> `NATURAL` death, message from `natural_death` or the hamster CSV.
- `PetStatusManager`: month end food report; month end alive check kills a pet (`UNKNOWN`) whose ship has no fleet data or sits in a dead fleet, but not one in storage, whose fleet data has no fleet (`PetStatusManager.java:408`); battle losses below.

### Battles

- `PetStatusManager.reportBattleOccurred(primaryWinner, battle)`, the global campaign listener report, walks `battle.getSnapshotBothSides()` for the player fleet and detachment fleets (`FleetUtils.isDetachmentFleet`); the fleet event report is empty. The global report also comes for AI battles of detachments, once per autoresolve round (`api/campaign/CampaignEventListener.java:51-56`; both reports come from `CampaignEngine.reportBattleOccurred`, `obf/campaign/CampaignEngine.java:2123-2141`).
- A living pet on a ship in `Misc.getSnapshotMembersLost(fleet)` rolls an escape pod at `POD_CHANCE_PER_HEART` (10%) per heart. Survivor: Shell shocked, on a free ship of the detachment (`getFreeShip(fleet, lost)`), else in the menagerie, with a message. Failed roll: `COMBAT` death. A later autoresolve round reports the same snapshot; the dead pet and the survivor are no longer on a lost ship, so nothing happens twice.

### Hand over and menagerie

- A ship sold, stored (`reportPlayerMarketTransaction`) or scuttled (`PetScuttlePatch`) hands its pet to a free ship of the player fleet through `handOver`: `getFreeShip` takes the flagship if it has no pet, else the largest hull size, then the highest deployment cost, never a fighter wing or a ship with a pet or a ship that is leaving. The pet is placed there (a move, so Unsettled) and a message names both ships.
- No free ship: a sold ship's pet dies (`SOLD`), a stored ship's pet stays aboard with the forgotten pet warning, a scuttled ship's pet dies at once (`UNKNOWN`), as at month end before.
- Menagerie: a living pet with no ship and no storage (`Pet.isInMenagerie()`). It ages and eats from the player fleet cargo, has no effect, and shows in the Pets panel under Menagerie, where it can be assigned or stored. An escape pod survivor from the player fleet lands there, and `indevo_pet give` without a free ship puts a pet there.
- `reportPetDied()` ignores a pet that is already dead, removes the hullmod and tag, posts a message, keeps `DeathData`.

### Lines

- `pet_lines.csv`: one row per line. `event` is `ambient`, `docking`, `status`, `petting`, `petting_starving`, `name`, `play`, `treat` or a want (`want_attention`, `want_play`, `want_treat`, `want_comfort`, `want_shore_leave`, `want_come_along`). `match` holds pet ids, body tags (`b_`) or class tags (`c_`), space separated, or `any`. `mood` (`PetMood.id()`), `disposition` (officer personality id) and `context` (`docked`, `storage`, `old`, `hyperspace`) are space separated sets; blank or `any` matches all. `text` has one `%s` for the pet name, shown highlighted. A `name` row holds a suggested name as its text, with no `%s`, mood, disposition or context; `suggestName` picks one the same way, the species name when none fits.
- `pets.csv` `body`: `b_fourleg`, `b_legs`, `b_wings`, `b_water`, `b_float`, `b_blob`, `b_machine`, `b_jar`, `b_coil`, `b_human`. A row without it (another mod's pet) matches only class and `any` lines.
- `PetDataRepo.pickLine`: every row of the event whose mood, disposition and context fit goes into one weighted pick. Weight by the best `match` token that fits: pet id 4, body 2, class 1.5, `any` 1. Class and `any` lines describe no body.
- `PetStatusManager.getLine(pet, event, docked)` builds the context: `storage` for a stored pet, else `docked` when docked; `old` past 80% of `lifetime` (`Pet.isOld()`); `hyperspace` while the player fleet is in hyperspace (lines about a jump). `say(pet, event, dialog)` adds the line to the dialog text, or posts a `MessageIntel` with the pet's icon when there is no dialog. Docked means the dialog target has a real market.
- Cadence (`advanceLines`, saved `daysSinceLine`, `lineRoll`): in flight, an ambient line comes once `daysSinceLine` passes `IndEvo_petLineDays` x `lineRoll` (1 to 1.5, `PetConstants.LINE_DAYS_SPREAD`). Docking (`sayDocking`, rule `IndEvo_petDockingLine` on `PopulateOptions`, `$hasMarket`, `$menuState == main`) shows a line once `daysSinceLine` passes the setting; the main market menu is populated again on every return to it, the cadence keeps it to one. Either resets both fields. Speaker: a living pet on a ship of the player fleet or in the menagerie, the flagship pet x3 (`FLAGSHIP_LINE_WEIGHT`), a favourite x3 more (`FAVOURITE_LINE_WEIGHT`).
- `status` lines are the card tagline; a stored pet takes only rows with the `storage` context, the others tell of life aboard a ship (`PetDataRepo.Line.weight`); one is picked per pet when the panel opens and kept with its `%s` while it is open, so a rename shows at once. `petting` and `petting_starving` lines answer petting ([Pets panel](#pets-panel)).

### Pets panel

- Opened by "Manage Pets..." on a functional Animal Center (`PetCenterOptionProvider`, `shop` = that PetShop) or by the `pet_management` ability (`shop` null). Board 1060 x 720 in `UiFrame` ([UI](../core/UI.md)); "Return" closes it, Escape too.
- List, by group: Favourites (the starred pets, taken out of the groups below in their order, count "2 / 3"), Mascots (pets on ships of the player fleet, fleet order), one group per detachment fleet by fleet name (`PetsPanel.awayFleet`), Menagerie, and with `shop` set Pet storage (this shop's living stored pets). Pets on ships in a storage submarket are not listed. Six tiles per row: portrait, marker, name, hearts (9 px, grey while Unsettled), ship sprite.
- Filter bar over the list (`UiFilter`, state `PetsView.filter`, kept while the panel is open): field "Name or species" (traits join it with talents), Marked (pets with a tile marker), sort Ship (group order: fleet order on ships), Name, Hearts (most `bondDays` first), Species, Age (oldest first) inside each group. While filtering, a group with no match is not drawn and the rest count "2 of 11"; nothing left shows "No pet matches.". The selected pet keeps its card while the filter hides its tile.
- Tile marker, first match: starving, hungry (not stored and `PetStatusManager.hasFood` false: no food for its next meal), shell shocked, an open want (the vanilla important icon, `pet_marker_want`).
- Card of the selected pet, the first listed pet when none: name field (at most `NAME_MAX_CHARS`, 40; committed on every rebuild, before a popup and on leave; an empty field keeps the old name) with the favourite star at its right (gold when set, an outline, grey with a tooltip when 3 are set; the grey star takes the click so the name field under it does not start editing), tagline, portrait with Species, Disposition, Age, Mood, the Hearts row (five 14 px hearts and a bar to the next heart, grey while Unsettled; its tooltip says what the next heart needs: days, a ship, not Unsettled or starving, a favourite past 3), "Effects off" with the days left while Unsettled, the want strip while a want is open (icon, label, days left, columns sized from the measured text, and the Play or Treat button, else what answers it; Treat is grey with "No <food> aboard."; the 16 px want icon also sits on the portrait), the ship strip, the Talents table (Mascot effect: Combat CR decay with its value, "at 1 heart" or "off"; tooltip with the rule) and the bottom row.
- Petting: `PETTING_CLICKS` (5) clicks on the portrait call `Pet.pet()` and show a `petting` line in the tagline (`petting_starving` while starving). With `IndEvo_petMotion` on, each click throws a heart and the portrait grows and turns; the fifth throws six. Off for a pet out with a detachment.
- Ship strip: on a ship, Move... and Unassign (to the menagerie); in storage, Assign... and To menagerie; in the menagerie, Assign.... Move... and Assign... open `PetPlaceDialog`: ships of the player fleet without a pet (`PetStatusManager.getFreeShips`: fleet order, no fighter wings, no ship with a pet), and To storage with its monthly cost when docked, except from storage. Over 8 ships the list scrolls and gets a filter bar: field "Ship or class", one hull size at a time (All, Frigate, Destroyer, Cruiser, Capital), sort Fleet, Name, Size (largest first). The popup can not grow after it opens, so it is sized for the open fold and the open fold shows three rows of ships instead of four. A filter that hides the picked ship picks the first one shown. A pet out with a detachment shows "Out with <fleet>" and its buttons are grey.
- Bottom row: Euthanize (grey with a tooltip while out with a detachment), and when docked Rehome (`REHOME_FEE`, 2,000 credits, grey with a tooltip when short or out with a detachment). Both ask through `UiConfirm`; Euthanize reports `EUTHANIZED`, Rehome takes the fee and reports `REHOMED` (`PetShop.rehome`).
- Help (`?` on the title line): pets reach 3 hearts with time and the mascot effect starts at 1; hearts 4 and 5 are for favourites, a favourite asks for something about every 2 months, an unanswered want costs nothing; a move makes a pet Unsettled for 30 days, 90 with a nest, effects off, hearts stay.
- Popups open as a second interaction dialog through `UiHost.over`; while one is open `PetsDialogPlugin.advance` stops setting `$IndEvo_BlockESC`, so Escape reaches the popup. The panel rebuilds when it closes.
- The campaign message list sits under the panel, so `PetsDialogPlugin` calls `PetStatusManager.holdMessages` in `init` and `releaseMessages` after `leave` dismisses: death messages (Euthanize, Rehome) post when the panel closes.

### Pet on its ship

- `PetCard.hover(tooltip, pet, heading, click)` is the pet's hover block: name with 11 px hearts at the right (a section heading, then the species with the hearts, when `heading`), species, a status line, Mood, Last meal (only when it was Supplies), Next meal (`Pet.getDaysToMeal`, "No food aboard" when `hasFood` is false), "Hearts grow at 25%. Wants <food>." after a Supplies meal while time still adds hearts, the Unsettled days left, the open want with its days left, and Mascot effect (Combat CR decay, "at 1 heart" under 1 heart, "off" while Unsettled). `click` adds the "Click to open" line.
- The status line is a `status` line picked with a `Random` seeded by pet id and game date (`PetCard.dailyLine`), so it holds for the day.
- `PetHullmod.addPostDescriptionSection` shows the block for the ship's pet; `IndEvo_petHullmod` has an empty `desc`. The "Click to open" line shows when `isForModSpec` is false and `PetRefitRowPatch` is enabled (`PatchLib.isPatchEnabled`), the refit row being the only click target under that tooltip (inferred: other screens that show the hull mod tooltip with a ship show it too). `Pet.addHullmodDescriptionSection` is kept for copies of `YourMod_PetName_PetHullmod` and shows the same block.
- Ship icons: `PetCard.shipIcon(pet, size)` is the picture alone; `shipButton(pet, size, onClose)` opens the Pets panel at the pet and shows the block on hover; `refitButton(pet, action)` is a 12 px cut button, 3 px gap and the framed 34 px picture for a refit hull mod row. The picture is the portrait with the tile marker, at 40% alpha while Unsettled. `shipButton` consumes mouse moves over itself, so a vanilla tooltip under it does not show; `refitButton` does not, so the row keeps its hullmod tooltip.
- Fleet screen (`PetFleetCardPatch`): the card of a player fleet ship with a living pet gets `shipButton` at 32 px, 5 px left and 72 px up from its bottom left, over the ship picture above the officer portrait. Target: obf `com.fs.starfarer.coreui.G` on 0.98.5a-RC4, picked as the `UIPanelAPI` in `com.fs.starfarer.coreui` that declares `addTooltipsForPips()`. The card builds its children in `renderImpl` after `clearChildren` and calls `addTooltipsForPips` last (`obf/coreui/G.java:110-191`), so the icon is added once per build. `onClose` calls the fleet panel's `updateListContents`, which rebuilds every card. The fleet tab processes the cards before `processCellClicksOnly` and that skips consumed clicks (`obf/coreui/_.java:107-113`, `obf/coreui/publicsuper.java:197-207`), so a click on the icon does not pick up the ship.
- Ship tooltip (`PetShipTooltipPatch`): the full ship tooltip of a ship with a living pet gets `shipIcon` at 48 px, 4 px inside the bottom right corner of the ship picture. Target: the anonymous `StandardTooltipV2` subclass in `createFleetMemberTooltipPreDeploy` (`obf/ui/impl/StandardTooltipV2.java:154-451`), picked as a `StandardTooltipV2` subclass in `com.fs.starfarer.ui.impl` whose `beforeShown` calls `sendToBottomWithinItself(1)`. `beforeShown` clears and rebuilds the content each time and calls it once, on the ship picture frame, after the picture is placed (`:413`). The member is the class's first `FleetMemberAPI` field, the captured factory argument; a subclass without one is skipped. Campaign only: the combat deployment dialog uses the same tooltip.
- Refit (`PetRefitRowPatch`): the hull mod row of the pet's own hullmod (`PetData.hullmod`) on a ship with a living pet shows the pet name in place of the hullmod name (fitted with `...`) and `refitButton` over the vanilla icon (`inRMid(5)`, the icon keeps its tooltip), the name 10 px left of it. Target: obf `com.fs.starfarer.coreui.refit.ModWidget$o` on 0.98.5a-RC4, picked as the `UIPanelAPI` in `com.fs.starfarer.coreui.refit` with a `HullModSpecAPI` field; its constructor calls `createHullModTooltip(spec, coreUI, ship, member, variant)` once, after the row is laid out (`obf/coreui/refit/ModWidget.java:365-466`), and `ModPickerDialogV3` calls it only from `updateTable`. The name label is the row child whose text is the hullmod display name. A click saves pending refit edits first, as vanilla does before a ship switch (`autoSaveRefitChanges` is true in the campaign, `obf/coreui/refit/G.java:166-170`), and on close calls the refit panel's `setForceSetMemberNextCall(true)` and `recreateUI`, which reloads the member (`obf/coreui/refit/Objectsuper.java:321-366`); without it the refit copy of the variant would put a moved pet back on the next save. The force flag is needed because the ship display `com.fs.starfarer.coreui.refit.oOOO.setFleetMember` skips a member it already shows (`:236-237`), so the open ship would keep its old hull mod list. The refit panel is the first parent that declares `recreateUI`, `saveCurrentVariant`, `isEditedSinceSave` and `setForceSetMemberNextCall`.
- `PetsDialogPlugin.over(pet, onClose)` opens the panel at the pet through `showInteractionDialogFromCargo`, with the docked functional Animal Center as `shop`; `onClose` refreshes the vanilla screen.

### Storage

- Store with Move... on the card while docked at an Animal Center, To storage in the where to popup: the pet leaves the ship, `storage` = the PetShop.
- `PetShop.reportEconomyTick()` on the last iteration of the month charges non player markets `getTotalStorageCost()` through `BillingHelper.charge`.

### AI cores

| Core | Effect | Where |
|---|---|---|
| Alpha | rare pet weight x1.3 in restock | `PetSubMarket.java:32` |
| Beta | pets stop aging in storage; tariff 0 | `Pet.java:202`, `PetSubMarket.java:61` |
| Gamma | `petStorageCostMult` 0.3 (storage fee 70% off) | `PetShop.java:278` |

## Checks

`indevo/tools/PetChecks.java`, headless, never shipped, not part of `ReleaseBuild`. It reads `pets.csv` and `pet_lines.csv` with the `SettingsChecks` CSV parser and checks: UTF-8 without BOM, no curly quotes, cell counts, unique ids; every pet has a class tag, a known body and a known pace, and only `hampter` has the hamster pace; every line row has a known event, mood, disposition and context, match tokens that are pet ids, bodies or class tags in use, one `%s`, no other `%`, a full stop except on status notes, status notes that start with the name; name rows hold a short capitalized name with no mood, disposition or context; class and `any` lines have no body words; every pet finds a line of every event for every mood and disposition; the pick weights 4, 2, 1.5 and 1 hold over 40,000 seeded picks; heart thresholds rise, one per heart, time reaches the mascot effect but not the nest; in play time (1.5 minutes a week) the first heart takes at most 5 minutes, the third at most 60, the third on Supplies at most 180; one favourite with every want answered at once and no shared events reaches the fourth heart in at most 90 minutes and the fifth in at most 180, a want stays open at least 5 minutes, wants in the fleet come at least 1 minute apart, the cooldown is longer than a want; the pace limits of `PetData.paceFor`.

```sh
java -cp "out/release-build-<n>/classes:lib/*" indevo.tools.PetChecks
```

Run from the repository root after the compile gate. Use `;` in place of `:` on Windows. It prints `Pets: N checks passed` or every failure and exits 1. It refuses to run inside the game.

## Contracts

- `IndEvo_petDockingLine` is on `PopulateOptions`, a `FireAll` trigger (vanilla `marketDock`: `FireAll PopulateOptions` after `FireBest MarketPostDock`), so it runs next to vanilla's option rows and its line follows the docking text. `MarketPostOpen` is `FireBest` (vanilla `marketOpen` row, `EndConversation.java:54`; the `FireAll` calls in `MarketCMD` and `AbandonMarketPluginImpl` are commented out): a row there competes with `marketPostOpenDefault`, and a win would drop the vanilla row that sets `$tradeMode` and fires `MarketPostDock`.
- `Pet.personality` and the card's Disposition use `Academy.COLOURS_BY_PERSONALITY` (`Pet.java:67`, `PetCard`); see [Academy](ACADEMY.md).
- A box whose data has no `pets.csv` row (unknown id, or null data in a real cargo) is invalid (`PetItemPlugin.isInvalid()`): no icon, the tooltip says so, a right-click throws `IllegalStateException`. Codex stacks have null data and no cargo (`obf/codex2/CodexDetailPanel.java:935`) and show the `hampter` fallback.
- Dialogs from `showInteractionDialogFromCargo` do not become `getCurrentInteractionDialog()` (`obf/campaign/CampaignState.java:2298-2318`, `:2372`), so `isFleetCargo()` stays true while the Cryochamber popup is open. A second box still cannot be clicked: the dialog opens inside the right-click and its full screen backing panel absorbs all input (`com.fs.starfarer.ui.oo0O`, `absorbOutsideEvents` defaults to true, `obf/ui/_c199.java:18`, `:44-63`; input order inferred from vanilla dialogs being modal).
- `EscapeInterceptListener` and `EscBlockerMemoryIterator` are also used by `industries/museum/plugins/ManageParadeDialoguePlugin.java` ([Museum](MUSEUM.md)).
- `SelfRepairingBuiltInHullmod` is the base for changeling `HandBuiltHullmod`, Hellpods and distress hullmods ([Changeling](CHANGELING.md), [Distress](../exploration/DISTRESS.md)). `PetHullmod` is the plugin class for the ghost ship rows in `hull_mods.csv:20-59`; those ships have no pet, so `getPet()` returns null there.
- Any code that removes ships from the player fleet without a market transaction, a battle or a scuttle does not report a pet death; the pet stays registered with a stale member (inferred).
- The manager only adds the player fleet event listener on creation; a replaced player fleet object loses it (inferred).
- A stored ship's `getFleetData()` is the `getMothballedShips()` of the submarket cargo holding it, with no fleet (`obf/campaign/fleet/FleetData.java:482`, `addFleetMember` sets it). `getFoodCargo` finds that cargo by identity over `getEconomy().getMarketsCopy()`, the same markets the vanilla storage list shows (`obf/campaign/command/OutpostListPanel.java:271`); a ship stored on a market outside the economy is not found and its pet starves.

## Known issues and dormant code

| Item | State |
|---|---|
| Storage fee report | Changelog_todo: "Fix the -1c pet food cost in monthly report"; not explained by the storage fee code |
| `YourMod_PetName_PetHullmod` | Checks `isModEnabled("indEvo")` (`:31`, `:48`); the mod id is `IndEvo` and `ModManager.isModEnabled` compares exactly (`obf/launcher/ModManager.java:196`), so a copy of the template never applies effects or tooltip |
| `PetStatusManager.dev()` | Runcode helper |
| `graphics/pets/alt/`, `gammacore.png`, `glowjar.png`, `mycojar.png` | Not referenced by `pets.csv` (inferred unused) |
| Changelog_todo | "Drop some pets from fleet battles (rarely)", "Pet stations: Add main menu dialogue" |

## Maintenance

Update this doc when pets, pet storage, the pet submarket or `pets.csv` columns change. Settings live in [Settings](../core/SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
