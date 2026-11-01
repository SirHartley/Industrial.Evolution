package indevo.industries.worldwonder.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

import static indevo.industries.worldwonder.patches.CloudPainterShieldPatch.*;

public class ShieldVisualsOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(ShieldVisualsOptionProvider.class)) {
            listeners.addListener(new ShieldVisualsOptionProvider(), true);
        }
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return super.isSuitable(ind, allowUnderConstruction) && hasFunctionalCloudPainter(ind.getMarket());
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        MarketAPI market = opt.ind.getMarket();

        if (market.hasTag(TAG_FULL)) {
            market.removeTag(TAG_FULL);
            market.addTag(TAG_LOW);
        } else if (market.hasTag(TAG_LOW)) {
            market.removeTag(TAG_LOW);
            market.addTag(TAG_NONE);
        } else {
            market.removeTag(TAG_NONE);
            market.addTag(TAG_FULL);
        }
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        tooltip.addPara("Toggles between the visual modes of the planetary shield, from full visibility to invisible mode.", 0f);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Change Shield Visuals";
    }

    @Override
    public String getTargetIndustryId() {
        return Industries.PLANETARYSHIELD;
    }
}
