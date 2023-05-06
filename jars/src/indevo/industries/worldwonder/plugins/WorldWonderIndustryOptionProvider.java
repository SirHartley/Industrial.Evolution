package indevo.industries.worldwonder.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.worldwonder.industry.WorldWonder;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WorldWonderIndustryOptionProvider extends BaseIndustryOptionProvider {
    public static final Object OPTION_IMAGE_CHANGE = new Object();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(WorldWonderIndustryOptionProvider.class)) {
            listeners.addListener(new WorldWonderIndustryOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        boolean isWW = ind instanceof WorldWonder;
        boolean playerOwned = ind.getMarket().isPlayerOwned();
        boolean hasAlternate = isWW && ((WorldWonder) ind).hasAlternateImage();

        return super.isUnsuitable(ind, allowUnderConstruction) && playerOwned && hasAlternate;
    }

    public List<IndustryOptionProvider.IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionProvider.IndustryOptionData> result = new ArrayList<IndustryOptionProvider.IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionProvider.IndustryOptionData("Change Visuals", OPTION_IMAGE_CHANGE, ind, this);
        opt.color = Color.ORANGE;
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionProvider.IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == OPTION_IMAGE_CHANGE) {
            tooltip.addPara("Changes the industry image to an alternate version.", 0f);
        }
    }

    @Override
    public void optionSelected(IndustryOptionProvider.IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == OPTION_IMAGE_CHANGE) {
            WorldWonder ww = (WorldWonder) opt.ind;
            ww.isAlternateVisual = !ww.isAlternateVisual;
        }
    }
}
