package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleBudget;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTarget;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.SpeedClass;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

// craft for one off events: a maintenance run to a captured objective, settlers for a new colony, tugs after a battle
public class EventFlights extends BaseFlights {

    private static final float SETTLE_DAYS = 5f;
    private static final float SETTLER_GAP_MIN = 3f;
    private static final float SETTLER_GAP_MAX = 6f;
    private static final float SETTLER_FLEET_RANGE = 2000f;
    private static final float RECOVERY_DAYS = 1f;
    private static final float RECOVERY_WANDER = 80f;
    private static final Set<String> TUG_HULLS = Set.of("ox", "mudskipper", "shepherd");
    private static final Set<Shuttle.Trait> TRAITS = Collections.unmodifiableSet(EnumSet.of(Shuttle.Trait.AVOIDS, Shuttle.Trait.FLEES));

    private final LocalFlights local;
    private final List<Objective> objectives = new ArrayList<>();
    private final List<Settlement> settlements = new ArrayList<>();
    private final List<Recovery> recoveries = new ArrayList<>();

    private record Objective(SectorEntityToken entity, FactionAPI owner) {
    }

    private record Recovery(LocationAPI location, Vector2f point, FactionAPI winner) {
    }

    private static final class Settlement {
        final PlanetAPI planet;
        float endDay = -1f;
        float timer = 0f;

        Settlement(PlanetAPI planet) {
            this.planet = planet;
        }
    }

    EventFlights(ShuttleTraffic traffic, ShuttlePaths paths, LocalFlights local) {
        super(traffic, paths);
        this.local = local;
    }

    public void queueObjective(SectorEntityToken objective, FactionAPI owner) {
        if (objective != null && owner != null) objectives.add(new Objective(objective, owner));
    }

    public void queueSettlers(PlanetAPI planet) {
        if (planet != null) settlements.add(new Settlement(planet));
    }

    public void queueRecovery(LocationAPI location, Vector2f point, FactionAPI winner) {
        recoveries.add(new Recovery(location, new Vector2f(point), winner));
    }

    public void clear() {
        objectives.clear();
        settlements.clear();
        recoveries.clear();
    }

    // after every fleet scan; callbacks arrive during dialogs, so everything waits until the player sees the system again
    public void update() {
        if (Global.getSector().getCampaignUI().isShowingDialog()) return;

        LocationAPI location = traffic.getLocation();
        for (Objective objective : objectives) {
            if (objective.entity().getContainingLocation() == location) launchMaintenance(objective);
        }
        objectives.clear();

        for (Recovery recovery : recoveries) {
            if (recovery.location() == location) launchRecovery(recovery);
        }
        recoveries.clear();

        Iterator<Settlement> iterator = settlements.iterator();
        while (iterator.hasNext()) {
            Settlement settlement = iterator.next();
            if (!settlement.planet.isAlive()) {
                iterator.remove();
                continue;
            }
            if (settlement.planet.getContainingLocation() != location) continue;
            if (settlement.endDay < 0f) settlement.endDay = traffic.getDays() + SETTLE_DAYS;
            if (traffic.getDays() > settlement.endDay) {
                iterator.remove();
                continue;
            }

            settlement.timer -= ShuttleTraffic.SCAN_INTERVAL;
            if (settlement.timer > 0f) continue;
            settlement.timer = SETTLER_GAP_MIN + random.nextFloat() * (SETTLER_GAP_MAX - SETTLER_GAP_MIN);
            launchSettler(settlement.planet);
        }
    }

    // the new owner's nearest market runs a maintenance lap at once
    private void launchMaintenance(Objective objective) {
        ShuttleAnchor home = nearestMarket(objective.entity().getLocation(), a -> a.faction != null && a.faction.getId().equals(objective.owner().getId()));
        if (home == null) return;

        ShuttleRoute route = new ShuttleRoute(ShuttleRoute.Kind.MAINTENANCE.name() + "|event|" + objective.entity().getId(), ShuttleRoute.Kind.MAINTENANCE, home, null, 0f);
        route.target = new ShuttleTarget(objective.entity(), 0f, 0f, 0f, 0f);
        add(local.launchMaintenance(route), ShuttleBudget.Group.RARE);
    }

    // from the player fleet while it is near and slow, else from the nearest market, to the new colony
    private void launchSettler(PlanetAPI planet) {
        ShuttleAnchor pad = network.anchors.get(planet);
        if (pad == null) pad = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), planet.getFaction());
        if (!pad.isUsable(traffic.getLocation())) return;

        FactionAPI faction = Global.getSector().getPlayerFaction();
        ShuttleHull hull = ShuttleHullPool.rollCargo(faction, random, 0.6f, false);
        if (hull == null) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        ShuttleAnchor colony = pad;
        if (player != null && player.getContainingLocation() == traffic.getLocation() && !FleetFlights.isFast(player, pad) && Misc.getDistance(player.getLocation(), planet.getLocation()) <= SETTLER_FLEET_RANGE) {
            Shuttle shuttle = create(hull, null, faction, ShuttleAnchor.forFleet(player), pad, paths.servicePath(pad, player, false, false), SpeedClass.SERVICE, 1f, 0f,
                    ShuttleLook.SERVICE, TRAITS);
            shuttle.tieTo(player);
            add(shuttle, ShuttleBudget.Group.FLEET);
            return;
        }

        ShuttleAnchor from = nearestMarket(planet.getLocation(), a -> a != colony && a.faction != null && !a.faction.isHostileTo(faction));
        if (from == null || !canFly(from, pad)) return;
        add(create(hull, null, faction, from, pad, paths.lanePath(from, pad, ShuttleRoute.rollBend(random), false), SpeedClass.CIVIL, 1f, LANE_JITTER,
                ShuttleLook.CRAFT, TRAITS), ShuttleBudget.Group.FLEET);
    }

    // 1 or 2 tugs from the nearest non hostile market that reaches the battle point within a day, circling it for a day
    private void launchRecovery(Recovery recovery) {
        FactionAPI winner = recovery.winner();
        float speed = SpeedClass.MILITARY.getMeanSpeed();
        ShuttleAnchor home = nearestMarket(recovery.point(), a -> a.faction != null && (winner == null || !a.faction.isHostileTo(winner))
                && SpeedClass.canReach(Misc.getDistance(a.getLocation(), recovery.point()) - a.getRadius(), speed, RECOVERY_DAYS));
        if (home == null || !canFly(home, home)) return;

        SectorEntityToken point = traffic.getLocation().createToken(recovery.point().x, recovery.point().y);
        ShuttleTarget target = new ShuttleTarget(point, 0f, 0f, 0f, RECOVERY_WANDER);
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            ShuttleHull hull = ShuttleHullPool.rollBase(random, TUG_HULLS);
            if (hull == null) return;

            Shuttle tug = create(hull, null, home.faction, home, home, paths.lingerPath(home, target, RECOVERY_DAYS, SpeedClass.MILITARY.getCruiseSpeed(hull)),
                    SpeedClass.MILITARY, 1f, LANE_JITTER, ShuttleLook.CRAFT, Shuttle.CIVIL);
            tug.holdFor(i * 1.5f);
            add(tug, ShuttleBudget.Group.FLEET);
        }
    }

    private ShuttleAnchor nearestMarket(Vector2f point, Predicate<ShuttleAnchor> filter) {
        ShuttleAnchor best = null;
        float bestDist = Float.MAX_VALUE;
        for (ShuttleAnchor anchor : network.marketAnchors) {
            if (!anchor.isUsable(traffic.getLocation()) || !filter.test(anchor)) continue;

            float dist = Misc.getDistance(anchor.getLocation(), point);
            if (dist < bestDist) {
                bestDist = dist;
                best = anchor;
            }
        }
        return best;
    }

    private void add(Shuttle shuttle, ShuttleBudget.Group group) {
        if (shuttle == null) return;

        ShuttleBudget budget = traffic.getBudget();
        budget.count(traffic.getShuttles());
        if (budget.getRoom(group) <= 0) return;

        shuttle.setGroup(group);
        budget.add(group, 1);
        traffic.getShuttles().add(shuttle);
    }
}
