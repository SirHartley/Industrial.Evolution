package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.industries.artillery.renderers.ArtilleryFireRenderer;
import indevo.industries.artillery.scripts.DroneNetScript;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.utils.helper.MiscIE.smootherstep;

public class RailgunProjectileEntityPlugin extends BaseCustomEntityPlugin {

    public static final float MAX_RANGE = 50000f;
    public static final float PROJECTILE_VELOCITY = 6000f;
    public static final float EXPLOSION_SIZE = 250f;
    public static final float HIT_DISTANCE = EXPLOSION_SIZE * 0.2f;
    public static final float FRIENDLY_FIRE_IMMUNITY_DIST_FRACT = 0.3f;

    public static final float LINE_LENGTH = 265;
    public static final float LINE_WIDTH = 4f;
    public static final float ALPHA_FADE_IN_FRACTION = 0.1f;
    public static final float MAX_LINE_ALPHA = 0.5f;

    public static final float BEAM_START = 40f;
    public static final float BEAM_CORE_WIDTH = 8f;
    public static final float BEAM_GLOW_WIDTH = 88f;
    public static final float BEAM_HALO_WIDTH = 176f;
    public static final float BEAM_HALO_ALPHA = 0.35f;
    public static final float BEAM_HOLD = 0.06f;
    public static final float BEAM_DECAY = 0.11f;
    public static final float BEAM_DURATION = 0.4f;
    public static final float MUZZLE_GLOW_SIZE = 90f;
    public static final float AFTERGLOW_DURATION = 1.1f;
    public static final float AFTERGLOW_WIDTH = 2.5f;
    public static final float AFTERGLOW_ALPHA = 0.16f;
    public static final float AFTERGLOW_DASH = 70f;
    public static final float AFTERGLOW_GAP = 50f;
    public static final float AFTERGLOW_DASH_SPEED = 700f;
    public static final float AFTERGLOW_DASH_ALPHA = 0.14f;

    public static final Color BEAM_COLOR = new Color(150, 215, 255);
    public static final Color BEAM_CORE_COLOR = new Color(235, 248, 255);
    public static final Color AFTERGLOW_COLOR = new Color(100, 220, 255);

    protected static final String ARTILLERY_REACTION_SCRIPT_KEY = "$RailReaction_";
    protected static final float AREA_AVOIDANCE_RADIUS = 300f;

    public float currentAlpha = 0f;

    transient private SpriteAPI line;
    transient private SpriteAPI stream;
    transient private SpriteAPI glow;

    private float timePassedSeconds = 0f;
    public float impactSeconds;
    public SectorEntityToken origin;
    public Vector2f target;
    public Vector2f originLocation = new Vector2f();
    private boolean fired = false;
    private float beamLength = MAX_RANGE;

    public static class ProjectileParams {
        public SectorEntityToken origin;
        public Vector2f target;
        public float impactSeconds;

        public ProjectileParams(SectorEntityToken origin, Vector2f target, float impactSeconds) {
            this.origin = origin;
            this.target = target;
            this.impactSeconds = impactSeconds;
        }
    }

    public static void spawn(LocationAPI loc, SectorEntityToken origin, Vector2f target, float impactSeconds) {
        SectorEntityToken t = loc.addCustomEntity(
                null,
                null,
                "IndEvo_railgun_projectile",
                null,
                new ProjectileParams(origin, target, impactSeconds));

        t.setLocation(origin.getLocation().x, origin.getLocation().y);
    }

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        entity.setDetectionRangeDetailsOverrideMult(0.75f);

        if (pluginParams instanceof ProjectileParams) {
            this.origin = ((ProjectileParams) pluginParams).origin;
            this.target = new Vector2f(((ProjectileParams) pluginParams).target);
            this.impactSeconds = ((ProjectileParams) pluginParams).impactSeconds;
            this.originLocation = new Vector2f(origin.getLocation());
        }

        readResolve();
    }

    Object readResolve() {
        line = Global.getSettings().getSprite("fx", "IndEvo_line");
        stream = Global.getSettings().getSprite("fx", "IndEvo_stream_core");
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    public void advance(float amount) {
        if (originLocation == null || target == null) {
            Misc.fadeAndExpire(entity, 0.1f);
            return;
        }

        timePassedSeconds += amount;

        if (!fired) {
            warnFleets();
            if (origin != null && origin.getLocation() != null) originLocation.set(origin.getLocation());
            ArtilleryFireRenderer.reportIncoming(origin, ArtilleryStationEntityPlugin.TYPE_RAILGUN, getAngle(), impactSeconds - timePassedSeconds);

            float fadeIn = impactSeconds * ALPHA_FADE_IN_FRACTION;
            currentAlpha = MAX_LINE_ALPHA * smootherstep(0, fadeIn, timePassedSeconds);

            if (timePassedSeconds >= impactSeconds) fire();
            return;
        }

        if (timePassedSeconds > impactSeconds + AFTERGLOW_DURATION) Misc.fadeAndExpire(entity, 0.1f);
    }

    private void warnFleets() {
        String key = ARTILLERY_REACTION_SCRIPT_KEY + entity.getId();
        float timing = impactSeconds - timePassedSeconds;

        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (Misc.getDistance(target, other.getLocation()) > 1000f) continue;
            if (other.getMemoryWithoutUpdate().contains(key)) continue;

            other.addScript(new MortarProjectileEntityPlugin.ArtilleryReactionScript(target, AREA_AVOIDANCE_RADIUS, other, timing));
            other.getMemoryWithoutUpdate().set(key, true, timing);
        }
    }

    private void fire() {
        fired = true;

        float angle = getAngle();
        Vector2f dir = Misc.getUnitVectorAtDegreeAngle(angle);
        float minDist = Math.max(Setting.ARTILLERY_MIN_RANGE.value(), Misc.getDistance(originLocation, target) * FRIENDLY_FIRE_IMMUNITY_DIST_FRACT);

        CampaignFleetAPI hit = null;
        float hitAlong = MAX_RANGE;

        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) {
            float dx = fleet.getLocation().x - originLocation.x;
            float dy = fleet.getLocation().y - originLocation.y;
            float along = dx * dir.x + dy * dir.y;
            if (along < minDist || along >= hitAlong) continue;
            if (Math.abs(dx * dir.y - dy * dir.x) > HIT_DISTANCE) continue;

            hit = fleet;
            hitAlong = along;
        }

        beamLength = hitAlong;
        Vector2f end = MathUtils.getPointOnCircumference(originLocation, beamLength, angle);
        entity.setLocation(end.x, end.y);

        ArtilleryFireRenderer.reportShot(origin, ArtilleryStationEntityPlugin.TYPE_RAILGUN, angle);
        Global.getSoundPlayer().playSound("IndEvo_railgun_fire", MathUtils.getRandomNumberInRange(0.9f, 1.1f), 0.5f, originLocation, Misc.ZERO);

        if (hit != null) spawnExplosion();
    }

    private float getAngle() {
        return Misc.getAngleInDegrees(originLocation, target);
    }

    public void spawnExplosion() {
        LocationAPI cl = entity.getContainingLocation();

        VariableExplosionEntityPlugin.VariableExplosionParams params =
                new VariableExplosionEntityPlugin.VariableExplosionParams(
                        "IndEvo_railgun_hit",
                        false,
                        1f,
                        new Color(100, 160, 255), cl, entity.getLocation(), EXPLOSION_SIZE, 0.6f);

        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.LOW;
        SectorEntityToken explosion = cl.addCustomEntity(Misc.genUID(), "Explosion",
                "IndEvo_VariableExplosion", Factions.NEUTRAL, params);

        explosion.setLocation(entity.getLocation().x, entity.getLocation().y);
        DroneNetScript.reportBlast(cl, entity.getLocation(), EXPLOSION_SIZE);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (line == null) readResolve();

        float alphaMult = viewport.getAlphaMult();
        if (alphaMult <= 0f) return;

        if (!fired) renderWarningLine(alphaMult);
        else {
            float age = timePassedSeconds - impactSeconds;
            renderAfterglow(alphaMult, age);
            renderBeam(alphaMult, age);
        }
    }

    private void renderWarningLine(float alphaMult) {
        float timeLeft = 1f - Math.min(timePassedSeconds / impactSeconds, 1f);
        Color color = new Color(255, Math.round(200 * timeLeft), Math.round(100 * timeLeft));
        float angle = getAngle();

        line.setAdditiveBlend();
        line.setAngle(angle);
        line.setSize(LINE_LENGTH, LINE_WIDTH);
        line.setAlphaMult(currentAlpha * alphaMult);
        line.setColor(color);

        float pos = LINE_LENGTH / 2f;
        for (int i = 0; i < Math.ceil(MAX_RANGE / LINE_LENGTH); i++) {
            Vector2f loc = MathUtils.getPointOnCircumference(originLocation, pos, angle);
            line.renderAtCenter(loc.x, loc.y);
            pos += LINE_LENGTH;
        }
    }

    private void renderBeam(float alphaMult, float age) {
        if (age > BEAM_DURATION) return;

        float energy = age < BEAM_HOLD ? 1f : (float) Math.exp(-(age - BEAM_HOLD) / BEAM_DECAY);
        energy *= 1f - smootherstep(BEAM_DURATION * 0.7f, BEAM_DURATION, age);
        float alpha = energy * alphaMult;
        float widthMult = 0.5f + 0.5f * energy;

        drawSegment(stream, BEAM_COLOR, BEAM_START, beamLength, BEAM_HALO_WIDTH * widthMult, alpha * BEAM_HALO_ALPHA);
        drawSegment(stream, BEAM_COLOR, BEAM_START, beamLength, BEAM_GLOW_WIDTH * widthMult, alpha);
        drawSegment(line, BEAM_CORE_COLOR, BEAM_START, beamLength, BEAM_CORE_WIDTH * (0.35f + 0.65f * energy), alpha * 0.95f);

        Vector2f muzzle = MathUtils.getPointOnCircumference(originLocation, BEAM_START, getAngle());
        float size = MUZZLE_GLOW_SIZE * (float) Math.sqrt(energy);
        glow.setAdditiveBlend();
        glow.setColor(BEAM_COLOR);
        glow.setSize(size, size);
        glow.setAlphaMult(alpha * 0.9f);
        glow.renderAtCenter(muzzle.x, muzzle.y);
    }

    private void renderAfterglow(float alphaMult, float age) {
        if (age > AFTERGLOW_DURATION) return;

        float alpha = (float) Math.pow(1f - age / AFTERGLOW_DURATION, 1.5f) * alphaMult;
        drawSegment(line, AFTERGLOW_COLOR, BEAM_START, beamLength, AFTERGLOW_WIDTH, alpha * AFTERGLOW_ALPHA);

        float period = AFTERGLOW_DASH + AFTERGLOW_GAP;
        float pos = BEAM_START + (age * AFTERGLOW_DASH_SPEED) % period;
        while (pos < beamLength) {
            drawSegment(line, AFTERGLOW_COLOR, pos, Math.min(pos + AFTERGLOW_DASH, beamLength), AFTERGLOW_WIDTH, alpha * AFTERGLOW_DASH_ALPHA);
            pos += period;
        }
    }

    private void drawSegment(SpriteAPI sprite, Color color, float from, float to, float width, float alpha) {
        if (alpha <= 0f || to <= from) return;

        float angle = getAngle();
        Vector2f center = MathUtils.getPointOnCircumference(originLocation, (from + to) / 2f, angle);

        sprite.setAdditiveBlend();
        sprite.setAngle(angle);
        sprite.setSize(to - from, width);
        sprite.setColor(color);
        sprite.setAlphaMult(alpha);
        sprite.renderAtCenter(center.x, center.y);
    }

    public float getRenderRange() {
        return 1000000f;
    }
}
