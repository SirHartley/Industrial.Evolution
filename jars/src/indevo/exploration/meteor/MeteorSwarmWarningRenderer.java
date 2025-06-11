package indevo.exploration.meteor;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.ModPlugin;
import indevo.utils.helper.CircularArc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import javax.swing.plaf.PanelUI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * kept in memory, last one added will supersede old one - can only have one swarm per system pending refactor
 */

public class MeteorSwarmWarningRenderer implements EveryFrameScript {

    public static final String WARNING_SIGN_ENTITY_ID = "IndEvo_warning_sign";
    public static final String MEM_INSTANCE = "$IndEvo_warningRenderer";
    public static final float DISTANCE_BETWEEN_SIGNS = 2000f;

    public boolean done = false;
    public boolean spawnedSigns = false;

    public LocationAPI location;
    public CircularArc arc;

    public IntervalUtil interval = new IntervalUtil(1f, 1f);
    public int emptyRounds = 0;

    public List<SectorEntityToken> warningSigns = new ArrayList<>();
    public List<Float> angles = new ArrayList<>();

    public MeteorSwarmWarningRenderer(LocationAPI location, CircularArc arc) {
        this.location = location;
        this.arc = arc;

        location.getMemoryWithoutUpdate().set(MEM_INSTANCE, this);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    public static void reportAngle(LocationAPI loc, float angle) {
        MeteorSwarmWarningRenderer renderer = (MeteorSwarmWarningRenderer) loc.getMemoryWithoutUpdate().get(MEM_INSTANCE);
        if (renderer != null) renderer.addAngle(angle);
    }

    @Override
    public void advance(float amount) {
        if (done) return;
        if (!spawnedSigns) spawnWarningSigns();

        interval.advance(amount);

        if (interval.intervalElapsed()){
            if (!angles.isEmpty()){
                float largest = (float) Math.floor(Collections.max(angles));
                float smallest = (float) Math.ceil(Collections.min(angles));

                for (SectorEntityToken sign : warningSigns) {
                    float angle = arc.getAngleForPoint(sign.getLocation());
                    if (Misc.isBetween(smallest, largest, angle)) sign.setFaction(Factions.PIRATES);
                    else sign.setFaction(Factions.NEUTRAL);
                }

                angles.clear();
            } else emptyRounds++;

            if (emptyRounds > 10) {
                for (SectorEntityToken sign : warningSigns) Misc.fadeAndExpire(sign, 0f);
                done = true;
                location.getMemoryWithoutUpdate().unset(MEM_INSTANCE);
            }
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

    public void addAngle(float angle){
        angles.add(angle);
    }
}
