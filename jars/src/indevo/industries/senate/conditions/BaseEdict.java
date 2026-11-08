package indevo.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.senate.EdictHelper;
import indevo.industries.senate.EdictRepo;
import indevo.industries.senate.EdictRequirement;
import indevo.industries.senate.industry.Senate;
import indevo.utils.helper.MiscIE;
import indevo.utils.timers.NewDayListener;
import indevo.utils.timers.TimeTracker;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseEdict extends BaseMarketConditionPlugin implements EdictAPI, NewDayListener {

    public int daysPassed = 1;
    public static final int BASE_MIN_RUNTIME = 186; //6M
    public static final int RELICS_PER_REMAINING_DAY = 1;

    public static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(BaseEdict.class).info(Text);
        }
    }

    public void apply(String id) {
        super.apply(id);
        // edict panel previews use a dummy market without primary entity
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
        return daysPassed > getMinRuntime(market);
    }

    public void removeWithPenalty() {
        applyFinishPenalty();

        if (!minimumRuntimePassed()) {
            RecentUnrest.get(market).add(getEarlyRemovalUnrest(), "Preliminary removal: " + getName());

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

    @Override
    public void removeEarlyWithRelics() {
        applyFinishPenalty();
        removeWithoutPenalty();
    }

    public void applyFinishPenalty() {
        int unrest = getFinishUnrest(market);
        if (unrest > 0) RecentUnrest.get(market).add(unrest, getName());
    }

    @Override
    public List<EdictRequirement> getRequirements(MarketAPI market) {
        List<EdictRequirement> requirements = new ArrayList<>();
        requirements.add(EdictHelper.getSenateRequirement(market));
        addRequirements(requirements, market);
        return requirements;
    }

    protected void addRequirements(List<EdictRequirement> requirements, MarketAPI market) {
    }

    @Override
    public boolean isPresenceConditionMet(MarketAPI market) {
        for (EdictRequirement requirement : getRequirements(market)) {
            if (!requirement.met) return false;
        }

        return true;
    }

    @Override
    public int getMinRuntime(MarketAPI market) {
        return withBetaCore(BASE_MIN_RUNTIME, market);
    }

    @Override
    public int getDaysPassed() {
        return daysPassed;
    }

    @Override
    public int getRemainingDays() {
        return Math.max(getMinRuntime(market) - daysPassed, 0);
    }

    @Override
    public int getRemovalUnrest() {
        return getFinishUnrest(market) + (minimumRuntimePassed() ? 0 : getEarlyRemovalUnrest());
    }

    @Override
    public int getRelicRemovalCost() {
        return getRemainingDays() * RELICS_PER_REMAINING_DAY;
    }

    @Override
    public int getRelicRemovalUnrest() {
        return getFinishUnrest(market);
    }

    public int getFinishUnrest(MarketAPI market) {
        return 0;
    }

    public int getEarlyRemovalUnrest() {
        return (int) Math.ceil(getRemainingDays() / 31.0 * EdictHelper.getEarlyRemovalUnrestMult(market));
    }

    @Override
    public boolean hasOtherColonyEffects(MarketAPI market) {
        return false;
    }

    @Override
    public void addOtherColonyEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
    }

    @Override
    public void addRuntimeText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        addRuntimeLine(tooltip, market, pad);
        addEarlyRemovalLine(tooltip, market, 3f);
    }

    protected void addRuntimeLine(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        tooltip.addPara("Minimum term: %s.", pad, Misc.getHighlightColor(), getMonthString(getMinRuntime(market)));
    }

    protected void addEarlyRemovalLine(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        String halved = EdictHelper.getCompoundsSenateInRange(market) != null ? ", halved by " + EdictHelper.COMPOUNDS_NAME : "";
        tooltip.addPara("Removing it earlier causes %s" + halved + ".", pad, Misc.getNegativeHighlightColor(), "1 unrest per remaining month");
    }

    protected String getRemainingTimeFormat() {
        return "This edict must stay in place for another %s before it can be removed without penalty.";
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        float opad = 10f;

        // codex tooltips have no real colony to read
        if (Global.CODEX_TOOLTIP_MODE || Global.getSettings().isShowingCodex()) {
            tooltip.addPara(getShortDesc(), opad);
            return;
        }

        int remaining = getRemainingDays();
        if (remaining > 0) tooltip.addPara(getRemainingTimeFormat(), opad, Misc.getHighlightColor(), getDurationString(remaining));
        else tooltip.addPara("This edict can be %s.", opad, Misc.getPositiveHighlightColor(), "removed without penalty");

        addEffectText(tooltip, market, opad);

        if (hasOtherColonyEffects(market)) {
            tooltip.addPara("Effects on other colonies:", opad);
            addOtherColonyEffectText(tooltip, market, 3f);
        }

        tooltip.addPara("Requirements:", opad);
        for (EdictRequirement requirement : getRequirements(market)) {
            Color color = requirement.met ? Misc.getTextColor() : Misc.getNegativeHighlightColor();
            tooltip.addPara(BaseIntelPlugin.BULLET + requirement.text, color, 3f);
        }
    }

    protected String getEdictName() {
        return EdictRepo.getShortName(getName());
    }

    protected int withBetaCore(int days, MarketAPI market) {
        return EdictHelper.senateHasBetaCore(market) ? days - Senate.EDICT_RUNTIME_DAY_RED : days;
    }

    public static String getMonthString(int days) {
        int months = (int) Math.ceil(days / 31f);
        return months + (months == 1 ? " month" : " months");
    }

    public static String getDurationString(int days) {
        if (days > 31) return getMonthString(days);
        return days + (days == 1 ? " day" : " days");
    }

    protected static List<MarketAPI> getOtherColoniesInSystem(MarketAPI market) {
        List<MarketAPI> others = new ArrayList<>();
        if (market.getStarSystem() == null) return others;

        for (MarketAPI other : MiscIE.getMarketsInLocation(market.getStarSystem(), market.getFactionId())) {
            if (!other.getId().equals(market.getId())) others.add(other);
        }

        return others;
    }

    protected static List<MarketAPI> getOtherPlayerColonies(MarketAPI market) {
        List<MarketAPI> others = new ArrayList<>();
        for (MarketAPI other : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (other.isPlayerOwned() && !other.getId().equals(market.getId())) others.add(other);
        }

        return others;
    }

    protected static EdictRequirement military(MarketAPI market) {
        return new EdictRequirement("Military presence on this colony", "Military presence", MiscIE.marketHasMilitaryIncludeRelays(market));
    }

    protected static EdictRequirement shipProduction(MarketAPI market) {
        return new EdictRequirement("Ship hull production on this colony", "Ship hull production", market.getCommodityData(Commodities.SHIPS).getMaxSupply() > 0);
    }

    protected static EdictRequirement twoColonies(MarketAPI market) {
        int colonies = getOtherColoniesInSystem(market).size() + 1;
        return new EdictRequirement("2 or more colonies in the star system (" + colonies + ")", "2+ colonies in system", colonies > 1);
    }

    protected EdictRequirement uniqueInSystem(MarketAPI market) {
        MarketAPI holder = null;
        for (MarketAPI other : getOtherColoniesInSystem(market)) {
            if (other.hasCondition(condition.getId())) {
                holder = other;
                break;
            }
        }

        String text = holder == null ? "No other " + getEdictName() + " in the star system" : getEdictName() + " already active on " + holder.getName();
        return new EdictRequirement(text, "Unique in system", holder == null);
    }
}
