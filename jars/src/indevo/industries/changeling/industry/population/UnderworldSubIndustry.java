package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.industries.EngineeringHub;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.utils.ModPlugin;
import indevo.utils.helper.IndustryHelper;

import java.sql.Time;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure.*;

public class UnderworldSubIndustry extends SubIndustry {

    public static final float BONUS_ILLEGAL_OUTPUT = 1f;
    public static final float BONUS_SMALL_PATROLS = 2f;
    public static final float BASE_STABILITY_AMT = 2f;

    public UnderworldSubIndustry(SubIndustryData data) {
        super(data);
    }

    private void increaseIllegalCommodityOutput(MarketAPI market) {
        for (CommodityOnMarketAPI commodity : market.getCommoditiesCopy()) {
            if (commodity.isIllegal()) {
                commodity.setDemandLegal(true);
                commodity.setSupplyLegal(true);

                for (Industry ind : market.getIndustries()) {
                    MutableCommodityQuantity supply = ind.getSupply(commodity.getId());
                    if (supply.getQuantity().getModifiedInt() > 0)
                        supply.getQuantity().modifyFlat(getId(), BONUS_ILLEGAL_OUTPUT, getName());
                }
            }
        }
    }

    public static final String HAS_BEEN_EXTORTED_KEY = "$HasBeenExtortedByUnderworld";
    public static final float EXTORTION_SHARE = 0.05f;
    public IntervalUtil extortionInterval = new IntervalUtil(10f, 10f);
    public List<TimedCommodityQuantity> stolenGoodsList = new ArrayList<>();

    public Map<Float, String> getSortedStolenGoodsMap(){
        SortedMap<Float, String> sortedStolenGoodsDescending = new TreeMap<>(new Comparator<Float>() {
            @Override
            public int compare(Float o1, Float o2) {
                return o2.compareTo(o1);
            }
        });

        Map<String, Float> stolenGoods = new HashMap<>();

        for (TimedCommodityQuantity q : stolenGoodsList) IndustryHelper.addOrIncrement(stolenGoods, q.id, q.amt);
        for (Map.Entry<String, Float> e : stolenGoods.entrySet()) sortedStolenGoodsDescending.put(e.getValue(), e.getKey());

        return sortedStolenGoodsDescending;
    }

    public static class TimedCommodityQuantity{
        public String id;
        public float amt;
        private final IntervalUtil interval;
        private boolean isElapsed = false;

        private static final float TTR = Global.getSector().getClock().getSecondsPerDay() * 30f;

        public TimedCommodityQuantity(String id, float amt) {
            this.id = id;
            this.amt = amt;
            this.interval = new IntervalUtil(TTR, TTR);
        }

        public void advance(float amt){
            interval.advance(amt);
            if (interval.intervalElapsed()) isElapsed = true;
        }
    }

    @Override
    public void advance(float amt) {
        super.advance(amt);
        if (!market.isPlayerOwned()) return;

        extortionInterval.advance(amt);

        for (TimedCommodityQuantity q : new ArrayList<>(stolenGoodsList)) {
            q.advance(amt);
            if (q.isElapsed) stolenGoodsList.remove(q);
        }

        if (extortionInterval.intervalElapsed()){
            if (market.getStarSystem() == null) return;
            if (Global.getSettings().isDevMode()) ModPlugin.log("DEV REPORT: underworld extorting traders");

            Map<String, Float> bounty = new HashMap<>();

            for (CampaignFleetAPI fleet : market.getStarSystem().getFleets()){
                String factionID = fleet.getFaction().getId();

                if (!fleet.getMemoryWithoutUpdate().getBoolean(HAS_BEEN_EXTORTED_KEY) && !Factions.PLAYER.equals(factionID) && !factionID.equals(Misc.getCommissionFactionId())){
                    if (Misc.isTrader(fleet) || Misc.isSmuggler(fleet) || Misc.isScavenger(fleet)){
                        for (CargoStackAPI stack : fleet.getCargo().getStacksCopy()){
                            if (stack.isCommodityStack()) {
                                String id = stack.getCommodityId();
                                float share = (float) Math.ceil(stack.getSize() * EXTORTION_SHARE);
                                IndustryHelper.addOrIncrement(bounty, id, share);
                                stolenGoodsList.add(new TimedCommodityQuantity(id, share));
                            }
                        }

                        fleet.getMemoryWithoutUpdate().set(HAS_BEEN_EXTORTED_KEY, true);
                    }
                }
            }

            CargoAPI cargo = Misc.getStorageCargo(market);
            if (cargo != null){
                for (Map.Entry<String, Float> e : bounty.entrySet()){
                    cargo.addCommodity(e.getKey(), e.getValue());
                }
            }
        }
    }

    private void increaseFleetSize(MarketAPI market) {
        //market.getStats().getDynamic().getStats().get(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(getId(), BONUS_SMALL_PATROLS, "Bored underworld spacers");
    }

    @Override
    public void addRightAfterDescription(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescription(tooltip, mode);

        float opad = 10f;
        tooltip.addSectionHeading("Extorted goods (past 30 days)", Alignment.MID, opad);
        tooltip.beginTable(market.getFaction(), 20f, "Commodity", 250f, "Amount", 140f);

        int i = 0;
        int max = 10;

        Map<Float, String> sortedStolenGoods = getSortedStolenGoodsMap();

        for (Map.Entry<Float, String > e : sortedStolenGoods.entrySet()) {
            tooltip.addRow(Global.getSettings().getCommoditySpec(e.getValue()).getName(), Math.round(e.getKey()));
            i++;
            if (i == max) break;
        }

        //add the table to the tooltip
        tooltip.addTable("No donations yet, check back later.", sortedStolenGoods.size() - 10, opad);

    }

    @Override
    public void apply() {
        MarketAPI market = industry.getMarket();
        BaseIndustry ind = (BaseIndustry) industry;
        SwitchablePopulation population = ((SwitchablePopulation) industry);

        population.unapply();

        //population.superApply(); //applies default pop&Infra
        modifyStability(industry, market, population.getModId(3));
        increaseIllegalCommodityOutput(industry.getMarket());
        increaseFleetSize(industry.getMarket());

        int size = market.getSize();

        ind.demand(Commodities.FOOD, size);

        if (!market.hasCondition(Conditions.HABITABLE)) {
            ind.demand(Commodities.ORGANICS, size - 1);
        }

        int luxuryThreshold = 2;

        ind.demand(Commodities.DOMESTIC_GOODS, size - 3);
        ind.demand(Commodities.LUXURY_GOODS, size - luxuryThreshold);
        ind.demand(Commodities.DRUGS, size);
        ind.demand(Commodities.ORGANS, size - 2);

        ind.demand(Commodities.SUPPLIES, Math.min(size, 3));

        ind.supply(Commodities.CREW, size - 3);
        ind.supply(Commodities.DRUGS, size - 3);
        ind.supply(Commodities.ORGANS, size - 4);


        Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.DOMESTIC_GOODS);
        /*if (deficit.two <= 0) {
            market.getStability().modifyFlat(population.getModId(0), 1, "Domestic goods demand met");
        } else {
            market.getStability().unmodifyFlat(population.getModId(0));
        }*/

        deficit = ind.getMaxDeficit(Commodities.LUXURY_GOODS);
        if (deficit.two <= 0 && size > luxuryThreshold) {
            market.getStability().modifyFlat(population.getModId(1), 1, "Luxury goods demand met");
        } else {
            market.getStability().unmodifyFlat(population.getModId(1));
        }

        deficit = ind.getMaxDeficit(Commodities.FOOD);
        if (!market.hasCondition(Conditions.HABITABLE)) {
            deficit = ind.getMaxDeficit(Commodities.FOOD, Commodities.ORGANICS);
        }
        if (deficit.two > 0) {
            market.getStability().modifyFlat(population.getModId(2), -deficit.two, getDeficitText(deficit.one));
        } else {
            market.getStability().unmodifyFlat(population.getModId(2));
        }


        boolean spaceportFirstInQueue = false;
        for (ConstructionQueue.ConstructionQueueItem item : market.getConstructionQueue().getItems()) {
            IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(item.id);
            if (spec.hasTag(Industries.TAG_SPACEPORT)) {
                spaceportFirstInQueue = true;
            }
            break;
        }
        if (spaceportFirstInQueue && Misc.getCurrentlyBeingConstructed(market) != null) {
            spaceportFirstInQueue = false;
        }
        if (!market.hasSpaceport() && !spaceportFirstInQueue) {
            float accessibilityNoSpaceport = Global.getSettings().getFloat("accessibilityNoSpaceport");
            market.getAccessibilityMod().modifyFlat(population.getModId(0), accessibilityNoSpaceport, "No spaceport");
        }

        float sizeBonus = getAccessibilityBonus(size);
        if (sizeBonus > 0) {
            market.getAccessibilityMod().modifyFlat(population.getModId(1), sizeBonus, "Colony size");
        }


        float stability = market.getPrevStability();
        float stabilityQualityMod = FleetFactoryV3.getShipQualityModForStability(stability);
        float doctrineQualityMod = market.getFaction().getDoctrine().getShipQualityContribution();

        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlatAlways(population.getModId(0), stabilityQualityMod,
                "Stability");

        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlatAlways(population.getModId(1), doctrineQualityMod,
                Misc.ucFirst(market.getFaction().getEntityNamePrefix()) + " fleet doctrine");


        float stabilityDefenseMult = 0.25f + stability / 10f * 0.75f;
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMultAlways(population.getModId(),
                stabilityDefenseMult, "Stability");

        float baseDef = PopulationAndInfrastructure.getBaseGroundDefenses(market.getSize());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyFlatAlways(population.getModId(),
                baseDef, "Base value for a size " + market.getSize() + " colony");

        if (HAZARD_INCREASES_DEFENSE) {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMultAlways(population.getModId(1),
                    Math.max(market.getHazardValue(), 1f), "Colony hazard rating");
        }

        market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).modifyFlat(population.getModId(), getMaxIndustries(market.getSize()), null);

        FactionDoctrineAPI doctrine = market.getFaction().getDoctrine();
        float doctrineShipsMult = FleetFactoryV3.getDoctrineNumShipsMult(doctrine.getNumShips());
        float marketSizeShipsMult = FleetFactoryV3.getNumShipsMultForMarketSize(market.getSize());
        float deficitShipsMult = FleetFactoryV3.getShipDeficitFleetSizeMult(market);
        float stabilityShipsMult = FleetFactoryV3.getNumShipsMultForStability(stability);

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlatAlways(population.getModId(0), marketSizeShipsMult,
                "Colony size");

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMultAlways(population.getModId(1), doctrineShipsMult,
                Misc.ucFirst(market.getFaction().getEntityNamePrefix()) + " fleet doctrine");

        if (deficitShipsMult != 1f) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMult(population.getModId(2), deficitShipsMult,
                    getDeficitText(Commodities.SHIPS));
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMultAlways(population.getModId(2), deficitShipsMult,
                    getDeficitText(Commodities.SHIPS).replaceAll("shortage", "demand met"));
        }

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMultAlways(population.getModId(3), stabilityShipsMult,
                "Stability");


        // chance of spawning officers and admins; some industries further modify this
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(population.getModId(0), OFFICER_BASE_PROB);
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(population.getModId(1),
                OFFICER_PROB_PER_SIZE * Math.max(0, market.getSize() - 3));

        market.getStats().getDynamic().getMod(Stats.OFFICER_ADDITIONAL_PROB_MULT_MOD).modifyFlat(population.getModId(0), OFFICER_ADDITIONAL_BASE_PROB);
        market.getStats().getDynamic().getMod(Stats.OFFICER_IS_MERC_PROB_MOD).modifyFlat(population.getModId(0), OFFICER_BASE_MERC_PROB);

        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).modifyFlat(population.getModId(0), ADMIN_BASE_PROB);
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).modifyFlat(population.getModId(1),
                ADMIN_PROB_PER_SIZE * Math.max(0, market.getSize() - 3));

        modifyStability2(industry, market, population.getModId(3));

        market.addTransientImmigrationModifier((PopulationAndInfrastructure) industry);
    }


    public static void modifyStability(Industry industry, MarketAPI market, String modId) {

        //turn the default income mult around
        float stability = market.getPrevStability();
        float incomeMult = 1f; //if below 5 increase by 1.1 per point, if above reduce until it's zero

        if (stability >= 5) {
            incomeMult = Math.max(0, 5f / stability);
        } else if (stability < 5) {
            incomeMult = (float) Math.pow(1.2, 5 - stability);
        }

        market.getIncomeMult().modifyMultAlways(modId, incomeMult, "Stability (Underworld)");
        market.getUpkeepMult().modifyMultAlways(modId, getUpkeepHazardMult(market.getHazardValue()), "Hazard rating");

        for (MutableStat.StatMod mod : market.getStability().getFlatMods().values()){
            if (mod.desc.toLowerCase().contains("_ms")) market.getStability().unmodify(mod.source);
        }

        market.getStability().modifyFlat("_" + modId + "_ms", BASE_STABILITY_AMT, "Base value");

        float inFactionSupply = 0f;
        float totalDemand = 0f;
        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            if (com.isNonEcon()) continue;

            int d = com.getMaxDemand();
            if (d <= 0) continue;

            totalDemand += d;
            CommodityMarketDataAPI cmd = com.getCommodityMarketData();
            int inFaction = Math.max(Math.min(com.getMaxSupply(), com.getAvailable()),
                    Math.min(cmd.getMaxShipping(market, true), cmd.getMaxExport(market.getFactionId())));
            if (inFaction > d) inFaction = d;
            if (inFaction < d) inFaction = Math.max(Math.min(com.getMaxSupply(), com.getAvailable()), 0);

            inFactionSupply += Math.max(0, Math.min(inFaction, com.getAvailable()));
        }

        if (totalDemand > 0) {
            float max = Global.getSettings().getFloat("upkeepReductionFromInFactionImports");
            float f = inFactionSupply / totalDemand;
            if (f < 0) f = 0;
            if (f > 1) f = 1;
            if (f > 0) {
                float mult = Math.round(100f - (f * max * 100f)) / 100f;
                String desc = "Demand supplied in-faction (" + (int) Math.round(f * 100f) + "%)";
                if (f == 1f) desc = "All demand supplied in-faction";
                market.getUpkeepMult().modifyMultAlways(modId + "ifi", mult, desc);
            } else {
                market.getUpkeepMult().modifyMultAlways(modId + "ifi", 1f, "All demand supplied out-of-faction; no upkeep reduction");
            }
        }


        if (market.isPlayerOwned() && market.getAdmin().isPlayer()) {
            int penalty = getMismanagementPenalty();
            if (penalty > 0) {
                market.getStability().modifyFlat("_" + modId + "_mm", -penalty, "Mismanagement penalty");
            } else if (penalty < 0) {
                market.getStability().modifyFlat("_" + modId + "_mm", -penalty, "Management bonus");
            } else {
                market.getStability().unmodifyFlat("_" + modId + "_mm");
            }
        } else {
            market.getStability().unmodifyFlat(modId + "_mm");
        }

        if (!market.hasCondition(Conditions.COMM_RELAY)) {
            market.getStability().modifyFlat(CommRelayCondition.COMM_RELAY_MOD_ID, CommRelayCondition.NO_RELAY_PENALTY, "No active comm relay in-system");
        }
    }
}
