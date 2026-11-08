package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ShuttleTrafficRenderer implements LunaCampaignRenderingPlugin {

    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_3, CampaignEngineLayers.BELOW_STATIONS, CampaignEngineLayers.TERRAIN_7);
    private static final float SCAN_INTERVAL = 0.25f;
    private static final float FADE_ZOOM_START = 2f;
    private static final float FADE_ZOOM_END = 3f;
    private static final float VIEWPORT_MARGIN = 300f;
    private static final float ANCHOR_THREAT_RANGE = 600f;
    private static final float CRAFT_THREAT_RANGE = 400f;
    private static final float STRANDED_CLEAR_RANGE = 800f;
    private static final float POD_PLANET_RANGE = 2000f;
    private static final int POD_CAP = 32;
    private static final int MAX_PODS_PER_FLEET = 16;
    private static final int EMPTY_SNAPSHOT_PODS = 3;

    private record PodEvent(LocationAPI location, Vector2f point, PlanetAPI planet, int count) {
    }

    private final ShuttleNetwork network = new ShuttleNetwork();
    private final Random random = new Random();
    private final ShuttleFlights flights = new ShuttleFlights(this, network, random);
    private final List<Shuttle> shuttles = new ArrayList<>();
    private final List<Shuttle> pods = new ArrayList<>();
    private final List<PodEvent> podQueue = new ArrayList<>();
    private final ShuttleFleetScan fleetScan = new ShuttleFleetScan();
    private final SpriteAPI glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

    private LocationAPI location;
    private float time = 0f;
    private float days = 0f;
    private float daysSinceRebuild = 0f;
    private float scanTimer = 0f;

    public static void register() {
        if (LunaCampaignRenderer.hasRendererOfClass(ShuttleTrafficRenderer.class)) return;

        ShuttleHullPool.reset();
        ShuttleTrafficRenderer renderer = new ShuttleTrafficRenderer();
        LunaCampaignRenderer.addTransientRenderer(renderer);
        Global.getSector().addTransientListener(new EscapePodListener(renderer));
    }

    public static Shuttle.End getEnd(ShuttleAnchor anchor) {
        return anchor.type == ShuttleAnchor.Type.PLANET ? Shuttle.End.PLANET : Shuttle.End.STATION;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return LAYERS;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().isPaused()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI current = player != null ? player.getContainingLocation() : null;
        int cap = Setting.SHUTTLE_TRAFFIC.intValue();

        if (cap <= 0 || current == null || current.isHyperspace()) {
            if (location != null) clear();
            podQueue.clear();
            return;
        }

        time += amount;
        float elapsedDays = Global.getSector().getClock().convertToDays(amount);
        days += elapsedDays;

        if (current != location) {
            clear();
            location = current;
            network.rebuild(location, random);
            fleetScan.scan(location, player);
            updateThreats();
            warmStart(cap);
        } else {
            daysSinceRebuild += elapsedDays;
            if (daysSinceRebuild >= 1f) {
                daysSinceRebuild = 0f;
                network.rebuild(location, random);
                rollRareFlights();
            }
        }

        scanTimer -= amount;
        if (scanTimer <= 0f) {
            scanTimer = SCAN_INTERVAL;
            fleetScan.scan(location, player);
            updateThreats();
            react();
        }

        spawn(amount, cap);
        launchQueuedPods();
        advance(shuttles, amount);
        advance(pods, amount);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (location == null || location != Global.getSector().getCurrentLocation()) return;
        if (shuttles.isEmpty() && pods.isEmpty()) return;

        float zoom = viewport.getViewMult();
        float fade = Math.max(0f, Math.min(1f, (zoom - FADE_ZOOM_START) / (FADE_ZOOM_END - FADE_ZOOM_START)));
        float alphaMult = viewport.getAlphaMult() * (1f - fade);
        if (alphaMult <= 0.01f) return;

        render(shuttles, layer, viewport, alphaMult);
        render(pods, layer, viewport, alphaMult);
    }

    public void queuePods(CampaignFleetAPI fleet) {
        if (Setting.SHUTTLE_TRAFFIC.intValue() <= 0) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI fleetLocation = fleet.getContainingLocation();
        if (player == null || fleetLocation == null || fleetLocation != player.getContainingLocation() || fleetLocation.isHyperspace()) return;

        int count = getPodCount(fleet);
        if (count <= 0) return;

        PlanetAPI planet = findPodPlanet(fleetLocation, fleet.getLocation(), fleet.getFaction());
        if (planet != null) podQueue.add(new PodEvent(fleetLocation, new Vector2f(fleet.getLocation()), planet, count));
    }

    public float getTime() {
        return time;
    }

    public float getDays() {
        return days;
    }

    public LocationAPI getLocation() {
        return location;
    }

    public ShuttleFleetScan getFleetScan() {
        return fleetScan;
    }

    public ShuttleFlights getFlights() {
        return flights;
    }

    public Random getRandom() {
        return random;
    }

    private void clear() {
        shuttles.clear();
        pods.clear();
        network.clear();
        fleetScan.clear();
        location = null;
        daysSinceRebuild = 0f;
        scanTimer = 0f;
    }

    private void warmStart(int cap) {
        for (ShuttleRoute route : network.routes.values()) {
            if (route.isRare()) continue;

            for (int i = 0; i < route.max; i++) {
                List<Shuttle> launched = flights.launch(route, getRoom(route, cap));
                if (launched.isEmpty()) break;

                float fraction = Math.min(0.95f, (i + random.nextFloat()) / route.max);
                for (Shuttle shuttle : launched) shuttle.warmStart(fraction);
                route.active++;
                shuttles.addAll(launched);
            }
        }
    }

    private void rollRareFlights() {
        for (ShuttleRoute route : network.routes.values()) {
            if (route.isRare() && !route.pending && route.active < route.max && random.nextFloat() < route.chancePerDay) route.pending = true;
        }
    }

    private void spawn(float amount, int cap) {
        for (ShuttleRoute route : network.routes.values()) {
            if (route.active >= route.max) continue;

            if (route.isRare()) {
                if (!route.pending) continue;
            } else {
                route.timer -= amount;
                if (route.timer > 0f) continue;
                route.timer = getInterval(route);
            }

            List<Shuttle> launched = flights.launch(route, getRoom(route, cap));
            if (launched.isEmpty()) continue;

            route.pending = false;
            route.active++;
            shuttles.addAll(launched);
        }
    }

    private int getRoom(ShuttleRoute route, int cap) {
        return (int) (cap * route.kind.capShare) - shuttles.size();
    }

    private float getInterval(ShuttleRoute route) {
        float jitter = 0.6f + 0.8f * random.nextFloat();
        if (route.b == null) return route.kind.pause * jitter;

        float flightTime = Misc.getDistance(route.a.getLocation(), route.b.getLocation()) / 27f + 6f;
        return flightTime / Math.max(1, route.max) * jitter;
    }

    private void updateThreats() {
        for (ShuttleAnchor anchor : network.anchors.values()) {
            anchor.threatened = false;
            anchor.overrun = false;
            if (anchor.role == ShuttleAnchor.Role.REMNANT || anchor.faction == null) continue;

            Vector2f loc = anchor.getLocation();
            if (fleetScan.findThreat(loc.x, loc.y, anchor.getRadius() + ANCHOR_THREAT_RANGE, anchor.faction) < 0) continue;

            anchor.threatened = true;
            anchor.lastThreatDay = days;
            anchor.overrun = fleetScan.findThreat(loc.x, loc.y, anchor.getRadius(), anchor.faction) >= 0;
        }
    }

    private void react() {
        for (Shuttle shuttle : shuttles) {
            if (shuttle.done || shuttle.isDown() || shuttle.leader != null || shuttle.isWaiting()) continue;

            if (shuttle.isStranded()) {
                if (fleetScan.findThreat(shuttle.pos.x, shuttle.pos.y, STRANDED_CLEAR_RANGE, shuttle.faction) >= 0) shuttle.lastThreatNearDay = days;
                continue;
            }

            if (shuttle.picket) {
                int threat = shuttle.home != null && shuttle.home.threatened ? findAnchorThreat(shuttle.home) : -1;
                if (shuttle.home != null && shuttle.home.overrun) flee(shuttle, threat);
                else if (threat >= 0) shuttle.screen(fleetScan.x[threat], fleetScan.y[threat], this);
                else divertIfClosed(shuttle);
                continue;
            }

            if (!shuttle.flees) {
                divertIfClosed(shuttle);
                continue;
            }

            boolean destinationThreatened = shuttle.to != null && shuttle.to.threatened;
            int near = fleetScan.findThreat(shuttle.pos.x, shuttle.pos.y, CRAFT_THREAT_RANGE, shuttle.faction);

            if (destinationThreatened || (near >= 0 && !shuttle.isFleeing())) {
                flee(shuttle, near >= 0 ? near : findAnchorThreat(shuttle.to));
                continue;
            }

            divertIfClosed(shuttle);
        }
    }

    private void flee(Shuttle shuttle, int threat) {
        float threatX = threat >= 0 ? fleetScan.x[threat] : shuttle.pos.x;
        float threatY = threat >= 0 ? fleetScan.y[threat] : shuttle.pos.y;

        List<Shuttle> wing = new ArrayList<>(shuttle.followers);
        shuttle.flee(network.findRefuge(null, shuttle.faction, shuttle.pos, location), threatX, threatY, this);
        for (Shuttle follower : wing) follower.flee(network.findRefuge(null, follower.faction, follower.pos, location), threatX, threatY, this);
    }

    private int findAnchorThreat(ShuttleAnchor anchor) {
        if (anchor == null) return -1;

        Vector2f loc = anchor.getLocation();
        return fleetScan.findThreat(loc.x, loc.y, anchor.getRadius() + ANCHOR_THREAT_RANGE, anchor.faction);
    }

    private void divertIfClosed(Shuttle shuttle) {
        if (shuttle.to == null || shuttle.isScreening() || shuttle.to.isUsable(location)) return;

        ShuttleAnchor refuge = network.findRefuge(shuttle.to, shuttle.faction, shuttle.pos, location);
        if (refuge != null) shuttle.redirect(refuge, this);
        else if (!shuttle.to.entity.isAlive() || shuttle.to.entity.getContainingLocation() != location) shuttle.done = true;
    }

    private void launchQueuedPods() {
        if (podQueue.isEmpty() || Global.getSector().getCampaignUI().isShowingDialog()) return;

        for (PodEvent event : podQueue) {
            int room = POD_CAP - pods.size();
            if (room <= 0) break;
            if (event.location() != location || !event.planet().isAlive()) continue;

            pods.addAll(flights.launchPods(event.point(), event.planet(), Math.min(room, event.count())));
        }

        podQueue.clear();
    }

    private void advance(List<Shuttle> list, float amount) {
        Iterator<Shuttle> iterator = list.iterator();
        while (iterator.hasNext()) {
            Shuttle shuttle = iterator.next();
            shuttle.advance(amount, this);
            if (!shuttle.done) continue;

            if (shuttle.route != null) shuttle.route.active--;
            iterator.remove();
        }
    }

    private void render(List<Shuttle> list, CampaignEngineLayers layer, ViewportAPI viewport, float alphaMult) {
        for (Shuttle shuttle : list) {
            if (shuttle.layer != layer || !viewport.isNearViewport(shuttle.pos, VIEWPORT_MARGIN)) continue;
            shuttle.render(alphaMult, glow);
        }
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
