package industrial_evolution.splinterFleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import industrial_evolution.splinterFleet.dialogue.customPanelPlugins.VisualCustomPanel;
import industrial_evolution.splinterFleet.fleetManagement.Behaviour;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lwjgl.input.Keyboard;

import java.util.Map;

import static industrial_evolution.splinterFleet.fleetManagement.Behaviour.updateActiveDetachmentBehaviour;

public class AbilityPanelDialoguePlugin implements InteractionDialogPlugin, SplinterFleetDialoguePluginAPI {

    public static final String DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY = "$SplinterFleet_DialoguePlugin";

    public static AbilityPanelDialoguePlugin getCurrentDialoguePlugin() {
        return (AbilityPanelDialoguePlugin) Global.getSector().getMemoryWithoutUpdate().get(DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY);
    }

    private enum Option {
        MAIN,
        MAIN_ABILITY_ACTIVE,
        CLOSE_PANEL,
    }

    //ability can be called in hyperspace but will only allow ABANDON_FLEET

    protected InteractionDialogAPI dialog;
    protected TextPanelAPI text;
    protected OptionPanelAPI options;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.options = dialog.getOptionPanel();
        this.text = dialog.getTextPanel();

        Global.getSector().getMemoryWithoutUpdate().set(DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY, this, 0f);

        VisualCustomPanel.createPanel(dialog, true);

        showBaseOptions();
    }

    public InteractionDialogAPI getDialog() {
        return dialog;
    }

    public void showBaseOptions() {
        optionSelected(null, Option.MAIN);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        options.clearOptions();
        Option opt = (Option) optionData;

        switch (opt) {
            case MAIN:
                refreshCustomPanel();
                displayAbilityInactiveDialogueText();

                options.addOption("Return", Option.CLOSE_PANEL);
                options.setShortcut(Option.CLOSE_PANEL, Keyboard.KEY_ESCAPE, false, false, false, false);
                break;
            case CLOSE_PANEL:
                VisualCustomPanel.clearPanel();
                updateActiveDetachmentBehaviour();
                dialog.dismiss();
                break;
        }
    }

    private void displayAbilityInactiveDialogueText() {
        float opad = 10f;
        float spad = 3f;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        text.clear();
        text.addPara("Split off part of your fleet into a detachment. You will remain in control of your main force, while the detachment will follow the behaviour you specified.");

        Behaviour.addBehaviourListTooltip(text);

        TooltipMakerAPI tooltip = text.beginTooltip();
        tooltip.addSectionHeading("Main Force", Alignment.MID, opad);

        int colAmt = 10;
        int rowAmt = (int) Math.ceil(playerFleet.getFleetData().getNumMembers() / 10f);

        tooltip.addShipList(colAmt, rowAmt, 50f, playerFleet.getFaction().getBaseUIColor(), playerFleet.getFleetData().getMembersListCopy(), opad);
        text.addTooltip();
    }


    public void refreshCustomPanel() {
        new SplinterFleetSidePanelCreator().showPanel(this);
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
