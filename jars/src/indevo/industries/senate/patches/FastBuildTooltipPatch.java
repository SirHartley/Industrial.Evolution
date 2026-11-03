package indevo.industries.senate.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import indevo.industries.senate.conditions.Edict_FastBuild;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

// industry tooltip, anonymous StandardTooltipV2Expandable in obf com.fs.starfarer.campaign.ui.marketinfo.intnew.createIndustryTooltip on 0.98a-RC8
@Patch(target = @ClassMatch(
        subtypeName = "com.fs.starfarer.ui.impl.StandardTooltipV2Expandable",
        packageName = "com.fs.starfarer.campaign.ui.marketinfo"))
public class FastBuildTooltipPatch {

    // BaseIndustry.createTooltip reads the days from the shared spec, so it holds the halved value for this one call only
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "createImpl", parameterTypes = boolean.class),
            call = @MethodMatch(methodName = "createTooltip", parameterCount = 3),
            owner = @ClassMatch(subtype = Industry.class))
    public static void redirectTooltip(MethodCallContext context) {
        IndustrySpecAPI spec = getSpecToHalve(context);
        if (spec == null) {
            context.call();
            return;
        }

        float original = spec.getBuildTime();
        spec.setBuildTime(Edict_FastBuild.getReducedDays((int) original));

        try {
            context.call();
        } finally {
            spec.setBuildTime(original);
        }
    }

    private static IndustrySpecAPI getSpecToHalve(MethodCallContext context) {
        if (!(context.getCallReceiver() instanceof Industry industry) || !(context.getCallArg(0) instanceof Industry.IndustryTooltipMode mode)) return null;
        if (mode != Industry.IndustryTooltipMode.ADD_INDUSTRY && mode != Industry.IndustryTooltipMode.QUEUED && mode != Industry.IndustryTooltipMode.UPGRADE) return null;
        if (!Edict_FastBuild.isActive(industry.getMarket())) return null;

        IndustrySpecAPI spec = industry.getSpec();
        return spec != null && spec.getBuildTime() >= 2f ? spec : null;
    }
}
