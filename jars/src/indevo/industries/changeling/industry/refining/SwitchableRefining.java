package indevo.industries.changeling.industry.refining;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;
import indevo.industries.changeling.industry.BaseSwitchableIndustry;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;

import java.util.LinkedList;
import java.util.List;

public class SwitchableRefining extends BaseSwitchableIndustry {

    public static final List<SubIndustryAPI> industryList = new LinkedList<SubIndustryAPI>(){{

        add(new SubIndustry("base_refining", "Specialized Refining", "graphics/icons/industry/refining.png", "IndEvo_base_refining") {
            @Override
            public void apply(Industry industry) {
                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();

                ind.demand(Commodities.HEAVY_MACHINERY, size - 2); // have to keep it low since it can be circular
                ind.demand(Commodities.ORE, size + 2);
                ind.demand(Commodities.RARE_ORE, size);

                ind.supply(Commodities.METALS, size);
                ind.supply(Commodities.RARE_METALS, size - 2);

                Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.ORE);
                applyDeficitToProduction(ind, 1, deficit, Commodities.METALS);

                deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.RARE_ORE);
                applyDeficitToProduction(ind, 1, deficit, Commodities.RARE_METALS);
            }
        });

        add(new SubIndustry("ore_refining", "Ore Refinery", Global.getSettings().getSpriteName("IndEvo", "ore_refining"), "IndEvo_ore_refining") {
            @Override
            public void apply(Industry industry) {
                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();

                ind.demand(Commodities.HEAVY_MACHINERY, size - 2); // have to keep it low since it can be circular
                ind.demand(Commodities.ORE, size + 4);

                ind.supply(Commodities.METALS, size + 2);

                Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.ORE);
                applyDeficitToProduction(ind, 1, deficit, Commodities.METALS);
            }
        });

        add(new SubIndustry("rare_refining", "Transplutonics Refinery", Global.getSettings().getSpriteName("IndEvo", "rare_ore_refining"), "IndEvo_rare_refining") {
            @Override
            public void apply(Industry industry) {
                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();

                ind.demand(Commodities.HEAVY_MACHINERY, size - 2); // have to keep it low since it can be circular
                ind.demand(Commodities.RARE_ORE, size + 2);

                ind.supply(Commodities.RARE_METALS, size);

                Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.RARE_ORE);
                applyDeficitToProduction(ind, 1, deficit, Commodities.RARE_METALS);
            }
        });
    }};

    @Override
    public List<SubIndustryAPI> getIndustryList() {
        return industryList;
    }
}
