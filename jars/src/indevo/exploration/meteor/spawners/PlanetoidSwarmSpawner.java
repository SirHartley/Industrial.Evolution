package indevo.exploration.meteor.spawners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.exploration.meteor.movement.ExternalOrbitMovement;
import indevo.exploration.meteor.scripts.DespawningMovementModuleRunner;
import indevo.exploration.meteor.terrain.RadioactiveTerrain;
import indevo.ids.Ids;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.enc.AbyssalRogueStellarObjectEPEC.PLANETOID_TYPES;

public class PlanetoidSwarmSpawner extends BaseArcingSwarmSpawner{

    public static final float PLANETOID_MIN_SIZE = 100f;
    public static final float PLANETOID_MAX_SIZE = 200f;
    public static final float PLANETAR_BASE_SPEED = 250f;

    public static final float INNER_RADIUS = 600f;
    public static final float BASE_METEOR_FIELD_WIDTH = 4000f;
    public static final float ASTEROIDS_PER_ANGLE = 1.5f;

    public static final float MAX_IRRADOID_CHANCE = 0.5f;
    public static final float MIN_DENSITY_FOR_RADOID_SPAWN = 1.5f;

    public float density;
    public float lootAmt;

    public boolean spawned = false;

    public PlanetoidSwarmSpawner(StarSystemAPI system, CircularArc arc, float runtime, long seed,
                                 float density, float lootAmt) {
        super(system, arc, runtime, seed);

        this.density = density;
        this.lootAmt = lootAmt;
    }

    @Override
    public void init() {
        super.init();
        RadioactiveTerrain.addToSystem(system);
    }

    @Override
    void advanceSpawner(float amount) {

        if (spawned) return;

        //spawn the entire thing at once fuck it
        //rotation faster towards center
        //slower on the outside

        //tail (maybe?)
        //zero rotation outer circle so the trail doesn't look funky
        //clear asteroids spawned in an arc around the planet
        //non-movement roids get arcing movement, the others orbit movement

        //basic
        float width = BASE_METEOR_FIELD_WIDTH * 0.5f + BASE_METEOR_FIELD_WIDTH * 0.5f * density;

        //planet
        String type = PLANETOID_TYPES.pick(random);
        String name = Misc.genEntityCatalogId(null, -1, -1, -1, Misc.CatalogEntryType.PLANET);;
        float size = MiscIE.getRandomInRange(PLANETOID_MIN_SIZE, PLANETOID_MAX_SIZE, random);

        PlanetAPI planet = system.addPlanet(Misc.genUID(), null, name, type, 0f, size, 0f, 0f);
        planet.setOrbit(null);
        planet.addTag(Tags.NOT_RANDOM_MISSION_TARGET);
        planet.addTag(Tags.TEMPORARY_LOCATION);
        planet.addTag(Tags.EXPIRES);
        planet.addTag("indevo_planetoid");

        MarketAPI market = planet.getMarket();
        market.addCondition(Conditions.RUINS_VAST);
        market.addCondition(Conditions.VERY_COLD);
        if (density > MIN_DENSITY_FOR_RADOID_SPAWN) market.addCondition(Conditions.IRRADIATED);
        market.addCondition(Conditions.NO_ATMOSPHERE);

        //rings
        system.addRingBand(planet, "misc", "rings_dust0", 256f, 2, Color.white, 256f, size + 200f, 31f);
        system.addRingBand(planet, "misc", "rings_dust0", 256f, 3, Color.white, 256f, size + 400f, 10f);
        system.addRingBand(planet, "misc", "rings_dust0", 256f, 4, Color.white, 256f, size + 800f, 10f);
        system.addRingBand(planet, "misc", "rings_dust0", 256f, 1, Color.white, 256f, size + 1200f, 10f);
        system.addRingBand(planet, "misc", "rings_dust0", 256f, 1, Color.white, 256f, size + 1400f, 10f);

        //planet movement
        float speed = PLANETAR_BASE_SPEED * 0.4f + 0.6f * PLANETAR_BASE_SPEED * random.nextFloat();
        ArcingMovementModule movement = new ArcingMovementModule(arc, speed);
        planet.addScript(new DespawningMovementModuleRunner(movement, planet));

        //loot station
        if (lootAmt > 1.6) addStation(planet, width);
        if(lootAmt > 2.1) addStation(planet, width);

        //roid field
        //we rotate the spawning stick around the planet and spawn one with size and chance depending on skewed density curve, speed depending on distance to center
        for (int i = 0; i <= 360; i++){

            //this is handling for when I manually change the density
            float baseAmt = ASTEROIDS_PER_ANGLE * density ;
            int amt = (int) Math.floor(baseAmt);
            float extraChance = baseAmt - (float) Math.floor(baseAmt);
            if (random.nextFloat() < extraChance) amt++;

            if (amt == 0) continue;

            for (int j = 0; j <= amt; j++){
                float distance = MiscIE.getRandomInRange(INNER_RADIUS + size, width + INNER_RADIUS, random);
                float factor = (distance - INNER_RADIUS - size) / width;
                float speedFactor = 1 - factor;
                float sizeFactor = leftSkewedDensity(factor);

                //wow this is trash code
                float roidSize =  Math.max(MeteorEntity.MIN_SIZE, (MeteorEntity.MAX_SIZE * sizeFactor * 0.3f) + 0.7f * random.nextFloat());
                float roidSpeed = (MeteorEntity.BASE_SPEED * 0.5f * speedFactor) + 0.5f * MeteorEntity.BASE_SPEED * random.nextFloat();

                float unitsPerDay = roidSpeed * Global.getSector().getClock().getSecondsPerDay();
                float circumference = (float) (2 * Math.PI * distance);
                float days = circumference / unitsPerDay;

                MeteorSwarmManager.MeteroidShowerType meteorType = MeteorSwarmManager.MeteroidShowerType.ASTEROID;
                //if above certain density replace some of the default roids with rads
                if (density > MIN_DENSITY_FOR_RADOID_SPAWN){
                    float chance = ((density - MIN_DENSITY_FOR_RADOID_SPAWN) / (MeteorSwarmManager.MAX_DENSITY - MIN_DENSITY_FOR_RADOID_SPAWN)) * MAX_IRRADOID_CHANCE; //do not question the ancient one

                    if (random.nextFloat() < chance) meteorType = MeteorSwarmManager.MeteroidShowerType.IRRADIOID;
                }

                MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(roidSize, new ExternalOrbitMovement(arc));
                SectorEntityToken meteor = MeteorFactory.spawn(system, data, meteorType);
                meteor.setCircularOrbit(planet, i, distance, days);
            }
        }

        spawned = true;

        //todo loot stations and planet despawn

        //lootAmt 1 = ruins
        //lootAmt 2 = ruins + mining
        //lootAmt 3 = ruins + habitat
        //lootAmt 4 = ruins + arsenal
    }

    public void addStation(SectorEntityToken planet, float width){
        float stationPos = MiscIE.getRandomInRange(0.1f, 0.3f, random);
        float dist = INNER_RADIUS + width * stationPos;
        float stationSpeedFact = 1 - ((dist - INNER_RADIUS - planet.getRadius()) / width);
        float stationSpeed = (MeteorEntity.BASE_SPEED * 0.5f * stationSpeedFact) + 0.5f * MeteorEntity.BASE_SPEED * random.nextFloat();
        float unitsPerDay = stationSpeed * Global.getSector().getClock().getSecondsPerDay();
        float circumference = (float) (2 * Math.PI * dist);
        float days = circumference / unitsPerDay;

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        picker.add(Entities.ORBITAL_HABITAT);
        picker.add(Entities.STATION_MINING);
        picker.add(Ids.ARSENAL_ENTITY);
        if (Settings.getBoolean(Settings.PETS)) picker.add(Ids.ABANDONED_PETSHOP_ENTITY);

        String stationType = picker.pick();
        SectorEntityToken station = planet.getContainingLocation().addCustomEntity(planet.getId() + "_loot", "Dust-covered " +
                Global.getSettings().getCustomEntitySpec(stationType).getDefaultName(), stationType, Factions.NEUTRAL);

        station.setCircularOrbit(planet, MathUtils.getRandomNumberInRange(0,360), dist, days);
        station.setDiscoverable(true);
        station.setSensorProfile(1000f);

        station.addScript(new DespawningMovementModuleRunner(new ExternalOrbitMovement(arc), station));
    }

    public static float leftSkewedDensity(float x) {
        float a = 2f; //magic numbers again I am on a roll
        float b = 6f;

        float y = (float) (Math.pow(x, a - 1) * Math.pow(1 - x, b - 1));
        float maxY = (float) (Math.pow((a - 1) / (a + b - 2), a - 1) * Math.pow(1 - (a - 1) / (a + b - 2), b - 1));
        return y / maxY;
    }
}
