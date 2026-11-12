package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleBudget;
import indevo.ambience.shuttletraffic.ShuttleFleetScan;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTarget;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.SpeedClass;
import indevo.config.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// craft between a market pad and a fleet that loads, unloads or prepares in orbit there
public class FleetFlights extends BaseFlights {

    private static final float ANCHOR_RANGE = 400f;
    private static final float ARRIVED_RANGE = 1500f;
    private static final float BASE_SHARE = 0.7f;
    private static final float TRADE_SPACE_PER_CRAFT = 200f;
    private static final int TRADE_MIN = 2;
    private static final int TRADE_MAX = 8;
    private static final float TRADE_BURST_TIME = 3f;
    private static final float TRADE_RANGE = 600f;
    private static final float RAID_BURST_TIME = 3f;
    private static final String RAID_FALLBACK = "talon";
    private static final Set<Shuttle.Trait> RAID_TRAITS = Collections.unmodifiableSet(EnumSet.of(Shuttle.Trait.AVOIDS));
    private static final float SURVEY_RANGE = 600f;
    private static final Set<String> SURVEY_HULLS = Set.of("shuttlepod", "kite", "mudskipper");
    private static final Set<Shuttle.Trait> SERVICE_TRAITS = Collections.unmodifiableSet(EnumSet.of(Shuttle.Trait.AVOIDS, Shuttle.Trait.FLEES));

    // cargo moved per market while the trade dialog is open
    private final Map<MarketAPI, Trade> trades = new LinkedHashMap<>();
    private final List<Raid> raids = new ArrayList<>();

    private record Raid(MarketAPI market, int count) {
    }

    private static final class Trade {
        float bought;
        float sold;
        boolean dark;
    }

    public FleetFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    public void queueTrade(PlayerMarketTransaction transaction) {
        MarketAPI market = transaction.getMarket();
        if (Setting.SHUTTLE_TRAFFIC.intValue() <= 0 || market == null) return;

        Trade trade = trades.computeIfAbsent(market, m -> new Trade());
        trade.bought += getMoved(transaction.getBought());
        trade.sold += getMoved(transaction.getSold());
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        boolean blackMarket = transaction.getSubmarket() != null && Submarkets.SUBMARKET_BLACK.equals(transaction.getSubmarket().getSpecId());
        trade.dark |= blackMarket || (player != null && !player.isTransponderOn());
    }

    public void queueRaid(MarketAPI market, int count) {
        if (Setting.SHUTTLE_TRAFFIC.intValue() > 0 && market != null) raids.add(new Raid(market, count));
    }

    public void clearQueue() {
        trades.clear();
        raids.clear();
    }

    // a burst of cargo craft between the pad and the player fleet once the dialog closes
    public void launchQueued() {
        if ((trades.isEmpty() && raids.isEmpty()) || Global.getSector().getCampaignUI().isShowingDialog()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || isFast(player)) {
            clearQueue();
            return;
        }

        for (Raid raid : raids) launchRaid(raid, player);
        raids.clear();

        for (Map.Entry<MarketAPI, Trade> entry : trades.entrySet()) {
            MarketAPI market = entry.getKey();
            Trade trade = entry.getValue();
            ShuttleAnchor pad = market.getPrimaryEntity() != null ? network.anchors.get(market.getPrimaryEntity()) : null;
            if (player == null || pad == null || pad.role != ShuttleAnchor.Role.MARKET || player.getContainingLocation() != traffic.getLocation()) continue;

            int count = Math.max(TRADE_MIN, Math.min(TRADE_MAX, Math.round((trade.bought + trade.sold) / TRADE_SPACE_PER_CRAFT)));
            launchTrade(pad, player, count, trade.bought >= trade.sold, trade.dark);
        }

        trades.clear();
    }

    private void launchTrade(ShuttleAnchor pad, CampaignFleetAPI player, int count, boolean toFleet, boolean dark) {
        if (!canFly(pad, pad) || Misc.getDistance(player.getLocation(), pad.getLocation()) - pad.getRadius() > TRADE_RANGE) return;

        String key = ShuttleRoute.Kind.TRADE.name() + "|" + pad.entity.getId();
        ShuttleRoute route = traffic.getFleetRoutes().get(key);
        if (route == null) {
            route = new ShuttleRoute(key, ShuttleRoute.Kind.TRADE, pad, ShuttleAnchor.forFleet(player), 0f);
            route.fleet = player;
            // the spawner never launches a trade route, the burst does
            route.max = 0;
            traffic.getFleetRoutes().put(key, route);
        }

        ShuttleBudget budget = traffic.getBudget();
        budget.count(traffic.getShuttles());
        int room = Math.min(count, budget.getRoom(ShuttleBudget.Group.FLEET));
        for (int i = 0; i < room; i++) {
            ShuttleHull hull = toFleet ? ShuttleHullPool.rollCargo(pad.faction, random, BASE_SHARE, true) : ShuttleHullPool.rollSmall(player.getFaction(), random);
            if (hull == null) hull = ShuttleHullPool.rollCargo(null, random, 1f, true);
            if (hull == null) return;

            ShuttleAnchor from = toFleet ? pad : route.b;
            ShuttleAnchor to = toFleet ? route.b : pad;
            Shuttle shuttle = create(hull, route, toFleet ? pad.faction : player.getFaction(), from, to, paths.servicePath(pad, player, toFleet, false), SpeedClass.SERVICE,
                    1f, 0f, dark ? ShuttleLook.SMUGGLER : ShuttleLook.SERVICE, SERVICE_TRAITS);
            shuttle.tieTo(player);
            shuttle.holdFor(TRADE_BURST_TIME * (i + random.nextFloat()) / room);
            shuttle.setGroup(ShuttleBudget.Group.FLEET);
            budget.add(ShuttleBudget.Group.FLEET, 1);
            route.active++;
            traffic.getShuttles().add(shuttle);
        }
    }

    // the raid party flies back up to the player fleet in the player's own fighters, at military speed
    private void launchRaid(Raid raid, CampaignFleetAPI player) {
        SectorEntityToken entity = raid.market().getPrimaryEntity();
        if (player == null || entity == null || entity.getContainingLocation() != traffic.getLocation() || player.getContainingLocation() != traffic.getLocation()) return;

        ShuttleAnchor pad = network.anchors.get(entity);
        if (pad == null) {
            ShuttleAnchor.Type type = entity instanceof PlanetAPI ? ShuttleAnchor.Type.PLANET : ShuttleAnchor.Type.STATION;
            pad = new ShuttleAnchor(entity, type, ShuttleAnchor.Role.TARGET, raid.market(), raid.market().getFaction());
        }

        ShuttleBudget budget = traffic.getBudget();
        budget.count(traffic.getShuttles());
        int room = Math.min(raid.count(), budget.getRoom(ShuttleBudget.Group.FLEET));
        ShuttleAnchor fleetEnd = ShuttleAnchor.forFleet(player);
        for (int i = 0; i < room; i++) {
            ShuttleHull hull = ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(player.getFaction(), true), random, h -> h.fighter);
            if (hull == null) hull = ShuttleHullPool.getHull(RAID_FALLBACK, true, 1);
            if (hull == null) return;

            Shuttle shuttle = create(hull, null, player.getFaction(), pad, fleetEnd, paths.servicePath(pad, player, true, false), SpeedClass.MILITARY, 1f, 0f,
                    ShuttleLook.CRAFT, RAID_TRAITS);
            shuttle.tieTo(player);
            shuttle.holdFor(RAID_BURST_TIME * (i + random.nextFloat()) / room);
            shuttle.setGroup(ShuttleBudget.Group.FLEET);
            budget.add(ShuttleBudget.Group.FLEET, 1);
            traffic.getShuttles().add(shuttle);
        }
    }

    // cargo, fuel and crew all ride the same shuttles
    private static float getMoved(CargoAPI cargo) {
        return cargo == null ? 0f : cargo.getSpaceUsed() + cargo.getFuel() + cargo.getTotalPersonnel();
    }

    // after every fleet scan: routes for serviced fleets, the rest end
    public void update() {
        Map<String, ShuttleRoute> routes = traffic.getFleetRoutes();
        for (ShuttleRoute route : routes.values()) if (route.kind == ShuttleRoute.Kind.FLEET_SERVICE || route.kind == ShuttleRoute.Kind.SURVEY) route.seen = false;

        ShuttleFleetScan scan = traffic.getFleetScan();
        for (int i = 0; i < scan.getCount(); i++) {
            ShuttleFleetScan.Entry entry = scan.get(i);
            if (isSurveying(entry)) {
                updateSurvey(entry, routes);
                continue;
            }

            ShuttleAnchor pad = findServicePad(entry);
            if (pad == null) continue;

            String key = ShuttleRoute.Kind.FLEET_SERVICE.name() + "|" + entry.fleet.getId();
            ShuttleRoute route = routes.get(key);
            if (route == null || route.a != pad) {
                if (route != null) end(route);
                route = new ShuttleRoute(key, ShuttleRoute.Kind.FLEET_SERVICE, pad, ShuttleAnchor.forFleet(entry.fleet), 0f);
                route.fleet = entry.fleet;
                route.timer = random.nextFloat() * 2f;
                route.demand = getServiceCount(entry.fleet, entry.type, pad);
                route.max = route.demand;
                routes.put(key, route);
            }

            route.seen = true;
        }

        Iterator<ShuttleRoute> iterator = routes.values().iterator();
        while (iterator.hasNext()) {
            ShuttleRoute route = iterator.next();
            boolean over = switch (route.kind) {
                case FLEET_SERVICE -> !route.seen;
                // survey craft fly back on their own, they only go with the fleet
                case SURVEY -> !route.seen && (route.active <= 0 || !route.fleet.isAlive() || route.fleet.getContainingLocation() != traffic.getLocation());
                // the burst only shows while the player stays near the pad
                case TRADE -> route.active <= 0 || route.fleet.getContainingLocation() != traffic.getLocation() || isFast(route.fleet)
                        || Misc.getDistance(route.fleet.getLocation(), route.a.getLocation()) - route.a.getRadius() > TRADE_RANGE;
                default -> false;
            };
            if (!over) continue;

            end(route);
            iterator.remove();
        }

        turnBackFromFastFleets();
    }

    public static boolean isFast(CampaignFleetAPI fleet) {
        return fleet.getVelocity().length() >= SpeedClass.FLEET_TIED_MAX_SPEED;
    }

    // craft bound for a fleet that got fast turn back to where they came from, or the nearest refuge; craft from it carry on
    private void turnBackFromFastFleets() {
        for (Shuttle shuttle : traffic.getShuttles()) {
            CampaignFleetAPI fleet = shuttle.getTiedFleet();
            ShuttleAnchor to = shuttle.getTo();
            if (fleet == null || to == null || to.type != ShuttleAnchor.Type.FLEET || !isFast(fleet) || !shuttle.canRedirect() || shuttle.isFollower()) continue;

            ShuttleAnchor back = shuttle.getFrom();
            if (back == null || back.type == ShuttleAnchor.Type.FLEET || !back.isUsable(traffic.getLocation())) {
                back = network.findRefuge(null, shuttle.faction, shuttle.pos, traffic.getLocation());
            }

            if (back != null) shuttle.redirect(back, traffic);
            else shuttle.finish();
        }
    }

    public Shuttle launchService(ShuttleRoute route) {
        CampaignFleetAPI fleet = route.fleet;
        ShuttleAnchor pad = route.a;
        if (fleet == null || !fleet.isAlive() || !canFly(pad, pad)) return null;

        String type = fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
        boolean smuggler = FleetTypes.TRADE_SMUGGLER.equals(type);
        boolean patrol = isPatrol(type);
        boolean toFleet = random.nextBoolean();

        // a fleet sends its own boats down, the port sends base or local craft up
        FactionAPI faction = toFleet ? pad.faction : fleet.getFaction();
        ShuttleHull hull = toFleet ? ShuttleHullPool.rollCargo(pad.faction, random, BASE_SHARE, true) : ShuttleHullPool.rollSmall(fleet.getFaction(), random);
        if (hull == null) return null;

        ShuttleAnchor from = toFleet ? pad : route.b;
        ShuttleAnchor to = toFleet ? route.b : pad;
        Shuttle shuttle = create(hull, route, faction, from, to, paths.servicePath(pad, fleet, toFleet, smuggler), patrol ? SpeedClass.CIVIL : SpeedClass.SERVICE, 1f,
                0f, smuggler ? ShuttleLook.SMUGGLER : ShuttleLook.SERVICE, SERVICE_TRAITS);
        shuttle.tieTo(fleet);
        return shuttle;
    }

    // 1 to 3 shuttles per investigation, launched once each; the route stays while the fleet investigates so it never sends more
    private void updateSurvey(ShuttleFleetScan.Entry entry, Map<String, ShuttleRoute> routes) {
        String key = ShuttleRoute.Kind.SURVEY.name() + "|" + entry.fleet.getId() + "|" + entry.target.getId();
        ShuttleRoute route = routes.get(key);
        if (route == null) {
            route = new ShuttleRoute(key, ShuttleRoute.Kind.SURVEY, ShuttleAnchor.forFleet(entry.fleet), null, 0f);
            route.fleet = entry.fleet;
            route.target = new ShuttleTarget(entry.target, 0f, 0f, 0f, 0f);
            // demand counts the launches left, max the craft in the air
            route.demand = 1 + random.nextInt(3);
            route.max = route.demand;
            route.timer = random.nextFloat() * 3f;
            routes.put(key, route);
        }

        route.seen = true;
    }

    private boolean isSurveying(ShuttleFleetScan.Entry entry) {
        CampaignFleetAPI fleet = entry.fleet;
        if (!ShuttleFleetScan.isScavenger(entry.type) || entry.assignment != FleetAssignment.PATROL_SYSTEM || entry.target == null) return false;
        if (fleet.getVisibilityLevelToPlayerFleet() == SectorEntityToken.VisibilityLevel.NONE || isFast(fleet)) return false;

        SectorEntityToken target = entry.target;
        return target.isAlive() && target.getContainingLocation() == traffic.getLocation()
                && Misc.getDistance(fleet.getLocation(), target.getLocation()) - target.getRadius() <= SURVEY_RANGE;
    }

    public Shuttle launchSurvey(ShuttleRoute route) {
        CampaignFleetAPI fleet = route.fleet;
        SectorEntityToken target = route.target.center;
        if (!route.seen || route.demand <= 0 || fleet == null || !fleet.isAlive() || !target.isAlive()) return null;

        ShuttleHull hull = ShuttleHullPool.rollBase(random, SURVEY_HULLS);
        if (hull == null) return null;

        route.demand--;
        ShuttleAnchor home = route.a;
        if (target instanceof PlanetAPI planet && !planet.isStar()) {
            ShuttleAnchor landing = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);
            Shuttle shuttle = create(hull, route, fleet.getFaction(), home, landing, paths.surveyLandPath(fleet, landing), SpeedClass.SERVICE, 1f, 0f,
                    ShuttleLook.HUNT, SERVICE_TRAITS);
            shuttle.stay(1f + random.nextFloat());
            shuttle.tieTo(fleet);
            return shuttle;
        }

        Shuttle shuttle = create(hull, route, fleet.getFaction(), home, home, paths.surveyLapPath(fleet, target), SpeedClass.SERVICE, 1f, 0f, ShuttleLook.HUNT, SERVICE_TRAITS);
        shuttle.tieTo(fleet);
        return shuttle;
    }

    // no more launches; craft flying to the fleet turn back to the pad, craft from it carry on, all go when the fleet is out of sight
    private void end(ShuttleRoute route) {
        boolean seen = route.fleet != null && route.fleet.isAlive() && route.fleet.getContainingLocation() == traffic.getLocation()
                && (route.fleet.isPlayerFleet() || route.fleet.getSensorFaderBrightness() > 0.01f);

        for (Shuttle shuttle : traffic.getShuttles()) {
            if (shuttle.getRoute() != route || shuttle.isDone()) continue;

            if (!seen) {
                shuttle.finish();
                continue;
            }

            if (shuttle.getTo() == route.b && shuttle.canRedirect() && route.a.isUsable(traffic.getLocation())) shuttle.redirect(route.a, traffic);
        }
    }

    private ShuttleAnchor findServicePad(ShuttleFleetScan.Entry entry) {
        CampaignFleetAPI fleet = entry.fleet;
        if (fleet == null || fleet.isPlayerFleet() || fleet.isStationMode() || !fleet.isAlive()) return null;
        if (entry.assignment != FleetAssignment.ORBIT_PASSIVE || isFast(fleet)) return null;
        if (fleet.getVisibilityLevelToPlayerFleet() == SectorEntityToken.VisibilityLevel.NONE) return null;

        ShuttleAnchor pad = entry.target != null ? network.anchors.get(entry.target) : null;
        if (pad == null || pad.role != ShuttleAnchor.Role.MARKET) pad = findNearestMarket(fleet);
        if (pad == null || !pad.isUsable(traffic.getLocation()) || pad.faction == null || pad.faction.isHostileTo(fleet.getFaction())) return null;

        float gap = Misc.getDistance(fleet.getLocation(), pad.getLocation()) - pad.getRadius();
        return gap <= ARRIVED_RANGE ? pad : null;
    }

    private ShuttleAnchor findNearestMarket(CampaignFleetAPI fleet) {
        ShuttleAnchor best = null;
        float bestGap = ANCHOR_RANGE;
        for (ShuttleAnchor anchor : network.anchors.values()) {
            if (anchor.role != ShuttleAnchor.Role.MARKET) continue;

            float gap = Misc.getDistance(fleet.getLocation(), anchor.getLocation()) - anchor.getRadius() - fleet.getRadius();
            if (gap < bestGap) {
                bestGap = gap;
                best = anchor;
            }
        }
        return best;
    }

    // smugglers and patrols get 2 or 3 craft, everyone else a cargo stream by fleet size and port density
    private int getServiceCount(CampaignFleetAPI fleet, String type, ShuttleAnchor pad) {
        if (FleetTypes.TRADE_SMUGGLER.equals(type) || isPatrol(type)) return 2 + random.nextInt(2);

        int stream = Math.max(3, Math.min(8, 2 + fleet.getNumShips() / 3));
        return Math.max(1, Math.round(stream * pad.density));
    }

    private static boolean isPatrol(String type) {
        return FleetTypes.PATROL_SMALL.equals(type) || FleetTypes.PATROL_MEDIUM.equals(type) || FleetTypes.PATROL_LARGE.equals(type);
    }
}
