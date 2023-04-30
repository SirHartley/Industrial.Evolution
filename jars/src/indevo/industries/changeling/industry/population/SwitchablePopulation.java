package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;
import indevo.utils.helper.StringHelper;
import lunalib.backend.ui.components.util.TooltipHelper;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

public class SwitchablePopulation extends PopulationAndInfrastructure implements SwitchableIndustryAPI {

    public static final String BASE_STATE_ID = "base_population_and_infrastructure";
    public static final int MAX_SIZE_FOR_CHANGE = 3;
    public static final int DAYS_TO_LOCK = 7;

    public float daysPassed = 0;
    public boolean locked = false;

    public static final List<SubIndustryAPI> industryList = new LinkedList<SubIndustryAPI>(){{

            add(new SubIndustry(BASE_STATE_ID, "Population and Infrastructure", "graphics/icons/industry/population.png", "IndEvo_base_mining") {
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

            add(new UnderworldSubIndustry("underworld", "Underworld Governance", Global.getSettings().getSpriteName("IndEvo", "pop_underworld"), "IndEvo_pop_uw"));
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
        return canChange() || super.canImprove();}

    public float getImproveBonusXP() {
        return canChange() ? 0 : super.getImproveBonusXP();
    }

    public String getImproveMenuText() {
        return canChange() ? "Change Governing Style" : super.getImproveMenuText();
    }

    public int getImproveStoryPoints() {
        return canChange() ? 0 : super.getImproveStoryPoints();
    }

    @Override
    public void setImproved(boolean improved) {
        if (canChange()){
            current = getNext();
            daysPassed = 0;
            reapply();
        }

        else super.setImproved(improved);
    }

    public String getImproveDialogTitle() {
        return canChange() ? "Changing Governing Style for " + getSpec().getName() : super.getImproveDialogTitle();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!locked && current != null && !current.getId().equals(BASE_STATE_ID)){
            daysPassed += Global.getSector().getClock().convertToDays(amount);
            if (daysPassed >= DAYS_TO_LOCK) {
                locked = true;

                Global.getSector().getCampaignUI().addMessage("The %s Government on %s has become permanent.", Misc.getTextColor(), current.getName(), market.getName(), Misc.getHighlightColor(), market.getFaction().getColor());
            }
        }
    }

    public boolean canChange(){
        return market.getSize() <= MAX_SIZE_FOR_CHANGE && !locked;
    }

    public boolean isNotChanged(){
        return current != null && current.getId().equals(BASE_STATE_ID);
    }

    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        if (canChange()){
            float opad = 10f;
            Color highlight = Misc.getHighlightColor();

            if (mode != ImprovementDescriptionMode.INDUSTRY_TOOLTIP) {
                String govName = getNext().getName();
                String govDesc = getNext().getDescription().getText2();

                info.addPara("Changes the local governing style to %s.", 0f, highlight, govName);
                info.addPara(govDesc, 3f);

                info.addPara("This change is only possible until %s and becomes permanent after %s.", 10f, highlight,
                        "size " + MAX_SIZE_FOR_CHANGE,
                        DAYS_TO_LOCK + " " + StringHelper.getDayOrDays(DAYS_TO_LOCK));

                info.addPara("Does not affect improvement cost of other buildings on this colony.", 10f);
            }

            info.addSpacer(opad);

        } else super.addImproveDesc(info, mode);
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
        if (current != null&& current.getId().equals(BASE_STATE_ID)){
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
