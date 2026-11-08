package indevo.industries.salvageyards.patches;

import com.fs.starfarer.api.campaign.FactionProductionAPI.ProductionItemType;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.campaign.command.CustomProductionPanel;
import com.fs.starfarer.loading.specs.FactionProduction;
import indevo.industries.salvageyards.plugins.RegistryProductionDelegate;
import patchlib.api.context.AfterContext;
import patchlib.api.context.Context;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.store.PatchData;

import java.awt.*;

// ship row of the production browsers, seen as obf com.fs.starfarer.campaign.command.while in 0.98a-RC8, the only class there with getHullId and getTypeString
@Patch(target = @ClassMatch(
        packageName = "com.fs.starfarer.campaign.command",
        methodMatches = {
                @MethodMatch(methodName = "getHullId", parameterCount = 0),
                @MethodMatch(methodName = "getTypeString", parameterCount = 0)}))
public class RegistryProductionRowPatch {

    private static final String HULL_KEY = "IndEvo_registryRowHull";
    private static final String LABEL_KEY = "IndEvo_registryRowLabel";
    private static final String NAME_KEY = "IndEvo_registryRowName";
    private static final String COUNT_KEY = "IndEvo_registryRowCount";
    private static final String COST_KEY = "IndEvo_registryRowCost";

    private static final FieldQuerySpec PANEL_FIELD = FieldQuery.create().type(CustomProductionPanel.class).build();

    // a hull is either on the ships tab or on the registry tab, so the hull id tells which row this is
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "createRow", parameterCount = 0),
            call = @MethodMatch(methodName = "getNameWithDesignationWithDashClass", parameterCount = 0),
            owner = @ClassMatch(subtype = ShipHullSpecAPI.class))
    public static void redirectName(MethodCallContext context) {
        String name = (String) context.call();
        context.setResult(name);

        CustomProductionPanel panel = getPanel(context);
        if (panel == null || !(panel.getDelegate() instanceof RegistryProductionDelegate delegate)) return;

        ShipHullSpecAPI spec = (ShipHullSpecAPI) context.getCallReceiver();
        if (!delegate.getRegistryHulls().contains(spec.getHullId())) return;

        int count = getRemaining(delegate, panel.getProd(), spec.getHullId());
        context.getData().put(HULL_KEY, spec);
        context.getData().put(COUNT_KEY, count);
        context.setResult(getPrefix(count) + name);
    }

    // the name label is the first label createRow makes after the name lookup
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "createRow", parameterCount = 0),
            call = @MethodMatch(methodName = "create", parameterTypes = {String.class, Color.class}),
            owner = @ClassMatch(subtype = LabelAPI.class))
    public static void redirectLabel(MethodCallContext context) {
        Object label = context.call();
        context.setResult(label);

        PatchData data = context.getData();
        if (!data.has(HULL_KEY) || data.has(LABEL_KEY) || !(label instanceof LabelAPI)) return;

        String text = (String) context.getCallArg(0);
        String prefix = getPrefix(data.<Integer>get(COUNT_KEY));
        if (!text.startsWith(prefix)) return;

        data.putWeak(LABEL_KEY, label);
        data.put(NAME_KEY, text.substring(prefix.length()));
    }

    // vanilla builds the row once, the count follows the order while the picker is open
    @After(target = @MethodMatch(methodName = "advanceImpl", parameterCount = 1))
    public static void afterAdvance(AfterContext context) {
        PatchData data = context.getData();
        if (!data.has(NAME_KEY)) return;

        LabelAPI label = data.get(LABEL_KEY);
        CustomProductionPanel panel = getPanel(context);
        if (label == null || panel == null || !(panel.getDelegate() instanceof RegistryProductionDelegate delegate)) return;

        FactionProduction production = panel.getProd();
        int cost = production.getTotalCurrentCost();
        if (data.has(COST_KEY) && data.<Integer>get(COST_KEY) == cost) return;
        data.put(COST_KEY, cost);

        ShipHullSpecAPI spec = data.get(HULL_KEY);
        int count = getRemaining(delegate, production, spec.getHullId());
        if (data.<Integer>get(COUNT_KEY) == count) return;

        data.put(COUNT_KEY, count);
        label.setText(getPrefix(count) + data.get(NAME_KEY));
        label.highlightFirst(spec.getHullName());
    }

    private static CustomProductionPanel getPanel(Context context) {
        if (!context.hasField(PANEL_FIELD)) return null;
        return context.getField(PANEL_FIELD).get() instanceof CustomProductionPanel panel ? panel : null;
    }

    private static int getRemaining(RegistryProductionDelegate delegate, FactionProduction production, String hullId) {
        return Math.max(0, delegate.getMaxQuantity(production, ProductionItemType.SHIP, hullId) - production.getCount(ProductionItemType.SHIP, hullId));
    }

    private static String getPrefix(int count) {
        return count + "x ";
    }
}
