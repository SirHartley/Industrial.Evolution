package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.dialogue.research.ResearchProjectTemplateRepo;
import indevo.dialogue.research.dialogue.ResearchProject;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetLongevity;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.listener.PetStatusManager.PetDeathCause;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.plugins.PetMemorial;
import indevo.industries.petshop.rules.IndEvo_PetTreatment;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// pet 5.1: the Memorial's Remembered section, the Longevity Syringe and Project Methuselah, the old age warnings, the Nomios treatment
public final class PetsLongevityChecks {
    private static final Logger log = Global.getLogger(PetsLongevityChecks.class);

    private PetsLongevityChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static boolean sw(String s) {
        return System.getProperty("ie.switches", "").contains(s);
    }

    private static Pet pet(Ctx c, String type, String name, float age) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName("ISS " + name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        Pet pet = new Pet(type, name);
        manager().register(pet);
        pet.place(member, null);
        pet.age = age;
        c.put(name, pet.id);
        return pet;
    }

    private static Pet get(Ctx c, String name) {
        return manager().get((String) c.get(name));
    }

    private static List<String> names(List<Pet> pets) {
        List<String> list = new ArrayList<>();
        for (Pet p : pets) list.add(p.name);
        return list;
    }

    private static boolean treatment(String verb) {
        Map<String, MemoryAPI> memory = new HashMap<>();
        return new IndEvo_PetTreatment().execute(null, null, Misc.tokenize(verb), memory);
    }

    private static int syringesIn(SectorEntityToken entity) {
        CargoAPI cargo = BaseSalvageSpecial.getCombinedExtraSalvage(entity);
        return cargo == null ? 0 : Math.round(cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.LONGEVITY_SYRINGE, null)));
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            if (!a.hasIndustry(Ids.PET_STORE)) a.addIndustry(Ids.PET_STORE);
            ((PetShop) a.getIndustry(Ids.PET_STORE)).finishBuildingOrUpgrading();

            // syringes placed once per game, Project Methuselah, Nomios
            c.expect(Global.getSector().getMemoryWithoutUpdate().getBoolean("$IndEvo_longevitySyringesPlaced"), "the syringes are placed once per game");
            int placed = 0, centers = 0, labs = 0;
            for (SectorEntityToken t : Global.getSector().getEntitiesWithTag(Tags.SALVAGEABLE)) {
                int n = syringesIn(t);
                if (n == 0) continue;
                placed += n;
                if (Ids.ABANDONED_PETSHOP_ENTITY.equals(t.getCustomEntityType())) centers++;
                if (Ids.LAB_ENTITY.equals(t.getCustomEntityType())) labs++;
            }
            c.note("syringes placed " + placed + " (pet centers " + centers + ", labs " + labs + ")");
            c.expect(placed == PetConstants.PLACED_SYRINGES, "3 syringes wait as extra salvage");
            int before = placed;
            PetLongevity.placeSyringes();
            int after = 0;
            for (SectorEntityToken t : Global.getSector().getEntitiesWithTag(Tags.SALVAGEABLE)) after += syringesIn(t);
            c.expect(after == before, "a second placeSyringes adds none");

            ResearchProject methuselah = ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(Ids.PROJ_LONGEVITY);
            c.expect(methuselah != null && "Project Methuselah".equals(methuselah.getName()) && methuselah.display(), "Project Methuselah shows at the Academy");
            CargoAPI reward = methuselah.getRewards();
            c.expect(reward.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.LONGEVITY_SYRINGE, null)) == 1f, "Project Methuselah gives one syringe");
            c.note("Methuselah points " + methuselah.getRequiredPoints() + ", repeatable " + methuselah.isRepeatable());

            c.expect(PetLongevity.getNomios() != null, "Nomios has its cryosanctum");
            c.expect(treatment("available"), "the Nomios option is available with pets on");

            // life left wording
            c.note("life left 10/30/120/730 days: " + PetLongevity.lifeLeft(10f) + " / " + PetLongevity.lifeLeft(30f) + " / " + PetLongevity.lifeLeft(120f) + " / " + PetLongevity.lifeLeft(730f));
            c.expect(PetLongevity.lifeLeft(10f).equals("A few days") && PetLongevity.lifeLeft(30f).equals("A few weeks") && PetLongevity.lifeLeft(120f).equals("About 4 months") && PetLongevity.lifeLeft(730f).equals("About 2 cycles"), "life left is never a day count");

            // old age warnings at 60 and 14 days left
            Pet rex = pet(c, "doge", "Rex", 400f);
            pet(c, "hampter", "Hammy", 1f);
            rex.age = rex.getLifetime() - 62f;
            c.days(3f);
        });
        steps.add(c -> {
            Pet rex = get(c, "Rex");
            c.note("Rex left " + (rex.getLifetime() - rex.age) + " days, warnings " + rex.lifeWarnings);
            c.expect(rex.lifeWarnings == 1, "the first warning at 60 days left");
            // a 31 day hamster passes the 60 day warning silently (shot warn60 has no Hammy line)
            Pet hammy = get(c, "Hammy");
            c.note("Hammy left " + (hammy.getLifetime() - hammy.age) + " of " + hammy.getLifetime() + " days, warnings " + hammy.lifeWarnings);
            c.days(1f);
        });
        steps.add(c -> {
            c.expect(get(c, "Rex").lifeWarnings == 1, "it comes once");
            c.shot("warn60");
        });
        steps.add(c -> {
            Pet rex = get(c, "Rex");
            rex.age = rex.getLifetime() - 16f;
            c.days(3f);
        });
        steps.add(c -> {
            c.expect(get(c, "Rex").lifeWarnings == 2, "the second warning at 14 days left");
            c.shot("warn14");
        });
        steps.add(c -> {
            Pet rex = get(c, "Rex"), hammy = get(c, "Hammy");
            // cycles per syringe: the setting, 1 for a hamster
            c.expect(rex.getSyringeCycles() == 2 && hammy.getSyringeCycles() == 1, "a syringe gives 2 cycles, 1 to a hamster");
            try {
                Colony.setting(Setting.PET_LONGEVITY_CYCLES, 3);
                c.expect(rex.getSyringeCycles() == 3 && hammy.getSyringeCycles() == 1, "the setting changes the cycles, not the hamster's");
                Colony.setting(Setting.PET_LONGEVITY_CYCLES, 2);
            } catch (Exception e) {
                c.expect(false, "setting: " + e);
            }

            float life = rex.getLifetime();
            rex.treat(rex.getSyringeCycles());
            c.near(rex.getLifetime() - life, 730f, 0.5f, "a treatment adds 730 days");
            c.expect(rex.treatments == 1 && rex.lifeWarnings == 0, "a treatment counts and resets the warnings");
            c.put("rexLife", rex.getLifetime());

            // a treated pet dies of old age
            Pet old = pet(c, "gravcat", "Grandpa", 400f);
            old.treat(1);
            old.age = old.getLifetime() - 0.5f;
            c.days(2f);
        });
        steps.add(c -> {
            Pet old = get(c, "Grandpa");
            c.expect(old.isDead() && old.deathData.cause == PetDeathCause.NATURAL, "a treated pet dies when its treatments run out");
            c.shot("treated-death");
        });
        // dead pets for the Memorial, a day apart; sold and rehomed are not dead
        String[][] dead = {{"spacerat", "Squeak", "COMBAT"}, {"doge", "Biscuit", "STARVED"}, {"gravcat", "Mittens", "SOLD"}, {"doge", "Pip", "EUTHANIZED"}, {"spacerat", "Nib", "REHOMED"}, {"gravcat", "Tom", "UNKNOWN"}};
        for (String[] d : dead) steps.add(c -> {
            manager().reportPetDied(pet(c, d[0], d[1], 300f), PetDeathCause.valueOf(d[2]));
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            if (sw("many")) for (int i = 1; i <= 14; i++) {
                manager().reportPetDied(pet(c, i % 2 == 0 ? "doge" : "gravcat", "Gone" + i, 200f + i * 30f), i % 3 == 0 ? PetDeathCause.COMBAT : PetDeathCause.NATURAL);
            }
            List<String> remembered = names(PetMemorial.remembered());
            c.put("remembered", String.join(",", remembered));
            c.note("remembered " + remembered);
            c.expect(remembered.size() == (sw("many") ? 19 : 5) && !remembered.contains("Mittens") && !remembered.contains("Nib"), "Remembered lists the dead, not the sold or rehomed");
            int tom = remembered.indexOf("Tom"), squeak = remembered.indexOf("Squeak"), grandpa = remembered.indexOf("Grandpa");
            c.expect(tom >= 0 && tom < squeak && squeak < grandpa, "newest first");
            boolean stamped = true;
            List<String> stamps = new ArrayList<>();
            for (Pet p : PetMemorial.remembered()) {
                stamped &= p.deathData.timestamp != 0L;
                stamps.add(p.name + " " + p.deathData.timestamp + " " + p.deathData.date);
            }
            c.note("death stamps " + stamps);
            // a death saved before the timestamp (0) comes last
            Pet sq = get(c, "Squeak");
            long kept = sq.deathData.timestamp;
            sq.deathData.timestamp = 0L;
            List<String> legacy = names(PetMemorial.remembered());
            c.note("with Squeak at timestamp 0: " + legacy);
            c.expect(legacy.get(legacy.size() - 1).equals("Squeak"), "a death from an older save comes last");
            sq.deathData.timestamp = kept;
            c.expect(stamped, "every death has a timestamp");

            // the Memorial on the colony
            if (!a.hasIndustry(Ids.MEMORIAL)) a.addIndustry(Ids.MEMORIAL);
            a.getIndustry(Ids.MEMORIAL).finishBuildingOrUpgrading();
            c.expect(a.getIndustry(Ids.MEMORIAL).isFunctional() && a.getIndustry(Ids.MEMORIAL).isTooltipExpandable(), "the Memorial tooltip expands with F1");

            c.reload();
        });
        steps.add(c -> {
            Pet rex = get(c, "Rex");
            c.expect(rex != null && rex.treatments == 1 && rex.treatedDays == 730f && rex.lifeWarnings == 0, "after a save and load the treatment stays");
            c.near(rex.getLifetime(), (Float) c.get("rexLife"), 0.5f, "the lifetime with it");
            c.expect(String.join(",", names(PetMemorial.remembered())).equals(c.get("remembered")), "Remembered keeps its order after a load");
            c.expect(PetLongevity.withFleet().size() == 2, "Rex and Hammy are with the fleet");

            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            // the default hold: syringes in the cargo, the Memorial on the colony
            if (!sw("nosyr")) cargo.addSpecial(new SpecialItemData(ItemIds.LONGEVITY_SYRINGE, null), 3);
            c.note("syringes in cargo " + PetLongevity.syringeCount());

            if (sw("many")) for (int i = 1; i <= 6; i++) pet(c, i % 2 == 0 ? "lizzer" : "salvageraven", "Crew" + i, 300f + i * 200f);

            if (sw("research")) {
                ResearchUiChecks.setup(c);
                // Methuselah alone on the board: the others redeemed
                for (ResearchProject p : ResearchProjectTemplateRepo.RESEARCH_PROJECTS.values()) {
                    p.getProgress().points = 0f;
                    p.getProgress().redeemed = !Ids.PROJ_LONGEVITY.equals(p.getId());
                }
                cargo.addCommodity(ItemIds.RARE_PARTS, 60f);
                c.keepDialog(true);
                ResearchUiChecks.open(c, new ResearchUiChecks.Host(0f, Ids.PROJ_LONGEVITY));
                c.frames(60);
                return;
            }

            if (sw("nomios")) {
                MarketAPI nomios = PetLongevity.getNomios();
                SectorEntityToken e = nomios.getPrimaryEntity();
                Colony.jump(e.getContainingLocation(), e.getLocation().x + 150f, e.getLocation().y);
                // SWITCHES=poor: nothing to pay; else 3 cycles' worth
                if (!sw("poor")) {
                    cargo.addCommodity(Commodities.ORGANS, 3 * PetConstants.NOMIOS_ORGANS);
                    cargo.addCommodity(Commodities.DRUGS, 3 * PetConstants.NOMIOS_DRUGS);
                    cargo.getCredits().set(3 * PetConstants.NOMIOS_CREDITS + 5000f);
                } else {
                    cargo.removeCommodity(Commodities.ORGANS, cargo.getCommodityQuantity(Commodities.ORGANS));
                    cargo.removeCommodity(Commodities.DRUGS, cargo.getCommodityQuantity(Commodities.DRUGS));
                    cargo.getCredits().set(1000f);
                }
                // SWITCHES=nopet: no pet with the fleet, the option greys out
                if (sw("nopet")) for (Pet p : PetLongevity.withFleet()) manager().reportPetDied(p, PetDeathCause.EUTHANIZED);
                c.note("cargo: organs " + cargo.getCommodityQuantity(Commodities.ORGANS) + ", drugs " + cargo.getCommodityQuantity(Commodities.DRUGS) + ", credits " + cargo.getCredits().get());
                c.keepDialog(true);
                Global.getSector().getCampaignUI().showInteractionDialog(e);
                c.frames(60);
            }
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
