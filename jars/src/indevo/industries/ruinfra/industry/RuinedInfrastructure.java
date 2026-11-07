package indevo.industries.ruinfra.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.ruinfra.conditions.DerelictInfrastructureCondition;

import static indevo.industries.ruinfra.conditions.DerelictInfrastructureCondition.RUINED_INFRA_UPGRADE_ID_KEY;

public class RuinedInfrastructure extends BaseIndustry {

    @Override
    public void apply() {
        if (!hasValidTarget()) {
            market.getMemoryWithoutUpdate().unset(RUINED_INFRA_UPGRADE_ID_KEY);
            DerelictInfrastructureCondition.setUpgradeSpec(market);
        }

        triggerUpgradeForAI();
    }

    private boolean hasValidTarget() {
        String target = market.getMemoryWithoutUpdate().getString(RUINED_INFRA_UPGRADE_ID_KEY);
        return target != null && Global.getSettings().getIndustrySpec(target) != null;
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
    public boolean canInstallAICores() {
        return false;
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);

        if (market.hasCondition(Ids.COND_INFRA)) {
            market.removeSpecificCondition(market.getCondition(Ids.COND_INFRA).getIdForPluginModifications());
        }
    }

    @Override
    public void startUpgrading() {
        building = true;
        buildProgress = 0;
        upgradeId = getSpec().getUpgrade();

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String target = memory.getString(RUINED_INFRA_UPGRADE_ID_KEY);

        IndustrySpecAPI upgrade = Global.getSettings().getIndustrySpec(target);
        buildTime = upgrade.getBuildTime() * Setting.DERINFRA_BUILD_RED.value();
    }

    public void finishBuildingOrUpgrading() {
        building = false;
        buildProgress = 0;
        buildTime = 1f;

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String upgradeId = memory.getString(RUINED_INFRA_UPGRADE_ID_KEY);

        if (upgradeId != null) {
            market.removeIndustry(Ids.CHAMELION, null, false);
            market.removeIndustry(getId(), null, true);
            market.addIndustry(upgradeId);
            BaseIndustry industry = (BaseIndustry) market.getIndustry(upgradeId);
            industry.finishBuildingOrUpgrading();
/*
            if (market.isPlayerOwned()) {
                MessageIntel intel = new MessageIntel(industry.getCurrentName() + " at " + market.getName(), MiscIE.getBasePlayerColor());
                intel.addLine(BaseIntelPlugin.BULLET + "Construction completed");
                intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
                intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
            }*/
        } else {
            sendBuildOrUpgradeMessage();
            reapply();
        }
    }

    private void triggerUpgradeForAI() {
        if (isFunctional()
                && !isBuilding()
                && market.getFaction() != Global.getSector().getPlayerFaction()
                && !market.isPlayerOwned()
                && market.getMemoryWithoutUpdate().contains(RUINED_INFRA_UPGRADE_ID_KEY)
                && market.getSize() > 4
                && Misc.getFactionMarkets(market.getFaction()).size() > 2) {

            Industry target = market.instantiateIndustry(Ids.CHAMELION);
            boolean slotFree = !target.isIndustry() || Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);

            if (slotFree && target.isAvailableToBuild()) startUpgrading();
        }
    }
}
