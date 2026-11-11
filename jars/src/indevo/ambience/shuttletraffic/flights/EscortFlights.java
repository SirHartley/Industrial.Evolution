package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleBudget;
import indevo.ambience.shuttletraffic.ShuttleFleetScan;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.SpeedClass;
import indevo.ambience.shuttletraffic.movement.SteerMover;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// free steering at dash speed: fighters that meet a fleet approaching a military market and lead it in, and wings that circle huge slow fleets
public class EscortFlights extends BaseFlights {

    private static final float APPROACH_RANGE = 900f;
    private static final float LEAVE_RANGE = 1200f;
    private static final float BREAK_RANGE = 150f;
    private static final float GUARD_AHEAD = 45f;
    private static final float GUARD_SIDE = 30f;
    private static final float GUARD_REPEAT_DAYS = 3f;
    private static final float NPC_GUARD_FP = 100f;
    private static final float LAUNCH_SPEED = 30f;
    private static final String GUARD_FALLBACK = "talon";
    private static final float WING_FP = 150f;
    private static final float WING_DOUBLE_FP = 300f;
    private static final float PLAYER_WING_DOUBLE_FP = 150f;
    private static final float WING_LAUNCH_SPEED = 60f;
    private static final float FOLD_SPEED = 100f;
    private static final float FOLD_TIME = 2f;
    private static final float HOSTILE_RANGE = 1500f;
    private static final float WING_RELAUNCH_DELAY = 5f;
    private static final float LAP_SPEED = 40f;
    private static final float WING_SPACING = 0.2f;
    private static final float FOLD_LENGTH = ShuttleAnchor.FLEET_END_LENGTH;
    private static final Set<Shuttle.Trait> GUARD_TRAITS = Collections.unmodifiableSet(EnumSet.of(Shuttle.Trait.AVOIDS, Shuttle.Trait.QUEUES,
            Shuttle.Trait.FLEES, Shuttle.Trait.PICKET));

    // keyed by the market's anchor entity: the player's guard and one NPC guard per market at a time
    private final Map<SectorEntityToken, Guard> guards = new LinkedHashMap<>();
    // last guard per market and fleet, so one approach gets one guard
    private final Map<String, Float> lastGuardDay = new HashMap<>();
    private final Map<CampaignFleetAPI, Wings> wings = new LinkedHashMap<>();
    // traffic time before which a fleet whose wings folded back gets no new ones
    private final Map<CampaignFleetAPI, Float> wingCooldown = new HashMap<>();
    private float lastUpdate = -1f;

    private final class Guard {
        final ShuttleAnchor pad;
        final CampaignFleetAPI fleet;
        final List<Shuttle> craft = new ArrayList<>(2);
        boolean over = false;

        Guard(ShuttleAnchor pad, CampaignFleetAPI fleet) {
            this.pad = pad;
            this.fleet = fleet;
        }

        boolean isActive() {
            for (Shuttle shuttle : craft) if (!shuttle.isDone() && shuttle.isSteering()) return true;
            return false;
        }
    }

    // one side of the pair: holds station ahead of the fleet, then breaks off and lands
    private final class GuardSlot implements Shuttle.Steering {
        final Guard guard;
        final float side;

        GuardSlot(Guard guard, float side) {
            this.guard = guard;
            this.side = side;
        }

        @Override
        public float aim(Shuttle shuttle, ShuttleTraffic traffic, Vector2f point, Vector2f velocity) {
            CampaignFleetAPI fleet = guard.fleet;
            ShuttleAnchor pad = guard.pad;
            if (guard.over || !pad.isUsable(traffic.getLocation()) || getGap(fleet, pad) <= BREAK_RANGE) {
                guard.over = true;
                if (pad.isUsable(traffic.getLocation())) shuttle.landAt(pad, traffic);
                else shuttle.finish();
                return 0f;
            }

            Vector2f vel = fleet.getVelocity();
            float speed = vel.length();
            Vector2f heading = speed > 5f ? new Vector2f(vel.x / speed, vel.y / speed) : Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(fleet.getLocation(), pad.getLocation()));
            float ahead = fleet.getRadius() + GUARD_AHEAD;
            Vector2f loc = fleet.getLocation();
            point.set(loc.x + heading.x * ahead - heading.y * GUARD_SIDE * side, loc.y + heading.y * ahead + heading.x * GUARD_SIDE * side);
            velocity.set(vel);
            return SteerMover.getDashSpeed(speed);
        }
    }

    private static final class Wings {
        final CampaignFleetAPI fleet;
        final List<Wing> list = new ArrayList<>(2);
        float fastTime = 0f;
        boolean folding = false;

        Wings(CampaignFleetAPI fleet) {
            this.fleet = fleet;
        }

        boolean isEmpty() {
            for (Wing wing : list) for (Shuttle shuttle : wing.craft) if (!shuttle.isDone() && shuttle.isSteering()) return false;
            return true;
        }
    }

    private static final class Wing {
        final List<Shuttle> craft = new ArrayList<>(3);
        final float radiusX;
        final float radiusY;
        final float rotation;
        final float startAngle;
        final float direction;

        Wing(float radiusX, float radiusY, float rotation, float startAngle, float direction) {
            this.radiusX = radiusX;
            this.radiusY = radiusY;
            this.rotation = rotation;
            this.startAngle = startAngle;
            this.direction = direction;
        }
    }

    // one craft of a wing: a slow ellipse around the live fleet, a string of craft along it; folding flies into the fleet and fades
    private final class WingSlot implements Shuttle.Steering {
        final Wings group;
        final Wing wing;
        final int index;

        WingSlot(Wings group, Wing wing, int index) {
            this.group = group;
            this.wing = wing;
            this.index = index;
        }

        @Override
        public float aim(Shuttle shuttle, ShuttleTraffic traffic, Vector2f point, Vector2f velocity) {
            CampaignFleetAPI fleet = group.fleet;
            Vector2f loc = fleet.getLocation();
            Vector2f vel = fleet.getVelocity();
            float top = SteerMover.getDashSpeed(vel.length());

            if (group.folding) {
                if (!fleet.isAlive() || fleet.getContainingLocation() != traffic.getLocation()) {
                    shuttle.finish();
                    return 0f;
                }

                float dist = Misc.getDistance(shuttle.pos, loc);
                if (dist < 3f) {
                    shuttle.finish();
                    return 0f;
                }

                float t = Math.min(1f, dist / FOLD_LENGTH);
                shuttle.setSteerLook(1f - 0.3f * (1f - t), t);
                point.set(loc);
                velocity.set(vel);
                return top;
            }

            float omega = LAP_SPEED / wing.radiusX * wing.direction;
            float angle = wing.startAngle + omega * traffic.getTime() - index * WING_SPACING * wing.direction;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float rc = (float) Math.cos(wing.rotation);
            float rs = (float) Math.sin(wing.rotation);
            float ex = cos * wing.radiusX;
            float ey = sin * wing.radiusY;
            float dx = -sin * wing.radiusX * omega;
            float dy = cos * wing.radiusY * omega;
            point.set(loc.x + ex * rc - ey * rs, loc.y + ex * rs + ey * rc);
            velocity.set(vel.x + dx * rc - dy * rs, vel.y + dx * rs + dy * rc);
            return top;
        }
    }

    public EscortFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    public void clear() {
        guards.clear();
        wings.clear();
        wingCooldown.clear();
        lastUpdate = -1f;
    }

    // after every fleet scan
    public void update() {
        Iterator<Guard> iterator = guards.values().iterator();
        while (iterator.hasNext()) {
            Guard guard = iterator.next();
            if (!guard.isActive()) {
                iterator.remove();
                continue;
            }

            if (shouldLeave(guard)) guard.over = true;
        }

        ShuttleFleetScan scan = traffic.getFleetScan();
        for (int i = 0; i < scan.getCount(); i++) {
            ShuttleFleetScan.Entry entry = scan.get(i);
            if (entry.fleet.isPlayerFleet()) updatePlayerGuard(entry.fleet);
        }

        for (int i = 0; i < scan.getCount(); i++) {
            ShuttleFleetScan.Entry entry = scan.get(i);
            if (!entry.fleet.isPlayerFleet()) updateNpcGuard(entry);
        }

        float time = traffic.getTime();
        float elapsed = lastUpdate < 0f ? 0f : time - lastUpdate;
        lastUpdate = time;
        updateWings(scan, elapsed);
    }

    // the player first: escorts share one cap
    private void updateWings(ShuttleFleetScan scan, float elapsed) {
        float time = traffic.getTime();
        wingCooldown.values().removeIf(until -> until < time);

        Iterator<Wings> iterator = wings.values().iterator();
        while (iterator.hasNext()) {
            Wings group = iterator.next();
            if (group.folding && group.isEmpty()) {
                wingCooldown.put(group.fleet, time + WING_RELAUNCH_DELAY);
                iterator.remove();
            }
        }

        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < scan.getCount(); i++) {
                ShuttleFleetScan.Entry entry = scan.get(i);
                if (entry.fleet.isPlayerFleet() == (pass == 0)) updateWings(entry, scan, elapsed);
            }
        }

        for (Wings group : wings.values()) {
            if (!group.fleet.isAlive() || group.fleet.getContainingLocation() != traffic.getLocation()) group.folding = true;
        }
    }

    private void updateWings(ShuttleFleetScan.Entry entry, ShuttleFleetScan scan, float elapsed) {
        CampaignFleetAPI fleet = entry.fleet;
        Wings group = wings.get(fleet);
        int wanted = getWantedWings(entry, scan);
        float speed = fleet.getVelocity().length();

        if (group != null) {
            group.fastTime = speed > FOLD_SPEED ? group.fastTime + elapsed : 0f;
            if (wanted == 0 || group.fastTime >= FOLD_TIME) group.folding = true;
            if (group.folding || group.list.size() >= wanted || speed >= WING_LAUNCH_SPEED) return;
        } else if (wanted == 0 || speed >= WING_LAUNCH_SPEED || wingCooldown.containsKey(fleet)) {
            return;
        }

        if (group == null) {
            group = new Wings(fleet);
            if (!launchWing(group)) return;
            wings.put(fleet, group);
            return;
        }

        launchWing(group);
    }

    // NPC fleets: 150 fleet points, slow, seen; the player: own fitted wings inside a system with markets or with a hostile fleet near, never dark
    private int getWantedWings(ShuttleFleetScan.Entry entry, ShuttleFleetScan scan) {
        CampaignFleetAPI fleet = entry.fleet;
        if (!fleet.isAlive() || fleet.getBattle() != null) return 0;

        float fp = fleet.getFleetData().getFleetPointsUsed();
        if (!fleet.isPlayerFleet()) {
            if (!entry.visible || fp < WING_FP) return 0;
            return fp >= WING_DOUBLE_FP ? 2 : 1;
        }

        if (isDark(fleet) || getFittedWings(fleet).isEmpty()) return 0;
        if (network.marketAnchors.isEmpty() && !isHostileNear(fleet, scan)) return 0;
        return fp >= PLAYER_WING_DOUBLE_FP ? 2 : 1;
    }

    private boolean isHostileNear(CampaignFleetAPI player, ShuttleFleetScan scan) {
        for (int i = 0; i < scan.getCount(); i++) {
            ShuttleFleetScan.Entry entry = scan.get(i);
            if (entry.fleet == player || !entry.visible || entry.faction == null || !entry.faction.isHostileTo(player.getFaction())) continue;

            float gap = Misc.getDistance(player.getLocation(), entry.fleet.getLocation()) - player.getRadius() - entry.fleet.getRadius();
            if (gap <= HOSTILE_RANGE) return true;
        }
        return false;
    }

    // 2 or 3 fighters of one of the fleet's own wings, faction fighters when it has none
    private boolean launchWing(Wings group) {
        CampaignFleetAPI fleet = group.fleet;
        ShuttleBudget budget = traffic.getBudget();
        budget.count(traffic.getShuttles());
        int room = budget.getRoom(ShuttleBudget.Group.ESCORT);
        if (room < 2) return false;

        List<String> fitted = getFittedWings(fleet);
        ShuttleHull hull = fitted.isEmpty() ? null : ShuttleHullPool.getWingHull(fitted.get(random.nextInt(fitted.size())), fleet.getFaction());
        if (hull == null && !fleet.isPlayerFleet()) hull = ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(fleet.getFaction(), false), random, h -> h.fighter);
        if (hull == null) return false;

        float radius = fleet.getRadius() + 60f + random.nextFloat() * 60f;
        Wing wing = new Wing(radius, radius * (0.6f + random.nextFloat() * 0.3f), random.nextFloat() * 6.28f, random.nextFloat() * 6.28f, paths.randomSign());
        ShuttleAnchor home = ShuttleAnchor.forFleet(fleet);
        int count = Math.min(room, 2 + random.nextInt(2));

        for (int i = 0; i < count; i++) {
            Shuttle shuttle = new Shuttle(hull, null, fleet.getFaction(), home, home, null, Shuttle.End.FLEET, Shuttle.End.FLEET, SpeedClass.MILITARY, 1f, 0f,
                    random.nextFloat(), ShuttleLook.CRAFT, EnumSet.noneOf(Shuttle.Trait.class));
            shuttle.pos.set(fleet.getLocation());
            shuttle.beginSteer(new WingSlot(group, wing, i), new Vector2f(fleet.getVelocity()), true);
            shuttle.holdFor(i * 0.4f);
            shuttle.tieTo(fleet);
            shuttle.setGroup(ShuttleBudget.Group.ESCORT);
            budget.add(ShuttleBudget.Group.ESCORT, 1);
            wing.craft.add(shuttle);
            traffic.getShuttles().add(shuttle);
        }

        group.list.add(wing);
        return true;
    }

    private static List<String> getFittedWings(CampaignFleetAPI fleet) {
        List<String> fitted = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing() || member.isMothballed()) continue;
            for (String wingId : member.getVariant().getFittedWings()) if (wingId != null && !wingId.isEmpty()) fitted.add(wingId);
        }
        return fitted;
    }

    private void updatePlayerGuard(CampaignFleetAPI player) {
        if (!player.isTransponderOn() || isDark(player)) return;

        for (ShuttleAnchor pad : network.marketAnchors) {
            if (guards.containsKey(pad.entity) || !isGuardPort(pad) || !welcomes(pad.market)) continue;

            float gap = getGap(player, pad);
            if (gap > APPROACH_RANGE || gap <= BREAK_RANGE * 2f) continue;
            if (!takeTurn(pad, player)) continue;

            launchGuard(pad, player);
            return;
        }
    }

    // a non hostile fleet of 100 fleet points or more whose assignment targets the market
    private void updateNpcGuard(ShuttleFleetScan.Entry entry) {
        CampaignFleetAPI fleet = entry.fleet;
        if (!entry.visible || entry.target == null || fleet.getFleetData().getFleetPointsUsed() < NPC_GUARD_FP || fleet.getBattle() != null) return;

        ShuttleAnchor pad = network.anchors.get(entry.target);
        if (pad == null || pad.role != ShuttleAnchor.Role.MARKET || guards.containsKey(pad.entity) || !isGuardPort(pad)) return;
        if (pad.faction == null || pad.faction.isHostileTo(fleet.getFaction())) return;

        float gap = getGap(fleet, pad);
        if (gap > APPROACH_RANGE || gap <= BREAK_RANGE * 2f || !takeTurn(pad, fleet)) return;

        launchGuard(pad, fleet);
    }

    private boolean takeTurn(ShuttleAnchor pad, CampaignFleetAPI fleet) {
        String key = pad.entity.getId() + "|" + fleet.getId();
        Float last = lastGuardDay.get(key);
        if (last != null && traffic.getDays() - last < GUARD_REPEAT_DAYS) return false;
        return true;
    }

    private void launchGuard(ShuttleAnchor pad, CampaignFleetAPI fleet) {
        ShuttleBudget budget = traffic.getBudget();
        budget.count(traffic.getShuttles());
        if (budget.getRoom(ShuttleBudget.Group.ESCORT) < 2 || !canFly(pad, pad)) return;

        ShuttleHull hull = ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(pad.faction, true), random, h -> h.fighter);
        if (hull == null) hull = ShuttleHullPool.getHull(GUARD_FALLBACK, true, 2);
        if (hull == null) return;

        lastGuardDay.put(pad.entity.getId() + "|" + fleet.getId(), traffic.getDays());
        Guard guard = new Guard(pad, fleet);
        Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(pad.getLocation(), fleet.getLocation()));
        Vector2f start = new Vector2f(pad.getLocation().x + dir.x * pad.getRadius() * pad.getPadDepth(), pad.getLocation().y + dir.y * pad.getRadius() * pad.getPadDepth());

        for (float side : new float[]{-1f, 1f}) {
            Shuttle shuttle = new Shuttle(hull, null, pad.faction, pad, pad, null, pad.getEnd(), pad.getEnd(), SpeedClass.MILITARY, 1f, 0f, random.nextFloat(),
                    ShuttleLook.CRAFT, GUARD_TRAITS);
            shuttle.pos.set(start.x - dir.y * 10f * side, start.y + dir.x * 10f * side);
            shuttle.beginSteer(new GuardSlot(guard, side), new Vector2f(dir.x * LAUNCH_SPEED, dir.y * LAUNCH_SPEED), true);
            shuttle.setGroup(ShuttleBudget.Group.ESCORT);
            budget.add(ShuttleBudget.Group.ESCORT, 1);
            guard.craft.add(shuttle);
            traffic.getShuttles().add(shuttle);
        }

        guards.put(pad.entity, guard);
    }

    // transponder off, going dark, out of range or a threat at the port: the pair breaks off; reactions put landing pickets on the screen
    private boolean shouldLeave(Guard guard) {
        CampaignFleetAPI fleet = guard.fleet;
        if (!fleet.isAlive() || fleet.getContainingLocation() != traffic.getLocation() || fleet.getBattle() != null) return true;
        if (guard.pad.threatened || guard.pad.closed || getGap(fleet, guard.pad) > LEAVE_RANGE) return true;
        return fleet.isPlayerFleet() && (!fleet.isTransponderOn() || isDark(fleet));
    }

    // Patrol HQ, Military Base or High Command, port open and not threatened
    private static boolean isGuardPort(ShuttleAnchor pad) {
        MarketAPI market = pad.market;
        if (market == null || pad.closed || pad.threatened || pad.type == ShuttleAnchor.Type.FLEET) return false;
        return market.hasIndustry(Industries.PATROLHQ) || market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND);
    }

    // own colonies and the commission faction always, anyone else at Welcoming or better
    private static boolean welcomes(MarketAPI market) {
        FactionAPI faction = market.getFaction();
        if (market.isPlayerOwned() || faction.isPlayerFaction()) return true;

        FactionAPI commission = Misc.getCommissionFaction();
        if (commission != null && commission == faction) return true;
        return faction.isAtWorst(Factions.PLAYER, RepLevel.WELCOMING);
    }

    static boolean isDark(CampaignFleetAPI fleet) {
        AbilityPlugin dark = fleet.getAbility(Abilities.GO_DARK);
        return dark != null && dark.isActive();
    }

    static float getGap(CampaignFleetAPI fleet, ShuttleAnchor pad) {
        return Misc.getDistance(fleet.getLocation(), pad.getLocation()) - pad.getRadius() - fleet.getRadius();
    }
}
