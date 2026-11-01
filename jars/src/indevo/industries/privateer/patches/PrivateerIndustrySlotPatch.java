package indevo.industries.privateer.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue.ConstructionQueueItem;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;

@Patch(target = @ClassMatch(type = Misc.class))
public class PrivateerIndustrySlotPatch {

    // vanilla counts the base once, built, building or queued; it takes two slots
    @After(target = @MethodMatch(methodName = "getNumIndustries", parameterCount = 1))
    public static void afterGetNumIndustries(AfterContext context) {
        MarketAPI market = (MarketAPI) context.getArg(0);

        int extra = 0;
        for (Industry industry : market.getIndustries()) {
            if (Ids.PIRATEHAVEN.equals(industry.getId())) extra++;
        }

        for (ConstructionQueueItem item : market.getConstructionQueue().getItems()) {
            if (Ids.PIRATEHAVEN.equals(item.id)) extra++;
        }

        if (extra > 0) context.setReturnValue((Integer) context.getReturnValue() + extra);
    }
}
