package indevo.industries.courierport.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.dialogue.sidepanel.VisualCustomPanel;
import indevo.industries.courierport.ShippingContractMemory;
import indevo.industries.salvageyards.rules.IndEvo_InitSYCustomProductionDiag;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class CourierPortDialoguePlugin extends BaseCommandPlugin implements InteractionDialogPlugin {

    //add
    // - New Contract
    // - Manage Contracts
    // - Return

    //either will display a custom panel on the right
    //that panes will

    private enum Option {
        RETURN,
        NEW_CONTRACT,
        MANAGE_CONTRACTS
    }

    protected SectorEntityToken entity;
    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;

    public Pair<String, Color> extraMessage = null;

    private static final String DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY = "$IndEvo_tempDialogueInstance";

    public static final Logger log = Global.getLogger(CourierPortDialoguePlugin.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (!(dialog instanceof IndEvo_InitSYCustomProductionDiag)) {
            this.memoryMap = memoryMap;
            this.originalPlugin = dialog.getPlugin();
            dialog.setPlugin(this);
        }

        init(dialog);
        return true;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.entity = dialog.getInteractionTarget();

        dialog.setPromptText("Select an Option");

        Global.getSector().getMemoryWithoutUpdate().set(DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY, this, 0f);
        VisualCustomPanel.createPanel(dialog, true);
        if (!ShippingContractMemory.getContractList().isEmpty()) addManageContractSidePanel();

        displayDefaultOptions();
    }

    public static void reload() {
        getCurrentDialoguePlugin().displayDefaultOptions();
    }

    public void displayDefaultOptions() {
        addTooltip(dialog.getTextPanel());

        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        opts.addOption("New Contract", Option.NEW_CONTRACT);
        opts.addOption("Manage Contracts", Option.MANAGE_CONTRACTS);
        opts.setEnabled(Option.MANAGE_CONTRACTS, !ShippingContractMemory.getContractList().isEmpty());

        opts.addOption("Return", Option.RETURN);
        opts.setShortcut(Option.RETURN, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    public void addNewContractSidePanel() {
        new ContractSidePanelCreator().showPanel(dialog, null);
    }


    public void addManageContractSidePanel() {
        new ContractListSidePanelCreator().showPanel(dialog);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        Option option = Option.valueOf(optionData.toString());

        switch (option) {
            case RETURN:
                ContractSidePanelCreator.clearBackup();
                VisualCustomPanel.clearPanel();
                returnToMenu();
                break;
            case NEW_CONTRACT:
                addNewContractSidePanel();
                reload();
                break;
            case MANAGE_CONTRACTS:
                addManageContractSidePanel();
                reload();
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + option);
        }
    }

    public static CourierPortDialoguePlugin getCurrentDialoguePlugin() {
        return (CourierPortDialoguePlugin) Global.getSector().getMemoryWithoutUpdate().get(DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY);
    }

    private void addTooltip(TextPanelAPI panel) {
        panel.clear();

        if (!entity.getTags().contains(Tags.COMM_RELAY)) {
            panel.addPara("The atmosphere here is markedly different from a usual spaceport. " +
                    "The offices are better kept, there is more activity in the docks and the overall attitude seems " +
                    "geared towards productivity rather than just passing the time.");
            panel.addPara("An assistant quickly welcomes you into the office of your associate before you can get a better look at the containers in the closest bay.");
        }

        int numOpen = ShippingContractMemory.getContractList().size();

        if (numOpen > 0)
            panel.addPara("There are currently " + numOpen + " ongoing shipping contracts.");
        else panel.addPara("There do not appear to be any ongoing contracts - you can create a new one at any time.");

        // if (inputProjId == null && rewardProjId == null) {
        panel.setFontSmallInsignia();
        panel.addParagraph("-----------------------------------------------------------------------------", Misc.getGrayColor());

        panel.addPara("Shipping contracts can be specified for any two planets as long as you have a courier port.\n" +
                "Shipment cost will be calculated depending on the actually transported amounts.", Misc.getGrayColor());
        panel.addPara("");
        //panel.addPara("You can change or remove contracts at any time.", MiscIE.getGrayColor());
        panel.addPara("Courier fleets will always reach their destination - no one dares attack them, for they would never be able to ship anything, ever again.", Misc.getGrayColor());

        panel.addParagraph("-----------------------------------------------------------------------------", Misc.getGrayColor());
        panel.setFontInsignia();

        if (extraMessage != null) panel.addPara(extraMessage.one, extraMessage.two);
    }

    private void returnToMenu() {
        VisualCustomPanel.clearPanel();
        dialog.getTextPanel().clear();

        dialog.setPlugin(originalPlugin);

        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);

        if (entity.getTags().contains(Tags.COMM_RELAY)) FireAll.fire(null, dialog, memoryMap, "COB_AddOptions");
        else {
            memoryMap.get(MemKeys.LOCAL).set("$option", "IndEvo_SelectreturnToMenu", 0f);
            FireAll.fire(null, dialog, memoryMap, "DialogOptionSelected");
        }
    }

    public void addExtraMessage(String str, Color color) {
        extraMessage = new Pair<>(str, color);
        addTooltip(dialog.getTextPanel());
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
        return memoryMap;
    }
}