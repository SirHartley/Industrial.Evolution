package indevo.items.consumables.particles;

import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.items.consumables.entities.SmokeCloudEntityPlugin.mixColors;

public class NebulaParticle {
    public float size, angle, baseAlpha, elapsed, duration;
    public int i, j;
    public Vector2f pos;
    private final Color originalColor;
    private final Color targetColor;
    public Color color;
    public float angleRotation;

    public static final float SPAWN_IN_SEC = 0.5f;

    public NebulaParticle(float size, float angle, float alpha, float duration, Color originalColor, Color targetColor, Vector2f pos) {
        this.size = size;
        this.angle = angle;
        this.baseAlpha = alpha;
        this.originalColor = originalColor;
        this.targetColor = targetColor;
        this.duration = duration;
        this.pos = pos;
        this.i = Misc.random.nextInt(4);
        this.j = Misc.random.nextInt(4);

        angleRotation = 5 * Misc.random.nextFloat();
        if(Misc.random.nextBoolean()) angleRotation *= -1;

        advance(0);
    }

    public void advance(float amt) {
        float timePassedMult = Math.min(elapsed / duration, 1);
        float smoothedDur = MiscIE.smootherstep(0, 1, timePassedMult);
        this.color = mixColors(originalColor, targetColor, smoothedDur);

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
