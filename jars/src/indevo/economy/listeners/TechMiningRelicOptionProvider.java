package indevo.economy.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.economy.plugins.RelicConfirmDialog;
import indevo.utils.helper.RelicHelper;
import indevo.utils.plugins.BaseSimpleBaseIndustryOptionProvider;

public class TechMiningRelicOptionProvider extends BaseSimpleBaseIndustryOptionProvider implements EconomyTickListener {

    public static final String LABEL = "Boost Extraction";
    public static final String MEM_BOOST = "$IndEvo_relicTechMining";
    public static final String MOD_ID = "IndEvo_relicTechMining";
    public static final float FIND_MULT = 2f;
    public static final float COST_BASE = 20f;
    public static final int COST_MIN = 5;
    public static final String LORE = "Relic scanners pick out sealed chambers beneath the exhausted dig sites. The crews can push them for a month before their ancient sensor arrays burn out.";

    private static final String[] RUINS = {Conditions.RUINS_VAST, Conditions.RUINS_EXTENSIVE, Conditions.RUINS_WIDESPREAD, Conditions.RUINS_SCATTERED};

    public static void register() {
        Global.getSector().getListenerManager().addListener(new TechMiningRelicOptionProvider(), true);
    }

    public static boolean isBoosted(MarketAPI market) {
        return market.getMemoryWithoutUpdate().contains(MEM_BOOST);
    }

    public static int getCost(MarketAPI market) {
        return Math.max(COST_MIN, Math.round(COST_BASE * TechMining.getTechMiningRuinSizeModifier(market) * getFindLevel(market)));
    }

    private static float getFindLevel(MarketAPI market) {
        MemoryAPI mem = market.getMemoryWithoutUpdate();
        return mem.contains(TechMining.TECH_MINING_MULT) ? mem.getFloat(TechMining.TECH_MINING_MULT) : 1f;
    }

    private static void applyBoost(MarketAPI market) {
        market.getStats().getDynamic().getStat(Stats.TECH_MINING_MULT).modifyMult(MOD_ID, FIND_MULT);
        market.getMemoryWithoutUpdate().set(MEM_BOOST, true);
    }

    private static void removeBoost(MarketAPI market) {
        market.getStats().getDynamic().getStat(Stats.TECH_MINING_MULT).unmodifyMult(MOD_ID);
        market.getMemoryWithoutUpdate().unset(MEM_BOOST);
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return ind != null
                && ind.getMarket() != null
                && Industries.TECHMINING.equals(ind.getId())
                && ind.isFunctional()
                && ind.getMarket().isPlayerOwned();
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return !isBoosted(opt.ind.getMarket());
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return LABEL;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        MarketAPI market = opt.ind.getMarket();

        tooltip.addPara("Doubles next month's finds.", 0f);

        if (isBoosted(market)) tooltip.addPara("Boost already active.", Misc.getNegativeHighlightColor(), 10f);
        else RelicHelper.addCostLine(tooltip, market, getCost(market), 10f);

        tooltip.addPara(LORE, Misc.getGrayColor(), 10f);
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        MarketAPI market = opt.ind.getMarket();
        int cost = getCost(market);

        new RelicConfirmDialog(market, cost, null) {
            @Override
            protected void addDescription(TooltipMakerAPI info) {
                String findLevel = Math.round(getFindLevel(market) * 100f) + "%";
                info.addPara("Doubles next month's finds. Ruins: %s, remaining yield: %s.", 10f, Misc.getHighlightColor(), getRuinsName(market), findLevel);
            }

            @Override
            protected void apply() {
                if (isBoosted(market)) return;

                RelicHelper.take(market, cost);
                applyBoost(market);
            }
        }.show(ui);
    }

    @Override
    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
        if (!Industries.TECHMINING.equals(ind.getId()) || ind.getMarket() == null || !isBoosted(ind.getMarket())) return;
        tooltip.addPara(LABEL + ": next month's finds doubled", 10f, Misc.getHighlightColor(), LABEL);
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    // ListenerUtil runs after the sector listeners, so CoreScript has already made this month's finds
    @Override
    public void reportEconomyMonthEnd() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!isBoosted(market)) continue;

            Industry ind = market.getIndustry(Industries.TECHMINING);
            boolean waitsForFind = ind != null && market.isPlayerOwned() && !ind.isFunctional();
            if (!waitsForFind) removeBoost(market);
        }
    }

    private static String getRuinsName(MarketAPI market) {
        for (String id : RUINS) {
            MarketConditionAPI cond = market.getCondition(id);
            if (cond != null) return cond.getName();
        }

        return "None";
    }
}
