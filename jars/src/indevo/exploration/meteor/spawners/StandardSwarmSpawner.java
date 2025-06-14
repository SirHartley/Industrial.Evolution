package indevo.exploration.meteor.spawners;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.entities.TreasuroidEntity;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.TrigHelper;

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

public class StandardSwarmSpawner extends BaseArcingSwarmSpawner {
    public static final float[] NORMAL_DIST_MAGIC_NUMBERS = {0.4f, 0}; //sets the distribution https://www.desmos.com/calculator/11rldprhvd
    public static final float[] WEIGHT_OVER_TIME_MAGIC_NUMBERS = {0.4f, 0};
    public static final int BASE_WIDTH_PER_ASTEROID_PER_SECOND = 700; //a good width is around 4k

    public static final float MIN_TREASURE_SPAWN_FRACT = 0.2f;
    public static final float MAX_TREASURE_SPAWN_FRACT = 0.8f;

    public float intensity; //0 to x, also sets the loot
    public float width;
    private final float density;

    private int treasureSpawned = 0;
    private IntervalUtil treasureInterval;

    public StandardSwarmSpawner(StarSystemAPI system, float intensity, int treasureAmt, float density, float runtime, CircularArc arc, float width, long seed) {
        super(system, arc, runtime, seed);
        this.intensity = intensity;
        this.width = width;
        this.density = density;
        this.treasureInterval = new IntervalUtil((runtime * 0.2f) / treasureAmt, (runtime * 0.5f) / treasureAmt);
    }

    @Override
    void advanceSpawner(float amount) {
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

        float size = Math.max(MeteorEntity.MIN_SIZE, (MeteorEntity.MAX_SIZE * normalDistributionFactor * normalDistFactorOverTime * 0.5f) + 0.5f * random.nextFloat());
        float speed = (MeteorEntity.BASE_SPEED * 0.4f) + 0.6f * MeteorEntity.BASE_SPEED * random.nextFloat();

        //treasuroid
        if (timePassed / runtime >= MIN_TREASURE_SPAWN_FRACT && timePassed / runtime <= MAX_TREASURE_SPAWN_FRACT){
            int treasureAmt = Math.max(1, Math.round(intensity));

            if (treasureSpawned < treasureAmt){
                treasureInterval.advance(amount);

                if (treasureInterval.intervalElapsed()){
                    MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(Math.max(MeteorEntity.MAX_SIZE * 0.4f, size* 1.5f), new ArcingMovementModule(arc.getModifiedRadiusArc(arc.radius + distFromLine * 0.5f), speed));
                    TreasuroidEntity.spawn(system, data);
                    treasureSpawned++;

                    return;
                }
            }
        }

        //Default
        if (roll < chance) {
            MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(size, new ArcingMovementModule( arc.getModifiedRadiusArc(arc.radius + distFromLine), speed));
            MeteorFactory.spawn(system, data, MeteorSwarmManager.MeteroidShowerType.ASTEROID);
        }
    }
}
