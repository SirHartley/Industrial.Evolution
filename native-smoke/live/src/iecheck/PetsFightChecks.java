package iecheck;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.OptionId;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// a real fleet encounter with pet ships deployed, then the game held with the pet manager open for clicks
public final class PetsFightChecks {
    private static final Logger log = Global.getLogger(PetsFightChecks.class);
    static final OptionId[] INTO_BATTLE = {OptionId.ENGAGE, OptionId.PURSUE, OptionId.CONTINUE_INTO_BATTLE, OptionId.INITIATE_BATTLE};

    static final List<String> testShips = List.of("ISS Steady Home", "ISS Old Home", "ISS New Home", "ISS Spare Berth");
    static final List<String> deployed = new ArrayList<>();
    static CampaignFleetAPI parked;
    static FleetMemberAPI oldFlagship;
    static FleetInteractionDialogPluginImpl fid;
    // set by PetsBattlePlugin, survives the combat state
    static volatile boolean fought = false;

    private PetsFightChecks() {
    }

    private static FleetMemberAPI ship(String variant, String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        member.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static String mood(Pet pet) {
        TooltipMakerAPI tooltip = Ui.tooltip();
        pet.addHullmodDescriptionSection(tooltip);
        return Ui.text(tooltip);
    }

    private static void pickBattleOption(Ctx c) {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (fought || dialog == null) return;
        for (OptionId option : INTO_BATTLE) {
            if (dialog.getOptionPanel().hasOption(option)) {
                c.note("encounter option " + option);
                fid.optionSelected(null, option);
                break;
            }
        }
        c.frames(90);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            cargo.addCommodity("food", 500f);
            cargo.addCommodity("ore", 500f);
            PetStatusManager manager = PetStatusManager.getInstance();

            Pet steady = new Pet("slaghound", "Steady");
            manager.register(steady);
            steady.place(ship("wolf_Assault", "ISS Steady Home"), null);

            Pet mover = new Pet("bugdog", "Mover");
            manager.register(mover);
            mover.place(ship("wolf_Assault", "ISS Old Home"), null);
            mover.place(ship("wolf_Assault", "ISS New Home"), null);
            ship("lasher_CS", "ISS Spare Berth");

            c.expect(steady.getMood() == PetMood.CONTENT && mover.getMood() == PetMood.UNSETTLED, "moods before the fight: " + steady.getMood() + ", " + mover.getMood());
            String steadyText = mood(steady), moverText = mood(mover);
            c.expect(steadyText.contains("Mood: " + PetMood.CONTENT.label), "pet data section shows Mood: Content (" + steadyText.replace('\n', '|') + ")");
            c.expect(moverText.contains("Mood: " + PetMood.UNSETTLED.label), "pet data section shows Mood: Unsettled (" + moverText.replace('\n', '|') + ")");
            c.put("steady", steady.id);
            c.put("mover", mover.id);

            // only the test ships fight, the base fleet waits in a holding fleet until the battle is over
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            oldFlagship = player.getFlagship();
            player.getFleetData().setFlagship(steady.assignedFleetMember);
            parked = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet check parking", true);
            for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
                if (testShips.contains(m.getShipName())) continue;
                player.getFleetData().removeFleetMember(m);
                parked.getFleetData().addFleetMember(m);
            }
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            c.expect(player.getFleetData().getNumMembers() == testShips.size(), "only the test ships are in the player fleet (" + player.getFleetData().getNumMembers() + ")");
            deployed.clear();
            Global.getSector().addTransientListener(new EngagementLog());

            CampaignFleetAPI enemy = Global.getFactory().createEmptyFleet(Factions.PIRATES, "Pet check pirates", true);
            enemy.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hound_Standard"));
            enemy.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hound_Standard"));
            enemy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            enemy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
            player.getContainingLocation().addEntity(enemy);
            enemy.setLocation(player.getLocation().x + 100f, player.getLocation().y);
            enemy.getFleetData().setSyncNeeded();
            enemy.getFleetData().syncIfNeeded();

            fought = false;
            fid = new FleetInteractionDialogPluginImpl();
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(fid, enemy);
            c.frames(60);
        });
        for (int i = 0; i < 4; i++) steps.add(PetsFightChecks::pickBattleOption);
        steps.add(c -> {
            c.expect(fought, "a real battle ran");
            c.expect(deployed.contains("ISS Steady Home") && deployed.contains("ISS New Home"), "both pet ships were deployed (" + deployed + ")");
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            for (FleetMemberAPI m : parked.getFleetData().getMembersListCopy()) {
                parked.getFleetData().removeFleetMember(m);
                player.getFleetData().addFleetMember(m);
            }
            if (oldFlagship != null) player.getFleetData().setFlagship(oldFlagship);
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            PetStatusManager manager = PetStatusManager.getInstance();
            Pet steady = manager.get((String) c.get("steady"));
            Pet mover = manager.get((String) c.get("mover"));
            c.expect(!steady.isDead() && !mover.isDead(), "both pets survived the fight");
            c.near(steady.shellShockedDays, 7f, 0.5f, "deployed pet is Shell shocked for 7 days");
            c.expect(steady.getMood() == PetMood.SHELL_SHOCKED, "settled pet after the fight is Shell shocked (" + steady.getMood() + ")");
            c.expect(mood(steady).contains("Mood: " + PetMood.SHELL_SHOCKED.label), "pet data section shows Mood: Shell shocked");
            c.expect(mover.shellShockedDays > 0f && mover.getMood() == PetMood.UNSETTLED, "Unsettled wins over Shell shocked in the mood (" + mover.getMood() + ")");
            c.keepDialog(false);
            c.frames(30);
        });
        steps.add(c -> {
            Global.getSector().addTransientScript(new PetWatch());
            c.keepDialog(true);
            PetsDialogPlugin.show(null, new PetsView());
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }

    // what the game reports to campaign listeners, the same call PetStatusManager gets
    static final class EngagementLog extends BaseCampaignEventListener {
        EngagementLog() {
            super(false);
        }

        @Override
        public void reportPlayerEngagement(EngagementResultAPI result) {
            for (EngagementResultForFleetAPI side : List.of(result.getWinnerResult(), result.getLoserResult())) {
                if (side == null || !side.isPlayer()) continue;
                List<FleetMemberAPI> fought = new ArrayList<>(side.getDeployed());
                fought.addAll(side.getRetreated());
                for (FleetMemberAPI m : fought) deployed.add(m.getShipName());
            }
            log.info("LIVE_CHECK note: engagement reported, player deployed " + deployed);
        }
    }

    // log evidence for the clicks in the held game
    static final class PetWatch implements EveryFrameScript {
        private String last = "";

        public boolean isDone() {
            return false;
        }

        public boolean runWhilePaused() {
            return true;
        }

        public void advance(float amount) {
            StringBuilder state = new StringBuilder();
            for (String id : new String[]{"steady", "mover"}) {
                Object petId = Global.getSector().getPersistentData().get("ie_check_" + id);
                Pet pet = petId == null ? null : PetStatusManager.getInstance().get((String) petId);
                if (pet == null) continue;
                state.append(pet.name).append(" on ").append(pet.assignedFleetMember == null ? "none" : pet.assignedFleetMember.getShipName())
                        .append(" mood ").append(pet.getMood()).append(" unsettled ").append(Math.round(pet.unsettledDays)).append("; ");
            }
            String now = state.toString();
            if (!now.equals(last)) log.info("LIVE_CHECK note: petwatch " + now);
            last = now;
        }
    }
}
