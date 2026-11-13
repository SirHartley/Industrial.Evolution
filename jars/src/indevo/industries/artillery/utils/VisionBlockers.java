package indevo.industries.artillery.utils;

import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import indevo.items.consumables.terrain.SmokeCloudTerrain;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class VisionBlockers {

    public static class Blocker {
        public final float x;
        public final float y;
        public final float radius;
        public final boolean smoke;

        public Blocker(float x, float y, float radius, boolean smoke) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.smoke = smoke;
        }
    }

    public static final float PLANET_RADIUS_MULT = 0.9f;

    public static List<Blocker> collect(LocationAPI loc) {
        List<Blocker> blockers = collectSmoke(loc);

        for (PlanetAPI planet : loc.getPlanets()) {
            Vector2f at = planet.getLocation();
            blockers.add(new Blocker(at.x, at.y, planet.getRadius() * PLANET_RADIUS_MULT, false));
        }

        return blockers;
    }

    public static List<Blocker> collectSmoke(LocationAPI loc) {
        List<Blocker> blockers = new ArrayList<>();

        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
            if (!(terrain.getPlugin() instanceof SmokeCloudTerrain smoke)) continue;

            Vector2f at = terrain.getLocation();
            blockers.add(new Blocker(at.x, at.y, smoke.getRingParams().bandWidthInEngine, true));
        }

        return blockers;
    }

    public static boolean isBlocked(List<Blocker> blockers, Vector2f from, Vector2f to) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= 0f) return false;

        return getRayLength(blockers, from, dx / dist, dy / dist, dist) < dist;
    }

    public static float getRayLength(List<Blocker> blockers, Vector2f from, float angle, float max) {
        double rad = Math.toRadians(angle);
        return getRayLength(blockers, from, (float) Math.cos(rad), (float) Math.sin(rad), max);
    }

    // a planet lights up to its middle and shadows what is behind, smoke stops the ray where it enters
    private static float getRayLength(List<Blocker> blockers, Vector2f from, float dirX, float dirY, float max) {
        float length = max;

        for (Blocker blocker : blockers) {
            float toX = blocker.x - from.x;
            float toY = blocker.y - from.y;
            float along = toX * dirX + toY * dirY;
            float distSq = toX * toX + toY * toY;
            float radiusSq = blocker.radius * blocker.radius;
            boolean inside = distSq <= radiusSq;

            if (inside) {
                if (blocker.smoke) return 0f;
                continue;
            }

            if (along <= 0f) continue;

            float offSq = distSq - along * along;
            if (offSq >= radiusSq) continue;

            float hit = blocker.smoke ? along - (float) Math.sqrt(radiusSq - offSq) : along;
            if (hit < length) length = hit;
        }

        return length;
    }
}
