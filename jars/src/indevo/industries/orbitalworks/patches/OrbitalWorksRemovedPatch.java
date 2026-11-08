package indevo.industries.orbitalworks.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import indevo.industries.orbitalworks.scripts.OrbitalWorksSlotManager;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;

// stands in for the industry removed listener 0.98a lacks, vanilla orbital works inherits notifyBeingRemoved
@Patch(target = @ClassMatch(type = BaseIndustry.class))
public class OrbitalWorksRemovedPatch {

    @After(target = @MethodMatch(methodName = "notifyBeingRemoved", parameterCount = 2))
    public static void afterNotifyBeingRemoved(AfterContext context) {
        Industry ind = (Industry) context.getSelf();
        if (!Industries.ORBITALWORKS.equals(ind.getId())) return;

        MarketAPI market = ind.getMarket();
        OrbitalWorksSlotManager manager = OrbitalWorksSlotManager.getInstance();
        if (market == null || manager == null) return;

        manager.onWorksRemoved(market, (MarketAPI.MarketInteractionMode) context.getArg(0));
    }
}
