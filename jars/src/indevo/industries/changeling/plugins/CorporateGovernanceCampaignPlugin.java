package indevo.industries.changeling.plugins;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.econ.ImmigrationPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;
import indevo.industries.changeling.industry.population.CorporateGovernanceSubIndustry;

public class CorporateGovernanceCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public PluginPick<ImmigrationPlugin> pickImmigrationPlugin(MarketAPI market) {
        for (Industry industry : market.getIndustries()){
            if (industry instanceof SwitchableIndustryAPI){
                if (((SwitchableIndustryAPI) industry).getCurrent() instanceof CorporateGovernanceSubIndustry){
                    return new PluginPick<ImmigrationPlugin>(new CorpoImmigrationPlugin(market), PickPriority.MOD_SPECIFIC);
                }
            }
        }

        return super.pickImmigrationPlugin(market);
    }
}
