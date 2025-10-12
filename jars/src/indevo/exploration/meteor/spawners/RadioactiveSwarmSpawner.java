package indevo.exploration.meteor.spawners;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.entities.TreasuradoidEntity;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.exploration.meteor.terrain.RadioactiveTerrain;
import indevo.utils.helper.Circle;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.TrigHelper;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.List;

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

public class RadioactiveSwarmSpawner extends BaseArcingSwarmSpawner {
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
    private int treasureAmt;

    public List<Circle> blockedSpawnCircles = new ArrayList<>();

    public RadioactiveSwarmSpawner(StarSystemAPI system, float intensity, int treasureAmt, float density, float runtime, CircularArc arc, float width, long seed) {
        super(system, arc, runtime, seed);
        this.intensity = intensity;
        this.width = width;
        this.density = density;
        this.treasureAmt = treasureAmt;
        this.treasureInterval = new IntervalUtil((runtime * 0.2f) / treasureAmt, (runtime * 0.5f) / treasureAmt);
    }

    @Override
    public void init() {
        super.init();
        RadioactiveTerrain.addToSystem(system);
    }

    @Override
    public void advanceSpawner(float amount) {
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

        float size =  Math.max(MeteorEntity.MIN_SIZE,(MeteorEntity.MAX_SIZE * normalDistributionFactor * normalDistFactorOverTime * 0.5f) + 0.5f * random.nextFloat());
        float speed = (MeteorEntity.BASE_SPEED * 0.4f) + 0.6f * MeteorEntity.BASE_SPEED * random.nextFloat();

        //treasuroid
        if (timePassed / runtime >= MIN_TREASURE_SPAWN_FRACT && timePassed / runtime <= MAX_TREASURE_SPAWN_FRACT){
            int treasureAmt = Math.max(1, this.treasureAmt);

            if (treasureSpawned < treasureAmt){
                treasureInterval.advance(amount);

                if (treasureInterval.intervalElapsed()){
                    MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(TreasuradoidEntity.DEFAULT_RADIUS, new ArcingMovementModule(arc.getModifiedRadiusArc(arc.radius + distFromLine * 0.5f), speed * 0.7f));
                    SectorEntityToken meteor = TreasuradoidEntity.spawn(system, data);
                    treasureSpawned++;

                    //get any roids that swarmed before, lobotomize the arc and make em orbit the treasureoid
                    Circle effectCircle = new Circle(meteor.getLocation(), TreasuradoidEntity.ENTOURAGE_ORBIT_RADIUS);

                    //causes large empty swathes in front, is shit
                    /*for (SectorEntityToken t : system.getEntitiesWithTag(MeteorSwarmManager.METEOR_TAG)) if (MathUtils.isPointWithinCircle(t.getLocation(), effectCircle.center, effectCircle.radius)){
                        if (t == meteor) continue;

                        MeteorEntity entityPlugin = (MeteorEntity) t.getCustomPlugin();
                        MeteorMovementModuleAPI movement = entityPlugin.movement;
                        MeteorMovementModuleAPI newMovement = new ExternalOrbitMovement(movement.getArc());
                        entityPlugin.setMovement(newMovement);//lobotomize the movement

                        newMovement.init(t);

                        float distance = Misc.getDistance(t.getLocation(), meteor.getLocation());
                        float factor = distance / TreasuradoidEntity.ENTOURAGE_ORBIT_RADIUS;
                        float speedFactor = 1 - factor;
                        float roidSpeed = (MeteorEntity.BASE_SPEED * 0.5f * speedFactor) + 0.5f * MeteorEntity.BASE_SPEED * random.nextFloat();
                        float unitsPerDay = roidSpeed * Global.getSector().getClock().getSecondsPerDay();
                        float circumference = (float) (2 * Math.PI * distance);
                        float days = circumference / unitsPerDay;

                        t.setCircularOrbit(meteor, Misc.getAngleInDegrees(meteor.getLocation(), t.getLocation()), distance, days);
                    }*/

                    blockedSpawnCircles.add(effectCircle);

                    //spawn entourage
                    TreasuradoidEntity plugin = (TreasuradoidEntity) meteor.getCustomPlugin();
                    plugin.spawnEntourage(density);
                    plugin.spawnLootStation();

                    return;
                }
            }
        }

        //Default
        if (roll < chance) {
            MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(size, new ArcingMovementModule( arc.getModifiedRadiusArc(arc.radius + distFromLine), speed));

            //block out treasureoid orbits
            for (Circle c : blockedSpawnCircles) if (MathUtils.isPointWithinCircle(data.movement.getCurrentLoc(), c.center, c.radius)) return;

            if (random.nextFloat() < 0.3f) MeteorFactory.spawn(system, data, MeteorSwarmManager.MeteroidShowerType.IRRADIOID).setFaction(Factions.LUDDIC_PATH);
            else MeteorFactory.spawn(system, data, MeteorSwarmManager.MeteroidShowerType.ASTEROID);
        }
    }
}
