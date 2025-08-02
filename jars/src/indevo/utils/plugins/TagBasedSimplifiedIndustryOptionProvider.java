package indevo.utils.plugins;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.utils.helper.MiscIE;

public abstract class TagBasedSimplifiedIndustryOptionProvider extends BaseSimpleBaseIndustryOptionProvider {

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction){
        if (ind == null
                || ind.getMarket() == null
                || (!allowUnderConstruction && (ind.isBuilding() || ind.isUpgrading()))) return false;

        boolean isTarget = ind.getSpec().getTags().contains(getTargetTag());

        MarketAPI currentMarket = MiscIE.getCurrentInteractionTargetMarket();
        boolean isLocal = currentMarket != null && ind.getMarket().getId().equals(currentMarket.getId());
        return isTarget && isLocal;
    }


    public abstract String getTargetTag();

}
