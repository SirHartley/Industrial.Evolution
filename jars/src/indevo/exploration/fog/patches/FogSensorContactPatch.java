package indevo.exploration.fog.patches;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import indevo.exploration.fog.terrain.FogTerrainPlugin;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;

// both methods are declared on the game's BaseCampaignEntity, fleets inherit them
@Patch(target = @ClassMatch(subtype = SectorEntityToken.class))
public class FogSensorContactPatch {

    private static boolean vanilla;

    public static VisibilityLevel getVanillaLevelToPlayerFleet(SectorEntityToken entity) {
        vanilla = true;
        try {
            return entity.getVisibilityLevelToPlayerFleet();
        } finally {
            vanilla = false;
        }
    }

    @After(target = @MethodMatch(methodName = "isVisibleToSensorsOf", parameterTypes = {SectorEntityToken.class}))
    public static void afterIsVisibleToSensorsOf(AfterContext context) {
        if (vanilla || Boolean.TRUE.equals(context.getReturnValue())) return;
        if (FogTerrainPlugin.isFogContact(context.getSelf(), context.getArg(0))) context.setReturnValue(true);
    }

    @After(target = @MethodMatch(methodName = "getVisibilityLevelTo", parameterTypes = {SectorEntityToken.class}))
    public static void afterGetVisibilityLevelTo(AfterContext context) {
        if (vanilla || context.getReturnValue() == VisibilityLevel.SENSOR_CONTACT) return;
        if (!FogTerrainPlugin.isFogContact(context.getSelf(), context.getArg(0))) return;
        // vanilla gives full details within 50 units even where its own sensor check hides the fleet
        if (context.getReturnValue() != VisibilityLevel.NONE && isVanillaVisible(context)) return;
        context.setReturnValue(VisibilityLevel.SENSOR_CONTACT);
    }

    private static boolean isVanillaVisible(AfterContext context) {
        vanilla = true;
        try {
            return ((SectorEntityToken) context.getSelf()).isVisibleToSensorsOf((SectorEntityToken) context.getArg(0));
        } finally {
            vanilla = false;
        }
    }
}
