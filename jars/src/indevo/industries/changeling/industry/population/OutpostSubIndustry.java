package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager.AI_CORE_ADMIN_INTEREST;

public class OutpostSubIndustry extends SubIndustry {

    public OutpostSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
    }

    //negate
    @Override
    public float getPatherInterest(Industry industry) {
        return -getLuddicPathMarketInterest(industry.getMarket());
    }

    public static float getLuddicPathMarketInterest(MarketAPI market) {
        if (market.getFactionId().equals(Factions.LUDDIC_PATH)) return 0f;
        float total = 0f;

        String aiCoreId = market.getAdmin().getAICoreId();
        if (aiCoreId != null) {
            total += AI_CORE_ADMIN_INTEREST;
        }

        for (Industry ind : market.getIndustries()) {
            if (ind instanceof SwitchablePopulation) continue;
            total += ind.getPatherInterest();
        }

        if (total > 0) {
            total += new Random(market.getName().hashCode()).nextFloat() * 0.1f;
        }

        if (market.getFactionId().equals(Factions.LUDDIC_CHURCH)) {
            total *= 0.1f;
        }

        return total;
    }
}
