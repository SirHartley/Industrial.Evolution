package indevo.exploration.crucible.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class VariableOrbitScript implements EveryFrameScript {

    public SectorEntityToken entity;
    protected float orbitDistance;
    protected float angle;
    protected float orbitDuration;
    protected SectorEntityToken orbitFocus;

    protected boolean firstIteration = true;

    protected float factor;

    public VariableOrbitScript(SectorEntityToken entity, SectorEntityToken orbitFocus,float angle, float orbitRadius, float orbitDuration) {
        this.entity = entity;
        this.orbitDistance = orbitRadius;
        this.angle = angle;
        this.orbitDuration = orbitDuration;
        this.orbitFocus = orbitFocus;
        this.factor = 1f;
    }

    public VariableOrbitScript(SectorEntityToken entity, SectorEntityToken orbitFocus,float angle, float orbitRadius, float orbitDuration, float factor) {
        this.entity = entity;
        this.orbitDistance = orbitRadius;
        this.angle = angle;
        this.orbitDuration = orbitDuration;
        this.orbitFocus = orbitFocus;
        this.factor = factor;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (orbitFocus == null || orbitFocus.getLocation() == null) return;

        if (firstIteration) {
            Vector2f loc = MathUtils.getPointOnCircumference(orbitFocus.getLocation(), orbitDistance, angle);
            entity.setLocation(loc.x, loc.y);
            firstIteration = false;
            return;
        }

        float orbitDegreesPerSecond = 360f / Global.getSector().getClock().convertToSeconds(orbitDuration);
        float orbitDegreesThisFrame = orbitDegreesPerSecond * amount;
        float nextAngle = angle + orbitDegreesThisFrame  * factor;

        if (nextAngle >= 360f) nextAngle -= 360f;
        if (nextAngle < 0f) nextAngle += 360f;

        Vector2f nextLoc = MathUtils.getPointOnCircumference(orbitFocus.getLocation(), orbitDistance, nextAngle);
        entity.setLocation(nextLoc.x, nextLoc.y);
        angle = nextAngle;
    }

    public void setFactor(float factor) {
        this.factor = factor;
    }

    public static VariableOrbitScript get(SectorEntityToken t){
        for (EveryFrameScript s : t.getScripts()) if (s instanceof VariableOrbitScript) return (VariableOrbitScript) s;
        return null;
    }
}
