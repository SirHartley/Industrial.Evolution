package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
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
    private static final float LIGHTS_ZOOM = 0.75f;
    private static final float VIEWPORT_MARGIN = 300f;
    private static final float LANE_OFFSET = 40f;
    private static final float LANE_JITTER = 10f;
    private static final float HANDLE_EXTRA = 80f;

    private final ShuttleNetwork network = new ShuttleNetwork();
    private final List<Shuttle> shuttles = new ArrayList<>();
    private final ShuttleFleetScan fleetScan = new ShuttleFleetScan();
    private final Random random = new Random();
    private final SpriteAPI glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

    private LocationAPI location;
    private float time = 0f;
    private float daysSinceRebuild = 0f;
    private float scanTimer = 0f;

    public static void register() {
        if (LunaCampaignRenderer.hasRendererOfClass(ShuttleTrafficRenderer.class)) return;
        ShuttleHullPool.reset();
        LunaCampaignRenderer.addTransientRenderer(new ShuttleTrafficRenderer());
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
            return;
        }

        time += amount;

        if (current != location) {
            clear();
            location = current;
            network.rebuild(location, random);
            fleetScan.scan(location, player);
            warmStart(cap);
        } else {
            daysSinceRebuild += Global.getSector().getClock().convertToDays(amount);
            if (daysSinceRebuild >= 1f) {
                daysSinceRebuild = 0f;
                network.rebuild(location, random);
            }
        }

        scanTimer -= amount;
        if (scanTimer <= 0f) {
            scanTimer = SCAN_INTERVAL;
            fleetScan.scan(location, player);
            divertFromClosed();
        }

        spawn(amount, cap);

        Iterator<Shuttle> iterator = shuttles.iterator();
        while (iterator.hasNext()) {
            Shuttle shuttle = iterator.next();
            shuttle.advance(amount, this);
            if (!shuttle.done) continue;

            if (shuttle.route != null) shuttle.route.active--;
            iterator.remove();
        }
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (location == null || shuttles.isEmpty() || location != Global.getSector().getCurrentLocation()) return;

        float zoom = viewport.getViewMult();
        float fade = Math.max(0f, Math.min(1f, (zoom - FADE_ZOOM_START) / (FADE_ZOOM_END - FADE_ZOOM_START)));
        float alphaMult = viewport.getAlphaMult() * (1f - fade);
        if (alphaMult <= 0.01f) return;

        boolean lights = zoom < LIGHTS_ZOOM;
        for (Shuttle shuttle : shuttles) {
            if (shuttle.layer != layer || !viewport.isNearViewport(shuttle.pos, VIEWPORT_MARGIN)) continue;
            shuttle.render(alphaMult, glow, lights, time);
        }
    }

    public float getTime() {
        return time;
    }

    public LocationAPI getLocation() {
        return location;
    }

    public ShuttleFleetScan getFleetScan() {
        return fleetScan;
    }

    private void clear() {
        shuttles.clear();
        network.clear();
        fleetScan.clear();
        location = null;
        daysSinceRebuild = 0f;
        scanTimer = 0f;
    }

    private void warmStart(int cap) {
        for (ShuttleRoute route : network.routes.values()) {
            for (int i = 0; i < route.max; i++) {
                if (shuttles.size() >= cap * route.kind.capShare) break;

                Shuttle shuttle = launch(route);
                if (shuttle == null) break;
                shuttle.warmStart(Math.min(0.95f, (i + random.nextFloat()) / route.max));
            }
        }
    }

    private void spawn(float amount, int cap) {
        for (ShuttleRoute route : network.routes.values()) {
            if (route.active >= route.max) continue;

            route.timer -= amount;
            if (route.timer > 0f) continue;

            route.timer = getInterval(route);
            if (shuttles.size() >= cap * route.kind.capShare) continue;
            launch(route);
        }
    }

    private float getInterval(ShuttleRoute route) {
        float flightTime = Misc.getDistance(route.a.getLocation(), route.b.getLocation()) / 27f + 6f;
        return flightTime / Math.max(1, route.max) * (0.6f + 0.8f * random.nextFloat());
    }

    private Shuttle launch(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        ShuttleAnchor to = route.b;
        if (random.nextBoolean()) {
            from = route.b;
            to = route.a;
        }

        if (!from.isUsable(location) || !to.isUsable(location)) return null;

        ShuttleHull hull = route.kind == ShuttleRoute.Kind.SUPPLY
                ? ShuttleHullPool.rollCargo(from.faction, random, 0.8f, true)
                : ShuttleHullPool.rollCargo(from.faction, random, 0.6f, false);
        if (hull == null) return null;

        Vector2f p0 = from.getLocation();
        Vector2f p3 = to.getLocation();
        float toAngle = Misc.getAngleInDegrees(p0, p3);
        float chord = Misc.getDistance(p0, p3);
        float launchAngle = toAngle + (random.nextFloat() - 0.5f) * 2f * LANE_OFFSET;
        float landAngle = toAngle + 180f + (random.nextFloat() - 0.5f) * 2f * LANE_OFFSET;
        float startHandle = Math.min(from.getRadius() + HANDLE_EXTRA, chord * 0.45f);
        float endHandle = Math.min(to.getRadius() + HANDLE_EXTRA, chord * 0.45f);
        float bend = network.getBend(p0, p3, route.bendFrac * chord, from.entity, to.entity);

        ShuttlePath path = new ShuttlePath().add(new ShuttlePath.BezierSegment(
                new ShuttlePath.PadWaypoint(from, launchAngle, false),
                new ShuttlePath.PadWaypoint(to, landAngle, true),
                startHandle, endHandle, bend));

        Shuttle shuttle = new Shuttle(hull, route, from.faction, from, to, path, getEnd(from), getEnd(to),
                0.85f + 0.3f * random.nextFloat(), (random.nextFloat() - 0.5f) * 2f * LANE_JITTER, random.nextFloat());

        shuttles.add(shuttle);
        route.active++;
        return shuttle;
    }

    private void divertFromClosed() {
        for (Shuttle shuttle : shuttles) {
            if (shuttle.done || shuttle.to == null || shuttle.to.isUsable(location)) continue;

            ShuttleAnchor refuge = network.findRefuge(shuttle.to, shuttle.faction, shuttle.pos, location);
            if (refuge != null) shuttle.redirect(refuge, this);
            else if (!shuttle.to.entity.isAlive() || shuttle.to.entity.getContainingLocation() != location) shuttle.done = true;
        }
    }
}
