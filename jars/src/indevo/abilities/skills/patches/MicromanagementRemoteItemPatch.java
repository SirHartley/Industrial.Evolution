package indevo.abilities.skills.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import indevo.abilities.skills.scripts.AdminGovernTimeTracker;
import indevo.ids.Ids;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.MethodType;
import patchlib.api.match.StaticType;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

// industry option menu, obf com.fs.starfarer.campaign.ui.marketinfo.b on 0.98a-RC8 windows, reads the setting once in its constructor
@Patch(target = @ClassMatch(subtype = DialogCreatorUI.class, packageName = "com.fs.starfarer.campaign.ui.marketinfo"))
public class MicromanagementRemoteItemPatch {

    public static final String SETTING = "allowRemoteIndustryItemManagement";
    public static final int MIN_DAYS_IN_OFFICE = 93;

    private static boolean loggedMissingIndustry = false;

    @RedirectMethodCall(
            target = @MethodMatch(methodType = MethodType.CONSTRUCTOR),
            call = @MethodMatch(parameterTypes = String.class, returnType = boolean.class, staticType = StaticType.STATIC),
            owner = @ClassMatch(typeName = "com.fs.starfarer.settings.StarfarerSettings"))
    public static void redirectSettingRead(MethodCallContext context) {
        boolean value = (Boolean) context.call();
        if (!value && SETTING.equals(context.getCallArg(0))) value = isMicromanaged(context.getArgs());

        context.setResult(value);
    }

    private static boolean isMicromanaged(Object[] hostArgs) {
        for (Object arg : hostArgs) {
            if (arg instanceof Industry industry) return isMicromanaged(industry.getMarket());
        }

        if (!loggedMissingIndustry) {
            loggedMissingIndustry = true;
            Global.getLogger(MicromanagementRemoteItemPatch.class).error("Industry option menu constructor has no Industry argument, Micromanagement remote item management is off", new IllegalStateException());
        }

        return false;
    }

    private static boolean isMicromanaged(MarketAPI market) {
        if (market == null || !market.isPlayerOwned()) return false;

        PersonAPI admin = market.getAdmin();
        if (admin == null || !admin.getStats().hasSkill(Ids.MICROMANAGEMENT)) return false;

        return Global.getSettings().isDevMode() || AdminGovernTimeTracker.getInstanceOrRegister().getValueForMarket(market.getId()) > MIN_DAYS_IN_OFFICE;
    }
}
