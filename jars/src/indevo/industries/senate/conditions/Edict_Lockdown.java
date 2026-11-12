package indevo.industries.senate.conditions;

import indevo.industries.senate.EdictEffect;
import indevo.industries.senate.EdictSupplyChange;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.senate.EdictHelper;
import indevo.industries.senate.EdictRequirement;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/*Edict:	Wartime lockdown
		Upside	+Fleet size/defence, lock the stability at what it currently is (via dynamic addition in case of penalties)
		Downside	reduce accessability of colony by -100%, 3 points of unrest at end
		Prerequisite	military presence
		Min. Runtime:	3M, auto-end
*/
public class Edict_Lockdown extends BaseEdict {

    private final float exportRed = 0.30F;
    private final int stabBonus = 5;
    public static final int BASE_MIN_RUNTIME = 93;
    private static final int EARLY_END_UNREST = 3;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getStability().modifyFlat(getModId(), stabBonus, getName());

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).modifyMult(getModId(), 2F, getName()); //2f
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(getModId(), 1F, getName()); //1.5f
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(getModId(), 3f, getName()); //2f

        ArrayList<String> doNotModify = new ArrayList<>();
        doNotModify.add(Industries.POPULATION);
        doNotModify.add(Industries.MILITARYBASE);
        doNotModify.add(Industries.PATROLHQ);
        doNotModify.add(Industries.HIGHCOMMAND);
        doNotModify.add(Industries.ORBITALSTATION);
        doNotModify.add(Industries.ORBITALSTATION_HIGH);
        doNotModify.add(Industries.ORBITALSTATION_MID);
        doNotModify.add(Industries.BATTLESTATION);
        doNotModify.add(Industries.BATTLESTATION_HIGH);
        doNotModify.add(Industries.ORBITALSTATION_MID);
        doNotModify.add(Industries.GROUNDDEFENSES);
        doNotModify.add(Industries.HEAVYBATTERIES);
        doNotModify.add(Industries.STARFORTRESS);
        doNotModify.add(Industries.STARFORTRESS_HIGH);
        doNotModify.add(Industries.STARFORTRESS_MID);

        for (Industry ind : market.getIndustries()) {
            for (MutableCommodityQuantity supply : ind.getAllSupply()) {
                supply.getQuantity().modifyMultAlways(getModId(), exportRed, getName());
            }

            if (doNotModify.contains(ind.getId())) {
                continue;
            }

            for (MutableCommodityQuantity demand : ind.getAllDemand()) {
                demand.getQuantity().modifyMultAlways(getModId(), exportRed, getName());
            }
        }
    }

    @Override
    public int getMinRuntime(MarketAPI market) {
        return withBetaCore(BASE_MIN_RUNTIME, market);
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getStability().unmodify(getModId());

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).unmodify(getModId());
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(getModId());

        for (Industry ind : market.getIndustries()) {
            for (MutableCommodityQuantity supply : ind.getAllSupply()) {
                supply.getQuantity().unmodify(getModId());
            }

            for (MutableCommodityQuantity demand : ind.getAllDemand()) {
                demand.getQuantity().unmodify(getModId());
            }
        }
    }

    @Override
    public void onNewDay() {
        super.onNewDay();

        if (minimumRuntimePassed()) {
            applyFinishPenalty();
            Global.getSector().getCampaignUI().addMessage("The %s on " + market.getName() + " %s.",
                    Global.getSettings().getColor("standardTextColor"), getName(), "has ended", Misc.getHighlightColor(), Misc.getPositiveHighlightColor());

            removeWithoutPenalty();
        }
    }

    @Override
    public void removeWithPenalty() {
        if (!minimumRuntimePassed()) {
            RecentUnrest.get(market).add(getEarlyRemovalUnrest() + EARLY_END_UNREST, "Preliminary removal: " + getName());

            Global.getSector().getCampaignUI().addMessage("A Lockdown on %s was ended before the minimum time passed. The rapid change in policy caused %s",
                    Global.getSettings().getColor("standardTextColor"), market.getName(), "additional unrest.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
        }

        removeWithoutPenalty();
    }

    @Override
    public int getRemovalUnrest() {
        return minimumRuntimePassed() ? 0 : getEarlyRemovalUnrest() + EARLY_END_UNREST;
    }

    @Override
    public boolean endsOnItsOwn() {
        return true;
    }

    @Override
    public int getMaxEarlyRemovalUnrest(MarketAPI market) {
        return super.getMaxEarlyRemovalUnrest(market) + EARLY_END_UNREST;
    }

    @Override
    public int getFinishUnrest(MarketAPI market) {
        return 1;
    }

    @Override
    protected String getRemainingTimeFormat() {
        return "This edict must stay in place until the lockdown is lifted, which will take another %s.";
    }

    @Override
    public List<EdictEffect> getEffects(MarketAPI market) {
        List<EdictEffect> effects = new ArrayList<>();
        effects.add(stability(market, stabBonus));
        effects.add(EdictEffect.good("Ground defences", "x3"));
        effects.add(EdictEffect.good("Fleet size", "+100%"));
        effects.add(EdictEffect.good("Patrol spawn rate", "x2"));
        effects.add(EdictEffect.bad("Supply and civilian demand", "x" + Misc.getRoundedValueMaxOneAfterDecimal(exportRed)));
        return effects;
    }

    @Override
    public List<EdictSupplyChange> getSupplyChanges(MarketAPI market) {
        return getSupplyChanges(market, false, null, s -> s.modifyMult(getModId(), exportRed));
    }

    @Override
    protected void addRuntimeLine(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        tooltip.addPara("Lasts %s, then ends on its own and causes %s.", pad,
                new Color[]{Misc.getHighlightColor(), Misc.getNegativeHighlightColor()},
                getMonthString(getMinRuntime(market)), getFinishUnrest(market) + " unrest");
    }

    @Override
    protected void addEarlyRemovalLine(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        String halved = EdictHelper.getCompoundsSenateInRange(market) != null ? " (halved by " + EdictHelper.COMPOUNDS_NAME + ")" : "";
        tooltip.addPara("Ending it earlier causes %s" + halved + " plus %s instead.", pad, Misc.getNegativeHighlightColor(),
                "1 unrest per remaining month", EARLY_END_UNREST + " unrest");
    }

    @Override
    public String getShortDesc() {
        return "Massively increases ground defense rating, fleet size and stability - at a large accessability penalty. " +
                "Lasts 3 months and causes unrest after it ends.";
    }

    @Override
    protected void addRequirements(List<EdictRequirement> requirements, MarketAPI market) {
        requirements.add(military(market));
    }
}
