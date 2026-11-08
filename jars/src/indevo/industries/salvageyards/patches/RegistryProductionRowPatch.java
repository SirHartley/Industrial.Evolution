package indevo.industries.salvageyards.patches;

import com.fs.starfarer.api.campaign.FactionProductionAPI.ProductionItemType;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.campaign.command.CustomProductionPanel;
import indevo.industries.salvageyards.plugins.RegistryProductionDelegate;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.spec.FieldQuerySpec;

// ship row of the production browsers, seen as obf com.fs.starfarer.campaign.command.while in 0.98a-RC8, the only class there with getHullId and getTypeString
@Patch(target = @ClassMatch(
        packageName = "com.fs.starfarer.campaign.command",
        methodMatches = {
                @MethodMatch(methodName = "getHullId", parameterCount = 0),
                @MethodMatch(methodName = "getTypeString", parameterCount = 0)}))
public class RegistryProductionRowPatch {

    private static final FieldQuerySpec PANEL_FIELD = FieldQuery.create().type(CustomProductionPanel.class).build();

    // a hull is either on the ships tab or on the registry tab, so the hull id tells which row this is
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "createRow", parameterCount = 0),
            call = @MethodMatch(methodName = "getNameWithDesignationWithDashClass", parameterCount = 0),
            owner = @ClassMatch(subtype = ShipHullSpecAPI.class))
    public static void redirectName(MethodCallContext context) {
        String name = (String) context.call();
        context.setResult(name);

        if (!context.hasField(PANEL_FIELD)) return;
        if (!(context.getField(PANEL_FIELD).get() instanceof CustomProductionPanel panel)) return;
        if (!(panel.getDelegate() instanceof RegistryProductionDelegate delegate)) return;

        String id = ((ShipHullSpecAPI) context.getCallReceiver()).getHullId();
        if (!delegate.getRegistryHulls().contains(id)) return;

        context.setResult(delegate.getMaxQuantity(null, ProductionItemType.SHIP, id) + "x " + name);
    }
}
