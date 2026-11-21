package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.ui.UiCanvas;
import indevo.utils.ui.UiImages;

import java.util.*;


public class UnderworldSubIndustry extends SubIndustry {

    public static final float BONUS_ILLEGAL_OUTPUT = 1f;
    public static final float BONUS_SMALL_PATROLS = 2f;
    public static final float BASE_STABILITY_AMT = 2f;

    public UnderworldSubIndustry(SubIndustryData data) {
        super(data);
    }

    public static final String HAS_BEEN_EXTORTED_KEY = "$HasBeenExtortedByUnderworld";
    public static final float EXTORTION_SHARE = 0.05f;
    public static final float GOODS_CELL = 40f;
    public static final float GOODS_GAP = 4f;
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

        for (TimedCommodityQuantity q : stolenGoodsList) MiscIE.addOrIncrement(stolenGoods, q.id, q.amt);
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
                                MiscIE.addOrIncrement(bounty, id, share);
                                stolenGoodsList.add(new TimedCommodityQuantity(id, share));
                            }
                        }

                        fleet.getMemoryWithoutUpdate().set(HAS_BEEN_EXTORTED_KEY, true);
                    }
                }
            }

            CargoAPI cargo = MiscIE.getStorageCargo(market);
            if (cargo != null){
                for (Map.Entry<String, Float> e : bounty.entrySet()){
                    cargo.addCommodity(e.getKey(), e.getValue());
                }
            }
        }
    }

    @Override
    public boolean hasOngoing() {
        return true;
    }

    @Override
    public void addOngoing(TooltipMakerAPI tooltip, float width) {
        tooltip.addPara("Extorted in the past 30 days", 10f);

        List<Map.Entry<Float, String>> goods = new ArrayList<>(getSortedStolenGoodsMap().entrySet());
        if (goods.isEmpty()) {
            tooltip.setBulletedListMode(BaseIntelPlugin.INDENT);
            tooltip.addPara("No donations yet, check back later.", Misc.getGrayColor(), 3f);
            tooltip.setBulletedListMode(null);
            return;
        }

        CustomPanelAPI row = Global.getSettings().createCustom(width, GOODS_CELL, new UiCanvas());
        UiImages.iconrow(row, 0f, 0f, width, goods, GOODS_CELL, GOODS_CELL, GOODS_GAP, 1,
                (p, x, y, w, h, e) -> UiImages.stack(p, x, y, w, UiImages.commoditySprite(e.getValue()), Math.round(e.getKey()), 0, null),
                e -> Global.getSettings().getCommoditySpec(e.getValue()).getName() + " " + Math.round(e.getKey()));
        tooltip.addCustom(row, 6f);
    }

    // the host pop applied first, this re-sets its base values and income mult under the host's own keys
    @Override
    public void apply() {
        BaseIndustry ind = (BaseIndustry) industry;
        int size = market.getSize();
        int luxuryThreshold = 2;

        ind.demand(Commodities.DOMESTIC_GOODS, size - 3);
        ind.demand(Commodities.LUXURY_GOODS, size - luxuryThreshold);
        ind.demand(Commodities.DRUGS, size);
        ind.demand(Commodities.ORGANS, size - 2);

        ind.supply(Commodities.DRUGS, size - 3);
        ind.supply(Commodities.ORGANS, size - 4);

        market.getStability().unmodifyFlat(modId(0));

        Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.LUXURY_GOODS);
        if (deficit.two <= 0 && size > luxuryThreshold) {
            market.getStability().modifyFlat(modId(1), 1, "Luxury goods demand met");
        } else {
            market.getStability().unmodifyFlat(modId(1));
        }

        market.getIncomeMult().modifyMultAlways(modId(3), getIncomeMult(market.getPrevStability()), "Stability (Underworld)");
        market.getStability().modifyFlat("_" + modId(3) + "_ms", BASE_STABILITY_AMT, "Base value");

        for (CommodityOnMarketAPI commodity : market.getCommoditiesCopy()) {
            if (!commodity.isIllegal()) continue;
            commodity.setDemandLegal(true);
            commodity.setSupplyLegal(true);
        }
    }

    //if below 5 increase by 1.2 per point, if above reduce until it's zero
    public static float getIncomeMult(float stability) {
        if (stability >= 5) return Math.max(0, 5f / stability);
        return (float) Math.pow(1.2, 5 - stability);
    }

    // after the industry applied, so only industries that make an illegal good on their own get the bonus
    @Override
    public void applyToOther(Industry ind) {
        for (MutableCommodityQuantity supply : ind.getAllSupply()) {
            supply.getQuantity().unmodifyFlat(getId());

            CommodityOnMarketAPI commodity = market.getCommodityData(supply.getCommodityId());
            if (commodity == null || !commodity.isIllegal()) continue;

            if (supply.getQuantity().getModifiedInt() > 0)
                supply.getQuantity().modifyFlat(getId(), BONUS_ILLEGAL_OUTPUT, getName());
        }
    }

    @Override
    public void unapplyFromOther(Industry ind) {
        for (MutableCommodityQuantity supply : ind.getAllSupply()) supply.getQuantity().unmodifyFlat(getId());
    }
}
