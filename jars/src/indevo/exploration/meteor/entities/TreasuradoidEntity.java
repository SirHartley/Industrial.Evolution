package indevo.exploration.meteor.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.exploration.meteor.movement.ExternalOrbitMovement;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;
import indevo.exploration.meteor.scripts.MovementModuleRunner;
import indevo.ids.Ids;
import indevo.utils.helper.Circle;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.TrigHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static com.fs.starfarer.api.util.Misc.addHitGlow;
import static com.fs.starfarer.api.util.Misc.random;
import static indevo.exploration.meteor.spawners.PlanetoidSwarmSpawner.leftSkewedDensity;

public class TreasuradoidEntity extends SpicyRockEntity{

    public static final float DEFAULT_RADIUS = 400f;
    public static final float ENTOURAGE_ORBIT_RADIUS = 1200f;
    public static final float CENTER_RADIUS = 0f;
    public static final float ENTOURAGE_IRRADIOD_SPAWN_CHANCE = 0.5f;

    public static final float CHANCE_TO_SPAWN_PER_ANGLE = 0.8f;

    public IntervalUtil sparkleInterval = new IntervalUtil(0.02f, 0.08f);

    public static SectorEntityToken spawn(LocationAPI loc, MeteorData data){
        return loc.addCustomEntity(Misc.genUID(), null, "IndEvo_spicy_rock_5", "engHubStorageColour", data.size, data.size , data.size, data);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        sparkleInterval.advance(amount);

        if (sparkleInterval.intervalElapsed()) {
            Vector2f loc = MathUtils.getRandomPointInCircle(entity.getLocation(), entity.getRadius());
            float distToCenter = Misc.getDistance( entity.getLocation(), loc);
            float fract = distToCenter / entity.getRadius();
            float dur = 1 + 3 * TrigHelper.getNormalDistributionCurve(fract, 0.4f, 0f);

            addHitGlow(entity.getContainingLocation(), loc, new Vector2f(0,0), 20f, dur, SpicyRockEntity.GLOW_COLOR_1);
        }

        //remove loot station at end of arc
        if (entity.hasTag(Tags.FADING_OUT_AND_EXPIRING) && !colliding && movement.getArc().getTraversalProgress(entity.getLocation()) >= 0.95f) {
            SectorEntityToken lootStation = entity.getContainingLocation().getEntityById(entity.getId() + "_loot");
            if (lootStation != null) Misc.fadeAndExpire(lootStation, 0f);
        }
    }

    @Override
    public boolean isInCollisionRange(SectorEntityToken t) {
        return  Misc.getDistance(t.getLocation(), entity.getLocation()) < size * 0.2f;
    }

    @Override
    public void setCollidingAndFade(SectorEntityToken t) {
        super.setCollidingAndFade(t);

        //station orbit
        SectorEntityToken lootStation = entity.getContainingLocation().getEntityById(entity.getId() + "_loot");
        if (lootStation != null) {
            lootStation.setOrbit(null);

            lootStation.setDiscoverable(true);
            lootStation.setDiscoveryXP(100f);
            lootStation.setSensorProfile(10000f);
        }

        //make the roids disperse
        String tag = "IndEvo_TreasureoidEntrouage_" + entity.getId();
        List<SectorEntityToken> entourageRoids = entity.getContainingLocation().getEntitiesWithTag(tag);

        for (SectorEntityToken roid : entourageRoids){
            Vector2f roidLoc = roid.getLocation();

            OrbitAPI orbit = roid.getOrbit();
            float circumfence = (float) (2 * Math.PI * Misc.getDistance(roidLoc, orbit.getFocus().getLocation()));
            float distPerDay = (circumfence / orbit.getOrbitalPeriod()) * 0.15f;

            float angle = Misc.getAngleInDegrees(roidLoc, orbit.getFocus().getLocation());
            float travelDir = Misc.normalizeAngle(angle + 90f);

            Vector2f secondPoint = MathUtils.getPointOnCircumference(roidLoc, 20000f, travelDir);
            Pair<Vector2f, Float> cicleData = TrigHelper.findTwoPointCircle(roidLoc, secondPoint, 30000f);
            Circle circle = new Circle(cicleData.one, cicleData.two);

            MeteorMovementModuleAPI movement = new ArcingMovementModule(new CircularArc(circle, circle.getAngleForPoint(roidLoc), circle.getAngleForPoint(secondPoint)), distPerDay);
            roid.addScript(new MovementModuleRunner(movement, roid));
            roid.setOrbit(null);

            Misc.fadeAndExpire(roid, 30f);
        }
    }

    public void spawnLootStation(){
        String type = random.nextBoolean() ? Entities.STATION_RESEARCH : Ids.LAB_ENTITY;
        SectorEntityToken station = entity.getContainingLocation().addCustomEntity(entity.getId() + "_loot", "Shielded " + Global.getSettings().getCustomEntitySpec(type).getDefaultName(), type, Factions.NEUTRAL);

        float roidSpeed = MeteorEntity.BASE_SPEED * 0.1f * random.nextFloat();
        float unitsPerDay = roidSpeed * Global.getSector().getClock().getSecondsPerDay();
        float circumference = (float) (2 * Math.PI * (size / 2f));
        float days = circumference / unitsPerDay;

        station.setCircularOrbit(entity, MathUtils.getRandomNumberInRange(0,360), size / 2f, - days);
    }

    public void spawnEntourage(float density) {
        //spawn station
        SectorEntityToken orbitFocus = entity.getContainingLocation().addCustomEntity(Misc.genUID(), null, "IndEvo_token", Factions.PLAYER);
        orbitFocus.addScript(new MovementModuleRunner(new ArcingMovementModule(movement.getArc(), movement.getVelocity()), orbitFocus));

        for (int i = 0; i <= 360; i++){
            if (random.nextFloat() > CHANCE_TO_SPAWN_PER_ANGLE) continue;

            float distance = MiscIE.getRandomInRange(CENTER_RADIUS + size, ENTOURAGE_ORBIT_RADIUS + CENTER_RADIUS, random);
            float factor = (distance - CENTER_RADIUS - size) / ENTOURAGE_ORBIT_RADIUS;
            float speedFactor = 1 - factor;
            float sizeFactor = leftSkewedDensity(factor);

            //more trash code
            float roidSize =  Math.max(20f, (MeteorEntity.MAX_SIZE * sizeFactor * 0.3f) + 0.7f * random.nextFloat());
            float roidSpeed = (MeteorEntity.BASE_SPEED * 0.5f * speedFactor) + 0.5f * MeteorEntity.BASE_SPEED * random.nextFloat();
            roidSpeed *= 0.5f; //too fast

            float unitsPerDay = roidSpeed * Global.getSector().getClock().getSecondsPerDay();
            float circumference = (float) (2 * Math.PI * distance);
            float days = circumference / unitsPerDay;

            MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(roidSize, new ExternalOrbitMovement(movement.getArc()));
            SectorEntityToken meteor = MeteorFactory.spawn(entity.getContainingLocation(), data,
                    random.nextFloat() < ENTOURAGE_IRRADIOD_SPAWN_CHANCE ? MeteorSwarmManager.MeteroidShowerType.IRRADIOID : MeteorSwarmManager.MeteroidShowerType.ASTEROID);

            meteor.setCircularOrbit(orbitFocus, i, distance, days);
            meteor.addTag("IndEvo_TreasureoidEntrouage_" + entity.getId());
        }
    }
}
