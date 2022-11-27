package indevo.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import indevo.utils.helper.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import indevo.industries.senate.industry.IndEvo_senate;
import indevo.items.installable.IndEvo_SpecialItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.ids.IndEvo_ids;
import indevo.utils.timers.IndEvo_TimeTracker;
import indevo.utils.timers.IndEvo_newDayListener;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

import static indevo.items.installable.IndEvo_SpecialItemEffectsRepo.NEURAL_COMPOUNDS_UNREST_RED;

public abstract class IndEvo_baseEdict extends BaseMarketConditionPlugin implements IndEvo_EdictAPI, IndEvo_newDayListener {

    public int daysPassed = 1;
    public static final int BASE_MIN_RUNTIME = 186; //6M

    public static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_baseEdict.class).info(Text);
        }
    }

    public void apply(String id) {
        super.apply(id);
        //this is needed so it doesn't crash when the condition gets applied to a dummy market for the rules handling, cause there is no star system.
        if (market.getPrimaryEntity() == null) return;

        if (!Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().addListener(this, true);
        }

        IndEvo_TimeTracker tt = IndEvo_TimeTracker.getTimeTrackerInstance();
        if (tt != null) tt.addMarketTimeTagTracker(market, getModId());
        daysPassed = IndEvo_TimeTracker.getTimeTagPassed(market, getModId());
    }

    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        Global.getSector().getListenerManager().removeListener(this);

        IndEvo_TimeTracker tt = IndEvo_TimeTracker.getTimeTrackerInstance();
        if (tt != null) tt.removeMarketTimeTagTracker(market, getModId());
    }

    @Override
    public void onNewDay() {
        daysPassed = IndEvo_TimeTracker.getTimeTagPassed(market, getModId());

        if (!isPresenceConditionMet(market)) {
            removeWithPenalty();
        }
    }

    public boolean minimumRuntimePassed() {
        return daysPassed > getMinRuntime();
    }

    public void removeWithPenalty() {
        applyFinishPenalty();

        if (!minimumRuntimePassed()) {
            RecentUnrest.get(market).add(getRemovalPenaltyUnrestDays(), "Preliminary removal: " + getName());

            Global.getSector().getCampaignUI().addMessage("An Edict at %s was removed before the minimum time passed. The rapid change in policy caused %s",
                    Global.getSettings().getColor("standardTextColor"), market.getName(), "additional unrest.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
        }

        removeWithoutPenalty();
    }

    public void removeWithoutPenalty() {
        Global.getSector().getListenerManager().removeListener(this);

        IndEvo_TimeTracker tt = IndEvo_TimeTracker.getTimeTrackerInstance();
        if (tt != null) tt.removeMarketTimeTagTracker(market, getModId());

        IndEvo_TimeTracker.removeTimeTag(market, getModId());
        market.removeSpecificCondition(condition.getIdForPluginModifications());
    }

    public boolean isPresenceConditionMet(MarketAPI market) {
        return senateWithItemInRange(market) || IndEvo_IndustryHelper.systemHasIndustry(IndEvo_ids.SENATE, market.getStarSystem(), market.getFaction());
    }

    public static boolean senateWithItemInRange(MarketAPI localMarket) {
        boolean senateInRange = false;

        for (MarketAPI market : Misc.getNearbyMarkets(localMarket.getLocationInHyperspace(), 10)) {
            if (!market.isPlayerOwned()) continue;

            if (Misc.getDistanceLY(market.getLocationInHyperspace(), localMarket.getLocationInHyperspace()) <= IndEvo_SpecialItemEffectsRepo.RANGE_LY_TEN) {
                if (market.hasIndustry(IndEvo_ids.SENATE) && !market.getIndustry(IndEvo_ids.SENATE).isBuilding() && market.getIndustry(IndEvo_ids.SENATE).getSpecialItem() != null) {
                    senateInRange = true;
                    break;
                }
            }
        }

        return senateInRange;
    }

    public boolean senateHasBetaCore() {
        if (!isPresenceConditionMet(market)) {
            return false;
        }

        List<MarketAPI> PlayerMarketsInSystem = IndEvo_IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFaction().getId());
        for (MarketAPI PlayerMarket : PlayerMarketsInSystem) {

            if (PlayerMarket.hasIndustry(IndEvo_ids.SENATE)) {
                return PlayerMarket.getIndustry(IndEvo_ids.SENATE).getAICoreId() != null && PlayerMarket.getIndustry(IndEvo_ids.SENATE).getAICoreId().equals(Commodities.BETA_CORE);
            }
        }

        return false;
    }

    @Override
    public void printRemovalPenaltyText(TextPanelAPI text) {
        int remainingDays = getRemainingDays();
        int penalty = getRemovalPenaltyUnrestDays();

        text.setFontSmallInsignia();

        if (remainingDays > 0) {
            String txt = penalty + " points of unrest";

            text.addParagraph("Removing this will cause " + txt + " in addition to any negative effects the edict would have caused on completion, if any.");
            text.highlightInLastPara(Misc.getNegativeHighlightColor(), txt);
        } else {
            text.addParagraph("There is no penalty for removing the current Edict as the minimum runtime has passed.");
            text.highlightInLastPara(Misc.getPositiveHighlightColor(), "no penalty");
        }

        text.setFontInsignia();
    }

    @Override
    public void printEdictRuntimeText(TextPanelAPI text) {
        int minRunTime = (int) Math.ceil(getMinRuntime() / 31f);
        String s1 = "" + minRunTime + " months";

        text.addPara("The minimum runtime of this Edict is " + s1);
        text.highlightInLastPara(Misc.getHighlightColor(), s1);
    }

    @Override
    public int getMinRuntime() {
        return senateHasBetaCore() ? BASE_MIN_RUNTIME - IndEvo_senate.EDICT_RUNTIME_DAY_RED : BASE_MIN_RUNTIME;
    }

    @Override
    public int getRemainingDays() {
        return Math.max((getMinRuntime() - daysPassed), 0);
    }

    @Override
    public int getRemovalPenaltyUnrestDays() {
        boolean hasItemSenate = senateWithItemInRange(market);
        float itemRed = hasItemSenate ? NEURAL_COMPOUNDS_UNREST_RED : 1f;

        return (int) Math.ceil((((double) getRemainingDays() * 1.0) / 31.0) * itemRed);
    }

    public void applyFinishPenalty() {
    }

    public boolean conditionUniqueInSystem(MarketAPI market, String condition_ID) {
        List<MarketAPI> marketsInLocation = IndEvo_IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFaction().getId());

        for (MarketAPI locMarket : marketsInLocation) {
            if (locMarket.getId().equals(market.getId())) continue;

            if (locMarket.hasCondition(condition_ID)) return false;
        }

        return true;
    }
}


