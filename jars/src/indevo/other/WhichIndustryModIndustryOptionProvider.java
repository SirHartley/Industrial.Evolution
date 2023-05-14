package indevo.other;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * To make this work, register it in modplugin on gameload via register()
 * then write a method that applies the PREFIX + the mod name as a tag to the industry spec
 * then watch it do se magix
 */
@Deprecated
public class WhichIndustryModIndustryOptionProvider extends BaseIndustryOptionProvider {
    public static final String PREFIX = "Whichmod_";

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(WhichIndustryModIndustryOptionProvider.class)) {
            listeners.addListener(new WhichIndustryModIndustryOptionProvider(), true);
        }
    }

    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        if (ind == null) return true;
        if (ind.getMarket() == null) return true;
        return false;

    }

    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
        for (String s : ind.getSpec().getTags()) {
            if (s.startsWith(PREFIX)) {
                tooltip.addPara("[" + s.substring(PREFIX.length()) + "]", Misc.getGrayColor(), 10f);
                break;
            }
        }
    }
}
