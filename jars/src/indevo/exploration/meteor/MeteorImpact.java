package indevo.exploration.meteor;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class MeteorImpact implements EveryFrameScript {

    private static final float DURATION_SECONDS = 0.2f;
    private static final float BASE_ARC = 160f;
    private static final float ARC_REDUCTION_FACTOR = 60f;
    private static final float BASE_IMPACT_MULT = 0.5f;

    private static final float RADIUS_PER_ASTERIOD = 20f;

    private SectorEntityToken fleet;
    private Vector2f dV;
    private float elapsed = 0f;

    public MeteorImpact(SectorEntityToken target, SectorEntityToken meteor, boolean dealDamage) {
        this.fleet = target;
        boolean isFleet = target instanceof CampaignFleetAPI;

        Vector2f velocity = target.getVelocity(); // Fleet velocity
        Vector2f meteorVel = meteor.getVelocity(); // Meteor velocity

        // Compute relative velocity (vector from meteor to target)
        Vector2f relativeVel = Vector2f.sub(meteorVel, velocity, null);
        float relativeSpeed = relativeVel.length();

        // Determine impact direction based on relative velocity, fall back to facing if too slow
        float angle;
        if (relativeSpeed >= 10f) {
            angle = Misc.getAngleInDegrees(relativeVel);
        } else if (velocity.length() >= 10f) {
            angle = Misc.getAngleInDegrees(velocity);
        } else {
            angle = target.getFacing();
        }

        float mult = isFleet ? Misc.getFleetRadiusTerrainEffectMult((CampaignFleetAPI) target) : 0f;
        float arc = BASE_ARC - ARC_REDUCTION_FACTOR * mult;

        // If target is barely moving or immune to terrain, skip most calculations
        if (!isFleet || mult <= 0f) {
            dV = new Vector2f();
        }

        if (target.isInCurrentLocation()) {
            if (isFleet && dealDamage) applyDamage();
            if (!dealDamage && target.isPlayerFleet()) notifyPlayer();

            playImpactSound(mult, dealDamage);
            spawnImpactGlow(mult);

            int numAsteroids = (int) Math.ceil(meteor.getRadius() / RADIUS_PER_ASTERIOD);
            float[] asteroidSizes = splitRadiusRandom(numAsteroids, meteor.getRadius());

            for (int i = 0; i < numAsteroids; i++) {
                float spawnAngle = angle + randomWithinArc(arc);

                float size = asteroidSizes[i];
                float sizeFraction = size / meteor.getRadius();
                float asteroidRelativeSpeed = relativeSpeed * (1f - 0.5f * sizeFraction);

                spawnAsteroid(spawnAngle, mult, asteroidRelativeSpeed, size);
            }
        }

        // Calculate delta-V applied to target due to the impact
        dV = calculateImpactVelocity(relativeSpeed, mult, angle);
    }

    public static float[] splitRadiusRandom(int n, float radius) {
        float[] radii = new float[n];
        float total = 0;
        Random rand = new Random();

        float[] randomParts = new float[n];
        for (int i = 0; i < n; i++) {
            randomParts[i] = rand.nextFloat();
            total += randomParts[i];
        }

        for (int i = 0; i < n; i++) {
            radii[i] = (randomParts[i] / total) * radius;
        }

        return radii;
    }

    private float randomWithinArc(float arc) {
        return (float) Math.random() * arc - arc / 2f;
    }

    private void applyDamage() {
        CampaignFleetAPI fleet = (CampaignFleetAPI) this.fleet;

        WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            picker.add(member, getHullSizeWeight(member));
        }

        FleetMemberAPI target = picker.pick();
        if (target != null) {
            float damageMult = Math.max(1f, fleet.getCurrBurnLevel() - Misc.getGoSlowBurnLevel(fleet));
            Misc.applyDamage(target, null, damageMult, true, "asteroid_impact", "Asteroid impact",
                    true, null, target.getShipName() + " suffers damage from an asteroid impact");
        }
    }

    private float getHullSizeWeight(FleetMemberAPI member) {
        return switch (member.getHullSpec().getHullSize()) {
            case CAPITAL_SHIP -> 20f;
            case CRUISER -> 10f;
            case DESTROYER -> 5f;
            case FRIGATE -> 1f;
            default -> 0f;
        };
    }

    private void notifyPlayer() {
        Global.getSector().getCampaignUI().addMessage(
                "Asteroid impact on drive bubble", Misc.getNegativeHighlightColor());
    }

    private void playImpactSound(float mult, boolean dealDamage) {
        float dist = Misc.getDistance(Global.getSector().getPlayerFleet().getLocation(), fleet.getLocation());
        if (dist >= HyperspaceTerrainPlugin.STORM_STRIKE_SOUND_RANGE) return;

        float volumeMult = 0.75f * (0.5f + 0.5f * mult);
        if (volumeMult <= 0f) return;

        String soundId = dealDamage ? "hit_heavy" : "hit_shield_heavy_gun";
        Global.getSoundPlayer().playSound(soundId, 1f, volumeMult, fleet.getLocation(), Misc.ZERO);
    }

    private void spawnAsteroid(float angle, float mult, float relativeSpeed, float size) {
        AsteroidAPI asteroid = fleet.getContainingLocation().addAsteroid(size);
        asteroid.setFacing((float) Math.random() * 360f);

        Vector2f spawnVelocity = Misc.getUnitVectorAtDegreeAngle(angle + 180f);
        // Use relativeSpeed instead of fleet speed for more realistic asteroid fling velocity
        spawnVelocity.scale(relativeSpeed + (20f + 20f * (float) Math.random()) * mult);

        asteroid.getVelocity().set(spawnVelocity);

        Vector2f spawnLocation = Misc.getUnitVectorAtDegreeAngle(angle + 180f);
        spawnLocation.scale(fleet.getRadius());
        Vector2f.add(spawnLocation, fleet.getLocation(), spawnLocation);
        asteroid.setLocation(spawnLocation.x, spawnLocation.y);

        float rotation = Math.signum(asteroid.getRotation()) * (50f + 50f * (float) Math.random());
        asteroid.setRotation(rotation);

        Misc.fadeInOutAndExpire(asteroid, 0.2f, 1f + 1f * (float) Math.random(), 1f);
    }

    private void spawnImpactGlow(float mult) {
        Vector2f reducedVelocity = new Vector2f(fleet.getVelocity());
        reducedVelocity.scale(0.7f);

        float glowSize = 100f + 100f * mult + 50f * (float) Math.random();
        Color glowColor = new Color(255, 165, 100, 255);

        Misc.addHitGlow(fleet.getContainingLocation(), fleet.getLocation(), reducedVelocity, glowSize, glowColor);
    }

    private Vector2f calculateImpactVelocity(float speed, float mult, float angle) {
        Vector2f impulse = Misc.getUnitVectorAtDegreeAngle(angle);
        float impact = speed * (BASE_IMPACT_MULT + mult * BASE_IMPACT_MULT);
        impulse.scale(impact / DURATION_SECONDS);
        return impulse;
    }

    @Override
    public void advance(float amount) {
        if (fleet instanceof CampaignFleetAPI){
            CampaignFleetAPI fleet = (CampaignFleetAPI) this.fleet;

            fleet.setOrbit(null);
            Vector2f velocity = fleet.getVelocity();
            fleet.setVelocity(velocity.x + dV.x * amount, velocity.y + dV.y * amount);
        }

        elapsed += amount;
    }

    @Override
    public boolean isDone() {
        return elapsed >= DURATION_SECONDS;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}