package indevo.industries.artillery.patches;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;

@Patch(target = @ClassMatch(type = Misc.class))
public class WatchtowerStableLocationPatch {

    // watchtowers carry the objective tag but never sit on a stable location
    @After(target = @MethodMatch(methodName = "getNumStableLocations", parameterCount = 1))
    public static void afterGetNumStableLocations(AfterContext context) {
        if (!(context.getArg(0) instanceof StarSystemAPI system)) return;

        int watchtowers = 0;
        for (SectorEntityToken entity : system.getEntitiesWithTag(Ids.TAG_WATCHTOWER)) {
            if (entity.hasTag(Tags.OBJECTIVE)) watchtowers++;
        }

        if (watchtowers == 0) return;

        int count = (Integer) context.getReturnValue();
        context.setReturnValue(Math.max(0, count - watchtowers));
    }
}
