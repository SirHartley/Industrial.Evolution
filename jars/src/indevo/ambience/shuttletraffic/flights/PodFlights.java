package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.movement.ShuttlePath;
import indevo.config.Setting;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class PodFlights extends BaseFlights {

    private static final float POD_SPEED = 35f;
    private static final float POD_PLANET_RANGE = 2000f;
    private static final int POD_CAP = 32;
    private static final int MAX_PODS_PER_FLEET = 16;
    private static final int EMPTY_SNAPSHOT_PODS = 3;

    private final List<PodEvent> queue = new ArrayList<>();

    private record PodEvent(LocationAPI location, Vector2f point, PlanetAPI planet, int count) {
    }

    public PodFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    public void queue(CampaignFleetAPI fleet) {
        if (Setting.SHUTTLE_TRAFFIC.intValue() <= 0) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI fleetLocation = fleet.getContainingLocation();
        if (player == null || fleetLocation == null || fleetLocation != player.getContainingLocation() || fleetLocation.isHyperspace()) return;

        int count = getPodCount(fleet);
        if (count <= 0) return;

        PlanetAPI planet = findPodPlanet(fleetLocation, fleet.getLocation(), fleet.getFaction());
        if (planet != null) queue.add(new PodEvent(fleetLocation, new Vector2f(fleet.getLocation()), planet, count));
    }

    public void clearQueue() {
        queue.clear();
    }

    // listener callbacks arrive during dialogs, pods wait until the player sees the system again
    public void launchQueued() {
        if (queue.isEmpty() || Global.getSector().getCampaignUI().isShowingDialog()) return;

        List<Shuttle> pods = traffic.getPods();
        for (PodEvent event : queue) {
            int room = POD_CAP - pods.size();
            if (room <= 0) break;
            if (event.location() != traffic.getLocation() || !event.planet().isAlive()) continue;

            pods.addAll(launch(event.point(), event.planet(), Math.min(room, event.count())));
        }

        queue.clear();
    }

    private List<Shuttle> launch(Vector2f point, PlanetAPI planet, int count) {
        List<Shuttle> pods = new ArrayList<>(count);
        ShuttleAnchor target = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);

        for (int i = 0; i < count; i++) {
            ShuttleHull hull = ShuttleHullPool.getPod();
            if (hull == null) break;

            float angle = random.nextFloat() * 360f;
            float distance = Misc.getDistance(point, planet.getLocation());
            ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(point, Misc.getUnitVectorAtDegreeAngle(angle));
            ShuttlePath path = new ShuttlePath().add(new ShuttlePath.BezierSegment(start, new ShuttlePath.PadWaypoint(target, start, paths.spread(60f), 0f, true),
                    120f + random.nextFloat() * 80f, paths.getHandle(target, distance), 0f));

            Shuttle pod = new Shuttle(hull, null, null, target, target, path, Shuttle.End.NONE, Shuttle.End.PLANET,
                    POD_SPEED / hull.getCruiseSpeed(), 0f, random.nextFloat(), ShuttleLook.pod((20f + random.nextFloat() * 40f) * paths.randomSign()),
                    EnumSet.noneOf(Shuttle.Trait.class));
            pod.eject(random.nextFloat() * 1.5f, 45f + random.nextFloat() * 40f, 2f);
            pods.add(pod);
        }

        return pods;
    }

    private static int getPodCount(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> snapshot = fleet.getFleetData().getSnapshot();
        if (snapshot == null || snapshot.isEmpty()) return EMPTY_SNAPSHOT_PODS;

        int count = 0;
        for (FleetMemberAPI member : snapshot) {
            if (member.isFighterWing() || Misc.isAutomated(member) || member.getHullSpec().getMinCrew() <= 0f) continue;

            count += switch (member.getHullSpec().getHullSize()) {
                case FRIGATE, DESTROYER -> 1;
                case CRUISER -> 2;
                case CAPITAL_SHIP -> 3;
                default -> 0;
            };
        }

        return Math.min(MAX_PODS_PER_FLEET, count);
    }

    private static PlanetAPI findPodPlanet(LocationAPI location, Vector2f point, FactionAPI faction) {
        PlanetAPI nearest = null;
        PlanetAPI friendly = null;
        float nearestDist = POD_PLANET_RANGE;
        float friendlyDist = POD_PLANET_RANGE;

        for (PlanetAPI planet : location.getPlanets()) {
            if (planet.isStar()) continue;

            float dist = Misc.getDistance(point, planet.getLocation()) - planet.getRadius();
            if (dist > POD_PLANET_RANGE) continue;

            MarketAPI market = planet.getMarket();
            if (market != null && market.isInEconomy() && faction != null && !market.getFaction().isHostileTo(faction) && dist < friendlyDist) {
                friendlyDist = dist;
                friendly = planet;
            }

            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = planet;
            }
        }

        return friendly != null ? friendly : nearest;
    }
}
