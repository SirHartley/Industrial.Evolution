package indevo.items.consumables.particles;

import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entities.SmokeCloudEntityPlugin;
import indevo.utils.helper.MiscIE;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class NebulaParticle {
    public float size, angle, alpha, elapsed, duration;
    public int i, j;
    public Vector2f pos;
    private final Color originalColor;
    private final Color targetColor;
    public Color color;

    public NebulaParticle(float size, float angle, float alpha, float duration, Color originalColor, Color targetColor, Vector2f pos) {
        this.size = size;
        this.angle = angle;
        this.alpha = alpha;
        this.originalColor = originalColor;
        this.targetColor = targetColor;
        this.duration = duration;
        this.pos = pos;
        this.i = Misc.random.nextInt(4);
        this.j = Misc.random.nextInt(4);
    }

    public void advance(float amt) {
        float timePassedMult = Math.min(elapsed / duration, 1);
        float smoothedDur = MiscIE.smootherstep(0, 1, timePassedMult);

        int rDiff = originalColor.getRed() - targetColor.getRed();
        int gDiff = originalColor.getGreen() - targetColor.getGreen();
        int bDiff = originalColor.getBlue() - targetColor.getBlue();

        int rMix = Math.round(originalColor.getRed() + rDiff * smoothedDur);
        int gMix = Math.round(originalColor.getGreen() + gDiff * smoothedDur);
        int bMix = Math.round(originalColor.getBlue() + bDiff * smoothedDur);

        this.color = new Color(rMix, gMix, bMix, 255);

        elapsed += amt;
        if (elapsed > duration * SmokeCloudEntityPlugin.FULL_EFFECT_FRACT) advanceAlpha();
    }

    public void advanceAlpha() {
        float remainingAlphaDur = duration - duration * SmokeCloudEntityPlugin.FULL_EFFECT_FRACT;
        float correctedDur = elapsed - duration * SmokeCloudEntityPlugin.FULL_EFFECT_FRACT;
        alpha = Math.max(0, MiscIE.smootherstep(1, 0, correctedDur / remainingAlphaDur));
    }

    public boolean isExpired() {
        return elapsed > duration;
    }

    public SpriteAPI getScaledSprite(SpriteAPI sprite) {
        sprite.setTexWidth(0.25f);
        sprite.setTexHeight(0.25f);
        sprite.setAdditiveBlend();

        sprite.setTexX(i * 0.25f);
        sprite.setTexY(j * 0.25f);

        sprite.setAngle(angle);
        sprite.setSize(size, size);
        sprite.setAlphaMult(alpha);
        sprite.setColor(color);
        return sprite;
    }
}
