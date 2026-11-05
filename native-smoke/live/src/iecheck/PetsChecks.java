package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;

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
        pet.assign(member);

        CargoAPI storage = Misc.getStorageCargo(market);
        storage.initMothballedShips(Global.getSector().getPlayerFaction().getId());
        fleet.getFleetData().removeFleetMember(member);
        storage.getMothballedShips().addFleetMember(member);
        return pet;
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
        return steps;
    }
}
