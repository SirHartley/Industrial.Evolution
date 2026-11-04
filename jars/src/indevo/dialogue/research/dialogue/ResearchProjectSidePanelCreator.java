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
import indevo.utils.ModPlugin;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ResearchProjectSidePanelCreator {
    public static final Logger log = Global.getLogger(ResearchProjectSidePanelCreator.class);

    protected static final float ROW_WIDTH = VisualCustomPanel.PANEL_WIDTH - 20f;
    protected static final float SHIP_ICON_WIDTH = 48;
    protected static final float ARROW_BUTTON_WIDTH = 20, BUTTON_HEIGHT = 30;
    protected static final float SELECT_BUTTON_WIDTH = 95f;
    protected static final float TEXT_FIELD_WIDTH = 80f;
    protected static final float TOGGLE_WIDTH = 80f, TOGGLE_HEIGHT = 20f;
    protected static final float TOOLTIP_WIDTH = 400f;
    protected static final String OVERFLOW_LABEL = "Stock";

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

        ModPlugin.log("showing research panel");

        final CustomPanelAPI panel = VisualCustomPanel.getPanel();
        TooltipMakerAPI panelTooltip = VisualCustomPanel.getTooltip();
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        TooltipMakerAPI lastUsedVariableButtonAnchor;

        //sort map to display projects according to their completion status

        List<ResearchProject> sortedProjects = new ArrayList<>(ResearchProjectTemplateRepo.RESEARCH_PROJECTS.values());
        sortedProjects.sort(Comparator.comparingDouble((ResearchProject p) -> p.getProgress().points).reversed());

        for (ResearchProject project : sortedProjects) {
            final String projId = project.getId();
            final ResearchProject.Progress progress = project.getProgress();
            CargoAPI cargo = playerFleet.getCargo();
            float rawProgressPercent = (progress.points) / (project.getRequiredPoints() * 1f);
            float progressPercent = Math.min(1f, rawProgressPercent);
            log.info("points " + progress.points + " req " + project.getRequiredPoints() + " % " + progressPercent);

            if (progress.redeemed || !project.display()) continue;

            boolean isFinished = progress.points >= project.getRequiredPoints() || progress.redeemed;
            boolean isRedeemed = progress.redeemed;
            boolean overflow = project.allowsOverflow();

            int availableItemCount = 0;
            for (RequiredItem item : project.getRequiredItems()) {
                float count = ResearchProjectDialoguePlugin.getQuantity(cargo, item);
                if (count > 0f) availableItemCount += count;
            }

            boolean playerHasAnyInputInCargo = availableItemCount > 0;

            panelTooltip.addSectionHeading(project.getName(), Alignment.MID, opad);

            CustomPanelAPI buttonPanel = panel.createCustomPanel(ROW_WIDTH, 50f, new NoFrameCustomPanelPlugin());

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

            prerequisiteForActive = (!isFinished || overflow) && playerHasAnyInputInCargo;

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
                        project.getProgress().points = Math.max(0f, project.getProgress().points - project.getRequiredPoints());
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

            if (project.isRepeatable()) addOverflowToggle(buttonPanel, projId, progress, opad, spad);

            panelTooltip.addCustom(buttonPanel, opad); //add panel

            // PROGRESS BAR SHOW % (+ X ITEMS AVAILABLE)

            //the most scuffed progress bar of all time
            CustomPanelAPI progressPanel = panel.createCustomPanel(ROW_WIDTH, SHIP_ICON_WIDTH + 6f, new FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false));

            float holderWidth = VisualCustomPanel.PANEL_WIDTH - 33f;
            TooltipMakerAPI barHolder = progressPanel.createUIElement(holderWidth, 0, false);

            ButtonAPI checkbox = barHolder.addAreaCheckbox((rawProgressPercent > 0 ? Math.abs(Math.round(rawProgressPercent * 1000f) / 10f) : 0f) + "%", null, Misc.getDarkPlayerColor(), Misc.getDarkPlayerColor(), Misc.getTextColor(), Math.max(50f, holderWidth * progressPercent), BUTTON_HEIGHT, opad);
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

    private void addOverflowToggle(CustomPanelAPI buttonPanel, String projId, final ResearchProject.Progress progress, float opad, float spad) {
        String buttonId = "button_overflow_" + projId;
        TooltipMakerAPI anchor = buttonPanel.createUIElement(TOGGLE_WIDTH, TOGGLE_HEIGHT, false);
        ButtonAPI toggle = anchor.addAreaCheckbox(OVERFLOW_LABEL, buttonId, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), TOGGLE_WIDTH, TOGGLE_HEIGHT, 0f);
        toggle.setChecked(progress.overflow);

        anchor.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return TOOLTIP_WIDTH;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Whether this project takes every item you hand in, or only what it needs to finish.", 0f);
                tooltip.addPara("When on, progress past %s carries over after each redeem, so one large hand-in can cover several rewards.", 10f, Misc.getHighlightColor(), "100%");
                tooltip.addPara("When off, items the project does not need stay in your cargo.", 10f);
            }
        }, toggle, TooltipMakerAPI.TooltipLocation.LEFT);

        InteractionDialogCustomPanelPlugin.ButtonEntry entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(toggle, buttonId) {
            @Override
            public void onToggle() {
                progress.overflow = state;
                ResearchProjectDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
            }
        };

        VisualCustomPanel.getPlugin().addButton(entry);
        buttonPanel.addUIElement(anchor).inTR(spad, opad + (BUTTON_HEIGHT - TOGGLE_HEIGHT) / 2f);
    }
}



