package indevo.exploration.salvage.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import patchlib.api.context.AfterContext;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;
import patchlib.api.store.PatchData;

import java.awt.Color;
import java.util.List;

@Patch(target = @ClassMatch(subtype = ShipRecoverySpecial.class))
public class RelicDerelictRecoveryPatch {

    public static final String OPTION_ID = "IndEvo_relicRecover";
    public static final int RELICS_PER_DP = 5;

    private static final String VANILLA_NOT_NOW_TEXT = "\"I'll need to consider my options.\"";
    private static final String OPTION_TEXT = "Rebuild with relic components (%s)";
    private static final String LORE = "Whole sections of the wreck's machinery are gone. Your engineers can rebuild them around the relic components in your holds.";

    private static final String PICKER_OPENING_KEY = "IndEvo_relicRecoverPickerOpening";
    private static final String PAY_FROM_INDEX_KEY = "IndEvo_relicRecoverPayFrom";

    private static final FieldQuerySpec MEMBERS_FIELD = FieldQuery.create().fieldName("members").build();
    private static final FieldQuerySpec RECOVERED_FIELD = FieldQuery.create().fieldName("recovered").build();
    private static final FieldQuerySpec OPTIONS_FIELD = FieldQuery.create().fieldName("options").build();
    private static final FieldQuerySpec TEXT_FIELD = FieldQuery.create().fieldName("text").build();
    private static final MethodQuerySpec ADD_STORY_OPTIONS = MethodQuery.create().methodName("addStoryOptions").parameterCount(0).build();

    @After(target = @MethodMatch(methodName = "addStoryOptions", parameterCount = 0))
    public static void afterAddStoryOptions(AfterContext context) {
        if (!context.isMostDerivedCall()) return;

        OptionPanelAPI options = context.<OptionPanelAPI>getField(OPTIONS_FIELD).get();
        List<FleetMemberAPI> members = context.<List<FleetMemberAPI>>getField(MEMBERS_FIELD).get();
        if (options == null || members == null || members.isEmpty()) return;

        int cost = getCost(members);
        int available = getAvailable();
        boolean canPay = available >= cost;

        // re-add not now below ours so it stays last
        boolean hadNotNow = options.hasOption(ShipRecoverySpecial.NOT_NOW);
        if (hadNotNow) options.removeOption(ShipRecoverySpecial.NOT_NOW);

        options.addOption(String.format(OPTION_TEXT, cost), OPTION_ID);
        options.setEnabled(OPTION_ID, canPay);
        options.addOptionTooltipAppender(OPTION_ID, (tooltip, hadOtherText) -> addTooltip(tooltip, cost, available));

        if (hadNotNow) options.addOption(VANILLA_NOT_NOW_TEXT, ShipRecoverySpecial.NOT_NOW);
    }

    @After(target = @MethodMatch(methodName = "isStoryPointRecovery", parameterCount = 0))
    public static void afterIsStoryPointRecovery(AfterContext context) {
        if (context.getData().has(PICKER_OPENING_KEY)) context.setReturnValue(false);
    }

    @Before(target = @MethodMatch(methodName = "optionSelected", parameterCount = 2))
    public static void beforeOptionSelected(BeforeContext context) {
        if (!context.isMostDerivedCall()) return;

        Object option = context.getArg(1);
        PatchData data = context.getData();

        if (OPTION_ID.equals(option)) {
            List<FleetMemberAPI> recovered = context.<List<FleetMemberAPI>>getField(RECOVERED_FIELD).get();
            data.put(PICKER_OPENING_KEY, true);
            data.put(PAY_FROM_INDEX_KEY, recovered.size());
            context.setArg(1, ShipRecoverySpecial.RECOVER);

        } else if (ShipRecoverySpecial.RECOVERY_FINISHED.equals(option)) {
            Integer from = data.get(PAY_FROM_INDEX_KEY);
            data.remove(PAY_FROM_INDEX_KEY);
            if (from != null) pay(context, from);

        } else data.remove(PAY_FROM_INDEX_KEY);
    }

    @After(target = @MethodMatch(methodName = "optionSelected", parameterCount = 2))
    public static void afterOptionSelected(AfterContext context) {
        if (!context.isMostDerivedCall()) return;

        PatchData data = context.getData();
        if (!data.has(PICKER_OPENING_KEY)) return;
        data.remove(PICKER_OPENING_KEY);

        // picker is open now; put the story options back for when it is cancelled
        OptionPanelAPI options = context.<OptionPanelAPI>getField(OPTIONS_FIELD).get();
        ShipRecoverySpecial special = (ShipRecoverySpecial) context.getSelf();
        if (options != null && options.hasOption(ShipRecoverySpecial.RECOVER) && !special.isDone()) {
            context.getMethod(ADD_STORY_OPTIONS).call();
        }
    }

    private static void pay(BeforeContext context, int from) {
        List<FleetMemberAPI> recovered = context.<List<FleetMemberAPI>>getField(RECOVERED_FIELD).get();
        if (recovered == null || from >= recovered.size()) return;

        int cost = getCost(recovered.subList(from, recovered.size()));
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int take = Math.min(cost, (int) cargo.getCommodityQuantity(ItemIds.RARE_PARTS));
        if (take <= 0) return;

        cargo.removeCommodity(ItemIds.RARE_PARTS, take);

        TextPanelAPI text = context.<TextPanelAPI>getField(TEXT_FIELD).get();
        if (text != null) AddRemoveCommodity.addCommodityLossText(ItemIds.RARE_PARTS, take, text);
    }

    private static void addTooltip(TooltipMakerAPI tooltip, int cost, int available) {
        float opad = 10f;
        Color hl = Misc.getHighlightColor();
        String relicName = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();
        boolean canPay = available >= cost;

        tooltip.addPara("Recover the ships here without spending a story point. Takes %s per deployment point for each ship you recover.", 0f, hl, RELICS_PER_DP + " " + relicName);
        tooltip.addPara("Cost: %s (Available: " + available + ")", opad, canPay ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), cost + " " + relicName);
        if (!canPay) tooltip.addPara("Needs enough to cover every ship here.", Misc.getNegativeHighlightColor(), opad);
        tooltip.addPara(LORE, Misc.getGrayColor(), opad);
    }

    private static int getCost(List<FleetMemberAPI> members) {
        int cost = 0;
        for (FleetMemberAPI member : members) cost += Math.round(member.getUnmodifiedDeploymentPointsCost() * RELICS_PER_DP);
        return cost;
    }

    private static int getAvailable() {
        return (int) Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS);
    }
}
