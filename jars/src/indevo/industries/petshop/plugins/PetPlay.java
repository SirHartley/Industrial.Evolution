package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiDraw;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// the Play button's effect: the portrait pops out of the card, moves and goes back while stars fall off the screen; never saved
// maths from the approved board pets/mockups/play: offsets y down, angles clockwise, u = t / time
final class PetPlay {

    enum Kind {
        BOUNCE(1.25f, 1.1f, 360f, 34f, 0.1f, 0.15f),
        TOSS(1.45f, 0.9f, 360f, 28f, 0.14f, 0.22f),
        ROCK(1.3f, 1f, 24f, 8f, 0.12f, 0.2f);

        final float pop;
        final float time;
        final float turn;
        final float drift;
        final float cutIn;
        final float cutOut;

        Kind(float pop, float time, float turn, float drift, float cutIn, float cutOut) {
            this.pop = pop;
            this.time = time;
            this.turn = turn;
            this.drift = drift;
            this.cutIn = cutIn;
            this.cutOut = cutOut;
        }
    }

    static final int STARS = 10;
    static final float SPEED = 700f;
    static final float GRAVITY = 1800f;
    static final float SETTLE = 0.1f;
    static final float SETTLE_DIP = 0.06f;
    static final float SHADOW_ALPHA = 0.5f;
    static final float SHADOW_X = 5f;
    static final float SHADOW_Y = 6f;
    // start, end and height of each hop over u
    private static final float[][] HOPS = {{0f, 0.42f, 1f}, {0.42f, 0.74f, 0.5f}, {0.74f, 1f, 0.22f}};
    private static final float[] BOUNCE_AT = {0.02f, 0.42f, 0.74f, 1f};

    private final Kind kind;
    private final float start;
    private final List<Star> stars = new ArrayList<>();

    private record Pose(float x, float y, float scale, float angle, float lift) {
    }

    private record Star(float te, float x, float y, float vx, float vy, float size, float spin, float rot) {
    }

    PetPlay(float clock, Random random) {
        Kind[] kinds = Kind.values();
        kind = kinds[random.nextInt(kinds.length)];
        start = clock;
        makeStars(random);
    }

    // the card leaves its own portrait out while this one is drawn
    boolean holdsPortrait(float clock) {
        float t = clock - start;
        return t >= 0f && t < kind.time + SETTLE;
    }

    // cx, cy: the portrait centre in screen pixels, y up
    void paint(SpriteAPI sprite, float cx, float cy, float size, float clock, float alpha) {
        float t = clock - start;
        if (holdsPortrait(clock)) paintPortrait(sprite, pose(t), cx, cy, size, alpha);

        Color c = Ui.hl();
        for (Star s : stars) {
            float q = t - s.te;
            if (q < 0f) continue;
            float x = cx + s.x + s.vx * q;
            float y = cy - (s.y + s.vy * q + GRAVITY / 2f * q * q);
            if (y < -s.size) continue;
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0f);
            GL11.glRotatef(-(s.rot + s.spin * q), 0f, 0f, 1f);
            UiDraw.fan(-s.size / 2f, -s.size / 2f, s.size, 20f, PetCard.STAR, 10f, 10f, c, alpha);
            GL11.glPopMatrix();
        }
    }

    private static void paintPortrait(SpriteAPI sprite, Pose p, float cx, float cy, float size, float alpha) {
        float s = Math.round(size * p.scale);
        sprite.setSize(s, s);
        sprite.setAngle(-p.angle);
        sprite.setNormalBlend();
        if (p.lift > 0f) {
            // the picture once more in black, down and right, as its shadow
            sprite.setColor(Color.BLACK);
            sprite.setAlphaMult(alpha * SHADOW_ALPHA * p.lift);
            sprite.renderAtCenter(cx + p.x + SHADOW_X * p.lift, cy - p.y - SHADOW_Y * p.lift);
        }
        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(alpha);
        sprite.renderAtCenter(Math.round(cx + p.x), Math.round(cy - p.y));
        sprite.setAngle(0f);
    }

    // offset from its place, scale, angle in degrees clockwise
    private Pose pose(float t) {
        if (t >= kind.time) {
            float s = (t - kind.time) / SETTLE;
            return new Pose(0f, 0f, s < 1f ? 1f - SETTLE_DIP * (float) Math.sin(Math.PI * s) : 1f, 0f, 0f);
        }

        float u = Math.max(0f, t / kind.time);
        float e = out(u);
        float scale = 1f + (kind.pop - 1f) * e;
        float lift = Math.max(0f, Math.min(1f, e));
        float d = kind.drift;
        return switch (kind) {
            case TOSS -> new Pose(d * sin(Math.PI * u), -0.55f * d * sin(2 * Math.PI * u), scale, kind.turn * smooth(u), lift);
            case ROCK -> {
                float swing = sin(2 * Math.PI * 2.5 * u) * (1f - u);
                yield new Pose(d * swing, -0.8f * d * lift, scale, kind.turn * swing, lift);
            }
            case BOUNCE -> {
                float y = 0f;
                for (float[] hop : HOPS) {
                    if (u < hop[0] || u >= hop[1]) continue;
                    float q = (u - hop[0]) / (hop[1] - hop[0]);
                    y = -1.3f * d * hop[2] * 4f * q * (1f - q);
                }
                yield new Pose(0.5f * d * sin(Math.PI * u), y, scale, kind.turn * smooth(u), lift);
            }
        };
    }

    // 0 at rest, 1 fully out, a small overshoot on the way up
    private float out(float u) {
        if (u <= 0f || u >= 1f) return 0f;
        if (u < kind.cutIn) return outBack(u / kind.cutIn);
        if (u > 1f - kind.cutOut) return 1f - smooth((u - (1f - kind.cutOut)) / kind.cutOut);
        return 1f;
    }

    private void makeStars(Random r) {
        float half = PetCard.PORTRAIT / 2f;
        for (int i = 0; i < STARS; i++) {
            switch (kind) {
                case TOSS -> {
                    // one after the other off the corners while it turns, up and to the sides
                    float te = kind.time * (0.1f + 0.62f * i / (STARS - 1f));
                    Pose p = pose(te);
                    double corner = Math.toRadians(p.angle + 45f + 90f * (i % 4));
                    float d = half * p.scale * 0.9f;
                    add(r, te, p.x + (float) Math.cos(corner) * d, p.y + (float) Math.sin(corner) * d, -165f + 150f * r.nextFloat());
                }
                case ROCK -> {
                    // at the end of each swing, to the side it swings to
                    int k = i % 5;
                    float u = 0.1f + 0.2f * k;
                    float te = kind.time * u + 0.03f * r.nextFloat();
                    Pose p = pose(kind.time * u);
                    float side = k % 2 == 0 ? 1f : -1f;
                    add(r, te, p.x + side * half * p.scale * 0.8f, p.y - half * p.scale * 0.8f, -90f + side * (20f + 55f * r.nextFloat()));
                }
                case BOUNCE -> {
                    // at the jump and at each landing, up from the lower edge
                    float u = BOUNCE_AT[i % BOUNCE_AT.length];
                    float te = kind.time * u + 0.03f * r.nextFloat();
                    Pose p = pose(Math.min(kind.time * u, kind.time - 0.001f));
                    add(r, te, p.x + (r.nextFloat() - 0.5f) * PetCard.PORTRAIT, p.y + half * 0.8f, -90f + (r.nextFloat() - 0.5f) * 140f);
                }
            }
        }
    }

    private void add(Random r, float te, float x, float y, float deg) {
        float sp = SPEED * (0.55f + 0.65f * r.nextFloat());
        double a = Math.toRadians(deg);
        float size = 8f + Math.round(r.nextFloat() * 8f);
        float spin = (r.nextFloat() < 0.5f ? -1f : 1f) * (360f + 540f * r.nextFloat());
        stars.add(new Star(te, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, size, spin, r.nextFloat() * 360f));
    }

    private static float sin(double v) {
        return (float) Math.sin(v);
    }

    private static float smooth(float u) {
        return u * u * (3f - 2f * u);
    }

    private static float outBack(float v) {
        float c = 1.70158f;
        float w = v - 1f;
        return 1f + (c + 1f) * w * w * w + c * w * w;
    }
}
