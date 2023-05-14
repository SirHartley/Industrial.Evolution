package indevo.industries.petshop.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;

public class PetShop extends BaseIndustry {

    @Override
    public void apply() {
        super.apply(true);

        supply(ItemIds.PET_FOOD, market.getSize());

        if (market.isPlayerOwned() && isFunctional()) {
            SubmarketPlugin sub = Misc.getLocalResources(market);
            if (sub instanceof LocalResourcesSubmarketPlugin) {
                LocalResourcesSubmarketPlugin lr = (LocalResourcesSubmarketPlugin) sub;
                float mult = Global.getSettings().getFloat("stockpileMultExcess");

                for (MutableCommodityQuantity q : supply.values()){
                    if(q.getQuantity().getModifiedInt() > 0){
                        lr.getStockpilingBonus(q.getCommodityId()).modifyFlat(getModId(0), market.getSize() * mult);
                    }
                }
            }
        }

        if (!isFunctional()) supply.clear();
    }
}
