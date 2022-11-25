package industrial_evolution.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Random;

public class IndEvo_NebulaParticle {

    public static final float MIN_DISTANCE_BEFORE_DESPAWN = 20f;
    public static final float FADE_OUT_DIST = 0.65f;
    public static final float FADE_IN_DIST = 0.2f;
    public static final float BASE_VELOCITY_PX_PER_SEC = 1000f;
    public boolean expired = false;
    public Vector2f currentPos;
    public Vector2f currentTargetPos;
    public Vector2f originalPos;
    public float currentAlpha = 0f;
    public float baseAlphaMult;
    public float velocityMult;
    public CampaignFleetAPI target;
    public Color color;

    public float turnDir = 1f;
    public float angle = 1f;
    public float size;
    public float originalSize;
    public float elapsed;
    public int i;
    public int j;

    public IndEvo_NebulaParticle(Vector2f startPos, float size, float maxAlpha, Color color) {
        this.currentPos = startPos;
        this.originalPos = startPos;
        this.velocityMult = 1f - 0.3f * new Random().nextFloat();
        this.target = Global.getSector().getPlayerFleet();
        this.currentTargetPos = target.getLocation();
        this.size = size;
        this.originalSize = size;
        this.baseAlphaMult = maxAlpha;
        this.color = color;

        i = Misc.random.nextInt(4);
        j = Misc.random.nextInt(4);

        angle = (float) Math.random() * 360f;
        turnDir = Math.signum((float) Math.random() - 0.5f) * 10f * (float) Math.random();
    }

    public void advance(float amount) {
        if (expired) {
            currentAlpha = 0f;
            return;
        }

        Vector2f targetLoc = target.getLocation();
        float distance = Misc.getDistance(currentPos, targetLoc);
        float originalDistance = Misc.getDistance(originalPos, targetLoc);

        //fade in/out
        float maxFadeInDistance = originalDistance * FADE_IN_DIST;
        float minFadeOutDistance = originalDistance * FADE_OUT_DIST;
        float currentPassedDistance = originalDistance - distance;

        if(currentPassedDistance <= maxFadeInDistance) currentAlpha = Math.min(smootherstep(0f, maxFadeInDistance, currentPassedDistance), baseAlphaMult);
        else if (currentPassedDistance < minFadeOutDistance) currentAlpha = baseAlphaMult;
        else currentAlpha = Math.max(0, 1 - smootherstep(minFadeOutDistance, originalDistance, currentPassedDistance));

        //size down with dist, vanish at 5% distance mark
        //size = Math.max(0, originalSize * (distance / (originalDistance * 1.05f)));

        float vel = BASE_VELOCITY_PX_PER_SEC * velocityMult; //px per second
        float distanceThisFrame = vel * amount;

        Vector2f currentTargetDirection = VectorUtils.getDirectionalVector(currentPos, targetLoc);
        currentTargetDirection = VectorUtils.resize(currentTargetDirection, distance - distanceThisFrame);
        Vector2f newCurrentPos = Vector2f.sub(targetLoc, currentTargetDirection, null);
        currentPos = newCurrentPos;

        //WhEEEEEeeeeEEEEeeee
        angle += turnDir * amount;
        elapsed += amount;

        float dist = Misc.getDistance(target.getLocation(), currentPos);
        if (dist <= MIN_DISTANCE_BEFORE_DESPAWN || dist > 10000f) expired = true;
    }

    private float smootherstep(float edge0, float edge1, float x) {
        //https://en.wikipedia.org/wiki/Smoothstep
        x = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    private float clamp(float x, float lowerlimit, float upperlimit) {
        if (x < lowerlimit)
            x = lowerlimit;
        if (x > upperlimit)
            x = upperlimit;
        return x;
    }

    public boolean isExpired() {
        return expired;
    }
}
