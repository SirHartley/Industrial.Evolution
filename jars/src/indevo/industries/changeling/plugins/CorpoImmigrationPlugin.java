package indevo.industries.changeling.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;

import static indevo.industries.changeling.industry.population.CorporateGovernanceSubIndustry.MAX_GROWTH_TAG;

public class CorpoImmigrationPlugin extends CoreImmigrationPluginImpl {
    public static final float IMMIGRATION_COST_MULT = 2f;

    public CorpoImmigrationPlugin(MarketAPI market) {
        super(market);
    }

    protected void applyIncentives(PopulationComposition inc, boolean uiUpdateOnly, float f) {
//		if (market.getName().equals("Jangala")) {
//			System.out.println("ewfwfew");
//		}
        if (!market.isImmigrationIncentivesOn()) return;

        float points = -getImmigrationHazardPenalty(market) + INCENTIVE_POINTS_EXTRA;
        //float cost = INCENTIVE_CREDITS_PER_POINT * points * f;
        float cost = market.getImmigrationIncentivesCost() * f;

        if (points > 0) {
            inc.getWeight().modifyFlat("inc_incentives", points, "Hazard pay");
            if (!uiUpdateOnly) {
                market.setIncentiveCredits(market.getIncentiveCredits() + cost);
            }
        }

    }

    @Override
    public void advance(float days, boolean uiUpdateOnly) {
        float f = days / 30f; // incoming is per month

        boolean firstTime = !market.wasIncomingSetBefore();
        Global.getSettings().profilerBegin("Computing incoming");
        market.setIncoming(computeIncoming(uiUpdateOnly, f));
        Global.getSettings().profilerEnd();

        if (uiUpdateOnly) return;

        int iter = 1;
        if (firstTime) {
            iter = 100;
        }

        for (int i = 0; i < iter; i++) {

            if (iter > 1) {
                f = (iter - i) * 0.1f;
            }

            PopulationComposition pop = market.getPopulation();
            PopulationComposition inc = market.getIncoming();

            for (String id : inc.getComp().keySet()) {
                pop.add(id, inc.get(id) * f);
            }

            float min = getWeightForMarketSize(market.getSize());
            float max = getWeightForMarketSize(market.getSize() + 1);
            float newWeight = pop.getWeightValue() + inc.getWeightValue() * f;

            if (Global.getSector().isInNewGameAdvance()) newWeight = min;

            if (newWeight < min) {
                decreaseMarketSize();
                newWeight = min; //might have to remove that to avoid it interfering with setting weight max
            }

            if (newWeight > max) {
                if (!market.hasTag(MAX_GROWTH_TAG)) increaseMarketSize();
                newWeight = max;
            }

            pop.setWeight(newWeight);
            pop.normalize();

            // up to 5% of the non-faction population gets converted to faction, per month, more or less
            float conversionFraction = 0.05f * market.getStabilityValue() / 10f;
            conversionFraction *= f;
            if (conversionFraction > 0) {
                pop.add(market.getFactionId(), (pop.getWeightValue() - pop.get(market.getFactionId())) * conversionFraction);
            }

            // add some poor/pirate population at stability below 5
            float pirateFraction = 0.01f * Math.max(0, (5f - market.getStabilityValue()) / 5f);
            pirateFraction *= f;
            if (pirateFraction > 0) {
                pop.add(Factions.PIRATES, pop.getWeightValue() * pirateFraction);
                pop.add(Factions.POOR, pop.getWeightValue() * pirateFraction);
            }

            for (String fid : new ArrayList<String>(pop.getComp().keySet())) {
                if (Global.getSector().getFaction(fid) == null) {
                    pop.getComp().remove(fid);
                }
            }

            pop.normalize();
        }
    }

    public void decreaseMarketSize() {
        if (market.getSize() <= 3) {
            market.getPopulation().setWeight(getWeightForMarketSize(market.getSize())); //i think that's how you get the min?
            market.getPopulation().normalize();
            return;
        }

        CoreImmigrationPluginImpl.reduceMarketSize(market);

        if (market.isPlayerOwned()) {
            MessageIntel intel = new MessageIntel("Colony Shrinkage - " + market.getName(), Misc.getBasePlayerColor());
            intel.addLine(BaseIntelPlugin.BULLET + "Size reduced to %s",
                    Misc.getTextColor(),
                    new String[] {"" + (int)Math.round(market.getSize())},
                    Misc.getNegativeHighlightColor());

            intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
            intel.setSound(BaseIntelPlugin.getSoundMajorPosting());
            Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
        }
    }
}
