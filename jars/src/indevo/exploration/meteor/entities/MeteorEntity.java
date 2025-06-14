package indevo.exploration.meteor.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;
import indevo.exploration.meteor.renderers.MeteorDebrisRenderer;
import indevo.exploration.meteor.scripts.MeteorImpact;
import indevo.items.consumables.fleet.MissileMemFlags;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class MeteorEntity extends BaseCustomEntityPlugin {
    public static final float MAX_ROTATION_PER_SEC = 7f;
    public static final float MAX_SIZE = 200f;
    public static final float MIN_SIZE = 15f;
    public static final float BASE_SPEED = 600f;

    public float size, angle, angleRotation;
    public boolean colliding = false;
    public MeteorMovementModuleAPI movement;

    public void init(){
        angleRotation = 0.1f + MAX_ROTATION_PER_SEC - (MAX_ROTATION_PER_SEC * (entity.getRadius() / MAX_SIZE));
        if (Misc.random.nextBoolean()) angleRotation *= -1;
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
                LocationAPI loc = entity.getContainingLocation();
                List<SectorEntityToken> collisionRelevantEntities = new ArrayList<>();
                collisionRelevantEntities.addAll(loc.getEntitiesWithTag(Tags.STATION));
                collisionRelevantEntities.addAll(loc.getFleets());
                collisionRelevantEntities.removeAll(loc.getEntitiesWithTag(Tags.SALVAGEABLE));

                for (SectorEntityToken t : collisionRelevantEntities) {
                    if (isInCollisionRange(t)) {
                        setCollidingAndFade(t);
                    }
                }
            }
        }

        if (movement.isMovementFinished()) Misc.fadeAndExpire(entity, 1f);
    }

    public boolean isInCollisionRange(SectorEntityToken t){
        return Misc.getDistance(t.getLocation(), entity.getLocation()) < size;
    }

    public void setCollidingAndFade(SectorEntityToken t){
        colliding = true;
        t.addScript(new MeteorImpact(t, entity, true));
        LunaCampaignRenderer.addRenderer(new MeteorDebrisRenderer(t, entity));
        Misc.fadeAndExpire(entity, 0.1f);
    }
}
