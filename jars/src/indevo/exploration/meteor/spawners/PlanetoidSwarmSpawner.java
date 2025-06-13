package indevo.exploration.meteor.spawners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.AccretionDiskGenPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.TerrainGenDataSpec;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.exploration.meteor.movement.ExternalOrbitMovement;
import indevo.exploration.meteor.scripts.MovementModuleRunner;
import indevo.utils.ModPlugin;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.MiscIE;

import java.awt.*;
import java.util.Collection;

import static com.fs.starfarer.api.impl.campaign.enc.AbyssalRogueStellarObjectEPEC.PLANETOID_TYPES;

public class PlanetoidSwarmSpawner extends BaseArcingSwarmSpawner{

    public static final float PLANETOID_MIN_SIZE = 100f;
    public static final float PLANETOID_MAX_SIZE = 200f;
    public static final float PLANETAR_BASE_SPEED = 400f;

    public static final float INNER_RADIUS = 600f;
    public static final float BASE_METEOR_FIELD_WIDTH = 6000f;
    public static final float MAGIC_METEOR_NUMBER = 0.00015f;

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
    void advanceSpawner(float amount) {

        if (spawned) return;

        //spawn the entire thing at once fuck it
        //rotation faster towards center
        //slower on the outside

        //tail (maybe?)
        //zero rotation outer circle so the trail doesn't look funky
        //clear asteroids spawned in an arc around the planet
        //non-movement roids get arcing movement, the others orbit movement

        String type = PLANETOID_TYPES.pick(random);
        String name = Misc.genEntityCatalogId(null, -1, -1, -1, Misc.CatalogEntryType.PLANET);;
        float size = MiscIE.getRandomInRange(PLANETOID_MIN_SIZE, PLANETOID_MAX_SIZE, random);

        PlanetAPI planet = system.addPlanet(Misc.genUID(), null, name, type, 0f, size, 0f, 0f);
        planet.setOrbit(null);

        float speed = PLANETAR_BASE_SPEED * 0.4f + 0.6f * PLANETAR_BASE_SPEED * random.nextFloat();
        ArcingMovementModule movement = new ArcingMovementModule(arc, speed);
        planet.addScript(new MovementModuleRunner(movement, planet));
        system.addRingBand(planet, "misc", "rings_dust0", 256f, 2, Color.white, 256f, size + 100f, 31f);
        system.addRingBand(planet, "misc", "rings_dust0", 256f, 3, Color.white, 256f, size + 300, 10f);

        float width = BASE_METEOR_FIELD_WIDTH * 0.5f + BASE_METEOR_FIELD_WIDTH * 0.5f * density;

        //we rotate the spawning stick around the planet and spawn one with size and chance depending on skewed density curve, speed depending on distance to center
        for (int i = 0; i <= 360; i++){
            int asteroidsPerDegree = Math.round(width * density * MAGIC_METEOR_NUMBER);

            //ModPlugin.log("Angle: " + i + " Spawning " + asteroidsPerDegree);

            for (int j = 0; j <= asteroidsPerDegree; j++){
                float distance = MiscIE.getRandomInRange(INNER_RADIUS + size, width + INNER_RADIUS, random);
                float factor = (distance - INNER_RADIUS - size) / width;
                float speedFactor = 1 - factor;
                float sizeFactor = leftSkewedDensity(factor);

                //wow this is trash code
                float roidSize =  Math.max(5f, (MeteorEntity.MAX_SIZE * sizeFactor * 0.3f) + 0.7f * random.nextFloat());
                float roidSpeed = (MeteorEntity.BASE_SPEED * 0.5f * speedFactor) + 0.5f * MeteorEntity.BASE_SPEED * random.nextFloat();

                float unitsPerDay = roidSpeed * Global.getSector().getClock().getSecondsPerDay();
                float circumference = (float) (2 * Math.PI * distance);
                float days = circumference / unitsPerDay;

                MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(roidSize, new ExternalOrbitMovement(arc));
                SectorEntityToken meteor = MeteorFactory.spawn(system, data, MeteorSwarmManager.MeteroidShowerType.PLANETOID);
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

    public static float leftSkewedDensity(float x) {
        float a = 2f; //magic numbers again I am on a roll
        float b = 8f;

        float y = (float) (Math.pow(x, a - 1) * Math.pow(1 - x, b - 1));
        float maxY = (float) (Math.pow((a - 1) / (a + b - 2), a - 1) * Math.pow(1 - (a - 1) / (a + b - 2), b - 1));
        return y / maxY;
    }
}
