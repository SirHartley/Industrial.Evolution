package indevo.industries.changeling.industry.refining;

import com.fs.starfarer.api.Global;
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
        add(new SubIndustryData("base_refining", "Refining", "graphics/icons/industry/refining.png", "IndEvo_base_refining", 10000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply() {
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

        add(new SubIndustryData("ore_refining", "Ore Refinery", Global.getSettings().getSpriteName("IndEvo", "ore_refining"), "IndEvo_ore_refining", 15000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply() {
                        if (industry instanceof SwitchableRefining)
                            ((SwitchableRefining) industry).superApply(); //applies default

                        int transplutonicsMod = -2;
                        int metalMod = 2;

                        industry.supply(industry.getId() + "_IndEvo_specialized", Commodities.RARE_METALS, transplutonicsMod, "Specialized Extraction");
                        industry.supply(industry.getId() + "_IndEvo_specialized", Commodities.METALS, metalMod, "Specialized Extraction");
                        ((BaseIndustry) industry).demand(industry.getId() + "_IndEvo_specialized", Commodities.RARE_ORE, transplutonicsMod, "Specialized Extraction");
                        ((BaseIndustry) industry).demand(industry.getId() + "_IndEvo_specialized", Commodities.ORE, metalMod, "Specialized Extraction");

                    }
                };
            }
        });

        add(new SubIndustryData("rare_refining", "Transplutonics Refinery", Global.getSettings().getSpriteName("IndEvo", "rare_ore_refining"), "IndEvo_rare_refining", 25000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply() {
                        if (industry instanceof SwitchableRefining)
                            ((SwitchableRefining) industry).superApply(); //applies default

                        int transplutonicsMod = 2;
                        int metalMod = -2;

                        industry.supply(industry.getId() + "_IndEvo_specialized", Commodities.RARE_METALS, transplutonicsMod, "Specialized Extraction");
                        industry.supply(industry.getId() + "_IndEvo_specialized", Commodities.METALS, metalMod, "Specialized Extraction");
                        ((BaseIndustry) industry).demand(industry.getId() + "_IndEvo_specialized", Commodities.RARE_ORE, transplutonicsMod, "Specialized Extraction");
                        ((BaseIndustry) industry).demand(industry.getId() + "_IndEvo_specialized", Commodities.ORE, metalMod, "Specialized Extraction");
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
    private String nameOverride = null;

    public void setCurrent(SubIndustryAPI current, boolean reapply) {
        String id = current.getId();
        boolean contains = false;

        for (SubIndustryData data : industryList)
            if (data.id.equals(id)) {
                contains = true;
                break;
            }

        if (contains) {
            current.init(this);

            if (this.current != null) this.current.unapply();
            this.current = current;

            if (reapply) reapply();
        } else
            throw new IllegalArgumentException("Switchable Industry List of " + getClass().getName() + " does not contain " + current.getName());
    }

    @Override
    public SubIndustryAPI getCurrent() {
        return current;
    }

    @Override
    public void apply() {
        if (!current.isInit()) current.init(this);
        current.apply();
        nameOverride = current.getName();

        super.apply(true); //since super does not override the baseIndustry overloaded apply we can call it here

        if (!isFunctional()) {
            supply.clear();
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        current.advance(amount);
    }

    public void superApply() {
        super.apply();
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
        return nameOverride == null ? super.getCurrentName() : nameOverride;
    }

    @Override
    public void init(String id, MarketAPI market) {
        if (current == null) setCurrent(getIndustryList().get(0).newInstance(), false);
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

    @Override
    public float getPatherInterest() {
        return super.getPatherInterest();
    }
}