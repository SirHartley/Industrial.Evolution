package indevo.WIP.mobilecolony.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.utils.ModPlugin;
import indevo.utils.trails.MagicCampaignTrailPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class MobileColonyEntityPlugin extends BaseCustomEntityPlugin {

    public static final float PROJECTILE_VELOCITY = 100f;
    public static final float MIN_FOLLOW_DISTANCE = 200f;
    public static final float MAX_ANGLE_TURN_DIST_PER_SECOND = 20f; //was 40f

    //trail
    public List<Trail> trailList = new ArrayList<>();

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        trailList.add(new Trail(30,40,12,2.5f,new Color(255, 200, 50, 255)));
        trailList.add(new Trail(20,57,18,3.5f,new Color(255, 150, 50, 255)));
        trailList.add(new Trail(5,63,25,5f,new Color(255, 100, 50, 255)));
        trailList.add(new Trail(-5,63,25,5f,new Color(255, 100, 50, 255)));
        trailList.add(new Trail(-20,57,18,3.5f,new Color(255, 150, 50, 255)));
        trailList.add(new Trail(-30,40,12,2.5f,new Color(255, 200, 50, 255)));
    }

    public void advance(float amount) {
        if (entity.isInCurrentLocation()) {
            advanceEntityTowardsTarget(amount);
            for (Trail trail : trailList) trail.render();
        }
    }

    public void advanceEntityTowardsTarget(float amount) {
        //This would be two lines with vectors, but I think I'd rather stick my head in hot tar

        CampaignFleetAPI currentTarget = Global.getSector().getPlayerFleet();
        float distToTarget = Misc.getDistance(entity, currentTarget);

        // TODO: 05/03/2023 make this nicer to come to a slow stop
        if (currentTarget.getInteractionTarget() != null && entity.getId().equals(currentTarget.getInteractionTarget().getId())) return; //we stop if player is approaching us

        float moveDist = distToTarget > MIN_FOLLOW_DISTANCE * 2 ? PROJECTILE_VELOCITY * amount : Math.min(PROJECTILE_VELOCITY, currentTarget.getVelocity().length()) * amount; //if we are far enough away, full steam, otherwise we follow go at the speed of the target if slower
        float turn = MAX_ANGLE_TURN_DIST_PER_SECOND * amount; //adjust turn for speed
        float moveAngle = entity.getFacing();
        float targetAngle = Misc.getAngleInDegrees(entity.getLocation(), currentTarget.getLocation());
        float angleDiff = Misc.getAngleDiff(targetAngle, moveAngle);

        if (angleDiff < turn) turn = angleDiff;

        float nextAngle = entity.getFacing() + turn * Misc.getClosestTurnDirection(entity.getFacing(), entity.getLocation(), currentTarget.getLocation());

        //ModPlugin.log("current angle " + moveAngle + " current diff " + angleDiff + " TURN " + turn + " next angle " + nextAngle);

        Vector2f nextPos = MathUtils.getPointOnCircumference(entity.getLocation(), moveDist, nextAngle);
        entity.setLocation(nextPos.x, nextPos.y);
        entity.setFacing(nextAngle);
    }

    public class Trail{
        float xOffset;
        float yOffset;
        float size;
        float time;
        Color colour;

        float id;

        public Trail(float xOffset, float yOffset, float size, float time, Color colour) {
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.size = size;
            this.time = time;
            this.colour = colour;

            this.id = MagicCampaignTrailPlugin.getUniqueID();
        }

        private Vector2f getTrailPos(){
            //math is hard ok? dont @ me

            Vector2f currentLoc = entity.getLocation();
            Vector2f yCorrect = MathUtils.getPointOnCircumference(currentLoc, yOffset, entity.getFacing() - 180f);
            Vector2f xCorrect = MathUtils.getPointOnCircumference(yCorrect, xOffset, entity.getFacing() -90f);

            return xCorrect;
        }

        public void render(){

            ////Creates the custom object we want
            //        MagicTrailObject objectToAdd = new MagicTrailObject(0f, 0f, duration, startSize, endSize, 0f, 0f,
            //                opacity, srcBlend, destBlend, speed, speed, color, color, angle, position, -1f, 0, offsetVelocity,
            //                0f, 0f);

/*
            MagicCampaignTrailPlugin.AddTrailMemberAdvanced(
                    entity,
                    id,
                    Global.getSettings().getSprite("fx", "IndEvo_trail_foggy"),
                    getTrailPos(),
                    0f,
                    0f,
                    entity.getFacing() - 180f,
                    0f,
                    0f,
                    size,
                    5f,
                    null,
                    null,
                    0.8f,
                    0.1f,
                    time,
                    0f,
                    GL_SRC_ALPHA,
                    GL_ONE,
                    -1f,
                    0,
                    new Vector2f(0, 0),
                    true,
                    entity.getContainingLocation());*/

            MagicCampaignTrailPlugin.AddTrailMemberSimple(
                    entity,
                    id,
                    Global.getSettings().getSprite("fx", "IndEvo_trail_foggy"),
                    getTrailPos(),
                    10f,
                    entity.getFacing() - 180f,
                    size,
                    size * 0.4f,
                    colour,
                    0.8f,
                    time,
                    true,
                    new Vector2f(0, 0));
        }
    }
}
