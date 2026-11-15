package indevo.ambience.shuttletraffic.dogfights;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

// one battle's shots in a fixed ring buffer; each moves every frame and tests its swept segment against enemy fighters and fleets
public class ShuttleProjectiles {

    public enum Kind {
        GUN(500f, 0.6f, 6f),
        LASER(900f, 0.4f, 6f),
        TORPEDO(200f, 2f, 24f);

        public final float speed;
        public final float life;
        public final float length;

        Kind(float speed, float life, float length) {
            this.speed = speed;
            this.life = life;
            this.length = length;
        }
    }

    public static final int SIZE = 256;
    public static final float FLEET_HIT_SHARE = 0.7f;
    public static final Color GUN_COLOR = new Color(255, 235, 160);
    public static final Color TORPEDO_COLOR = new Color(255, 200, 120);
    private static final float FADE_TIME = 0.1f;

    private final float[] x = new float[SIZE];
    private final float[] y = new float[SIZE];
    private final float[] vx = new float[SIZE];
    private final float[] vy = new float[SIZE];
    private final float[] age = new float[SIZE];
    private final int[] side = new int[SIZE];
    private final Kind[] kind = new Kind[SIZE];
    private final Color[] color = new Color[SIZE];
    private final boolean[] live = new boolean[SIZE];
    private final boolean[] atFleet = new boolean[SIZE];
    private int next = 0;
    private int liveCount = 0;

    // check program counters, per firing side; a stray shot at a fighter that hits a fleet counts for neither
    final int[] firedAtFighters = new int[2];
    final int[] firedAtFleets = new int[2];
    final int[] hitFighters = new int[2];
    final int[] hitFleets = new int[2];

    // a full buffer overwrites the oldest shot
    public void fire(Kind type, int from, float px, float py, float angle, float baseVx, float baseVy, Color tint, boolean atFleet) {
        int i = next;
        next = (next + 1) % SIZE;
        if (!live[i]) liveCount++;

        double rad = Math.toRadians(angle);
        x[i] = px;
        y[i] = py;
        vx[i] = (float) Math.cos(rad) * type.speed + baseVx;
        vy[i] = (float) Math.sin(rad) * type.speed + baseVy;
        age[i] = 0f;
        side[i] = from;
        kind[i] = type;
        color[i] = tint;
        live[i] = true;
        this.atFleet[i] = atFleet;
        if (atFleet) firedAtFleets[from]++;
        else firedAtFighters[from]++;
    }

    public void advance(float amount, ShuttleDogfight fight) {
        if (liveCount == 0) return;

        List<Dogfighter> fighters = fight.getFighters();
        for (int i = 0; i < SIZE; i++) {
            if (!live[i]) continue;

            age[i] += amount;
            if (age[i] >= kind[i].life) {
                remove(i);
                continue;
            }

            float x0 = x[i];
            float y0 = y[i];
            float dx = vx[i] * amount;
            float dy = vy[i] * amount;
            x[i] += dx;
            y[i] += dy;

            if (kind[i] != Kind.TORPEDO) {
                Dogfighter hit = hitFighter(fighters, side[i], x0, y0, dx, dy);
                if (hit != null) {
                    hitFighters[side[i]]++;
                    fight.onFighterHit(hit, x[i], y[i], color[i]);
                    remove(i);
                    continue;
                }
            }

            DogfightFleet fleet = hitFleet(fight.getSide(1 - side[i]), x[i], y[i]);
            if (fleet != null) {
                if (atFleet[i]) hitFleets[side[i]]++;
                fight.onFleetHit(fleet, x[i], y[i], kind[i], color[i]);
                remove(i);
            }
        }
    }

    public int getFired(int from, boolean atFleets) {
        return atFleets ? firedAtFleets[from] : firedAtFighters[from];
    }

    public int getHits(int from, boolean onFleets) {
        return onFleets ? hitFleets[from] : hitFighters[from];
    }

    public boolean isEmpty() {
        return liveCount == 0;
    }

    // one GL_LINES batch: a bright head fading to the tail, the last 0.1 s fading out
    public void render(float alphaMult) {
        if (liveCount == 0 || alphaMult <= 0.01f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < SIZE; i++) {
            if (!live[i]) continue;

            Kind type = kind[i];
            float speed = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
            if (speed < 0.01f) continue;

            float fade = Math.min(1f, (type.life - age[i]) / FADE_TIME) * alphaMult;
            float length = Math.min(type.length, speed * age[i] + 2f);
            Color c = color[i];
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, fade);
            GL11.glVertex2f(x[i], y[i]);
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0f);
            GL11.glVertex2f(x[i] - vx[i] / speed * length, y[i] - vy[i] / speed * length);
        }
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private void remove(int i) {
        live[i] = false;
        color[i] = null;
        liveCount--;
    }

    // closest point of the shot's segment this frame to each enemy fighter
    private static Dogfighter hitFighter(List<Dogfighter> fighters, int from, float x0, float y0, float dx, float dy) {
        float len2 = dx * dx + dy * dy;
        for (Dogfighter fighter : fighters) {
            if (fighter.side == from || !fighter.canBeHit()) continue;

            float rx = fighter.pos.x - x0;
            float ry = fighter.pos.y - y0;
            float t = len2 > 0f ? Math.max(0f, Math.min(1f, (rx * dx + ry * dy) / len2)) : 0f;
            float cx = rx - dx * t;
            float cy = ry - dy * t;
            float radius = fighter.getHitRadius();
            if (cx * cx + cy * cy <= radius * radius) return fighter;
        }
        return null;
    }

    private static DogfightFleet hitFleet(List<DogfightFleet> fleets, float px, float py) {
        for (DogfightFleet fleet : fleets) {
            if (!fleet.alive) continue;

            float rx = fleet.loc.x - px;
            float ry = fleet.loc.y - py;
            float radius = fleet.radius * FLEET_HIT_SHARE;
            if (rx * rx + ry * ry <= radius * radius) return fleet;
        }
        return null;
    }
}
