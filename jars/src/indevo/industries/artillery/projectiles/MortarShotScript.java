package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;

public class MortarShotScript implements EveryFrameScript {

    public static final float AVERAGE_PROJ_IMPACT_TIME = Global.getSettings().getFloat("IndEvo_Artillery_mortar_projectilesImpactTime");
    public static final float MIN_TARGET_RADIUS = 250f;
    public static final float MAX_TARGET_RADIUS = 700f;
    public static final int DEFAULT_PROJECTILE_AMT = Global.getSettings().getInt("IndEvo_Artillery_mortar_projectilesPerShot");

    public SectorEntityToken target;
    public SectorEntityToken origin;
    public int amt;

    public boolean done = false;

    public MortarShotScript(SectorEntityToken origin, SectorEntityToken target, int amt) {
        this.origin = origin;
        this.target = target;
        this.amt = amt;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (isDone()) return;

        List<Vector2f> targetList = getTargetList(target, AVERAGE_PROJ_IMPACT_TIME);

        for (Vector2f t : targetList) {
            MortarProjectileEntityPlugin.spawn(origin.getContainingLocation(), origin, t, (float) (AVERAGE_PROJ_IMPACT_TIME + 3f * random()));
        }

        done = true;
    }

    public static Vector2f getAnticipatedTargetLoc(SectorEntityToken entity) {
        Vector2f vel = entity.getVelocity();
        float dist = vel.length() * AVERAGE_PROJ_IMPACT_TIME * 1.1f;
        float currentNavigationAngle = Misc.getAngleInDegrees(vel);
        Vector2f location = Misc.getUnitVectorAtDegreeAngle(currentNavigationAngle);
        location.scale(dist);
        return Vector2f.add(location, entity.getLocation(), location);
    }

    public List<Vector2f> getTargetList(SectorEntityToken entity, float impactTimeSeconds) {
        //Je langsamer desto offener der Winkel?
        //Nein, wechsel zu nicht-winkelbasiertem Kreissystem

        List<Vector2f> targetList = new ArrayList<>();

        Vector2f vel = entity.getVelocity();
        float dist = vel.length() * impactTimeSeconds * 1.1f;
        float currentNavigationAngle = Misc.getAngleInDegrees(vel);
        Vector2f location = Misc.getUnitVectorAtDegreeAngle(currentNavigationAngle);
        location.scale(dist);
        Vector2f anticipatedEntityPos = Vector2f.add(location, entity.getLocation(), location);

        float targetRadius = MathUtils.clamp(dist, MIN_TARGET_RADIUS, MAX_TARGET_RADIUS);

        ModPlugin.log("target radius " + targetRadius + " impact length " + dist);

        for (int i = 0; i < amt; i++) {
            //spawn 5 reticules without overlap within the circle
            //spawn each, check if they overlap, then just adjust the coodinates until they do not

            float r = (float) (targetRadius * Math.sqrt(random()));
            float theta = (float) (random() * 2 * Math.PI);

            Vector2f ret = new Vector2f();
            ret.x = (float) (anticipatedEntityPos.x + r * cos(theta));
            ret.y = (float) (anticipatedEntityPos.y + r * sin(theta));

            targetList.add(ret);
        }

        return targetList;
    }
}
