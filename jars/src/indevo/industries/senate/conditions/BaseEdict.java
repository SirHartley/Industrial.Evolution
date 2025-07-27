package indevo.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.senate.industry.Senate;
import indevo.items.installable.SpecialItemEffectsRepo;
import indevo.utils.helper.MiscIE;
import indevo.utils.timers.NewDayListener;
import indevo.utils.timers.TimeTracker;

import java.util.List;

import static indevo.items.installable.SpecialItemEffectsRepo.NEURAL_COMPOUNDS_UNREST_RED;

public abstract class BaseEdict extends BaseMarketConditionPlugin implements EdictAPI, NewDayListener {

    public int daysPassed = 1;
    public static final int BASE_MIN_RUNTIME = 186; //6M

    public static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(BaseEdict.class).info(Text);
        }
    }

    public void apply(String id) {
        super.apply(id);
        //this is needed so it doesn't crash when the condition gets applied to a dummy market for the rules handling, cause there is no star system.
        if (market.getPrimaryEntity() == null || Global.getSettings().isShowingCodex()) return;

        if (!Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().addListener(this, true);
        }

        TimeTracker tt = TimeTracker.getTimeTrackerInstance();
        if (tt != null) tt.addMarketTimeTagTracker(market, getModId());
        daysPassed = TimeTracker.getTimeTagPassed(market, getModId());
    }

    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null || Global.getSettings().isShowingCodex()) return;

        Global.getSector().getListenerManager().removeListener(this);

        TimeTracker tt = TimeTracker.getTimeTrackerInstance();
        if (tt != null) tt.removeMarketTimeTagTracker(market, getModId());
    }

    @Override
    public void onNewDay() {
        daysPassed = TimeTracker.getTimeTagPassed(market, getModId());

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

        TimeTracker tt = TimeTracker.getTimeTrackerInstance();
        if (tt != null) tt.removeMarketTimeTagTracker(market, getModId());

        TimeTracker.removeTimeTag(market, getModId());
        market.removeSpecificCondition(condition.getIdForPluginModifications());
    }

    public boolean isPresenceConditionMet(MarketAPI market) {
        return senateWithItemInRange(market) || MiscIE.systemHasIndustry(Ids.SENATE, market.getStarSystem(), market.getFaction());
    }

    public static boolean senateWithItemInRange(MarketAPI localMarket) {
        boolean senateInRange = false;

        for (MarketAPI market : Misc.getNearbyMarkets(localMarket.getLocationInHyperspace(), SpecialItemEffectsRepo.RANGE_LY_TWELVE)) {
            if (!market.isPlayerOwned()) continue;

            if (market.hasIndustry(Ids.SENATE) && !market.getIndustry(Ids.SENATE).isBuilding() && market.getIndustry(Ids.SENATE).getSpecialItem() != null) {
                senateInRange = true;
                break;
            }
        }

        return senateInRange;
    }


    public boolean senateHasBetaCore() {
        if (!isPresenceConditionMet(market)) {
            return false;
        }

        List<MarketAPI> PlayerMarketsInSystem = MiscIE.getMarketsInLocation(market.getStarSystem(), market.getFaction().getId());
        for (MarketAPI PlayerMarket : PlayerMarketsInSystem) {

            if (PlayerMarket.hasIndustry(Ids.SENATE)) {
                return PlayerMarket.getIndustry(Ids.SENATE).getAICoreId() != null && PlayerMarket.getIndustry(Ids.SENATE).getAICoreId().equals(Commodities.BETA_CORE);
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
        return senateHasBetaCore() ? BASE_MIN_RUNTIME - Senate.EDICT_RUNTIME_DAY_RED : BASE_MIN_RUNTIME;
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
        List<MarketAPI> marketsInLocation = MiscIE.getMarketsInLocation(market.getStarSystem(), market.getFaction().getId());

        for (MarketAPI locMarket : marketsInLocation) {
            if (locMarket.getId().equals(market.getId())) continue;

            if (locMarket.hasCondition(condition_ID)) return false;
        }

        return true;
    }
}


