package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Shuttle {

    public enum End {
        NONE,
        PLANET,
        STATION,
        FLEET
    }

    public enum Trait {
        AVOIDS,
        QUEUES,
        FLEES,
        PICKET
    }

    public static final Set<Trait> CIVIL = Collections.unmodifiableSet(EnumSet.of(Trait.AVOIDS, Trait.QUEUES, Trait.FLEES));

    private static final float TURN_RATE = 90f;
    private static final float FLEE_WOBBLE = 10f;
    private static final float FLEE_WOBBLE_TIME = 3f;
    private static final float NEAR_MISS_WOBBLE_TIME = 2f;
    private static final float REFORM_TIME = 3f;
    private static final float REFORM_GIVE_UP = 30f;
    private static final float STRANDED_HOLD_RADIUS = 150f;
    private static final float REFUGE_WAIT_DAYS = 3f;
    private static final int GREY_STEPS = 32;
    private static final Color[] GREYS = new Color[GREY_STEPS + 1];

    static {
        for (int i = 0; i <= GREY_STEPS; i++) {
            int v = Math.round(255f * i / GREY_STEPS);
            GREYS[i] = new Color(v, v, v);
        }
    }

    public final ShuttleHull hull;
    public final FactionAPI faction;
    public final Vector2f pos = new Vector2f();

    private final ShuttleRoute route;
    private final ShuttleLook look;
    private final Set<Trait> traits;
    private final SpeedClass speedClass;
    private final float phase;
    private ShuttleAnchor from;
    private ShuttleAnchor to;
    private ShuttleAnchor home;
    private float speedMult;
    private boolean done = false;
    // picket duty ends once the craft flees
    private boolean picket;
    private float stayDays = 0f;
    private float boost = 0f;
    private float boostTime = 0f;
    private float delay = 0f;
    private float lastThreatNearDay = -100f;
    private ShuttleBudget.Group group = ShuttleBudget.Group.RARE;
    // craft tied to a fleet fade with its sensor fader and never dodge it
    private CampaignFleetAPI tiedFleet;

    private final List<Shuttle> followers = new ArrayList<>(2);
    private final ShuttleTrail trail;
    private final PathMover mover;
    private FollowMover follow;
    // a craft that broke formation flies back into its slot once nobody dodges for REFORM_TIME
    private Shuttle formerLeader;
    private float formerBack;
    private float formerSide;
    private float formerNoise;
    private boolean formerStraggler;
    private float reformAt;

    private float facing = 0f;
    private float scale = 1f;
    private float alpha = 1f;
    private float darken = 1f;
    private float glowMult = 1f;
    private float throttle = 1f;
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
    private float nearMissStart = -100f;
    private float resumeDay = 0f;
    private float landDay = 0f;
    private float glowFlicker = 1f;
    private float screenX = 0f;
    private float screenY = 0f;

    public Shuttle(ShuttleHull hull, ShuttleRoute route, FactionAPI faction, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath path,
                   End launchEnd, End landEnd, SpeedClass speedClass, float speedMult, float jitter, float phase, ShuttleLook look, Set<Trait> traits) {
        this.hull = hull;
        this.route = route;
        this.look = look;
        this.traits = traits.isEmpty() ? EnumSet.noneOf(Trait.class) : EnumSet.copyOf(traits);
        this.picket = traits.contains(Trait.PICKET);
        this.faction = faction;
        this.from = from;
        this.to = to;
        this.home = from;
        this.speedClass = speedClass;
        this.speedMult = speedMult;
        this.phase = phase;
        this.trail = new ShuttleTrail(look.trailSeconds());
        this.mover = new PathMover(this, path, launchEnd, landEnd, jitter);
    }

    public void follow(Shuttle leader, float slotBack, float slotSide, float noise, boolean straggler) {
        follow = new FollowMover(this, leader, slotBack, slotSide, noise, straggler);
        leader.followers.add(this);
    }

    // ejection: hidden for delay seconds, then boost su/s on top of cruise speed, fading over boostTime
    public void eject(float delay, float boost, float boostTime) {
        this.delay = delay;
        spin = phase * 360f;
        this.boost = boost;
        this.boostTime = boostTime;
    }

    public void tieTo(CampaignFleetAPI fleet) {
        tiedFleet = fleet;
    }

    public CampaignFleetAPI getTiedFleet() {
        return tiedFleet;
    }

    // hidden and still until the delay runs out, so a burst leaves one craft after the other
    public void holdFor(float seconds) {
        delay = seconds;
    }

    public void stay(float days) {
        stayDays = days;
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
        trail.advance(amount);
        glowFlicker = 1f - look.flicker() + look.flicker() * (float) Math.random();
        if (isTumbling()) spin += look.tumbleRate() * (1f - age / boostTime) * amount;

        if (state == ShuttleState.DOWN) {
            advanceDown(traffic);
            return;
        }

        if (follow != null) {
            follow.advance(amount);
            return;
        }

        mover.advance(amount, traffic);
        if (formerLeader != null && !done) tryReform();
    }

    public void redirect(ShuttleAnchor newTarget, ShuttleTraffic traffic) {
        to = newTarget;
        cleared = false;
        setState(state.redirect());
        // a fleet can leave at burn 20, a turn that moved with it would drag the craft along
        SectorEntityToken frame = getFrame() instanceof CampaignFleetAPI ? null : getFrame();
        setPath(traffic.getPaths().redirectPath(frame, pos, facing, newTarget), End.NONE, newTarget.getEnd());
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
            setPath(traffic.getPaths().fleePath(frame, pos, heading, refuge, panic), End.NONE, refuge.getEnd());
            return;
        }

        lastThreatNearDay = traffic.getDays();
        setPath(traffic.getPaths().awayPath(frame, pos, heading, threatX, threatY, panic), End.NONE, End.NONE);
    }

    public void screen(float threatX, float threatY, ShuttleTraffic traffic) {
        screenX = threatX;
        screenY = threatY;
        if (state == ShuttleState.SCREENING) return;

        setState(state.screen());
        cleared = false;
        setPath(traffic.getPaths().screenPath(getFrame(), pos, facing, home, threatX, threatY), End.NONE, End.NONE);
    }

    // true when the pad is busy and the craft starts a holding lap
    public boolean holdForPad(ShuttleTraffic traffic) {
        if (traits.contains(Trait.QUEUES) && !cleared) {
            if (to.isPadBusy(traffic.getTime())) {
                setState(state.hold());
                setPath(traffic.getPaths().holdPath(to.entity, pos, facing, ShuttleAnchor.HOLD_RADIUS), End.NONE, End.NONE);
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
            landDay = traffic.getDays();
            resumeDay = landDay + stayDays;
            return;
        }

        done = true;
    }

    public void disperse(Random random) {
        for (Shuttle follower : followers) follower.breakFrom(this, random);
        followers.clear();
    }

    public float getSpeed(float ease) {
        float speed = getCruiseSpeed() * state.pace * ease;
        if (boostTime > 0f && age < boostTime) speed += boost * (1f - age / boostTime);
        return speed;
    }

    // a near miss: the flee wobble for 2 s and the formation breaks
    public void nearMiss(Random random) {
        if (age - nearMissStart >= NEAR_MISS_WOBBLE_TIME) nearMissStart = age;
        if (!followers.isEmpty()) disperse(random);
    }

    public float getWobble() {
        float wobble = 0f;
        float sinceNearMiss = age - nearMissStart;
        if (sinceNearMiss < NEAR_MISS_WOBBLE_TIME) wobble += FLEE_WOBBLE * (1f - sinceNearMiss / NEAR_MISS_WOBBLE_TIME) * (float) Math.sin(sinceNearMiss * 6f);
        if (!state.isFleeing()) return wobble;

        float since = age - fleeStart;
        return wobble + FLEE_WOBBLE * Math.max(0f, 1f - since / FLEE_WOBBLE_TIME) * (float) Math.sin(since * 2f);
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
        float speed = getCruiseSpeed();

        scale = pathScale;
        alpha = 1f;
        darken = 1f;
        glowMult = (launchLength > 0f && traveled < launchLength + speed * 2f) || (landLength > 0f && remaining < landLength + speed * 2f) ? 1.3f : 1f;

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
        throttle = lead.throttle;
    }

    public void pushTrailAtRear() {
        if (isTumbling()) return;

        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));
        float back = hull.length * scale * 0.5f;
        trail.pushTrailIfDue(pos.x - hx * back, pos.y - hy * back);
    }

    // dims the trail while the craft eases out of or into a pad
    public void setThrottle(float speed) {
        throttle = Math.max(0.4f, Math.min(1f, speed / Math.max(1f, speedClass.getCruiseSpeed(hull))));
    }

    public void finish() {
        done = true;
    }

    public void markThreatNear(float days) {
        lastThreatNearDay = days;
    }

    public boolean isDone() {
        return done;
    }

    public boolean avoids() {
        return traits.contains(Trait.AVOIDS);
    }

    public boolean flees() {
        return traits.contains(Trait.FLEES);
    }

    public boolean isPicket() {
        return picket;
    }

    public void setGroup(ShuttleBudget.Group group) {
        this.group = group;
    }

    public ShuttleBudget.Group getGroup() {
        return group;
    }

    public ShuttleRoute getRoute() {
        return route;
    }

    public ShuttleAnchor getFrom() {
        return from;
    }

    public ShuttleAnchor getTo() {
        return to;
    }

    public ShuttleAnchor getHome() {
        return home;
    }

    public float getSpeedMult() {
        return speedMult;
    }

    public SpeedClass getSpeedClass() {
        return speedClass;
    }

    public float getCruiseSpeed() {
        return speedClass.getCruiseSpeed(hull) * speedMult;
    }

    public ShuttleLook getLook() {
        return look;
    }

    public Set<Trait> getTraits() {
        return traits;
    }

    public List<Shuttle> getFollowers() {
        return followers;
    }

    public float getFacing() {
        return facing;
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

    // a refugee waiting hidden for its home to be safe again holds no cap slot
    public boolean isWaitingAtRefuge() {
        return state == ShuttleState.DOWN && waitForSafeHome;
    }

    // redirect only leaves holding laps; screening, stranded and landed craft keep their own paths
    public boolean canRedirect() {
        return !isDone() && (state == ShuttleState.CRUISE || state == ShuttleState.HOLDING || state == ShuttleState.FLEEING
                || state == ShuttleState.FLEEING_HOLDING);
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

    public void renderTrail(float alphaMult) {
        float a = getRenderAlpha(alphaMult);
        if (a <= 0.01f || isTumbling()) return;

        float width = Math.min(look.trailWidth(), 0.5f * hull.width * scale);
        trail.render(getRearX(), getRearY(), width, hull.trailColor, hull.additiveTrail, a * look.trailAlpha() * throttle);
    }

    public void renderSprite(float alphaMult, SpriteAPI glow) {
        float a = getRenderAlpha(alphaMult);
        if (a <= 0.01f) return;

        SpriteAPI sprite = hull.sprite;
        sprite.setSize(hull.width * scale, hull.length * scale);
        sprite.setAngle(getSpriteAngle());
        sprite.setColor(GREYS[Math.max(0, Math.min(GREY_STEPS, Math.round(darken * GREY_STEPS)))]);
        sprite.setAlphaMult(a);
        sprite.setNormalBlend();
        sprite.renderAtCenter(pos.x, pos.y);

        if (look.glow() && !isTumbling()) {
            // never wider than the craft: 0.7 * 1.3 at most
            float glowSize = hull.width * scale * 0.7f * glowMult;
            glow.setSize(glowSize, glowSize);
            glow.setAngle(0f);
            glow.setColor(hull.glowColor);
            glow.setAdditiveBlend();
            glow.setAlphaMult(a * glowFlicker);
            glow.renderAtCenter(getRearX(), getRearY());
        }
    }

    private boolean isTumbling() {
        return look.tumbleRate() != 0f && age < boostTime;
    }

    // the tumble blends into the heading over the boost
    private float getSpriteAngle() {
        float heading = facing - 90f;
        if (!isTumbling()) return heading;

        float t = age / boostTime;
        float blend = t * t * (3f - 2f * t);
        return spin + Misc.getClosestTurnDirection(spin, heading) * Misc.getAngleDiff(spin, heading) * blend;
    }

    private float getRenderAlpha(float alphaMult) {
        if (delay > 0f || state == ShuttleState.DOWN) return 0f;
        float fader = tiedFleet != null && !tiedFleet.isPlayerFleet() ? tiedFleet.getSensorFaderBrightness() : 1f;
        return alpha * alphaMult * fader;
    }

    private float getRearX() {
        return pos.x - (float) Math.cos(Math.toRadians(facing)) * hull.length * scale * 0.5f;
    }

    private float getRearY() {
        return pos.y - (float) Math.sin(Math.toRadians(facing)) * hull.length * scale * 0.5f;
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
        if (!exists(home) || home.entity.getContainingLocation() != traffic.getLocation() || to == null || !to.entity.isAlive()) {
            done = true;
            return;
        }

        if (waitForSafeHome && days - landDay >= REFUGE_WAIT_DAYS) {
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
        setPath(traffic.getPaths().lanePath(start, home, ShuttleRoute.rollBend(traffic.getRandom()), false), start.getEnd(), home.getEnd());
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
            setPath(traffic.getPaths().screenPath(getFrame(), pos, facing, home, screenX, screenY), End.NONE, End.NONE);
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

        setPath(traffic.getPaths().holdPath(getFrame(), pos, facing, STRANDED_HOLD_RADIUS), End.NONE, End.NONE);
    }

    private void tryReform() {
        Shuttle lead = formerLeader;
        if (lead.isDone() || lead.isDown() || lead.isFollower() || age > reformAt + REFORM_GIVE_UP) {
            formerLeader = null;
            return;
        }

        if (mover.isDodging() || lead.mover.isDodging()) {
            reformAt = age + REFORM_TIME;
            return;
        }

        if (age < reformAt || lead.state != state || state != ShuttleState.CRUISE || !mover.sharesPath(lead.mover)) return;

        formerLeader = null;
        follow = new FollowMover(this, lead, formerBack, formerSide, formerNoise, formerStraggler);
        follow.blendFrom(pos);
        lead.followers.add(this);
    }

    private void breakFrom(Shuttle lead, Random random) {
        float slotBack = follow.getSlotBack();
        float slotSide = follow.getSlotSide();
        SectorEntityToken leadFrame = follow.getFrame();
        formerLeader = lead;
        formerBack = slotBack;
        formerSide = slotSide;
        formerNoise = follow.getNoise();
        formerStraggler = follow.isStraggler();
        reformAt = age + REFORM_TIME;
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
        } else if (end == End.STATION || end == End.FLEET) {
            scale *= 1f - 0.3f * progress;
            alpha *= 1f - progress;
        }
    }

    private static boolean exists(ShuttleAnchor anchor) {
        return anchor != null && anchor.seen && anchor.entity.isAlive();
    }
}
