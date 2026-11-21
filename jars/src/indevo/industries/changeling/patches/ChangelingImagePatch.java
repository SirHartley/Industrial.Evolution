package indevo.industries.changeling.patches;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.industry.Specialization;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;

// population and mining skip imageOverride at some sizes, a government's or specialization's own override wins there
@Patch(target = @ClassMatch(subtype = BaseIndustry.class))
public class ChangelingImagePatch {

    @After(target = @MethodMatch(methodName = "getCurrentImage", parameterCount = 0))
    public static void afterGetCurrentImage(AfterContext context) {
        if (!context.isMostDerivedCall() || !(context.getSelf() instanceof BaseIndustry ind) || ind.getImageOverride() == null) return;
        if (GovernmentCondition.isOwnImage(ind) || Specialization.isOwnImage(ind)) context.setReturnValue(ind.getImageOverride());
    }
}
