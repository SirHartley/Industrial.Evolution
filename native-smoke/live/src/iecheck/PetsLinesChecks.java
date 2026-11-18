package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// package 1.3: pet lines in flight and on docking, their shared cadence and who may speak
public final class PetsLinesChecks {
    static final List<Float> resets = new ArrayList<>();
    static float lastDays = 0f;

    private PetsLinesChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static VarHandle field(String name) throws Exception {
        return MethodHandles.privateLookupIn(PetStatusManager.class, MethodHandles.lookup()).findVarHandle(PetStatusManager.class, name, float.class);
    }

    private static float days() {
        try {
            return (float) field("daysSinceLine").get(manager());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void set(String name, float value) throws Exception {
        field(name).set(manager(), value);
    }

    private static Pet speaker() {
        try {
            MethodHandle pick = MethodHandles.privateLookupIn(PetStatusManager.class, MethodHandles.lookup()).findVirtual(PetStatusManager.class, "pickSpeaker", MethodType.methodType(Pet.class));
            return (Pet) pick.invoke(manager());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static FleetMemberAPI ship(String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        return member;
    }

    private static Pet pet(String species, String name, FleetMemberAPI on) {
        Pet pet = new Pet(species, name);
        manager().register(pet);
        pet.place(on, null);
        return pet;
    }

    // a drop of daysSinceLine to near 0 is a line or a silent reset
    private static void watch() {
        float now = days();
        if (now < lastDays - 0.01f) resets.add(lastDays);
        lastDays = now;
    }

    private static SectorEntityToken entity(String marketId) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        return market == null ? null : market.getPrimaryEntity();
    }

    private static void dock(Ctx c, SectorEntityToken entity) {
        c.keepDialog(true);
        Global.getSector().getCampaignUI().showInteractionDialog(entity);
        c.frames(90);
    }

    private static String dialogText() {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        return dialog == null ? "" : Ui.text(dialog.getTextPanel());
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            int withBody = 0;
            for (PetData data : PetDataRepo.getAll()) if (data.body != null && !data.body.isEmpty()) withBody++;
            c.expect(withBody == PetDataRepo.getAll().size() && withBody > 30, "every pets.csv row loads with a body (" + withBody + " of " + PetDataRepo.getAll().size() + ")");
            PetData jar = PetDataRepo.get("mipacem7_souljar");
            c.expect(jar != null && !jar.desc.contains("’") && !jar.desc.contains("‘"), "the souljar description has straight apostrophes");
            c.expect(PetDataRepo.getLines().size() > 50, "pet_lines.csv loads (" + PetDataRepo.getLines().size() + " lines)");

            c.note("loaded from a pre 1.3 base save, daysSinceLine " + days());
            c.expect(manager().getPets().isEmpty(), "no pets yet");
            Colony.setting(Setting.PET_LINE_DAYS, 1);
            set("daysSinceLine", 0f);
            set("lineRoll", 0f);
            lastDays = 0f;
            resets.clear();
            c.until(() -> resets.size() >= 2, 6f, "with no pets the counter still runs and resets", PetsLinesChecks::watch);
        });
        steps.add(c -> {
            c.expect(resets.size() >= 2, "no pets: two silent resets in a few days, no crash (" + resets + ")");

            Pet talker = pet("doge", "Talker", ship("ISS Talker Home"));
            Global.getSector().getPlayerFleet().getCargo().addCommodity("food", 500f);
            set("daysSinceLine", 0f);
            set("lineRoll", 0f);
            lastDays = 0f;
            resets.clear();
            c.put("talker", talker.id);
            c.until(() -> resets.size() >= 3, 8f, "three ambient lines with interval 1", PetsLinesChecks::watch);
        });
        steps.add(c -> {
            c.expect(resets.size() >= 3, "ambient lines keep coming at interval 1");
            boolean inRange = true;
            for (float gap : resets) if (gap < 0.99f || gap > 1.55f) inRange = false;
            c.expect(inRange, "each ambient line came after interval x 1 to 1.5 days (" + resets + ")");
            c.shot("petslines-ambient-message");
        });
        steps.add(c -> {
            Pet talker = manager().get((String) c.get("talker"));
            String line = manager().getLine(talker, PetDataRepo.EVENT_AMBIENT, false);
            boolean known = false;
            for (PetDataRepo.Line l : PetDataRepo.getLines()) if (l.event().equals(PetDataRepo.EVENT_AMBIENT) && l.text().equals(line)) known = true;
            c.expect(line != null && known, "an ambient line comes from the ambient rows (" + line + ")");

            set("daysSinceLine", 0f);
            set("lineRoll", 1.5f);
            c.days(1.1f);
        });
        steps.add(c -> {
            float before = days();
            c.expect(before >= 1f && before < 1.5f, "counter past the interval, below the rolled ambient time (" + before + ")");
            SectorEntityToken jangala = entity("jangala");
            c.put("before", before);
            dock(c, jangala);
        });
        steps.add(c -> {
            Pet talker = manager().get((String) c.get("talker"));
            String text = dialogText();
            float after = days();
            c.expect(text.contains("Talker"), "docking at Jangala adds a pet line to the market text (" + text.replace('\n', '|') + ")");
            c.expect(after < 0.05f, "the docking line resets the counter (" + after + ")");
            InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            int vanilla = text.indexOf("docking clearance"), pet = text.lastIndexOf("Talker");
            c.expect(vanilla >= 0 && pet > vanilla, "the pet line comes after the vanilla docking text");
            c.expect(dialog.getOptionPanel().hasOption("marketOpenCoreUI") && dialog.getOptionPanel().hasOption("marketCommDir"), "the market options are there (trade, comm directory)");
            c.shot("petslines-docking-jangala");
            // back to the main menu, as after trade or the comm directory
            com.fs.starfarer.api.impl.campaign.rulecmd.FireAll.fire(null, dialog, dialog.getPlugin().getMemoryMap(), "PopulateOptions");
            c.frames(10);
        });
        steps.add(c -> {
            String text = dialogText();
            int count = text.split("Talker", -1).length - 1;
            c.expect(count == 1, "going back to the main menu adds no second line (" + count + " lines)");
            c.expect(Global.getSector().getCampaignUI().getCurrentInteractionDialog().getOptionPanel().hasOption("marketOpenCoreUI"), "the market options are rebuilt");
            Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
            c.frames(10);
        });
        steps.add(c -> dock(c, entity("jangala")));
        steps.add(c -> {
            c.expect(!dialogText().contains("Talker"), "docking again right after a line adds none");
            Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
            c.keepDialog(false);
            lastDays = days();
            resets.clear();
            set("lineRoll", 1.2f);
            c.until(() -> !resets.isEmpty(), 3f, "an ambient line after the docking line", PetsLinesChecks::watch);
        });
        steps.add(c -> {
            c.expect(!resets.isEmpty() && days() < 0.1f, "ambient line came and reset the counter");
            c.expect(!resets.isEmpty() && resets.get(0) >= 0.99f, "no ambient line right after the docking line (" + resets + " days after it)");
            dock(c, entity("jangala"));
        });
        steps.add(c -> {
            c.expect(!dialogText().contains("Talker"), "docking right after an ambient line adds none");
            Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();

            // a planet with no market and a non market entity
            SectorEntityToken bare = null;
            for (com.fs.starfarer.api.campaign.PlanetAPI p : Global.getSector().getPlayerFleet().getStarSystem() == null ? new ArrayList<com.fs.starfarer.api.campaign.PlanetAPI>() : Global.getSector().getPlayerFleet().getStarSystem().getPlanets()) {
                if (!p.isStar() && (p.getMarket() == null || p.getMarket().isPlanetConditionMarketOnly())) {
                    bare = p;
                    break;
                }
            }
            c.put("bare", bare == null ? null : bare.getId());
            set("daysSinceLine", 1.2f);
            set("lineRoll", 1.5f);
            if (bare != null) dock(c, bare);
        });
        steps.add(c -> {
            String text = dialogText();
            c.expect(c.get("bare") != null, "found a planet without a market in the player's system");
            c.expect(!text.contains("Talker") && days() >= 1.2f, "no pet line at a planet without a market (" + text.replace('\n', '|') + ")");
            c.shot("petslines-no-market-planet");
            if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
            SectorEntityToken gate = null;
            for (SectorEntityToken t : Global.getSector().getPlayerFleet().getContainingLocation().getAllEntities()) {
                if (t.getMarket() == null && t.hasTag("gate")) gate = t;
            }
            if (gate == null) for (SectorEntityToken t : Global.getSector().getPlayerFleet().getContainingLocation().getJumpPoints()) gate = t;
            c.put("other", gate == null ? null : gate.getId());
            if (gate != null) dock(c, gate);
        });
        steps.add(c -> {
            c.expect(c.get("other") != null, "found a non market entity");
            c.expect(!dialogText().contains("Talker") && days() >= 1.2f, "no pet line at a non market entity (" + dialogText().replace('\n', '|') + ")");
            if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
            c.keepDialog(false);

            Colony.setting(Setting.PET_LINE_DAYS, 0);
            set("daysSinceLine", 5f);
            lastDays = 5f;
            resets.clear();
            c.days(3f);
        });
        steps.add(c -> {
            c.expect(resets.isEmpty() && Math.abs(days() - 5f) < 0.01f, "interval 0: the counter stands still, no ambient line (" + days() + ")");
            dock(c, entity("jangala"));
        });
        steps.add(c -> {
            c.expect(!dialogText().contains("Talker"), "interval 0: no docking line");
            Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
            c.keepDialog(false);
            Colony.setting(Setting.PET_LINE_DAYS, 1);

            Colony.pair(c, 6);
            MarketAPI market = c.market("a");
            if (!market.hasIndustry(Ids.PET_STORE)) market.addIndustry(Ids.PET_STORE);
            PetShop shop = (PetShop) market.getIndustry(Ids.PET_STORE);
            Pet stored = pet("doge", "Stored", ship("ISS Stored Home"));
            stored.place(null, shop);
            Pet dead = pet("doge", "Dead", ship("ISS Dead Home"));
            manager().reportPetDied(dead, PetStatusManager.PetDeathCause.UNKNOWN);
            CampaignFleetAPI detachment = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet check detachment", true);
            FleetMemberAPI away = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
            detachment.getFleetData().addFleetMember(away);
            pet("doge", "Away", away);
            pet("doge", "Menagerie", null);

            Map<String, Integer> picks = new HashMap<>();
            for (int i = 0; i < 2000; i++) {
                Pet p = speaker();
                picks.merge(p == null ? "none" : p.name, 1, Integer::sum);
            }
            c.note("speaker picks " + picks);
            c.expect(!picks.containsKey("Stored") && !picks.containsKey("Dead") && !picks.containsKey("Away"), "never a stored, dead or detachment pet");
            c.expect(picks.getOrDefault("Menagerie", 0) > 0 && picks.getOrDefault("Talker", 0) > 0, "menagerie and ship pets speak");

            FleetMemberAPI flag = Global.getSector().getPlayerFleet().getFlagship();
            Pet boss = pet("doge", "Boss", flag);
            picks.clear();
            for (int i = 0; i < 6000; i++) {
                Pet p = speaker();
                picks.merge(p == null ? "none" : p.name, 1, Integer::sum);
            }
            float ratio = picks.getOrDefault("Boss", 0) / (float) Math.max(1, picks.getOrDefault("Talker", 0));
            c.note("speaker picks with a flagship pet " + picks);
            c.expect(ratio > 2.5f && ratio < 3.5f, "the flagship pet speaks about 3x as often (" + ratio + ")");

            set("daysSinceLine", 0.7f);
            set("lineRoll", 1.3f);
            c.reload();
        });
        steps.add(c -> {
            float d = days();
            float roll;
            try {
                roll = (float) field("lineRoll").get(manager());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            c.near(d, 0.7f, 0.05f, "daysSinceLine survives save and load");
            c.near(roll, 1.3f, 0.001f, "lineRoll survives save and load");

            Pet dog = pet("doge", "Hyperdog", null);
            Pet ray = pet("vapourray", "Hyperray", null);
            Pet jar = pet("mipacem7_souljar", "Hyperjar", null);
            Pet old = pet("doge", "Oldtimer", null);
            old.age = old.lifetime * 0.9f;
            Pet[] pets = {dog, ray, jar, old};
            Map<String, String> jumpIds = new HashMap<>();
            for (PetDataRepo.Line l : PetDataRepo.getLines()) {
                if (List.of("IndEvo_pl_amb_fourleg_2", "IndEvo_pl_amb_float_2", "IndEvo_pl_amb_jar", "IndEvo_pl_amb_sleepy", "IndEvo_pl_amb_sleepy_2").contains(l.id())) jumpIds.put(l.text(), l.id());
            }
            c.expect(jumpIds.size() == 5, "the four jump lines and the new sleepy line exist (" + jumpIds.values() + ")");
            c.expect(old.getMood() == indevo.industries.petshop.PetMood.SLEEPY, "the old pet is Sleepy");

            java.util.Set<String> normal = new java.util.HashSet<>(), hyper = new java.util.HashSet<>();
            for (int i = 0; i < 300; i++) for (Pet p : pets) {
                String id = jumpIds.get(manager().getLine(p, PetDataRepo.EVENT_AMBIENT, false));
                if (id != null) normal.add(id);
            }
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            com.fs.starfarer.api.campaign.LocationAPI from = fleet.getContainingLocation();
            float x = fleet.getLocation().x, y = fleet.getLocation().y;
            from.removeEntity(fleet);
            Global.getSector().getHyperspace().addEntity(fleet);
            boolean inHyper = fleet.isInHyperspace();
            try {
                for (int i = 0; i < 300; i++) for (Pet p : pets) {
                    String id = jumpIds.get(manager().getLine(p, PetDataRepo.EVENT_AMBIENT, false));
                    if (id != null) hyper.add(id);
                }
            } finally {
                Global.getSector().getHyperspace().removeEntity(fleet);
                from.addEntity(fleet);
                fleet.setLocation(x, y);
            }
            c.expect(inHyper, "the fleet counted as in hyperspace for the sample");
            c.expect(normal.equals(java.util.Set.of("IndEvo_pl_amb_sleepy_2")), "in normal space no jump line, the sleepy pet gets the new line (" + normal + ")");
            c.expect(hyper.containsAll(List.of("IndEvo_pl_amb_fourleg_2", "IndEvo_pl_amb_float_2", "IndEvo_pl_amb_jar", "IndEvo_pl_amb_sleepy")), "in hyperspace all four jump lines come up (" + hyper + ")");

            set("daysSinceLine", 1.2f);
            set("lineRoll", 1.5f);
            dock(c, entity("jangala"));
        });
        steps.add(c -> {
            c.expect(dialogText().contains("Talker") || dialogText().contains("Boss") || dialogText().contains("Menagerie") || dialogText().contains("Hyper") || dialogText().contains("Oldtimer"), "a docking line for the held market dialog");
            org.apache.log4j.Logger.getLogger(PetsLinesChecks.class).info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
