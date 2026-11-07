package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEntityPickerListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.dialogue.sidepanel.FramedCustomPanelPlugin;
import indevo.dialogue.sidepanel.RunnableButtonPanelPlugin;
import indevo.industries.courierport.ShippingTargetHelper;
import indevo.utils.helper.MiscIE;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class SplinterFleetSidePanelCreator {
    public static final Logger log = Global.getLogger(SplinterFleetSidePanelCreator.class);

    public static final float PANEL_WIDTH = 600;
    public static final float PANEL_HEIGHT = 600;
    protected static final float PANEL_WIDTH_1 = 240;
    protected static final float PANEL_WIDTH_2 = PANEL_WIDTH - PANEL_WIDTH_1 - 8;
    protected static final float SHIP_ICON_WIDTH = 48;
    protected static final float ARROW_BUTTON_WIDTH = 20, BUTTON_HEIGHT = 30;
    protected static final float SELECT_BUTTON_WIDTH = 95f;
    protected static final float TEXT_FIELD_WIDTH = 80f;

    protected final AbilityPanelDialoguePlugin plugin;

    public SplinterFleetSidePanelCreator(AbilityPanelDialoguePlugin plugin) {
        this.plugin = plugin;
    }

    public void showPanel() {
        float opad = 10f;
        float spad = 3f;

        CustomPanelAPI panel = plugin.getDialog().getVisualPanel().showCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, new RunnableButtonPanelPlugin());
        TooltipMakerAPI panelTooltip = panel.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, true);
        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        TooltipMakerAPI lastUsedVariableButtonAnchor;

        for (int i : Arrays.asList(1, 2, 3)) {
            final Integer num = i;

            final LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(i);
            CampaignFleetAPI detachment = DetachmentMemory.getDetachment(i);

            boolean detachmentIsActive = DetachmentMemory.isDetachmentActive(i);
            boolean isDetachmentReturning = Behaviour.isReturning(detachment);
            boolean detachmentIsDormant = Behaviour.isDormant(detachment);
            boolean prerequisiteForActive = true;

            List<FleetMemberAPI> ships = FleetUtils.convertToFleetMemberList(loadout.shipVariantList);
            List<FleetMemberAPI> availableLoadoutMembers = FleetUtils.getFleetMembersForLoadout(loadout, playerFleet);

            final FleetStatus status;
            if (detachmentIsActive) status = FleetStatus.getFleetStatus(detachment);
            else status = FleetStatus.getFleetStatus(loadout.id, loadout.targetCargo, availableLoadoutMembers);

            MarketAPI transportTargetMarket = loadout.transportTargetMarket != null ? Global.getSector().getEconomy().getMarket(loadout.transportTargetMarket) : null;

            float distance = transportTargetMarket != null ? Misc.getDistanceLY(playerFleet, transportTargetMarket.getPrimaryEntity()) : 0f;
            float currentFuel = status.currentFuel;
            float requiredFuel = status.requiredFuelPerLY * distance;

            int fleetMembersMatching = availableLoadoutMembers.size();

            boolean allFleetMembersPresent = fleetMembersMatching >= loadout.shipVariantList.size();
            boolean atLeastOneShipPresent = fleetMembersMatching > 0;
            boolean loadoutReadyForSettings = detachmentIsActive || (!loadout.shipVariantList.isEmpty() && atLeastOneShipPresent);

            panelTooltip.addSectionHeading("Detachment " + i, Alignment.MID, opad);

            CustomPanelAPI loadoutPanel = panel.createCustomPanel(PANEL_WIDTH_1, 50f, new RunnableButtonPanelPlugin());

            //buttons in a row: Members - Cargo - Behaviour - Spawn/Recall
            TooltipMakerAPI variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);


            //members

            prerequisiteForActive = !detachmentIsActive;

            Color baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
            Color bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

            ButtonAPI newLoadoutButton = variableButtonAnchor.addButton("Members", (Runnable) () -> new TwoFleetFleetMemberPicker(plugin, LoadoutMemory.getLoadout(num)).init(), baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            loadoutPanel.addUIElement(variableButtonAnchor).inTL(spad, opad);       //first in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //cargo

            prerequisiteForActive = !detachmentIsActive && loadoutReadyForSettings;

            baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
            bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

            variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            newLoadoutButton = variableButtonAnchor.addButton("Cargo", (Runnable) () -> new TwoFleetCargoPicker(plugin, Global.getSector().getPlayerFleet(), LoadoutMemory.getLoadout(num), true).init(), baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //behaviour

            prerequisiteForActive = loadoutReadyForSettings && !detachmentIsDormant;

            baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
            bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

            // "<"
            variableButtonAnchor = loadoutPanel.createUIElement(ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            newLoadoutButton = variableButtonAnchor.addButton("<", (Runnable) () -> cycleBehaviour(num, -1), baseColor, bgColour, ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);         //third in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //behaviour text
            TooltipMakerAPI desc = loadoutPanel.createUIElement(TEXT_FIELD_WIDTH, BUTTON_HEIGHT, false);
            LabelAPI label = desc.addPara(Misc.ucFirst(loadout.behaviour.toString().toLowerCase()), loadoutReadyForSettings ? Behaviour.getColourForBehaviour(loadout.behaviour) : Misc.getGrayColor(), 0f);

            label.getPosition().inTMid(6f);
            label.setAlignment(Alignment.MID);
            label.getPosition().setXAlignOffset(6f);

            loadoutPanel.addUIElement(desc).rightOfMid(lastUsedVariableButtonAnchor, opad);
            lastUsedVariableButtonAnchor = desc;

            // ">"
            variableButtonAnchor = loadoutPanel.createUIElement(ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            newLoadoutButton = variableButtonAnchor.addButton(">", (Runnable) () -> cycleBehaviour(num, 1), baseColor, bgColour, ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);         //third in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //plot course

            if (loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT) && !detachmentIsActive) {
                prerequisiteForActive = true;

                baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
                bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Set Target", (Runnable) () -> showTransportTargetPicker(loadout, status), baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
                loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
                lastUsedVariableButtonAnchor = variableButtonAnchor;
            } else {
                boolean playerTargetIsDetachment = detachmentIsActive && playerFleet.getInteractionTarget() != null && detachment.getId().equals(playerFleet.getInteractionTarget().getId());
                prerequisiteForActive = detachmentIsActive && !playerTargetIsDetachment;

                baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
                bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Lay Course", (Runnable) () -> layCourse(num), baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
                loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
                lastUsedVariableButtonAnchor = variableButtonAnchor;
            }

            //spawn/recall

            baseColor = Misc.getTextColor();
            boolean detachmentSmallerThanFleet = loadout.shipVariantList.size() < playerFleet.getFleetData().getNumMembers();

            prerequisiteForActive = loadoutReadyForSettings && !detachmentIsDormant;

            if (isDetachmentReturning) {
                bgColour = prerequisiteForActive ? new Color(200, 160, 10, 255) : Misc.getGrayColor();
                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Cancel", (Runnable) () -> cancelRecall(num), baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);

            } else if (detachmentIsActive) {
                bgColour = prerequisiteForActive ? new Color(160, 30, 20, 255) : Misc.getGrayColor();
                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Recall", (Runnable) () -> recall(num), baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
            } else {
                boolean transportRequirements = !loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT) || (transportTargetMarket != null && currentFuel > requiredFuel);

                prerequisiteForActive = loadoutReadyForSettings
                        && detachmentSmallerThanFleet
                        && !loadout.targetCargo.isEmpty()
                        && transportRequirements
                        && !detachmentIsDormant
                        && !status.isOverCapacity()
                        && status.currentSupplies >= (status.suppliesToRecover + status.requiredSuppliesPerMonth);

                bgColour = prerequisiteForActive ? new Color(50, 130, 0, 255) : Misc.getGrayColor();

                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Spawn", (Runnable) () -> spawn(num), baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
            }
            loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);         //last in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            panelTooltip.addCustom(loadoutPanel, opad); //add panel

            //fleet display below it

            CustomPanelAPI fleetPanel = panel.createCustomPanel(PANEL_WIDTH - 20f, SHIP_ICON_WIDTH + 6f, new FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false));

            float shipAreaWidth = PANEL_WIDTH_2;
            TooltipMakerAPI shipHolder = fleetPanel.createUIElement(shipAreaWidth, 0, false);

            int max = Math.min((int) (shipAreaWidth / SHIP_ICON_WIDTH), ships.size());
            ships = ships.subList(0, max);
            shipHolder.addShipList(max, 1, SHIP_ICON_WIDTH, Misc.getBasePlayerColor(), ships, 0);

            fleetPanel.addUIElement(shipHolder).inTL(1f, 1f); //add it to top left of fleet panel (?)
            panelTooltip.addCustom(fleetPanel, 3); //add fleet panel

            //cargo info panel and notification text if required

            if (detachmentIsDormant)
                panelTooltip.addPara("!!! The detachment is Dormant and urgently needs your assistance !!!", Misc.getNegativeHighlightColor(), opad);
            if (isDetachmentReturning)
                panelTooltip.addPara("The detachment is currently returning to the Main Fleet", Misc.getHighlightColor(), opad);

            if (!detachmentIsActive && !detachmentSmallerThanFleet)
                panelTooltip.addPara("At least one ship must stay with the main fleet!", Misc.getNegativeHighlightColor(), opad);
            else if (!detachmentIsActive && status.isOverCapacity())
                panelTooltip.addPara("The ships cannot carry the selected cargo: over %s capacity.", opad, Misc.getNegativeHighlightColor(), Misc.getAndJoined(status.getOverCapacityList()));
            else if (!detachmentIsActive && status.currentSupplies < (status.suppliesToRecover + status.requiredSuppliesPerMonth)) {
                panelTooltip.addPara("Not enough supplies to repair and operate all ships for one month", Misc.getNegativeHighlightColor(), opad);
            } else if (loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT)) {
                if (transportTargetMarket == null && !detachmentIsActive)
                    panelTooltip.addPara("Specify a target planet for the transport detachment.", Misc.getNegativeHighlightColor(), opad);
                else if (!detachmentIsActive && currentFuel < requiredFuel) {
                    panelTooltip.addPara("Insufficient Fuel to reach the target planet, load at least " + (int) Math.ceil(requiredFuel * 1.10) + " units!", Misc.getNegativeHighlightColor(), opad);

                    panelTooltip.addPara("Note that the detachment can still run out of supplies - provide enough for the journey.", Misc.getTextColor(), opad);
                } else if (transportTargetMarket != null) {
                    String isOrWill = detachmentIsActive ? "is heading" : "will head";
                    SectorEntityToken dist = detachmentIsActive ? detachment : playerFleet;
                    panelTooltip.addPara("The Detachment " + isOrWill + " to " + transportTargetMarket.getName() + " in the " + transportTargetMarket.getStarSystem().getName() + " (" + (int) Math.floor(Misc.getDistanceLY(dist, transportTargetMarket.getPrimaryEntity())) + " LY)", Misc.getHighlightColor(), opad);
                }
            }

            if (!allFleetMembersPresent && !detachmentIsActive && loadoutReadyForSettings)
                panelTooltip.addPara("Some ships are unavailable, detachment will be spawned without them.", Misc.getHighlightColor(), spad);
            else if (!detachmentIsActive && !loadout.shipVariantList.isEmpty() && fleetMembersMatching <= 0)
                panelTooltip.addPara("None of the ships specified in the loadout are present in the main fleet.", Misc.getNegativeHighlightColor(), spad);

            if (!detachmentIsActive && loadoutReadyForSettings && status.currentFuel < 1)
                panelTooltip.addPara("Without fuel the detachment can not leave this location, or follow you out of it.", Misc.getHighlightColor(), spad);

            if (detachmentIsActive || !loadout.targetCargo.isEmpty()) {
                if (status.suppliesToRecover > 1)
                    panelTooltip.addPara("Supplies needed to finish repairs: %s", opad, Misc.getHighlightColor(), status.suppliesToRecover + " units");
                panelTooltip.addPara("Estimated operating time: %s", opad, Misc.getHighlightColor(), status.operatingTimeString);
                panelTooltip.addPara("Estimated range: %s", spad, Misc.getHighlightColor(), status.fuelRangeLYString);
            } else if (!loadoutReadyForSettings)
                panelTooltip.addPara("%s for the detachment.", opad, Misc.getHighlightColor(), "Specify fleet members");
            else panelTooltip.addPara("%s for the detachment.", opad, Misc.getHighlightColor(), "Specify cargo");
        }

        //added last so the scroller covers everything
        panel.addUIElement(panelTooltip).inTL(0f, 0f);
    }

    private void cycleBehaviour(int num, int step) {
        LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(num);
        boolean live = DetachmentMemory.isDetachmentActive(num);
        Behaviour.FleetBehaviour next = Behaviour.getBehaviourForIndex(Behaviour.getNextIndex(Behaviour.getIndexForBehaviour(loadout.behaviour), step, live));

        if (live) Behaviour.changeBehaviourAndUpdateAI(DetachmentMemory.getDetachment(num), next);
        else loadout.behaviour = next;

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

    private void showTransportTargetPicker(LoadoutMemory.Loadout loadout, FleetStatus status) {
        plugin.getDialog().showCampaignEntityPicker("Select a planet to transport to", "Selected: ", "Confirm", Global.getSector().getPlayerFleet().getFaction(), ShippingTargetHelper.getValidOriginPlanets(), new CampaignEntityPickerListener() {
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
                int amt = (int) MiscIE.getStorageCargo(m).getSpaceUsed();
                return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
            }

            @Override
            public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
                float opad = 10f;
                MarketAPI m = entity.getMarket();
                int amt = (int) MiscIE.getStorageCargo(m).getSpaceUsed();
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



