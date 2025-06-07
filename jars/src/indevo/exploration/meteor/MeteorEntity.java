package indevo.exploration.meteor;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class MeteorEntity extends BaseCustomEntityPlugin {
    public static final float MAX_ROTATION_PER_SEC = 4f;
    public static final float MAX_SIZE = 150f;

    public float size, angle, angleRotation;
    public boolean colliding = false;

    public void init(){
        angleRotation = 0.1f + MAX_ROTATION_PER_SEC - (MAX_ROTATION_PER_SEC * (entity.getRadius() / MAX_SIZE));
        if (Misc.random.nextBoolean()) angleRotation *= -1;
    }

    private Vector2f center;
    private float radius;
    private float startAngle;
    private float endAngle;
    private float velocity;

    private float currentAngle;

    public static class MeteorData{
        public Vector2f center;
        public float pathRadius;
        public float startAngle;
        public float endAngle;
        public float velocity;
        public float size;

        public MeteorData(float size, Vector2f center, float pathRadius, float startAngle, float endAngle, float velocity) {
            this.size = size;
            this.center = center;
            this.pathRadius = pathRadius;
            this.startAngle = startAngle;
            this.endAngle = endAngle;
            this.velocity = velocity;
        }
    }

    public static void spawn(LocationAPI loc, MeteorData data){
        loc.addCustomEntity(Misc.genUID(), "Meteor", "IndEvo_meteor", null, data.size * 1.2f, data.size, data.size, data);
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        if (pluginParams == null) return;

        MeteorData data = (MeteorData) pluginParams;

        this.center = data.center;
        this.radius = data.pathRadius;
        this.startAngle = data.startAngle;
        this.endAngle = data.endAngle;
        this.velocity = data.velocity;
        this.size = data.size;

        currentAngle = startAngle;
        updatePositionAndVelocity();
    }

    @Override
    public void advance(float amount) {
        //check for collision, impact, break it up into pieces dep. on size
        //get fleets, stations, custom entities
        //if in range apply impact script, fade out meteor, reverse vel
        //spawn some dust clouds to cover up fade

        if (!colliding){
            LocationAPI loc = entity.getContainingLocation();
            List<SectorEntityToken> collisionRelevantEntities = new ArrayList<>();
            collisionRelevantEntities.addAll(loc.getEntitiesWithTag(Tags.SALVAGEABLE));
            collisionRelevantEntities.addAll(loc.getEntitiesWithTag(Tags.STATION));
            collisionRelevantEntities.addAll(loc.getFleets());
            collisionRelevantEntities.removeAll(loc.getEntitiesWithTag(Tags.DEBRIS_FIELD)); //may be added through salvageable tag

            for (SectorEntityToken t : collisionRelevantEntities) if (Misc.getDistance(t.getLocation(), entity.getLocation()) < size){
                colliding = true;
                t.addScript(new MeteorImpact(t, entity, true));
                LunaCampaignRenderer.addRenderer(new MeteorDebrisRenderer(t, entity));
                Misc.fadeAndExpire(entity, 0.1f);
            }
        }

        float arcLengthTraveled = velocity * amount;
        float angleDelta = arcLengthTraveled / radius;

        entity.setFacing(entity.getFacing() + angleRotation * amount);
        currentAngle += angleDelta;

        // Clamp angle so we don't overshoot & fade when we're done
        if (velocity > 0) {
            if (currentAngle > endAngle) {
                currentAngle = endAngle;
                Misc.fadeAndExpire(entity, 10f);
            }
        } else {
            if (currentAngle < endAngle) {
                currentAngle = endAngle;
                Misc.fadeAndExpire(entity, 10f);
            }
        }

        updatePositionAndVelocity();
    }

    private void updatePositionAndVelocity() {
        float x = center.x + radius * (float)Math.cos(currentAngle);
        float y = center.y + radius * (float)Math.sin(currentAngle);
        entity.getLocation().set(x, y);

        // Compute tangent direction
        float dx = -(float)Math.sin(currentAngle);
        float dy = (float)Math.cos(currentAngle);
        Vector2f tangent = new Vector2f(dx, dy);
        tangent.normalise();
        tangent.scale(velocity);

        entity.getVelocity().set(tangent);
    }
}
