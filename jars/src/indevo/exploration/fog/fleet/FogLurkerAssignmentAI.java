package indevo.exploration.fog.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.fog.terrain.FogTerrainPlugin;
import org.lwjgl.util.vector.Vector2f;

public class FogLurkerAssignmentAI implements EveryFrameScript {

    public static final String MEM_LURKER = "$IndEvo_fogLurker";
    public static final float NOTICE_RANGE = 1500f;
    public static final float CHASE_DAYS = 2f;
    public static final float POST_CHECK_DAYS = 1f;
    public static final float POST_DRIFT = 600f;

    protected final CampaignFleetAPI fleet;
    protected final SectorEntityToken fog;
    // post offset from the fog centre, the bank can move
    protected final float postX;
    protected final float postY;
    protected boolean chasing = false;
    protected float sinceSeen = 0f;
    protected float sincePostCheck = 0f;
    protected boolean done = false;

    public FogLurkerAssignmentAI(CampaignFleetAPI fleet, SectorEntityToken fog, float postX, float postY) {
        this.fleet = fleet;
        this.fog = fog;
        this.postX = postX;
        this.postY = postY;
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
        if (!fleet.isAlive()) {
            done = true;
            return;
        }
        if (!fog.isAlive() || getFog().isOver()) {
            leave();
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (noticesTrail(player)) {
            sinceSeen = 0f;
            if (!chasing || fleet.getAI().getCurrentAssignmentType() != FleetAssignment.INTERCEPT) {
                fleet.clearAssignments();
                fleet.addAssignment(FleetAssignment.INTERCEPT, player, CHASE_DAYS + 1f);
                chasing = true;
            }
            // the lurker cannot see the target: without a last seen point the tactical module picks a random one
            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
            mem.set(FleetAIFlags.LAST_SEEN_TARGET_LOC, new Vector2f(player.getLocation()), 0.1f);
            mem.unset(FleetAIFlags.DAYS_TARGET_UNSEEN);
            return;
        }

        if (chasing) {
            sinceSeen += days;
            if (sinceSeen > CHASE_DAYS) {
                chasing = false;
                holdPost();
            }
            return;
        }

        sincePostCheck += days;
        if (sincePostCheck >= POST_CHECK_DAYS || fleet.getAI().getCurrentAssignment() == null) {
            sincePostCheck = 0f;
            SectorEntityToken target = fleet.getAI().getCurrentAssignment() != null ? fleet.getAI().getCurrentAssignment().getTarget() : null;
            float x = fog.getLocation().x + postX, y = fog.getLocation().y + postY;
            if (target == null || Misc.getDistance(target.getLocation().x, target.getLocation().y, x, y) > POST_DRIFT) holdPost();
        }
    }

    public void holdPost() {
        fleet.clearAssignments();
        SectorEntityToken post = fleet.getContainingLocation().createToken(fog.getLocation().x + postX, fog.getLocation().y + postY);
        fleet.addAssignment(FleetAssignment.HOLD, post, 1000f);
    }

    private boolean noticesTrail(CampaignFleetAPI player) {
        return player != null
                && player.getContainingLocation() == fleet.getContainingLocation()
                && Misc.getDistance(player, fleet) <= NOTICE_RANGE
                && !Misc.isSlowMoving(player)
                && fleet.isHostileTo(player)
                && getFog().getDensityAt(player.getLocation()) > FogTerrainPlugin.CONTAINS_THRESHOLD;
    }

    private FogTerrainPlugin getFog() {
        if (!(fog instanceof CampaignTerrainAPI terrain) || !(terrain.getPlugin() instanceof FogTerrainPlugin plugin)) {
            throw new IllegalStateException("Fog lurker anchored to " + fog + ", not an IndEvo_fog terrain");
        }
        return plugin;
    }

    private void leave() {
        done = true;
        fleet.clearAssignments();
        LocationAPI loc = fleet.getContainingLocation();
        SectorEntityToken exit = null;
        float best = Float.MAX_VALUE;
        for (SectorEntityToken jp : loc.getJumpPoints()) {
            float dist = Misc.getDistance(jp, fleet);
            if (dist < best) {
                best = dist;
                exit = jp;
            }
        }
        if (exit == null) {
            if (!(loc instanceof StarSystemAPI system)) throw new IllegalStateException("Fog lurker " + fleet.getName() + " outside a star system");
            exit = system.getCenter();
        }
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, exit, 1000f);
    }
}
