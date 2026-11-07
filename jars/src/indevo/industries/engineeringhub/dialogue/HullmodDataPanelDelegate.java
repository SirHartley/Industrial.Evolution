package indevo.industries.engineeringhub.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.industries.engineeringhub.memory.RecoveredHullmods;
import indevo.utils.helper.RelicHelper;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class HullmodDataPanelDelegate extends ButtonReportingDialogueDelegate {

    public static final float WIDTH = 600f;
    public static final float HEADER_HEIGHT = 60f;
    public static final float FOOTER_HEIGHT = 30f;
    public static final float ROW_HEIGHT = 44f;
    public static final float ICON_SIZE = 40f;
    public static final float COST_WIDTH = 90f;
    public static final float BUTTON_WIDTH = 90f;
    public static final float BUTTON_HEIGHT = 26f;
    public static final int MAX_VISIBLE_ROWS = 6;
    public static final String LORE = "The dismantled ship's control modules still contain its modification firmware. Domain decoding hardware can recover a specification your engineers can use.";

    private final MarketAPI market;
    private CustomPanelAPI basePanel;
    private CustomPanelAPI panel;

    public HullmodDataPanelDelegate(MarketAPI market) {
        this.market = market;
    }

    public static float getListHeight(int rows) {
        return Math.max(1, Math.min(rows, MAX_VISIBLE_ROWS)) * (ROW_HEIGHT + 5f) + 30f;
    }

    public static float getHeight(MarketAPI market) {
        return HEADER_HEIGHT + getListHeight(RecoveredHullmods.getLearnable(market).size()) + FOOTER_HEIGHT;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        basePanel = panel;
        recreatePanel();
    }

    private void recreatePanel() {
        if (panel != null) basePanel.removeComponent(panel);

        float width = basePanel.getPosition().getWidth();
        float height = basePanel.getPosition().getHeight();
        float opad = 10f;
        float spad = 5f;

        panel = Global.getSettings().createCustom(width, height, null);

        Map<String, String> data = RecoveredHullmods.getLearnable(market);
        int available = RelicHelper.getAvailable(market);
        String relicName = RelicHelper.getName();

        TooltipMakerAPI header = panel.createUIElement(width, HEADER_HEIGHT, false);
        header.addSectionHeading("Recovered hullmod data", Alignment.MID, 0f);
        header.addPara("Hullmods found on ships the hub stripped. Learning one adds it to the refit list.", Misc.getGrayColor(), opad);
        panel.addUIElement(header).inTL(0f, 0f);

        float listHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT;
        TooltipMakerAPI list = panel.createUIElement(width, listHeight, true);
        if (data.isEmpty()) list.addPara("No recovered hullmod data.", Misc.getGrayColor(), opad);

        Map<String, ShipHullSpecAPI> hulls = getHullSpecs(data);
        float rowWidth = width - opad;
        float textWidth = rowWidth - ICON_SIZE - COST_WIDTH - BUTTON_WIDTH - 4 * opad;

        for (Map.Entry<String, String> entry : data.entrySet()) {
            final HullModSpecAPI spec = Global.getSettings().getHullModSpec(entry.getKey());
            ShipHullSpecAPI hull = hulls.get(entry.getValue());
            final int cost = RecoveredHullmods.getCost(spec);
            final boolean affordable = cost <= available;

            CustomPanelAPI row = panel.createCustomPanel(rowWidth, ROW_HEIGHT, new ButtonReportingCustomPanel(this));

            TooltipMakerAPI icon = row.createUIElement(ICON_SIZE, ICON_SIZE, false);
            icon.addImage(spec.getSpriteName(), ICON_SIZE, ICON_SIZE, 0f);
            row.addUIElement(icon).inLMid(0f);

            TooltipMakerAPI text = row.createUIElement(textWidth, ICON_SIZE, false);
            text.addPara(spec.getDisplayName(), Misc.getTextColor(), 0f);
            if (hull != null) {
                String name = hull.getHullName();
                text.addPara(spec.getCostFor(hull.getHullSize()) + " OP, from " + Misc.getAOrAnFor(name) + " " + name, Misc.getGrayColor(), 2f);
            }
            row.addUIElement(text).rightOfMid(icon, opad);

            TooltipMakerAPI costEl = row.createUIElement(COST_WIDTH, ICON_SIZE, false);
            costEl.addPara("Cost: " + cost, affordable ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), 0f);
            row.addUIElement(costEl).rightOfMid(text, opad);

            TooltipMakerAPI buttonEl = row.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            ButtonAPI learn = buttonEl.addButton("Learn", new ButtonAction(this) {
                @Override
                public void execute() {
                    RecoveredHullmods.learn(market, spec.getId());
                    recreatePanel();
                }
            }, Misc.getButtonTextColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.C2_MENU, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            learn.setEnabled(affordable);
            buttonEl.addTooltipToPrevious(new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 360f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara("Adds %s to the hullmods your faction knows.", 0f, Misc.getHighlightColor(), spec.getDisplayName());
                    tooltip.addPara("Cost: %s (Available: " + available + ")", opad,
                            affordable ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), cost + " " + relicName);
                    tooltip.addPara(LORE, Misc.getGrayColor(), opad);
                }
            }, TooltipMakerAPI.TooltipLocation.LEFT);
            row.addUIElement(buttonEl).rightOfMid(costEl, opad);

            list.addCustom(row, spad);
        }

        panel.addUIElement(list).belowLeft(header, 0f);

        TooltipMakerAPI footer = panel.createUIElement(width, FOOTER_HEIGHT, false);
        footer.addPara(relicName + " available: %s", spad, Misc.getHighlightColor(), available + "");
        panel.addUIElement(footer).belowLeft(list, 0f);

        basePanel.addComponent(panel);
    }

    private static Map<String, ShipHullSpecAPI> getHullSpecs(Map<String, String> data) {
        Map<String, ShipHullSpecAPI> result = new HashMap<>();
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (data.containsValue(spec.getHullId())) result.put(spec.getHullId(), spec);
        }
        return result;
    }

    @Override
    public boolean hasCancelButton() {
        return false;
    }

    @Override
    public String getConfirmText() {
        return "Return";
    }
}
