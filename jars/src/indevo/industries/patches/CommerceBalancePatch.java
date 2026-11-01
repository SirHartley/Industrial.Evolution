package indevo.industries.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.Industry.AICoreDescriptionMode;
import com.fs.starfarer.api.campaign.econ.Industry.IndustryTooltipMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.TradeCenter;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import patchlib.api.context.AfterContext;
import patchlib.api.context.BeforeContext;
import patchlib.api.context.Context;
import patchlib.api.context.FieldReadContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.FieldMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectFieldRead;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.MethodQuerySpec;

import java.awt.*;

@Patch(target = @ClassMatch(type = TradeCenter.class))
public class CommerceBalancePatch {

    public static final float ALT_IMPROVE_BONUS = 10f;
    public static final float ALPHA_CORE_TARIFF_MULT = 0.75f;
    public static final String ONE_PER_SYSTEM = "Can only have one in the star system";

    private static final MethodQuerySpec GET_MOD_ID = MethodQuery.create().methodName("getModId").parameterCount(1).build();

    @After(target = @MethodMatch(methodName = "isAvailableToBuild", parameterCount = 0))
    public static void afterIsAvailableToBuild(AfterContext context) {
        if (!isActive() || !(Boolean) context.getReturnValue()) return;
        if (!isOnlyOneInSystem(((Industry) context.getSelf()).getMarket())) context.setReturnValue(false);
    }

    @After(target = @MethodMatch(methodName = "getUnavailableReason", parameterCount = 0))
    public static void afterGetUnavailableReason(AfterContext context) {
        if (!isActive()) return;
        if (!isOnlyOneInSystem(((Industry) context.getSelf()).getMarket())) context.setReturnValue(ONE_PER_SYSTEM);
    }

    @After(target = @MethodMatch(methodName = "addRightAfterDescriptionSection", parameterCount = 2))
    public static void afterAddRightAfterDescriptionSection(AfterContext context) {
        if (!isActive() || context.getArg(1) != IndustryTooltipMode.ADD_INDUSTRY) return;
        if (!((Industry) context.getSelf()).isAvailableToBuild()) return;

        TooltipMakerAPI tooltip = (TooltipMakerAPI) context.getArg(0);
        tooltip.addPara("%s", 10f, Misc.getHighlightColor(), ONE_PER_SYSTEM);
    }

    @Before(target = @MethodMatch(methodName = "applyAlphaCoreModifiers", parameterCount = 0))
    public static void beforeApplyAlphaCoreModifiers(BeforeContext context) {
        BaseIndustry industry = (BaseIndustry) context.getSelf();
        MarketAPI market = industry.getMarket();
        if (!isActive() || !market.isPlayerOwned()) return;

        market.getTariff().modifyMult(getModId(context, 1), ALPHA_CORE_TARIFF_MULT, "Alpha core (" + industry.getNameForModifier() + ")");
        context.skipOriginal(null);
    }

    // also runs from BaseIndustry.unapply, so the tariff goes with the core and with the industry
    @After(target = @MethodMatch(methodName = "applyNoAICoreModifiers", parameterCount = 0))
    public static void afterApplyNoAICoreModifiers(AfterContext context) {
        ((Industry) context.getSelf()).getMarket().getTariff().unmodifyMult(getModId(context, 1));
    }

    @Before(target = @MethodMatch(methodName = "addAlphaCoreDescription", parameterCount = 2))
    public static void beforeAddAlphaCoreDescription(BeforeContext context) {
        Industry industry = (Industry) context.getSelf();
        if (!isActive() || !industry.getMarket().isPlayerOwned()) return;

        TooltipMakerAPI tooltip = (TooltipMakerAPI) context.getArg(0);
        AICoreDescriptionMode mode = (AICoreDescriptionMode) context.getArg(1);
        Color highlight = Misc.getHighlightColor();

        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        String text = pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. Reduces tariffs by %s.";
        String upkeep = (int) ((1f - BaseIndustry.UPKEEP_MULT) * 100f) + "%";
        String demand = "" + BaseIndustry.DEMAND_REDUCTION;
        String tariff = StringHelper.getAbsPercentString(ALPHA_CORE_TARIFF_MULT, true);

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            TooltipMakerAPI image = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(Commodities.ALPHA_CORE).getIconName(), 48);
            image.addPara(text, 0f, highlight, upkeep, demand, tariff);
            tooltip.addImageWithText(10f);
        } else {
            tooltip.addPara(text, 10f, highlight, upkeep, demand, tariff);
        }

        context.skipOriginal(null);
    }

    @RedirectFieldRead(field = @FieldMatch(fieldName = "IMPROVE_BONUS"), owner = @ClassMatch(type = TradeCenter.class))
    public static void redirectImproveBonus(FieldReadContext context) {
        context.setResult(isActive() ? ALT_IMPROVE_BONUS : context.read());
    }

    public static boolean isActive() {
        return Settings.getBoolean(Settings.COMMERCE_BALANCE_CHANGES);
    }

    private static boolean isOnlyOneInSystem(MarketAPI market) {
        StarSystemAPI system = market.getStarSystem();
        if (system == null) return true;

        return MiscIE.isOnlyInstanceInSystemExcludeMarket(Ids.COMMERCE, system, market, market.getFaction());
    }

    private static String getModId(Context context, int index) {
        return context.getMethod(GET_MOD_ID).call(index);
    }
}
