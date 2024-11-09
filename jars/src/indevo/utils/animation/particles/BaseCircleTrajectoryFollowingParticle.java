package indevo.utils.animation.particles;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.utils.ModPlugin;
import indevo.utils.animation.data.CirclePathData;
import indevo.utils.helper.TrigHelper;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.EnumSet;

public abstract class BaseCircleTrajectoryFollowingParticle implements LunaCampaignRenderingPlugin {
    public Vector2f originLocation;
    public Vector2f targetLocation;
    public Vector2f relativeCenter;
    public float totalAngle;
    public float radius;

    public float duration;
    public float elapsed = 0f;

    public Vector2f position;
    public float facing = 0f;
    public float currentAngle;

    public int turnDirection;

    public BaseCircleTrajectoryFollowingParticle(Vector2f start,Vector2f end, float radius, float travelDuration) {
        this.relativeCenter = TrigHelper.findTwoPointCircle(start, end, radius).one;
        this.originLocation = start;
        this.targetLocation = end;
        this.radius = radius;
        this.duration = travelDuration;
        this.position = start;
        this.totalAngle = Misc.getAngleDiff(Misc.getAngleInDegrees(relativeCenter, start), Misc.getAngleInDegrees(relativeCenter, end));
        this.currentAngle = Misc.getAngleInDegrees(relativeCenter, start);
        this.turnDirection = 1; //Misc.getAngleInDegrees(originLocation, targetLocation) > 0 ? -1 : 1;
    }

    @Override
    public boolean isExpired() {
        return elapsed >= duration;
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;

        //update center
        relativeCenter = TrigHelper.findTwoPointCircle(originLocation, targetLocation, radius).one;
        currentAngle += (float) ((((totalAngle *1.5) / duration) * amount) * turnDirection); //I do not know why it stops short exactly by factor 1.5 but it does.
        Vector2f nextPos = MathUtils.getPointOnCircumference(relativeCenter, radius, currentAngle);
        float projRotation = Misc.getAngleInDegrees(position, nextPos);

        //ModPlugin.log("angle " + currentAngle + " target angle " + circleData.endAngle);

        position = nextPos;
        facing = projRotation;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }
}
