package indevo.industries;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.ids.Ids;
import indevo.submarkets.IndustrySubmarkets;

public abstract class SharedSubmarketUser extends BaseIndustry implements SharedSubmarketUserAPI {

    @Override
    public void advance(float amount) {
        super.advance(amount);
        IndustrySubmarkets.ensure(this, Ids.SHAREDSTORAGE);
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        IndustrySubmarkets.release(this);
    }
}
