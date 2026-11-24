package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 3.1 and 3.2: mutations and flaws from the console, on the ship and in the pet's numbers
public final class PetsGeneticsChecks {
    private static final Logger log = Global.getLogger(PetsGeneticsChecks.class);

    private PetsGeneticsChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static FleetMemberAPI ship(String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    // a doge on its own ship with a clean sheet and these traits
    private static Pet pet(Ctx c, String name, Object... idLevel) {
        Pet pet = new Pet("doge", name);
        manager().register(pet);
        pet.place(ship("ISS " + name), null);
        for (String id : new ArrayList<>(pet.sheet.traits.keySet())) pet.setTrait(id, 0);
        for (int i = 0; i < idLevel.length; i += 2) cmd("trait " + pet.id + " " + idLevel[i] + " " + idLevel[i + 1]);
        c.put(name, pet.id);
        return pet;
    }

    private static MutableShipStatsAPI stats(Pet pet) {
        pet.assignedFleetMember.updateStats();
        return pet.assignedFleetMember.getStats();
    }

    private static float mascot(Pet pet) {
        MutableStat.StatMod mod = stats(pet).getCRLossPerSecondPercent().getPercentBonus(pet.getData().hullmod);
        return mod == null ? 0f : mod.value;
    }

    private static float pct(MutableStat s, String id) {
        MutableStat.StatMod m = s.getPercentStatMod(id);
        return m == null ? 0f : m.value;
    }

    private static float mult(MutableStat s, String id) {
        MutableStat.StatMod m = s.getMultStatMod(id);
        return m == null ? 1f : m.value;
    }

    private static float pctB(StatBonus s, String id) {
        MutableStat.StatMod m = s.getPercentBonus(id);
        return m == null ? 0f : m.value;
    }

    private static float multB(StatBonus s, String id) {
        MutableStat.StatMod m = s.getMultBonus(id);
        return m == null ? 1f : m.value;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            if (!c.market("a").hasIndustry(Ids.PET_STORE)) c.market("a").addIndustry(Ids.PET_STORE);
            ((PetShop) c.market("a").getIndustry(Ids.PET_STORE)).finishBuildingOrUpgrading();
            Global.getSector().getPlayerFleet().getCargo().addCommodity("food", 500f);

            int mutations = 0, flaws = 0;
            for (PetDataRepo.Trait t : PetDataRepo.getTraits().values()) {
                if (t.isMutation()) mutations++;
                if (t.isFlaw()) flaws++;
            }
            c.expect(PetDataRepo.getTraits().size() == 51 && mutations == 8 && flaws == 12, "pet_traits.csv loads 51 traits, 8 mutations, 12 flaws (" + PetDataRepo.getTraits().size() + ", " + mutations + ", " + flaws + ")");

            // ship flaws at 0 hearts, under <hullmod>_flaw
            Pet flaw = pet(c, "Flaw", "destructive", 1, "vicious", 1, "unnerving", 1, "loud", 1, "jinx", 1);
            String id = flaw.getData().hullmod + "_flaw";
            MutableShipStatsAPI s = stats(flaw);
            c.expect(flaw.getHearts() == 0, "the flaw pet has 0 hearts");
            c.near(mult(s.getBaseCRRecoveryRatePercentPerDay(), id), 0.75f, 0.0001f, "Destructive 1: CR recovery x0.75");
            c.near(mult(s.getRepairRatePercentPerDay(), id), 0.75f, 0.0001f, "Destructive 1: repairs x0.75");
            c.near(pct(s.getCrewLossMult(), id), 25f, 0.01f, "Vicious 1: crew losses +25%");
            c.near(multB(s.getPeakCRDuration(), id), 0.9f, 0.0001f, "Unnerving 1: peak time x0.9");
            c.near(pct(s.getSensorProfile(), id), 25f, 0.01f, "Loud 1: sensor profile +25%");
            c.near(multB(s.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD), id), 0.75f, 0.0001f, "Jinx 1: recovery chance x0.75");
            c.near(multB(s.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD), id), 1.25f, 0.0001f, "Jinx 1: d-mod chance x1.25");
            c.expect(flaw.sheet.isKnown(PetDataRepo.TRAIT_PREFIX + "destructive"), "a flaw set by console counts as read");
            MutableStat.StatMod named = s.getSensorProfile().getPercentStatMod(id);
            c.note("flaw modifier source: " + (named == null ? "none" : named.desc));

            // Glutton: food x3 at level 1, its ship +15% supplies
            Pet glut = pet(c, "Glut", "glutton", 1);
            c.near(glut.getFoodPerMonth(), glut.getData().foodPerMonth * 3f, 0.001f, "Glutton 1: food per meal x3 (" + glut.getFoodPerMonth() + ")");
            c.near(pct(stats(glut).getSuppliesPerMonth(), glut.getData().hullmod + "_flaw"), 15f, 0.01f, "Glutton 1: its ship +15% supplies per month");

            // lifetime
            Pet longp = pet(c, "Long", "long_lived", 1);
            c.near(longp.getLifetime(), longp.lifetime * 1.25f, 0.5f, "Long-lived 1: lifespan x1.25 (" + longp.getLifetime() + " of " + longp.lifetime + ")");
            Pet shortp = pet(c, "Short", "short_lived", 1);
            c.near(shortp.getLifetime(), shortp.lifetime / 2f, 0.5f, "Short lived 1: lifespan halved");
            cmd("trait " + shortp.id + " short_lived 2");
            c.near(shortp.getLifetime(), shortp.lifetime / 3f, 0.5f, "Short lived 2: lifespan a third");

            // mascot: Giant and Runt at 1 heart
            Pet giant = pet(c, "Giant", "giant", 1);
            cmd("hearts " + giant.id + " 1");
            c.near(mascot(giant), -10f, 0.01f, "Giant 1: mascot effect x2, -10%");
            Pet runt = pet(c, "Runt", "runt", 1);
            cmd("hearts " + runt.id + " 1");
            c.near(mascot(runt), -2.5f, 0.01f, "Runt 1: mascot effect halved, -2.5%");
            cmd("trait " + runt.id + " runt 2");
            c.near(mascot(runt), -5f / 3f, 0.01f, "Runt 2: mascot effect a third");

            // talents: Savant, Split mind, Runt
            Pet sav = pet(c, "Sav", "capacitor", 3, "savant", 1);
            cmd("hearts " + sav.id + " 1");
            String h = sav.getData().hullmod;
            c.near(pct(stats(sav).getFluxCapacity(), h), 18.75f, 0.01f, "Savant 1: talents x1.25, Capacitor 3 grades +18.75%");
            Pet split = pet(c, "Split", "capacitor", 3, "split_mind", 1);
            cmd("hearts " + split.id + " 1");
            c.near(pct(stats(split).getFluxCapacity(), h), 30f, 0.01f, "Split mind 1: the best talent counts again, +30%");
            Pet rt = pet(c, "RuntTal", "capacitor", 3, "runt", 1);
            cmd("hearts " + rt.id + " 1");
            c.near(pct(stats(rt).getFluxCapacity(), h), 7.5f, 0.01f, "Runt 1: talents halved, +7.5%");

            // Sullen: hearts take twice as long
            Pet sul = pet(c, "Sullen", "sullen", 1);
            c.near(sul.getDaysToHeart(), 28f, 0.01f, "Sullen 1: first heart in 28 days aboard");
            float before = sul.bondDays;
            sul.addBond(30f);
            c.near(sul.bondDays - before, 15f, 0.01f, "Sullen 1: +30 bond days count half");

            // no effect yet
            Pet none = pet(c, "Inert", "sterile", 1, "slow_grower", 1, "mayfly", 1, "luminous", 1);
            c.near(none.getLifetime(), none.lifetime, 0.5f, "Sterile, Slow grower, Mayfly and Luminous change no lifespan");
            c.near(none.getFoodPerMonth(), none.getData().foodPerMonth, 0.001f, "and no food");
            c.near(pct(stats(none).getSensorProfile(), h + "_flaw"), 0f, 0.001f, "and no ship stat");

            // the held state: a mutation and a flaw on one card, a plain bought pet beside it
            pet(c, "Mixed", "capacitor", 2, "giant", 1, "loud", 1);
            Pet bought = new Pet("doge", "Bought");
            manager().register(bought);
            bought.place(ship("ISS Bought"), null);
            c.note("Bought sheet " + bought.sheet.traits + ", known " + bought.sheet.known);
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
