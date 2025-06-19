package indevo.exploration.meteor.entities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcOnOrbitLossMovement;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;
import indevo.exploration.meteor.renderers.MeteorDebrisRenderer;
import indevo.exploration.meteor.scripts.IceroidImpact;
import indevo.utils.ModPlugin;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lazywizard.lazylib.MathUtils;

import static indevo.exploration.meteor.scripts.MeteorImpact.splitRadiusRandom;

public class IcyRockEntity extends MeteorEntity {

    public static final String TAG_NO_SPLINTERS = "IndEvo_IceRockNoSplinters";
    public static final float MIN_RADIUS_FOR_EXTRAS = 50f;
    public static final float RADIUS_PER_ASTERIOD = 50f;
    public static final float ENTOURAGE_MAX_ORBIT = 200f;
    public static final float ENTOURAGE_MAX_ORBIT_DUR = 10f;
    public static final float ENTOURAGE_MINX_ORBIT_DUR = 3f;

    public boolean spawnedSplinters = false;

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (!entity.hasTag(TAG_NO_SPLINTERS) && !spawnedSplinters && entity.getRadius() >= MIN_RADIUS_FOR_EXTRAS) spawnSplinters();
        if (entity.getOrbit() != null && entity.getOrbit().getFocus() != null && !entity.getOrbit().getFocus().isAlive()) entity.setOrbit(null);
    }

    public void spawnSplinters(){
        int numAsteroids = (int) Math.ceil(entity.getRadius() / RADIUS_PER_ASTERIOD);
        float[] asteroidSizes = splitRadiusRandom(numAsteroids, entity.getRadius());

        ModPlugin.log("spawning " + numAsteroids + " fragments");

        for (int i = 0; i < numAsteroids; i++) {
            float spawnAngle = MathUtils.getRandomNumberInRange(0,360);
            float size = asteroidSizes[i];
            float distanceFromEntity = (float) (entity.getRadius() + size + ENTOURAGE_MAX_ORBIT * Math.random());
            float orbitDur = MathUtils.getRandomNumberInRange(ENTOURAGE_MINX_ORBIT_DUR, ENTOURAGE_MAX_ORBIT_DUR);

            MeteorMovementModuleAPI newMovement = new ArcOnOrbitLossMovement(movement.getArc(), movement.getVelocity());
            SectorEntityToken asteroid = MeteorFactory.spawn(entity.getContainingLocation(), new MeteorEntity.MeteorData(size, newMovement), MeteorSwarmManager.MeteroidShowerType.ICEROID);
            asteroid.addTag("IndEvo_IceEntourage_" + entity.getId());

            asteroid.setCircularOrbit(entity, spawnAngle, distanceFromEntity, orbitDur);
        }

        spawnedSplinters = true;
    }

    @Override
    public void setCollidingAndFade(SectorEntityToken t) {
        colliding = true;

        t.addScript(new IceroidImpact(t, entity, true));
        LunaCampaignRenderer.addRenderer(new MeteorDebrisRenderer(t, entity));
        Misc.fadeAndExpire(entity, 0.1f);

        //for (SectorEntityToken entity : this.entity.getContainingLocation().getEntitiesWithTag("IndEvo_IceEntourage_" + this.entity.getId())) entity.setOrbit(null);
    }
}
