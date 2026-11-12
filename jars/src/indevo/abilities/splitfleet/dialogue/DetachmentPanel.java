package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.MarkerData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetAssignmentAIs.DetachmentAI;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.industries.courierport.ShippingTargetHelper;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiFrame;
import indevo.utils.ui.UiRoot;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;

// one column per detachment slot, before and after deployment
public class DetachmentPanel extends BaseCustomDialogDelegate {

    public static final float BOARD_W = 1024f;
    public static final float BOARD_H = 703f;
    static final float COLUMN_GAP = 12f;
    static final float BODY_Y = 30f;

    private final DetachmentDialogPlugin plugin;
    private final DetachmentView view;
    private CustomDialogCallback callback;
    private UiRoot root;
    private boolean open = false;
    private boolean picking = false;

    public DetachmentPanel(DetachmentDialogPlugin plugin, DetachmentView view) {
        this.plugin = plugin;
        this.view = view;
    }

    public static float getWidth() {
        return UiFrame.width(BOARD_W);
    }

    public static float getHeight() {
        return UiFrame.height(BOARD_H);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        open = true;
        root = new UiRoot(panel, view.scroll, this::build);
        root.refresh();
    }

    @Override
    public boolean hasCancelButton() {
        return false;
    }

    @Override
    public String getConfirmText() {
        return "Return";
    }

    @Override
    public void customDialogConfirm() {
        closed();
    }

    @Override
    public void customDialogCancel() {
        closed();
    }

    private void closed() {
        open = false;
        if (picking) picking = false;
        else plugin.leave();
    }

    boolean isOpen() {
        return open;
    }

    void dismissForPicker() {
        picking = true;
        callback.dismissCustomDialog(1);
    }

    void refresh() {
        root.refresh();
    }

    DetachmentView getView() {
        return view;
    }

    private void build(CustomPanelAPI p, float w, float h) {
        String notice = view.notice;
        int noticeSlot = view.noticeSlot;
        view.notice = null;

        UiFrame.title(p, w, "Detachments", null, this::addHelp);

        float colW = Math.round((w - COLUMN_GAP) / 2f);
        List<Integer> slots = DetachmentMemory.SLOTS;
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            new DetachmentColumn(this, slot, slot == noticeSlot ? notice : null).build(p, i * (colW + COLUMN_GAP), BODY_Y, colW, h - BODY_Y);
        }

        Ui.vrule(p, colW + COLUMN_GAP / 2f, BODY_Y, h - BODY_Y);
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        float pad = 10f;
        tooltip.addPara("Split off part of your fleet into a detachment. You remain in control of your main force, while the detachment follows the mode you set.", 0f);
        tooltip.addPara("Send: the ships and cargo you pick leave. Take: you keep the ships and cargo you pick, everything else leaves. Your flagship always stays with you.", pad);
        tooltip.addPara("A detachment uses supplies and fuel like your fleet. Out of supplies it goes %s, out of fuel outside hyperspace it is %s. Meet it and move supplies or fuel over.", pad,
                Misc.getNegativeHighlightColor(), "dormant", "stranded");
        tooltip.addPara("Auto-recall brings it back when supplies drop below %s days. Officers in a detachment draw their salary.", pad,
                Misc.getHighlightColor(), (int) DetachmentAI.AUTO_RECALL_DAYS + "");
        tooltip.addPara("Transport stores all ships and cargo at the target planet's storage.", pad);
    }

    void setMode(int num, FleetBehaviour mode) {
        if (DetachmentMemory.isDetachmentActive(num)) Behaviour.changeBehaviourAndUpdateAI(DetachmentMemory.getDetachment(num), mode);
        else LoadoutMemory.getLoadout(num).behaviour = mode;
        refresh();
    }

    // the stored ships and cargo flip meaning between the modes
    void setTakeMode(int num, boolean take) {
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        if (loadout.takeMode != take) {
            loadout.takeMode = take;
            loadout.shipVariantList.clear();
            loadout.targetCargo.clear();
        }

        refresh();
    }

    void toggleAutoRecall(int num) {
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        loadout.autoRecall = !loadout.autoRecall;
        refresh();
    }

    // the flag is set when the mode is applied, so a live detachment applies it again
    void toggleJoinFights(int num) {
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        loadout.joinFights = !loadout.joinFights;

        DetachmentAI ai = DetachmentMemory.isDetachmentActive(num) ? FleetUtils.getAssignmentAI(DetachmentMemory.getDetachment(num)) : null;
        if (ai != null) ai.refresh();
        refresh();
    }

    // load min and max on the loadout, the same amounts the cargo picker's buttons set
    void load(int num, DetachmentCargoLoad.Mode mode) {
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        boolean take = loadout.takeMode;

        CargoAPI picked = loadout.targetCargo.createCopy();
        CargoAPI rest = player.getCargo().createCopy();
        rest.removeAll(picked);

        List<FleetMemberAPI> pickedMembers = take ? FleetUtils.getTakenMembers(loadout, player) : FleetUtils.getFleetMembersForLoadout(loadout, player);
        List<FleetMemberAPI> restMembers = new ArrayList<>(player.getFleetData().getMembersListCopy());
        restMembers.removeAll(pickedMembers);

        FleetStatus from = FleetStatus.getFleetStatus(take ? "Left behind" : "Main Fleet", rest, restMembers, !take);
        FleetStatus to = FleetStatus.getFleetStatus(take ? "You take" : "Detachment", picked, pickedMembers, take);
        DetachmentCargoLoad.apply(mode, take ? from : to, take ? to : from);

        loadout.targetCargo.clear();
        loadout.targetCargo.addAll(picked);
        refresh();
    }

    void deploy(int num) {
        FleetUtils.createAndSpawnFleet(LoadoutMemory.getLoadout(num), num);
        view.scroll.reset(DetachmentView.listId(num));
        refresh();
    }

    void layCourse(int num) {
        Global.getSector().getPlayerFleet().setInteractionTarget(DetachmentMemory.getDetachment(num));
        refresh();
    }

    void recall(int num) {
        CampaignFleetAPI fleet = DetachmentMemory.getDetachment(num);
        Behaviour.setReturning(fleet, true);

        if (fleet.getBattle() != null && Misc.getDistance(fleet, Global.getSector().getPlayerFleet()) < FleetUtils.MIN_MERGE_DISTANCE_IN_MENU)
            FleetUtils.mergeDetachment(num);

        refresh();
    }

    void cancelRecall(int num) {
        Behaviour.setReturning(DetachmentMemory.getDetachment(num), false);
        refresh();
    }

    void merge(int num) {
        Behaviour.setReturning(num, true);
        FleetUtils.mergeDetachment(num);
        view.scroll.reset(DetachmentView.listId(num));
        refresh();
    }

    void startTradeRun(int num) {
        DetachmentAI ai = FleetUtils.getAssignmentAI(DetachmentMemory.getDetachment(num));
        if (ai != null) ai.startTradeRun();
        refresh();
    }

    void stopOrder(int num, FleetBehaviour order) {
        Behaviour.removeOverride(DetachmentMemory.getDetachment(num), order);
        refresh();
    }

    void showShipPicker(int num) {
        plugin.openPicker(num, () -> new TwoFleetFleetMemberPicker(plugin, LoadoutMemory.getLoadout(num)).init());
    }

    void showCargoPicker(int num) {
        plugin.openPicker(num, () -> new TwoFleetCargoPicker(plugin, Global.getSector().getPlayerFleet(), LoadoutMemory.getLoadout(num), true).init());
    }

    void showCargoExchange(int num) {
        plugin.openPicker(num, () -> new TwoFleetCargoPicker(plugin, Global.getSector().getPlayerFleet(), DetachmentMemory.getDetachment(num), false, false, true).init());
    }

    public static List<SectorEntityToken> getGoToTargets(CampaignFleetAPI detachment) {
        List<SectorEntityToken> targets = new ArrayList<>();
        if (!(detachment.getContainingLocation() instanceof StarSystemAPI system) || detachment.isInHyperspace()) return targets;

        for (SectorEntityToken entity : system.getAllEntities()) {
            boolean planet = entity instanceof PlanetAPI && !entity.isStar();
            if (planet || entity instanceof JumpPointAPI || entity.hasTag(Tags.STATION) || entity.hasTag(Tags.GATE) || entity.hasTag(Tags.OBJECTIVE)) targets.add(entity);
        }

        return targets;
    }

    public static List<SectorEntityToken> getAttackTargets(CampaignFleetAPI detachment) {
        List<SectorEntityToken> targets = new ArrayList<>();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (!(detachment.getContainingLocation() instanceof StarSystemAPI) || detachment.isInHyperspace()) return targets;

        for (CampaignFleetAPI other : detachment.getContainingLocation().getFleets()) {
            if (other.isAlive() && !other.isStationMode() && other.isHostileTo(player) && other.isVisibleToPlayerFleet()) targets.add(other);
        }

        return targets;
    }

    void showGoToPicker(int num) {
        CampaignFleetAPI detachment = DetachmentMemory.getDetachment(num);
        showEntityPicker(num, "Select where " + detachment.getName() + " should go", getGoToTargets(detachment), detachment, entity -> {
            DetachmentAI ai = FleetUtils.getAssignmentAI(detachment);
            if (ai != null) ai.goTo(entity);
        });
    }

    void showAttackPicker(int num) {
        CampaignFleetAPI detachment = DetachmentMemory.getDetachment(num);
        showEntityPicker(num, "Select a fleet for " + detachment.getName() + " to attack", getAttackTargets(detachment), detachment, entity -> {
            DetachmentAI ai = FleetUtils.getAssignmentAI(detachment);
            if (ai != null && entity instanceof CampaignFleetAPI target) ai.attack(target);
        });
    }

    private void showEntityPicker(int num, String title, List<SectorEntityToken> entities, CampaignFleetAPI detachment, Consumer<SectorEntityToken> onPick) {
        plugin.openPicker(num, () -> plugin.getDialog().showCampaignEntityPicker(title, "Selected: ", "Confirm", Global.getSector().getPlayerFaction(), entities, new BaseCampaignEntityPickerListener() {
            @Override
            public void pickedEntity(SectorEntityToken entity) {
                onPick.accept(entity);
                plugin.showBaseOptions();
            }

            @Override
            public void cancelledEntityPicking() {
                plugin.showBaseOptions();
            }

            @Override
            public String getSelectedTextOverrideFor(SectorEntityToken entity) {
                return entity.getName();
            }

            @Override
            public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
                info.addPara("%s in %s", 10f, Misc.getHighlightColor(), entity.getName(), entity.getContainingLocation().getName());
            }

            @Override
            public boolean canConfirmSelection(SectorEntityToken entity) {
                return entity != null && entities.contains(entity);
            }

            @Override
            public Set<StarSystemAPI> getStarSystemsToShow() {
                Set<StarSystemAPI> systems = new HashSet<>();
                if (detachment.getStarSystem() != null) systems.add(detachment.getStarSystem());
                return systems;
            }
        }));
    }

    void showTransportTargetPicker(int num) {
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        FleetStatus status = FleetStatus.getFleetStatus(loadout.id, FleetUtils.getDetachmentCargo(loadout, playerFleet), FleetUtils.getDetachmentMembers(loadout, playerFleet));

        plugin.openPicker(num, () -> plugin.getDialog().showCampaignEntityPicker("Select a planet to transport to", "Selected: ", "Confirm", playerFleet.getFaction(), ShippingTargetHelper.getValidOriginPlanets(), new CampaignEntityPickerListener() {
            @Override
            public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
                MarketAPI m = entity.getMarket();
                return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
            }

            @Override
            public void pickedEntity(SectorEntityToken entity) {
                loadout.transportTargetMarket = entity.getMarket().getId();
                plugin.showBaseOptions();
            }

            @Override
            public void cancelledEntityPicking() {
                plugin.showBaseOptions();
            }

            @Override
            public String getSelectedTextOverrideFor(SectorEntityToken entity) {
                MarketAPI m = entity.getMarket();
                return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
            }

            @Override
            public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
                float opad = 10f;
                MarketAPI m = entity.getMarket();
                info.addPara("Travel to " + m.getName() + " (" + m.getFaction().getDisplayName()
                        + ", size " + m.getSize() + ") and store all ships and cargo in the local storage.", opad);

                float distance = Misc.getDistanceLY(Global.getSector().getPlayerFleet(), entity);
                float requiredFuel = status.requiredFuelPerLY * distance;
                info.addPara("Transport requires " + (int) Math.round(requiredFuel) + " units of fuel.", opad);
            }

            @Override
            public boolean canConfirmSelection(SectorEntityToken entity) {
                return entity != null && entity.getMarket() != null;
            }

            @Override
            public float getFuelColorAlphaMult() {
                return 0;
            }

            @Override
            public float getFuelRangeMult() {
                return 0;
            }

            @Override
            public List<IntelInfoPlugin.ArrowData> getArrows() {
                return null;
            }

            @Override
            public List<MarkerData> getMarkers() {
                return null;
            }

            @Override
            public Set<StarSystemAPI> getStarSystemsToShow() {
                return null;
            }
        }));
    }
}
