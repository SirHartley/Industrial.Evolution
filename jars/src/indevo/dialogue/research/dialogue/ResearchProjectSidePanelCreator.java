package indevo.dialogue.research.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.research.ResearchProjectTemplateRepo;
import indevo.dialogue.sidepanel.FramedCustomPanelPlugin;
import indevo.dialogue.sidepanel.InteractionDialogCustomPanelPlugin;
import indevo.dialogue.sidepanel.NoFrameCustomPanelPlugin;
import indevo.dialogue.sidepanel.VisualCustomPanel;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.Map;

public class ResearchProjectSidePanelCreator {
    public static final Logger log = Global.getLogger(ResearchProjectSidePanelCreator.class);

    protected static final float PANEL_WIDTH_1 = 240;
    protected static final float PANEL_WIDTH_2 = VisualCustomPanel.PANEL_WIDTH - PANEL_WIDTH_1 - 8;
    protected static final float SHIP_ICON_WIDTH = 48;
    protected static final float ARROW_BUTTON_WIDTH = 20, BUTTON_HEIGHT = 30;
    protected static final float SELECT_BUTTON_WIDTH = 95f;
    protected static final float TEXT_FIELD_WIDTH = 80f;

    //create one slot for each research project .json
    //Header with project title
    //Button "Donate Artifacts" - Grey out if none available, or display the available gun amt
    //button should spawn cargoPicker
    //button "Required Items"
    //shows a list with the required items
    //Progress Tracker to the left of it
    //short blurb what the project is about with weap. size ("Researching a reality warping large weapons platform based on some exotic samples...")
    //on completion, add button "Redeem Rewards" - giving you 3/2/1 weapons of the category and the blueprint.


    public void showPanel(InteractionDialogAPI dialogue) {
        VisualCustomPanel.createPanel(dialogue, true);
        showCustomPanel();
    }
//check Nex_NGCStartFleetOptionsV2

    private void showCustomPanel() {
        float opad = 10f;
        float spad = 3f;

        final CustomPanelAPI panel = VisualCustomPanel.getPanel();
        TooltipMakerAPI panelTooltip = VisualCustomPanel.getTooltip();
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        TooltipMakerAPI lastUsedVariableButtonAnchor;

        for (Map.Entry<String, ResearchProject> projectEntry : ResearchProjectTemplateRepo.RESEARCH_PROJECTS.entrySet()) {

            final ResearchProject project = projectEntry.getValue();
            final String projId = project.getId();
            final ResearchProject.Progress progress = project.getProgress();
            CargoAPI cargo = playerFleet.getCargo();
            float progressPercent = Math.min(1f, (progress.points * 1f) / (project.getRequiredPoints() * 1f));
            log.info("points " + progress.points + " req " + project.getRequiredPoints() + " % " + progressPercent);

            if (progress.redeemed || !project.display()) continue;

            boolean isFinished = progress.points >= project.getRequiredPoints() || progress.redeemed;
            boolean isRedeemed = progress.redeemed;

            int availableItemCount = 0;
            for (RequiredItem item : project.getRequiredItems()) {
                float count = ResearchProjectDialoguePlugin.getQuantity(cargo, item);
                if (count > 0f) availableItemCount += count;
            }

            boolean playerHasAnyInputInCargo = availableItemCount > 0;

            panelTooltip.addSectionHeading(project.getName(), Alignment.MID, opad);

            CustomPanelAPI buttonPanel = panel.createCustomPanel(PANEL_WIDTH_1, 50f, new NoFrameCustomPanelPlugin());

            //             NAME
            // CHECK INPUT - CONTRIBUTE - REDEEM
            // PROGRESS BAR SHOW % (+ X ITEMS AVAILABLE)
            // FLAVOUR TEXT

            //CHECK INPUT

            TooltipMakerAPI variableButtonAnchor = buttonPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);

            String buttonId = "button_inputs_" + projId;

            boolean prerequisiteForActive = true;

            Color baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
            Color bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

            ButtonAPI newLoadoutButton = variableButtonAnchor.addButton("Show info", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            InteractionDialogCustomPanelPlugin.ButtonEntry entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                @Override
                public void onToggle() {
                    ResearchProjectDialoguePlugin.getCurrentDialoguePlugin().setProjectIdForInputs(projId);
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            buttonPanel.addUIElement(variableButtonAnchor).inTL(spad, opad);       //first in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //CONTRIBUTE

            prerequisiteForActive = !isFinished && playerHasAnyInputInCargo;

            baseColor = prerequisiteForActive ? Misc.getButtonTextColor() : Misc.getTextColor();
            bgColour = prerequisiteForActive ? Misc.getDarkPlayerColor() : Misc.getGrayColor();

            buttonId = "button_cargo_" + projId;
            variableButtonAnchor = buttonPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            newLoadoutButton = variableButtonAnchor.addButton("Cargo", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                @Override
                public void onToggle() {
                    ResearchProjectDonationCargoPicker.init(ResearchProjectDialoguePlugin.getCurrentDialoguePlugin(), projId);
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            buttonPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);         //second in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            //REDEEM

            prerequisiteForActive = isFinished && !isRedeemed;

            baseColor = Misc.getTextColor();
            bgColour = prerequisiteForActive ? new Color(50, 130, 0, 255) : Misc.getGrayColor();

            buttonId = "button_redeem_" + projId;
            variableButtonAnchor = buttonPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            newLoadoutButton = variableButtonAnchor.addButton("Redeem", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            newLoadoutButton.setEnabled(prerequisiteForActive);
            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(newLoadoutButton, buttonId) {
                @Override
                public void onToggle() {
                    ResearchProject project = ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(projId);
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    CargoAPI rewards = project.getRewards();
                    cargo.addAll(rewards.createCopy());

                    if (project.isRepeatable()) {
                        project.getProgress().points = 0;
                        project.getProgress().redeemed = false;
                    } else project.getProgress().redeemed = true;

                    ResearchProjectDialoguePlugin plugin = ResearchProjectDialoguePlugin.getCurrentDialoguePlugin();
                    plugin.setProjectIdForRewards(projId, rewards);
                    plugin.refreshCustomPanel();

                    Global.getSoundPlayer().playUISound("ui_rep_raise", 1f, 1f);
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            buttonPanel.addUIElement(variableButtonAnchor).rightOfMid(lastUsedVariableButtonAnchor, opad);         //last in row
            lastUsedVariableButtonAnchor = variableButtonAnchor;

            panelTooltip.addCustom(buttonPanel, opad); //add panel

            // PROGRESS BAR SHOW % (+ X ITEMS AVAILABLE)

            //the most scuffed progress bar of all time
            CustomPanelAPI progressPanel = panel.createCustomPanel(VisualCustomPanel.PANEL_WIDTH - 20f, SHIP_ICON_WIDTH + 6f, new FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false));

            float holderWidth = VisualCustomPanel.PANEL_WIDTH - 33f;
            TooltipMakerAPI barHolder = progressPanel.createUIElement(holderWidth, 0, false);

            ButtonAPI checkbox = barHolder.addAreaCheckbox((progressPercent > 0 ? Math.abs(Math.round(progressPercent * 1000f) / 10f) : 0f) + "%", null, Misc.getDarkPlayerColor(), Misc.getDarkPlayerColor(), Misc.getTextColor(), Math.max(50f, holderWidth * progressPercent), BUTTON_HEIGHT, opad);
            checkbox.setEnabled(false);
            checkbox.setChecked(true);
            progressPanel.addUIElement(barHolder).inTL(1f, 1f); //add it to top left of fleet panel (?)

            panelTooltip.addCustom(progressPanel, 3); //add fleet panel

            // FLAVOUR TEXT
            panelTooltip.addPara("You have %S items available for this project.", opad, Misc.getHighlightColor(), availableItemCount + "");
            panelTooltip.addPara(project.getShortDesc(), Misc.getGrayColor(), opad);
        }

        VisualCustomPanel.addTooltipToPanel();
    }
}



