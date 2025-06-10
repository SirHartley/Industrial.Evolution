package indevo.exploration.meteor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.OrbitFocus;
import indevo.items.consumables.fleet.MissileMemFlags;
import indevo.utils.helper.CircularArc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class MeteorEntity extends BaseCustomEntityPlugin {
    public static final float MAX_ROTATION_PER_SEC = 4f;
    public static final float MAX_SIZE = 300f;
    public static final float BASE_SPEED = 700f;

    public float size, angle, angleRotation;
    public boolean colliding = false;

    public void init(){
        angleRotation = 0.1f + MAX_ROTATION_PER_SEC - (MAX_ROTATION_PER_SEC * (entity.getRadius() / MAX_SIZE));
        if (Misc.random.nextBoolean()) angleRotation *= -1;
    }

    private CircularArc arc;
    private float velocity;

    private float currentAngle;

    public static class MeteorData{
        public CircularArc arc;
        public float velocity;
        public float size;

        public MeteorData(float size, CircularArc arc, float velocity) {
            this.size = size;
            this.arc = arc;
            this.velocity = velocity;
        }
    }

    public static void spawn(LocationAPI loc, MeteorData data){
        loc.addCustomEntity(Misc.genUID(), null, MeteorFactory.getMeteorForSize(data.size), Factions.NEUTRAL, data.size * 1.2f, data.size, data.size, data);
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        if (pluginParams == null) return;

        MeteorData data = (MeteorData) pluginParams;
        this.arc = data.arc;
        this.velocity = data.velocity;
        this.size = data.size;

        currentAngle = arc.startAngle;

        angleRotation = (float) (0.5f + (MAX_ROTATION_PER_SEC * Math.random()) - (MAX_ROTATION_PER_SEC * (size / MAX_SIZE)));
        if (Misc.random.nextBoolean()) angleRotation *= -1;

        updatePositionAndVelocity();
    }

    @Override
    public void advance(float amount) {
        if (!colliding && !entity.hasTag(Tags.FADING_OUT_AND_EXPIRING)) {
            if (entity.getMemoryWithoutUpdate().contains(MissileMemFlags.MEM_CAUGHT_BY_MISSILE)){
                Vector2f missileLoc = entity.getMemoryWithoutUpdate().getVector2f(MissileMemFlags.MEM_CAUGHT_BY_MISSILE);
                Vector2f asteroidLoc = entity.getLocation();

                SectorEntityToken t = entity.getContainingLocation().addCustomEntity(Misc.genUID(), null, "SplinterFleet_OrbitFocus", null);
                t.setLocation(missileLoc.x, missileLoc.y);
                t.setFacing(Misc.getAngleInDegrees(missileLoc, asteroidLoc));

                setCollidingAndFade(t);
                Misc.fadeAndExpire(t, 1f);

                //addspecial IndEvo_consumable_missile_concussive
            }

            LocationAPI loc = entity.getContainingLocation();
            List<SectorEntityToken> collisionRelevantEntities = new ArrayList<>();
            collisionRelevantEntities.addAll(loc.getEntitiesWithTag(Tags.SALVAGEABLE));
            collisionRelevantEntities.addAll(loc.getEntitiesWithTag(Tags.STATION));
            collisionRelevantEntities.addAll(loc.getFleets());
            collisionRelevantEntities.removeAll(loc.getEntitiesWithTag(Tags.DEBRIS_FIELD));

            for (SectorEntityToken t : collisionRelevantEntities) {
                if (Misc.getDistance(t.getLocation(), entity.getLocation()) < size) {
                    setCollidingAndFade(t);
                }
            }
        }

        float arcLengthTraveled = velocity * amount;
        float angleDelta = (arcLengthTraveled / arc.radius) * (float) (180f / Math.PI);  // Convert radians to degrees
        int dir = arc.getAngleTravelDir();
        currentAngle += angleDelta * dir;
        currentAngle = Misc.normalizeAngle(currentAngle);

        // Clamp to endAngle if we reached the end of the arc
        if (arc.getTraversalProgress(currentAngle) >= 1f && !entity.hasTag(Tags.FADING_OUT_AND_EXPIRING)) {
            Misc.fadeAndExpire(entity, 1f);
        }

        entity.setFacing(entity.getFacing() + angleRotation * amount);
        updatePositionAndVelocity();
    }

    private void updatePositionAndVelocity() {
        Vector2f pos = arc.getPointForAngle(currentAngle);
        entity.getLocation().set(pos.x, pos.y);

        float tangentAngle = Misc.normalizeAngle(currentAngle + 90f * arc.getAngleTravelDir());
        Vector2f tangent = Misc.getUnitVectorAtDegreeAngle(tangentAngle);
        tangent.scale(velocity);
        entity.getVelocity().set(tangent);
    }

    public void setCollidingAndFade(SectorEntityToken t){
        colliding = true;
        t.addScript(new MeteorImpact(t, entity, true));
        LunaCampaignRenderer.addRenderer(new MeteorDebrisRenderer(t, entity));
        Misc.fadeAndExpire(entity, 0.1f);
    }

    public static float getBaseSpeedForSize(float size){
        return (1 - size / MAX_SIZE) * BASE_SPEED;
    }
}
