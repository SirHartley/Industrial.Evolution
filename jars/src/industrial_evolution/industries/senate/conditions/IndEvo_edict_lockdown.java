package industrial_evolution.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import industrial_evolution.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import industrial_evolution.industries.senate.industry.IndEvo_senate;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;

/*Edict:	Wartime lockdown
		Upside	+Fleet size/defence, lock the stability at what it currently is (via dynamic addition in case of penalties)
		Downside	reduce accessability of colony by -100%, 3 points of unrest at end
		Prerequisite	military presence
		Min. Runtime:	3M, auto-end
*/
public class IndEvo_edict_lockdown extends IndEvo_baseEdict {

    private final float exportRed = 0.30F;
    private final int stabBonus = 5;
    public static final int BASE_MIN_RUNTIME = 93;

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
    public int getMinRuntime() {
        return senateHasBetaCore() ? BASE_MIN_RUNTIME - IndEvo_senate.EDICT_RUNTIME_DAY_RED : BASE_MIN_RUNTIME;
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
            RecentUnrest.get(market).add(getRemovalPenaltyUnrestDays() + 3, "Preliminary removal: " + getName());

            Global.getSector().getCampaignUI().addMessage("A Lockdown on %s was ended before the minimum time passed. The rapid change in policy caused %s",
                    Global.getSettings().getColor("standardTextColor"), market.getName(), "additional unrest.", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
        }

        removeWithoutPenalty();
    }

    @Override
    public void applyFinishPenalty() {
        RecentUnrest.get(market).add(1, getName());
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if (getRemainingDays() > 31) {
            tooltip.addPara("This edict must stay in place until the lockdown is lifted, which will take another %s.", 10f, Misc.getHighlightColor(), (int) Math.ceil(getRemainingDays() / 31.0) + " Months");
        } else {
            tooltip.addPara("This edict must stay in place until the lockdown is lifted, which will take another %s.", 10f, Misc.getHighlightColor(), getRemainingDays() + " Days");
        }

        tooltip.addPara("Stability is %s.",
                10f, Misc.getPositiveHighlightColor(), "increased by " + stabBonus);

        tooltip.addPara("Fleet size %s.",
                3f, Misc.getHighlightColor(), "increased by 100% (flat)");
        tooltip.addPara("Ground defence rating %s.",
                3f, Misc.getHighlightColor(), "increased by 300%");
        tooltip.addPara("Industry exports %s.",
                3f, Misc.getNegativeHighlightColor(), "reduced by 70%");

        tooltip.addPara("Requires a %s in the star system and %s on this planet.",
                10f, Misc.getHighlightColor(), new String[]{"Senate", "Military Presence"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Military drafts, mobilization of the wartime " +
                "industrial complex and full planetary lockdown can drastically increase the defensive potential of a world - at a cost.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        text.addParagraph("Stability increased by 5.");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "increased by 5");
        text.addParagraph("Fleet size increased by 100% (flat).");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "increased by 100% (flat)");
        text.addParagraph("Ground defence rating increased by 200%.");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "increased by 300%");
        text.addParagraph("Industry exports reduced by 70%.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "reduced by 70%");
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return "Requires military presence on this planet.";
    }

    @Override
    public String getShortDesc() {
        return "Massively increases ground defense rating, fleet size and stability - at a large accessability penalty. " +
                "Lasts 3 months and causes unrest after it ends.";
    }

    @Override
    public boolean isPresenceConditionMet(MarketAPI market) {
        return super.isPresenceConditionMet(market)
                && IndEvo_IndustryHelper.marketHasMilitaryIncludeRelays(market);
    }
}
