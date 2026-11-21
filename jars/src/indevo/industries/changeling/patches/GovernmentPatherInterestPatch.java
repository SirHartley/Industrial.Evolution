package indevo.industries.changeling.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager;
import indevo.industries.changeling.conditions.GovernmentCondition;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

@Patch(target = @ClassMatch(type = LuddicPathBaseManager.class))
public class GovernmentPatherInterestPatch {

    // the population plugin is never replaced, so a government's pather interest stands in for the population's own
    @RedirectMethodCall(target = @MethodMatch(methodName = "getLuddicPathMarketInterest", parameterCount = 1),
            call = @MethodMatch(methodName = "getPatherInterest", parameterCount = 0),
            owner = @ClassMatch(subtype = Industry.class))
    public static void redirectPatherInterest(MethodCallContext context) {
        Float interest = context.getCallReceiver() instanceof Industry ind ? GovernmentCondition.getPatherInterest(ind) : null;
        context.setResult(interest != null ? interest : context.call());
    }
}
