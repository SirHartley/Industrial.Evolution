package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.SpecialItemPlugin;
import indevo.ids.Ids;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.dialogue.PetPickerInteractionDialoguePlugin;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;

import java.util.ArrayList;
import java.util.List;

public final class PetsChecks {
    private PetsChecks() {
    }

    private static Pet stored(MarketAPI market, String name) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        fleet.getFleetData().addFleetMember(member);

        Pet pet = new Pet("slaghound", name);
        PetStatusManager.getInstance().register(pet);
        pet.place(member, null);

        CargoAPI storage = Misc.getStorageCargo(market);
        storage.initMothballedShips(Global.getSector().getPlayerFaction().getId());
        fleet.getFleetData().removeFleetMember(member);
        storage.getMothballedShips().addFleetMember(member);
        return pet;
    }

    // the real Cryochamber dialog, counting how often advance opens the ship picker
    static final class CountingPicker extends PetPickerInteractionDialoguePlugin {
        static CountingPicker last;
        int shown = 0;

        CountingPicker(PetData data) {
            super(data, new SpecialItemPlugin.RightClickActionHelper() {
                public void removeFromClickedStackFirst(int num) {
                }

                public void removeFromAnyStack(CargoItemType type, Object data, int num) {
                }

                public void addItems(CargoItemType type, Object data, float quantity) {
                }

                public float getNumItems(CargoItemType type, Object data) {
                    return 1f;
                }
            });
        }

        @Override
        public void showShipPicker() {
            shown++;
            super.showShipPicker();
        }
    }

    private static FleetMemberAPI ship() {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        return member;
    }

    private static boolean hasEffect(Pet pet, FleetMemberAPI member) {
        member.updateStats();
        return member.getStats().getCRLossPerSecondPercent().getPercentBonus(pet.getData().hullmod) != null;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            Misc.getStorageCargo(c.market("a")).addCommodity("ore", 10f);
            c.put("fed", stored(c.market("a"), "Fed").id);
            c.put("hungry", stored(c.market("b"), "Hungry").id);
            c.days(33f);
        });
        steps.add(c -> {
            Pet fed = PetStatusManager.getInstance().get((String) c.get("fed"));
            Pet hungry = PetStatusManager.getInstance().get((String) c.get("hungry"));
            float ore = Misc.getStorageCargo(c.market("a")).getCommodityQuantity("ore");
            c.expect(!fed.isDead() && !fed.isStarving(), "1007 stored pet with food in that storage is fine");
            c.expect(ore < 10f, "1007 stored pet ate from the storage of its market (ore " + ore + ")");
            c.expect(!hungry.isDead(), "1013 stored pet does not die at month end");
            c.expect(hungry.isStarving(), "1007 stored pet without food starves");
            c.days(33f);
        });
        steps.add(c -> {
            Pet fed = PetStatusManager.getInstance().get((String) c.get("fed"));
            Pet hungry = PetStatusManager.getInstance().get((String) c.get("hungry"));
            c.expect(!fed.isDead() && !fed.isStarving(), "fed pet alive after a second month");
            c.expect(hungry.isDead(), "unfed pet dead after a second month");
            c.expect(hungry.deathData != null && hungry.deathData.cause == PetStatusManager.PetDeathCause.STARVED, "unfed pet died of starvation (" + (hungry.deathData == null ? null : hungry.deathData.cause) + ")");
        });
        steps.add(c -> {
            Global.getSector().getPlayerFleet().getCargo().addCommodity("ore", 1000f);
            FleetMemberAPI first = ship();
            FleetMemberAPI second = ship();

            Pet pet = new Pet("slaghound", "Mover");
            PetStatusManager.getInstance().register(pet);
            pet.place(first, null);
            c.expect(!pet.isUnsettled() && pet.getMood() == PetMood.CONTENT, "first assign is no move (" + pet.getMood() + ")");
            c.put("mover", pet.id);
            c.put("moverFirst", first);
            c.put("moverShip", second);
            // the 365 day ramp starts at 0, a zero modifier is never stored
            c.days(5f);
        });
        steps.add(c -> {
            Pet pet = PetStatusManager.getInstance().get((String) c.get("mover"));
            FleetMemberAPI first = (FleetMemberAPI) c.get("moverFirst");
            FleetMemberAPI second = (FleetMemberAPI) c.get("moverShip");
            c.expect(pet.getEffectFract() > 0f && hasEffect(pet, first), "settled pet applies its hullmod effect after 5 days (fract " + pet.getEffectFract() + ")");

            pet.place(second, null);
            c.expect(pet.assignedFleetMember == second && PetStatusManager.getInstance().getPet(first.getVariant()) == null, "moved pet sits on the new ship only");
            c.expect(pet.isUnsettled() && pet.getMood() == PetMood.UNSETTLED, "moved pet is Unsettled (" + pet.getMood() + ")");
            c.near(pet.unsettledDays, 30f, 0.01f, "Unsettled lasts 30 days");
            c.expect(!hasEffect(pet, second), "Unsettled pet applies no hullmod effect");
            c.days(31f);
        });
        steps.add(c -> {
            Pet pet = PetStatusManager.getInstance().get((String) c.get("mover"));
            c.expect(!pet.isDead() && !pet.isUnsettled(), "pet settles after 30 days");
            c.expect(pet.getMood() == PetMood.CONTENT, "settled fed pet is Content (" + pet.getMood() + ")");
            c.expect(pet.getEffectFract() > 0f && hasEffect(pet, (FleetMemberAPI) c.get("moverShip")), "settled pet applies its effect again (fract " + pet.getEffectFract() + ")");

            pet.shellShock();
            c.expect(pet.getMood() == PetMood.SHELL_SHOCKED, "shell shocked pet shows the mood (" + pet.getMood() + ")");
            c.days(8f);
        });
        steps.add(c -> {
            Pet pet = PetStatusManager.getInstance().get((String) c.get("mover"));
            c.expect(pet.getMood() == PetMood.CONTENT, "Shell shocked wears off after 7 days (" + pet.getMood() + ")");

            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            cargo.removeCommodity("food", cargo.getCommodityQuantity("food"));
            cargo.addCommodity("food", 10f);
            cargo.addCommodity("supplies", 50f);

            Pet eater = new Pet("bugdog", "Eater");
            PetStatusManager.getInstance().register(eater);
            eater.place(ship(), null);
            float supplies = cargo.getCommodityQuantity("supplies");
            c.expect(PetStatusManager.getInstance().feed(eater), "pet with food in the cargo eats");
            c.near(cargo.getCommodityQuantity("supplies"), supplies, 0.01f, "pet eats its food before Supplies");
            c.expect(eater.getMood() == PetMood.CONTENT, "pet fed its food is Content (" + eater.getMood() + ")");

            cargo.removeCommodity("food", cargo.getCommodityQuantity("food"));
            c.expect(PetStatusManager.getInstance().feed(eater), "pet without its food eats Supplies");
            c.expect(cargo.getCommodityQuantity("supplies") < supplies, "Supplies were taken");
            c.expect(eater.getMood() == PetMood.UNHAPPY_WITH_FOOD, "pet fed Supplies is Unhappy with its food (" + eater.getMood() + ")");
        });
        steps.add(c -> {
            MarketAPI market = c.market("a");
            if (!market.hasIndustry(Ids.PET_STORE)) market.addIndustry(Ids.PET_STORE);
            PetShop shop = (PetShop) market.getIndustry(Ids.PET_STORE);
            FleetMemberAPI home = ship(), other = ship();

            Pet keeper = new Pet("slaghound", "Keeper");
            PetStatusManager.getInstance().register(keeper);
            keeper.place(home, null);
            keeper.place(null, shop);
            c.expect(keeper.storage == shop && keeper.assignedFleetMember == null && shop.getStoredPetsPetsCopy().contains(keeper), "placed in storage, the pet sits in the Animal Center");
            c.expect(PetStatusManager.getInstance().getPet(home.getVariant()) == null && !home.getVariant().hasHullMod(keeper.getData().hullmod), "its ship lost the pet hullmod");
            c.expect(!keeper.isUnsettled(), "storing is no move (" + keeper.getMood() + ")");

            keeper.place(home, null);
            c.expect(keeper.assignedFleetMember == home && keeper.storage == null && !shop.getStoredPetsPetsCopy().contains(keeper), "back from storage, the pet is on its ship and out of storage");
            c.expect(!keeper.isUnsettled() && keeper.getMood() == PetMood.CONTENT, "storage and back to the same ship is no move (" + keeper.getMood() + ")");

            keeper.place(null, shop);
            keeper.place(other, null);
            c.expect(keeper.isUnsettled() && keeper.getMood() == PetMood.UNSETTLED, "storage to a different ship is a move (" + keeper.getMood() + ")");
            c.near(keeper.unsettledDays, 30f, 0.01f, "storage to a different ship: Unsettled for 30 days");

            Pet orphan = new Pet("bugdog", "Orphan");
            PetStatusManager.getInstance().register(orphan);
            Throwable crash = null;
            try {
                PetStatusManager.getInstance().reportPetDied(orphan, PetStatusManager.PetDeathCause.UNKNOWN);
                PetStatusManager.getInstance().reportPetDied(orphan, PetStatusManager.PetDeathCause.UNKNOWN);
            } catch (Throwable t) {
                crash = t;
            }
            c.expect(crash == null, "pet with no ship and no storage dies without a crash (" + crash + ")");
            c.expect(orphan.isDead() && orphan.deathData != null, "the orphan pet is dead with death data");

            CountingPicker picker = new CountingPicker(PetDataRepo.get("bugdog"));
            CountingPicker.last = picker;
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(picker, Global.getSector().getPlayerFleet());
            c.frames(30);
        });
        steps.add(c -> {
            CountingPicker.last.setName("Cryo");
            c.frames(120);
        });
        steps.add(c -> {
            c.expect(CountingPicker.last.shown == 1, "the Cryochamber ship picker opens once after naming (" + CountingPicker.last.shown + ")");
            c.shot("pets-cryo-picker");
        });
        steps.add(c -> {
            c.keepDialog(false);
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            FleetMemberAPI a = ship(), b = ship();
            Pet saver = new Pet("bugdog", "Saver");
            PetStatusManager.getInstance().register(saver);
            saver.place(a, null);
            saver.place(b, null);
            saver.shellShock();
            cargo.removeCommodity("food", cargo.getCommodityQuantity("food"));
            cargo.addCommodity("supplies", 50f);
            PetStatusManager.getInstance().feed(saver);
            c.expect(saver.lastMealSupplies && saver.unsettledDays > 29f && saver.shellShockedDays > 6f && b.getId().equals(saver.homeShipId), "saver pet has all four new fields set before the save");
            c.put("saver", saver.id);
            c.put("saverHome", b.getId());
            c.put("saverUnsettled", saver.unsettledDays);
            c.put("saverShock", saver.shellShockedDays);
            c.reload();
        });
        steps.add(c -> {
            Pet saver = PetStatusManager.getInstance().get((String) c.get("saver"));
            c.expect(saver != null, "saver pet is registered after the reload");
            if (saver == null) return;
            c.near(saver.unsettledDays, (Float) c.get("saverUnsettled"), 0.5f, "unsettledDays survives save and load");
            c.near(saver.shellShockedDays, (Float) c.get("saverShock"), 0.5f, "shellShockedDays survives save and load");
            c.expect(saver.lastMealSupplies, "lastMealSupplies survives save and load");
            c.expect(c.get("saverHome").equals(saver.homeShipId), "homeShipId survives save and load (" + saver.homeShipId + ")");
            c.expect(saver.getMood() == PetMood.UNSETTLED && saver.assignedFleetMember != null && PetStatusManager.getInstance().getPet(saver.assignedFleetMember.getVariant()) == saver, "after the reload the pet is Unsettled on its ship");
        });
        return steps;
    }
}
