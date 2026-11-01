package indevo.industries.engineeringhub.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.engineeringhub.industry.EngineeringHub;
import indevo.items.installable.BlueprintInstallableItemPlugin;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.FieldMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.spec.FieldQuerySpec;

import java.util.ArrayList;
import java.util.List;

// install item dialog, obf com.fs.starfarer.campaign.ui.marketinfo.OOoo on 0.98a-RC8, lists stacks from copies of real cargo
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.campaign.ui.marketinfo",
        fieldMatches = @FieldMatch(type = InstallableIndustryItemPlugin.class),
        methodMatches = @MethodMatch(parameterTypes = {CargoAPI.class, List.class, String.class})))
public class RelicInstallDialogPatch {

    private static final FieldQuerySpec PLUGIN_FIELD = FieldQuery.create().type(InstallableIndustryItemPlugin.class).build();

    @RedirectMethodCall(
            target = @MethodMatch(parameterTypes = {CargoAPI.class, List.class, String.class}),
            call = @MethodMatch(methodName = "getStacksCopy", parameterCount = 0),
            owner = @ClassMatch(subtype = CargoAPI.class))
    public static void redirectListedStacks(MethodCallContext context) {
        Object result = context.call();
        EngineeringHub hub = getHub(context);

        if (hub != null && result instanceof List<?> stacks && context.getArg(0) == Misc.getStorageCargo(hub.getMarket())) {
            CargoStackAPI relic = Global.getFactory().createCargoStack(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.RELIC_SPECIAL_ITEM, null), null);
            relic.setSize(1);

            List<Object> withRelic = new ArrayList<>(stacks);
            withRelic.add(relic);
            result = withRelic;
        }

        context.setResult(result);
    }

    // swap, uninstall and cancel put the old item back into cargo; the relic never came from there
    @RedirectMethodCall(
            call = @MethodMatch(methodName = "addItems", parameterCount = 3),
            owner = @ClassMatch(subtype = CargoAPI.class))
    public static void redirectPutBack(MethodCallContext context) {
        if (EngineeringHub.isRelicItem(context.getCallArg(1))) return;

        context.call();
    }

    private static EngineeringHub getHub(MethodCallContext context) {
        Object plugin = context.getField(PLUGIN_FIELD).get();
        return plugin instanceof BlueprintInstallableItemPlugin blueprintPlugin ? blueprintPlugin.getIndustry() : null;
    }
}
