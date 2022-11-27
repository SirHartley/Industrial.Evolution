package com.fs.starfarer.api.mobileColony.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.splinterFleet.plugins.FleetUtils;
import com.fs.starfarer.api.splinterFleet.plugins.dialogue.FleetStatus;
import com.fs.starfarer.api.splinterFleet.plugins.fleetAssignmentAIs.BaseSplinterFleetAssignmentAIV2;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.Behaviour;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.DetachmentMemory;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.Map;

import static com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.Behaviour.updateActiveDetachmentBehaviour;

public class MobileColonyInteractionDialoguePlugin implements InteractionDialogPlugin {

    private enum Option {
        MAIN,
        OPEN_CORE,
        REPAIR,
        COMM_DIRECTORY,
        LEAVE
    }

    //move cargo between detachment and fleet
    //merge into main force
    //change behaviour

    protected SectorEntityToken entity;
    protected InteractionDialogAPI dialog;

    protected int detachmentNum = 0;

    protected static final String BEHAVIOUR_ITEM = "behaviourItem_";
    protected int detachmentBehaviouIndex = 0;

    public void printText() {
        TextPanelAPI text = dialog.getTextPanel();

        text.clear();
        text.addPara("Splinter Fleet Control");

        //god I hate UI work
        Behaviour.addBehaviourListTooltip(text);
        FleetStatus.addFleetStatusTooltip(text, (CampaignFleetAPI) entity);
    }

    public void showBaseOptions() {
        printText();
        dialog.getVisualPanel().showFleetInfo(entity.getName(), (CampaignFleetAPI) entity, "Main Force", Global.getSector().getPlayerFleet());

        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        addTooltip(dialog.getTextPanel());

        opts.addOption("Trade or manage the colony", Option.OPEN_CORE);
        //dialog.makeOptionOpenCore(Option.OPEN_CORE, CoreUITabId.OUTPOSTS, CampaignUIAPI.CoreUITradeMode.OPEN);

        //repairs
        //id	trigger	conditions	script	text	options	notes
        //marketAddOptionRepair1	PopulateOptions	$hasMarket
        //$menuState == main
        //$tradeMode == OPEN
        //RepairAvailable
        //RepairNeeded
        //RepairEnoughSupplies	SetTooltip marketRepair "Full repairs require $global.repairSupplyCost supplies. $player.supplies supplies are available."
        //SetTooltipHighlightColors marketRepair buttonShortcut buttonShortcut
        //SetTooltipHighlights marketRepair $global.repairSupplyCost $player.supplies
        //SetShortcut marketRepair "A" true		15:marketRepair:Repair your ships at the dockyard

        opts.addOption("Leave", Option.LEAVE);
        opts.setShortcut(Option.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, false);
    }

    public InteractionDialogAPI getDialog() {
        return dialog;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        Behaviour.log.info("executing");

        this.dialog = dialog;
        this.entity = dialog.getInteractionTarget();

        if (entity instanceof CampaignFleetAPI) {
            detachmentNum = DetachmentMemory.getNumForFleet((CampaignFleetAPI) entity);
            detachmentBehaviouIndex = Behaviour.getIndexForBehaviour(Behaviour.getFleetBehaviour((CampaignFleetAPI) entity, true));
        } else dialog.dismiss();

        showBaseOptions();
    }

    private void addBehaviourOptionPoints() {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        for (int i = 1; i <= Behaviour.behaviourIndexMap.size(); i++) {

            String pre = i == detachmentBehaviouIndex ? "> " : "";
            String post = i == detachmentBehaviouIndex ? " <" : "";

            opts.addOption(pre + Misc.ucFirst(Behaviour.getBehaviourForIndex(i).toString().toLowerCase()) + post, BEHAVIOUR_ITEM + i, Behaviour.getColourForBehaviour(Behaviour.getBehaviourForIndex(i)), Behaviour.behaviourTooltipMap.get(i));
        }

        opts.addOption("Return", Option.MAIN);
        opts.setShortcut(Option.MAIN, Keyboard.KEY_ESCAPE, false, false, false, false);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        addTooltip(dialog.getTextPanel());

        String optionString = optionData.toString();

        if (optionString.startsWith(BEHAVIOUR_ITEM)) {
            detachmentBehaviouIndex = (int) Integer.parseInt(optionString.substring(BEHAVIOUR_ITEM.length()));

            Behaviour.changeBehaviourAndUpdateAI((CampaignFleetAPI) entity, Behaviour.getBehaviourForIndex(detachmentBehaviouIndex));
            addBehaviourOptionPoints();

        } else switch (optionString) {
            case "LEAVE":
                //for some stupid reason both fleets start battling so we force em to... not
                CampaignFleetAPI fleet = (CampaignFleetAPI) entity;
                fleet.setBattle(null);
                Global.getSector().getPlayerFleet().setBattle(null);

                //if(((CampaignFleetAPI) entity).getCargo().getCommodityQuantity(Commodities.SUPPLIES) < 1) Behaviour.setFleetBehaviourOverride((CampaignFleetAPI) entity, Behaviour.FleetBehaviour.DORMANT);
                if(fleet.getCargo().getSupplies() < 1) fleet.getCargo().addCommodity(Commodities.SUPPLIES, 1);

                Behaviour.clearBehaviourOverride(fleet);
                BaseSplinterFleetAssignmentAIV2 ai = (BaseSplinterFleetAssignmentAIV2) FleetUtils.getAssignmentAI(fleet);
                if (ai != null) ai.advance(0f);

                updateActiveDetachmentBehaviour();

                dialog.dismiss();
                break;
            case "MERGE":
                Behaviour.setReturning(detachmentNum, true);
                FleetUtils.mergeDetachment(detachmentNum);
                updateActiveDetachmentBehaviour();
                dialog.dismiss();
                break;
            default:
                showBaseOptions();
                break;
        }
    }

    private void addTooltip(TextPanelAPI panel) {
        CampaignFleetAPI f = DetachmentMemory.getDetachment(detachmentNum);
        dialog.getVisualPanel().showFleetInfo(f.getName(), f, null, null);
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {

    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
