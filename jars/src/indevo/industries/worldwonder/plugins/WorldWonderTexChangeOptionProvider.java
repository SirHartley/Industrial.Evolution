package indevo.industries.worldwonder.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.worldwonder.industry.CloudPainter;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WorldWonderTexChangeOptionProvider extends BaseIndustryOptionProvider {
    public static final Object OPTION_TEXTURE_CHANGE = new Object();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(WorldWonderTexChangeOptionProvider.class)) {
            listeners.addListener(new WorldWonderTexChangeOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        boolean isWW = ind instanceof CloudPainter;
        boolean playerOwned = ind.getMarket().isPlayerOwned();

        return super.isUnsuitable(ind, allowUnderConstruction) || !isWW || !playerOwned;
    }

    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData("Change cloud texture", OPTION_TEXTURE_CHANGE, ind, this);
        opt.color = new Color(150, 100, 255, 255);
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == OPTION_TEXTURE_CHANGE) {
            tooltip.addPara("Changes the cloud texture.", 0f);
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == OPTION_TEXTURE_CHANGE) {
            CloudPainter ww = (CloudPainter) opt.ind;
            ww.nextTexture();
        }
    }
}
