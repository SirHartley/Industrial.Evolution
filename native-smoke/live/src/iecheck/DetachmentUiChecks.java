package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.dialogue.DetachmentDialogPlugin;
import indevo.abilities.splitfleet.dialogue.DetachmentPanel;
import indevo.abilities.splitfleet.dialogue.DetachmentView;
import indevo.abilities.splitfleet.fleetAssignmentAIs.DetachmentAI;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory.Loadout;

import java.util.ArrayList;
import java.util.List;

// test only: every detachment panel view from code, audited and captured
public final class DetachmentUiChecks {
    private static final String TARGET = "jangala";
    private static final String[] ONE = {"falcon_Attack", "hammerhead_Balanced", "wolf_Assault", "lasher_CS"};
    private static final String[] TWO = {"enforcer_Assault", "buffalo2_FS"};

    private DetachmentUiChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(DetachmentUiChecks::setup);

        UiViews.view(steps, "detach-setup", c -> open(c, new DetachmentView()));
        UiViews.view(steps, "detach-setup-blocked", c -> {
            Loadout one = LoadoutMemory.getLoadout(1);
            one.shipVariantList.add(Global.getSettings().getVariant("onslaught_Elite").clone());
            one.targetCargo.removeSupplies(one.targetCargo.getSupplies() - 1f);
            open(c, new DetachmentView());
        });
        UiViews.view(steps, "detach-take", c -> {
            LoadoutMemory.setLoadout(1, loadout(ONE, FleetBehaviour.TRANSPORT, TARGET, 400f));
            LoadoutMemory.getLoadout(2).takeMode = true;
            open(c, new DetachmentView());
        });
        UiViews.view(steps, "detach-ships-many", c -> {
            LoadoutMemory.getLoadout(2).takeMode = false;
            Loadout two = LoadoutMemory.getLoadout(2);
            for (int i = 0; i < 30; i++) two.addToMembersList(ship(i % 2 == 0 ? "lasher_CS" : "wolf_Assault", null));
            open(c, new DetachmentView());
        });
        UiViews.view(steps, "detach-worst", c -> {
            market().setName("Nova Maxios Prime Station");
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(987654321f);
            Global.getSector().getPlayerFleet().getCargo().addSupplies(987654f);
            for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (!m.isFlagship()) m.setShipName("ISS Unreasonably Long Ship Name");
            }
            open(c, new DetachmentView());
        });
        UiViews.view(steps, "detach-live", c -> {
            market().setName("Jangala");
            Global.getSector().getPlayerFleet().getCargo().removeSupplies(987654f);
            LoadoutMemory.setLoadout(2, loadout(TWO, FleetBehaviour.STAY, null, 200f));
            Loadout one = loadout(ONE, FleetBehaviour.FOLLOW, null, 400f);
            LoadoutMemory.setLoadout(1, one);
            FleetUtils.createAndSpawnFleet(one, 1);
            CampaignFleetAPI fleet = DetachmentMemory.getDetachment(1);
            c.expect(fleet != null, "slot 1 deployed");
            fleet.getMemoryWithoutUpdate().set(FleetUtils.SPAWN_TIMESTAMP_KEY, Global.getSector().getClock().getTimestamp() - 5L * 86400000L);
            open(c, new DetachmentView());
        });
        UiViews.view(steps, "detach-met", c -> {
            CampaignFleetAPI fleet = DetachmentMemory.getDetachment(1);
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            fleet.setLocation(player.getLocation().x + 50f, player.getLocation().y);
            c.expect(DetachmentDialogPlugin.show(new DetachmentView(1)), "meeting panel opens");
        });
        UiViews.view(steps, "detach-attack", c -> {
            CampaignFleetAPI pirates = Global.getFactory().createEmptyFleet(Factions.PIRATES, "Raiders", true);
            pirates.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault"));
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            player.getContainingLocation().addEntity(pirates);
            pirates.setLocation(player.getLocation().x - 150f, player.getLocation().y);
            pirates.setNoEngaging(100f);
            CampaignFleetAPI fleet = DetachmentMemory.getDetachment(1);
            List<SectorEntityToken> targets = DetachmentPanel.getAttackTargets(fleet);
            c.note("attack targets " + targets.size());
            DetachmentAI ai = FleetUtils.getAssignmentAI(fleet);
            if (!targets.isEmpty() && ai != null && targets.get(0) instanceof CampaignFleetAPI target) ai.attack(target);
            c.expect(Behaviour.isAttacking(fleet), "slot 1 attacks");
            open(c, new DetachmentView());
        });
        UiViews.view(steps, "detach-states", c -> {
            CampaignFleetAPI fleet = DetachmentMemory.getDetachment(1);
            Behaviour.removeOverride(fleet, FleetBehaviour.ATTACK_TARGET);
            fleet.getCargo().removeSupplies(fleet.getCargo().getSupplies());
            Behaviour.addOverride(fleet, FleetBehaviour.DORMANT);
            FleetUtils.createAndSpawnFleet(LoadoutMemory.getLoadout(2), 2);
            c.expect(DetachmentMemory.getDetachment(2) != null, "slot 2 deployed");
            Behaviour.setReturning(2, true);
            open(c, new DetachmentView());
        });
        UiViews.view(steps, "detach-empty", c -> {
            for (int slot : DetachmentMemory.SLOTS) if (DetachmentMemory.isDetachmentActive(slot)) FleetUtils.mergeDetachment(slot);
            for (int slot : DetachmentMemory.SLOTS) LoadoutMemory.setLoadout(slot, new Loadout());
            open(c, new DetachmentView());
        });
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    private static void setup(Ctx c) {
        MarketAPI market = market();
        Colony.jump(market.getContainingLocation(), market.getPrimaryEntity().getLocation().x + 400f, market.getPrimaryEntity().getLocation().y);
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        fleet.getCargo().getCredits().set(500000f);
        fleet.getCargo().addSupplies(1500f);
        fleet.getCargo().addFuel(1500f);
        fleet.getCargo().addCrew(1500);
        LoadoutMemory.setLoadout(1, loadout(ONE, FleetBehaviour.TRANSPORT, TARGET, 400f));
        LoadoutMemory.setLoadout(2, loadout(TWO, FleetBehaviour.STAY, null, 200f));
        c.note("detachments at " + market.getName() + ", fleet " + fleet.getFleetData().getNumMembers() + " ships");
    }

    private static Loadout loadout(String[] variants, FleetBehaviour behaviour, String target, float supplies) {
        Loadout loadout = new Loadout();
        for (String id : variants) loadout.addToMembersList(ship(id, officer()));
        loadout.behaviour = behaviour;
        loadout.transportTargetMarket = target;
        loadout.targetCargo.addSupplies(supplies);
        loadout.targetCargo.addFuel(supplies / 2f);
        loadout.targetCargo.addCrew((int) supplies / 2);
        return loadout;
    }

    // a fresh member with a captain so every loadout entry matches one ship in the fleet
    private static FleetMemberAPI ship(String variant, PersonAPI captain) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        fleet.getFleetData().addFleetMember(m);
        m.getRepairTracker().setCR(0.7f);
        if (captain != null) {
            fleet.getFleetData().addOfficer(captain);
            m.setCaptain(captain);
        }
        return m;
    }

    private static PersonAPI officer() {
        return OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 3);
    }

    private static MarketAPI market() {
        return Global.getSector().getEconomy().getMarket(TARGET);
    }

    private static void open(Ctx c, DetachmentView view) {
        c.expect(DetachmentDialogPlugin.show(view), "detachment panel opens");
    }
}
