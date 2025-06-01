package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SwitchablePopulation extends PopulationAndInfrastructure implements SwitchableIndustryAPI {

    public static final int DAYS_TO_LOCK = 7;

    public float daysPassed = 0;
    public boolean locked = false;

    public static final List<SubIndustryData> industryList = new LinkedList<SubIndustryData>() {
        {
            add(new SubIndustryData("base_population_and_infrastructure", "Population & Infrastructure", "graphics/icons/industry/population.png", "IndEvo_pop_default", 10000) {
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

            add(new SubIndustryData("underworld", "Underworld Governance", Global.getSettings().getSpriteName("IndEvo", "pop_underworld"), "IndEvo_pop_uw", 45000) {
                @Override
                public SubIndustry newInstance() {
                    return new UnderworldSubIndustry(this);
                }
            });

            add(new SubIndustryData("rural", "Rural Polity", Global.getSettings().getSpriteName("IndEvo", "pop_rural"), "IndEvo_pop_rural",80000) {
                @Override
                public SubIndustry newInstance() {
                    return new RuralPolitySubIndustry(this);
                }
            });
            add(new SubIndustryData("hidden", "Hidden Arcology", Global.getSettings().getSpriteName("IndEvo", "pop_hidden"), "IndEvo_pop_hidden", 125000) {
                @Override
                public SubIndustry newInstance() {
                    return new HiddenArcologiesSubIndustry(this);
                }
            });

            add(new SubIndustryData("outpost", "Anchorage", Global.getSettings().getSpriteName("IndEvo", "pop_outpost"), "IndEvo_pop_outpost", 30000) {
                @Override
                public SubIndustry newInstance() {
                    return new OutpostSubIndustry(this);
                }
            });

            /*add(new SubIndustryData("monks", "Monastic Order", Global.getSettings().getSpriteName("IndEvo", "pop_monks"), "IndEvo_pop_monks", 75000) {
                @Override
                public SubIndustry newInstance() {
                    return new MonasticOrderSubIndustry(this);
                }
            });*/

            add(new SubIndustryData("helldivers", "Managed Democracy", Global.getSettings().getSpriteName("IndEvo", "pop_helldivers"), "IndEvo_pop_helldivers", 100000) {
                @Override
                public SubIndustry newInstance() {
                    return new HelldiversSubIndustry(this);
                }
            });

            add(new SubIndustryData("warhammer", "Forge World", Global.getSettings().getSpriteName("IndEvo", "pop_warhammer"), "IndEvo_pop_warhammer", 150000) {
                @Override
                public SubIndustry newInstance() {
                    return new WarhammerSubIndustry(this);
                }
            });
            /*add(new SubIndustryData("resort", "Resort Planet", Global.getSettings().getSpriteName("IndEvo", "pop_resort"), "IndEvo_pop_resort") {
                @Override
                public SubIndustry newInstance() {
                    return new ResortSubIndustry(this);
                }
            });*/
            add(new SubIndustryData("corpos", "Corporate Governance", Global.getSettings().getSpriteName("IndEvo", "pop_corpos"), "IndEvo_pop_corpos", 50000) {
                @Override
                public SubIndustry newInstance() {
                    return new CorporateGovernanceSubIndustry(this);
                }
            });
        }
    };

    @Override
    public List<SubIndustryData> getIndustryList() {
        return industryList;
    }

    @Override
    public SubIndustryAPI getCurrent() {
        return current;
    }

    private SubIndustryAPI current = null;
    private String nameOverride = null;

    public void apply() {
        supply.clear();
        demand.clear();

        if (!current.isInit()) current.init(this);

        nameOverride = current.getName();

        if(Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE)){
            superApply();
        } else {
            current.apply();
        }

        if (!isFunctional()) {
            supply.clear();
        }
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

            if (this.current != null) this.current.unapply();
            this.current = current;

            if (reapply) reapply();
        } else
            throw new IllegalArgumentException("Switchable Industry List of " + getClass().getName() + " does not contain " + current.getName());
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
        return nameOverride == null ? super.getCurrentName() : nameOverride;
    }

    @Override
    public void init(String id, MarketAPI market) {
        if (current == null) setCurrent(getIndustryList().get(0).newInstance(), false);
        super.init(id, market);
    }

    @Override
    public void setSpecialItem(SpecialItemData special) {
        super.setSpecialItem(special);
        if (addedHeatCondition != null && current != null && current instanceof HiddenArcologiesSubIndustry) market.suppressCondition(addedHeatCondition);
    }

    @Override
    public String getCurrentImage() {
        String imageName = current.getImageName(market);

        try {
            Global.getSettings().loadTexture(imageName);
        } catch (IOException e) {
            Global.getLogger(SwitchablePopulation.class).error("Could not load image for government: " + imageName);
        }

        return current.getImageName(market);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        current.advance(amount);

        if (!locked && current != null && !current.isBase() && !(current instanceof OutpostSubIndustry)) {
            daysPassed += Global.getSector().getClock().convertToDays(amount);
            if (daysPassed >= DAYS_TO_LOCK) {
                locked = true;

                MessageIntel intel = new MessageIntel("%s established on " + market.getName(), Misc.getBasePlayerColor(), new String[]{current.getName()});
                intel.addLine(BaseIntelPlugin.BULLET + "Now permanent",
                        Misc.getTextColor(),
                        new String[] {current.getName()},
                        Misc.getHighlightColor());

                intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
                intel.setSound(BaseIntelPlugin.getSoundMajorPosting());
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
            }
        }
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);

        if (Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE)) return;

        current.addRightAfterDescription(tooltip, mode);
    }

    public boolean canChange() {
        return Global.getSettings().isDevMode() || (market.getSize() <= Settings.getInt(Settings.GOVERNMENT_MAX_SIZE) && !locked);
    }

    public boolean isDefault() {
        return current != null && current.isBase();
    }

    @Override
    public void unapply() {
        super.unapply();

        if (!current.isInit()) current.init(this);
        current.unapply();
    }

    public float getPatherInterest() {
        if (Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE)) return super.getPatherInterest();

        float currentNum = current.getPatherInterest(this);
        return currentNum > 100000000f ? super.getPatherInterest() : currentNum;
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;

        tooltip.addSectionHeading("Governance Type", Alignment.MID, opad);

        if (Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE)){
            SubIndustryData sub = null;
            for (SubIndustryData s : industryList) {if (s.newInstance().isBase()) sub = s; break;} //meh
            if (sub != null) tooltip.addPara(Global.getSettings().getDescription(sub.descriptionID, Description.Type.CUSTOM).getText2(), opad);

        } else if (current != null) tooltip.addPara(current.getDescription().getText2(), opad);

        if(!canChange()) return;

        if (current instanceof OutpostSubIndustry) tooltip.addPara("An Anchorage can always be changed to a different government style.", opad);
        else if(canChange()) tooltip.addPara("Changing the government style is only possible until %s and becomes permanent after %s.", opad, Misc.getHighlightColor(),
                "colony size " + Settings.getInt(Settings.GOVERNMENT_MAX_SIZE),
                DAYS_TO_LOCK + " " + StringHelper.getDayOrDays(DAYS_TO_LOCK));

        if (!isDefault() && !locked && !(current instanceof OutpostSubIndustry)) {
            int daysRemaining = (int) Math.ceil(DAYS_TO_LOCK - daysPassed);
            tooltip.addPara("Days until permanent: %s", opad, Misc.getHighlightColor(), daysRemaining + " " + StringHelper.getDayOrDays(daysRemaining));
        }

        if(canChange()) tooltip.addPara("%s", opad, Misc.getPositiveHighlightColor(), "Click to change government type.");
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
