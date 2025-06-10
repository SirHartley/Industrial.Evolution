package indevo.exploration.meteor;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.CircularArc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class MeteorSwarmWarningRenderer implements EveryFrameScript {

    public static final String WARNING_SIGN_ENTITY_ID = "IndEvo_warning_sign";
    public static final float DISTANCE_BETWEEN_SIGNS = 2000f;

    public boolean done = false;
    public boolean spawnedSigns = false;
    public boolean startEndArc = false;

    public LocationAPI location;
    public CircularArc arc;
    public float firstTraversalVel = 0f;
    public float lastTraversalVel = 0f;

    public float firstAngle;
    public float lastAngle;

    public List<SectorEntityToken> warningSigns = new ArrayList<>();

    public MeteorSwarmWarningRenderer(LocationAPI location, CircularArc arc) {
        this.location = location;
        this.arc = arc;

        firstAngle = arc.startAngle + 0.1f;
        lastAngle = arc.startAngle;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;
        if (!spawnedSigns) spawnWarningSigns();

        if (!hasFirstSet()) return;

        firstAngle += arc.convertToDegreesPerSecond(firstTraversalVel) * amount;
        if (startEndArc) lastAngle += arc.convertToDegreesPerSecond(lastTraversalVel) * amount;

        for (SectorEntityToken sign : warningSigns) {
            float angle = arc.getAngleForPoint(sign.getLocation());
            float minAngle = Math.min(firstAngle, lastAngle);
            float maxAngle = Math.max(firstAngle, lastAngle);

            if (Misc.isBetween(angle, minAngle, maxAngle)) {
                sign.setFaction(Factions.PIRATES);
            } else {
                sign.setFaction(Factions.NEUTRAL);
            }
        }

        if (lastAngle > arc.endAngle) {
            for (SectorEntityToken sign : warningSigns) Misc.fadeAndExpire(sign, 0f);
            done = true;
        }
    }

    public void spawnWarningSigns(){
        for (Vector2f loc : arc.getEvenlySpacedLocationsOnArc(DISTANCE_BETWEEN_SIGNS)) {
            SectorEntityToken sign = location.addCustomEntity(Misc.genUID(), null, WARNING_SIGN_ENTITY_ID, Factions.NEUTRAL);
            sign.setLocation(loc.x, loc.y);
            warningSigns.add(sign);
        }

        spawnedSigns = true;
    }

    public void setFirstTraversalVel(float firstTraversalVel) {
        this.firstTraversalVel = firstTraversalVel;
    }

    public void setLastTraversalVel(float lastTraversalVel) {
        this.lastTraversalVel = lastTraversalVel;
    }

    public boolean hasFirstSet(){
        return firstTraversalVel > 0f;
    }

    public void setStartEndArc(){
        startEndArc = true;
    }
}
