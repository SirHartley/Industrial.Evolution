package indevo.WIP.mobilecolony.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.Map;

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
        this.dialog = dialog;
        this.entity = dialog.getInteractionTarget();
        // TODO: 27/01/2023 get colony memory data for fleet

        showBaseOptions();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        addTooltip(dialog.getTextPanel());

        String optionString = optionData.toString();

        if (optionString.startsWith(BEHAVIOUR_ITEM)) {
            detachmentBehaviouIndex = (int) Integer.parseInt(optionString.substring(BEHAVIOUR_ITEM.length()));


        } else switch (optionString) {
            case "LEAVE":
                //for some stupid reason both fleets start battling so we force em to... not
                CampaignFleetAPI fleet = (CampaignFleetAPI) entity;
                fleet.setBattle(null);
                Global.getSector().getPlayerFleet().setBattle(null);

                //if(((CampaignFleetAPI) entity).getCargo().getCommodityQuantity(Commodities.SUPPLIES) < 1) Behaviour.setFleetBehaviourOverride((CampaignFleetAPI) entity, Behaviour.FleetBehaviour.DORMANT);
                if(fleet.getCargo().getSupplies() < 1) fleet.getCargo().addCommodity(Commodities.SUPPLIES, 1);
                dialog.dismiss();

                break;
            case "MERGE":

                dialog.dismiss();
                break;
            default:
                showBaseOptions();
                break;
        }
    }

    private void addTooltip(TextPanelAPI panel) {
        //CampaignFleetAPI f = DetachmentMemory.getDetachment(detachmentNum);
        //dialog.getVisualPanel().showFleetInfo(f.getName(), f, null, null);
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
