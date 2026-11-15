package indevo.dialogue.research.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.research.ResearchProjectTemplateRepo;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Map;

public class ResearchProjectDialoguePlugin extends BaseCommandPlugin implements InteractionDialogPlugin {

    private enum Option {
        RETURN
    }

    protected SectorEntityToken entity;
    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;

    protected final ResearchView view = new ResearchView();
    protected ResearchProjectPanel panel;

    protected String rewardProjId = null;
    protected CargoAPI rewardsCargo = null;
    protected CargoAPI consumedCargo = null;

    private static final String DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY = "$IndEvo_tempDialogueInstance";

    public static final Logger log = Global.getLogger(ResearchProjectDialoguePlugin.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        if (dialog == null) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        entity = dialog.getInteractionTarget();
        originalPlugin = dialog.getPlugin();
        dialog.setPlugin(this);

        dialog.setPromptText("Select an Option");

        init(dialog);
        return true;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        Global.getSector().getMemoryWithoutUpdate().set(DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY, this, 0f);
        panel = new ResearchProjectPanel(this, view);
        panel.show(dialog);
        displayDefaultOptions();
    }

    public void displayDefaultOptions() {
        addTooltip(dialog.getTextPanel());

        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        opts.addOption("Return", Option.RETURN);
        opts.setShortcut(Option.RETURN, Keyboard.KEY_ESCAPE, false, false, false, true);

        refreshCustomPanel();
    }

    public void refreshCustomPanel() {
        if (panel != null) panel.refresh();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        OptionPanelAPI opts = dialog.getOptionPanel();

        opts.clearOptions();

        Option option = Option.valueOf(optionData.toString());

        switch (option) {
            case RETURN:
                returnToMenu();
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + option);
        }
    }


    public InteractionDialogAPI getDialog() {
        return dialog;
    }

    public static ResearchProjectDialoguePlugin getCurrentDialoguePlugin() {
        return (ResearchProjectDialoguePlugin) Global.getSector().getMemoryWithoutUpdate().get(DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY);
    }

    private void addTooltip(TextPanelAPI panel) {
        panel.clear();

        panel.addPara("Arriving at the front desk, you are greeted by a secretary and handed a few folders.");
        int numOpen = ResearchProjectTemplateRepo.getNumProjectsPlayerCanContribute();
        if (numOpen > 0)
            panel.addPara("At a glance, are currently " + numOpen + " research projects that you could contribute to.");
        else panel.addPara("There does not appear to be any ongoing project worth your time.");

        panel.setFontSmallInsignia();
        panel.addParagraph("-----------------------------------------------------------------------------", Misc.getGrayColor());

        panel.addPara("You can donate certain items to research projects, causing them to advance.\n" +
                "Once a project is finished, you will receive rewards, which can range from blueprints to unique weapons.", Misc.getGrayColor());
        panel.addPara("");
        panel.addPara("You will be notified when new projects become available.", Misc.getGrayColor());

        panel.addParagraph("-----------------------------------------------------------------------------", Misc.getGrayColor());
        panel.setFontInsignia();

        if (rewardProjId != null) {
            ResearchProject project = ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(rewardProjId);
            TooltipMakerAPI rewardsToolTip = panel.beginTooltip();
            rewardsToolTip.addSectionHeading(project.getName() + " - Rewards", Alignment.MID, 10f);
            project.addTooltipOutputOnCompletion(rewardsToolTip);
            panel.addTooltip();

            if (consumedCargo != null) for (CargoStackAPI stack : consumedCargo.getStacksCopy()) {
                AddRemoveCommodity.addStackLossText(stack, panel);
            }

            if (rewardsCargo != null) for (CargoStackAPI stack : rewardsCargo.getStacksCopy()) {
                AddRemoveCommodity.addStackGainText(stack, panel);
            }

        }
    }

    public static float getQuantity(CargoAPI cargo, RequiredItem item) {
        return getQuantity(cargo, item.type, item.id);
    }

    public static float getQuantity(CargoAPI cargo, CargoAPI.CargoItemType type, String itemId) {
        float total = 0f;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack.getType() == type && itemId.equals(getStackItemId(stack))) total += stack.getSize();
        }

        return total;
    }

    public static String getStackItemId(CargoStackAPI stack) {
        switch (stack.getType()) {
            case RESOURCES:
                return stack.getCommodityId();
            case WEAPONS:
                return stack.getWeaponSpecIfWeapon().getWeaponId();
            case FIGHTER_CHIP:
                return stack.getFighterWingSpecIfWing().getId();
            case SPECIAL:
                return stack.getSpecialDataIfSpecial().getId();
            default:
                return null;
        }
    }

    private void returnToMenu() {
        dialog.getTextPanel().clear();

        dialog.setPlugin(originalPlugin);
        //new ShowDefaultVisual().execute(null, dialog, MiscIE.tokenize(""), memoryMap);
        memoryMap.get(MemKeys.LOCAL).set("$option", "gaMeetingEnd", 0f);
        FireAll.fire(null, dialog, memoryMap, "DialogOptionSelected");
    }

    public void setProjectIdForRewards(String id, CargoAPI cargo, CargoAPI consumed) {
        this.rewardsCargo = cargo.createCopy();
        this.consumedCargo = consumed;
        this.rewardProjId = id;

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