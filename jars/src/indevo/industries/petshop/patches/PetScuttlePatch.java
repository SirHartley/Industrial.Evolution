package indevo.industries.petshop.patches;

import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.industries.petshop.listener.PetStatusManager;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;

// scuttling fires no listener, vanilla FleetData is the only FleetDataAPI implementation
@Patch(target = @ClassMatch(subtype = FleetDataAPI.class))
public class PetScuttlePatch {

    @After(target = @MethodMatch(methodName = "scuttle", parameterTypes = FleetMemberAPI.class))
    public static void afterScuttle(AfterContext context) {
        PetStatusManager.getInstance().reportScuttled((FleetDataAPI) context.getSelf(), (FleetMemberAPI) context.getArg(0));
    }
}
