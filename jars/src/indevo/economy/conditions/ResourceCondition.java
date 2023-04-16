package indevo.economy.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.Supercomputer;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.campaign.econ.MS_industries;
import data.campaign.econ.industries.MS_fabUpgrader;
import data.campaign.econ.industries.MS_modularFac;
import indevo.utils.helper.Settings;
import org.lwjgl.util.vector.Vector2f;

import static indevo.ids.Ids.COND_RESSOURCES;

public class ResourceCondition extends BaseMarketConditionPlugin {

    public static final float MAX_QUALITY_PENALTY = -0.5f;
    public static final float MAX_QUALITY_PENALTY_WITH_CORRUPTED = -0.4f;
    public static final float MAX_QUALITY_PENALTY_WITH_PRISTINE = -0.3f;

    @Override
    public void apply(String id) {
        super.apply(id);
        if(Settings.SCRAPYARD) applyParts();
        applySupComIncome();
    }

    public void applySupComIncome() {
        for (MarketAPI m : Misc.getMarketsInLocation(market.getContainingLocation(), market.getFactionId())) {
            if (m.hasIndustry(Ids.SUPCOM)) {
                return;
            }
        }

        Vector2f locInHyper = market.getLocationInHyperspace();
        Pair<MarketAPI, Float> p = Supercomputer.getNearestSupCom(locInHyper);
        float distMult = Supercomputer.getDistanceIncomeMult(locInHyper);

        if (distMult > 0f && p != null) {
            market.getIncomeMult().modifyMult(getModId(), 1 + distMult, p.one.getName() + " Simulation Engine");
        }
    }

    public void applyParts() {
        int size = market.getSize();

        for (Industry ind : market.getIndustries()) {
            if (ind instanceof HeavyIndustry) {
                int supply = 0;
                int demand = 0;

                supply = Math.min(size - 3, 3);
                demand = size;

                applyPartsSupply((BaseIndustry) ind, supply);
                applyPartsDemands((BaseIndustry) ind, demand);
            }

            if (!Global.getSettings().getModManager().isModEnabled("shadow_ships")) continue;

            if (ind instanceof MS_modularFac || ind instanceof MS_fabUpgrader) {
                int supply = 0;
                int demand = 0;

                switch (ind.getId()) {
                    case MS_industries.MODULARFACTORIES:
                        supply = size - 3;
                        break;
                    case MS_industries.PARALLEL_PRODUCTION:
                        supply = size - 2;
                        break;
                    case MS_industries.MILITARY_LINES:
                        demand = size - 4;
                        break;
                    case MS_industries.SHIPYARDS:
                        demand = size - 2;
                        break;
                }

                applyPartsSupply((BaseIndustry) ind, supply);
                applyPartsDemands((BaseIndustry) ind, demand);
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);

        market.getIncomeMult().unmodify(getModId());
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodify(getModId());

        for (Industry ind : market.getIndustries()) {
            unapplyAdditionalSupDem((BaseIndustry) ind);
        }
    }

    @Override
    public boolean showIcon() {
        return false;
    }

    public String getModId() {
        return condition.getId();
    }

    private void applyPartsSupply(BaseIndustry ind, int supply) {
        if (supply == 0) return;

        /*//unmodify nanoforge production boni
        if(ind.getSpecialItem() != null){
            ind.getSupply(ItemIds.PARTS).getQuantity().unmodifyFlat("ind_sb");
        }
*/
        ind.supply(ItemIds.PARTS, supply);

        ind.getSupply(ItemIds.PARTS).getQuantity().unmodify(getModId());
        if (ind.getSpecialItem() != null) {
            if (ind.getSpecialItem().getId().equals(Items.CORRUPTED_NANOFORGE)) {
                ind.getSupply(ItemIds.PARTS).getQuantity().modifyFlat(getModId(), -1, "Corrupted Nanoforge");
            }

            if (ind.getSpecialItem().getId().equals(Items.PRISTINE_NANOFORGE)) {
                ind.getSupply(ItemIds.PARTS).getQuantity().modifyFlat(getModId(), -3, "Pristine Nanoforge");
            }
        }

        Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.METALS, Commodities.RARE_METALS);
        applyDeficitToProduction(ind, 3, deficit,
                ItemIds.PARTS);
    }

    private void applyPartsDemands(BaseIndustry ind, int demand) {
        if (demand == 0) return;

        ind.demand(ItemIds.PARTS, demand);

        ind.getDemand(ItemIds.PARTS).getQuantity().unmodify(getModId());
        float qualityPenalty = MAX_QUALITY_PENALTY;

        if (ind.getSpecialItem() != null) {
            if (ind.getSpecialItem().getId().equals(Items.CORRUPTED_NANOFORGE)) {
                ind.getDemand(ItemIds.PARTS).getQuantity().modifyFlat(getModId(), -1, "Corrupted Nanoforge");
                qualityPenalty = MAX_QUALITY_PENALTY_WITH_CORRUPTED;
            }

            if (ind.getSpecialItem().getId().equals(Items.PRISTINE_NANOFORGE)) {
                ind.getDemand(ItemIds.PARTS).getQuantity().modifyFlat(getModId(), -2, "Pristine Nanoforge");
                qualityPenalty = MAX_QUALITY_PENALTY_WITH_PRISTINE;
            }
        }
        //prod. qual penalty
        ind.getMarket().getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodify(getModId());

        Pair<String, Integer> deficit = ind.getMaxDeficit(ItemIds.PARTS);
        if (deficit.two > 0) {
            float red = (deficit.two / (demand * 1f)) * qualityPenalty;
            ind.getMarket().getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(), red, Global.getSettings().getCommoditySpec(ItemIds.PARTS).getName() + " demand not met");
        }

        //deficit
        int maxDeficit = 2; // missing ship parts do not affect the output much, they just reduce quality.
        if (deficit.two > maxDeficit) deficit.two = maxDeficit;

        applyDeficitToProduction(ind, 3, deficit,
                Commodities.SHIPS);
    }

    public void unapplyAdditionalSupDem(BaseIndustry ind) {
        ind.supply(ItemIds.PARTS, 0, "");
        ind.demand(ItemIds.PARTS, 0, "");
    }

    protected void applyDeficitToProduction(Industry ind, int index, Pair<String, Integer> deficit, String... commodities) {
        String[] var7 = commodities;
        int var6 = commodities.length;

        for (int var5 = 0; var5 < var6; ++var5) {
            String commodity = var7[var5];
            if (!ind.getSupply(commodity).getQuantity().isUnmodified()) {
                ind.supply(String.valueOf(index), commodity, -(Integer) deficit.two, BaseIndustry.getDeficitText((String) deficit.one));
            }
        }
    }

    public static void applyRessourceCond(MarketAPI m) {
        if (m.isInEconomy() && !m.hasCondition(COND_RESSOURCES)) m.addCondition(COND_RESSOURCES);
    }
}
