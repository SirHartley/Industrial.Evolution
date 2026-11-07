package indevo.industries.worldwonder.patches;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.MethodQuerySpec;
import patchlib.api.store.PatchData;

// colony screen planet background, obf com.fs.starfarer.campaign.ui.marketinfo.nullnew on 0.98a-RC8, only class there with setForce2dMode
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.campaign.ui.marketinfo",
        methodMatches = @MethodMatch(methodName = "setForce2dMode")))
public class PlanetBackgroundRefreshPatch {

    private static final String SPEC_KEY = "IndEvo_planetBackgroundSpec";
    private static final MethodQuerySpec GET_PLANET = MethodQuery.create().methodName("getPlanet").parameterCount(0).build();
    private static final MethodQuerySpec SET_PLANET = MethodQuery.create().methodName("setPlanet").parameterCount(1).build();

    // setPlanet builds the sprites once, applySpecChanges swaps in a new spec object, so a new identity means stale sprites
    @After(target = @MethodMatch(methodName = "advanceImpl", parameterTypes = float.class))
    public static void afterAdvance(AfterContext context) {
        if (!(context.getMethod(GET_PLANET).call() instanceof PlanetAPI planet)) return;

        PlanetSpecAPI spec = planet.getSpec();
        PatchData data = context.getData();
        Object shown = data.get(SPEC_KEY);
        if (shown == spec) return;

        if (shown != null) context.getMethod(SET_PLANET).call(planet);
        data.put(SPEC_KEY, spec);
    }
}
