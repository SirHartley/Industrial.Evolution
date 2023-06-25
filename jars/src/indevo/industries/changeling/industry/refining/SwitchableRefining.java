package indevo.industries.changeling.industry.refining;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.Refining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Pair;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;
import indevo.utils.helper.IndustryHelper;

import java.util.LinkedList;
import java.util.List;

public class SwitchableRefining extends Refining implements SwitchableIndustryAPI {

    public static final List<SubIndustryData> industryList = new LinkedList<SubIndustryData>() {{
        add(new SubIndustryData("base_refining", "graphics/icons/industry/refining.png", "Refining", "IndEvo_base_refining") {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply(Industry industry) {
                        if (industry instanceof SwitchableRefining)
                            ((SwitchableRefining) industry).superApply(); //applies default
                    }

                    @Override
                    public boolean isBase() {
                        return true;
                    }
                };
            }
        });

        add(new SubIndustryData("ore_refining", Global.getSettings().getSpriteName("IndEvo", "ore_refining"), "Ore Refinery", "IndEvo_ore_refining") {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply(Industry industry) {
                        BaseIndustry ind = (BaseIndustry) industry;
                        int size = ind.getMarket().getSize();
                        ind.demand(Commodities.HEAVY_MACHINERY, size - 2); // have to keep it low since it can be circular
                        ind.demand(Commodities.ORE, size + 4);
                        ind.demand(Commodities.RARE_ORE, size - 3);

                        ind.supply(Commodities.METALS, size);
                        ind.supply(Commodities.RARE_METALS, size - 2);

                        Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.ORE);
                        IndustryHelper.applyDeficitToProduction(ind, 1, deficit, Commodities.METALS);

                        deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.RARE_ORE);
                        IndustryHelper.applyDeficitToProduction(ind, 1, deficit, Commodities.RARE_METALS);
                    }
                };
            }
        });

        add(new SubIndustryData("rare_refining", Global.getSettings().getSpriteName("IndEvo", "rare_ore_refining"), "Transplutonics Refinery", "IndEvo_rare_refining") {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply(Industry industry) {
                        BaseIndustry ind = (BaseIndustry) industry;
                        int size = ind.getMarket().getSize();
                        ind.demand(Commodities.HEAVY_MACHINERY, size - 2); // have to keep it low since it can be circular
                        ind.demand(Commodities.ORE, size - 1);
                        ind.demand(Commodities.RARE_ORE, size + 2);

                        ind.supply(Commodities.METALS, size);
                        ind.supply(Commodities.RARE_METALS, size - 2);

                        Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.ORE);
                        IndustryHelper.applyDeficitToProduction(ind, 1, deficit, Commodities.METALS);

                        deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.RARE_ORE);
                        IndustryHelper.applyDeficitToProduction(ind, 1, deficit, Commodities.RARE_METALS);
                    }
                };
            }
        });
    }};

    @Override
    public List<SubIndustryData> getIndustryList() {
        return industryList;
    }

    private SubIndustryAPI current = null;

    public void setCurrent(SubIndustryAPI current) {
        if (industryList.contains(current)) {
            this.current = current;
            reapply();
        } else throw new IllegalArgumentException("Switchable Industry List of " + getClass().getName() + " does not contain " + current.getName());
    }

    @Override
    public SubIndustryAPI getCurrent() {
        return current;
    }

    public void apply() {
        supply.clear();
        demand.clear();

        super.apply(true); //since super does not override the baseIndustry overloaded apply we can call it here

        current.apply(this);

        if (!isFunctional()) {
            supply.clear();
        }
    }

    public void superApply() {
        supply.clear();
        demand.clear();

        super.apply();
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public String getId() {
        return Industries.REFINING;
    }

    @Override
    public String getModId() {
        return super.getModId();
    }

    @Override
    public String getModId(int index) {
        return super.getModId(index);
    }

    @Override
    public String getCurrentName() {
        return current.getName();
    }

    @Override
    public void init(String id, MarketAPI market) {
        if (current == null) current = getIndustryList().get(0).newInstance();
        super.init(id, market);
    }

    @Override
    public String getCurrentImage() {
        return current.getImageName(market);
    }

    public boolean canChange() {
        return true;
    }

    @Override
    protected String getDescriptionOverride() {
        return current == null ? super.getDescriptionOverride() : current.getDescription().getText1();
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }
}