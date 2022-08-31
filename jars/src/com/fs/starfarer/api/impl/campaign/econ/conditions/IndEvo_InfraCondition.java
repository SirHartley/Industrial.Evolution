package com.fs.starfarer.api.impl.campaign.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;

import java.util.Random;

public class IndEvo_InfraCondition extends BaseHazardCondition {
    public static final Logger log = Global.getLogger(IndEvo_InfraCondition.class);
    public static final String RUINED_INFRA_UPGRADE_ID_KEY = "$IndEvo_RuinedInfraIndId";

    public void apply(String id) {
        super.apply(id);

        if (!market.getMemoryWithoutUpdate().getBoolean("$isPlanetConditionMarketOnly")
                && !isRuinfraConditionSet()
                && market.getFaction() != null
                && !market.getFactionId().equals("neutral")
                && !market.isPlanetConditionMarketOnly()) {

            setUpgradeSpec(market);
            addRuinfraIfNeeded();
        }
    }

    private static boolean isAvailableToBuild(String id, MarketAPI market) {
        return Global.getSettings().getIndustrySpec(id) != null && Global.getSettings().getIndustrySpec(id).getNewPluginInstance(market).isAvailableToBuild();
    }

    public void unapply(String id) {
        super.unapply(id);
    }

    @Override
    public void advance(float amount) {
        removeConditionIfRuinsNotPresent();
    }

    public static void setUpgradeSpec(MarketAPI market) {
        MemoryAPI memory = market.getMemoryWithoutUpdate();

        //if this ind has not set it's target already
        if (memory.contains(RUINED_INFRA_UPGRADE_ID_KEY)) return;

        WeightedRandomPicker<String> industryIdPicker = new WeightedRandomPicker<>();
        industryIdPicker.addAll(IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.RUIND_LIST));

        Random random = new Random(Misc.getSalvageSeed(market.getPlanetEntity()));
        String pickedId = industryIdPicker.pick(random);
        log.info("picked " + pickedId);

        while (pickedId != null && !isAvailableToBuild(pickedId, market)) {
            pickedId = industryIdPicker.pickAndRemove();

            log.info("repicked " + pickedId);
        }

        if (pickedId == null) pickedId = IndEvo_ids.ADASSEM;

        market.getMemoryWithoutUpdate().set(RUINED_INFRA_UPGRADE_ID_KEY, pickedId);
    }

    private void addRuinfraIfNeeded() {
        if (market == null) return;

        log.info("Adding ruinfra to " + market.getName());
        market.addIndustry(IndEvo_ids.RUINFRA);
        setRuinfraCondition();
        Global.getSector().getEconomy().tripleStep();
    }

    private boolean isRuinfraConditionSet() {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String ruinsConditionSet = "$IndEvo_infraPlaced_" + market.getId();

        return memory.getBoolean(ruinsConditionSet);
    }

    private void setRuinfraCondition() {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String ruinsConditionSet = "$IndEvo_infraPlaced_" + market.getId();

        memory.set(ruinsConditionSet, true);
    }

    private void removeConditionIfRuinsNotPresent() {
        if (!market.getMemoryWithoutUpdate().getBoolean("$isPlanetConditionMarketOnly")
                && isRuinfraConditionSet()
                && !market.hasIndustry(IndEvo_ids.RUINFRA)
                && !market.getFactionId().equals("neutral")
                && !market.isPlanetConditionMarketOnly()) {

            log.info("Removing InfraCondition on " + market.getName());
            market.removeSpecificCondition(condition.getIdForPluginModifications());
        }
    }

    @Override
    public boolean showIcon() {
        return false;
    }
}