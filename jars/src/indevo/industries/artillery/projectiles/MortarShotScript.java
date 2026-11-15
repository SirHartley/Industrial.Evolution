package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;

public class MortarShotScript implements EveryFrameScript {

    public static final float MIN_TARGET_RADIUS = 250f;
    public static final float MAX_TARGET_RADIUS = 700f;

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

        Vector2f center = getAnticipatedTargetLoc(origin, target);
        SectorEntityToken track = target.isPlayerFleet() ? target : null;

        for (Vector2f t : getTargetList(target, center, getLeadSeconds(origin, target))) {
            float warningSeconds = (float) (Setting.ARTILLERY_MORTAR_PROJECTILES_IMPACT_TIME.value() + 3f * random());
            MortarProjectileEntityPlugin.spawn(origin.getContainingLocation(), origin, t, warningSeconds, track, Vector2f.sub(t, center, null));
        }

        done = true;
    }

    public static Vector2f getAnticipatedTargetLoc(SectorEntityToken origin, SectorEntityToken entity) {
        return predict(entity, getLeadSeconds(origin, entity));
    }

    // shells leave at once when the flight outlasts the warning, so the lead covers the flight
    public static float getLeadSeconds(SectorEntityToken origin, SectorEntityToken entity) {
        float impact = Setting.ARTILLERY_MORTAR_PROJECTILES_IMPACT_TIME.value();
        float lead = Math.max(impact, MortarProjectileEntityPlugin.AVERAGE_LAUNCH_DELAY + Misc.getDistance(origin, entity) / MortarProjectileEntityPlugin.PROJECTILE_VELOCITY);
        float flight = Misc.getDistance(origin.getLocation(), predict(entity, lead)) / MortarProjectileEntityPlugin.PROJECTILE_VELOCITY;
        return Math.max(impact, MortarProjectileEntityPlugin.AVERAGE_LAUNCH_DELAY + flight);
    }

    public static Vector2f predict(SectorEntityToken entity, float seconds) {
        Vector2f vel = entity.getVelocity();
        float dist = vel.length() * seconds * 1.1f;
        Vector2f location = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(vel));
        location.scale(dist);
        return Vector2f.add(location, entity.getLocation(), location);
    }

    public List<Vector2f> getTargetList(SectorEntityToken entity, Vector2f anticipatedEntityPos, float leadSeconds) {
        //Je langsamer desto offener der Winkel?
        //Nein, wechsel zu nicht-winkelbasiertem Kreissystem

        List<Vector2f> targetList = new ArrayList<>();

        float dist = entity.getVelocity().length() * leadSeconds * 1.1f;
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
