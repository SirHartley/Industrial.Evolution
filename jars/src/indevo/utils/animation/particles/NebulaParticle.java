package indevo.utils.animation.particles;

import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.items.consumables.entities.SmokeCloudEntityPlugin.mixColors;

public class NebulaParticle {
    public static final float MAX_ROTATION_PER_SEC = 4f;

    public float size, angle, baseAlpha, elapsed, duration;
    public int i, j;
    public LocationData pos;
    private final Color originalColor;
    private final Color targetColor;
    public Color color;
    public float angleRotation;
    public static final float SPAWN_IN_SEC = 0.5f;

    public static class LocationData {
        float radius;
        float angle;

        public LocationData(float radius, float angle) {
            this.radius = radius;
            this.angle = angle;
        }

        public Vector2f getLocation(Vector2f center){
            return MathUtils.getPointOnCircumference(center, radius, angle);
        }

        public void incrementRadius(float byAmount){
            radius += byAmount;
        }
    }

    public NebulaParticle(float size, float angle, float alpha, float duration, Color originalColor, Color targetColor, LocationData pos) {
        this.size = size;
        this.angle = angle;
        this.baseAlpha = alpha;
        this.originalColor = originalColor;
        this.targetColor = targetColor;
        this.duration = duration;
        this.pos = pos;
        this.i = Misc.random.nextInt(4);
        this.j = Misc.random.nextInt(4);

        angleRotation = MAX_ROTATION_PER_SEC * Misc.random.nextFloat();
        if(Misc.random.nextBoolean()) angleRotation *= -1;

        advance(0);
    }

    public void advance(float amt) {
        float timePassedMult = Math.min(elapsed / duration, 1);
        float smoothedDur = MiscIE.smootherstep(0, 1, timePassedMult);
        this.color = mixColors(originalColor, targetColor, smoothedDur);

        angle += angleRotation * amt;
        if (angle > 360f) angle = 0f;
        if (angle < 0) angle = 360f;

        elapsed += amt;
    }

    public float getCurrentAlpha() {
        if (elapsed < SPAWN_IN_SEC) return baseAlpha * (elapsed / SPAWN_IN_SEC);
        else return baseAlpha * Math.max(0, MiscIE.smootherstep(1, 0, elapsed / duration));
    }

    public boolean isExpired() {
        return elapsed > duration;
    }
}
