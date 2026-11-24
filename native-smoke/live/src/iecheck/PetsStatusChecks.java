package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.OptionId;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// pet 5.2: the status line of the piloted pet ship in a real battle, driven by PetsStatusPlugin
public final class PetsStatusChecks {
    private static final Logger log = Global.getLogger(PetsStatusChecks.class);
    static final List<String> testShips = List.of("ISS Rex", "ISS Long", "ISS Plain");
    static CampaignFleetAPI parked;
    static FleetMemberAPI oldFlagship;
    static FleetInteractionDialogPluginImpl fid;
    static volatile boolean fought = false;

    private PetsStatusChecks() {
    }

    private static FleetMemberAPI ship(String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static void pickBattleOption(Ctx c) {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (fought || dialog == null) return;
        for (OptionId option : PetsFightChecks.INTO_BATTLE) {
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
            PetStatusManager manager = PetStatusManager.getInstance();
            Global.getSector().getPlayerFleet().getCargo().addCommodity("food", 500f);
            FleetMemberAPI rexShip = ship("ISS Rex");
            Pet rex = new Pet("doge", "Rex");
            manager.register(rex);
            rex.place(rexShip, null);
            // Rex wears a look (Black), the long name cat is a base pet: both badges show
            new indevo.console.PetCommand().runCommand("look " + rex.id + " doge_3", org.lazywizard.console.BaseCommand.CommandContext.CAMPAIGN_MAP);
            c.note("Rex look " + rex.getLook() + ", icon " + rex.getIcon());
            Pet longName = new Pet("gravcat", "Bartholomew Wigglesworth the Third");
            manager.register(longName);
            longName.place(ship("ISS Long"), null);
            ship("ISS Plain");
            c.put("rex", rex.id);

            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            oldFlagship = player.getFlagship();
            player.getFleetData().setFlagship(rexShip);
            parked = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet check parking", true);
            for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
                if (testShips.contains(m.getShipName())) continue;
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

            fought = false;
            fid = new FleetInteractionDialogPluginImpl();
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(fid, enemy);
            c.frames(60);
        });
        for (int i = 0; i < 4; i++) steps.add(PetsStatusChecks::pickBattleOption);
        steps.add(c -> {
            c.expect(fought && PetsStatusPlugin.done, "a real battle ran every status phase");
            for (String r : PetsStatusPlugin.results) {
                String[] p = r.split("\\|", 2);
                c.expect("ok".equals(p[0]), p[1]);
            }
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            for (FleetMemberAPI m : parked.getFleetData().getMembersListCopy()) {
                parked.getFleetData().removeFleetMember(m);
                player.getFleetData().addFleetMember(m);
            }
            if (oldFlagship != null) player.getFleetData().setFlagship(oldFlagship);
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            c.keepDialog(false);
            c.frames(30);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
