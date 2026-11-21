package indevo.industries.changeling.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.industry.Specialization;
import patchlib.api.context.AfterContext;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

// the host plugin is never replaced, so the variant's description goes into the host's own tooltip
@Patch(target = @ClassMatch(type = BaseIndustry.class))
public class ChangelingTooltipPatch {

    @RedirectMethodCall(target = @MethodMatch(methodName = "createTooltip", parameterCount = 3),
            call = @MethodMatch(methodName = "getDescriptionOverride", parameterCount = 0),
            owner = @ClassMatch(subtype = BaseIndustry.class))
    public static void redirectDescription(MethodCallContext context) {
        Object desc = context.call();

        if (context.getCallReceiver() instanceof Industry ind) {
            // createTooltip(mode, tooltip, expanded)
            boolean expanded = Boolean.TRUE.equals(context.getArg(2));
            String variant = GovernmentCondition.getDescriptionOverride(ind, expanded);
            if (variant == null) variant = Specialization.getDescriptionOverride(ind);
            if (variant != null) desc = variant;
        }

        context.setResult(desc);
    }

    // a governed population keeps its effect lines and lore behind F1; only ever turns false into true
    @After(target = @MethodMatch(methodName = "isTooltipExpandable", parameterCount = 0))
    public static void afterTooltipExpandable(AfterContext context) {
        if (Boolean.TRUE.equals(context.getReturnValue())) return;
        if (context.getSelf() instanceof Industry ind && GovernmentCondition.isExpandable(ind)) context.setReturnValue(true);
    }
}
