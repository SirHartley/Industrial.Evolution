package indevo.industries.changeling.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.industry.Specialization;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

// the host plugin is never replaced, so the variant's description and extra section go into the host's own tooltip
@Patch(target = @ClassMatch(type = BaseIndustry.class))
public class ChangelingTooltipPatch {

    @RedirectMethodCall(target = @MethodMatch(methodName = "createTooltip", parameterCount = 3),
            call = @MethodMatch(methodName = "getDescriptionOverride", parameterCount = 0),
            owner = @ClassMatch(subtype = BaseIndustry.class))
    public static void redirectDescription(MethodCallContext context) {
        Object desc = context.call();

        if (context.getCallReceiver() instanceof Industry ind) {
            String variant = GovernmentCondition.getDescriptionOverride(ind);
            if (variant == null) variant = Specialization.getDescriptionOverride(ind);
            if (variant != null) desc = variant;
        }

        context.setResult(desc);
    }

    @RedirectMethodCall(target = @MethodMatch(methodName = "createTooltip", parameterCount = 3),
            call = @MethodMatch(methodName = "addRightAfterDescriptionSection", parameterCount = 2),
            owner = @ClassMatch(subtype = BaseIndustry.class))
    public static void redirectRightAfterDescription(MethodCallContext context) {
        context.setResult(context.call());

        if (context.getCallReceiver() instanceof Industry ind) {
            GovernmentCondition.addRightAfterDescription((TooltipMakerAPI) context.getCallArg(0), ind, (Industry.IndustryTooltipMode) context.getCallArg(1));
        }
    }
}
