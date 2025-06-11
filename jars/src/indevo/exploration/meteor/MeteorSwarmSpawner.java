package indevo.exploration.meteor;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Circle;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.TrigHelper;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//add to system
//spawn warning icons via MeteorSwarWrnRenderer
//spawn exclusion zone for NPCs
//spawn meteors outside of viewpoint
//make em move
//make em despawn

//begin with small and fast, dense in middle, random fast ones to the side
//scale up in size, larger in middle
//spawn the large loot ones in dead center

//scale with vertical width and time

public class MeteorSwarmSpawner implements EveryFrameScript {
    public static final float[] NORMAL_DIST_MAGIC_NUMBERS = {0.4f, 0}; //sets the distribution https://www.desmos.com/calculator/11rldprhvd
    public static final float[] WEIGHT_OVER_TIME_MAGIC_NUMBERS = {0.4f, 0};
    public static final int BASE_WIDTH_PER_ASTEROID_PER_SECOND = 700; //a good width is around 4k

    public static final float MIN_TREASURE_SPAWN_FRACT = 0.2f;
    public static final float MAX_TREASURE_SPAWN_FRACT = 0.8f;

    public StarSystemAPI system;
    public float intensity; //0 to x, also sets the loot

    public float width;
    public CircularArc arc;
    private final Random random;
    private final float runtime;
    private final float density;

    private float timePassed = 0;
    private int treasureSpawned = 0;
    private IntervalUtil treasureInterval;

    private MeteorSwarmWarningRenderer warningRenderer = null;
    private float speedLastSpawned = 0f;

    public MeteorSwarmSpawner(StarSystemAPI system, float intensity, int treasureAmt, float density, float runtime, Vector2f startLoc, Vector2f centerLoc, Vector2f endLoc, float width, long seed) {
        this.system = system;
        this.intensity = intensity;
        this.width = width;
        this.runtime = runtime;
        this.density = density;

        Circle circle = TrigHelper.findThreePointCircle(startLoc, centerLoc, endLoc);
        this.arc = new CircularArc(circle, circle.getAngleForPoint(startLoc), circle.getAngleForPoint(endLoc));

        this.random = new Random(seed);

        //treasure spawns after 20 and before 80% has run
        //it spawns x times
        //split middle time in three, fudge a bit, good enough
        this.treasureInterval = new IntervalUtil((runtime * 0.2f) / treasureAmt, (runtime * 0.5f) / treasureAmt);
    }

    public MeteorSwarmSpawner(StarSystemAPI system, float intensity, int treasureAmt, float density, float runtime, CircularArc arc, float width, long seed) {
        this.system = system;
        this.intensity = intensity;
        this.width = width;
        this.runtime = runtime;
        this.density = density;
        this.arc = arc;
        this.random = new Random(seed);
        this.treasureInterval = new IntervalUtil((runtime * 0.2f) / treasureAmt, (runtime * 0.5f) / treasureAmt);
    }

    @Override
    public boolean isDone() {
        return timePassed > runtime;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (isDone()) return;

        if (warningRenderer == null) {
            warningRenderer = new MeteorSwarmWarningRenderer(system, arc);
            system.addScript(warningRenderer);
            LunaCampaignRenderer.addRenderer(new WarningSignNotificationRenderer(arc, system));
            Global.getSoundPlayer().playUISound("cr_allied_critical", 1, 1);
        }

        timePassed += amount;

        float baseChance = (width / BASE_WIDTH_PER_ASTEROID_PER_SECOND) * density * amount;
        float distFromLine = random.nextFloat() * (width / 2f) * (random.nextBoolean() ? -1 : 1);

        float normalDistributionFactor = Math.abs(TrigHelper.getNormalDistributionCurve(Math.abs(distFromLine) / (width / 2), NORMAL_DIST_MAGIC_NUMBERS[0], NORMAL_DIST_MAGIC_NUMBERS[1]));

        float runTimeDist = timePassed / runtime;
        runTimeDist = runTimeDist < 0.5f ? runTimeDist * 2f : (1f - runTimeDist) * 2f;  //if < 0.5 we want it to be from 0 to 1, if > 0.5 we want 1 to 0
        float normalDistFactorOverTime = 1 - Math.abs(TrigHelper.getNormalDistributionCurve(runTimeDist, WEIGHT_OVER_TIME_MAGIC_NUMBERS[0], WEIGHT_OVER_TIME_MAGIC_NUMBERS[1]));

        float chance = baseChance * normalDistributionFactor * normalDistFactorOverTime;
        float roll = random.nextFloat();

        //ModPlugin.log("timePassed " + timePassed + "runtime " + runtime + " runtimeDist" + runTimeDist + " normalDistributionFactor " + normalDistributionFactor + " normalDistFactorOverTime " + normalDistFactorOverTime);
        //ModPlugin.log("Meteor Spawner Spawn Chance: " + chance + " rolled " + roll + " spawn " + (roll < chance));

        float size = (MeteorEntity.MAX_SIZE * normalDistributionFactor * normalDistFactorOverTime * 0.5f) + 0.5f * random.nextFloat();
        float speed = (MeteorEntity.BASE_SPEED * 0.4f) + 0.6f * MeteorEntity.BASE_SPEED * random.nextFloat();

        //treasuroid
        if (timePassed / runtime >= MIN_TREASURE_SPAWN_FRACT && timePassed / runtime <= MAX_TREASURE_SPAWN_FRACT){
            int treasureAmt = Math.max(1, Math.round(intensity));

            if (treasureSpawned < treasureAmt){
                treasureInterval.advance(amount);

                if (treasureInterval.intervalElapsed()){
                    TreasuroidEntity.MeteorData data = new MeteorEntity.MeteorData(Math.max(MeteorEntity.MAX_SIZE * 0.4f, size* 1.5f), arc.getModifiedRadiusArc(arc.radius + distFromLine * 0.5f), speed);
                    TreasuroidEntity.spawn(system, data);
                    treasureSpawned++;

                    return;
                }
            }
        }

        //Default
        if (roll < chance) {
            MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(size, arc.getModifiedRadiusArc(arc.radius + distFromLine), speed);
            MeteorEntity.spawn(system, data);
        }
    }
}
