package indevo.industries.changeling.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChangelingIndustryDialogueDelegate implements CustomDialogDelegate {
    public static final float WIDTH = 600f;
    public static final float HEIGHT = Global.getSettings().getScreenHeight() - 300f;
    public static final float ENTRY_HEIGHT = 84f; //MUST be even
    public static final float ENTRY_WIDTH = WIDTH - 5f; //MUST be even
    public static final float CONTENT_HEIGHT = 80f;


    public Industry industry;
    public List<SubIndustryAPI> subIndustries;
    public String industryToAdd;
    public SubIndustryAPI selected = null;
    public List<ButtonAPI> buttons = new ArrayList<>();

    public ChangelingIndustryDialogueDelegate(Industry industry, String baseChangelingIndustryID, List<SubIndustryAPI> subIndustries) {
        this.industry = industry;
        this.subIndustries = subIndustries;
        this.industryToAdd = baseChangelingIndustryID;
    }

    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        TooltipMakerAPI panelTooltip = panel.createUIElement(WIDTH, HEIGHT, true);
        panelTooltip.addSectionHeading("Select a Variant", Alignment.MID, 0f);

        float opad = 10f;
        float spad = 2f;

        buttons.clear();

        for (SubIndustryAPI sub : subIndustries) {
            if (industry instanceof SwitchableIndustryAPI && sub == ((SwitchableIndustryAPI) industry).getCurrent())
                continue;
            if (!(industry instanceof SwitchableIndustryAPI) && sub.isBase()) continue;

            int buildTime = Math.round(sub.getBuildTime());
            float cost = sub.getCost();
            boolean canAfford = Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= cost;

            Color baseColor = Misc.getButtonTextColor();
            Color bgColour = Misc.getDarkPlayerColor();
            Color brightColor = Misc.getBrightPlayerColor();

            if (!canAfford) {
                baseColor = Color.darkGray;
                bgColour = Color.lightGray;
                brightColor = Color.gray;
            }

            CustomPanelAPI subIndustryButtonPanel = panel.createCustomPanel(ENTRY_WIDTH, ENTRY_HEIGHT + 2f, new ButtonReportingCustomPanel(this));
            TooltipMakerAPI anchor = subIndustryButtonPanel.createUIElement(ENTRY_WIDTH, ENTRY_HEIGHT, false);

            ButtonAPI areaCheckbox = anchor.addAreaCheckbox("", sub.getId(), baseColor, bgColour, brightColor, //new Color(255,255,255,0)
                    ENTRY_WIDTH,
                    ENTRY_HEIGHT,
                    0f,
                    true);

            areaCheckbox.setChecked(selected == sub);
            areaCheckbox.setEnabled(canAfford);
            subIndustryButtonPanel.addUIElement(anchor).inTL(-opad, 0f); //if we don't -opad it kinda does it by its own, no clue why

            String spriteName = sub.getImageName(industry.getMarket());
            SpriteAPI sprite = Global.getSettings().getSprite(spriteName);
            float aspectRatio = sprite.getWidth() / sprite.getHeight();
            float adjustedWidth = CONTENT_HEIGHT * aspectRatio;
            float defaultPadding = (ENTRY_HEIGHT - CONTENT_HEIGHT) / 2;

            anchor = subIndustryButtonPanel.createUIElement(adjustedWidth, ENTRY_HEIGHT, false);
            anchor.addImage(spriteName, adjustedWidth, CONTENT_HEIGHT, 0f);
            subIndustryButtonPanel.addUIElement(anchor).inTL(defaultPadding - opad, defaultPadding);

            TooltipMakerAPI lastPos = anchor;

            anchor = subIndustryButtonPanel.createUIElement(ENTRY_WIDTH - adjustedWidth - opad - defaultPadding, CONTENT_HEIGHT, false);
            if (canAfford) anchor.addSectionHeading(" " + sub.getName(), Alignment.LMID, 0f);
            else anchor.addSectionHeading(" " + sub.getName(), Color.WHITE, brightColor, Alignment.LMID, 0f);
            anchor.addPara(sub.getDescription().getText2(), opad);
            anchor.addPara("Cost: %s", spad, canAfford ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), Misc.getDGSCredits(cost)).setAlignment(Alignment.RMID);
            //anchor.addPara("Build time: %s", spad, Misc.getHighlightColor(), buildTime + " " + StringHelper.getDayOrDays(buildTime));

            subIndustryButtonPanel.addUIElement(anchor).rightOfMid(lastPos, opad);

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

        if (industry instanceof SwitchableIndustryAPI) {
            SwitchableIndustryAPI swIndustry = (SwitchableIndustryAPI) industry;

            if (swIndustry.getCurrent() != selected) {
                swIndustry.setCurrent(selected);
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(selected.getCost());
            }
        } else {
            MarketAPI market = industry.getMarket();
            market.removeIndustry(industry.getId(), null, false);
            market.addIndustry(industryToAdd);
            Industry switchable = null;

            for (Industry industry : market.getIndustries()) {
                //the switchables return a fake id on .getId() so we gotta check the spec
                if (industry.getSpec().getId().equals(industryToAdd)) {
                    switchable = industry;
                    break;
                }
            }

            if (switchable == null) return;

            //I wonder why it does that
            switchable.setHidden(false);

            //refund or transfer items
            SpecialItemData specialItemData = industry.getSpecialItem();

            if (specialItemData != null && canInstallItem(switchable, specialItemData.getId())) {
                switchable.setSpecialItem(specialItemData);
            } else if (specialItemData != null) Misc.getStorageCargo(market).addSpecial(specialItemData, 1);

            switchable.setAICoreId(industry.getAICoreId());
            if (switchable.canImprove()) switchable.setImproved(industry.isImproved());

            if (switchable instanceof SwitchableIndustryAPI) {
                ((SwitchableIndustryAPI) switchable).setCurrent(selected);
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(selected.getCost());
            } else
                throw new IllegalArgumentException("non-switchable industry passed to switchable industry dialogue delegate");
        }
    }

    public boolean canInstallItem(Industry industry, String itemID) {
        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(itemID);

        //check if it's applicable to the industry
        boolean isApplicableToIndustry = Arrays.asList(spec.getParams().replaceAll("\\s", "").split(",")).contains(industry.getId());
        //check if it has unmet requirements on the market
        return isApplicableToIndustry && ItemEffectsRepo.ITEM_EFFECTS.get(itemID).getUnmetRequirements(industry).isEmpty();

    }

    public void customDialogCancel() {
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }

    public void reportButtonPressed(Object id) {
        if (id instanceof String) {
            for (SubIndustryAPI sub : subIndustries) {
                if (sub.getId().equals(id)) {
                    selected = sub;
                    break;
                }
            }
        }

        for (ButtonAPI button : buttons) {
            if (button.isChecked() && button.getCustomData() != id) button.setChecked(false);
        }
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
