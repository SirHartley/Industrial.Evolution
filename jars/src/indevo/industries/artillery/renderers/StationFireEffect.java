package indevo.industries.artillery.renderers;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.config.Setting;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class StationFireEffect {

    private enum Profile {
        MORTAR(new Color(255, 150, 70), new Color(122, 110, 100), 1f, 0.5f, Setting.ARTILLERY_MORTAR_PROJECTILES_PER_SHOT),
        RAILGUN(new Color(150, 215, 255), new Color(170, 200, 222), 1f, 1f, Setting.ARTILLERY_RAILGUN_PROJECTILES_PER_SHOT),
        MISSILE(new Color(255, 200, 95), new Color(158, 158, 164), 0.6f, 0.3f, Setting.ARTILLERY_MISSILE_PROJECTILES_PER_SHOT);

        final Color hot;
        final Color smoke;
        final float maxHeat;
        final float windup;
        final Setting shots;

        Profile(Color hot, Color smoke, float maxHeat, float windup, Setting shots) {
            this.hot = hot;
            this.smoke = smoke;
            this.maxHeat = maxHeat;
            this.windup = windup;
            this.shots = shots;
        }

        static Profile of(String type) {
            if (ArtilleryStationEntityPlugin.TYPE_RAILGUN.equals(type)) return RAILGUN;
            if (ArtilleryStationEntityPlugin.TYPE_MISSILE.equals(type)) return MISSILE;
            return MORTAR;
        }
    }

    public static final float MUZZLE_DIST = 23f;
    public static final float TUBE_DIST = 16f;
    public static final float REAR_PORT_DIST = 21f;
    public static final float SIDE_PORT_DIST = 20f;
    public static final float SIDE_PORT_ANGLE = 116f;
    public static final float NOZZLE_DIST = 22f;
    public static final float NOZZLE_OFFSET = 6f;
    public static final float NOZZLE_SPLAY = 6f;
    public static final float RIM_RADIUS = 25f;
    public static final float HEAT_GLOW_SIZE = 110f;
    public static final float MAX_HEAT_ALPHA = 0.6f;
    public static final float MAX_RIM_ALPHA = 0.5f;
    public static final float GLOW_CORE_FRACTION = 0.45f;

    public static final float FLASH_SECONDS = 0.13f;
    public static final float STAR_LENGTH = 98f;
    public static final float STAR_WIDTH = 5.5f;
    public static final float STAR_CORE_LENGTH = 58f;
    public static final float STAR_CORE_WIDTH = 2.2f;
    public static final float STAR_SIDE_ANGLE = 54f;
    public static final float STAR_SIDE_LENGTH = 38f;
    public static final float STAR_SIDE_WIDTH = 3.2f;
    public static final float STAR_CROSS_LENGTH = 17f;
    public static final float STAR_CROSS_WIDTH = 1.1f;
    public static final float STAR_LIGHT_SIZE = 144f;
    public static final float LANCE_LENGTH = 95f;
    public static final float HOT_FRACTION = 0.18f;
    public static final float CHARGE_FADE_PER_SECOND = 6f;
    public static final float PENDING_SECONDS = 0.3f;

    public static final float VENT_DELAY = 0.45f;
    public static final float COOL_FRACTION_OF_RELOAD = 0.6f;
    public static final float MIN_COOL_SECONDS = 4f;
    public static final int PULSES = 5;
    public static final float PULSE_SPAN = 0.85f;
    public static final float PULSE_DROP_SECONDS = 0.3f;
    public static final float PULSE_GLOW_SECONDS = 0.25f;
    public static final float SLOW_COOL_SHARE = 0.35f;
    public static final float JET_SECONDS = 2f;
    public static final float JET_COOL_SHARE = 0.75f;
    public static final float JETS_PER_SECOND = 150f;
    public static final float RING_PER_SECOND = 46f;
    public static final float RING_TAIL_SECONDS = 0.4f;
    public static final float RING_SWIRL = 46f;
    public static final float MAX_EMITS_PER_FRAME = 20f;

    private static final Color STEAM = new Color(215, 226, 236);
    private static final Color JET = new Color(190, 220, 240);
    private static final Color HEAT = new Color(255, 120, 40);
    private static final Color RIM = new Color(255, 150, 60);
    private static final Color PORT_GLOW = new Color(255, 190, 120);
    private static final Color FLASH_CORE = new Color(255, 245, 225);
    private static final Color LANCE = new Color(235, 248, 255);
    private static final int RIM_SEGMENTS = 40;

    private final SectorEntityToken station;
    private final Profile profile;
    private final Random random = new Random();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Flash> flashes = new ArrayList<>();

    private float direction;
    private float charge;
    private float chargeAngle;
    private float pending;

    private float heat;
    private float sinceShot;
    private boolean venting;
    private float ventTime;
    private int pulses;
    private float sincePulse = 10f;
    private float flow;
    private float emit;

    private static class Particle {
        float x, y, vx, vy;
        float age, life;
        float startSize, endSize;
        float alpha, drag, hot;
        float angle, spin;
        int cellX, cellY;
        boolean spark;
        Color color;
    }

    private static class Flash {
        float x, y, angle, age;
    }

    public StationFireEffect(SectorEntityToken station, String type) {
        this.station = station;
        this.profile = Profile.of(type);
    }

    public SectorEntityToken getStation() {
        return station;
    }

    public boolean isIdle() {
        return particles.isEmpty() && flashes.isEmpty() && heat <= 0f && charge <= 0f && pending <= 0f;
    }

    public void incoming(float angle, float secondsToShot) {
        pending = PENDING_SECONDS;
        if (secondsToShot > profile.windup) return;

        float level = 1f - Math.max(0f, secondsToShot) / profile.windup;
        if (level < charge) return;

        charge = level;
        chargeAngle = angle;
    }

    public void shot(float angle) {
        heat = Math.min(profile.maxHeat, getHeat() + profile.maxHeat / Math.max(1, profile.shots.intValue()));
        venting = false;
        sinceShot = 0f;
        flow = 0f;
        charge = 0f;
        direction = angle;

        Flash flash = new Flash();
        Vector2f at = port(angle, profile == Profile.MISSILE ? TUBE_DIST : MUZZLE_DIST);
        flash.x = at.x;
        flash.y = at.y;
        flash.angle = angle;
        flashes.add(flash);

        Vector2f rear = port(angle + 180f, REAR_PORT_DIST);

        switch (profile) {
            case MORTAR:
                burst(7, rear, angle + 180f, 29f, 50f, 175f, 1.6f, 3f, 5f, 20f, profile.smoke, 0.5f, 2.2f, 0.9f);
                for (int side = -1; side <= 1; side += 2) {
                    float out = angle + side * SIDE_PORT_ANGLE;
                    burst(2, port(angle + side * 90f, SIDE_PORT_DIST), out, 17f, 30f, 95f, 1f, 2f, 3f, 12f, profile.smoke, 0.4f, 2.4f, 0.5f);
                }
                break;
            case RAILGUN:
                burst(7, rear, angle + 180f, 17f, 90f, 230f, 1f, 2.1f, 3f, 15f, profile.smoke, 0.38f, 2.6f, 0.6f);
                for (int side = -1; side <= 1; side += 2) {
                    float out = angle + side * (SIDE_PORT_ANGLE - 9f);
                    burst(4, port(angle + side * 90f, SIDE_PORT_DIST), out, 11f, 80f, 170f, 0.5f, 1f, 2f, 10f, profile.smoke, 0.3f, 3f, 0.5f);
                }
                sparks(12, at, angle, 52f);
                break;
            case MISSILE:
                burst(8, port(angle, TUBE_DIST - 6f), angle + 180f, 26f, 40f, 125f, 1.2f, 2.4f, 4f, 16f, profile.smoke, 0.45f, 2.4f, 0.8f);
                burst(6, at, angle, 7f, 60f, 210f, 1.3f, 2.3f, 3f, 11f, profile.smoke, 0.38f, 4f, 0.7f);
                break;
        }
    }

    public void advance(float amount) {
        charge = Math.max(0f, charge - amount * CHARGE_FADE_PER_SECOND);
        pending = Math.max(0f, pending - amount);
        sincePulse += amount;

        advanceVent(amount);

        Iterator<Flash> flashIterator = flashes.iterator();
        while (flashIterator.hasNext()) {
            Flash flash = flashIterator.next();
            flash.age += amount;
            if (flash.age >= FLASH_SECONDS) flashIterator.remove();
        }

        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            p.age += amount;
            if (p.age < 0f) continue;
            if (p.age >= p.life) {
                iterator.remove();
                continue;
            }

            float keep = (float) Math.exp(-p.drag * amount);
            p.vx *= keep;
            p.vy *= keep;
            p.x += p.vx * amount;
            p.y += p.vy * amount;
            p.angle += p.spin * amount;
        }
    }

    // the vent starts once the last shot of the volley is out, a new shot stops it
    private void advanceVent(float amount) {
        if (heat <= 0f) return;

        sinceShot += amount;

        if (!venting) {
            if (sinceShot < VENT_DELAY || pending > 0f) return;
            venting = true;
            ventTime = 0f;
            pulses = 0;
            emit = 0f;
        } else ventTime += amount;

        float cool = getCoolSeconds();
        if (ventTime >= cool) {
            heat = 0f;
            venting = false;
            flow = 0f;
            return;
        }

        float rate = 0f;
        switch (profile) {
            case MORTAR:
                while (pulses < PULSES && ventTime >= pulses * getPulseGap(cool)) {
                    pulse();
                    pulses++;
                }
                break;
            case RAILGUN:
                float jet = ventTime / JET_SECONDS;
                flow = jet < 1f ? 1f - jet * jet : 0f;
                rate = JETS_PER_SECOND;
                break;
            case MISSILE:
                float ring = ventTime / (cool - RING_TAIL_SECONDS);
                flow = ring < 1f ? 1f - 0.6f * ring : 0f;
                rate = RING_PER_SECOND;
                break;
        }

        if (flow <= 0f) return;

        emit = Math.min(MAX_EMITS_PER_FRAME, emit + rate * flow * amount);
        while (emit >= 1f) {
            emit--;
            if (profile == Profile.RAILGUN) jets();
            else ring();
        }
    }

    private void pulse() {
        sincePulse = 0f;
        burst(6, port(direction + 180f, REAR_PORT_DIST), direction + 180f, 14f, 50f, 115f, 0.5f, 0.9f, 2f, 10f, STEAM, 0.4f, 3f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            burst(6, port(direction + side * 90f, SIDE_PORT_DIST), direction + side * SIDE_PORT_ANGLE, 14f, 50f, 115f, 0.5f, 0.9f, 2f, 10f, STEAM, 0.4f, 3f, 0f);
        }
    }

    private void jets() {
        for (int side = -1; side <= 1; side += 2) {
            burst(1, nozzle(side), direction + 180f - side * NOZZLE_SPLAY, 2f, 190f, 250f, 0.32f, 0.5f, 2.4f, 6.5f, JET, 0.42f, 1f, 0.6f);
        }
    }

    private void ring() {
        float angle = random.nextFloat() * 360f;
        burst(1, port(angle, MUZZLE_DIST), angle + RING_SWIRL, 9f, 7f, 17f, 1.8f, 2.8f, 3.5f, 11f, STEAM, 0.34f, 0.5f, 0f);
    }

    private float getHeat() {
        if (!venting) return heat;

        float cool = getCoolSeconds();
        float share = ventTime / cool;
        float left = 1f - share;

        if (profile == Profile.MORTAR) {
            left = 1f - SLOW_COOL_SHARE * share;
            float drop = (1f - SLOW_COOL_SHARE) / PULSES;
            for (int i = 0; i < PULSES; i++) {
                float since = ventTime - i * getPulseGap(cool);
                if (since > 0f) left -= drop * Math.min(1f, since / PULSE_DROP_SECONDS);
            }
        } else if (profile == Profile.RAILGUN) {
            left = 1f - (1f - JET_COOL_SHARE) * share - JET_COOL_SHARE * Math.min(1f, ventTime / JET_SECONDS);
        }

        return heat * Math.max(0f, left);
    }

    private float getCoolSeconds() {
        return Math.max(MIN_COOL_SECONDS, Setting.ARTILLERY_MIN_COOLDOWN.value() * COOL_FRACTION_OF_RELOAD);
    }

    private float getPulseGap(float cool) {
        return cool * PULSE_SPAN / PULSES;
    }

    private Vector2f port(float angle, float dist) {
        double rad = Math.toRadians(angle);
        Vector2f loc = station.getLocation();
        return new Vector2f(loc.x + (float) Math.cos(rad) * dist, loc.y + (float) Math.sin(rad) * dist);
    }

    private Vector2f nozzle(int side) {
        Vector2f rear = port(direction + 180f, NOZZLE_DIST);
        double rad = Math.toRadians(direction + side * 90f);
        rear.x += (float) Math.cos(rad) * NOZZLE_OFFSET;
        rear.y += (float) Math.sin(rad) * NOZZLE_OFFSET;
        return rear;
    }

    private void burst(int count, Vector2f at, float angle, float spread, float minSpeed, float maxSpeed, float minLife, float maxLife,
                       float startSize, float endSize, Color color, float alpha, float drag, float hot) {
        for (int i = 0; i < count; i++) {
            double rad = Math.toRadians(angle + (random.nextFloat() * 2f - 1f) * spread);
            float speed = minSpeed + random.nextFloat() * (maxSpeed - minSpeed);

            Particle p = new Particle();
            p.x = at.x + (random.nextFloat() - 0.5f) * 4f;
            p.y = at.y + (random.nextFloat() - 0.5f) * 4f;
            p.vx = (float) Math.cos(rad) * speed;
            p.vy = (float) Math.sin(rad) * speed;
            p.age = -random.nextFloat() * 0.12f;
            p.life = minLife + random.nextFloat() * (maxLife - minLife);
            p.startSize = startSize;
            p.endSize = endSize * (0.75f + random.nextFloat() * 0.5f);
            p.color = color;
            p.alpha = alpha;
            p.drag = drag;
            p.hot = hot;
            p.angle = random.nextFloat() * 360f;
            p.spin = (random.nextFloat() - 0.5f) * 40f;
            p.cellX = random.nextInt(4);
            p.cellY = random.nextInt(4);
            particles.add(p);
        }
    }

    private void sparks(int count, Vector2f at, float angle, float spread) {
        for (int i = 0; i < count; i++) {
            double rad = Math.toRadians(angle + (random.nextFloat() * 2f - 1f) * spread);
            float speed = 110f + random.nextFloat() * 190f;

            Particle p = new Particle();
            p.x = at.x;
            p.y = at.y;
            p.vx = (float) Math.cos(rad) * speed;
            p.vy = (float) Math.sin(rad) * speed;
            p.life = 0.15f + random.nextFloat() * 0.25f;
            p.drag = 2.5f;
            p.spark = true;
            particles.add(p);
        }
    }

    public void render(float alphaMult, SpriteAPI smoke, SpriteAPI glow, SpriteAPI line) {
        renderSmoke(alphaMult, smoke);

        glow.setAdditiveBlend();
        line.setAdditiveBlend();

        renderHotParticles(alphaMult, glow, line);

        Vector2f loc = station.getLocation();
        float current = getHeat();

        if (charge > 0f) {
            Vector2f at = port(chargeAngle, profile == Profile.MISSILE ? TUBE_DIST : MUZZLE_DIST);
            float size = (4f + 9f * charge) * 3f;
            glow(glow, at.x, at.y, size, profile.hot, alphaMult * (0.35f + 0.5f * charge));
        }

        if (current > 0f) {
            glow(glow, loc.x, loc.y, HEAT_GLOW_SIZE, HEAT, alphaMult * MAX_HEAT_ALPHA * current);
            renderRim(loc, alphaMult * MAX_RIM_ALPHA * current);
        }

        if (profile == Profile.MORTAR && sincePulse < PULSE_GLOW_SECONDS && current > 0f) {
            float fade = alphaMult * 0.7f * (1f - sincePulse / PULSE_GLOW_SECONDS);
            Vector2f rear = port(direction + 180f, REAR_PORT_DIST);
            glow(glow, rear.x, rear.y, 18f, PORT_GLOW, fade);
            for (int side = -1; side <= 1; side += 2) {
                Vector2f at = port(direction + side * 90f, SIDE_PORT_DIST);
                glow(glow, at.x, at.y, 18f, PORT_GLOW, fade);
            }
        }

        if (profile == Profile.RAILGUN && flow > 0f) {
            for (int side = -1; side <= 1; side += 2) {
                Vector2f at = nozzle(side);
                glow(glow, at.x, at.y, 15f, profile.hot, alphaMult * 0.7f * flow);
            }
        }

        for (Flash flash : flashes) renderFlash(flash, alphaMult, glow, line);
    }

    private void renderSmoke(float alphaMult, SpriteAPI smoke) {
        smoke.setNormalBlend();
        smoke.setTexWidth(0.25f);
        smoke.setTexHeight(0.25f);

        for (Particle p : particles) {
            if (p.spark || p.age < 0f) continue;

            float done = p.age / p.life;
            float eased = 1f - (1f - done) * (1f - done);
            float size = 2f * (p.startSize + (p.endSize - p.startSize) * eased);
            float alpha = p.alpha * Math.min(1f, done * 9f) * (float) Math.pow(1f - done, 1.4f);

            smoke.setTexX(p.cellX * 0.25f);
            smoke.setTexY(p.cellY * 0.25f);
            smoke.setAngle(p.angle);
            smoke.setSize(size, size);
            smoke.setColor(p.color);
            smoke.setAlphaMult(alphaMult * alpha);
            smoke.renderAtCenter(p.x, p.y);
        }
    }

    private void renderHotParticles(float alphaMult, SpriteAPI glow, SpriteAPI line) {
        for (Particle p : particles) {
            if (p.age < 0f) continue;

            float done = p.age / p.life;

            if (p.spark) {
                float length = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy) * 0.03f;
                line.setAngle((float) Math.toDegrees(Math.atan2(p.vy, p.vx)));
                line.setSize(length, 1.5f);
                line.setColor(profile.hot);
                line.setAlphaMult(alphaMult * (1f - done));
                line.renderAtCenter(p.x - p.vx * 0.015f, p.y - p.vy * 0.015f);
            } else if (p.hot > 0f && done < HOT_FRACTION) {
                float size = 3f * (p.startSize + (p.endSize - p.startSize) * done * 2f);
                glow(glow, p.x, p.y, size, profile.hot, alphaMult * p.hot * 0.5f * (1f - done / HOT_FRACTION));
            }
        }
    }

    private void renderFlash(Flash flash, float alphaMult, SpriteAPI glow, SpriteAPI line) {
        float done = flash.age / FLASH_SECONDS;
        float alpha = alphaMult * (1f - done);

        switch (profile) {
            case MORTAR:
                renderStar(flash, alpha, done, glow);
                break;
            case RAILGUN:
                double rad = Math.toRadians(flash.angle);
                line.setAngle(flash.angle);
                line.setSize(LANCE_LENGTH, 3f - 2f * done);
                line.setColor(LANCE);
                line.setAlphaMult(alpha);
                line.renderAtCenter(flash.x + (float) Math.cos(rad) * LANCE_LENGTH / 2f, flash.y + (float) Math.sin(rad) * LANCE_LENGTH / 2f);
                glow(glow, flash.x, flash.y, 48f, profile.hot, alpha);
                break;
            case MISSILE:
                glow(glow, flash.x, flash.y, 40f, FLASH_CORE, alpha);
                glow(glow, flash.x, flash.y, 66f, profile.hot, alpha);
                break;
        }
    }

    private void glow(SpriteAPI glow, float x, float y, float size, Color color, float alpha) {
        glow.setColor(color);
        glow.setAlphaMult(Math.min(1f, alpha));
        glow.setSize(size, size);
        glow.renderAtCenter(x, y);
        glow.setSize(size * GLOW_CORE_FRACTION, size * GLOW_CORE_FRACTION);
        glow.renderAtCenter(x, y);
    }

    private void renderStar(Flash flash, float alpha, float done, SpriteAPI glow) {
        float grow = 1f - (1f - done) * (1f - done);
        float thin = 1f - 0.5f * done;
        float reach = 0.5f + 0.5f * grow;

        glow.setColor(profile.hot);
        glow.setAlphaMult(Math.min(1f, alpha * 0.3f));
        glow.setSize(STAR_LIGHT_SIZE, STAR_LIGHT_SIZE);
        glow.renderAtCenter(flash.x, flash.y);

        streak(flash, glow, 0f, STAR_LENGTH * reach, STAR_WIDTH * thin, profile.hot, alpha);
        streak(flash, glow, 0f, STAR_CORE_LENGTH * reach, STAR_CORE_WIDTH * thin, FLASH_CORE, alpha);
        for (int side = -1; side <= 1; side += 2) {
            streak(flash, glow, side * STAR_SIDE_ANGLE, STAR_SIDE_LENGTH * (0.6f + 0.4f * grow), STAR_SIDE_WIDTH * thin, profile.hot, alpha * 0.9f);
            streak(flash, glow, side * 90f, STAR_CROSS_LENGTH, STAR_CROSS_WIDTH, FLASH_CORE, alpha * 0.8f);
        }

        glow(glow, flash.x, flash.y, 33f, FLASH_CORE, alpha);
    }

    // kite from just behind the muzzle to the tip: white at the muzzle, the colour at 30%, clear at the tip
    private void streak(Flash flash, SpriteAPI glow, float offset, float length, float width, Color color, float alpha) {
        float angle = flash.angle + offset;
        float dx = (float) Math.cos(Math.toRadians(angle));
        float dy = (float) Math.sin(Math.toRadians(angle));
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float wr = FLASH_CORE.getRed() / 255f;
        float wg = FLASH_CORE.getGreen() / 255f;
        float wb = FLASH_CORE.getBlue() / 255f;
        float a = Math.min(1f, alpha);

        float[] along = {-0.06f * length, 0.1f * length, 0.3f * length, length};
        float[] half = {0f, width, width * 0.78f, 0f};
        float[][] rgba = {
                {wr, wg, wb, a},
                {wr + (r - wr) * 0.33f, wg + (g - wg) * 0.33f, wb + (b - wb) * 0.33f, a * 0.97f},
                {r, g, b, a * 0.9f},
                {r, g, b, 0f}
        };

        beginShapes();
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i < along.length; i++) {
            GL11.glColor4f(rgba[i][0], rgba[i][1], rgba[i][2], rgba[i][3]);
            GL11.glVertex2f(flash.x + dx * along[i] - dy * half[i], flash.y + dy * along[i] + dx * half[i]);
            GL11.glVertex2f(flash.x + dx * along[i] + dy * half[i], flash.y + dy * along[i] - dx * half[i]);
        }
        GL11.glEnd();
        endShapes();

        glow.setColor(color);
        glow.setAlphaMult(a * 0.5f);
        glow.setAngle(angle);
        glow.setSize(length * 0.9f, width * 4f);
        glow.renderAtCenter(flash.x + dx * length * 0.35f, flash.y + dy * length * 0.35f);
        glow.setAngle(0f);
    }

    private void renderRim(Vector2f loc, float alpha) {
        beginShapes();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.5f);
        GL11.glColor4f(RIM.getRed() / 255f, RIM.getGreen() / 255f, RIM.getBlue() / 255f, Math.min(1f, alpha));
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < RIM_SEGMENTS; i++) {
            double rad = 2f * Math.PI * i / RIM_SEGMENTS;
            GL11.glVertex2f(loc.x + (float) Math.cos(rad) * RIM_RADIUS, loc.y + (float) Math.sin(rad) * RIM_RADIUS);
        }
        GL11.glEnd();
        endShapes();
    }

    private void beginShapes() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    private void endShapes() {
        GL11.glPopAttrib();
    }
}
