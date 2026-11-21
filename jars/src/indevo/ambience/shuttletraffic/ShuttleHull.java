package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

// one pool entry: role flags and wing size per entry, the look shared by every entry of the hull
public class ShuttleHull {

    public static final float FIGHTER_SCALE = 0.18f;
    public static final float FRIGATE_SCALE = 0.075f;
    // drawn sizes are for looks, not to scale: the longer side is never below this
    public static final float MIN_SIZE = 5f;
    public static final float FIGHTER_SPEED = 30f;
    public static final float FRIGATE_SPEED = 24f;

    public record Look(String hullId, SpriteAPI sprite, Color glowColor, Color trailColor, boolean additiveTrail) {
    }

    public final Look look;
    public final String hullId;
    public final SpriteAPI sprite;
    public final float width;
    public final float length;
    public final boolean fighter;
    public final boolean civilian;
    public final boolean small;
    public final boolean liner;
    public final boolean freighter;
    public final int wingSize;
    public final Color glowColor;
    public final Color trailColor;
    public final boolean additiveTrail;

    public ShuttleHull(Look look, boolean fighter, boolean civilian, boolean small, boolean liner, boolean freighter, int wingSize) {
        this(look, fighter, civilian, small, liner, freighter, wingSize,
                look.sprite().getWidth() * drawScale(look.sprite(), fighter), look.sprite().getHeight() * drawScale(look.sprite(), fighter));
    }

    public ShuttleHull(Look look, boolean fighter, boolean civilian, boolean small, boolean liner, boolean freighter, int wingSize, float width, float length) {
        this.look = look;
        this.hullId = look.hullId();
        this.sprite = look.sprite();
        this.glowColor = look.glowColor();
        this.trailColor = look.trailColor();
        this.additiveTrail = look.additiveTrail();
        this.fighter = fighter;
        this.civilian = civilian;
        this.small = small;
        this.liner = liner;
        this.freighter = freighter;
        this.wingSize = wingSize;
        this.width = width;
        this.length = length;
    }

    public float getCruiseSpeed() {
        return fighter ? FIGHTER_SPEED : FRIGATE_SPEED;
    }

    public static float drawScale(SpriteAPI sprite, boolean fighter) {
        float scale = fighter ? FIGHTER_SCALE : FRIGATE_SCALE;
        float longer = Math.max(sprite.getWidth(), sprite.getHeight()) * scale;
        return longer < MIN_SIZE ? scale * MIN_SIZE / Math.max(0.01f, longer) : scale;
    }

    public ShuttleHull withLook(Look newLook) {
        return newLook == look ? this : new ShuttleHull(newLook, fighter, civilian, small, liner, freighter, wingSize, width, length);
    }

    public ShuttleHull withWingSize(int size) {
        return size == wingSize ? this : new ShuttleHull(look, fighter, civilian, small, liner, freighter, size, width, length);
    }
}
