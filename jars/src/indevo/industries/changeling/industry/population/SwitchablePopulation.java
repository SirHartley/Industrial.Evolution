package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.campaign.econ.Market;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

public class SwitchablePopulation extends PopulationAndInfrastructure implements SwitchableIndustryAPI {

    public static final List<SubIndustryAPI> industryList = new LinkedList<SubIndustryAPI>(){{

            add(new SubIndustry("base_population_and_infrastructure", "Population and Infrastructure", "graphics/icons/industry/population.png", "IndEvo_base_mining") {
                @Override
                public void apply(Industry industry) {
                    if (industry instanceof SwitchablePopulation) ((SwitchablePopulation) industry).superApply(); //applies default pop&Infra
                }

                @Override
                public String getImage(MarketAPI market) {
                    float size = market.getSize();
                    if (size <= SIZE_FOR_SMALL_IMAGE) {
                        return Global.getSettings().getSpriteName("industry", "pop_low");
                    }
                    if (size >= SIZE_FOR_LARGE_IMAGE) {
                        return Global.getSettings().getSpriteName("industry", "pop_high");
                    }

                    return imageName;
                }
            });

            add(new UnderworldSubIndustry("underworld", "Underworld Governance", Global.getSettings().getSpriteName("IndEvo", "pop_underworld"), "IndEvo_ore_mining"));
        }
    };


    @Override
    public List<SubIndustryAPI> getIndustryList() {
        return industryList;
    }

    private SubIndustryAPI current = null;

    public void apply() {
        supply.clear();
        demand.clear();

        super.apply(true); //since popInfra does not override the baseIndustry overloaded apply we can call it here

        current.apply(this);

        if (!isFunctional()) {
            supply.clear();
        }
    }

    public void superApply(){
        supply.clear();
        demand.clear();

        super.apply();
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
        current = getIndustryList().get(0);
        super.init(id, market);
    }

    @Override
    public String getCurrentImage() {
        return current.getImage(market);
    }

    public boolean canImprove() {
        return true;}

    public float getImproveBonusXP() {
        return 0;
    }

    public String getImproveMenuText() {
        return "Change Output";
    }

    public int getImproveStoryPoints() {
        return 0;
    }

    @Override
    public void setImproved(boolean improved) {
        current = getNext();
        reapply();
    }

    public String getImproveDialogTitle() {
        return "Changing Type for " + getSpec().getName();
    }

    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        if (mode != ImprovementDescriptionMode.INDUSTRY_TOOLTIP) {
            info.addPara("Changes the %s to %s.", 0f, highlight, "output", getNext().getDescription().getText2());
            info.addPara("Does not affect improvement cost of other buildings on this colony.", 3f);
        }

        info.addSpacer(opad);
    }

    public SubIndustryAPI getNext(){
        List<SubIndustryAPI> industryAPIList = getIndustryList();

        for (int i = 0; i < industryAPIList.size(); i++){
            if (industryAPIList.get(i).getId().equals(current.getId())){
                if (i == industryAPIList.size()-1) return industryAPIList.get(0);
                else return industryAPIList.get(i+1);
            }
        }

        return null;
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    public float getPatherInterest() {
        return 2f + super.getPatherInterest();
    }

    @Override
    protected boolean canImproveToIncreaseProduction() {
        return true;
    }

    public void addBaseSwitchableDesc(TooltipMakerAPI tooltip){
        tooltip.addPara("%s", 10f, Misc.getGrayColor(), "Click this industry to switch the output.");
    }
}
