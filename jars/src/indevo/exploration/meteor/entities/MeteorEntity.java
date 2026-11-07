package indevo.exploration.meteor.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;
import indevo.exploration.meteor.renderers.MeteorDebrisRenderer;
import indevo.exploration.meteor.scripts.MeteorImpact;
import indevo.items.consumables.fleet.MissileMemFlags;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MeteorEntity extends BaseCustomEntityPlugin {
    public static final float MAX_ROTATION_PER_SEC = 7f;
    public static final float MAX_SIZE = 200f;
    public static final float MIN_SIZE = 20F;
    public static final float BASE_SPEED = 500f;

    public float size, angle, angleRotation;
    public boolean colliding = false;
    public MeteorMovementModuleAPI movement;

    public void init(){
        angleRotation = 0.1f + MAX_ROTATION_PER_SEC - (MAX_ROTATION_PER_SEC * (entity.getRadius() / MAX_SIZE));
        if (Misc.random.nextBoolean()) angleRotation *= -1;
    }

    //weak so a static can't keep the old sector alive after a load
    private static WeakReference<CollisionTargets> collisionTargets = new WeakReference<>(null);

    private static class CollisionTargets {
        final LocationAPI loc;
        final long timestamp;
        final List<SectorEntityToken> entities = new ArrayList<>();

        CollisionTargets(LocationAPI loc, long timestamp) {
            this.loc = loc;
            this.timestamp = timestamp;

            entities.addAll(loc.getEntitiesWithTag(Tags.STATION));
            entities.addAll(loc.getFleets());
            entities.removeAll(loc.getEntitiesWithTag(Tags.SALVAGEABLE));
        }
    }

    public static class MeteorData{
        public MeteorMovementModuleAPI movement;
        public float size;

        public MeteorData(float size, MeteorMovementModuleAPI movement) {
            this.size = size;
            this.movement = movement;
        }
    }

    public void setMovement(MeteorMovementModuleAPI movement) {
        this.movement = movement;
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        entity.addTag(MeteorSwarmManager.METEOR_TAG);
        if (pluginParams == null) return;

        MeteorData data = (MeteorData) pluginParams;
        this.size = data.size;
        this.movement = data.movement;
        //entity.setSensorProfile(SENSOR_PROFILE);

        angleRotation = (float) (0.5f + (MAX_ROTATION_PER_SEC * Math.random()) - (MAX_ROTATION_PER_SEC * (size / MAX_SIZE)));
        if (Misc.random.nextBoolean()) angleRotation *= -1;

        movement.init(entity);
    }

    @Override
    public void advance(float amount) {
        entity.setFacing(entity.getFacing() + angleRotation * amount);
        movement.advance(amount);

        if (!colliding && !entity.hasTag(Tags.FADING_OUT_AND_EXPIRING)) {

            if (entity.getMemoryWithoutUpdate().contains(MissileMemFlags.MEM_CAUGHT_BY_MISSILE)){
                Vector2f missileLoc = entity.getMemoryWithoutUpdate().getVector2f(MissileMemFlags.MEM_CAUGHT_BY_MISSILE);
                Vector2f asteroidLoc = entity.getLocation();

                SectorEntityToken t = entity.getContainingLocation().addCustomEntity(Misc.genUID(), null, "IndEvo_token", null);
                t.setLocation(missileLoc.x, missileLoc.y);
                t.setFacing(Misc.getAngleInDegrees(missileLoc, asteroidLoc));

                setCollidingAndFade(t);
                Misc.fadeAndExpire(t, 1f);

                //addspecial IndEvo_consumable_missile_concussive
            }

            //disable collision checks if out of sensor range
            if (Global.getSector().getViewport().isNearViewport(entity.getLocation(), 1000f)){
                for (SectorEntityToken t : getCollisionRelevantEntities(entity.getContainingLocation())) {
                    if (isInCollisionRange(t)) {
                        setCollidingAndFade(t);
                    }
                }
            }
        }

        if (movement.isMovementFinished()) Misc.fadeAndExpire(entity, 1f);
    }

    //a tag lookup walks every entity in the location, swarms have thousands
    private static List<SectorEntityToken> getCollisionRelevantEntities(LocationAPI loc) {
        long timestamp = Global.getSector().getClock().getTimestamp();
        CollisionTargets targets = collisionTargets.get();

        if (targets == null || targets.loc != loc || targets.timestamp != timestamp) {
            targets = new CollisionTargets(loc, timestamp);
            collisionTargets = new WeakReference<>(targets);
        }

        return targets.entities;
    }

    public boolean isInCollisionRange(SectorEntityToken t){
        return Misc.getDistance(t.getLocation(), entity.getLocation()) < entity.getRadius();
    }

    public void setCollidingAndFade(SectorEntityToken t){
        colliding = true;
        t.addScript(new MeteorImpact(t, entity, true));
        LunaCampaignRenderer.addTransientRenderer(new MeteorDebrisRenderer(t, entity));
        Misc.fadeAndExpire(entity, 0.1f);
    }
}
