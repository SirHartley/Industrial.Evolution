package com.fs.starfarer.api.impl.campaign.econ.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class IndEvo_WorldWonder extends BaseIndustry implements MarketImmigrationModifier, EconomyTickListener {

    public static final float IMMIGRATION_BONUS = 10f;
    public static final float STABILITY_BONUS = 1f;

    public static final String HAS_AWARDED_SP = "$IndEvo_hasAwardedSP";
    public static final String ALTERNATE_CHURCH_VISUAL = "$IndEvo_alternateWonderVisual";

    @Override
    public String getCurrentImage() {
        if (market.getMemoryWithoutUpdate().getBoolean(ALTERNATE_CHURCH_VISUAL)) {
            if (getId().equals(IndEvo_ids.CHURCH)) return Global.getSettings().getSpriteName("IndEvo", "kadur_megachurch");
        }

        return super.getCurrentImage();
    }

    @Override
    public void apply() {
        super.apply(true);

        if (isFunctional()) {
            Global.getSector().getListenerManager().addListener(this, true);

            for (MarketAPI market : Misc.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
               if(!market.hasCondition(IndEvo_ids.COND_WORLD_WONDER)) market.addCondition(IndEvo_ids.COND_WORLD_WONDER);
            }
        }
    }

    @Override
    public void finishBuildingOrUpgrading() {
        super.finishBuildingOrUpgrading();

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        if (!memory.getBoolean(HAS_AWARDED_SP) && market.isPlayerOwned()) {
            Global.getSector().getCampaignUI().addMessage("Constructing the " + getCurrentName() + " on " + market.getName() + " has awarded you an additional story point.",
                    Misc.getPositiveHighlightColor());
            Global.getSector().getPlayerStats().addStoryPoints(1);
            memory.set(HAS_AWARDED_SP, true);
        }
    }

    @Override
    protected void upgradeFinished(Industry previous) {
        super.upgradeFinished(previous);
    }

    @Override
    public boolean canInstallAICores() {
        return false;
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (isFunctional()) {
            incoming.add(Factions.INDEPENDENT, IMMIGRATION_BONUS);
            incoming.getWeight().modifyFlat(getModId(), IMMIGRATION_BONUS, getNameForModifier());
        }
    }

    @Override
    public void reportEconomyTick(int iterIndex) {

    }

    @Override
    public void reportEconomyMonthEnd() {
        RecentUnrest unrest = RecentUnrest.get(market);

        if (unrest.getPenalty() > 0) {
            unrest.add(-1, getNameForModifier());
        }
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        MemoryAPI memory = market.getMemoryWithoutUpdate();

        if (IndustryTooltipMode.ADD_INDUSTRY.equals(mode) && !memory.getBoolean(HAS_AWARDED_SP))
            tooltip.addPara("Constructing a World Symbol will award you a Story Point.", Misc.getPositiveHighlightColor(), 10f);
        else if (IndustryTooltipMode.ADD_INDUSTRY.equals(mode) && memory.getBoolean(HAS_AWARDED_SP))
            tooltip.addPara("A story point has already been awarded for building a Symbol on this world.", Misc.getHighlightColor(), 10f);

        //if(IndustryTooltipMode.ADD_INDUSTRY.equals(mode) && isAvailableToBuild()) tooltip.addPara("Only one can exist at a time", 10f, Misc.getHighlightColor());
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            Color h = Misc.getHighlightColor();
            float opad = 10f;

            tooltip.addPara("Population growth bonus: %s", opad, h, "+" + Math.round(IMMIGRATION_BONUS));
        }
    }
}
