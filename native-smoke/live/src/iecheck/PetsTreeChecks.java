package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 3.6: a held Animal Center with a three generation family, two mates, gone pets, a cousin pair and a second family, for the Family tree tab
public final class PetsTreeChecks {
    private static final Logger log = Global.getLogger(PetsTreeChecks.class);

    private PetsTreeChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    // a doge, on a ship or in storage at the shop, with optional parents and read traits
    private static Pet pet(Ctx c, PetShop shop, String name, boolean ship, Pet a, Pet b, Object... idLevel) {
        Pet pet = new Pet("doge", name);
        manager().register(pet);
        if (ship) {
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
            member.setShipName("ISS " + name);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
            member.getRepairTracker().setCR(0.7f);
            pet.place(member, null);
        } else pet.place(null, shop);
        pet.age = 400f;
        if (a != null) pet.parentA = a.id;
        if (b != null) pet.parentB = b.id;
        for (String id : new ArrayList<>(pet.sheet.traits.keySet())) pet.setTrait(id, 0);
        for (int i = 0; i < idLevel.length; i += 2) cmd("trait " + pet.id + " " + idLevel[i] + " " + idLevel[i + 1]);
        c.put(name, pet.id);
        return pet;
    }

    private static void die(Pet pet) {
        manager().reportPetDied(pet, PetStatusManager.PetDeathCause.NATURAL);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            if (!c.market("a").hasIndustry(Ids.PET_STORE)) c.market("a").addIndustry(Ids.PET_STORE);
            PetShop shop = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            shop.finishBuildingOrUpgrading();
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(100000f);

            // generation 1: Gran x Gramps; Opa x Oma, Oma dead (has living kin, so she shows dimmed)
            Pet gran = pet(c, shop, "Gran", false, null, null, "capacitor", 2);
            Pet gramps = pet(c, shop, "Gramps", false, null, null, "runner", 1);
            Pet opa = pet(c, shop, "Opa", false, null, null, "plating", 1);
            Pet oma = pet(c, shop, "Oma", false, null, null, "ward", 1);
            // generation 2: Mom and Aunt from Gran x Gramps, Dad from Opa x Oma, Dad2 a founder, Uncle a founder
            Pet mom = pet(c, shop, "Mom", false, gran, gramps, "capacitor", 2);
            Pet aunt = pet(c, shop, "Aunt", false, gran, gramps, "runner", 1);
            Pet dad = pet(c, shop, "Dad", false, opa, oma, "plating", 1);
            Pet dad2 = pet(c, shop, "Dadtwo", false, null, null, "far_sight", 1);
            Pet uncle = pet(c, shop, "Uncle", false, null, null, "vent", 1);
            // a dead branch: Gone1 (Gran x Gramps) and its dead young Gone2, no living descendant
            Pet gone1 = pet(c, shop, "Goneone", false, gran, gramps);
            Pet gone2 = pet(c, shop, "Gonetwo", false, gone1, uncle);
            // generation 3: Mom x Dad (Kid1, Kid2), Mom x Dad2 (Kid3), Aunt x Uncle (Cousin)
            pet(c, shop, "Kidone", true, mom, dad, "capacitor", 3, "plating", 1);
            pet(c, shop, "Kidtwo", false, mom, dad, "capacitor", 1);
            pet(c, shop, "Kidthree", false, mom, dad2, "far_sight", 2);
            pet(c, shop, "Cousin", true, aunt, uncle, "capacitor", 2, "runner", 1);
            die(oma);
            die(gone1);
            die(gone2);
            // a second family and a single pet, for the full view
            Pet x1 = pet(c, shop, "Xavi", false, null, null, "giant", 1);
            Pet x2 = pet(c, shop, "Xena", false, null, null, "loud", 1);
            pet(c, shop, "Xylo", false, x1, x2);
            pet(c, shop, "Lone", false, null, null);
            c.note("Kidone x Cousin inbreeding " + PetShop.getInbreeding(manager().get((String) c.get("Kidone")), manager().get((String) c.get("Cousin"))));
            c.expect(oma.isDead() && gone1.isDead() && gone2.isDead(), "three gone pets");
            c.days(1f);
        });
        steps.add(c -> {
            PetShop shop = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            c.keepDialog(true);
            c.expect(PetsDialogPlugin.show(shop, new PetsView((String) c.get("Kidone"))), "the Pets panel of the player center opens on Kidone");
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
