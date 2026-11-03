package indevo.industries.senate.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import indevo.industries.senate.conditions.Edict_FastBuild;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

// industry option menu, obf com.fs.starfarer.campaign.ui.marketinfo.b on 0.98a-RC8, builds the upgrade confirm text in actionPerformed
@Patch(target = @ClassMatch(subtype = DialogCreatorUI.class, packageName = "com.fs.starfarer.campaign.ui.marketinfo"))
public class FastBuildUpgradeConfirmPatch {

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "actionPerformed", parameterCount = 2),
            call = @MethodMatch(methodName = "getBuildTime", parameterCount = 0),
            owner = @ClassMatch(subtype = Industry.class))
    public static void redirectUpgradeDays(MethodCallContext context) {
        Object result = context.call();

        if (result instanceof Float days && days >= 2f
                && context.getCallReceiver() instanceof Industry industry && Edict_FastBuild.isActive(industry.getMarket())) {
            result = (float) Edict_FastBuild.getReducedDays(days.intValue());
        }

        context.setResult(result);
    }
}
