package indevo.industries.derelicts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.derelicts.dialogue.AncientLabRelicPanelDelegate;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

import java.awt.*;

public class AncientLabRelicOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(AncientLabRelicOptionProvider.class)) {
            listeners.addListener(new AncientLabRelicOptionProvider(), true);
        }
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.LAB;
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Relic services...";
    }

    // no docked check, vanilla also lists industry options on remote colony screens
    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return ind != null
                && ind.getMarket() != null
                && Ids.LAB.equals(ind.getId())
                && ind.isFunctional()
                && ind.getMarket().isPlayerOwned();
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        AncientLabRelicPanelDelegate.show(ui, opt.ind.getMarket());
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        Color hl = Misc.getHighlightColor();
        tooltip.addSectionHeading("Relic services", Alignment.MID, 0f);
        tooltip.addPara("Refine Forge Templates or integrate automated ships, paid with %s.", 10f, hl, AncientLabRelicPanelDelegate.getRelicName());
        tooltip.addPara("Available: %s, from colony storage first, then fleet cargo.", 10f, hl, AncientLabRelicPanelDelegate.getAvailableRelics(opt.ind.getMarket()) + "");
    }
}
