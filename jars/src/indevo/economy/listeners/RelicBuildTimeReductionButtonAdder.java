package indevo.economy.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.economy.RelicSpeedUpTarget;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.utils.helper.StarReflectionUtils;
import indevo.utils.plugins.BaseSimpleBaseIndustryOptionProvider;

public class RelicBuildTimeReductionButtonAdder extends BaseSimpleBaseIndustryOptionProvider {

    public static final String MEM_COOLDOWN = "$IndEvo_reduceBuildTimeCooldown";

    public static void register(){
        Global.getSector().getListenerManager().addListener(new RelicBuildTimeReductionButtonAdder(), true);
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        if (!(ind instanceof BaseIndustry)) return false;

        boolean isUpgrading = ind.isUpgrading();
        boolean hasUpgrade = ind.getSpec().getUpgrade() != null; //some modded industries can upgrade without an upgrade -> crash when getting the spec
        boolean isBuilding = ind.isBuilding() && !ind.isUpgrading();

        return isBuilding || (isUpgrading && hasUpgrade);
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return RelicSpeedUpDialog.isAvailable(new ConstructionTarget((BaseIndustry) opt.ind));
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        RelicSpeedUpDialog.show(ui, new ConstructionTarget((BaseIndustry) opt.ind));
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        RelicSpeedUpDialog.addTooltip(tooltip, new ConstructionTarget((BaseIndustry) opt.ind));
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Speed up construction...";
    }

    public static class ConstructionTarget implements RelicSpeedUpTarget {

        private final BaseIndustry ind;

        public ConstructionTarget(BaseIndustry ind) {
            this.ind = ind;
        }

        @Override
        public MarketAPI getMarket() {
            return ind.getMarket();
        }

        @Override
        public String getSpeedUpId() {
            return ind.getId();
        }

        @Override
        public String getSpeedUpLabel() {
            return "Speed up construction";
        }

        @Override
        public String getSpeedUpLore() {
            return null;
        }

        @Override
        public int getSpeedUpDaysLeft() {
            return Math.round(getBuildTime() - ind.getBuildProgress());
        }

        @Override
        public int getSpeedUpDaysTotal() {
            return Math.round(getBuildTime());
        }

        @Override
        public void speedUp(int days) {
            ind.setBuildProgress(ind.getBuildProgress() + days);
        }

        @Override
        public String getSpeedUpScope() {
            return "on this colony";
        }

        @Override
        public float getSpeedUpCooldownLeft() {
            MemoryAPI mem = getMarket().getMemoryWithoutUpdate();
            return mem.contains(MEM_COOLDOWN) ? mem.getExpire(MEM_COOLDOWN) : 0f;
        }

        @Override
        public void startSpeedUpCooldown() {
            getMarket().getMemoryWithoutUpdate().set(MEM_COOLDOWN, true, COOLDOWN_DAYS);
        }

        private float getBuildTime() {
            return (float) StarReflectionUtils.get(ind, "buildTime", float.class, true);
        }
    }
}
