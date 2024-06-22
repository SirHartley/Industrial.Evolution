package indevo.dialogue.research;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.VisualCustomPanel;
import indevo.industries.salvageyards.rules.IndEvo_InitSYCustomProductionDiag;
import indevo.utils.ModPlugin;
import indevo.utils.helper.StringHelper;
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

    protected String inputProjId = null;
    protected String rewardProjId = null;
    protected CargoAPI rewardsCargo = null;

    private static final String DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY = "$IndEvo_tempDialogueInstance";

    public static final Logger log = Global.getLogger(ResearchProjectDialoguePlugin.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        if (!(dialog instanceof IndEvo_InitSYCustomProductionDiag)) {
            this.dialog = dialog;
            this.memoryMap = memoryMap;
            if (dialog == null) return false;

            entity = dialog.getInteractionTarget();
            originalPlugin = dialog.getPlugin();
            dialog.setPlugin(this);
        }

        dialog.setPromptText("Select an Option");

        init(dialog);
        return true;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        Global.getSector().getMemoryWithoutUpdate().set(DIALOGUE_PLUGIN_TEMP_INSTANCE_KEY, this, 0f);
        VisualCustomPanel.createPanel(dialog, true);
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
        new ResearchProjectSidePanelCreator().showPanel(dialog);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
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

        // if (inputProjId == null && rewardProjId == null) {
        panel.setFontSmallInsignia();
        panel.addParagraph("-----------------------------------------------------------------------------", Misc.getGrayColor());

        panel.addPara("You can donate certain items to research projects, causing them to advance.\n" +
                "Once a project is finished, you will receive rewards, which can range from blueprints to unique weapons.", Misc.getGrayColor());
        panel.addPara("");
        panel.addPara("You will be notified when new projects become available.", Misc.getGrayColor());

        panel.addParagraph("-----------------------------------------------------------------------------", Misc.getGrayColor());
        panel.setFontInsignia();
        //}

        if (rewardProjId != null && inputProjId == null) {
            ResearchProject project = ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(rewardProjId);
            TooltipMakerAPI rewardsToolTip = panel.beginTooltip();
            rewardsToolTip.addSectionHeading(project.getName() + " - Rewards", Alignment.MID, 10f);
            project.addTooltipOutputOnCompletion(rewardsToolTip);
            panel.addTooltip();

            if (rewardsCargo != null) for (CargoStackAPI stack : rewardsCargo.getStacksCopy()) {
                AddRemoveCommodity.addStackGainText(stack, panel);
            }

        } else if (inputProjId != null && rewardProjId == null) {
            ResearchProject project = ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(inputProjId);
            if (project != null) {

                TooltipMakerAPI textPanel = panel.beginTooltip();
                textPanel.addSectionHeading(project.getName(), Alignment.MID, 10f);
                textPanel.addPara(project.getLongDesc(), 3f);

                textPanel.addSectionHeading("Contribution options:", Alignment.MID, 10f);
                panel.addTooltip();

                CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                TooltipMakerAPI tt = panel.beginTooltip();
                float requiredPoints = project.getRequiredPoints();

                for (RequiredItem item : project.getRequiredItems()) {
                    String progress = Math.abs(Math.round(item.points / requiredPoints * 1000f)) / 10f + "%";
                    //String progress = StringHelper.getAbsPercentString(item.points / requiredPoints, false);
                    float count = getQuantity(cargo, item);

                    ModPlugin.log("count " + item.type + " " + item.id + " " + count);

                    if (item.type.equals(CargoAPI.CargoItemType.WEAPONS)) {
                        WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(item.id);
                        TooltipMakerAPI imageWithText = tt.beginImageWithText(spec.getTurretSpriteName(), 30f);
                        imageWithText.addPara("%s  -  Progress: %s  -  Available: %s",
                                5f,
                                Misc.getHighlightColor(),
                                new String[]{spec.getWeaponName(), progress, (int) Math.round(count) + ""});

                        tt.addImageWithText(2f);

                    } else if (item.type.equals(CargoAPI.CargoItemType.RESOURCES)) {
                        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(item.id);

                        TooltipMakerAPI imageWithText = tt.beginImageWithText(spec.getIconName(), 30f);
                        imageWithText.addPara("%s  -  Progress: %s  -  Available: %s",
                                5f,
                                Misc.getHighlightColor(),
                                new String[]{spec.getName(), progress, (int) Math.round(count) + ""});
                        tt.addImageWithText(2f);

                    } else if (item.type.equals(CargoAPI.CargoItemType.SPECIAL)) {
                        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(item.id);

                        TooltipMakerAPI imageWithText = tt.beginImageWithText(spec.getIconName(), 30f);
                        imageWithText.addPara("%s  -  Progress: %s  -  Available: %s",
                                5f,
                                Misc.getHighlightColor(),
                                new String[]{spec.getName(), progress, (int) Math.round(count) + ""});
                        tt.addImageWithText(2f);
                    }
                }

                panel.addTooltip();
            }
        }
    }

    public static float getQuantity(CargoAPI cargo, RequiredItem item){
        float total = 0f;

        for (CargoStackAPI stack : cargo.getStacksCopy()) if (stack.getType().toString().equals(item.type.toString())) {
            String id = null;

            switch (stack.getType()){
                case RESOURCES:
                    id = stack.getCommodityId();
                    break;
                case WEAPONS:
                    id = stack.getWeaponSpecIfWeapon().getWeaponId();
                    break;
                case FIGHTER_CHIP:
                    id = stack.getFighterWingSpecIfWing().getId();
                    break;
                case SPECIAL:
                    id = stack.getSpecialDataIfSpecial().getId();
                    break;
            }

            if (item.id.equals(id)) total += stack.getSize();
        }

        return total;
    }

    private void returnToMenu() {
        VisualCustomPanel.clearPanel();
        dialog.getTextPanel().clear();

        dialog.setPlugin(originalPlugin);
        //new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        memoryMap.get(MemKeys.LOCAL).set("$option", "gaMeetingEnd", 0f);
        FireAll.fire(null, dialog, memoryMap, "DialogOptionSelected");
    }

    public void setProjectIdForRewards(String id, CargoAPI cargo) {
        this.inputProjId = null;
        this.rewardsCargo = cargo.createCopy();
        this.rewardProjId = id;

        addTooltip(dialog.getTextPanel());
    }

    public void setProjectIdForInputs(String id) {
        this.inputProjId = id;
        this.rewardsCargo = null;
        this.rewardProjId = null;

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