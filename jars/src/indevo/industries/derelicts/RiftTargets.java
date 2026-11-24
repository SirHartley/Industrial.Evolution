package indevo.industries.derelicts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.utils.helper.MiscIE;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RiftTargets {

    public static final float AREA_RADIUS_LY = 3f;
    public static final int MIN_AREA_SYSTEMS = 3;
    public static final String NO_TARGET_TAG = "IndEvo_noRiftTarget";
    public static final String NEX_OUTPOST_TAG = "nex_playerOutpost";

    private final MarketAPI market;
    private final float rangeLY;

    public RiftTargets(MarketAPI market, float rangeLY) {
        this.market = market;
        this.rangeLY = rangeLY;
    }

    public RiftTargetArea getArea(RiftTargetMode mode) {
        Vector2f origin = market.getLocationInHyperspace();
        float reach = rangeLY * mode.reachMult;
        List<StarSystemAPI> candidates = getSystemsInReach(reach);
        if (candidates.isEmpty()) return RiftTargetArea.empty(mode, origin, reach);
        if (mode == RiftTargetMode.RANDOM) return new RiftTargetArea(mode, origin, origin, reach, null, candidates);

        Vector2f aim = getAim(mode, origin, reach);
        if (aim == null) return RiftTargetArea.empty(mode, origin, reach);

        candidates.sort(Comparator.comparingDouble(s -> Misc.getDistanceLY(aim, s.getLocation())));
        int count = 0;
        while (count < candidates.size()
                && (count < MIN_AREA_SYSTEMS || Misc.getDistanceLY(aim, candidates.get(count).getLocation()) <= AREA_RADIUS_LY)) {
            count++;
        }

        List<StarSystemAPI> area = new ArrayList<>(candidates.subList(0, count));
        return new RiftTargetArea(mode, origin, aim, reach, area.get(0), area);
    }

    public List<StarSystemAPI> getPreciseTargets() {
        List<StarSystemAPI> targets = getSystemsInReach(rangeLY);
        targets.sort(Comparator.comparingDouble(s -> Misc.getDistanceLY(market.getLocationInHyperspace(), s.getLocation())));
        return targets;
    }

    public boolean hasAnyTarget() {
        float maxMult = 0f;
        for (RiftTargetMode mode : RiftTargetMode.values()) maxMult = Math.max(maxMult, mode.reachMult);
        return !getSystemsInReach(rangeLY * maxMult).isEmpty();
    }

    public boolean isPreciseTarget(StarSystemAPI system) {
        return isEligible(system) && Misc.getDistanceLY(market.getLocationInHyperspace(), system.getLocation()) <= rangeLY;
    }

    public boolean hasOtherColonySystem() {
        return getClosestOtherColony() != null;
    }

    public boolean isEligible(StarSystemAPI system) {
        if (system == null || system == market.getStarSystem()) return false;
        if (system.hasTag(Tags.THEME_HIDDEN)
                || system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                || system.hasTag(Tags.SYSTEM_ABYSSAL)
                || system.hasTag(NO_TARGET_TAG)) return false;
        if (system.isDeepSpace() || system.getHyperspaceAnchor() == null || Misc.isInAbyss(system.getLocation())) return false;
        if (system.getStar() == null && !system.isNebula()) return false;

        return !MiscIE.getCSVSetFromMemory(Ids.RIFT_BLOCKED_SYSTEMS_LIST).contains(system.getId());
    }

    public static Vector2f getCoreCenter() {
        Vector2f center = new Vector2f();
        int count = 0;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.hasTag(Tags.THEME_CORE_POPULATED)) continue;
            Vector2f.add(center, system.getLocation(), center);
            count++;
        }

        if (count > 0) center.scale(1f / count);
        return center;
    }

    private List<StarSystemAPI> getSystemsInReach(float reachLY) {
        Vector2f origin = market.getLocationInHyperspace();
        List<StarSystemAPI> result = new ArrayList<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (Misc.getDistanceLY(origin, system.getLocation()) > reachLY) continue;
            if (isEligible(system)) result.add(system);
        }

        return result;
    }

    private Vector2f getAim(RiftTargetMode mode, Vector2f origin, float reachLY) {
        switch (mode) {
            case CORE:
                return towards(origin, getCoreCenter(), reachLY);
            case FRINGE:
                Vector2f away = Vector2f.sub(origin, getCoreCenter(), new Vector2f());
                if (away.lengthSquared() < 1f) away.set(1f, 0f);
                away.normalise();
                away.scale(reachLY * Misc.getUnitsPerLightYear());
                return Vector2f.add(origin, away, away);
            case COLONY:
                MarketAPI colony = getClosestOtherColony();
                return colony == null ? null : towards(origin, colony.getLocationInHyperspace(), reachLY);
            default:
                return new Vector2f(origin);
        }
    }

    private MarketAPI getClosestOtherColony() {
        MarketAPI best = null;
        float bestDist = Float.MAX_VALUE;
        for (MarketAPI other : Misc.getFactionMarkets(market.getFaction())) {
            if (other.getStarSystem() == null || other.getStarSystem() == market.getStarSystem()) continue;

            SectorEntityToken primary = other.getPrimaryEntity();
            if (primary != null && primary.hasTag(NEX_OUTPOST_TAG)) continue;

            float dist = Misc.getDistanceLY(market.getLocationInHyperspace(), other.getLocationInHyperspace());
            if (dist < bestDist) {
                bestDist = dist;
                best = other;
            }
        }

        return best;
    }

    private static Vector2f towards(Vector2f from, Vector2f to, float reachLY) {
        float dist = Misc.getDistanceLY(from, to);
        if (dist <= reachLY) return new Vector2f(to);
        return Misc.interpolateVector(from, to, reachLY / dist);
    }
}
