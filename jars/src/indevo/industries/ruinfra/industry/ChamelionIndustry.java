package indevo.industries.ruinfra.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import indevo.ids.Ids;
import indevo.utils.helper.Settings;

import static indevo.industries.ruinfra.conditions.DerelictInfrastructureCondition.RUINED_INFRA_UPGRADE_ID_KEY;

public class ChamelionIndustry extends BaseIndustry {

    public static final float BUILD_PARAM_RED = Settings.DERINFRA_BUILD_RED;
    private boolean isDone = false;

    // 20.04.2021 check if this properly blocks the actual ind from being built, if not, make check that prohibits upgrading if it is added or stops the current upgrade if it is ongoing.
    // 03/12/2022 it does

    @Override
    public void init(String id, MarketAPI market) {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        if (memory.contains(RUINED_INFRA_UPGRADE_ID_KEY)) id = memory.getString(RUINED_INFRA_UPGRADE_ID_KEY);

        super.init(id, market);
    }

    @Override
    protected Object readResolve() {
        Object o = super.readResolve();

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        if (!memory.contains(RUINED_INFRA_UPGRADE_ID_KEY)) {
            spec = Global.getSettings().getIndustrySpec(memory.getString(RUINED_INFRA_UPGRADE_ID_KEY));
        }

        return o;
    }

    @Override
    public void apply() {
        super.apply(false);
    }

    @Override
    public boolean isAvailableToBuild() {
        if (id.equals(Ids.CHAMELION)) return false;
        return spec.getNewPluginInstance(market).isAvailableToBuild();
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
    public IndustrySpecAPI getSpec() {
        return Global.getSettings().getIndustrySpec(id);
    }

    @Override
    public float getBuildCost() {
        return super.getBuildCost() * BUILD_PARAM_RED;
    }

    @Override
    public float getBuildTime() {
        return super.getBuildTime() * BUILD_PARAM_RED;
    }

    @Override
    public boolean isHidden() {
        //if (isDone) return true;

        return super.isHidden();
    }

    @Override
    public void finishBuildingOrUpgrading() {
        super.finishBuildingOrUpgrading();
        id = Ids.CHAMELION;
        isDone = true;
    }
}
