package indevo.exploration.meteor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.MiscIE;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

public class WarningSignNotificationRenderer implements LunaCampaignRenderingPlugin {

    public static final float WARNING_SIGN_FADE_IN = 0.5f;
    public static final float WARNING_SIGN_FADE_OUT = 15f;
    public static final float WARNING_SIGN_SIZE = 80f;
    public static final float ARROW_SIZE = 80f;
    public static final float DISTANCE_FROM_FLEET = 300f;

    public transient SpriteAPI warningSign;
    public transient SpriteAPI arrow;

    public CircularArc arc;
    public LocationAPI location;
    public Vector2f warningSignLoc;
    public Vector2f arrowLoc;

    public float timePassed = 0f;
    public float alpha = 0f;

    public WarningSignNotificationRenderer(CircularArc arc, LocationAPI location) {
        this.arc = arc;
        this.location = location;
        recalculateLocation();
    }

    //"IndEvo_missile_targetting_arrow":"graphics/fx/IndEvo_targeting_arrow.png",
    //          "IndEvo_warning_sign":"graphics/fx/IndEvo_warning.png",

    @Override
    public boolean isExpired() {
        return timePassed > WARNING_SIGN_FADE_IN + WARNING_SIGN_FADE_OUT;
    }

    @Override
    public void advance(float amount) {
        timePassed += amount;

        //loc
        recalculateLocation();

        //alpha
        float fadeInFactor = timePassed / WARNING_SIGN_FADE_IN;
        float fadeOutFactor = (timePassed - WARNING_SIGN_FADE_IN) / WARNING_SIGN_FADE_OUT;
        float correctedFadeOut = MiscIE.smootherstep(1,0,fadeOutFactor);

        boolean fadeIn = fadeInFactor <= 1f;
        if (fadeIn) alpha = fadeInFactor;
        else alpha = correctedFadeOut;
    }

    public void recalculateLocation(){
        SectorEntityToken fleet = Global.getSector().getPlayerFleet();
        float distance = fleet.getRadius() + DISTANCE_FROM_FLEET;
        Vector2f fleetLoc = fleet.getLocation();

        warningSignLoc = MathUtils.getPointOnCircumference(fleetLoc, distance, Misc.getAngleInDegrees(arc.center, fleetLoc));
        arrowLoc = MathUtils.getPointOnCircumference(fleetLoc, distance + 200f, Misc.getAngleInDegrees(arc.center, fleetLoc));
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (!location.isCurrentLocation()) return;

        if (warningSign == null){
            warningSign = Global.getSettings().getSprite("fx", "IndEvo_warning_sign");
            arrow = Global.getSettings().getSprite("fx", "IndEvo_missile_targetting_arrow");
        }

        warningSign.setAlphaMult(0.8f * alpha);
        warningSign.setWidth(WARNING_SIGN_SIZE);
        warningSign.setHeight(WARNING_SIGN_SIZE);
        warningSign.setColor(Color.RED);
        warningSign.renderAtCenter(warningSignLoc.x, warningSignLoc.y);

        arrow.setAlphaMult(0.8f * alpha);
        arrow.setWidth(ARROW_SIZE);
        arrow.setHeight(ARROW_SIZE);
        arrow.setColor(new Color(0xBD0606));
        arrow.setAngle(Misc.getAngleInDegrees(warningSignLoc, arc.center) + 90f);
        arrow.renderAtCenter(arrowLoc.x, arrowLoc.y);
    }
}
