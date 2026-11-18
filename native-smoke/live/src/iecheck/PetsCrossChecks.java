package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 3.4: a held Animal Center for the cross flow chart: a read pair with mutations and flaws, a related pair, an unread pair and a running brood
public final class PetsCrossChecks {
    private static final Logger log = Global.getLogger(PetsCrossChecks.class);

    private PetsCrossChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    private static Pet adult(Ctx c, String name, boolean clean, Object... idLevel) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName("ISS " + name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        Pet pet = new Pet("doge", name);
        manager().register(pet);
        pet.place(member, null);
        pet.age = 400f;
        if (clean) for (String id : new ArrayList<>(pet.sheet.traits.keySet())) pet.setTrait(id, 0);
        for (int i = 0; i < idLevel.length; i += 2) cmd("trait " + pet.id + " " + idLevel[i] + " " + idLevel[i + 1]);
        c.put(name, pet.id);
        return pet;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            if (!c.market("a").hasIndustry(Ids.PET_STORE)) c.market("a").addIndustry(Ids.PET_STORE);
            PetShop shop = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            shop.finishBuildingOrUpgrading();
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(100000f);

            // a read pair: talents, a mutation, flaws
            adult(c, "Alpha", true, "capacitor", 3, "plating", 1, "giant", 1, "loud", 1);
            adult(c, "Beta", true, "capacitor", 2, "far_sight", 2, "glutton", 1);
            // a related pair, for the Malformed chip
            Pet mom = adult(c, "Mom", true, "ward", 1);
            Pet dad = adult(c, "Dad", true, "vent", 1);
            Pet sis = adult(c, "Sis", true, "runner", 2);
            Pet bro = adult(c, "Bro", true, "runner", 1, "sullen", 1);
            sis.parentA = mom.id;
            sis.parentB = dad.id;
            bro.parentA = mom.id;
            bro.parentB = dad.id;
            c.note("Sis x Bro inbreeding " + PetShop.getInbreeding(sis, bro));
            // an unread pair: rolled sheets, nothing read
            Pet u1 = adult(c, "Unread1", false);
            Pet u2 = adult(c, "Unread2", false);
            c.note("Unread1 " + u1.sheet.traits + " known " + u1.sheet.known + ", Unread2 " + u2.sheet.traits + " known " + u2.sheet.known);
            // a running brood
            Pet r1 = adult(c, "Run1", true, "quick_trigger", 2, "savant", 1);
            Pet r2 = adult(c, "Run2", true, "quick_trigger", 1, "vicious", 1);
            shop.startBrood(r1, r2, 0);
            PetBrood brood = shop.getBrood(0);
            c.expect(brood != null && !brood.isReady(), "a running brood in habitat 1");
            // SWITCHES=ready: the brood is ready at the hold, for the New young popup
            c.days(System.getProperty("ie.switches", "").contains("ready") ? 61f : 1f);
        });
        steps.add(c -> {
            PetShop shop = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            String sw = System.getProperty("ie.switches", "");
            // SWITCHES=one: the ready brood keeps a single young, for the narrow popup
            PetBrood ready = shop.getBrood(0);
            if (sw.contains("one") && ready != null && ready.isReady()) while (ready.young.size() > 1) ready.young.remove(ready.young.size() - 1);
            // SWITCHES=long: parent names too long for the popup title, so it is cut
            if (sw.contains("long")) {
                manager().get((String) c.get("Run1")).name = "Run1 the Very Long Named Space Dog of the Outer Persean Sector Fringe";
                manager().get((String) c.get("Run2")).name = "Run2 the Equally Long Named Space Dog of the Inner Core Worlds Belt";
            }
            // with a ready brood, a second one runs beside it for the running card
            if (sw.contains("ready")) {
                Pet mom = manager().get((String) c.get("Mom"));
                Pet dad = manager().get((String) c.get("Dad"));
                shop.startBrood(mom, dad, 3);
            }
            c.keepDialog(true);
            c.expect(PetsDialogPlugin.show(shop, new PetsView()), "the Pets panel of the player center opens");
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
