package indevo.economy.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.economy.RelicSpeedUpTarget;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.utils.plugins.BaseSimpleBaseIndustryOptionProvider;

public class RelicSpeedUpOptionProvider extends BaseSimpleBaseIndustryOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(RelicSpeedUpOptionProvider.class)) {
            listeners.addListener(new RelicSpeedUpOptionProvider(), true);
        }
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return ind instanceof RelicSpeedUpTarget
                && ind.getMarket() != null
                && ind.getMarket().isPlayerOwned()
                && ((RelicSpeedUpTarget) ind).canSpeedUp();
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return RelicSpeedUpDialog.isAvailable((RelicSpeedUpTarget) opt.ind);
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        RelicSpeedUpDialog.show(ui, (RelicSpeedUpTarget) opt.ind);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        RelicSpeedUpDialog.addTooltip(tooltip, (RelicSpeedUpTarget) opt.ind);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return ((RelicSpeedUpTarget) ind).getSpeedUpLabel() + "...";
    }
}
