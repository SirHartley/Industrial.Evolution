package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.apache.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

// package 1.4: every pet state the Pets panel shows, then the colony left open for clicks into the panel
public final class PetsUiChecks {
    private static final Logger log = Global.getLogger(PetsUiChecks.class);

    private PetsUiChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static FleetMemberAPI ship(CampaignFleetAPI fleet, String variant, String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        member.setShipName(name);
        fleet.getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static Pet pet(String species, String name, FleetMemberAPI on) {
        Pet pet = new Pet(species, name);
        manager().register(pet);
        pet.place(on, null);
        return pet;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI market = c.market("a");
            if (!market.hasIndustry(Ids.PET_STORE)) market.addIndustry(Ids.PET_STORE);
            PetShop shop = (PetShop) market.getIndustry(Ids.PET_STORE);
            shop.finishBuildingOrUpgrading();

            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            CargoAPI cargo = fleet.getCargo();
            for (String ore : new String[]{"ore", "rare_ore", "metals", "rare_metals", "IndEvo_parts", "heavy_machinery"})
                cargo.removeCommodity(ore, cargo.getCommodityQuantity(ore));
            cargo.addCommodity("food", 500f);

            pet("doge", "Ace", ship(fleet, "wolf_Assault", "ISS Ace Home"));
            pet("slaghound", "Hungry", ship(fleet, "wolf_Assault", "ISS Hungry Home"));
            Pet starving = pet("doge", "Starving", ship(fleet, "wolf_Assault", "ISS Starving Home"));
            MethodHandles.privateLookupIn(Pet.class, MethodHandles.lookup()).findVarHandle(Pet.class, "isStarving", boolean.class).set(starving, true);
            pet("bugdog", "Shocky", ship(fleet, "wolf_Assault", "ISS Shocky Home")).shellShock();
            pet("doge", "Boxed", ship(fleet, "wolf_Assault", "ISS Boxed Home")).place(null, shop);
            pet("doge", "Roamer", null);
            pet("vapourray", "A very long pet name to test how the card copes", ship(fleet, "lasher_CS", "ISS Long Name Home"));
            for (int i = 1; i <= 10; i++) ship(fleet, "lasher_CS", "ISS Free " + i);

            CampaignFleetAPI away = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet Check Detachment", true);
            market.getContainingLocation().addEntity(away);
            away.setLocation(market.getPrimaryEntity().getLocation().x + 200f, market.getPrimaryEntity().getLocation().y);
            pet("doge", "Wanderer", ship(away, "wolf_Assault", "ISS Away Home"));
            away.getFleetData().setSyncNeeded();
            away.getFleetData().syncIfNeeded();

            if (System.getProperty("ie.switches", "").contains("nomotion")) Colony.setting(indevo.config.Setting.PET_MOTION, false);
            if (System.getProperty("ie.switches", "").contains("poor")) cargo.getCredits().set(500f);
            else cargo.getCredits().set(50000f);

            c.expect(manager().getPets().size() == 8, "eight test pets (" + manager().getPets().size() + ")");
            for (Pet p : manager().getPets()) if (p.name.equals("Hungry")) c.expect(!manager().hasFood(p), "the slag hound has no food aboard");
            c.expect(shop.isFunctional(), "the Animal Center is functional");
            c.put("colony", market.getPrimaryEntity().getId());

            Colony.jump(market.getContainingLocation(), market.getPrimaryEntity().getLocation().x + 100f, market.getPrimaryEntity().getLocation().y);
            c.frames(60);
        });
        steps.add(c -> {
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(c.market("a").getPrimaryEntity());
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
