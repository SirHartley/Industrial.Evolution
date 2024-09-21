package indevo.items.consumables.entities;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.ParticleControllerAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.items.consumables.entityAbilities.InterdictionMineAbility;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class InterceptMissileEntityPlugin extends BaseMissileEntityPlugin {

    public static final float DISTANCE_TO_EXPLODE = 10f;
    public static final float INTERDICT_RANGE = 300f;
    public static final float INTERDICT_SECONDS = 6f;
    public static final float TRACE_SECONDS = 20f;
    public static final float TRACE_PROFILE_INCREASE = 1000f;
    public static final float STUN_SECONDS = 2f;

    @Override
    public boolean shouldExplode() {
        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets())
            if (Misc.getDistance(fleet, entity) < DISTANCE_TO_EXPLODE + fleet.getRadius() && fleet != source) return true;
        return false;
    }

    @Override
    public void onExplosion() {
        CampaignPingSpec custom = new CampaignPingSpec();
        custom.setColor(getTrailColour());
        custom.setWidth(15);
        custom.setRange(INTERDICT_RANGE * 1.3f);
        custom.setDuration(0.5f);
        custom.setAlphaMult(1f);
        custom.setInFraction(0.1f);
        custom.setNum(1);
        Global.getSector().addPing(entity, custom);

        for (final CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == entity) continue;
            if (other.isInHyperspaceTransition()) continue;

            float dist = Misc.getDistance(entity.getLocation(), other.getLocation());
            if (dist > INTERDICT_RANGE) continue;

            SectorEntityToken.VisibilityLevel vis = other.getVisibilityLevelToPlayerFleet();

            if (vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS ||
                vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS) {
                other.addScript(new InterdictionMineAbility.GoSlowScript(other, STUN_SECONDS));
                other.addFloatingText("Slowed & Interdict! (" + (int) Math.round(INTERDICT_SECONDS) + "s)", getTrailColour(), 2f, true);
            }

            other.addScript(new EveryFrameScript() {
                public static final float ROTATIONS_PER_SEC = 2f;
                public final float dur = TRACE_SECONDS;
                public final CampaignFleetAPI fleet = other;
                public float amt = 0;
                public boolean init = false;
                public ParticleControllerAPI[] indicator;
                public float currentAngle;

                @Override
                public boolean isDone() {
                    return amt > dur;
                }

                @Override
                public boolean runWhilePaused() {
                    return false;
                }

                @Override
                public void advance(float amount) {
                    if (isDone()) return;

                    amt += amount;
                    String id = "IndEvo_TrackingMissile";

                    if (!init){
                        currentAngle = Misc.random.nextFloat() * 360f;
                        indicator = Misc.addGlowyParticle(
                                fleet.getContainingLocation(),
                                MathUtils.getPointOnCircumference(fleet.getLocation(), fleet.getRadius() + 30f, currentAngle),
                                new Vector2f(0,0),
                                40f,
                                0.5f,
                                TRACE_SECONDS + 1f,
                                getTrailColour());

                        init = true;
                    }

                    //had some instances where indicator would time out before anim, not sure why, so we just nullcheck - good enough
                    if (indicator != null){
                        currentAngle += (360f / ROTATIONS_PER_SEC) * amount;
                        if (currentAngle > 360f) angle = 0f;
                        if (currentAngle < 0) angle = 360f;

                        Vector2f nextLoc = MathUtils.getPointOnCircumference(fleet.getLocation(), fleet.getRadius() + 30f, currentAngle);
                        indicator[0].setX(nextLoc.x);
                        indicator[0].setY(nextLoc.y);
                    }

                    if (!isDone()){
                        fleet.getStats().getSensorProfileMod().modifyFlat(id, TRACE_PROFILE_INCREASE, "Tracer Missile");
                    } else fleet.getStats().getSensorProfileMod().unmodify(id);
                }
            });

            float interdictDays = INTERDICT_SECONDS / Global.getSector().getClock().getSecondsPerDay();

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
                cooldown += origCooldown;
                cooldown += extra;
                float max = Math.max(ability.getSpec().getDeactivationCooldown(), 2f);
                if (cooldown > max) cooldown = max;
                ability.setCooldownLeft(cooldown);
            }

            Global.getSoundPlayer().playSound("world_interdict_hit", 1f, 1f, other.getLocation(), other.getVelocity());
        }
    }

    @Override
    public SpriteAPI getMissileSprite() {
        return Global.getSettings().getSprite("IndEvo", "IndEvo_consumable_missile_intercept");
    }

    @Override
    public Color getTrailColour() {
        return new Color(240, 20,240);
    }
}
