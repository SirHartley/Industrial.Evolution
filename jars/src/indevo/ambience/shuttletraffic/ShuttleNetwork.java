package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ShuttleNetwork {

    public static class Body {
        public final SectorEntityToken entity;
        public final float radius;

        public Body(SectorEntityToken entity, float radius) {
            this.entity = entity;
            this.radius = radius;
        }
    }

    public final Map<SectorEntityToken, ShuttleAnchor> anchors = new LinkedHashMap<>();
    public final Map<String, ShuttleRoute> routes = new LinkedHashMap<>();
    public final List<ShuttleAnchor> marketAnchors = new ArrayList<>();
    public final List<Body> bodies = new ArrayList<>();

    public void clear() {
        anchors.clear();
        routes.clear();
        marketAnchors.clear();
        bodies.clear();
    }

    public void rebuild(LocationAPI location, Random random) {
        for (ShuttleAnchor anchor : anchors.values()) anchor.seen = false;
        for (ShuttleRoute route : routes.values()) route.seen = false;
        marketAnchors.clear();

        Map<ShuttleAnchor, Float> density = new HashMap<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(location)) {
            if (!hasTraffic(market, location)) continue;

            SectorEntityToken primary = market.getPrimaryEntity();
            boolean stationMarket = primary.hasTag(Tags.STATION);
            ShuttleAnchor anchor = getAnchor(primary, stationMarket ? ShuttleAnchor.Type.STATION : ShuttleAnchor.Type.PLANET, market, market.getFaction());
            anchor.closed = isPortDisrupted(market);
            marketAnchors.add(anchor);

            float mult = getDensity(market);
            density.put(anchor, mult);

            if (stationMarket) continue;

            for (SectorEntityToken entity : market.getConnectedEntities()) {
                if (entity == primary || !entity.hasTag(Tags.STATION) || entity.getContainingLocation() != location) continue;

                ShuttleAnchor station = getAnchor(entity, ShuttleAnchor.Type.STATION, market, market.getFaction());
                station.closed = anchor.closed;

                ShuttleRoute route = getRoute(ShuttleRoute.Kind.SUPPLY, anchor, station, random);
                route.max = Math.max(1, Math.round((1f + market.getSize() / 3f) * mult));
            }
        }

        for (int i = 0; i < marketAnchors.size(); i++) {
            for (int j = i + 1; j < marketAnchors.size(); j++) {
                ShuttleAnchor a = marketAnchors.get(i);
                ShuttleAnchor b = marketAnchors.get(j);
                if (isPirate(a) || isPirate(b) || a.faction.isHostileTo(b.faction)) continue;

                float mult = (density.get(a) + density.get(b)) / 2f;
                ShuttleRoute route = getRoute(ShuttleRoute.Kind.MARKET, a, b, random);
                route.max = Math.max(1, Math.round((a.market.getSize() + b.market.getSize()) / 4f * mult));
            }
        }

        routes.values().removeIf(route -> !route.seen);
        Iterator<ShuttleAnchor> iterator = anchors.values().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().seen) iterator.remove();
        }

        rebuildBodies(location);
    }

    public ShuttleAnchor findRefuge(ShuttleAnchor exclude, FactionAPI faction, Vector2f from, LocationAPI location) {
        ShuttleAnchor best = null;
        float bestScore = Float.MAX_VALUE;

        for (ShuttleAnchor anchor : anchors.values()) {
            if (anchor == exclude || !anchor.isUsable(location)) continue;
            if (faction != null && anchor.faction != null && anchor.faction.isHostileTo(faction)) continue;

            float score = Misc.getDistance(from, anchor.getLocation());
            if (faction != null && anchor.faction != faction) score *= 1.5f;
            if (score < bestScore) {
                bestScore = score;
                best = anchor;
            }
        }

        return best;
    }

    public float getBend(Vector2f p0, Vector2f p3, float bend, SectorEntityToken fromEntity, SectorEntityToken toEntity) {
        float cx = p3.x - p0.x;
        float cy = p3.y - p0.y;
        float chord2 = cx * cx + cy * cy;
        if (chord2 < 1f) return bend;

        float chord = (float) Math.sqrt(chord2);
        float nx = -cy / chord;
        float ny = cx / chord;

        for (Body body : bodies) {
            if (body.entity == fromEntity || body.entity == toEntity) continue;

            Vector2f loc = body.entity.getLocation();
            float ox = loc.x - p0.x;
            float oy = loc.y - p0.y;
            float t = (ox * cx + oy * cy) / chord2;
            if (t < 0.1f || t > 0.9f) continue;

            // the bezier midpoint sits at 0.75 of the control point shift
            float across = ox * nx + oy * ny;
            float mid = bend * 0.75f;
            if (Math.abs(mid - across) >= body.radius) continue;

            float side = across != 0f ? Math.signum(across) : (bend != 0f ? -Math.signum(bend) : 1f);
            bend = (across - side * body.radius * 1.1f) / 0.75f;
        }

        return Math.max(-chord * 0.6f, Math.min(chord * 0.6f, bend));
    }

    private void rebuildBodies(LocationAPI location) {
        bodies.clear();

        Map<SectorEntityToken, Float> coronas = new HashMap<>();
        for (CampaignTerrainAPI terrain : location.getTerrainCopy()) {
            String type = terrain.getType();
            if (!Terrain.CORONA.equals(type) && !Terrain.EVENT_HORIZON.equals(type)) continue;
            if (!(terrain.getPlugin() instanceof BaseRingTerrain ring) || ring.params == null || ring.params.relatedEntity == null) continue;

            float outer = ring.params.middleRadius + ring.params.bandWidthInEngine / 2f;
            coronas.merge(ring.params.relatedEntity, outer, Math::max);
        }

        for (PlanetAPI planet : location.getPlanets()) {
            float radius;
            if (planet.isStar()) radius = Math.max(planet.getRadius() * 1.5f, coronas.getOrDefault(planet, 0f));
            else radius = planet.getRadius() * 1.3f + 30f;

            bodies.add(new Body(planet, radius));
        }
    }

    private ShuttleAnchor getAnchor(SectorEntityToken entity, ShuttleAnchor.Type type, MarketAPI market, FactionAPI faction) {
        ShuttleAnchor anchor = anchors.get(entity);
        if (anchor == null || anchor.type != type) {
            anchor = new ShuttleAnchor(entity, type, market, faction);
            anchors.put(entity, anchor);
        }

        anchor.market = market;
        anchor.faction = faction;
        anchor.seen = true;
        return anchor;
    }

    private ShuttleRoute getRoute(ShuttleRoute.Kind kind, ShuttleAnchor a, ShuttleAnchor b, Random random) {
        String key = kind.name() + "|" + a.entity.getId() + "|" + (b != null ? b.entity.getId() : "");
        ShuttleRoute route = routes.get(key);
        if (route == null || route.a != a || route.b != b) {
            route = new ShuttleRoute(key, kind, a, b, (random.nextFloat() - 0.5f) * 0.5f);
            route.timer = random.nextFloat() * 5f;
            routes.put(key, route);
        }

        route.seen = true;
        return route;
    }

    private static boolean hasTraffic(MarketAPI market, LocationAPI location) {
        SectorEntityToken primary = market.getPrimaryEntity();
        return primary != null && primary.getContainingLocation() == location && market.isInEconomy() && !market.isHidden()
                && market.hasSpaceport() && !primary.isDiscoverable();
    }

    private static boolean isPortDisrupted(MarketAPI market) {
        Industry port = market.getIndustry(Industries.SPACEPORT);
        if (port == null) port = market.getIndustry(Industries.MEGAPORT);
        return port != null && port.isDisrupted();
    }

    private static float getDensity(MarketAPI market) {
        float mult = market.hasIndustry(Industries.MEGAPORT) ? 1.5f : 1f;
        if (market.hasIndustry(Industries.COMMERCE)) mult *= 1.25f;
        mult *= Math.max(0.5f, Math.min(1f, 0.5f + market.getStabilityValue() / 20f));

        boolean raided = market.getMemoryWithoutUpdate().getBoolean(MemFlags.RECENTLY_RAIDED)
                || market.getMemoryWithoutUpdate().getBoolean(MemFlags.RECENTLY_BOMBARDED);
        if (raided) mult *= 0.5f;

        return mult;
    }

    private static boolean isPirate(ShuttleAnchor anchor) {
        return anchor.faction != null && Factions.PIRATES.equals(anchor.faction.getId());
    }
}
