package indevo.industries.salvageyards.patches;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.spec.FieldQuerySpec;

import java.util.Map;

// anonymous tooltips of the production browsers (cost column, rows), their only own field is the outer browser
@Patch(target = @ClassMatch(subtype = TooltipMakerAPI.class, packageName = "com.fs.starfarer.campaign.command"))
public class RegistryProductionTooltipPatch {

    private static final FieldQuerySpec BROWSER_FIELD = FieldQuery.create().subtype(UIPanelAPI.class).build();

    private static final Map<String, String> REPLACEMENTS = Map.of(
            "Cost to produce a hull of this ship type. The hull will not have any weapons or fighter LPCs mounted.",
            "Cost to produce a hull of this ship type. It comes with the weapons, fighter LPCs and hullmods it had when it was lost, those are charged at their value on top.",
            "Ship will not be outfitted with weapons and fighters.",
            "Ship comes with the weapons, fighters and hullmods it had when it was lost.");

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "createImpl", parameterCount = 1),
            call = @MethodMatch(methodName = "addPara", parameterTypes = {String.class, float.class}),
            owner = @ClassMatch(subtype = TooltipMakerAPI.class))
    public static void redirectPara(MethodCallContext context) {
        String replacement = REPLACEMENTS.get((String) context.getCallArg(0));

        if (replacement != null && context.hasField(BROWSER_FIELD) && RegistryProductionTabPatch.isRegistryBrowser(context.getField(BROWSER_FIELD).get())) {
            context.setCallArg(0, replacement);
        }

        context.setResult(context.call());
    }
}
