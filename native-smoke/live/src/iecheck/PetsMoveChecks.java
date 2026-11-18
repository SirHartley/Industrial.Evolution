package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI.CoreUITradeMode;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// package 1.2: hand over to a free ship on scuttle, sale and storage, the menagerie, the effect ramp, indevo_pet
public final class PetsMoveChecks {
    private static final Logger log = Global.getLogger(PetsMoveChecks.class);
    static CampaignFleetAPI parked;
    static FleetMemberAPI oldFlagship;

    private PetsMoveChecks() {
    }

    private static CampaignFleetAPI fleet() {
        return Global.getSector().getPlayerFleet();
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static FleetMemberAPI ship(String variant, String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        member.setShipName(name);
        fleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static Pet pet(String species, String name, FleetMemberAPI on) {
        Pet pet = new Pet(species, name);
        manager().register(pet);
        pet.place(on, null);
        return pet;
    }

    private static Pet byName(String name) {
        for (Pet pet : manager().getPets()) if (pet.name.equals(name)) return pet;
        return null;
    }

    private static String where(Pet pet) {
        if (pet == null) return "no pet";
        if (pet.isDead()) return "dead (" + (pet.deathData == null ? null : pet.deathData.cause) + ")";
        if (pet.assignedFleetMember != null) return "on " + pet.assignedFleetMember.getShipName();
        if (pet.storage != null) return "stored";
        return "menagerie";
    }

    private static PetShop shop(MarketAPI market) {
        if (!market.hasIndustry(Ids.PET_STORE)) market.addIndustry(Ids.PET_STORE);
        return (PetShop) market.getIndustry(Ids.PET_STORE);
    }

    private static void transfer(MarketAPI market, String submarketId, FleetMemberAPI member) {
        SubmarketAPI submarket = market.getSubmarket(submarketId);
        PlayerMarketTransaction tx = new PlayerMarketTransaction(market, submarket, CoreUITradeMode.OPEN);
        tx.getShipsSold().add(new PlayerMarketTransaction.ShipSaleInfo(member, 1000f));
        fleet().getFleetData().removeFleetMember(member);
        submarket.getCargo().initMothballedShips(Factions.PLAYER);
        submarket.getCargo().getMothballedShips().addFleetMember(member);
        Global.getSector().reportPlayerMarketTransaction(tx);
    }

    private static float food(CargoAPI cargo, Pet pet) {
        float sum = 0f;
        for (String c : pet.getData().foodCommodities) sum += cargo.getCommodityQuantity(c);
        return sum;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            CampaignFleetAPI fleet = fleet();
            oldFlagship = fleet.getFlagship();
            parked = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet check parking", true);
            FleetMemberAPI flag = ship("wolf_Assault", "ISS Flag");
            fleet.getFleetData().setFlagship(flag);
            for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                if (m == flag) continue;
                fleet.getFleetData().removeFleetMember(m);
                parked.getFleetData().addFleetMember(m);
            }
            FleetMemberAPI eagle = ship("eagle_Assault", "ISS Eagle");
            FleetMemberAPI hammer = ship("hammerhead_Balanced", "ISS Hammer");
            FleetMemberAPI enforcer = ship("enforcer_Assault", "ISS Enforcer");
            ship("lasher_CS", "ISS Lasher");
            FleetMemberAPI doomed = ship("wolf_Assault", "ISS Doomed");
            fleet.getCargo().addCommodity("food", 500f);
            fleet.getCargo().addCommodity("ore", 500f);

            c.expect(manager().getFreeShip(fleet, Collections.emptyList()) == flag, "free ship: the flagship while it has no pet");
            pet("slaghound", "Flagpet", flag);
            FleetMemberAPI free = manager().getFreeShip(fleet, Collections.emptyList());
            c.expect(free == eagle, "free ship: then the largest hull, the cruiser (" + (free == null ? null : free.getShipName()) + ")");
            FleetMemberAPI bigDp = hammer.getDeploymentPointsCost() >= enforcer.getDeploymentPointsCost() ? hammer : enforcer;
            free = manager().getFreeShip(fleet, Collections.singletonList(eagle));
            c.expect(free == bigDp, "free ship: among destroyers the higher deployment cost, never a leaving ship (" + (free == null ? null : free.getShipName()) + ", hammer " + hammer.getDeploymentPointsCost() + " enforcer " + enforcer.getDeploymentPointsCost() + ")");

            Pet scuttler = pet("bugdog", "Scuttler", doomed);
            Throwable crash = null;
            try {
                fleet.getFleetData().scuttle(doomed);
            } catch (Throwable t) {
                crash = t;
            }
            c.expect(crash == null, "scuttling a pet ship does not crash (" + crash + ")");
            c.expect(!fleet.getFleetData().getMembersListCopy().contains(doomed), "the scuttled ship left the fleet");
            c.expect(!scuttler.isDead() && scuttler.assignedFleetMember == eagle, "scuttle: the pet moved to the free cruiser (" + where(scuttler) + ")");
            c.expect(scuttler.isUnsettled(), "scuttle: the moved pet is Unsettled (" + scuttler.getMood() + ")");
            c.frames(20);
        });
        steps.add(c -> {
            c.shot("petsmove-scuttle-handover");
            CampaignFleetAPI fleet = fleet();
            for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                if (manager().getPet(m.getVariant()) == null) pet("slaghound", "Fill " + m.getShipName(), m);
            }
            FleetMemberAPI last = ship("wolf_Assault", "ISS Last");
            Pet lastPet = pet("bugdog", "Lastpet", last);
            c.expect(manager().getFreeShip(fleet, Collections.singletonList(last)) == null, "every other ship has a pet");
            Throwable crash = null;
            try {
                fleet.getFleetData().scuttle(last);
            } catch (Throwable t) {
                crash = t;
            }
            c.expect(crash == null, "scuttle without a free ship does not crash (" + crash + ")");
            c.expect(lastPet.isDead(), "scuttle without a free ship: the pet dies (" + where(lastPet) + ")");
            c.frames(20);
        });
        steps.add(c -> {
            c.shot("petsmove-scuttle-death");
            MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
            c.expect(market != null && market.getSubmarket(Submarkets.SUBMARKET_OPEN) != null, "Jangala has an open market");
            FleetMemberAPI spare = ship("wolf_Assault", "ISS Spare");
            FleetMemberAPI sold = ship("wolf_Assault", "ISS Sold");
            Pet seller = pet("bugdog", "Seller", sold);
            transfer(market, Submarkets.SUBMARKET_OPEN, sold);
            c.expect(!seller.isDead() && seller.assignedFleetMember == spare, "sale: the pet moved to the free ship (" + where(seller) + ")");
            c.expect(seller.isUnsettled(), "sale: the moved pet is Unsettled");
            c.expect(!sold.getVariant().hasHullMod(seller.getData().hullmod), "sale: the sold ship lost the pet hullmod");

            FleetMemberAPI sold2 = ship("wolf_Assault", "ISS Sold Two");
            Pet seller2 = pet("bugdog", "Sellertwo", sold2);
            transfer(market, Submarkets.SUBMARKET_OPEN, sold2);
            c.expect(seller2.isDead() && seller2.deathData != null && seller2.deathData.cause == PetStatusManager.PetDeathCause.SOLD, "sale without a free ship: the pet dies as SOLD (" + where(seller2) + ")");
            c.frames(20);
        });
        steps.add(c -> {
            c.shot("petsmove-sold");
            MarketAPI market = c.market("a");
            FleetMemberAPI spare = ship("wolf_Assault", "ISS Spare Two");
            FleetMemberAPI stored = ship("wolf_Assault", "ISS Stored");
            Pet storer = pet("slaghound", "Storer", stored);
            transfer(market, Submarkets.SUBMARKET_STORAGE, stored);
            c.expect(!storer.isDead() && storer.assignedFleetMember == spare, "storage: the pet moved to the free ship (" + where(storer) + ")");

            FleetMemberAPI stored2 = ship("wolf_Assault", "ISS Stored Two");
            Pet storer2 = pet("slaghound", "Storertwo", stored2);
            transfer(market, Submarkets.SUBMARKET_STORAGE, stored2);
            c.expect(!storer2.isDead() && storer2.assignedFleetMember == stored2, "storage without a free ship: the pet stays aboard (" + where(storer2) + ")");
            // the forgotten pet warning is posted on the first unpaused frame
            c.days(0.3f);
        });
        steps.add(c -> {
            c.shot("petsmove-forgotten-warning");
            PetCommand command = new PetCommand();
            BaseCommand.CommandResult give = command.runCommand("give doge Rex", BaseCommand.CommandContext.CAMPAIGN_MAP);
            Pet rex = byName("Rex");
            c.expect(give == BaseCommand.CommandResult.SUCCESS && rex != null && rex.isInMenagerie(), "indevo_pet give with every ship taken: Rex goes to the menagerie (" + give + ", " + where(rex) + ")");
            c.expect(rex != null && manager().getMenageriePets().contains(rex), "the menagerie list holds Rex");
            c.expect(command.runCommand("list", BaseCommand.CommandContext.CAMPAIGN_MAP) == BaseCommand.CommandResult.SUCCESS, "indevo_pet list runs");
            float age = rex == null ? 0f : rex.age;
            c.expect(command.runCommand("age Rex 30", BaseCommand.CommandContext.CAMPAIGN_MAP) == BaseCommand.CommandResult.SUCCESS && rex != null, "indevo_pet age Rex 30 runs");
            if (rex != null) c.near(rex.age, age + 30f, 0.01f, "indevo_pet age adds 30 days");
            c.expect(command.runCommand("give nosuchpet", BaseCommand.CommandContext.CAMPAIGN_MAP) == BaseCommand.CommandResult.BAD_SYNTAX, "indevo_pet give with an unknown species says so");

            FleetMemberAPI free = ship("wolf_Assault", "ISS Free");
            command.runCommand("give doge Rextwo", BaseCommand.CommandContext.CAMPAIGN_MAP);
            Pet rex2 = byName("Rextwo");
            c.expect(rex2 != null && rex2.assignedFleetMember == free && !rex2.isUnsettled(), "indevo_pet give with a free ship: on that ship, Content (" + where(rex2) + ")");

            fleet().getCargo().addCommodity("food", 1000f);
            c.days(35f);
        });
        steps.add(c -> {
            Pet rex = byName("Rex");
            c.expect(!rex.isDead() && !rex.isStarving() && rex.isInMenagerie(), "menagerie pet with food in the fleet cargo is fed at month end (" + where(rex) + ")");
            // the check runner keeps 100+ Supplies in the fleet, so Rex can only fall back to Supplies
            CargoAPI cargo = fleet().getCargo();
            for (String f : rex.getData().foodCommodities) if (!"supplies".equals(f)) cargo.removeCommodity(f, cargo.getCommodityQuantity(f));
            c.days(35f);
        });
        steps.add(c -> {
            Pet rex = byName("Rex");
            c.expect(!rex.isStarving() && rex.getMood() == PetMood.UNHAPPY_WITH_FOOD, "menagerie pet with only Supplies in the fleet cargo eats them at month end (" + rex.getMood() + ")");
            c.expect(rex.assignedFleetMember == null, "menagerie pet has no ship and no hullmod");
            fleet().getCargo().addCommodity("food", 1000f);
            fleet().getCargo().addCommodity("ore", 1000f);

            PetShop shopB = shop(c.market("b"));
            CargoAPI storageB = c.market("b").getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
            storageB.addCommodity("ore", 100f);
            FleetMemberAPI rampA = ship("wolf_Assault", "ISS Ramp A");
            Pet shopped = pet("slaghound", "Shopped", rampA);
            shopped.place(null, shopB);
            c.put("shoppedStorage", food(storageB, shopped));

            FleetMemberAPI rampB = ship("wolf_Assault", "ISS Ramp B");
            Pet ramp = pet("slaghound", "Ramp", rampB);
            c.days(10f);
        });
        steps.add(c -> {
            Pet shopped = byName("Shopped");
            Pet ramp = byName("Ramp");
            CargoAPI storageB = c.market("b").getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
            c.expect(!shopped.isDead() && !shopped.isStarving() && shopped.storage != null, "stored pet is fine after month end");
            c.near(food(storageB, shopped), (Float) c.get("shoppedStorage"), 0.01f, "stored pet took no food from its market's storage");

            float f1 = ramp.getEffectFract();
            c.expect(f1 > 0f, "effect ramp grows on its ship (" + f1 + ")");
            c.put("rampF1", f1);
            c.put("rampShip", ramp.assignedFleetMember.getId());
            ramp.place(null, shop(c.market("b")));
            c.days(10f);
        });
        steps.add(c -> {
            Pet ramp = byName("Ramp");
            float f1 = (Float) c.get("rampF1");
            c.near(ramp.getEffectFract(), f1, 0.0001f, "effect ramp does not grow in storage");
            FleetMemberAPI home = null;
            for (FleetMemberAPI m : fleet().getFleetData().getMembersListCopy()) if (m.getId().equals(c.get("rampShip"))) home = m;
            ramp.place(home, null);
            c.near(ramp.getEffectFract(), f1, 0.0001f, "storage and back to the same ship keeps the effect fraction");
            c.expect(!ramp.isUnsettled(), "storage and back to the same ship is no move");
            ramp.place(null, null);
            c.expect(ramp.isInMenagerie(), "place with no ship and no storage puts the pet in the menagerie");
            c.days(10f);
        });
        steps.add(c -> {
            Pet ramp = byName("Ramp");
            float f1 = (Float) c.get("rampF1");
            c.near(ramp.getEffectFract(), f1, 0.0001f, "effect ramp does not grow in the menagerie");
            FleetMemberAPI other = ship("wolf_Assault", "ISS Ramp C");
            ramp.place(other, null);
            c.near(ramp.getEffectFract(), 0f, 0.0001f, "arriving on another ship sets the effect fraction to 0");
            c.expect(ramp.isUnsettled(), "arriving on another ship is a move");
            c.put("rexId", byName("Rex").id);

            // the parked ships come back before the save, a parking fleet is not in any location
            CampaignFleetAPI fleet = fleet();
            for (FleetMemberAPI m : parked.getFleetData().getMembersListCopy()) {
                parked.getFleetData().removeFleetMember(m);
                fleet.getFleetData().addFleetMember(m);
            }
            if (oldFlagship != null) fleet.getFleetData().setFlagship(oldFlagship);
            c.reload();
        });
        steps.add(c -> {
            Pet rex = manager().get((String) c.get("rexId"));
            c.expect(rex != null && !rex.isDead() && rex.isInMenagerie() && manager().getMenageriePets().contains(rex), "menagerie pet survives save and load (" + where(rex) + ")");
            c.keepDialog(true);
            PetsDialogPlugin.show(shop(c.market("a")), new PetsView());
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
