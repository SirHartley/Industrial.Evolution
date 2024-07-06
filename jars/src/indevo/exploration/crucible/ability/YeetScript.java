package indevo.exploration.crucible.ability;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.fleet.FleetMemberViewAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseAbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.crucible.VignetteRenderer;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class YeetScript implements EveryFrameScript {

    public static void yeet() {
        PlanetAPI furthestPlanet = null;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        float furthestDist = Float.MIN_VALUE;

        for (PlanetAPI entity : player.getContainingLocation().getPlanets()) {
            if (entity != null) {
                float distance = Misc.getDistance(entity, player);

                if (distance > furthestDist) {
                    furthestDist = distance;
                    furthestPlanet = entity;
                }
            }
        }

        if (furthestPlanet != null) {
            Global.getSector().getPlayerFleet().addScript(new YeetScript(player, furthestPlanet.getLocation()));
            ModPlugin.log("YeetScript added to fleet. Target location: " + furthestPlanet.getLocation());
        } else {
            ModPlugin.log("No valid target planet found.");
        }

        //runcode indevo.exploration.crucible.ability.YeetScript.yeet();
    }

    public static float TRANSIT_MUSIC_SUPPRESSION = 1f;
    public static String TRANSIT_SOUND_LOOP = "ui_slipsurge_travel_loop";
    public static float SENSOR_RANGE_MULT = 0.1f;
    public static String SENSOR_MOD_ID = "slipsurge_sensor_penalty";

    public float travelledDistance = 0f;
    public float maxVelocity;

    public static final float DECEL_FRACTION = 0.25f; // 20% of the journey for deceleration
    public static final float MIN_TRAVEL_TIME = 1f;
    public static final float MAX_TRAVEL_TIME = 3f;
    public static final float DEFAULT_VEL = 200000f;

    private final Vector2f originLocation;
    private final Vector2f targetLocation;

    protected CampaignFleetAPI fleet;

    protected boolean done = false;

    protected VignetteRenderer renderer;

    public YeetScript(CampaignFleetAPI fleet, Vector2f targetLocation) {
        this.fleet = fleet;
        this.originLocation = new Vector2f(fleet.getLocation());
        this.targetLocation = new Vector2f(targetLocation);

        float dist = Misc.getDistance(originLocation, targetLocation);
        float timeToTravel = dist / DEFAULT_VEL;
        this.maxVelocity = dist / MathUtils.clamp(timeToTravel, MIN_TRAVEL_TIME, MAX_TRAVEL_TIME);

        this.renderer = new VignetteRenderer();
        LunaCampaignRenderer.addRenderer(renderer);

        ModPlugin.log("YeetScript initialized. Origin: " + originLocation + ", Target: " + targetLocation);
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        float currentDistance = Misc.getDistance(fleet.getLocation(), originLocation);
        float totalDistance = Misc.getDistance(originLocation, targetLocation);
        float distFraction = currentDistance / totalDistance;

        if (distFraction >= 0.98f) {
            unsetHighSpeedFlagsOnFleet();
            renderer.setDone();
            renderer = null;
            done = true;
            return;
        }

        updateVignetteAlpha(distFraction);
        setFleetVelocity(distFraction);

        //smoother if we allow limited control in the final approach
        if (distFraction < 0.85f) {
            forceDisableAbilities();
            advanceFleetPosition(amount, distFraction);
            setHighSpeedFlagsOnFleetOneFrame();
        }

        addHighBurnMusicFadeAndJitter();
    }

    public static float VIGNETTE_SMOOTING_FRACT = 0.2f;

    public void updateVignetteAlpha(float fraction){
        float alpha = getVelocityForDistFract(fraction) / maxVelocity;
        if (fraction <= VIGNETTE_SMOOTING_FRACT) alpha = alpha * MiscIE.smootherstep(0f, VIGNETTE_SMOOTING_FRACT, fraction);

        renderer.setAlphaMult(alpha);
    }

    public void advanceFleetPosition(float amount, float fraction) {
        travelledDistance += getVelocityForDistFract(fraction) * amount;
        float angle = Misc.getAngleInDegrees(originLocation, targetLocation);
        Vector2f newLoc = MathUtils.getPointOnCircumference(originLocation, travelledDistance, angle);
        fleet.setLocation(newLoc.x, newLoc.y);
    }

    private float getVelocityForDistFract(float fraction) {
        float smoothedT;
        if (fraction >= (1f - DECEL_FRACTION)) { // Last 20% of the journey (deceleration)
            smoothedT = 1 - MiscIE.smootherstep(1 - DECEL_FRACTION, 1, fraction);
        } else { // Middle of the journey (cruising)
            smoothedT = 1.0f;
        }

        float velocity = smoothedT * maxVelocity;
        return velocity;
    }

    private void setFleetVelocity(float fraction) {
        Vector2f direction = VectorUtils.getDirectionalVector(originLocation, targetLocation);
        Vector2f velocity = new Vector2f(direction);
        velocity.scale(getVelocityForDistFract(fraction));
        fleet.setVelocity(velocity.x, velocity.y);
    }

    private void setFleetVelocityNoForce(float fraction) {
        Vector2f direction = VectorUtils.getDirectionalVector(originLocation, targetLocation);
        Vector2f velocity = new Vector2f(direction);
        velocity.scale(getVelocityForDistFract(fraction));

        Vector2f velocityFromMovement = fleet.getVelocityFromMovementModule();
        float velocityLength = velocity.length();
        float velocityFromMovementLength = velocityFromMovement.length();

        // Normalize velocityFromMovement manually
        if (velocityFromMovementLength != 0) {
            velocityFromMovement.x /= velocityFromMovementLength;
            velocityFromMovement.y /= velocityFromMovementLength;
        }

        float newVelocityFromMovementLength = velocityFromMovementLength + velocityLength;
        velocityFromMovement.scale(newVelocityFromMovementLength);

        fleet.setVelocity(velocityFromMovement.x, velocityFromMovement.y);
    }

    private void addHighBurnMusicFadeAndJitter() {
        float burn = Misc.getBurnLevelForSpeed(fleet.getVelocity().length());
        float angle = Misc.getAngleInDegrees(fleet.getVelocity());
        Vector2f jitterDir = Misc.getUnitVectorAtDegreeAngle(angle + 180f);

        float b = (burn - 50) / 450f;
        if (b < 0) b = 0;
        if (b > 1f) b = 1f;

        if (b > 0) {
            // music suppression
            if (fleet.isPlayerFleet()) {
                float volume = b;
                Global.getSector().getCampaignUI().suppressMusic(TRANSIT_MUSIC_SUPPRESSION * volume);
                Global.getSoundPlayer().setNextLoopFadeInAndOut(0.05f, 0.5f);
                Global.getSoundPlayer().playLoop(TRANSIT_SOUND_LOOP, fleet,
                        1f, volume,
                        fleet.getLocation(), fleet.getVelocity());
            }

            // jitter
            for (FleetMemberViewAPI view : fleet.getViews()) {
                Color c = view.getMember().getHullSpec().getHyperspaceJitterColor();
                c = Misc.setAlpha(c, 60);
                view.setJitter(0.1f, 1f, c, 10 + Math.round(40f * b), 20f);
                view.setUseCircularJitter(true);
                view.setJitterDirection(jitterDir);
                view.setJitterLength(30f * b);
                view.setJitterBrightness(b);
            }
        } else {
            for (FleetMemberViewAPI view : fleet.getViews()) {
                view.endJitter();
            }
        }
    }

    private void setHighSpeedFlagsOnFleetOneFrame() {
        // prepare fleet for high speed forced movement
        fleet.setNoEngaging(0.1f);
        fleet.setInteractionTarget(null);
        if (fleet.isPlayerFleet()) {
            Global.getSector().getCampaignUI().setFollowingDirectCommand(true);
            Global.getSector().getCampaignUI().clearLaidInCourse();
        }
        fleet.getStats().getSensorRangeMod().modifyMult(SENSOR_MOD_ID, SENSOR_RANGE_MULT, "Extreme burn level");
    }

    public void unsetHighSpeedFlagsOnFleet() {
        done = true;
        fleet.setNoEngaging(0f);
        fleet.getStats().getSensorRangeMod().unmodifyMult(SENSOR_MOD_ID);

        for (FleetMemberViewAPI view : fleet.getViews()) view.endJitter();
        if (fleet.isPlayerFleet()) Global.getSector().getCampaignUI().setFollowingDirectCommand(false);

        fleet.fadeInIndicator();
    }

    public void forceDisableAbilities() {
        AbilityPlugin gs = fleet.getAbility(Abilities.GENERATE_SLIPSURGE);
        if (gs instanceof BaseAbilityPlugin) {
            BaseAbilityPlugin base = (BaseAbilityPlugin) gs;
            for (AbilityPlugin curr : fleet.getAbilities().values()) {
                if (!base.isCompatible(curr)) {
                    if (curr.isActiveOrInProgress()) {
                        curr.deactivate();
                    }
                    curr.forceDisable();
                }
            }
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
