package indevo.industries.senate.patches;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import indevo.industries.senate.conditions.Edict_FastBuild;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

// industry card in the colony industry list, obf com.fs.starfarer.campaign.ui.marketinfo.intnew on 0.98a-RC8
@Patch(target = @ClassMatch(
        packageName = "com.fs.starfarer.campaign.ui.marketinfo",
        methodMatches = @MethodMatch(methodName = "addCostTimeLabels")))
public class FastBuildQueuedIndustryPatch {

    // queued items read the spec time; the edict only halves a build once it has started
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "addCostTimeLabels", parameterCount = 0),
            call = @MethodMatch(methodName = "getBuildTime", parameterCount = 0),
            owner = @ClassMatch(subtype = IndustrySpecAPI.class))
    public static void redirectQueuedDays(MethodCallContext context) {
        Object result = context.call();

        if (result instanceof Float days && days >= 2f && Edict_FastBuild.isActive(getMarket(context))) {
            result = (float) Edict_FastBuild.getReducedDays(days.intValue());
        }

        context.setResult(result);
    }

    private static MarketAPI getMarket(MethodCallContext context) {
        if (!context.hasField("market")) return null;
        Object market = context.getField("market").get();
        return market instanceof MarketAPI m ? m : null;
    }
}
