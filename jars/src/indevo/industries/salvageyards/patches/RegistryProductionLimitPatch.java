package indevo.industries.salvageyards.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionProductionAPI.ProductionItemType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.loading.specs.FactionProduction;
import indevo.industries.salvageyards.plugins.RegistryProductionDelegate;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;

// FactionProduction is DoNotObfuscate (0.98a-RC8), getDelegate is inferred, addItem comes from FactionProductionAPI
@Patch(target = @ClassMatch(type = FactionProduction.class))
public class RegistryProductionLimitPatch {

    // every picker add (browser rows, plan table) goes through the four argument overload
    @Before(target = @MethodMatch(methodName = "addItem", parameterCount = 4))
    public static void beforeAddItem(BeforeContext context) {
        if (context.isSkipOriginal()) return;
        if (!(context.getSelf() instanceof FactionProduction production)) return;
        if (!(production.getDelegate() instanceof RegistryProductionDelegate delegate)) return;

        ProductionItemType type = (ProductionItemType) context.getArg(0);
        String id = (String) context.getArg(1);
        int add = (int) context.getArg(2);

        int max = delegate.getMaxQuantity(production, type, id);
        if (max == Integer.MAX_VALUE) return;

        int current = production.getCount(type, id);
        if (current + add <= max) return;

        Global.getSector().getCampaignUI().getMessageDisplay().addMessage(delegate.getLimitMessage(production, type, id), Misc.getNegativeHighlightColor());

        if (max <= current) context.skipOriginal(false);
        else context.setArg(3, max);
    }
}
