package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.items.consumables.entityAbilities.InterdictionMineAbility.getInterdictSeconds;

public class ECMExplosion extends BaseCustomEntityPlugin {
    public static final float DURATION = Settings.getFloat(Settings.ARTILLERY_MISSILE_DURATION);
    public static final float BASE_RADIUS = Settings.getFloat(Settings.ARTILLERY_MISSILE_EXPLOSION_RADIUS);

    public static final float RAMPUP_DUR_FRACT = 0.1f;
    public static final float PX_PER_PARTICLE = 100f;
    public static final float EXPLOSION_SIZE = 100f;

    public transient SpriteAPI sprite;

    public IntervalUtil particleInterval = new IntervalUtil(0.05f, 0.1f);
    public IntervalUtil additionalParticleInterval = new IntervalUtil(0.05f, 0.2f);
    public Color color = new Color(20, 200, 255, 255);

    public boolean explosion = true;
    public boolean finishing = false;
    public SectorEntityToken terrain;

    public float timePassedSeconds = 0;
    public float angle;
    public float dur;
    public float rad;

    public static class ECMExplosionParams {
        public LocationAPI loc;
        public Vector2f pos;
        public float dur;
        public float rad;

        public ECMExplosionParams(LocationAPI loc, Vector2f pos, float dur, float rad) {
            this.loc = loc;
            this.pos = pos;
            this.dur = dur;
            this.rad = rad;
        }
    }

    public static void spawn(ECMExplosionParams params) {
        params.loc.addCustomEntity(com.fs.starfarer.api.util.Misc.genUID(), null, "IndEvo_ECMExplosion", null, params);
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        if (pluginParams instanceof ECMExplosionParams) {
            this.dur = ((ECMExplosionParams) pluginParams).dur;
            this.rad = ((ECMExplosionParams) pluginParams).rad;

            angle = MathUtils.getRandomNumberInRange(0, 360);
            entity.setLocation(((ECMExplosionParams) pluginParams).pos.x, ((ECMExplosionParams) pluginParams).pos.y);

            terrain = ((ECMExplosionParams) pluginParams).loc.addTerrain("IndEvo_slowfield", new BaseRingTerrain.RingParams(rad, 0f, entity, "Slow Field"));
            terrain.setCircularOrbit(entity, 0, 0, 0);
        }

        readResolve();
    }

    Object readResolve() {
        sprite = Global.getSettings().getSprite("fx", "IndEvo_sub_missile_explosion");
        return this;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (!finishing && timePassedSeconds > dur) {
            com.fs.starfarer.api.util.Misc.fadeAndExpire(terrain, 0.1f);
            com.fs.starfarer.api.util.Misc.fadeAndExpire(entity, 0.1f);
            finishing = true;
            return;
        }

        timePassedSeconds += amount;
        particleInterval.advance(amount);
        additionalParticleInterval.advance(amount);

        float timePassedMult = Math.min(timePassedSeconds / (dur * RAMPUP_DUR_FRACT), 1);
        float currentRadius = rad * timePassedMult;

        int rColour = Math.max((int) Math.round(20 + 140 * timePassedMult), 0);
        int gColour = Math.max((int) Math.round(200 * (1 - timePassedMult)), 5);
        int bColour = 255;

        this.color = new Color(rColour, gColour, bColour, 255); //Start

        if (explosion) {
            spawnExplosion(EXPLOSION_SIZE);
            explosion = false;

            for (CampaignFleetAPI f : com.fs.starfarer.api.util.Misc.getNearbyFleets(entity, rad)) {
                interdictTarget(f);
            }
        }

        if (timePassedSeconds < dur * RAMPUP_DUR_FRACT && particleInterval.intervalElapsed()) {
            float circ = (float) (2 * currentRadius * Math.PI);
            float particleAmt = circ / PX_PER_PARTICLE;

            for (int i = 0; i < particleAmt; i++) {
                Vector2f loc = MathUtils.getPointOnCircumference(entity.getLocation(), currentRadius, MathUtils.getRandomNumberInRange(0, 360));
                entity.getContainingLocation().addHitParticle(
                        loc,
                        new Vector2f(0, 0),
                        (float) (12f + 8f * Math.random()),
                        (float) (0.1f + 0.5f * Math.random()),
                        MathUtils.getRandomNumberInRange((dur - timePassedSeconds) * 0.1f, (dur - timePassedSeconds) * 0.4f),
                        color);
            }
        } else if (additionalParticleInterval.intervalElapsed()) {
            Vector2f loc = MathUtils.getPointOnCircumference(entity.getLocation(), MathUtils.getRandomNumberInRange(10f, currentRadius), MathUtils.getRandomNumberInRange(0, 360));

            rColour = Math.max((int) Math.round(20 + 140 * Math.random()), 0);
            gColour = Math.max((int) Math.round(200 * (1 - Math.random())), 5);

            Color particleColour = new Color(rColour, gColour, bColour, 255); //Start

            entity.getContainingLocation().addHitParticle(
                    loc,
                    new Vector2f(0, 0),
                    (float) (12f + 8f * Math.random()),
                    (float) (0.1f + 0.5f * Math.random()),
                    (float) ((dur - timePassedSeconds) * Math.random()),
                    particleColour);
        }
    }

    public void interdictTarget(CampaignFleetAPI other) {
        if (other.isInHyperspaceTransition()) return;

        float interdictSeconds = getInterdictSeconds(other);
        if (interdictSeconds > 0 && interdictSeconds < 1f) interdictSeconds = 1f;

        SectorEntityToken.VisibilityLevel vis = other.getVisibilityLevelToPlayerFleet();
        if (vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS ||
                vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS
                || other.isPlayerFleet()) {

            if (interdictSeconds <= 0) {
                other.addFloatingText("Interdict avoided!", other.getFaction().getBaseUIColor(), 1f, true);
                return;
            } else {
                other.addFloatingText("Interdict! (" + (int) Math.round(interdictSeconds) + "s)", other.getFaction().getBaseUIColor(), 1f, true);
            }
        }

        float interdictDays = interdictSeconds / Global.getSector().getClock().getSecondsPerDay();

        for (AbilityPlugin ability : other.getAbilities().values()) {
            if (!ability.getSpec().hasTag(Abilities.TAG_BURN + "+") &&
                    !ability.getId().equals(Abilities.INTERDICTION_PULSE)) continue;

            float origCooldown = ability.getCooldownLeft();
            float extra = 0;
            if (ability.isActiveOrInProgress()) {
                extra += ability.getSpec().getDeactivationCooldown() * ability.getProgressFraction();
                ability.deactivate();

            }

            if (!ability.getSpec().hasTag(Abilities.TAG_BURN + "+")) continue;

            float cooldown = interdictDays;
            //cooldown = Math.max(cooldown, origCooldown);
            cooldown += origCooldown;
            cooldown += extra;
            float max = Math.max(ability.getSpec().getDeactivationCooldown(), 2f);
            if (cooldown > max) cooldown = max;
            ability.setCooldownLeft(cooldown);
        }
    }

    public void spawnExplosion(float size) {
        LocationAPI cl = entity.getContainingLocation();

        VariableExplosionEntityPlugin.VariableExplosionParams params =
                new VariableExplosionEntityPlugin.VariableExplosionParams(
                        "IndEvo_missile_hit",
                        false,
                        1f,
                        color, cl, entity.getLocation(), size, 0.3f);

        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.NONE;

        SectorEntityToken explosion = cl.addCustomEntity(com.fs.starfarer.api.util.Misc.genUID(), "Explosion",
                "IndEvo_VariableExplosion", Factions.NEUTRAL, params);

        explosion.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        float size = 3f * rad * MiscIE.smootherstep(0, (dur * RAMPUP_DUR_FRACT), timePassedSeconds);
        float alpha = 1 - Math.min(timePassedSeconds / (dur * RAMPUP_DUR_FRACT), 1);

        sprite.setAdditiveBlend();
        sprite.setAngle(angle);
        sprite.setSize(size, size);
        sprite.setAlphaMult(alpha);
        sprite.setColor(color);
        sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }

    public float getRenderRange() {
        return 9999999999999f;
    }
}
