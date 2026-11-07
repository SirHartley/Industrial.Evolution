package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEntityPickerListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.dialogue.sidepanel.FramedCustomPanelPlugin;
import indevo.dialogue.sidepanel.RunnableButtonPanelPlugin;
import indevo.industries.courierport.ShippingTargetHelper;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SplinterFleetSidePanelCreator {
    public static final Logger log = Global.getLogger(SplinterFleetSidePanelCreator.class);

    public static final float PANEL_WIDTH = 600;
    public static final float PANEL_HEIGHT = 600;
    protected static final float SHIP_AREA_WIDTH = 352f;
    protected static final float SHIP_ICON_WIDTH = 48;
    protected static final float BUTTON_HEIGHT = 30;
    protected static final float SELECT_BUTTON_WIDTH = 95f;
    protected static final float TOGGLE_WIDTH = 60f;
    protected static final float COLUMN_WIDTH = (PANEL_WIDTH - 30f) / 2f;
    protected static final float COLUMN_HEIGHT = 160f;
    protected static final float MODE_BUTTON_WIDTH = (COLUMN_WIDTH - 10f) / 3f;
    protected static final float MODE_BUTTON_HEIGHT = 24f;
    protected static final float OPAD = 10f, SPAD = 3f;

    protected static final Color SPAWN_COLOUR = new Color(50, 130, 0, 255);
    protected static final Color RECALL_COLOUR = new Color(160, 30, 20, 255);
    protected static final Color CANCEL_COLOUR = new Color(200, 160, 10, 255);

    protected final AbilityPanelDialoguePlugin plugin;

    public SplinterFleetSidePanelCreator(AbilityPanelDialoguePlugin plugin) {
        this.plugin = plugin;
    }

    public void showPanel() {
        CustomPanelAPI panel = plugin.getDialog().getVisualPanel().showCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, new RunnableButtonPanelPlugin());
        TooltipMakerAPI panelTooltip = panel.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, true);

        addCommandSection(panel, panelTooltip);
        for (int num : DetachmentMemory.SLOTS) addSlotSection(panel, panelTooltip, num);

        //added last so the scroller covers everything
        panel.addUIElement(panelTooltip).inTL(0f, 0f);
    }

    private void addCommandSection(CustomPanelAPI panel, TooltipMakerAPI panelTooltip) {
        panelTooltip.addSectionHeading("Command", Alignment.MID, OPAD);

        CustomPanelAPI row = panel.createCustomPanel(PANEL_WIDTH - 10f, COLUMN_HEIGHT, new RunnableButtonPanelPlugin());
        float x = 0f;

        for (int num : DetachmentMemory.SLOTS) {
            CustomPanelAPI column = row.createCustomPanel(COLUMN_WIDTH, COLUMN_HEIGHT, new RunnableButtonPanelPlugin());
            addCommandColumn(column, num);
            row.addComponent(column).inTL(x, 0f);
            x += COLUMN_WIDTH + OPAD;
        }

        panelTooltip.addCustom(row, OPAD);
    }

    private void addCommandColumn(CustomPanelAPI column, int num) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        CampaignFleetAPI detachment = DetachmentMemory.getDetachment(num);
        boolean live = detachment != null;
        boolean dormant = Behaviour.isDormant(detachment);

        TooltipMakerAPI head = column.createUIElement(COLUMN_WIDTH, 40f, false);
        head.addSectionHeading("Detachment " + num, Alignment.MID, 0f);

        if (!live) head.addPara("Not deployed", Misc.getGrayColor(), SPAD);
        else {
            String location = detachment.getContainingLocation() != null ? detachment.getContainingLocation().getName() : "an unknown location";
            String state = FleetStatus.getStateName(Behaviour.getFleetBehaviour(detachment, false));
            Color stateColour = dormant || Behaviour.isStranded(detachment) ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();

            if (state != null) head.addPara("%s in %s", SPAD, new Color[]{stateColour, Misc.getHighlightColor()}, Misc.ucFirst(state), location);
            else head.addPara("Active in %s", SPAD, Misc.getHighlightColor(), location);
        }

        column.addUIElement(head).inTL(0f, 0f);

        float y = head.getHeightSoFar() + OPAD;
        FleetBehaviour current = live ? Behaviour.getFleetBehaviour(detachment, true) : loadout.behaviour;
        int modes = Behaviour.behaviourIndexMap.size();

        for (int i = 1; i <= modes; i++) {
            FleetBehaviour mode = Behaviour.getBehaviourForIndex(i);

            TooltipMakerAPI el = column.createUIElement(MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT, false);
            ButtonAPI button = el.addAreaCheckbox(Misc.ucFirst(mode.toString().toLowerCase()), (Runnable) () -> setMode(num, mode), Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT, 0f);
            button.setChecked(Behaviour.behaviourEquals(mode, current));
            button.setEnabled(!dormant && (!live || Behaviour.canSwitchLiveTo(mode)));
            button.setShowTooltipWhileInactive(true);
            addTooltip(el, Behaviour.behaviourTooltipMap.get(i));

            int index = i - 1;
            column.addUIElement(el).inTL((index % 3) * (MODE_BUTTON_WIDTH + 5f), y + (index / 3) * (MODE_BUTTON_HEIGHT + 5f));
        }

        y += (int) Math.ceil(modes / 3f) * (MODE_BUTTON_HEIGHT + 5f) + 5f;
        List<TooltipMakerAPI> orders = new ArrayList<>();

        if (live) {
            if (Behaviour.isReturning(detachment)) orders.add(createButton(column, "Cancel recall", () -> cancelRecall(num), Misc.getTextColor(), CANCEL_COLOUR, !dormant));
            else orders.add(createButton(column, "Recall", () -> recall(num), Misc.getTextColor(), RECALL_COLOUR, !dormant));

            boolean playerTargetIsDetachment = playerFleet.getInteractionTarget() != null && detachment.getId().equals(playerFleet.getInteractionTarget().getId());
            orders.add(createButton(column, "Lay Course", () -> layCourse(num), Misc.getButtonTextColor(), Misc.getDarkPlayerColor(), !playerTargetIsDetachment));
        } else if (Behaviour.behaviourEquals(loadout.behaviour, FleetBehaviour.TRANSPORT)) {
            orders.add(createButton(column, "Set Target", () -> showTransportTargetPicker(num), Misc.getButtonTextColor(), Misc.getDarkPlayerColor(), true));
        }

        float x = 0f;
        for (TooltipMakerAPI el : orders) {
            column.addUIElement(el).inTL(x, y);
            x += SELECT_BUTTON_WIDTH + OPAD;
        }
    }

    private void addSlotSection(CustomPanelAPI panel, TooltipMakerAPI panelTooltip, int num) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        CampaignFleetAPI detachment = DetachmentMemory.getDetachment(num);

        boolean live = detachment != null;
        boolean dormant = Behaviour.isDormant(detachment);
        boolean take = loadout.takeMode;

        List<FleetMemberAPI> detachmentMembers = live ? detachment.getFleetData().getMembersListCopy() : FleetUtils.getDetachmentMembers(loadout, playerFleet);
        CargoAPI detachmentCargo = live ? detachment.getCargo() : FleetUtils.getDetachmentCargo(loadout, playerFleet);
        FleetStatus status = live ? FleetStatus.getFleetStatus(detachment) : FleetStatus.getFleetStatus(loadout.id, detachmentCargo, detachmentMembers);

        MarketAPI transportTargetMarket = loadout.transportTargetMarket != null ? Global.getSector().getEconomy().getMarket(loadout.transportTargetMarket) : null;
        float distance = transportTargetMarket != null ? Misc.getDistanceLY(playerFleet, transportTargetMarket.getPrimaryEntity()) : 0f;
        float requiredFuel = status.requiredFuelPerLY * distance;
        boolean transport = Behaviour.behaviourEquals(loadout.behaviour, FleetBehaviour.TRANSPORT);

        int matching = FleetUtils.getFleetMembersForLoadout(loadout, playerFleet).size();
        boolean ready = live || !detachmentMembers.isEmpty();
        boolean cargoPicked = !loadout.targetCargo.isEmpty();
        boolean enoughSupplies = status.currentSupplies >= status.suppliesToRecover + status.requiredSuppliesPerMonth;

        boolean canSpawn = !live
                && ready
                && detachmentMembers.size() < playerFleet.getFleetData().getNumMembers()
                && cargoPicked
                && !detachmentCargo.isEmpty()
                && (!transport || (transportTargetMarket != null && status.currentFuel > requiredFuel))
                && !status.isOverCapacity()
                && enoughSupplies;

        panelTooltip.addSectionHeading("Detachment " + num, Alignment.MID, OPAD);

        //buttons in a row: Send - Take - Members - Cargo - Spawn
        CustomPanelAPI row = panel.createCustomPanel(PANEL_WIDTH - 10f, 50f, new RunnableButtonPanelPlugin());
        List<TooltipMakerAPI> buttons = new ArrayList<>();

        buttons.add(createToggle(row, "Send", !take, !live, () -> setTakeMode(num, false), "Pick the ships and cargo that leave as the detachment. You keep the rest."));
        buttons.add(createToggle(row, "Take", take, !live, () -> setTakeMode(num, true), "Pick the ships and cargo you take with you. Everything else stays behind as the detachment. Your flagship always stays with you."));
        buttons.add(createButton(row, "Members", () -> new TwoFleetFleetMemberPicker(plugin, LoadoutMemory.getLoadout(num)).init(), Misc.getButtonTextColor(), Misc.getDarkPlayerColor(), !live));
        buttons.add(createButton(row, "Cargo", () -> new TwoFleetCargoPicker(plugin, Global.getSector().getPlayerFleet(), LoadoutMemory.getLoadout(num), true).init(), Misc.getButtonTextColor(), Misc.getDarkPlayerColor(), !live && ready));
        if (!live) buttons.add(createButton(row, "Spawn", () -> spawn(num), Misc.getTextColor(), SPAWN_COLOUR, canSpawn));

        TooltipMakerAPI previous = null;
        for (TooltipMakerAPI el : buttons) {
            if (previous == null) row.addUIElement(el).inTL(SPAD, OPAD);
            else row.addUIElement(el).rightOfMid(previous, previous == buttons.get(0) ? SPAD : OPAD);
            previous = el;
        }

        panelTooltip.addCustom(row, OPAD);

        //fleet display below it
        List<FleetMemberAPI> ships;
        if (live) ships = detachmentMembers;
        else if (take) {
            ships = FleetUtils.getTakenMembers(loadout, playerFleet);
            panelTooltip.addPara("You take these ships, %s stay behind.", SPAD, Misc.getHighlightColor(), detachmentMembers.size() + "");
        } else ships = FleetUtils.convertToFleetMemberList(loadout.shipVariantList);

        CustomPanelAPI fleetPanel = panel.createCustomPanel(PANEL_WIDTH - 20f, SHIP_ICON_WIDTH + 6f, new FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false));
        TooltipMakerAPI shipHolder = fleetPanel.createUIElement(SHIP_AREA_WIDTH, 0, false);

        int max = Math.min((int) (SHIP_AREA_WIDTH / SHIP_ICON_WIDTH), ships.size());
        shipHolder.addShipList(max, 1, SHIP_ICON_WIDTH, Misc.getBasePlayerColor(), ships.subList(0, max), 0);

        fleetPanel.addUIElement(shipHolder).inTL(1f, 1f);
        panelTooltip.addCustom(fleetPanel, 3);

        //notes
        if (dormant)
            panelTooltip.addPara("!!! The detachment is Dormant and urgently needs your assistance !!!", Misc.getNegativeHighlightColor(), OPAD);
        if (Behaviour.isReturning(detachment))
            panelTooltip.addPara("The detachment is currently returning to the Main Fleet", Misc.getHighlightColor(), OPAD);

        if (!live && take && detachmentMembers.isEmpty())
            panelTooltip.addPara("Leave at least one ship behind as the detachment.", Misc.getNegativeHighlightColor(), OPAD);
        else if (!live && ready && status.isOverCapacity()) {
            String text = take ? "The ships left behind can not carry the rest of the cargo: over %s capacity. Take more with you." : "The ships cannot carry the selected cargo: over %s capacity.";
            panelTooltip.addPara(text, OPAD, Misc.getNegativeHighlightColor(), Misc.getAndJoined(status.getOverCapacityList()));
        } else if (!live && ready && cargoPicked && !enoughSupplies) {
            panelTooltip.addPara("Not enough supplies to repair and operate all ships of the detachment for one month", Misc.getNegativeHighlightColor(), OPAD);
        } else if (transport) {
            if (transportTargetMarket == null && !live)
                panelTooltip.addPara("Specify a target planet for the transport detachment.", Misc.getNegativeHighlightColor(), OPAD);
            else if (!live && status.currentFuel < requiredFuel) {
                panelTooltip.addPara("Insufficient Fuel to reach the target planet, load at least " + (int) Math.ceil(requiredFuel * 1.10) + " units!", Misc.getNegativeHighlightColor(), OPAD);
                panelTooltip.addPara("Note that the detachment can still run out of supplies - provide enough for the journey.", Misc.getTextColor(), OPAD);
            } else if (transportTargetMarket != null) {
                String isOrWill = live ? "is heading" : "will head";
                SectorEntityToken dist = live ? detachment : playerFleet;
                panelTooltip.addPara("The Detachment " + isOrWill + " to " + transportTargetMarket.getName() + " in the " + transportTargetMarket.getStarSystem().getName() + " (" + (int) Math.floor(Misc.getDistanceLY(dist, transportTargetMarket.getPrimaryEntity())) + " LY)", Misc.getHighlightColor(), OPAD);
            }
        }

        if (!live && !take && ready && matching < loadout.shipVariantList.size())
            panelTooltip.addPara("Some ships are unavailable, detachment will be spawned without them.", Misc.getHighlightColor(), SPAD);
        else if (!live && !take && !loadout.shipVariantList.isEmpty() && matching <= 0)
            panelTooltip.addPara("None of the ships specified in the loadout are present in the main fleet.", Misc.getNegativeHighlightColor(), SPAD);

        if (!live && ready && cargoPicked && status.currentFuel < 1)
            panelTooltip.addPara("Without fuel the detachment can not leave this location, or follow you out of it.", Misc.getHighlightColor(), SPAD);

        if (live || (ready && cargoPicked)) {
            if (status.suppliesToRecover > 1)
                panelTooltip.addPara("Supplies needed to finish repairs: %s", OPAD, Misc.getHighlightColor(), status.suppliesToRecover + " units");
            panelTooltip.addPara("Estimated operating time: %s", OPAD, Misc.getHighlightColor(), status.operatingTimeString);
            panelTooltip.addPara("Estimated range: %s", SPAD, Misc.getHighlightColor(), status.fuelRangeLYString);
        } else if (!ready && !take)
            panelTooltip.addPara("%s for the detachment.", OPAD, Misc.getHighlightColor(), "Specify fleet members");
        else if (ready)
            panelTooltip.addPara(take ? "%s you take with you." : "%s for the detachment.", OPAD, Misc.getHighlightColor(), take ? "Specify the cargo" : "Specify cargo");
    }

    private TooltipMakerAPI createButton(CustomPanelAPI owner, String text, Runnable action, Color textColour, Color bgColour, boolean enabled) {
        TooltipMakerAPI el = owner.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
        ButtonAPI button = el.addButton(text, action, enabled ? textColour : Misc.getTextColor(), enabled ? bgColour : Misc.getGrayColor(), Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
        button.setEnabled(enabled);
        return el;
    }

    private TooltipMakerAPI createToggle(CustomPanelAPI owner, String text, boolean checked, boolean enabled, Runnable action, String tooltip) {
        TooltipMakerAPI el = owner.createUIElement(TOGGLE_WIDTH, BUTTON_HEIGHT, false);
        ButtonAPI button = el.addAreaCheckbox(text, action, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), TOGGLE_WIDTH, BUTTON_HEIGHT, 0f);
        button.setChecked(checked);
        button.setEnabled(enabled);
        button.setShowTooltipWhileInactive(true);
        addTooltip(el, tooltip);
        return el;
    }

    private void addTooltip(TooltipMakerAPI el, String text) {
        el.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 300f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(text, 0f);
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
    }

    private void setMode(int num, FleetBehaviour mode) {
        if (DetachmentMemory.isDetachmentActive(num)) Behaviour.changeBehaviourAndUpdateAI(DetachmentMemory.getDetachment(num), mode);
        else LoadoutMemory.getLoadout(num).behaviour = mode;

        plugin.refreshCustomPanel();
    }

    //the stored ships and cargo flip meaning between the modes
    private void setTakeMode(int num, boolean take) {
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        if (loadout.takeMode != take) {
            loadout.takeMode = take;
            loadout.shipVariantList.clear();
            loadout.targetCargo.clear();
        }

        plugin.refreshCustomPanel();
    }

    private void layCourse(int num) {
        Global.getSector().getPlayerFleet().setInteractionTarget(DetachmentMemory.getDetachment(num));
        plugin.refreshCustomPanel();
    }

    private void cancelRecall(int num) {
        Behaviour.setReturning(DetachmentMemory.getDetachment(num), false);
        plugin.refreshCustomPanel();
    }

    private void recall(int num) {
        CampaignFleetAPI fleet = DetachmentMemory.getDetachment(num);
        Behaviour.setReturning(fleet, true);

        if (fleet.getBattle() != null && Misc.getDistance(fleet, Global.getSector().getPlayerFleet()) < FleetUtils.MIN_MERGE_DISTANCE_IN_MENU)
            FleetUtils.mergeDetachment(num);

        plugin.refreshCustomPanel();
    }

    private void spawn(int num) {
        FleetUtils.createAndSpawnFleet(LoadoutMemory.getLoadout(num), num);
        plugin.refreshCustomPanel();
    }

    private void showTransportTargetPicker(int num) {
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        FleetStatus status = FleetStatus.getFleetStatus(loadout.id, FleetUtils.getDetachmentCargo(loadout, playerFleet), FleetUtils.getDetachmentMembers(loadout, playerFleet));

        plugin.getDialog().showCampaignEntityPicker("Select a planet to transport to", "Selected: ", "Confirm", playerFleet.getFaction(), ShippingTargetHelper.getValidOriginPlanets(), new CampaignEntityPickerListener() {
            @Override
            public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
                MarketAPI m = entity.getMarket();
                return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
            }

            @Override
            public void pickedEntity(SectorEntityToken entity) {
                loadout.transportTargetMarket = entity.getMarket().getId();

                plugin.refreshCustomPanel();
            }

            @Override
            public void cancelledEntityPicking() {
                plugin.refreshCustomPanel();
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
        });
    }
}
