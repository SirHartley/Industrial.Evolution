package indevo.economy.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.coreui.refit.ModPickerDialogV3;
import indevo.ids.ItemIds;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.MethodQuerySpec;

import java.util.ArrayList;
import java.util.List;

// effect list of the refit build in dialog, vanilla rebuilds it in every advanceImpl (0.98a-RC8), all targets inferred
@Patch(target = @ClassMatch(type = ModPickerDialogV3.PermaModInfoWidget.class))
public class RelicBuildInListPatch {

    private static final float OPAD = 10f;
    private static final float ICON_SIZE = 32f;
    private static final float COST_WIDTH = 48f;
    private static final float TOOLTIP_WIDTH = 300f;

    private static final MethodQuerySpec GET_CONTENT = MethodQuery.create().methodName("getContentContainer").parameterCount(0).build();
    private static final MethodQuerySpec GET_CHILDREN = MethodQuery.create().methodName("getChildrenCopy").parameterCount(0).build();
    private static final MethodQuerySpec SET_CONTENT_SIZE = MethodQuery.create().methodName("setContentSize").parameterCount(2).build();
    private static final MethodQuerySpec GET_REFIT_PANEL = MethodQuery.create().methodName("getRefitPanel").parameterCount(0).build();
    private static final MethodQuerySpec GET_SHIP_DISPLAY = MethodQuery.create().methodName("getShipDisplay").parameterCount(0).build();
    private static final MethodQuerySpec GET_SHIP = MethodQuery.create().methodName("getShip").parameterCount(0).build();

    @After(target = @MethodMatch(methodName = "advanceImpl", parameterTypes = float.class))
    public static void afterAdvance(AfterContext context) {
        RelicBuildInPatch.Toggle toggle = RelicBuildInPatch.of(context.getSelf());
        if (toggle == null) return;

        ScrollPanelAPI effects = toggle.effects;
        List<HullModSpecAPI> pending = new ArrayList<>(toggle.dialog.getAddedPermaMods());
        pending.addAll(toggle.dialog.getAddedSModdedBuiltIns());

        if (!toggle.isOn() || pending.isEmpty()) {
            if (toggle.list != null) {
                effects.removeComponent(toggle.list);
                context.getMethod(SET_CONTENT_SIZE, effects).call(effects.getPosition().getWidth(), 0f);
            }
            toggle.list = null;
            toggle.listKey = null;
            return;
        }

        // vanilla adds a fresh effect tooltip every frame, drop it so only the relic list shows
        Object content = context.getMethod(GET_CONTENT, effects).call();
        for (Object child : (List<?>) context.getMethod(GET_CHILDREN, content).call()) {
            if (child != toggle.list && child instanceof UIComponentAPI c) effects.removeComponent(c);
        }

        String key = getKey(pending);
        if (toggle.list != null && key.equals(toggle.listKey)) return;
        if (toggle.list != null) effects.removeComponent(toggle.list);

        Object ship = context.getMethod(GET_SHIP, context.getMethod(GET_SHIP_DISPLAY, context.getMethod(GET_REFIT_PANEL, toggle.dialog).call()).call()).call();
        if (!(ship instanceof ShipAPI shipAPI)) {
            RelicBuildInPatch.log.warn("Refit panel has no ship, relic build in list skipped");
            toggle.list = null;
            toggle.listKey = key;
            return;
        }

        float width = effects.getPosition().getWidth() - 4f;
        toggle.list = createList(shipAPI, pending, toggle.getCostPerMod(), width);
        toggle.listKey = key;
        effects.addComponent(toggle.list).inTL(0f, 0f);
        context.getMethod(SET_CONTENT_SIZE, effects).call(width, toggle.list.getPosition().getHeight());
    }

    private static String getKey(List<HullModSpecAPI> specs) {
        StringBuilder key = new StringBuilder();
        for (HullModSpecAPI spec : specs) key.append(spec.getId()).append(',');
        return key.toString();
    }

    private static CustomPanelAPI createList(ShipAPI ship, List<HullModSpecAPI> specs, int cost, float width) {
        CustomPanelAPI list = Global.getSettings().createCustom(width, 0f, null);
        String icon = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getIconName();
        float textWidth = width - COST_WIDTH - OPAD;

        List<TooltipMakerAPI> costs = new ArrayList<>();
        List<TooltipMakerAPI> texts = new ArrayList<>();

        for (HullModSpecAPI spec : specs) {
            TooltipMakerAPI text = list.createUIElement(textWidth, 0f, false);
            if (spec.getEffect().hasSModEffectSection(ship.getHullSize(), ship, false)) {
                spec.getEffect().addSModSection(text, ship.getHullSize(), ship, textWidth, false, true);
            } else {
                text.addSectionHeading(spec.getDisplayName(), Misc.getStoryOptionColor(), Misc.getStoryDarkColor(), Alignment.MID, OPAD);
            }
            texts.add(text);

            // same top pad as the section heading, so the icon sits in line with it
            TooltipMakerAPI costEl = list.createUIElement(COST_WIDTH, 0f, false);
            costEl.addImages(COST_WIDTH, ICON_SIZE, OPAD, 0f, icon);
            // first tooltip element gets a 5 px x offset, the image row is centered in the full width
            costEl.getPrev().getPosition().setXAlignOffset(0f);
            costEl.addTooltipToPrevious(new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara("Use %s relics instead of 1 SP to build in this hullmod", 0f, Misc.getHighlightColor(), cost + "");
                }
            }, TooltipMakerAPI.TooltipLocation.LEFT);
            costEl.addPara("x " + cost, Misc.getHighlightColor(), 3f).setAlignment(Alignment.MID);
            costs.add(costEl);
        }

        float height = 0f;
        for (int i = 0; i < specs.size(); i++) height += Math.max(costs.get(i).getHeightSoFar(), texts.get(i).getHeightSoFar());
        list.getPosition().setSize(width, height + OPAD);

        float y = 0f;
        for (int i = 0; i < specs.size(); i++) {
            list.addUIElement(texts.get(i)).inTL(0f, y);
            list.addUIElement(costs.get(i)).inTL(width - COST_WIDTH, y);
            y += Math.max(costs.get(i).getHeightSoFar(), texts.get(i).getHeightSoFar());
        }

        return list;
    }
}
