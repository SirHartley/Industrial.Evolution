package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.listener.PetStatusManager.PetDeathCause;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetMemorialFile;
import indevo.industries.petshop.plugins.PetMemorial;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// pet 5.4: the Memorial's Remembered section with N dead pets, a starred death carried to other saves through IndEvo_pet_memorial.json
// SWITCHES: n<count> dead pets (default 10); other: no starred pet in this save, the carried pets of earlier runs show last; nomotion
public final class PetsMemorialChecks {
    private static final Logger log = Global.getLogger(PetsMemorialChecks.class);
    static final PetDeathCause[] CAUSES = {PetDeathCause.NATURAL, PetDeathCause.STARVED, PetDeathCause.EUTHANIZED, PetDeathCause.NATURAL, PetDeathCause.UNKNOWN};

    private PetsMemorialChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static String sw() {
        return System.getProperty("ie.switches", "");
    }

    private static int count() {
        Matcher m = Pattern.compile("n(\\d+)").matcher(sw());
        return m.find() ? Integer.parseInt(m.group(1)) : 10;
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

    private static List<String> names(List<PetMemorial.Gone> gone) {
        List<String> list = new ArrayList<>();
        for (PetMemorial.Gone g : gone) list.add(g.name() + (g.grey() ? " (other save)" : ""));
        return list;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        boolean other = sw().contains("other");
        int n = count();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            if (!a.hasIndustry(Ids.PET_STORE)) a.addIndustry(Ids.PET_STORE);
            ((PetShop) a.getIndustry(Ids.PET_STORE)).finishBuildingOrUpgrading();
            if (!a.hasIndustry(Ids.MEMORIAL)) a.addIndustry(Ids.MEMORIAL);
            a.getIndustry(Ids.MEMORIAL).finishBuildingOrUpgrading();
            if (sw().contains("nomotion")) {
                try {
                    Colony.setting(Setting.PET_MOTION, false);
                } catch (Exception e) {
                    c.expect(false, "motion setting: " + e);
                }
            }
            List<PetMemorialFile.Lost> before = PetMemorialFile.get().getPets();
            List<String> carried = new ArrayList<>();
            for (PetMemorialFile.Lost l : before) carried.add(l.name());
            c.put("carried", carried.size());
            c.note("memorial file before: " + carried);
            // SWITCHES=shift: move the sector UID counter, so this save's pets do not reuse the carried pets' ids (same base save)
            if (sw().contains("shift")) for (int i = 0; i < 500; i++) Global.getSector().genUID();
            // one living pet so the Pets tab has both lists
            pet(c, "doge", "Living", 300f);
            // SWITCHES=alive: a living pet that is the first carried pet (its memorial id), as in an older save of the game it died in
            if (sw().contains("alive") && !PetMemorialFile.get().getPets().isEmpty()) {
                Pet twin = pet(c, "lizzer", "Twin", 300f);
                String uid = PetMemorialFile.get().getPets().get(0).id();
                try {
                    java.lang.invoke.MethodHandles.privateLookupIn(Pet.class, java.lang.invoke.MethodHandles.lookup()).findVarHandle(Pet.class, "memorialId", String.class).set(twin, uid);
                } catch (ReflectiveOperationException e) {
                    c.expect(false, "set memorialId: " + e);
                }
                c.put("twin", uid);
            }
        });
        // the dead, a day apart so the newest first order is clear: a starred old timer, a combat loss with its ship, then the rest
        List<PetData> species = new ArrayList<>();
        for (PetData d : PetDataRepo.getAll()) if (!d.isNoSell() && !"hampter".equals(d.id)) species.add(d);
        for (int i = 0; i < n; i++) {
            int k = i;
            steps.add(c -> {
                String name = k == 0 ? (other ? "Pip" : "Old Jack") : k == 1 ? "Tamsin" : "Gone" + k;
                Pet pet = pet(c, species.get(k % species.size()).id, name, 400f + 37f * k);
                if (k == 0 && other && sw().contains("star2")) pet.setFavourite(true);
                if (k == 0 && !other) {
                    pet.setFavourite(true);
                    pet.earn(PetTitle.OLD_TIMER);
                    pet.earn(PetTitle.NAVIGATOR);
                    pet.earn(PetTitle.VETERAN);
                    new indevo.console.PetCommand().runCommand("hearts " + pet.id + " 5", org.lazywizard.console.BaseCommand.CommandContext.CAMPAIGN_MAP);
                }
                if (k == 1) pet.earn(PetTitle.VETERAN);
                manager().reportPetDied(pet, k == 1 ? PetDeathCause.COMBAT : CAUSES[k % CAUSES.length]);
                c.days(n > 20 ? 0.2f : 1f);
            });
        }
        steps.add(c -> {
            List<PetMemorial.Gone> gone = PetMemorial.remembered();
            int carried = (Integer) c.get("carried");
            c.note("remembered " + gone.size() + ": " + names(gone));
            boolean ownFirst = true;
            for (int i = 0; i < gone.size(); i++) ownFirst &= gone.get(i).grey() == (i >= n);
            int twin = c.get("twin") == null ? 0 : 1;
            c.expect(gone.size() == n + carried - twin, "Remembered holds this save's " + n + " dead and the carried pets, a carried pet alive here not twice (" + gone.size() + ")");
            if (sw().contains("star2")) c.expect(PetMemorialFile.get().contains(manager().get((String) c.get("Pip")).getMemorialId()) && PetMemorialFile.get().getPets().size() == carried + 1, "a starred death in the second save is written too (" + PetMemorialFile.get().getPets().size() + " in the file)");
            if (twin == 1) for (PetMemorial.Gone g : gone) c.expect(!g.id().equals(c.get("twin")), "the pet alive in this save is not shown as carried");
            c.expect(ownFirst, "this save's dead come first, the carried pets last");
            if (!other) {
                PetMemorial.Gone jack = null;
                for (PetMemorial.Gone g : gone) if (g.name().equals("Old Jack")) jack = g;
                if (jack != null) c.note("Old Jack: star " + jack.star() + ", hearts " + jack.hearts() + ", titles " + jack.titles() + ", how " + jack.how());
                c.expect(jack != null && jack.star() && jack.hearts() == 5 && jack.titles().containsAll(List.of("Old Timer", "Navigator", "Veteran")), "the starred Old Jack keeps star, hearts and titles");
                c.expect(PetMemorialFile.get().contains(manager().get((String) c.get("Old Jack")).getMemorialId()), "the starred death is written to IndEvo_pet_memorial.json");
                c.expect(!PetMemorialFile.get().contains(manager().get((String) c.get("Tamsin")).getMemorialId()), "an unstarred death is not carried");
            }
            if (n > 1) {
                PetMemorial.Gone tamsin = null;
                for (PetMemorial.Gone g : gone) if (g.name().equals("Tamsin")) tamsin = g;
                c.note("Tamsin: " + (tamsin == null ? "none" : tamsin.how() + ", cause " + tamsin.cause() + ", cycle " + tamsin.cycle()));
                c.expect(tamsin != null && tamsin.how().startsWith("Lost with the ISS Tamsin"), "a combat death names its ship");
            }
            for (PetMemorial.Gone g : gone) {
                if (!g.grey()) continue;
                c.expect(g.how().equals("Lost in another universe") && g.cycle() == -1, "a carried pet reads Lost in another universe, no cycle (" + g.name() + ")");
                break;
            }
            c.reload();
        });
        steps.add(c -> {
            c.expect(PetMemorial.remembered().size() >= n, "after a save and load Remembered keeps its pets");
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
