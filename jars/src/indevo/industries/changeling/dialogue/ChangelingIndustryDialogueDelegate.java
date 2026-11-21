package indevo.industries.changeling.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.industry.ChangelingTarget;
import indevo.industries.compatibility.AotdManager;
import indevo.utils.helper.BillingHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChangelingIndustryDialogueDelegate implements CustomDialogDelegate {
    public static final float WIDTH = 600f;
    public static final float HEIGHT = Global.getSettings().getScreenHeight() - 300f;
    public static final float ENTRY_HEIGHT = 84f; //MUST be even
    public static final float CONTENT_HEIGHT = ENTRY_HEIGHT - 4f;
    public static final float ENTRY_WIDTH = WIDTH - 5f; //MUST be even

    public Industry industry;
    public ChangelingTarget target;
    public List<SubIndustryData> subIndustries;
    public SubIndustryData selected = null;
    public List<ButtonAPI> buttons = new ArrayList<>();

    public ChangelingIndustryDialogueDelegate(Industry industry, ChangelingTarget target) {
        this.industry = industry;
        this.target = target;
        this.subIndustries = target.getOptions();
    }

    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        TooltipMakerAPI panelTooltip = panel.createUIElement(WIDTH, HEIGHT, true);
        panelTooltip.addSectionHeading("Select a Variant", Alignment.MID, 0f);

        float opad = 10f;
        float spad = 2f;

        buttons.clear();

        String currentId = target.getCurrentId(industry.getMarket());

        for (SubIndustryData data : subIndustries) {
            SubIndustry sub = data.newInstance();

            if (sub.getId().equals(currentId)) continue;

            sub.init(industry);

            int buildTime = Math.round(sub.getBuildTime());
            float cost = sub.getCost();
            boolean canAfford = BillingHelper.canAfford(cost);
            String research = AotdManager.getMissingResearchReason(data.getUnlockId(), industry.getMarket());
            boolean canBuild = research == null && sub.isAvailableToBuild();

            Color baseColor = Misc.getButtonTextColor();
            Color bgColour = Misc.getDarkPlayerColor();
            Color brightColor = Misc.getBrightPlayerColor();

            if (!canAfford) {
                baseColor = Color.darkGray;
                bgColour = Color.lightGray;
                brightColor = Color.gray;
            }

            CustomPanelAPI subIndustryButtonPanel = panel.createCustomPanel(ENTRY_WIDTH, ENTRY_HEIGHT + 2f, new ButtonReportingCustomPanel(this));

            //image creation
            String spriteName = sub.getImageName(industry.getMarket());
            SpriteAPI sprite = Global.getSettings().getSprite(spriteName);
            float aspectRatio = sprite.getWidth() / sprite.getHeight();
            float adjustedWidth = Math.min(CONTENT_HEIGHT * aspectRatio, sprite.getWidth());
            float defaultPadding = (ENTRY_HEIGHT - CONTENT_HEIGHT) / 2;

            //Text creation so we know the total height
            TooltipMakerAPI textPanel = subIndustryButtonPanel.createUIElement(ENTRY_WIDTH - adjustedWidth - opad - defaultPadding, CONTENT_HEIGHT, false);
            if (canAfford && canBuild) textPanel.addSectionHeading(" " + sub.getName(), Alignment.LMID, 0f);
            else textPanel.addSectionHeading(" " + sub.getName(), Color.WHITE, Misc.getGrayColor(), Alignment.LMID, 0f);
            textPanel.addPara(sub.getDescription().getText2(), opad);
            if (!canBuild) textPanel.addPara(research != null ? research : sub.getUnavailableReason(), Misc.getNegativeHighlightColor(), spad).setAlignment(Alignment.RMID);
            else textPanel.addPara("Cost: %s", spad, canAfford ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), Misc.getDGSCredits(cost)).setAlignment(Alignment.RMID);

            //adjust panel height
            float baseHeight = textPanel.getHeightSoFar() + 2f + opad;
            subIndustryButtonPanel.getPosition().setSize(ENTRY_WIDTH, Math.max(ENTRY_HEIGHT, baseHeight));

            //create background button
            TooltipMakerAPI anchor = subIndustryButtonPanel.createUIElement(ENTRY_WIDTH, baseHeight, false);

            ButtonAPI areaCheckbox = anchor.addAreaCheckbox("", sub.getId(), baseColor, bgColour, brightColor, //new Color(255,255,255,0)
                    ENTRY_WIDTH,
                    baseHeight,
                    0f,
                    true);

            areaCheckbox.setChecked(selected == data);
            areaCheckbox.setEnabled(canAfford && canBuild);
            subIndustryButtonPanel.addUIElement(anchor).inTL(-opad, 0f); //if we don't -opad it kinda does it by its own, no clue why

            //image addition
            anchor = subIndustryButtonPanel.createUIElement(adjustedWidth, ENTRY_HEIGHT, false);
            anchor.addImage(spriteName, adjustedWidth, Math.min(CONTENT_HEIGHT, sprite.getHeight()), 0f);
            subIndustryButtonPanel.addUIElement(anchor).inTL(defaultPadding - opad, defaultPadding);

            TooltipMakerAPI lastPos = anchor;

            //text addition
            subIndustryButtonPanel.addUIElement(textPanel).rightOfTop(lastPos, opad);

            panelTooltip.addCustom(subIndustryButtonPanel, 0f);
            buttons.add(areaCheckbox);
        }

        panel.addUIElement(panelTooltip).inTL(0.0F, 0.0F);
    }

    public boolean hasCancelButton() {
        return true;
    }

    public String getConfirmText() {
        return "Confirm";
    }

    public String getCancelText() {
        return "Cancel";
    }

    public void customDialogConfirm() {
        if (selected == null) return;

        MarketAPI market = industry.getMarket();
        if (selected.id.equals(target.getCurrentId(market))) return;

        target.setCurrent(market, selected);
        BillingHelper.chargeNow(selected.cost);
    }

    public void customDialogCancel() {
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }

    public void reportButtonPressed(Object id) {
        if (id instanceof String) {
            for (SubIndustryData sub : subIndustries) {
                if (sub.id.equals(id)) {
                    selected = sub;
                    break;
                }
            }
        }

        boolean anyChecked = false;

        for (ButtonAPI button : buttons) {
            if (button.isChecked() && button.getCustomData() != id) {
                button.setChecked(false);
            }

            if (button.isChecked()) anyChecked = true;
        }

        if (!anyChecked) selected = null;
    }

    public static class ButtonReportingCustomPanel extends BaseCustomUIPanelPlugin {
        public ChangelingIndustryDialogueDelegate delegate;

        public ButtonReportingCustomPanel(ChangelingIndustryDialogueDelegate delegate) {
            this.delegate = delegate;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            super.buttonPressed(buttonId);
            delegate.reportButtonPressed(buttonId);
        }
    }
}
