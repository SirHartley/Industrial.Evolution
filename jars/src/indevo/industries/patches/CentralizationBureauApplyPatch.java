package indevo.industries.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.ids.Ids;
import indevo.industries.CentralizationBureau;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;

// stands in for the industry apply listener 0.98a lacks
@Patch(target = @ClassMatch(subtype = Industry.class))
public class CentralizationBureauApplyPatch {

    @After(target = @MethodMatch(methodName = "apply", parameterCount = 0))
    public static void afterApply(AfterContext context) {
        if (!context.isMostDerivedCall()) return;

        Industry ind = (Industry) context.getSelf();
        if (ind instanceof CentralizationBureau) return;

        MarketAPI market = ind.getMarket();
        if (market == null) return;

        Industry bureau = market.getIndustry(Ids.ADINFRA);
        if (bureau instanceof CentralizationBureau) ((CentralizationBureau) bureau).applyBonus(ind);
    }
}
