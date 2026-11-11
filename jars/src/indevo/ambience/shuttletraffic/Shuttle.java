package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.movement.FollowMover;
import indevo.ambience.shuttletraffic.movement.PathMover;
import indevo.ambience.shuttletraffic.movement.ShuttlePath;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Shuttle {

    public enum End {
        NONE,
        PLANET,
        STATION
    }

    private static final float TURN_RATE = 90f;
    private static final float FLEE_WOBBLE = 10f;
    private static final float FLEE_WOBBLE_TIME = 3f;
    private static final float STRANDED_HOLD_RADIUS = 150f;

    public final ShuttleHull hull;
    public final FactionAPI faction;
    public ShuttleRoute route;
    public ShuttleAnchor from;
    public ShuttleAnchor to;
    public ShuttleAnchor home;
    public float speedMult;
    public boolean done = false;

    public boolean engineGlow = true;
    public boolean avoids = true;
    public boolean queues = true;
    public boolean flees = true;
    public boolean picket = false;
    public float trailAlpha = 1f;
    public float trailWidth = 3f;
    public int trailMax = ShuttleTrail.TRAIL_POINTS;
    public float flicker = 0.2f;
    public float spinRate = 0f;
    public float stayDays = 0f;
    public float boost = 0f;
    public float boostTime = 0f;
    public float delay = 0f;

    public final List<Shuttle> followers = new ArrayList<>(2);

    public final Vector2f pos = new Vector2f();
    public float facing = 0f;
    public float scale = 1f;
    public float alpha = 1f;
    public float lastThreatNearDay = -100f;

    private final float phase;
    private final ShuttleTrail trail = new ShuttleTrail();
    private final PathMover mover;
    private FollowMover follow;

    private float darken = 1f;
    private float glowMult = 1f;
    private float age = 0f;
    private float spin = 0f;
    private float turnSpeed = 0f;
    private boolean facingSet = false;
    private float appearRamp = 1f;
    private float appearAlpha = 1f;
    private float appearScale = 1f;

    private ShuttleState state = ShuttleState.CRUISE;
    // pad clearance and the wait at a refuge hold across states: a fleeing craft queues at a station like any other
    private boolean cleared = false;
    private boolean waitForSafeHome = false;
    private float fleeStart = 0f;
    private float resumeDay = 0f;
    private float screenX = 0f;
    private float screenY = 0f;

    public Shuttle(ShuttleHull hull, ShuttleRoute route, FactionAPI faction, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath path,
                   End launchEnd, End landEnd, float speedMult, float jitter, float phase) {
        this.hull = hull;
        this.route = route;
        this.faction = faction;
        this.from = from;
        this.to = to;
        this.home = from;
        this.speedMult = speedMult;
        this.phase = phase;
        this.mover = new PathMover(this, path, launchEnd, landEnd, jitter);
    }

    public void follow(Shuttle leader, float slotBack, float slotSide, float noise, boolean straggler) {
        follow = new FollowMover(this, leader, slotBack, slotSide, noise, straggler);
        leader.followers.add(this);
    }

    public void warmStart(float fraction) {
        mover.warmStart(fraction);
    }

    public void advance(float amount, ShuttleTraffic traffic) {
        if (delay > 0f) {
            delay -= amount;
            return;
        }

        age += amount;
        spin += spinRate * amount;

        if (state == ShuttleState.DOWN) {
            advanceDown(traffic);
            return;
        }

        if (follow != null) {
            follow.advance(amount);
            return;
        }

        mover.advance(amount, traffic);
    }

    public void redirect(ShuttleAnchor newTarget, ShuttleTraffic traffic) {
        to = newTarget;
        cleared = false;
        setState(state.redirect());
        setPath(traffic.getFlights().redirectPath(getFrame(), pos, facing, newTarget), End.NONE, newTarget.getEnd());
    }

    public void flee(ShuttleAnchor refuge, float threatX, float threatY, ShuttleTraffic traffic) {
        Random random = traffic.getRandom();
        SectorEntityToken frame = getFrame();
        follow = null;
        followers.clear();
        setState(state.flee(refuge != null));
        cleared = false;
        picket = false;
        stayDays = 0f;
        fleeStart = age;

        float heading = facing + (random.nextFloat() - 0.5f) * 140f;
        boolean panic = random.nextFloat() < 0.2f;

        if (refuge != null) {
            to = refuge;
            waitForSafeHome = home != null && refuge != home;
            setPath(traffic.getFlights().fleePath(frame, pos, heading, refuge, panic), End.NONE, refuge.getEnd());
            return;
        }

        lastThreatNearDay = traffic.getDays();
        setPath(traffic.getFlights().awayPath(frame, pos, heading, threatX, threatY, panic), End.NONE, End.NONE);
    }

    public void screen(float threatX, float threatY, ShuttleTraffic traffic) {
        screenX = threatX;
        screenY = threatY;
        if (state == ShuttleState.SCREENING) return;

        setState(state.screen());
        cleared = false;
        setPath(traffic.getFlights().screenPath(getFrame(), pos, facing, home, threatX, threatY), End.NONE, End.NONE);
    }

    // true when the pad is busy and the craft starts a holding lap
    public boolean holdForPad(ShuttleTraffic traffic) {
        if (queues && !cleared) {
            if (to.isPadBusy(traffic.getTime())) {
                setState(state.hold());
                setPath(traffic.getFlights().holdPath(to.entity, pos, facing, ShuttleAnchor.HOLD_RADIUS), End.NONE, End.NONE);
                return true;
            }

            cleared = true;
        }

        to.padBusyUntil = traffic.getTime() + 1f;
        return false;
    }

    public void onPathEnd(ShuttleTraffic traffic) {
        if (state.isHolding()) {
            finishHoldingLap(traffic);
            return;
        }

        if (state == ShuttleState.SCREENING) {
            continueScreening(traffic);
            return;
        }

        if (state.isStranded()) {
            continueStranded(traffic);
            return;
        }

        if (to != null && home != null && to != home && mover.getLandEnd() != End.NONE && (stayDays > 0f || waitForSafeHome)) {
            setState(state.land());
            resumeDay = traffic.getDays() + stayDays;
            return;
        }

        done = true;
    }

    public void disperse(Random random) {
        for (Shuttle follower : followers) follower.breakFrom(this, random);
        followers.clear();
    }

    public float getSpeed(float ease) {
        float speed = hull.getCruiseSpeed() * speedMult * state.pace * ease;
        if (boostTime > 0f && age < boostTime) speed += boost * (1f - age / boostTime);
        return speed;
    }

    public float getFleeWobble() {
        if (!state.isFleeing()) return 0f;

        float since = age - fleeStart;
        return FLEE_WOBBLE * Math.max(0f, 1f - since / FLEE_WOBBLE_TIME) * (float) Math.sin(since * 2f);
    }

    public void setFacing(float angle) {
        facing = angle;
        facingSet = true;
    }

    public void updateFacing(float amount, float desired) {
        if (!facingSet) {
            facing = desired;
            facingSet = true;
            turnSpeed = 0f;
            return;
        }

        float diff = Misc.getAngleDiff(facing, desired);
        float turn = Math.min(diff, TURN_RATE * state.turnMult * amount);
        float direction = Misc.getClosestTurnDirection(facing, desired);
        facing = Misc.normalizeAngle(facing + turn * direction);
        turnSpeed = amount > 0f ? turn * direction / amount : 0f;
    }

    public void updateLook(float amount, float pathScale, float traveled, float remaining, float launchLength, float landLength) {
        float speed = hull.getCruiseSpeed() * speedMult;

        scale = pathScale;
        alpha = 1f;
        darken = 1f;
        glowMult = (launchLength > 0f && traveled < launchLength + speed * 2f) || (landLength > 0f && remaining < landLength + speed * 2f) ? 1.5f : 1f;

        if (launchLength > 0f && traveled < launchLength) applyEnd(mover.getLaunchEnd(), from, 1f - traveled / launchLength);
        if (landLength > 0f && remaining < landLength) applyEnd(mover.getLandEnd(), to, 1f - remaining / landLength);

        if (appearRamp < 1f) {
            appearRamp = Math.min(1f, appearRamp + amount);
            alpha *= appearAlpha + (1f - appearAlpha) * appearRamp;
            scale *= appearScale + (1f - appearScale) * appearRamp;
        }
    }

    public void copyLook(Shuttle lead) {
        scale = lead.scale;
        alpha = lead.alpha;
        darken = lead.darken;
        glowMult = lead.glowMult;
    }

    public void pushTrailAtRear() {
        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));
        float back = hull.length * scale * 0.5f;
        trail.pushTrailIfMoved(pos.x - hx * back, pos.y - hy * back);
    }

    public ShuttleState getState() {
        return state;
    }

    public boolean isDown() {
        return state == ShuttleState.DOWN;
    }

    public boolean isFleeing() {
        return state.isFleeing();
    }

    public boolean isStranded() {
        return state.isStranded();
    }

    public boolean isScreening() {
        return state == ShuttleState.SCREENING;
    }

    public boolean isWaiting() {
        return delay > 0f;
    }

    public boolean isFollower() {
        return follow != null;
    }

    public End getLaunchEnd() {
        return mover.getLaunchEnd();
    }

    public End getLandEnd() {
        return mover.getLandEnd();
    }

    public ShuttleTrail getTrail() {
        return trail;
    }

    public SectorEntityToken getFrame() {
        return follow != null ? follow.getFrame() : mover.getFrame();
    }

    public float getTurnSpeed() {
        return turnSpeed;
    }

    public float getAge() {
        return age;
    }

    public float getPhase() {
        return phase;
    }

    public void render(float alphaMult, SpriteAPI glow) {
        if (delay > 0f || state == ShuttleState.DOWN) return;

        float a = alpha * alphaMult;
        if (a <= 0.01f) return;

        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));
        float rearX = pos.x - hx * hull.length * scale * 0.5f;
        float rearY = pos.y - hy * hull.length * scale * 0.5f;

        trail.render(rearX, rearY, trailMax, trailWidth, hull.trailColor, hull.additiveTrail, a * trailAlpha);

        SpriteAPI sprite = hull.sprite;
        sprite.setSize(hull.width * scale, hull.length * scale);
        sprite.setAngle(spinRate != 0f ? spin : facing - 90f);
        sprite.setColor(darken >= 1f ? Color.WHITE : new Color((int) (255 * darken), (int) (255 * darken), (int) (255 * darken)));
        sprite.setAlphaMult(a);
        sprite.setNormalBlend();
        sprite.renderAtCenter(pos.x, pos.y);

        if (engineGlow) {
            float glowSize = Math.max(4f, hull.width * scale * 0.9f) * glowMult;
            glow.setSize(glowSize, glowSize);
            glow.setAngle(0f);
            glow.setColor(hull.glowColor);
            glow.setAdditiveBlend();
            glow.setAlphaMult(a * (1f - flicker + flicker * (float) Math.random()));
            glow.renderAtCenter(rearX, rearY);
        }
    }

    private void setPath(ShuttlePath newPath, End newLaunch, End newLand) {
        appearRamp = 1f;
        if (newLaunch == End.NONE && (alpha < 1f || scale < 1f)) {
            appearRamp = 0f;
            appearAlpha = alpha;
            appearScale = scale;
        }

        mover.setPath(newPath, newLaunch, newLand);
    }

    // pace and turn rate come from the state alone
    private void setState(ShuttleState next) {
        state = next;
    }

    private void advanceDown(ShuttleTraffic traffic) {
        float days = traffic.getDays();
        if (!exists(home) || to == null || !to.entity.isAlive()) {
            done = true;
            return;
        }

        if (days < resumeDay || home.closed || !home.isSafe(days) || to.threatened) return;

        ShuttleAnchor start = to;
        setState(state.calm());
        waitForSafeHome = false;
        stayDays = 0f;
        from = start;
        to = home;
        facingSet = false;
        trail.clear();
        setPath(traffic.getFlights().lanePath(start, home, (traffic.getRandom().nextFloat() - 0.5f) * 0.5f, false), start.getEnd(), home.getEnd());
    }

    private void finishHoldingLap(ShuttleTraffic traffic) {
        if (to == null || !to.isUsable(traffic.getLocation())) {
            done = true;
            return;
        }

        if (to.isPadBusy(traffic.getTime())) {
            mover.restartLap();
            return;
        }

        to.padBusyUntil = traffic.getTime() + 1f;
        redirect(to, traffic);
        cleared = true;
    }

    private void continueScreening(ShuttleTraffic traffic) {
        LocationAPI location = traffic.getLocation();
        if (home != null && home.isUsable(location) && home.threatened && !home.overrun) {
            setPath(traffic.getFlights().screenPath(getFrame(), pos, facing, home, screenX, screenY), End.NONE, End.NONE);
            return;
        }

        setState(state.calm());
        if (home == null || !home.isUsable(location)) {
            done = true;
            return;
        }

        redirect(home, traffic);
    }

    private void continueStranded(ShuttleTraffic traffic) {
        setState(state.strand());

        if (!exists(home)) {
            done = true;
            return;
        }

        float days = traffic.getDays();
        if (home.isUsable(traffic.getLocation()) && home.isSafe(days) && days - lastThreatNearDay >= 1f) {
            setState(state.calm());
            redirect(home, traffic);
            return;
        }

        setPath(traffic.getFlights().holdPath(getFrame(), pos, facing, STRANDED_HOLD_RADIUS), End.NONE, End.NONE);
    }

    private void breakFrom(Shuttle lead, Random random) {
        float slotBack = follow.getSlotBack();
        float slotSide = follow.getSlotSide();
        SectorEntityToken leadFrame = follow.getFrame();
        follow = null;
        from = lead.from;
        to = lead.to;
        setState(lead.state);
        cleared = lead.cleared;
        waitForSafeHome = lead.waitForSafeHome;
        screenX = lead.screenX;
        screenY = lead.screenY;
        speedMult = lead.speedMult * (0.9f + 0.2f * random.nextFloat());
        mover.join(lead.mover, leadFrame, slotBack, slotSide, random);
    }

    // drawn above the planet: the craft shrinks on approach and fades only once it is over the surface
    private void applyEnd(End end, ShuttleAnchor anchor, float progress) {
        if (end == End.PLANET) {
            scale *= 1f - 0.5f * progress;
            darken = Math.min(darken, 1f - 0.3f * progress);
            if (anchor != null) {
                float radius = anchor.getRadius();
                float pad = radius * anchor.getPadDepth();
                float dist = Misc.getDistance(pos, anchor.getLocation());
                alpha *= Math.max(0f, Math.min(1f, (dist - pad) / Math.max(1f, radius - pad)));
            } else {
                alpha *= 1f - progress;
            }
        } else if (end == End.STATION) {
            scale *= 1f - 0.3f * progress;
            alpha *= 1f - progress;
        }
    }

    private static boolean exists(ShuttleAnchor anchor) {
        return anchor != null && anchor.seen && anchor.entity.isAlive();
    }
}
