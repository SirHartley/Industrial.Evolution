package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.industry.*;
import indevo.utils.helper.StringHelper;
import org.lazywizard.lazylib.MathUtils;

import java.util.LinkedList;
import java.util.List;

public class SwitchablePopulation extends PopulationAndInfrastructure implements SwitchableIndustryAPI {

    public static final int MAX_SIZE_FOR_CHANGE = 3;
    public static final int DAYS_TO_LOCK = 7;

    public float daysPassed = 0;
    public boolean locked = false;

    public static final List<SubIndustryData> industryList = new LinkedList<SubIndustryData>() {
        {
            add(new SubIndustryData("base_population_and_infrastructure", "Population & Infrastructure", "graphics/icons/industry/population.png", "IndEvo_pop_default") {
                @Override
                public SubIndustry newInstance() {
                    return new SubIndustry(this) {
                        @Override
                        public void apply() {
                            if (industry instanceof SwitchablePopulation)
                                ((SwitchablePopulation) industry).superApply(); //applies default pop&Infra
                        }

                        @Override
                        public String getImageName(MarketAPI market) {
                            float size = market.getSize();
                            if (size <= SIZE_FOR_SMALL_IMAGE) {
                                return Global.getSettings().getSpriteName("industry", "pop_low");
                            }
                            if (size >= SIZE_FOR_LARGE_IMAGE) {
                                return Global.getSettings().getSpriteName("industry", "pop_high");
                            }

                            return imageName;
                        }

                        @Override
                        public boolean isBase() {
                            return true;
                        }
                    };
                }
            });

            add(new SubIndustryData("underworld", "Underworld Governance", Global.getSettings().getSpriteName("IndEvo", "pop_underworld"), "IndEvo_pop_uw") {
                @Override
                public SubIndustry newInstance() {
                    return new UnderworldSubIndustry(this);
                }
            });

            add(new SubIndustryData("rural", "Rural Polity", Global.getSettings().getSpriteName("IndEvo", "pop_rural"), "IndEvo_pop_rural") {
                @Override
                public SubIndustry newInstance() {
                    return new RuralPolitySubIndustry(this);
                }
            });
            add(new SubIndustryData("hidden", "Hidden Arcology", Global.getSettings().getSpriteName("IndEvo", "pop_hidden"), "IndEvo_pop_hidden") {
                @Override
                public SubIndustry newInstance() {
                    return new HiddenArcologiesSubIndustry(this);
                }
            });
            add(new SubIndustryData("monks", "Monastic Order", Global.getSettings().getSpriteName("IndEvo", "pop_monks"), "IndEvo_pop_monks") {
                @Override
                public SubIndustry newInstance() {
                    return new MonasticOrderSubIndustry(this);
                }
            });
            add(new SubIndustryData("resort", "Resort Planet", Global.getSettings().getSpriteName("IndEvo", "pop_resort"), "IndEvo_pop_resort") {
                @Override
                public SubIndustry newInstance() {
                    return new ResortSubIndustry(this);
                }
            });
            add(new SubIndustryData("corpos", "Corporate Governance", Global.getSettings().getSpriteName("IndEvo", "pop_corpos"), "IndEvo_pop_corpos") {
                @Override
                public SubIndustry newInstance() {
                    return new CorporateGovernanceSubIndustry(this);
                }
            });
            add(new SubIndustryData("outpost", "Outpost", Global.getSettings().getSpriteName("IndEvo", "pop_outpost"), "IndEvo_pop_outpost") {
                @Override
                public SubIndustry newInstance() {
                    return new OutpostSubIndustry(this);
                }
            });
        }
    };

    @Override
    public List<SubIndustryData> getIndustryList() {
        return industryList;
    }

    private SubIndustryAPI current = null;

    public void setCurrent(SubIndustryAPI current) {
        setCurrent(current, false);
    }

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

            if (this.current != null) current.unapply();
            this.current = current;

            if (reapply) reapply();
        } else
            throw new IllegalArgumentException("Switchable Industry List of " + getClass().getName() + " does not contain " + current.getName());
    }

    @Override
    public SubIndustryAPI getCurrent() {
        return current;
    }

    public void apply() {
        supply.clear();
        demand.clear();

        if (!current.isInit()) current.init(this);
        current.apply();

        super.apply(true); //since popInfra does not override the baseIndustry overloaded apply we can call it here

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
    public String getId() {
        return Industries.POPULATION;
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
        if (current == null) setCurrent(getIndustryList().get(0).newInstance(), false);
        super.init(id, market);
    }

    @Override
    public String getCurrentImage() {
        return current.getImageName(market);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!locked && current != null && !current.isBase()) {
            daysPassed += Global.getSector().getClock().convertToDays(amount);
            if (daysPassed >= DAYS_TO_LOCK) {
                locked = true;

                Global.getSector().getCampaignUI().addMessage("The %s Government on %s has become permanent.", Misc.getTextColor(), current.getName(), market.getName(), Misc.getHighlightColor(), market.getFaction().getColor());
            }
        }
    }

    public boolean canChange() {
        return market.getSize() <= MAX_SIZE_FOR_CHANGE && !locked;
    }

    public boolean isNotChanged() {
        return current != null && current.isBase();
    }

    @Override
    public void unapply() {
        super.unapply();

        if (!current.isInit()) current.init(this);
        current.unapply();
    }

    public float getPatherInterest() {
        float currentNum = current.getPatherInterest(this);
        return currentNum > 100000000f ? super.getPatherInterest() : currentNum;
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostDescriptionSection(tooltip, mode);

        if (current != null) tooltip.addPara(current.getDescription().getText2(), 10f);

        if (canChange()) {
            tooltip.addPara("%s", 3f, Misc.getPositiveHighlightColor(), "Click to change government type.");
            tooltip.addPara("Changing the government style is only possible until %s and becomes permanent after %s.", 10f, Misc.getHighlightColor(),
                    "colony size " + MAX_SIZE_FOR_CHANGE,
                    DAYS_TO_LOCK + " " + StringHelper.getDayOrDays(DAYS_TO_LOCK));
        }

        if (!isNotChanged() && canChange()) {
            int daysRemaining = (int) Math.ceil(DAYS_TO_LOCK - daysPassed);
            tooltip.addPara("Days until permanent: %s", 3f, Misc.getHighlightColor(), daysRemaining + " " + StringHelper.getDayOrDays(daysRemaining));
        }
    }

    @Override
    protected String getDescriptionOverride() {
        if (current != null && current.isBase()) {
            int size = market.getSize();
            String cid = null;
            if (size >= 1 && size <= 9) {
                cid = "population_" + size;
                MarketConditionSpecAPI mcs = Global.getSettings().getMarketConditionSpec(cid);
                if (mcs != null) {
                    return spec.getDesc() + "\n\n" + mcs.getDesc();
                }
            }
        }

        return current == null ? super.getDescriptionOverride() : current.getDescription().getText1();
    }
}
