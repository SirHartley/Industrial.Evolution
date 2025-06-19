package indevo.exploration.meteor.spawners;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.entities.IceTreasureoidEntity;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.utils.ModPlugin;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.TrigHelper;
import org.lazywizard.lazylib.MathUtils;

public class IceSwarmSpawner extends BaseArcingSwarmSpawner{

    public static final float[] NORMAL_DIST_MAGIC_NUMBERS = {0.4f, 0};
    public static final float[] WEIGHT_OVER_TIME_MAGIC_NUMBERS = {0.4f, 0};
    public static final int BASE_WIDTH_PER_ASTEROID_PER_SECOND = 1600;

    public static final float MIN_TREASURE_SPAWN_FRACT = 0.2f;
    public static final float MAX_TREASURE_SPAWN_FRACT = 0.8f;

    public static final float MIN_TREASURE_SHIP_VALUE = 20000f;

    public float intensity; //0 to x, also sets the loot
    public float width;
    private final float density;
    private int treasureAmt;

    private int treasureSpawned = 0;
    private IntervalUtil treasureInterval;

    public IceSwarmSpawner(StarSystemAPI system, float intensity, int treasureAmt, float density, float runtime, CircularArc arc, float width, long seed) {
        super(system, arc, runtime, seed);
        this.intensity = intensity;
        this.width = width;
        this.density = density;
        this.treasureAmt = treasureAmt;
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

        float size = Math.max(MeteorEntity.MIN_SIZE, (MeteorEntity.MAX_SIZE * normalDistributionFactor * normalDistFactorOverTime * 0.5f) + 0.5f * random.nextFloat());
        float speed = (MeteorEntity.BASE_SPEED * 0.4f) + 0.6f * MeteorEntity.BASE_SPEED * random.nextFloat();

        //treasuroid
        if (timePassed / runtime >= MIN_TREASURE_SPAWN_FRACT && timePassed / runtime <= MAX_TREASURE_SPAWN_FRACT){
            int treasureAmt = Math.max(1, this.treasureAmt);

            if (treasureSpawned < treasureAmt){
                treasureInterval.advance(amount);

                if (treasureInterval.intervalElapsed()){
                    MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(MathUtils.getRandomNumberInRange(MeteorEntity.MAX_SIZE*0.7f, MeteorEntity.MAX_SIZE), new ArcingMovementModule(arc.getModifiedRadiusArc(arc.radius + distFromLine), speed));
                    ModPlugin.log("spawning ice treasuroid size " + data.size);

                    IceTreasureoidEntity.spawn(system, data, random);
                    treasureSpawned++;

                    return;
                }
            }
        }

        //Default
        if (roll < chance && random.nextBoolean()) {

            size *= 2;

            MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(size, new ArcingMovementModule( arc.getModifiedRadiusArc(arc.radius + distFromLine), speed));
            ModPlugin.log("spawning ice roid size " + size);
            MeteorFactory.spawn(system, data, MeteorSwarmManager.MeteroidShowerType.ICEROID).setFaction(Factions.TRITACHYON);
            return;
        }

        //Default
        if (roll < chance) {
            MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(size, new ArcingMovementModule( arc.getModifiedRadiusArc(arc.radius + distFromLine), speed));
            ModPlugin.log("spawning roid size " + size);
            MeteorFactory.spawn(system, data, MeteorSwarmManager.MeteroidShowerType.ASTEROID);
        }
    }
}
