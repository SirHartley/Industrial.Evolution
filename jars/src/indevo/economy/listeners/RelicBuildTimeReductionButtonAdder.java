package indevo.economy.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.StarReflectionUtils;
import indevo.utils.helper.StringHelper;
import indevo.utils.plugins.BaseSimpleBaseIndustryOptionProvider;
import org.codehaus.janino.Mod;

import java.awt.*;

import static indevo.industries.ruinfra.conditions.DerelictInfrastructureCondition.RUINED_INFRA_UPGRADE_ID_KEY;

public class RelicBuildTimeReductionButtonAdder extends BaseSimpleBaseIndustryOptionProvider {

    public static final float RELICS_PER_DAY = 1f; //this means: two per day reduction because it scales off the total days left but reduction is half the total days
    public static final float COOLDOWN_DAYS = 60f;
    public static final String MEM_COOLDOWN = "$IndEvo_reduceBuildTimeCooldown";

    public static void register(){
        Global.getSector().getListenerManager().addListener(new RelicBuildTimeReductionButtonAdder(), true);
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {

        ModPlugin.log("checking " + ind.getId());
        boolean isUpgrading = ind.isUpgrading();
        boolean hasUpgrade = ind.getSpec().getUpgrade() != null; //some modded industries can upgrade without an upgrade -> crash when getting the spec

        boolean isBuilding = ind.isBuilding() && !ind.isUpgrading();

        ModPlugin.log(isBuilding + " " + isUpgrading + " " + hasUpgrade);

        return isBuilding || (isUpgrading && hasUpgrade);
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return !opt.ind.getMarket().getMemoryWithoutUpdate().contains(MEM_COOLDOWN);
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        Industry ind = opt.ind;

        float buildTime = (float) StarReflectionUtils.get(ind, "buildTime", float.class, true);
        float buildProgress = ind.getBuildOrUpgradeProgress(); //this is from 0 to 1
        float buildDaysLeft = (float) Math.round(buildTime * (1 - buildProgress));

        CargoAPI marketCargo = Misc.getStorageCargo(ind.getMarket());
        CargoAPI playerCargo = MiscIE.getPlayerCargo();

        float relicsInMarketCargo = marketCargo != null ? marketCargo.getCommodityQuantity(ItemIds.RARE_PARTS) : 0f;
        float relicsInPlayerCargo = playerCargo != null ? playerCargo.getCommodityQuantity(ItemIds.RARE_PARTS) : 0f;

        int relicsRequired = Math.round(buildDaysLeft * RELICS_PER_DAY);
        boolean hasEnough = relicsRequired <= relicsInPlayerCargo + relicsInMarketCargo;

        CustomDialogDelegate delegate = new BaseCustomDialogDelegate() {
            @Override
            public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
                float opad = 10f;
                float spad = 5f;
                Color hl = Misc.getHighlightColor();
                Color pl = Misc.getPositiveHighlightColor();
                Color nl = Misc.getNegativeHighlightColor();

                String relicName = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();

                TooltipMakerAPI info = panel.createUIElement(500f, 150f, false);

                info.setParaInsigniaLarge();

                info.addPara("Halves the remaining construction time.", opad);
                info.addPara("Can only be done %s every %s on this colony.", spad, Misc.getHighlightColor(), "once", Math.round(COOLDOWN_DAYS) + " days");

                info.addPara("Days left: %s", opad, hl, Math.round(buildDaysLeft) + " " + StringHelper.getDayOrDays(Math.round(buildDaysLeft)));
                info.addPara("New construction time: %s", spad, pl, Math.round(buildDaysLeft / 2) + " " + StringHelper.getDayOrDays(Math.round(buildDaysLeft) / 2));
                info.addPara("Cost: %s (Available: " + ((int) Math.round(relicsInMarketCargo + relicsInPlayerCargo)) + ")", spad, hasEnough ? pl : nl, relicsRequired + " " + relicName);

                panel.addUIElement(info).inTL(0,0);
            }

            @Override
            public boolean hasCancelButton() {
                return hasEnough;
            }

            @Override
            public void customDialogConfirm() {
                if (hasEnough) {
                    float remaining = relicsRequired;

                    // use local first
                    if (marketCargo != null && relicsInMarketCargo > 0) {
                        float take = Math.min(remaining, relicsInMarketCargo);
                        marketCargo.removeCommodity(ItemIds.RARE_PARTS, take);
                        remaining -= take;
                    }

                    //no need to safety-proof because if we're here there must be relics in cargo
                    if (playerCargo != null && remaining > 0) {
                        playerCargo.removeCommodity(ItemIds.RARE_PARTS, remaining);
                    }

                    float newProgress = (buildTime - buildDaysLeft) + buildDaysLeft / 2;

                    ((BaseIndustry) ind).setBuildProgress(newProgress);
                    ind.getMarket().getMemoryWithoutUpdate().set(MEM_COOLDOWN,true, COOLDOWN_DAYS);
                }
            }

            @Override
            public String getCancelText() {
                return "Cancel";
            }

            @Override
            public String getConfirmText() {
                String text = "Confirm";
                if (!hasEnough) text = "Return";

                return text;
            }
        };
        ui.showDialog(500f, 150f, delegate);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        tooltip.addPara("Speed up construction of this building using %s.", 0f, Misc.getHighlightColor(), Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName());
        if (!optionEnabled(opt)) {
            float timeLeft = opt.ind.getMarket().getMemoryWithoutUpdate().getExpire(MEM_COOLDOWN);
            String days = (int) Math.ceil(timeLeft) + " " + StringHelper.getDayOrDays((int) Math.ceil(timeLeft));
            tooltip.addPara("Unavailable for another %s", 0f, Misc.getNegativeHighlightColor(), days);
        }
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Speed up construction...";
    }
}
