package indevo.industries.worldwonder.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PlanetaryShield;
import indevo.industries.worldwonder.industry.CloudPainter;
import indevo.utils.helper.Settings;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

import java.awt.*;

@Patch(target = @ClassMatch(type = PlanetaryShield.class))
public class CloudPainterShieldPatch {

    public static final String TAG_FULL = "IndEvo_PS_FULL";
    public static final String TAG_LOW = "IndEvo_PS_LOW";
    public static final String TAG_NONE = "IndEvo_PS_NONE";

    // vanilla apply only reaches this call while the shield is functional
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "apply", parameterCount = 0),
            call = @MethodMatch(methodName = "applyVisuals", parameterCount = 1),
            owner = @ClassMatch(type = PlanetaryShield.class))
    public static void redirectApplyVisuals(MethodCallContext context) {
        MarketAPI market = ((Industry) context.getSelf()).getMarket();

        if (!hasFunctionalCloudPainter(market)) {
            context.call();
            return;
        }

        if (!market.hasTag(TAG_FULL) && !market.hasTag(TAG_LOW) && !market.hasTag(TAG_NONE)) market.addTag(getDefaultTag());

        PlanetAPI planet = (PlanetAPI) context.getCallArg(0);
        if (market.hasTag(TAG_LOW)) applyAlternateVisuals(planet);
        else if (market.hasTag(TAG_NONE)) PlanetaryShield.unapplyVisuals(planet);
        else context.call();
    }

    public static boolean hasFunctionalCloudPainter(MarketAPI market) {
        if (market == null) return false;

        for (Industry industry : market.getIndustries()) {
            if (industry instanceof CloudPainter && industry.isFunctional()) return true;
        }

        return false;
    }

    public static String getDefaultTag() {
        if (Settings.getBoolean(Settings.CLOUD_PAINTER_SHIELD_REMOVE)) return TAG_NONE;
        if (Settings.getBoolean(Settings.CLOUD_PAINTER_SHIELD_OVERRIDE)) return TAG_LOW;
        return TAG_FULL;
    }

    private static void applyAlternateVisuals(PlanetAPI planet) {
        if (planet == null) return;
        planet.getSpec().setShieldTexture(Global.getSettings().getSpriteName("IndEvo", "shield_texture"));
        planet.getSpec().setShieldThickness(0.1f);
        planet.getSpec().setShieldColor(new Color(255, 255, 255, 175));
        planet.applySpecChanges();
    }
}
