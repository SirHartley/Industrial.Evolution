package com.fs.starfarer.api.splinterFleet.plugins.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEntityPickerListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.ShippingTargetHelper;
import com.fs.starfarer.api.splinterFleet.plugins.FleetUtils;
import com.fs.starfarer.api.splinterFleet.plugins.dialogue.customPanelPlugins.FramedCustomPanelPlugin;
import com.fs.starfarer.api.splinterFleet.plugins.dialogue.customPanelPlugins.InteractionDialogCustomPanelPlugin;
import com.fs.starfarer.api.splinterFleet.plugins.dialogue.customPanelPlugins.NoFrameCustomPanelPlugin;
import com.fs.starfarer.api.splinterFleet.plugins.dialogue.customPanelPlugins.VisualCustomPanel;
import com.fs.starfarer.api.splinterFleet.plugins.fleetAssignmentAIs.DeliverAssignmentAI;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.Behaviour;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.DetachmentMemory;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.LoadoutMemory;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SplinterFleetSidePanelCreator {
    public static final Logger log = Global.getLogger(SplinterFleetSidePanelCreator.class);

    protected static final float PANEL_WIDTH_1 = 240;
    protected static final float PANEL_WIDTH_2 = VisualCustomPanel.PANEL_WIDTH - PANEL_WIDTH_1 - 8;
    protected static final float SHIP_ICON_WIDTH = 48;
    protected static final float ARROW_BUTTON_WIDTH = 20, BUTTON_HEIGHT = 30;
    protected static final float SELECT_BUTTON_WIDTH = 95f;
    protected static final float TEXT_FIELD_WIDTH = 80f;

    public void showPanel(AbilityPanelDialoguePlugin plugin) {
        VisualCustomPanel.createPanel(plugin.getDialog(), true);
        showCustomPanel();
    }
//check Nex_NGCStartFleetOptionsV2

    private void showCustomPanel() {
        float opad = 10f;
        float spad = 3f;

        CustomPanelAPI panel = VisualCustomPanel.getPanel();
        TooltipMakerAPI panelTooltip = VisualCustomPanel.getTooltip();
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

            final FleetStatus status;
            if (detachmentIsActive) status = FleetStatus.getFleetStatus(detachment);
            else status = FleetStatus.getFleetStatus(loadout.id, loadout.targetCargo, ships);

            MarketAPI transportTargetMarket = loadout.transportTargetMarket != null ? Global.getSector().getEconomy().getMarket(loadout.transportTargetMarket) : null;

            float distance = transportTargetMarket != null ? Misc.getDistanceLY(playerFleet, transportTargetMarket.getPrimaryEntity()) : 0f;
            float currentFuel = status.currentFuel;
            float requiredFuel = loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT) ? status.requiredFuelPerLY * distance : status.requiredFuelPerLY * distance * 2.2f;

            List<FleetMemberAPI> fleetMemberlist = new ArrayList<>();
            for (ShipVariantAPI variant : loadout.shipVariantList) {
                for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
                    if (member.getVariant().getHullVariantId().equals(variant.getHullVariantId()) && !fleetMemberlist.contains(member)) {
                        fleetMemberlist.add(member);
                        break;
                    }
                }
            }

            int fleetMembersMatching = fleetMemberlist.size();

            boolean allFleetMembersPresent = fleetMembersMatching >= loadout.shipVariantList.size();
            boolean atLeastOneShipPresent = fleetMembersMatching > 0;
            boolean loadoutReadyForSettings = detachmentIsActive || (!loadout.shipVariantList.isEmpty() && atLeastOneShipPresent);

            panelTooltip.addSectionHeading("Detachment " + i, Alignment.MID, opad);

            CustomPanelAPI loadoutPanel = panel.createCustomPanel(PANEL_WIDTH_1, 50f, new NoFrameCustomPanelPlugin());

            //buttons in a row: Members - Cargo - Behaviour - Spawn/Recall
            TooltipMakerAPI variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);

            String buttonId = "button_members_" + i;

            //members

            prerequisiteForActive = !detachmentIsActive;

            Color baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
            Color bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

            ButtonAPI newLoadoutButton = variableButtonAnchor.addButton("Members", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            InteractionDialogCustomPanelPlugin.ButtonEntry entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                @Override
                public void onToggle() {
                    new TwoFleetFleetMemberPicker(AbilityPanelDialoguePlugin.getCurrentDialoguePlugin(), LoadoutMemory.getLoadout(num)).init();
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            loadoutPanel.addUIElement(variableButtonAnchor).inTL(spad, opad);       //first in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //cargo

            prerequisiteForActive = !detachmentIsActive && loadoutReadyForSettings;

            baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
            bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

            buttonId = "button_cargo_" + i;
            variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            newLoadoutButton = variableButtonAnchor.addButton("Cargo", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                @Override
                public void onToggle() {
                    new TwoFleetCargoPicker(AbilityPanelDialoguePlugin.getCurrentDialoguePlugin(), Global.getSector().getPlayerFleet(), LoadoutMemory.getLoadout(num), true).init();
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //behaviour

            prerequisiteForActive = loadoutReadyForSettings && !detachmentIsDormant;

            baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
            bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

            // "<"
            buttonId = "button_behaviour_<_" + i;
            variableButtonAnchor = loadoutPanel.createUIElement(ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            newLoadoutButton = variableButtonAnchor.addButton("<", buttonId, baseColor, bgColour, ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                @Override
                public void onToggle() {
                    int newIndex = Behaviour.getIndexForBehaviour(LoadoutMemory.getLoadout(num).behaviour);
                    newIndex = newIndex - 1;
                    if (newIndex < 1) newIndex = Behaviour.behaviourIndexMap.size();

                    LoadoutMemory.getLoadout(num).behaviour = Behaviour.getBehaviourForIndex(newIndex);
                    AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
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
            buttonId = "button_behaviour_>_" + i;
            variableButtonAnchor = loadoutPanel.createUIElement(ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            newLoadoutButton = variableButtonAnchor.addButton(">", buttonId, baseColor, bgColour, ARROW_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                @Override
                public void onToggle() {
                    int newIndex = Behaviour.getIndexForBehaviour(LoadoutMemory.getLoadout(num).behaviour);
                    newIndex = newIndex + 1;
                    if (newIndex > Behaviour.behaviourIndexMap.size()) newIndex = 1;

                    LoadoutMemory.getLoadout(num).behaviour = Behaviour.getBehaviourForIndex(newIndex);
                    AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);         //third in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //plot course

            if((loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT) || loadout.behaviour.equals(Behaviour.FleetBehaviour.DELIVER)) && !detachmentIsActive){
                prerequisiteForActive = true;

                baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
                bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

                buttonId = "button_set_target_" + i;
                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Set Target", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                    @Override
                    public void onToggle() {
                        AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().getDialog().showCampaignEntityPicker("Select a planet to transport to", "Selected: ", "Confirm", playerFleet.getFaction(), ShippingTargetHelper.getValidOriginPlanets(), new CampaignEntityPickerListener() {
                            @Override
                            public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
                                MarketAPI m = entity.getMarket();
                                return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
                            }

                            @Override
                            public void pickedEntity(SectorEntityToken entity) {
                                loadout.transportTargetMarket = entity.getMarket().getId();

                                AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                            }

                            @Override
                            public void cancelledEntityPicking() {
                                AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                            }

                            @Override
                            public String getSelectedTextOverrideFor(SectorEntityToken entity) {
                                MarketAPI m = entity.getMarket();
                                int amt = (int) Misc.getStorageCargo(m).getSpaceUsed();
                                return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
                            }

                            @Override
                            public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
                                float opad = 10f;
                                MarketAPI m = entity.getMarket();
                                int amt = (int) Misc.getStorageCargo(m).getSpaceUsed();
                                boolean transport = loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT);

                                if(transport) info.addPara("Travel to " + m.getName() + " (" + m.getFaction().getDisplayName()
                                        + ", size " + m.getSize() + ") and store all ships and cargo in the local storage.", opad);
                                else info.addPara("Travel to " + m.getName() + " (" + m.getFaction().getDisplayName()
                                        + ", size " + m.getSize() + ") and store all cargo in the local storage, then return to the main force.", opad);

                                float distance = Misc.getDistanceLY(playerFleet, entity);
                                float requiredFuel = transport ? status.requiredFuelPerLY * distance : status.requiredFuelPerLY * distance * 2;

                                if (transport) info.addPara("Transport requires " + (int) Math.round(requiredFuel) + " units of fuel.", opad);
                                else info.addPara("Transport and trip back will require " + (int) Math.round(requiredFuel) + " units of fuel.", opad);
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
                        });
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);
                loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
                lastUsedVariableButtonAnchor = variableButtonAnchor;
            } else {
                boolean playerTargetIsDetachment = detachmentIsActive && playerFleet.getInteractionTarget() != null && detachment.getId().equals(playerFleet.getInteractionTarget().getId());
                prerequisiteForActive = detachmentIsActive && !playerTargetIsDetachment;

                baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
                bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

                buttonId = "button_plot_course_" + i;
                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Lay Course", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                    @Override
                    public void onToggle() {
                        Global.getSector().getPlayerFleet().setInteractionTarget(DetachmentMemory.getDetachment(num));
                        AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);
                loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
                lastUsedVariableButtonAnchor = variableButtonAnchor;
            }

            //spawn/recall

            baseColor = Misc.getTextColor();
            boolean detachmentSmallerThanFleet = loadout.shipVariantList.size() < playerFleet.getFleetData().getNumMembers();

            prerequisiteForActive = loadoutReadyForSettings && !detachmentIsDormant;

            if (isDetachmentReturning) {
                bgColour = prerequisiteForActive ? new Color(200, 160, 10, 255) : Misc.getGrayColor();
                buttonId = "button_cancel_recall_" + i;
                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Cancel", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                    @Override
                    public void onToggle() {
                        CampaignFleetAPI fleet = DetachmentMemory.getDetachment(num);
                        Behaviour.setReturning(fleet, false);
                        AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                    }
                };

            } else if (detachmentIsActive) {
                bgColour = prerequisiteForActive ? new Color(160, 30, 20, 255) : Misc.getGrayColor();
                buttonId = "button_recall_" + i;
                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Recall", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                    @Override
                    public void onToggle() {
                        CampaignFleetAPI fleet = DetachmentMemory.getDetachment(num);

                        if (fleet.getBattle() != null && Misc.getDistance(fleet, Global.getSector().getPlayerFleet()) < FleetUtils.MIN_MERGE_DISTANCE_IN_MENU) {
                            Behaviour.setReturning(fleet, true);
                            FleetUtils.mergeDetachment(num);
                        } else {
                            Behaviour.setReturning(fleet, true);
                        }

                        AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                    }
                };
            } else {
                boolean transportRequirements = (!loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT) && !loadout.behaviour.equals(Behaviour.FleetBehaviour.DELIVER)) || (transportTargetMarket != null && currentFuel > requiredFuel);

                prerequisiteForActive = loadoutReadyForSettings
                        && detachmentSmallerThanFleet
                        && !loadout.targetCargo.isEmpty()
                        && transportRequirements
                        && !detachmentIsDormant
                        && status.currentSupplies >= (status.suppliesToRecover + status.requiredSuppliesPerMonth);

                bgColour = prerequisiteForActive ? new Color(50, 130, 0, 255) : Misc.getGrayColor();

                buttonId = "button_spawn_" + i;
                variableButtonAnchor = loadoutPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                newLoadoutButton = variableButtonAnchor.addButton("Spawn", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
                newLoadoutButton.setEnabled(prerequisiteForActive);
                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                    @Override
                    public void onToggle() {
                        FleetUtils.createAndSpawnFleet(LoadoutMemory.getLoadout(num), num);
                        AbilityPanelDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                    }
                };
            }

            VisualCustomPanel.getPlugin().addButton(entry);
            loadoutPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);         //last in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            panelTooltip.addCustom(loadoutPanel, opad); //add panel

            //fleet display below it

            CustomPanelAPI fleetPanel = panel.createCustomPanel(VisualCustomPanel.PANEL_WIDTH - 20f, SHIP_ICON_WIDTH + 6f, new FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false));

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
            else if(!detachmentIsActive && status.currentSupplies < (status.suppliesToRecover + status.requiredSuppliesPerMonth)){
                panelTooltip.addPara("Not enough supplies to repair and operate all ships for one month", Misc.getNegativeHighlightColor(), opad);
            } else if (loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT) || loadout.behaviour.equals(Behaviour.FleetBehaviour.DELIVER)) {
                if (transportTargetMarket == null && !detachmentIsActive)
                    panelTooltip.addPara("Specify a target planet for the transport detachment.", Misc.getNegativeHighlightColor(), opad);
                else if (!detachmentIsActive && currentFuel < requiredFuel){
                    if(loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT)) panelTooltip.addPara("Insufficient Fuel to reach the target planet, load at least " + (int) Math.ceil(requiredFuel * 1.10) + " units!", Misc.getNegativeHighlightColor(), opad);
                    else panelTooltip.addPara("Insufficient Fuel to reach target the planet and return, load at least " + (int) Math.ceil(requiredFuel * 1.10) + " units!", Misc.getNegativeHighlightColor(), opad);

                    panelTooltip.addPara("Note that the detachment can still run out of supplies - provide enough for the journey.", Misc.getTextColor(), opad);
                } else if (transportTargetMarket != null) {
                    if(loadout.behaviour.equals(Behaviour.FleetBehaviour.TRANSPORT)){
                        String isOrWill = detachmentIsActive ? "is heading" : "will head";
                        SectorEntityToken dist = detachmentIsActive ? detachment : playerFleet;
                        panelTooltip.addPara("The Detachment " + isOrWill + " to " + transportTargetMarket.getName() + " in the " + transportTargetMarket.getStarSystem().getName() + " (" + (int) Math.floor(Misc.getDistanceLY(dist, transportTargetMarket.getPrimaryEntity())) + " LY)", Misc.getHighlightColor(), opad);

                    } else {
                        String isOrWill = detachmentIsActive ? "is heading" : "will head";
                        SectorEntityToken dist = detachmentIsActive ? detachment : playerFleet;
                        boolean hasDelivered = false;

                        if(detachmentIsActive){
                            DeliverAssignmentAI ai = (DeliverAssignmentAI) FleetUtils.getAssignmentAI(DetachmentMemory.getDetachment(i));
                            if (ai != null) hasDelivered = ai.cargoTransferScript.finished;
                        }

                        if(!hasDelivered) panelTooltip.addPara("The Detachment " + isOrWill + " to " + transportTargetMarket.getName() + " in the " + transportTargetMarket.getStarSystem().getName() + " (" + (int) Math.floor(Misc.getDistanceLY(dist, transportTargetMarket.getPrimaryEntity())) + " LY)", Misc.getHighlightColor(), opad);
                        else panelTooltip.addPara("The Detachment has delivered its cargo and is %s.", opad,Misc.getHighlightColor(), "heading back to the main force");
                    }
                }
            }

            if (!allFleetMembersPresent && !detachmentIsActive && loadoutReadyForSettings)
                panelTooltip.addPara("Some ships are unavailable, detachment will be spawned without them.", Misc.getHighlightColor(), spad);
            else if (!detachmentIsActive && !loadout.shipVariantList.isEmpty() && fleetMembersMatching <= 0)
                panelTooltip.addPara("None of the ships specified in the loadout are present in the main fleet.", Misc.getNegativeHighlightColor(), spad);

            if (detachmentIsActive || !loadout.targetCargo.isEmpty()) {
                if(status.suppliesToRecover > 1) panelTooltip.addPara("Supplies needed to finish repairs: %s", opad, Misc.getHighlightColor(), status.suppliesToRecover + " units");
                panelTooltip.addPara("Estimated operating time: %s", opad, Misc.getHighlightColor(), status.operatingTimeString);
                panelTooltip.addPara("Estimated range: %s", spad, Misc.getHighlightColor(), status.fuelRangeLYString);
            } else if (!loadoutReadyForSettings)
                panelTooltip.addPara("%s for the detachment.", opad, Misc.getHighlightColor(), "Specify fleet members");
            else panelTooltip.addPara("%s for the detachment.", opad, Misc.getHighlightColor(), "Specify cargo");
        }

        VisualCustomPanel.addTooltipToPanel();
    }
}



