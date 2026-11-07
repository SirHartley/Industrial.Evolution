package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.derelicts.utils.IndustrySpecView;
import indevo.industries.derelicts.utils.RuinsManager;

public class Ruins extends BaseIndustry {

    public static final String INDUSTRY_ID_MEMORY_KEY = "$IndEvo_ruinsIndustryId";

    private transient IndustrySpecView specView = null;

    @Override
    public void apply() {
        if (getUpgradeTarget() == null && market.getPlanetEntity() != null) RuinsManager.setUpgradeSpec(market.getPlanetEntity());
        triggerUpgradeForAI();
    }

    @Override
    public IndustrySpecAPI getSpec() {
        IndustrySpecAPI base = super.getSpec();
        specView = IndustrySpecView.of(specView, base, getUpgradeTarget(), base.getDowngrade());
        return specView;
    }

    private String getUpgradeTarget() {
        return market == null ? null : market.getMemoryWithoutUpdate().getString(INDUSTRY_ID_MEMORY_KEY);
    }

    @Override
    public boolean showShutDown() {
        return false;
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

        if (market.hasCondition(Ids.COND_RUINS)) {
            market.removeSpecificCondition(market.getCondition(Ids.COND_RUINS).getIdForPluginModifications());
        }
    }

    private void triggerUpgradeForAI() {

        if (isFunctional()
                && !isBuilding()
                && market.getFaction() != Global.getSector().getPlayerFaction()
                && !market.isPlayerOwned()
                && getUpgradeTarget() != null) {

            boolean sizeReq = market.getSize() > 4;
            boolean indSlots = Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);
            boolean marketAmount = Misc.getFactionMarkets(market.getFaction()).size() > 2;

            if (sizeReq && indSlots && marketAmount) {
                startUpgrading();
            }
        }
    }

    @Override
    public boolean isIndustry() {
        return false;
    }

    protected void sendBuildOrUpgradeMessage() {
        if (market.isPlayerOwned()) {
            MessageIntel intel = new MessageIntel(getCurrentName() + " at " + market.getName(), Misc.getBasePlayerColor());
            intel.addLine(BaseIntelPlugin.BULLET + "Restoration completed");
            intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
            intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
            Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
        }
    }
}
