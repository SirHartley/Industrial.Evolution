package iecheck;

import assortment_of_things.frontiers.FrontiersUtils;
import assortment_of_things.frontiers.SettlementData;
import assortment_of_things.frontiers.data.SettlementFacilitySlot;
import assortment_of_things.frontiers.plugins.facilities.BaseSettlementFacility;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingContractMemory;
import indevo.industries.engineeringhub.ReverseEngineering;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.ratsettlement.RatSettlements;
import indevo.industries.ratsettlement.facility.EngineeringAnnex;
import indevo.industries.ratsettlement.facility.PetHabitat;
import indevo.industries.ratsettlement.facility.SettlementFacility;
import indevo.items.VPCItemPlugin;
import indevo.utils.helper.MiscIE;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// RAT settlement facilities (PR #213), the logic: all ten IE facilities on one settlement next to Jangala (Hegemony), picker gating,
// Annex (class lock, progress, Accelerate, Finish with relics and with a blueprint, Abandon with and without a hub), Restoration Bay 31 days,
// Requisition 40%, Fabricator and Hangar month end, Consulate month end, Courier contract with only the office, Habitat brood, bill, mutation,
// Quarters storage, then demolish everything and count what comes back
public final class RatFlowChecks {
    private static final Logger log = Global.getLogger(RatFlowChecks.class);
    static final String[] ALL = {Ids.RAT_ANNEX, Ids.RAT_GANTRY, Ids.RAT_QUARTERS, Ids.RAT_COURIER, Ids.RAT_HABITAT,
            Ids.RAT_REQUISITION, Ids.RAT_RESTORATION, Ids.RAT_FABRICATOR, Ids.RAT_EXHIBITION, Ids.RAT_CONSULATE};

    private RatFlowChecks() {
    }

    static SettlementData data() {
        return RatSettleChecks.data();
    }

    static MarketAPI market() {
        return RatSettlements.getSettlementMarket();
    }

    static CargoAPI storage() {
        return MiscIE.getStorageCargo(market());
    }

    static <T> T fac(String id) {
        @SuppressWarnings("unchecked") T t = (T) RatSettlements.getFacility(id);
        return t;
    }

    static java.lang.invoke.VarHandle handle(Class<?> cls, String name, Class<?> type) {
        try {
            return java.lang.invoke.MethodHandles.privateLookupIn(cls, java.lang.invoke.MethodHandles.lookup()).findVarHandle(cls, name, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Object field(Object o, String name, Class<?> type) {
        return handle(o.getClass(), name, type).get(o);
    }

    static void setField(Object o, String name, Class<?> type, Object value) {
        handle(o.getClass(), name, type).set(o, value);
    }

    static Setting parent(BaseSettlementFacility f) {
        try {
            Class<?> cls = f.getClass();
            return (Setting) java.lang.invoke.MethodHandles.privateLookupIn(cls, java.lang.invoke.MethodHandles.lookup()).findVirtual(cls, "parentSetting", java.lang.invoke.MethodType.methodType(Setting.class)).invoke(f);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static FleetMemberAPI ship(String variant, String name) {
        FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        m.setShipName(name);
        m.getRepairTracker().setCR(0.7f);
        return m;
    }

    static FleetMemberAPI inFleet(String variant, String name) {
        FleetMemberAPI m = ship(variant, name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
        return m;
    }

    static FleetMemberAPI find(List<FleetMemberAPI> list, String name) {
        for (FleetMemberAPI m : list) if (name.equals(m.getShipName())) return m;
        return null;
    }

    static FleetMemberAPI inStorage(String name) {
        return find(storage().getMothballedShips().getMembersListCopy(), name);
    }

    static FleetMemberAPI inPlayerFleet(String name) {
        return find(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy(), name);
    }

    static int specials(CargoAPI cargo, String id, String data) {
        int n = 0;
        for (CargoStackAPI s : cargo.getStacksCopy()) {
            SpecialItemData d = s.getSpecialDataIfSpecial();
            if (d != null && id.equals(d.getId()) && (data == null || data.equals(d.getData()))) n += (int) s.getSize();
        }
        return n;
    }

    static PetStatusManager pets() {
        return PetStatusManager.getInstance();
    }

    static Pet pet(Ctx c, String species, String name, float age, PetShop storeAt) {
        Pet pet = new Pet(species, name);
        pets().register(pet);
        if (storeAt != null) pet.place(null, storeAt);
        else pet.place(inFleet("wolf_Assault", "ISS " + name), null);
        pet.age = age;
        c.put("pet:" + name, pet.id);
        return pet;
    }

    static Pet pet(Ctx c, String name) {
        return pets().get((String) c.get("pet:" + name));
    }

    // a line of last month's report anywhere in the tree, by node id suffix
    static FDNode node(FDNode n, String suffix) {
        if (n == null) return null;
        for (java.util.Map.Entry<String, FDNode> e : n.getChildren().entrySet()) {
            if (e.getKey().endsWith(suffix)) return e.getValue();
            FDNode hit = node(e.getValue(), suffix);
            if (hit != null) return hit;
        }
        return null;
    }

    static float relation() {
        return Global.getSector().getPlayerFaction().getRelationship(Factions.HEGEMONY);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI jangala = Global.getSector().getEconomy().getMarket("jangala");
            PlanetAPI planet = (PlanetAPI) jangala.getPrimaryEntity();
            jangala.addCondition("irradiated");
            SettlementData data = RatSettleChecks.found(planet);
            for (int i = 0; i < 5; i++) data.getFacilitySlots().add(new SettlementFacilitySlot(data));
            for (String id : ALL) c.expect(RatSettleChecks.build(data, id), "built " + id);
            c.note("functional " + RatSettleChecks.built(data));
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(2000000f);
            Global.getSector().getPlayerFleet().getCargo().addCommodity(Commodities.FOOD, 500f);

            // picker gating: each IE facility shows while its parent setting is on
            List<String> gating = new ArrayList<>();
            boolean gateOk = true;
            for (BaseSettlementFacility p : FrontiersUtils.INSTANCE.getAllFacilityPlugins()) {
                if (!(p instanceof SettlementFacility)) continue;
                Setting s = parent(p);
                gating.add(p.getID() + "<-" + s.name());
                gateOk &= p.shouldShowInPicker() == s.enabled();
            }
            c.note("picker parents: " + gating);
            c.expect(gating.size() == 10 && gateOk, "ten IE facilities in the picker, each shown while its parent setting is on");
            try {
                Colony.setting(Setting.PETS, false);
                boolean hidden = false;
                for (BaseSettlementFacility p : FrontiersUtils.INSTANCE.getAllFacilityPlugins()) if (Ids.RAT_HABITAT.equals(p.getID())) hidden = !p.shouldShowInPicker();
                c.expect(hidden, "Pets off: the Pet Habitat leaves the picker");
                Colony.setting(Setting.ENGHUB, false);
                for (BaseSettlementFacility p : FrontiersUtils.INSTANCE.getAllFacilityPlugins()) if (Ids.RAT_ANNEX.equals(p.getID())) c.expect(!p.shouldShowInPicker(), "Engineering Hub off: the Annex leaves the picker");
                Colony.setting(Setting.PETS, true);
                Colony.setting(Setting.ENGHUB, true);
            } catch (Exception e) {
                c.expect(false, "setting flip: " + e);
            }

            // consulate: buildable on a planet another faction owns, greyed on a neutral one
            BaseSettlementFacility consulate = null;
            for (BaseSettlementFacility p : FrontiersUtils.INSTANCE.getAllFacilityPlugins()) if (Ids.RAT_CONSULATE.equals(p.getID())) consulate = p;
            c.expect(consulate != null && consulate.canBeBuild(), "Consulate can be built next to Jangala (Hegemony)");
            PlanetAPI neutral = Colony.planets(3).get(2);
            data.setPrimaryPlanet(neutral);
            c.expect(consulate != null && !consulate.canBeBuild(), "Consulate greyed on an unowned planet (" + neutral.getName() + ", " + neutral.getMarket().getFactionId() + ")");
            data.setPrimaryPlanet(planet);

            // annex: three clean lashers, the first sets the class
            EngineeringAnnex annex = fac(Ids.RAT_ANNEX);
            for (int i = 1; i <= 3; i++) inFleet("lasher_CS", "Annex" + i);
            FleetMemberAPI wolf = inFleet("wolf_Assault", "Annexwolf");
            c.expect(annex.canTake(wolf) && annex.accepts(wolf), "no class yet: a wolf is accepted");
            annex.put(inPlayerFleet("Annex1"));
            c.expect("lasher".equals(annex.getHullId()), "the first ship sets the class (" + annex.getHullId() + ")");
            c.expect(annex.canTake(wolf) && !annex.accepts(wolf), "class lock: a wolf is no longer accepted");
            annex.put(inPlayerFleet("Annex2"));
            annex.put(inPlayerFleet("Annex3"));
            c.expect(annex.getDropOff().size() == 3 && inPlayerFleet("Annex1") == null, "three lashers in the drop off, out of the fleet");

            // restoration: a hammerhead with two d-mods in settlement storage
            FleetMemberAPI resto = ship("hammerhead_Balanced", "Resto");
            ShipVariantAPI v = resto.getVariant().clone();
            v.setSource(com.fs.starfarer.api.loading.VariantSource.REFIT);
            v.addPermaMod("degraded_engines");
            v.addPermaMod("faulty_grid");
            resto.setVariant(v, false, true);
            storage().getMothballedShips().addFleetMember(resto);
            c.expect(DModManager.getNumNonBuiltInDMods(inStorage("Resto").getVariant()) == 2, "Resto in storage with 2 D-mods");

            // fabricator: a common vpc loaded
            Object fab = fac(Ids.RAT_FABRICATOR);
            SpecialItemData vpc = VPCItemPlugin.roll(VPCItemPlugin.Tier.COMMON, new Random(3));
            setField(fab, "vpc", SpecialItemData.class, vpc);
            c.note("vpc " + vpc.getId() + " " + vpc.getData() + ", outputs " + VPCItemPlugin.getCommodityIds(vpc));
            c.put("vpcData", vpc.getData());

            // exhibition: rare hulls in the hangar storage
            CargoAPI hangar = market().getSubmarket(Ids.MUSEUM_SUBMARKET).getCargo();
            if (hangar.getMothballedShips() == null) hangar.initMothballedShips(Factions.PLAYER);
            for (String vid : new String[]{"paragon_Elite", "astral_Elite", "doom_Strike", "odyssey_Balanced"}) hangar.getMothballedShips().addFleetMember(ship(vid, "Show " + vid));

            // requisition: submarket at 40%
            c.expect(market().getSubmarket(Ids.REQMARKET) != null, "Requisition Office adds its submarket");
            float tariff = market().getSubmarket(Ids.REQMARKET).getPlugin().getTariff();
            c.near(tariff, 0.40f, 0.001f, "Requisition tariff 40% (" + tariff + ")");
            market().getSubmarket(Ids.REQMARKET).getPlugin().updateCargoPrePlayerInteraction();
            CargoAPI req = market().getSubmarket(Ids.REQMARKET).getCargo();
            c.note("requisition stock: " + req.getWeapons().size() + " weapon stacks, " + req.getStacksCopy().size() + " stacks");
            c.expect(!req.getStacksCopy().isEmpty(), "Requisition stock is not empty");

            // quarters: an officer stored
            PersonAPI officer = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 2, true);
            officer.getName().setFirst("Quinn");
            officer.getName().setLast("Stored");
            Global.getSector().getPlayerFleet().getFleetData().addOfficer(officer);
            Object academy = field(fac(Ids.RAT_QUARTERS), "academy", indevo.industries.ratsettlement.facility.QuartersAcademy.class);
            ((indevo.industries.academy.industry.Academy) academy).storeOfficer(officer);
            c.expect(((indevo.industries.academy.industry.Academy) academy).getOfficerStorage().contains(officer), "officer stored in the Quarters");
            c.put("officer", officer.getId());

            // habitat: a hamster brood and a stored doge
            PetHabitat habitat = fac(Ids.RAT_HABITAT);
            PetShop shop = habitat.getShop();
            Pet h1 = pet(c, "hampter", "Hamone", 8f, null);
            Pet h2 = pet(c, "hampter", "Hamtwo", 8f, null);
            pet(c, "doge", "Keeper", 300f, shop);
            float irr = shop.getMutationRate(h1, h2);
            jangala.removeCondition("irradiated");
            float plain = shop.getMutationRate(h1, h2);
            jangala.addCondition("irradiated");
            c.note("mutation rate irradiated " + irr + ", plain " + plain);
            c.expect(plain > 0f && Math.abs(irr / plain - 2f) < 0.05f, "irradiated planet doubles the habitat mutation rate (" + irr + " vs " + plain + ")");
            c.expect(PetShop.class.getName().length() > 0 && RatSettlements.getHabitat() == shop, "RatSettlements finds the habitat");
            shop.startBrood(h1, h2, 0);
            c.expect(shop.getBrood(0) != null, "hamster brood started in the habitat");

            // courier: settlement storage to colony a with no port anywhere
            boolean port = false;
            for (MarketAPI m : com.fs.starfarer.api.util.Misc.getPlayerMarkets(true)) port |= m.hasIndustry(Ids.PORT);
            c.expect(!port, "no Courier Port in any colony");
            storage().addCommodity(Commodities.METALS, 300f);
            ShippingContractMemory.getContractList().clear();
            ShippingContract k = new ShippingContract();
            k.name = "Settlement metals";
            k.fromMarketId = RatSettlements.MARKET_ID;
            k.fromSubmarketId = "rat_settlement_storage";
            k.toMarketId = c.market("a").getId();
            k.toSubmarketId = Submarkets.SUBMARKET_STORAGE;
            k.scope = ShippingContract.Scope.SPECIFIC_CARGO;
            k.targetCargo.addCommodity(Commodities.METALS, 200f);
            k.setRecurrentDays(0);
            ShippingContractMemory.getContractList().add(k);
            c.put("metalsA", MiscIE.getStorageCargo(c.market("a")).getCommodityQuantity(Commodities.METALS));
            c.note("contract from " + k.getFromMarket() + ", to " + k.getToMarket());

            c.put("rel0", relation());
            c.put("credits0", Global.getSector().getPlayerFleet().getCargo().getCredits().get());
            Colony.jump(planet.getContainingLocation(), planet.getLocation().x + 400f, planet.getLocation().y);
            c.reload();
        });
        steps.add(c -> {
            c.expect(data() != null && RatSettleChecks.built(data()).size() == 11, "after a save and load all ten IE facilities stand (" + (data() == null ? "none" : RatSettleChecks.built(data())) + ")");
            EngineeringAnnex annex = fac(Ids.RAT_ANNEX);
            c.expect(annex.getDropOff().size() == 3 && "lasher".equals(annex.getHullId()), "the Annex keeps its class and drop off");
            PetShop shop = RatSettlements.getHabitat();
            c.expect(shop != null && shop.getBrood(0) != null, "the habitat keeps its brood");
            c.expect(field(fac(Ids.RAT_FABRICATOR), "vpc", SpecialItemData.class) != null, "the Fabricator keeps its VPC");
            c.days(3f);
        });
        steps.add(c -> {
            EngineeringAnnex annex = fac(Ids.RAT_ANNEX);
            c.expect(annex.getInWork() != null && annex.getDropOff().size() == 2, "a lasher went into work (" + annex.getInWorkName() + ", drop off " + annex.getDropOff().size() + ")");
            int before = annex.getSpeedUpDaysLeft();
            annex.speedUp(5);
            c.note("accelerate: " + before + " days left, after 5 " + annex.getSpeedUpDaysLeft());
            c.expect(annex.getSpeedUpDaysLeft() == before - 5, "Accelerate takes 5 days off the ship in work");
            c.days(8f);
        });
        steps.add(c -> {
            PetShop shop = RatSettlements.getHabitat();
            PetBrood b = shop.getBrood(0);
            c.expect(b != null && b.isReady() && !b.getYoung().isEmpty(), "the hamster brood is ready with young after 11 days (" + (b == null ? "none" : b.getYoung().size()) + ")");
            EngineeringAnnex annex = fac(Ids.RAT_ANNEX);
            c.note("annex progress " + annex.getProgress() + ", in work " + annex.getInWorkName() + ", drop off " + annex.getDropOff().size());
            c.expect(annex.getProgress() >= 0.34f, "the first lasher is done, 35% (" + annex.getProgress() + ")");
            c.near(ReverseEngineering.getProgress("lasher"), annex.getProgress(), 0.001f, "the progress is the shared hub map");
            c.days(25f);
        });
        steps.add(c -> {
            // 36 days in: one month end passed
            int dmods = inStorage("Resto") == null ? -1 : DModManager.getNumNonBuiltInDMods(inStorage("Resto").getVariant());
            c.expect(dmods == 1, "Restoration Bay removed one D-mod after 31 days in storage (" + dmods + " left)");
            MonthlyReport prev = SharedData.getData().getPreviousReport();
            FDNode bill = node(prev.getRoot(), Ids.RAT_HABITAT + "_storage");
            c.note("habitat storage line: " + (bill == null ? "none" : bill.name + " " + bill.upkeep));
            c.expect(bill != null && bill.upkeep > 0f, "the habitat storage bill is on last month's report");
            FDNode repair = node(prev.getRoot(), Ids.RAT_RESTORATION + "_repairs");
            FDNode repairNow = node(SharedData.getData().getCurrentReport().getRoot(), Ids.RAT_RESTORATION + "_repairs");
            c.note("repair line: last " + (repair == null ? "none" : repair.upkeep) + ", now " + (repairNow == null ? "none" : repairNow.upkeep));
            c.expect((repair != null && repair.upkeep > 0f) || (repairNow != null && repairNow.upkeep > 0f), "the D-mod removal is billed");
            List<String> outs = VPCItemPlugin.getCommodityIds(new SpecialItemData(ItemIds.VPC, (String) c.get("vpcData")));
            float made = 0f;
            for (String id : outs) made += storage().getCommodityQuantity(id);
            c.note("fabricator outputs " + outs + " in storage: " + made);
            c.expect(made > 0f, "the Fabricator deposited its VPC output at month end");
            var inc = data().getStats().getIncome().getFlatStatMod("IndEvo_rat_exhibition");
            c.note("hangar income " + (inc == null ? "none" : inc.getValue()));
            c.expect(inc != null && inc.getValue() > 0f, "the Exhibition Hangar adds settlement income at month end");
            float rel0 = (Float) c.get("rel0");
            c.note("hegemony relation " + rel0 + " -> " + relation());
            c.expect(relation() > rel0 + 0.02f, "the Consulate raised the Hegemony standing at month end");
            float metalsA = MiscIE.getStorageCargo(c.market("a")).getCommodityQuantity(Commodities.METALS);
            float metalsS = storage().getCommodityQuantity(Commodities.METALS);
            c.note("metals: colony a " + c.get("metalsA") + " -> " + metalsA + ", settlement " + metalsS + ", contracts " + ShippingContractMemory.getContractList().size());
            c.expect(metalsA >= (Float) c.get("metalsA") + 199f, "the Courier contract delivered settlement metals with only the office");
            EngineeringAnnex annex = fac(Ids.RAT_ANNEX);
            c.note("annex progress " + annex.getProgress() + ", in work " + annex.getInWorkName() + ", drop off " + annex.getDropOff().size());
            c.days(12f);
        });
        steps.add(c -> {
            EngineeringAnnex annex = fac(Ids.RAT_ANNEX);
            c.note("annex progress " + annex.getProgress() + ", ready " + annex.isReady() + ", relic cost " + annex.getRelicCost());
            c.expect(annex.isReady(), "three lashers bring the class to 100%");
            // the annex onNewDay never fires (day counter bug), so force the data to test Finish, Abandon and demolish
            if (!annex.isReady()) {
                c.note("forcing lasher data to 100% to test the rest");
                ReverseEngineering.addProgress("lasher", 1f);
            }
            c.expect(!annex.finish(null), "Finish with relics refused without relics");
            Global.getSector().getPlayerFleet().getCargo().addCommodity(ItemIds.RARE_PARTS, annex.getRelicCost());
            int bps = specials(storage(), Items.SHIP_BP, "lasher");
            c.expect(annex.finish(null), "Finish with relics");
            c.expect(specials(storage(), Items.SHIP_BP, "lasher") == bps + 1 && annex.getHullId() == null && ReverseEngineering.getProgress("lasher") == 0f,
                    "the lasher blueprint lands in settlement storage, the class is free, the data is spent");
            c.expect(Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS) == 0f, "the relics are taken");

            // finish with a blueprint of the same size
            FleetMemberAPI wolf = inPlayerFleet("Annexwolf");
            annex.put(wolf);
            ReverseEngineering.addProgress("wolf", 1f);
            storage().addSpecial(new SpecialItemData(Items.SHIP_BP, "hound"), 1f);
            storage().addSpecial(new SpecialItemData(Items.SHIP_BP, "onslaught"), 1f);
            List<EngineeringAnnex.Payment> pays = annex.getPayments();
            List<String> payIds = new ArrayList<>();
            for (EngineeringAnnex.Payment p : pays) payIds.add(p.item().getData());
            c.note("payments for wolf: " + payIds);
            c.expect(payIds.contains("hound") && !payIds.contains("onslaught"), "a frigate blueprint pays for a frigate class, a capital one does not");
            EngineeringAnnex.Payment hound = null;
            for (EngineeringAnnex.Payment p : pays) if ("hound".equals(p.item().getData())) hound = p;
            c.expect(annex.finish(hound), "Finish with the hound blueprint");
            c.expect(specials(storage(), Items.SHIP_BP, "hound") == 0 && specials(storage(), Items.SHIP_BP, "wolf") == 1, "the hound blueprint is overwritten into a wolf blueprint");
            c.expect(inStorage("Annexwolf") != null, "the drop off wolf goes to settlement storage after Finish");

            // abandon without a hub wipes the data
            FleetMemberAPI cen = inFleet("centurion_Assault", "Abandonone");
            annex.put(cen);
            ReverseEngineering.addProgress("centurion", 0.3f);
            c.expect(!EngineeringAnnex.ownsHub(), "no Engineering Hub owned");
            annex.abandon();
            c.expect(ReverseEngineering.getProgress("centurion") == 0f && annex.getHullId() == null && inStorage("Abandonone") != null, "Abandon without a hub: data wiped, ship to storage");

            // abandon with a hub keeps the data
            MarketAPI a = c.market("a");
            a.addIndustry(Ids.ENGHUB);
            a.getIndustry(Ids.ENGHUB).finishBuildingOrUpgrading();
            FleetMemberAPI cen2 = inFleet("centurion_Assault", "Abandontwo");
            annex.put(cen2);
            ReverseEngineering.addProgress("centurion", 0.3f);
            c.expect(EngineeringAnnex.ownsHub(), "an Engineering Hub is owned");
            annex.abandon();
            c.near(ReverseEngineering.getProgress("centurion"), 0.3f, 0.001f, "Abandon with a hub keeps the data for the hub");

            // demolish: everything comes back
            FleetMemberAPI back = inFleet("lasher_CS", "Demolished");
            annex.put(back);
            CargoAPI st = storage();
            int chambersBefore = specials(st, ItemIds.PET_CHAMBER, null);
            PetShop shop = RatSettlements.getHabitat();
            int living = 0;
            for (Pet p : shop.getStoredPetsPetsCopy()) if (!p.isDead()) living++;
            for (PetBrood b : shop.getBroods()) living += b.getYoung().size();
            c.note("habitat before demolish: stored living " + shop.getStoredPetsPetsCopy().size() + ", broods " + shop.getBroods().size() + ", total to chambers " + living);
            int hangarShips = market().getSubmarket(Ids.MUSEUM_SUBMARKET).getCargo().getMothballedShips().getNumMembers();
            int storageShips = st.getMothballedShips().getNumMembers();
            for (SettlementFacilitySlot slot : data().getFacilitySlots()) if (slot.getFacilityID().startsWith("IndEvo_")) slot.removeCurrentFacility();
            c.expect(RatSettleChecks.built(data()).size() == 1, "all IE facilities demolished");
            c.expect(inStorage("Demolished") != null, "demolish: the Annex drop off goes to storage");
            boolean officerBack = false;
            for (var o : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) officerBack |= o.getPerson().getId().equals(c.get("officer"));
            c.expect(officerBack, "demolish: the stored officer comes back to the fleet");
            int chambers = specials(storage(), ItemIds.PET_CHAMBER, null) - chambersBefore;
            c.expect(chambers == living && living > 0, "demolish: living habitat pets go into Cryochambers in settlement storage (" + chambers + " of " + living + ")");
            c.expect(specials(storage(), ItemIds.VPC, null) + specials(storage(), ItemIds.VPC_RARE, null) + specials(storage(), ItemIds.VPC_ULTRA, null) >= 1, "demolish: the VPC goes back to storage");
            c.expect(market().getSubmarket(Ids.REQMARKET) == null && market().getSubmarket(Ids.MUSEUM_SUBMARKET) == null, "demolish: Requisition and Hangar submarkets gone");
            c.note("hangar ships " + hangarShips + ", storage ships before " + storageShips + " after " + storage().getMothballedShips().getNumMembers());
            c.expect(storage().getMothballedShips().getNumMembers() >= storageShips + hangarShips + 1, "demolish: the hangar ships go to settlement storage");
            c.note("done");
        });
        return steps;
    }
}
