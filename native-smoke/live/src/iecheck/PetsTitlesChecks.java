package iecheck;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

// pet 2.5: titles and their counters, the Scavenger find, jump and fight lines
public final class PetsTitlesChecks {
    private static final Logger log = Global.getLogger(PetsTitlesChecks.class);
    static CampaignFleetAPI parked;

    private PetsTitlesChecks() {
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

    private static Pet pet(Ctx c, String name, FleetMemberAPI on) {
        Pet pet = new Pet("doge", name);
        manager().register(pet);
        pet.place(on, null);
        c.put(name, pet.id);
        return pet;
    }

    private static Pet get(Ctx c, String key) {
        return manager().get((String) c.get(key));
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    private static VarHandle field(String name) throws Exception {
        return MethodHandles.privateLookupIn(PetStatusManager.class, MethodHandles.lookup()).findVarHandle(PetStatusManager.class, name, float.class);
    }

    private static float lineDays() {
        try {
            return (float) field("daysSinceLine").get(manager());
        } catch (Exception e) {
            return -1f;
        }
    }

    private static void lineDue() {
        try {
            // due for a moment line (interval 1 day) but far from an ambient one (interval x lineRoll)
            field("daysSinceLine").set(manager(), 1.05f);
            field("lineRoll").set(manager(), 100f);
        } catch (Exception e) {
            log.info("LIVE_CHECK note: no daysSinceLine field " + e);
        }
    }

    private static boolean has(Pet p, PetTitle t) {
        return p.getTitleIds().contains(t.name());
    }

    private static void jump(SectorEntityToken to) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        Global.getSector().doHyperspaceTransition(fleet, fleet, new JumpPointAPI.JumpDestination(to, null));
    }

    private static String items(CargoAPI cargo) {
        List<String> list = new ArrayList<>();
        for (CargoStackAPI s : cargo.getStacksCopy()) list.add(Math.round(s.getSize()) + " " + s.getDisplayName());
        return list.toString();
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            if (!c.market("a").hasIndustry(Ids.PET_STORE)) c.market("a").addIndustry(Ids.PET_STORE);
            ((PetShop) c.market("a").getIndustry(Ids.PET_STORE)).finishBuildingOrUpgrading();
            Colony.setting(indevo.config.Setting.PET_LINE_DAYS, 1);
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            fleet.getCargo().addCommodity("food", 500f);

            Pet none = pet(c, "Plain", ship("ISS Plain"));
            c.expect(none.getTitles().isEmpty(), "a new pet has no titles");
            Pet bel = pet(c, "Bel", ship("ISS Bel"));
            cmd("hearts " + bel.id + " 5");
            c.expect(has(bel, PetTitle.BELOVED), "Beloved at 5 hearts");
            Pet vet = pet(c, "Vet", ship("ISS Vet Home"));
            vet.fights = 8;
            Pet nav = pet(c, "Nav", ship("ISS Nav"));
            nav.jumps = 48;
            Pet pam = pet(c, "Pam", ship("ISS Pam"));
            pam.answeredWants = 9;
            cmd("want " + pam.id + " play");
            pam.play();
            c.expect(has(pam, PetTitle.PAMPERED) && pam.answeredWants == 10, "Pampered at 10 answered wants (" + pam.answeredWants + ")");
            Pet old = pet(c, "Old", ship("ISS Old"));
            cmd("age " + old.id + " " + (old.getData().maxLife - 1));
            Pet adm = pet(c, "Adm", fleet.getFlagship());
            adm.flagshipDays = 364f;
            Pet glob = pet(c, "Glob", ship("ISS Glob"));
            for (int i = 0; i < 24; i++) glob.markets.add("pet_check_market_" + i);
            Pet star = pet(c, "Star", ship("ISS Star"));
            star.hyperspaceDays = 119f;
            // console title and clear
            cmd("title " + none.id + " veteran");
            c.expect(has(none, PetTitle.VETERAN), "console gives a title");
            cmd("title " + none.id + " clear");
            c.expect(none.getTitles().isEmpty(), "console clear removes the titles");
            c.days(2f);
        });
        steps.add(c -> {
            c.expect(has(get(c, "Old"), PetTitle.OLD_TIMER), "Old Timer past the species' max life (" + get(c, "Old").age + " of " + get(c, "Old").getData().maxLife + ")");
            c.expect(has(get(c, "Adm"), PetTitle.ADMIRAL), "Admiral after 365 days on the flagship (" + get(c, "Adm").flagshipDays + ")");
            c.expect(!has(get(c, "Star"), PetTitle.STARGAZER), "no Stargazer outside hyperspace (" + get(c, "Star").hyperspaceDays + ")");
            // a jump: Navigator, a line
            lineDue();
            jump(Global.getSector().getHyperspace().createToken(Global.getSector().getPlayerFleet().getLocationInHyperspace().x + 300f, Global.getSector().getPlayerFleet().getLocationInHyperspace().y));
            c.days(0.5f);
        });
        steps.add(c -> {
            Pet nav = get(c, "Nav");
            c.expect(nav.jumps == 49, "a jump counts (" + nav.jumps + ")");
            c.expect(lineDays() < 1f, "a jump takes a pet line when one is due (days since line " + lineDays() + ")");
            c.shot("petstitles-jump-line");
            c.days(2f);
        });
        steps.add(c -> {
            c.expect(has(get(c, "Star"), PetTitle.STARGAZER), "Stargazer after 120 days in hyperspace (" + get(c, "Star").hyperspaceDays + ")");
            jump(c.market("a").getStarSystem().getCenter());
            c.days(0.5f);
        });
        steps.add(c -> {
            Pet nav = get(c, "Nav");
            c.expect(has(nav, PetTitle.NAVIGATOR), "Navigator at 50 jumps (" + nav.jumps + ")");
            Global.getSector().getPlayerFleet().setLocation(c.market("a").getPrimaryEntity().getLocation().x + 200f, c.market("a").getPrimaryEntity().getLocation().y);
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(c.market("a").getPrimaryEntity());
            c.frames(60);
        });
        steps.add(c -> {
            Pet glob = get(c, "Glob");
            c.expect(has(glob, PetTitle.GLOBETROTTER), "Globetrotter at 25 markets (" + glob.markets.size() + " kept after)");
            InteractionDialogAPI d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            if (d != null) d.dismiss();
            c.keepDialog(false);
            // Scavenger find: a lead Scavenger, a salvageable wreck whose seed rolls under 25%
            Pet scav = pet(c, "Scav", ship("ISS Scav"));
            for (String id : new ArrayList<>(scav.sheet.traits.keySet())) scav.setTrait(id, 0);
            cmd("trait " + scav.id + " scavenger 3");
            cmd("hearts " + scav.id + " 1");
            scav.finds = 4;
            SectorEntityToken hit = null, miss = null;
            for (int i = 0; i < 60 && (hit == null || miss == null); i++) {
                SectorEntityToken e = Global.getSector().getPlayerFleet().getContainingLocation().addCustomEntity(null, "Pet check cache " + i, "supply_cache", Factions.NEUTRAL);
                if (!e.hasTag(Tags.SALVAGEABLE)) e.addTag(Tags.SALVAGEABLE);
                e.setLocation(Global.getSector().getPlayerFleet().getLocation().x + 60f, Global.getSector().getPlayerFleet().getLocation().y);
                boolean rolls = Misc.getRandom(Misc.getSalvageSeed(e), 23).nextFloat() < 0.25f;
                if (rolls && hit == null) hit = e;
                else if (!rolls && miss == null) miss = e;
            }
            c.put("hit", hit.getId());
            c.put("miss", miss.getId());
            c.frames(20);
        });
        steps.add(c -> {
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(Global.getSector().getEntityById((String) c.get("hit")));
            c.frames(30);
        });
        steps.add(c -> {
            InteractionDialogAPI d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            CargoAPI loot = Global.getFactory().createCargo(true);
            ListenerUtil.reportAboutToShowLootToPlayer(loot, d);
            c.expect(!loot.isEmpty(), "a lead Scavenger finds extra salvage in a wreck that rolls under 25% (" + items(loot) + ")");
            c.put("find", items(loot));
            Pet scav = get(c, "Scav");
            c.expect(has(scav, PetTitle.TREASURE_HUNTER), "Treasure Hunter at 5 finds (" + scav.finds + ")");
            if (d != null) d.dismiss();
            c.frames(20);
        });
        steps.add(c -> {
            Global.getSector().getCampaignUI().showInteractionDialog(Global.getSector().getEntityById((String) c.get("miss")));
            c.frames(30);
        });
        steps.add(c -> {
            InteractionDialogAPI d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            CargoAPI loot = Global.getFactory().createCargo(true);
            ListenerUtil.reportAboutToShowLootToPlayer(loot, d);
            c.expect(loot.isEmpty(), "no find in a wreck that rolls over 25% (" + items(loot) + ")");
            if (d != null) d.dismiss();
            c.keepDialog(false);
            c.days(0.3f);
            c.shot("petstitles-find-message");
            c.reload();
        });
        steps.add(c -> {
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(Global.getSector().getEntityById((String) c.get("hit")));
            c.frames(30);
        });
        steps.add(c -> {
            InteractionDialogAPI d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            CargoAPI loot = Global.getFactory().createCargo(true);
            ListenerUtil.reportAboutToShowLootToPlayer(loot, d);
            c.expect(items(loot).equals(c.get("find")), "after a reload the same wreck gives the same find (" + items(loot) + " vs " + c.get("find") + ")");
            if (d != null) d.dismiss();
            c.keepDialog(false);
            c.frames(10);
        });
        steps.add(c -> {
            // the fight: Vet at 9 fights, six 5-heart pets on lost ships for Survivor
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            Pet vet = get(c, "Vet");
            vet.fights = 9;
            for (int i = 1; i <= 6; i++) {
                Pet p = pet(c, "Surv" + i, ship("ISS Pod Surv " + i));
                cmd("hearts " + p.id + " 5");
            }
            FleetMemberAPI keeper = vet.assignedFleetMember;
            player.getFleetData().setFlagship(keeper);
            parked = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet check parking", true);
            for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
                if (m == keeper || m.getShipName() != null && m.getShipName().startsWith("ISS Pod")) continue;
                player.getFleetData().removeFleetMember(m);
                parked.getFleetData().addFleetMember(m);
            }
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            CampaignFleetAPI enemy = Global.getFactory().createEmptyFleet(Factions.PIRATES, "Pet check pirates", true);
            enemy.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hound_Standard"));
            enemy.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hound_Standard"));
            enemy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            enemy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
            player.getContainingLocation().addEntity(enemy);
            enemy.setLocation(player.getLocation().x + 100f, player.getLocation().y);
            enemy.getFleetData().setSyncNeeded();
            enemy.getFleetData().syncIfNeeded();
            lineDue();
            PetsFightChecks.fought = false;
            PetsHeartsChecks.fid = new FleetInteractionDialogPluginImpl();
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(PetsHeartsChecks.fid, enemy);
            c.frames(60);
        });
        for (int i = 0; i < 4; i++) steps.add(c -> { if (!PetsFightChecks.fought) PetsHeartsChecks.pickPublic(c, PetsHeartsChecks.INTO_BATTLE); });
        for (int i = 0; i < 5; i++) steps.add(c -> PetsHeartsChecks.pickPublic(c, PetsHeartsChecks.OUT_OF_BATTLE));
        steps.add(c -> {
            c.expect(PetsFightChecks.fought, "a real battle ran");
            Pet vet = get(c, "Vet");
            c.expect(vet.fights == 10 && has(vet, PetTitle.VETERAN), "Veteran at 10 fights (" + vet.fights + ")");
            c.expect(lineDays() < 1f, "a fight takes a pet line when one is due (days since line " + lineDays() + ")");
            int survivors = 0;
            for (int i = 1; i <= 6; i++) {
                Pet p = get(c, "Surv" + i);
                c.note("Surv" + i + ": " + (p.isDead() ? "dead" : "alive") + ", titles " + p.getTitleIds());
                if (!p.isDead()) {
                    c.expect(has(p, PetTitle.SURVIVOR), "a pet that escaped in a pod is a Survivor");
                    survivors++;
                }
            }
            c.note("survivors " + survivors + " of 6");
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            for (FleetMemberAPI m : parked.getFleetData().getMembersListCopy()) {
                parked.getFleetData().removeFleetMember(m);
                player.getFleetData().addFleetMember(m);
            }
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            c.keepDialog(false);
            c.frames(30);
        });
        steps.add(c -> {
            // the held state: Vet with every title, Plain with none; Late earns Stargazer 40 s into the hold, for the held message
            Pet vet = get(c, "Vet");
            for (PetTitle t : PetTitle.values()) if (t != PetTitle.STARGAZER) cmd("title " + vet.id + " " + t.name().toLowerCase());
            pet(c, "Late", ship("ISS Late"));
            Global.getSector().addTransientScript(new LateTitle((String) c.get("Late")));
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

    // earns a title 40 real seconds into the hold, while the Pets panel can be open
    static final class LateTitle implements EveryFrameScript {
        private final String petId;
        private float time = 0f;
        private boolean done = false;

        LateTitle(String petId) {
            this.petId = petId;
        }

        public boolean isDone() {
            return done;
        }

        public boolean runWhilePaused() {
            return true;
        }

        public void advance(float amount) {
            time += Global.getSector().isPaused() ? amount : amount;
            if (time < 40f) return;
            Pet p = PetStatusManager.getInstance().get(petId);
            if (p != null) p.earn(PetTitle.STARGAZER);
            log.info("LIVE_CHECK note: Late earned Stargazer now, panel held messages " + held());
            done = true;
        }

        private static String held() {
            try {
                return String.valueOf(MethodHandles.privateLookupIn(PetStatusManager.class, MethodHandles.lookup()).findVarHandle(PetStatusManager.class, "held", List.class).get(PetStatusManager.getInstance()));
            } catch (Exception e) {
                return "unknown";
            }
        }
    }
}
