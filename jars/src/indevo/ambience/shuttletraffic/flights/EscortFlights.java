package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.campaign.RepLevel;
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

// fighters that fly out to meet a fleet approaching a military market and lead it in, free steering at dash speed
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
    private static final Set<Shuttle.Trait> GUARD_TRAITS = Collections.unmodifiableSet(EnumSet.of(Shuttle.Trait.AVOIDS, Shuttle.Trait.QUEUES,
            Shuttle.Trait.FLEES, Shuttle.Trait.PICKET));

    // keyed by the market's anchor entity: the player's guard and one NPC guard per market at a time
    private final Map<SectorEntityToken, Guard> guards = new LinkedHashMap<>();
    // last guard per market and fleet, so one approach gets one guard
    private final Map<String, Float> lastGuardDay = new HashMap<>();

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

    public EscortFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    public void clear() {
        guards.clear();
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
