package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 2.3: talents from the sheet on the ship and the fleet, gated by hearts
public final class PetsTalentsChecks {
    private static final Logger log = Global.getLogger(PetsTalentsChecks.class);
    static final String P = PetDataRepo.TRAIT_PREFIX;

    private PetsTalentsChecks() {
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

    private static Pet pet(String species, String name, FleetMemberAPI on) {
        Pet pet = new Pet(species, name);
        manager().register(pet);
        pet.place(on, null);
        return pet;
    }

    private static Pet get(Ctx c, String key) {
        return manager().get((String) c.get(key));
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    // a clean sheet, then these traits in this order
    private static void traits(Pet pet, Object... idGrade) {
        for (String id : new ArrayList<>(pet.sheet.traits.keySet())) pet.setTrait(id, 0);
        for (int i = 0; i < idGrade.length; i += 2) cmd("trait " + pet.id + " " + idGrade[i] + " " + idGrade[i + 1]);
    }

    private static MutableShipStatsAPI stats(Pet pet) {
        pet.assignedFleetMember.updateStats();
        return pet.assignedFleetMember.getStats();
    }

    private static float pct(MutableStat stat, String id) {
        MutableStat.StatMod mod = stat.getPercentStatMod(id);
        return mod == null ? 0f : mod.value;
    }

    private static float pct(StatBonus stat, String id) {
        MutableStat.StatMod mod = stat.getPercentBonus(id);
        return mod == null ? 0f : mod.value;
    }

    private static float mult(MutableStat stat, String id) {
        MutableStat.StatMod mod = stat.getMultStatMod(id);
        return mod == null ? 1f : mod.value;
    }

    private static float flat(MutableStat stat, String id) {
        MutableStat.StatMod mod = stat.getFlatStatMod(id);
        return mod == null ? 0f : mod.value;
    }

    private static float flat(StatBonus stat, String id) {
        MutableStat.StatMod mod = stat.getFlatBonus(id);
        return mod == null ? 0f : mod.value;
    }

    private static float multB(StatBonus stat, String id) {
        MutableStat.StatMod mod = stat.getMultBonus(id);
        return mod == null ? 1f : mod.value;
    }

    private static String state(Pet p) {
        return p.name + " hearts " + p.getHearts() + " talents " + p.getTalents() + " on " + p.getActiveTalents() + " known " + p.sheet.known + " mult " + p.getTalentMult();
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            if (!c.market("a").hasIndustry(Ids.PET_STORE)) c.market("a").addIndustry(Ids.PET_STORE);
            ((PetShop) c.market("a").getIndustry(Ids.PET_STORE)).finishBuildingOrUpgrading();
            Global.getSector().getPlayerFleet().getCargo().addCommodity("food", 500f);

            // a new pet comes with a sheet: a disposition and 1 or 2 unread grade 1 talents
            Pet fresh = pet("doge", "Show", ship("ISS Show Home"));
            c.put("show", fresh.id);
            c.expect(fresh.sheet != null && PetGenetics.DISPOSITIONS.contains(fresh.sheet.disposition), "a new pet has a sheet with a disposition (" + (fresh.sheet == null ? "none" : fresh.sheet.disposition) + ")");
            int n = fresh.getTalents().size();
            boolean grade1 = fresh.getTalents().stream().allMatch(id -> fresh.sheet.getGrade(id) == 1);
            c.expect(n >= 1 && n <= 2 && grade1 && fresh.sheet.known.isEmpty(), "1 or 2 grade 1 talents, none read (" + fresh.sheet.traits + ", known " + fresh.sheet.known + ")");
            cmd("give doge Gift");
            Pet gift = manager().getPets().stream().filter(p -> "Gift".equals(p.name)).findFirst().orElse(null);
            c.expect(gift != null && gift.sheet != null && !gift.getTalents().isEmpty(), "a pet given by the console has a sheet (" + (gift == null ? "none" : gift.sheet.traits) + ")");

            // seven talents, best grade first
            Pet tal = pet("doge", "Tal", ship("ISS Tal Home"));
            c.put("tal", tal.id);
            traits(tal, "capacitor", 3, "plating", 2, "far_sight", 1, "ward", 1, "high_spirits", 1, "pest_hunter", 1, "lucky_charm", 1);
            c.expect(tal.getTalents().get(0).equals(P + "capacitor") && tal.getTalents().get(1).equals(P + "plating") && tal.getTalents().size() == 7, "talents sort best grade first (" + tal.getTalents() + ")");
            String h = tal.getData().hullmod;
            c.put("hullmod", h);
            MutableShipStatsAPI s = stats(tal);
            c.expect(pct(s.getFluxCapacity(), h) == 0f, "0 hearts: no talent on");
            cmd("hearts " + tal.id + " 1");
            s = stats(tal);
            c.near(pct(s.getFluxCapacity(), h), 15f, 0.01f, "1 heart: the best talent on, Capacitor 3 grades: flux capacity +15%");
            c.expect(pct(s.getArmorBonus(), h) == 0f, "1 heart: Plating still off");
            cmd("hearts " + tal.id + " 2");
            s = stats(tal);
            c.near(pct(s.getArmorBonus(), h), 10f, 0.01f, "2 hearts: Plating 2 grades: armor +10%");
            c.expect(pct(s.getBallisticWeaponRangeBonus(), h) == 0f, "2 hearts: Far sight still off");
            cmd("hearts " + tal.id + " 3");
            s = stats(tal);
            c.near(pct(s.getBallisticWeaponRangeBonus(), h), 5f, 0.01f, "3 hearts: Far sight: ballistic range +5%");
            c.near(pct(s.getEnergyWeaponRangeBonus(), h), 5f, 0.01f, "3 hearts: Far sight: energy range +5%");
            c.expect(mult(s.getShieldDamageTakenMult(), h) == 1f, "3 hearts: Ward still off");
            c.expect(tal.sheet.known.size() == 7, "3 hearts: every talent is read (" + tal.sheet.known.size() + ")");
            c.note(state(tal));
            cmd("hearts " + tal.id + " 4");
            s = stats(tal);
            c.expect(tal.hasNest(), "4 hearts on its ship: nest x1.3");
            c.near(pct(s.getFluxCapacity(), h), 19.5f, 0.01f, "4 hearts with a nest: Capacitor +19.5%");
            c.near(mult(s.getShieldDamageTakenMult(), h), 1f - 0.065f, 0.0001f, "4 hearts: Ward on as a multiplier: shield damage taken x0.935");
            c.expect(flat(s.getMaxCombatReadiness(), h) == 0f, "4 hearts: High spirits still off");
            cmd("hearts " + tal.id + " 5");
            s = stats(tal);
            c.near(flat(s.getMaxCombatReadiness(), h), 0.039f, 0.0001f, "5 hearts: High spirits +3.9 CR points (3 a grade, nest)");
            c.near(mult(s.getSuppliesPerMonth(), h), 0.935f, 0.0001f, "5 hearts: Pest hunter: supplies per month x0.935");
            c.near(flat(s.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD), h), 0.065f, 0.0001f, "5 hearts: Lucky charm: recovery +6.5%");
            c.near(multB(s.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD), h), 0.935f, 0.0001f, "5 hearts: Lucky charm: d-mod chance x0.935");
            c.expect(tal.getActiveTalents().size() == 7, "5 hearts: all 7 talents on");
            String src = tal.name + " the " + tal.getData().species;
            MutableStat.StatMod capMod = s.getFluxCapacity().getPercentStatMod(h);
            c.expect(capMod != null && src.equals(capMod.desc), "stat modifiers are listed under the pet's name (" + (capMod == null ? "none" : capMod.desc) + ")");
            c.put("fluxBase", s.getFluxCapacity().getBaseValue());
            c.note("Tal ship flux capacity " + s.getFluxCapacity().getBaseValue() + " -> " + s.getFluxCapacity().getModifiedValue() + ", armor " + s.getArmorBonus().computeEffective(150f) + " of 150, max CR " + s.getMaxCombatReadiness().getModifiedValue());

            // Unsettled turns talents off
            Pet mov = pet("doge", "Mov", ship("ISS Mov Old"));
            c.put("mov", mov.id);
            traits(mov, "capacitor", 2);
            cmd("hearts " + mov.id + " 2");
            c.near(pct(stats(mov).getFluxCapacity(), h), 10f, 0.01f, "Mov settled: Capacitor 2 grades +10%");
            mov.place(ship("ISS Mov New"), null);
            c.expect(mov.isUnsettled() && pct(stats(mov).getFluxCapacity(), h) == 0f && mov.getTalentMult() == 0f, "Unsettled: talents off");

            // a hamster doubles talents; reduce rows stop at 90%
            Pet ham = pet("hampter", "Ham", ship("ISS Ham Home"));
            c.put("ham", ham.id);
            traits(ham, "ward", 10, "capacitor", 1);
            cmd("hearts " + ham.id + " 1");
            s = stats(ham);
            c.near(mult(s.getShieldDamageTakenMult(), h), 0.1f, 0.0001f, "hamster Ward 10 grades: 5 x 10 x 2 = 100%, capped at 90%: shield damage taken x0.1");
            c.expect(pct(s.getFluxCapacity(), h) == 0f, "hamster at 1 heart: only its best talent on");
            cmd("hearts " + ham.id + " 2");
            c.near(pct(stats(ham).getFluxCapacity(), h), 10f, 0.01f, "hamster Capacitor 1 grade: +10% (doubled)");
            c.days(0.3f);
        });
        steps.add(c -> {
            // fleet talents: only the best pet in the fleet counts
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            String h = (String) c.get("hullmod");
            float salvage0 = Misc.getFleetwideTotalMod(fleet, Stats.SALVAGE_VALUE_MULT_MOD, 0f);
            float ground0 = Misc.getFleetwideTotalMod(fleet, Stats.FLEET_GROUND_SUPPORT, 0f);
            float burn0 = Misc.getFleetwideTotalMod(fleet, Stats.FLEET_BURN_BONUS, 0f);
            float burnLevel0 = fleet.getFleetData().getBurnLevel();
            Pet low = pet("doge", "Low", ship("ISS Low Home"));
            Pet top = pet("doge", "Top", ship("ISS Top Home"));
            c.put("low", low.id);
            c.put("top", top.id);
            traits(low, "scavenger", 2, "marine_mascot", 1, "pathfinder", 4);
            traits(top, "scavenger", 3, "marine_mascot", 2, "pathfinder", 8);
            cmd("hearts " + low.id + " 5");
            cmd("hearts " + top.id + " 5");
            stats(low);
            stats(top);
            fleet.getFleetData().setSyncNeeded();
            fleet.getFleetData().syncIfNeeded();
            c.expect(manager().getLead(fleet, P + "scavenger") == top, "the higher Scavenger leads");
            float salvage = Misc.getFleetwideTotalMod(fleet, Stats.SALVAGE_VALUE_MULT_MOD, 0f) - salvage0;
            float ground = Misc.getFleetwideTotalMod(fleet, Stats.FLEET_GROUND_SUPPORT, 0f) - ground0;
            float burn = Misc.getFleetwideTotalMod(fleet, Stats.FLEET_BURN_BONUS, 0f) - burn0;
            c.near(salvage, 0.195f, 0.0001f, "two Scavengers: only the higher counts, salvage +19.5% (3 grades, nest)");
            c.near(ground, 130f, 0.01f, "two Marine mascots: only the higher counts, ground support +130");
            c.near(burn, 2.6f, 0.01f, "two Pathfinders: only the higher counts, fleet burn +2.6 (8 grades = 2 steps, nest)");
            c.expect(flat(stats(low).getDynamic().getMod(Stats.SALVAGE_VALUE_MULT_MOD), h) == 0f, "the lower pet's ship applies no Scavenger");
            c.note("fleet burn level " + burnLevel0 + " -> " + fleet.getFleetData().getBurnLevel() + ", fleetwide burn mod " + burn0 + " -> " + (burn0 + burn));

            // the lead moves on when the top pet leaves
            top.place(null, (PetShop) c.market("a").getIndustry(Ids.PET_STORE));
            fleet.getFleetData().setSyncNeeded();
            fleet.getFleetData().syncIfNeeded();
            stats(low);
            c.near(Misc.getFleetwideTotalMod(fleet, Stats.SALVAGE_VALUE_MULT_MOD, 0f) - salvage0, 0.13f, 0.0001f, "with the top pet stored the lower Scavenger counts: +13%");
            top.place(fleet.getFleetData().getMembersListCopy().stream().filter(m -> "ISS Top Home".equals(m.getShipName())).findFirst().get(), null);
            for (String k : new String[]{"show", "tal", "mov", "ham", "low", "top"}) c.note(state(get(c, k)));
            c.days(0.3f);
        });
        steps.add(c -> {
            // the talent message: Show reaches 1 heart in a running game
            Pet show = get(c, "show");
            cmd("hearts " + show.id + " 1");
            c.expect(show.sheet.known.size() == 1, "1 heart reads the one talent it switches on (" + show.sheet.known + ")");
            c.days(0.3f);
            c.shot("petstalents-message");
        });
        steps.add(c -> {
            Pet tal = get(c, "tal");
            cmd("hearts " + tal.id + " 3");
            c.note(state(tal));
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
