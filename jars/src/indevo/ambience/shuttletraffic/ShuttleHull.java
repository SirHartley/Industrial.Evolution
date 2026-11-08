package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

public class ShuttleHull {

    public static final float FIGHTER_SCALE = 0.13f;
    public static final float FRIGATE_SCALE = 0.075f;
    public static final float FIGHTER_SPEED = 30f;
    public static final float FRIGATE_SPEED = 24f;

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

    public ShuttleHull(String hullId, SpriteAPI sprite, boolean fighter, boolean civilian, boolean small, boolean liner, boolean freighter,
                       int wingSize, Color glowColor, Color trailColor, boolean additiveTrail) {
        this(hullId, sprite, fighter, civilian, small, liner, freighter, wingSize, glowColor, trailColor, additiveTrail,
                sprite.getWidth() * (fighter ? FIGHTER_SCALE : FRIGATE_SCALE), sprite.getHeight() * (fighter ? FIGHTER_SCALE : FRIGATE_SCALE));
    }

    public ShuttleHull(String hullId, SpriteAPI sprite, boolean fighter, boolean civilian, boolean small, boolean liner, boolean freighter,
                       int wingSize, Color glowColor, Color trailColor, boolean additiveTrail, float width, float length) {
        this.hullId = hullId;
        this.sprite = sprite;
        this.fighter = fighter;
        this.civilian = civilian;
        this.small = small;
        this.liner = liner;
        this.freighter = freighter;
        this.wingSize = wingSize;
        this.glowColor = glowColor;
        this.trailColor = trailColor;
        this.additiveTrail = additiveTrail;
        this.width = width;
        this.length = length;
    }

    public float getCruiseSpeed() {
        return fighter ? FIGHTER_SPEED : FRIGATE_SPEED;
    }
}
