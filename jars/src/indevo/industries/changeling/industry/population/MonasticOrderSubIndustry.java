package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.utils.helper.StringHelper;
import org.lazywizard.lazylib.MathUtils;

import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager.AI_CORE_ADMIN_INTEREST;
import static com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl.getWeightForMarketSizeStatic;

public class MonasticOrderSubIndustry extends SubIndustry implements EconomyTickListener {

    /*
Monastic Orders
•	x-50% positive income
•	xNo luddic cells
•	xStability can’t fall below 3 or go above 7
•	xLocked at size 5
•	xMilitary industries -upkeep
•   xMarines stored here passively gather experience
•	Randomly builds known frigates and destroyers, rarely cruisers (by hand with a special hullmod)*/

    public static final int MAX_STAB = 7;
    public static final int MIN_STAB = 3;
    public static final float INCOME_RED = 0.5f;
    public static final int MAX_MARKET_SIZE = 5;
    public static final float MONASTIC_UPKEEP_RED = 0.7f;
    public static final float EXP_GAIN_PER_TICK = 1000f;

    @Override
    public void reportEconomyTick(int iterIndex) {
        //todo change this to 15% per month up to 500 marines, then distributed max val
        PlayerFleetPersonnelTracker.PersonnelAtEntity e = PlayerFleetPersonnelTracker.getInstance().getPersonnelAtLocation(Commodities.MARINES, Misc.getStorage(market).getSubmarket());
        if (e != null) e.data.addXP(EXP_GAIN_PER_TICK);
    }

    @Override
    public void reportEconomyMonthEnd() {

    }

    public static class MonasticOrderTooltipAdder extends BaseIndustryOptionProvider {
        public static void register(){
            ListenerManagerAPI manager = Global.getSector().getListenerManager();
            if (!manager.hasListenerOfClass(MonasticOrderTooltipAdder.class)) manager.addListener(new MonasticOrderTooltipAdder(), true);
        }

        @Override
        public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
            Industry pop = ind.getMarket().getIndustry(Industries.POPULATION);
            boolean isTarget = pop instanceof SwitchablePopulation && ((SwitchablePopulation) pop).getCurrent() instanceof MonasticOrderSubIndustry;
            return !isTarget;
        }

        @Override
        public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
            if (isUnsuitable(ind, true)) return;
            float opad = 10f;

            boolean military = ind.getSpec().getTags().contains("military");

            tooltip.addSectionHeading("Governance Effects: Monastic Orders", Alignment.MID, opad);

           if (military){
                tooltip.addPara("Military buildings: %s decreased by %s", opad, Misc.getTextColor(), Misc.getPositiveHighlightColor(), "upkeep", StringHelper.getAbsPercentString(MONASTIC_UPKEEP_RED, true));
            } else {
                tooltip.addPara("No effect on this building.", opad);
            }
        }
    }

    public MonasticOrderSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();

        MonasticOrderTooltipAdder.register();
        Global.getSector().getListenerManager().addListener(this);
        correctStability();

        market.getIncomeMult().modifyMult(((SwitchablePopulation) industry).getModId(), INCOME_RED, getName());
        if (market.getSize() >= MAX_MARKET_SIZE) market.getPopulation().setWeight(getWeightForMarketSizeStatic(market.getSize()));

        for (Industry ind : market.getIndustries()) {
           if (ind.getSpec().getTags().contains("military")) {
                ind.getUpkeep().modifyMult(getId(), MONASTIC_UPKEEP_RED, getName());
            }
        }

        //MarineExperience handling
    }

    @Override
    public void unapply() {
        super.unapply();
        String id = ((SwitchablePopulation) industry).getModId();
        market.getStability().unmodify(id);
        market.getIncomeMult().unmodify(id);

        Global.getSector().getListenerManager().removeListener(this);

        for (Industry ind : market.getIndustries()) {
            if (ind.getSpec().getTags().contains("military")) {
                ind.getUpkeep().unmodify(getId());
            }
        }
    }

    public void correctStability() {
        int current = market.getStability().getModifiedInt();
        int absDiff = Math.abs(MathUtils.clamp(current, MIN_STAB, MAX_STAB) - current);
        int mod = current > MAX_STAB ? absDiff * -1 : current < MIN_STAB ? absDiff : 0;
        market.getStability().modifyFlat(((SwitchablePopulation) industry).getModId(), mod, "Base value");
    }

    //negate pather interest
    @Override
    public float getPatherInterest(Industry industry) {
        return -getLuddicPathMarketInterest(industry.getMarket());
    }

    public static float getLuddicPathMarketInterest(MarketAPI market) {
        if (market.getFactionId().equals(Factions.LUDDIC_PATH)) return 0f;
        float total = 0f;

        String aiCoreId = market.getAdmin().getAICoreId();
        if (aiCoreId != null) {
            total += AI_CORE_ADMIN_INTEREST;
        }

        for (Industry ind : market.getIndustries()) {
            if (ind instanceof SwitchablePopulation) continue;
            total += ind.getPatherInterest();
        }

        if (total > 0) {
            total += new Random(market.getName().hashCode()).nextFloat() * 0.1f;
        }

        if (market.getFactionId().equals(Factions.LUDDIC_CHURCH)) {
            total *= 0.1f;
        }

        return total;
    }
}
