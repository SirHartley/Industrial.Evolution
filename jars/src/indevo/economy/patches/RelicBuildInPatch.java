package indevo.economy.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.MessageDisplayAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.coreui.refit.ModPickerDialogV3;
import indevo.utils.helper.RelicHelper;
import org.apache.log4j.Logger;
import patchlib.api.context.AfterContext;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.List;

// refit build in dialog, readable class in an obf package (0.98a-RC8), every target here is inferred
@Patch(target = @ClassMatch(type = ModPickerDialogV3.class))
public class RelicBuildInPatch {

    public static final Logger log = Global.getLogger(RelicBuildInPatch.class);

    public static final float RELICS_PER_DP = 5f;
    public static final String LORE = "Your engineers have drawn up a permanent installation, but the available parts cannot meet its tolerances. Domain components can.";

    private static final String NO_STORY_POINTS = "No " + Misc.STORY + " points available";
    private static final float TOGGLE_HEIGHT = 22f;
    private static final float TOGGLE_SHIFT = 30f;
    private static final float TOOLTIP_WIDTH = 400f;

    private static final FieldQuerySpec WIDGET_FIELD = FieldQuery.create().type(ModPickerDialogV3.PermaModInfoWidget.class).build();
    private static final MethodQuerySpec GET_REFIT_PANEL = MethodQuery.create().methodName("getRefitPanel").parameterCount(0).build();
    private static final MethodQuerySpec GET_MEMBER = MethodQuery.create().methodName("getMember").parameterCount(0).build();
    private static final MethodQuerySpec GET_CHILDREN = MethodQuery.create().methodName("getChildrenCopy").parameterCount(0).build();

    // the counter widget can not reach the dialog's PatchData, only one build in dialog is open at a time
    private static WeakReference<Toggle> active = new WeakReference<>(null);

    @After(target = @MethodMatch(methodName = "createUI", parameterCount = 0))
    public static void afterCreateUI(AfterContext context) {
        active = new WeakReference<>(null);

        ModPickerDialogV3 dialog = context.getInferredSelf();
        if (!dialog.isPermMode()) return;

        // only built in the campaign
        Object widget = context.getField(WIDGET_FIELD).get();
        if (!(widget instanceof UIPanelAPI widgetPanel)) return;

        if (!(context.getMethod(GET_MEMBER, context.getMethod(GET_REFIT_PANEL).call()).call() instanceof FleetMemberAPI member)) {
            log.warn("Refit panel has no fleet member in build in mode, relic toggle skipped");
            return;
        }

        ScrollPanelAPI scroller = null;
        List<?> children = context.getMethod(GET_CHILDREN, widget).call();
        for (Object child : children) {
            if (child instanceof ScrollPanelAPI s) {
                scroller = s;
                break;
            }
        }

        if (scroller == null) {
            log.warn("No hullmod effect list in the build in panel, relic toggle skipped");
            return;
        }

        // vanilla leaves 15 between the story point counter and the effect list, make room and keep its bottom edge
        PositionAPI pos = scroller.getPosition();
        pos.setSize(pos.getWidth(), pos.getHeight() - TOGGLE_SHIFT);
        pos.setYAlignOffset(-TOGGLE_SHIFT);

        Toggle toggle = new Toggle(dialog, widget, member);
        widgetPanel.addComponent(toggle.create(pos.getWidth())).aboveLeft(scroller, (TOGGLE_SHIFT - TOGGLE_HEIGHT) / 2f);
        active = new WeakReference<>(toggle);
    }

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "tableRowSelected", parameterCount = 3),
            call = @MethodMatch(methodName = "getStoryPoints", parameterCount = 0),
            owner = @ClassMatch(subtype = MutableCharacterStatsAPI.class))
    public static void redirectRowStoryPoints(MethodCallContext context) {
        Toggle toggle = active(context.getSelf());
        context.setResult(toggle != null ? toggle.getAffordable() : context.call());
    }

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "tableRowSelected", parameterCount = 3),
            call = @MethodMatch(methodName = "addMessage", parameterTypes = {String.class, Color.class}),
            owner = @ClassMatch(subtype = MessageDisplayAPI.class))
    public static void redirectRowMessage(MethodCallContext context) {
        if (active(context.getSelf()) != null && NO_STORY_POINTS.equals(context.getCallArg(0))) {
            context.call("Not enough " + RelicHelper.getName(), context.getCallArg(1));
        } else context.call();
    }

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "actionPerformed", parameterCount = 2),
            call = @MethodMatch(methodName = "spendStoryPoints", parameterCount = 6),
            owner = @ClassMatch(subtype = MutableCharacterStatsAPI.class))
    public static void redirectSpendStoryPoints(MethodCallContext context) {
        Toggle toggle = active(context.getSelf());

        if (toggle != null) toggle.pay((int) context.getCallArg(0));
        else context.call();
    }

    public static Toggle active(Object owner) {
        Toggle toggle = active.get();
        return toggle != null && toggle.on && (toggle.dialog == owner || toggle.widget.get() == owner) ? toggle : null;
    }

    public static class Toggle extends BaseCustomUIPanelPlugin {
        private final ModPickerDialogV3 dialog;
        private final WeakReference<Object> widget;
        private final int costPerMod;
        private final MarketAPI market;
        private ButtonAPI checkbox;
        private boolean on = false;

        Toggle(ModPickerDialogV3 dialog, Object widget, FleetMemberAPI member) {
            this.dialog = dialog;
            this.widget = new WeakReference<>(widget);
            this.costPerMod = Math.max(1, Math.round(member.getUnmodifiedDeploymentPointsCost() * RELICS_PER_DP));

            InteractionDialogAPI interaction = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            MarketAPI m = interaction != null && interaction.getInteractionTarget() != null ? interaction.getInteractionTarget().getMarket() : null;
            this.market = m != null && Misc.playerHasStorageAccess(m) ? m : null;
        }

        CustomPanelAPI create(float width) {
            CustomPanelAPI panel = Global.getSettings().createCustom(width, TOGGLE_HEIGHT, this);
            TooltipMakerAPI el = panel.createUIElement(width, TOGGLE_HEIGHT, false);
            checkbox = el.addAreaCheckbox("Use relic components", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), width, TOGGLE_HEIGHT, 0f);
            el.addTooltipTo(new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    createToggleTooltip(tooltip);
                }
            }, checkbox, TooltipMakerAPI.TooltipLocation.LEFT);

            panel.addUIElement(el).inTL(0f, 0f);
            return panel;
        }

        private void createToggleTooltip(TooltipMakerAPI tooltip) {
            float opad = 10f;
            int available = getAvailable();
            String relicName = RelicHelper.getName();
            boolean canPay = available >= costPerMod;

            tooltip.addPara("Build hullmods into this ship with %s instead of story points. No bonus experience is granted.", 0f, Misc.getHighlightColor(), relicName);
            tooltip.addPara("While this is on, the counter above shows how many more hullmods your %s pay for.", opad, Misc.getHighlightColor(), relicName);
            tooltip.addPara("Cost: %s per hullmod (Available: " + available + ")", opad, canPay ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), costPerMod + " " + relicName);
            if (getPending() > 0) tooltip.addPara("Clear the selected hullmods to switch.", Misc.getNegativeHighlightColor(), opad);
            tooltip.addPara(LORE, Misc.getGrayColor(), opad);
        }

        @Override
        public void advance(float amount) {
            if (checkbox == null) return;

            int pending = getPending();
            if (checkbox.isChecked() != on) {
                // switching with hullmods selected would pay them with the other currency
                if (pending > 0) checkbox.setChecked(on);
                else on = checkbox.isChecked();
            }

            boolean enabled = pending == 0 && (on || getAvailable() >= costPerMod);
            if (checkbox.isEnabled() != enabled) checkbox.setEnabled(enabled);
        }

        int getAffordable() {
            return getAvailable() / costPerMod;
        }

        private int getPending() {
            return dialog.getAddedPermaMods().size() + dialog.getAddedSModdedBuiltIns().size();
        }

        void pay(int count) {
            int cost = count * costPerMod;
            RelicHelper.take(market, cost);

            Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Lost " + cost + " " + RelicHelper.getName(), "" + cost, Misc.getNegativeHighlightColor());
            log.info("Built " + count + " hullmods in with " + cost + " relic components");
        }

        private int getAvailable() {
            return RelicHelper.getAvailable(market);
        }
    }
}
