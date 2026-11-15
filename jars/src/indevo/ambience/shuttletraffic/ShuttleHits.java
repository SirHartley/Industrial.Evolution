package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.InterdictionPulseAbility;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

// visual only: explosions, fleets on sustained burn and interdiction pulses destroy or stall craft, nothing else changes
public class ShuttleHits {

    public static final float RUN_OVER_SPEED = 280f;
    private static final float RUN_OVER_FULL_SPEED = 400f;
    private static final float RUN_OVER_MIN_CHANCE = 0.25f;
    private static final float RUN_OVER_MAX_CHANCE = 0.75f;
    private static final float PULSE_EXPLODE_CHANCE = 0.25f;
    private static final float DISABLE_MIN = 3f;
    private static final float DISABLE_MAX = 6f;
    private static final float POD_CHANCE = 0.5f;
    // a blast only hits while it is young, a craft flying into its fading glow is fine
    private static final float BLAST_HIT_TIME = 1f;
    private static final float FALLBACK_BLAST_RADIUS = 100f;

    private final ShuttleTraffic traffic;
    // no callback reports a spawned explosion entity, the 0.25 s scan finds them; value is the traffic time first seen
    private final Map<SectorEntityToken, Float> blasts = new IdentityHashMap<>();
    private final Map<CampaignFleetAPI, Boolean> pulses = new IdentityHashMap<>();
    private final List<Shuttle> wrecked = new ArrayList<>();

    public ShuttleHits(ShuttleTraffic traffic) {
        this.traffic = traffic;
    }

    public void clear() {
        blasts.clear();
        pulses.clear();
        wrecked.clear();
    }

    public void scan() {
        LocationAPI location = traffic.getLocation();
        float time = traffic.getTime();

        Iterator<Map.Entry<SectorEntityToken, Float>> iterator = blasts.entrySet().iterator();
        while (iterator.hasNext()) {
            SectorEntityToken entity = iterator.next().getKey();
            if (!entity.isAlive() || entity.getContainingLocation() != location) iterator.remove();
        }

        for (CustomCampaignEntityAPI entity : location.getCustomEntities()) {
            CustomCampaignEntityPlugin plugin = entity.getCustomPlugin();
            if (!(plugin instanceof ExplosionEntityPlugin)) continue;

            Float seen = blasts.get(entity);
            if (seen == null) {
                seen = time;
                blasts.put(entity, seen);
            }
            if (time - seen <= BLAST_HIT_TIME) blast(entity.getLocation(), getBlastRadius(entity, plugin));
        }

        scanPulses(location);
        dropPods();
    }

    // every frame: a fleet at 280 su/s moves 9 su per frame at 30 Hz
    public void advance(List<ShuttleFleetScan.Entry> fast) {
        if (fast.isEmpty()) return;

        Random random = traffic.getRandom();
        for (ShuttleFleetScan.Entry entry : fast) {
            CampaignFleetAPI fleet = entry.fleet;
            Vector2f loc = fleet.getLocation();
            float speed = fleet.getVelocity().length();
            if (speed < RUN_OVER_SPEED) continue;

            float hitRange = fleet.getRadius() * 0.5f;
            float t = Math.min(1f, (speed - RUN_OVER_SPEED) / (RUN_OVER_FULL_SPEED - RUN_OVER_SPEED));
            float chance = RUN_OVER_MIN_CHANCE + (RUN_OVER_MAX_CHANCE - RUN_OVER_MIN_CHANCE) * t;
            runOver(traffic.getShuttles(), fleet, loc, hitRange, chance, random, true);
            runOver(traffic.getPods(), fleet, loc, hitRange, chance, random, false);
        }

        dropPods();
    }

    private void runOver(List<Shuttle> list, CampaignFleetAPI fleet, Vector2f loc, float hitRange, float chance, Random random, boolean crewed) {
        for (Shuttle shuttle : list) {
            if (!canBeHit(shuttle) || shuttle.getTiedFleet() == fleet) continue;

            float dx = shuttle.pos.x - loc.x;
            float dy = shuttle.pos.y - loc.y;
            float dist2 = dx * dx + dy * dy;
            if (shuttle.getRunOverBy() == fleet) {
                if (dist2 > fleet.getRadius() * fleet.getRadius()) shuttle.setRunOverBy(null);
                continue;
            }
            if (dist2 > hitRange * hitRange) continue;

            shuttle.setRunOverBy(fleet);
            if (random.nextFloat() < chance) wreck(shuttle, crewed);
            else shuttle.nearMiss(random);
        }
    }

    private void blast(Vector2f center, float radius) {
        hitInside(traffic.getShuttles(), center, radius, true);
        hitInside(traffic.getPods(), center, radius, false);
    }

    private void hitInside(List<Shuttle> list, Vector2f center, float radius, boolean crewed) {
        float r2 = radius * radius;
        for (Shuttle shuttle : list) {
            if (!canBeHit(shuttle)) continue;

            float dx = shuttle.pos.x - center.x;
            float dy = shuttle.pos.y - center.y;
            if (dx * dx + dy * dy <= r2) wreck(shuttle, crewed);
        }
    }

    // the pulse flag lasts 0.1 day, each fleet's pulse is handled once
    private void scanPulses(LocationAPI location) {
        boolean pulsed = Global.getSector().getMemoryWithoutUpdate().getBoolean(MemFlags.GLOBAL_INTERDICTION_PULSE_JUST_USED_IN_CURRENT_LOCATION);
        if (!pulsed) {
            pulses.clear();
            return;
        }

        pulses.keySet().removeIf(fleet -> !fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.JUST_DID_INTERDICTION_PULSE));
        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (pulses.containsKey(fleet) || !fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.JUST_DID_INTERDICTION_PULSE)) continue;

            pulses.put(fleet, Boolean.TRUE);
            pulse(traffic.getShuttles(), fleet, true);
            pulse(traffic.getPods(), fleet, false);
        }
    }

    private void pulse(List<Shuttle> list, CampaignFleetAPI fleet, boolean crewed) {
        Random random = traffic.getRandom();
        Vector2f loc = fleet.getLocation();
        float range = InterdictionPulseAbility.getRange(fleet);
        float r2 = range * range;

        for (Shuttle shuttle : list) {
            if (!canBeHit(shuttle) || shuttle.getState() == ShuttleState.DISABLED || shuttle.getTiedFleet() == fleet) continue;

            float dx = shuttle.pos.x - loc.x;
            float dy = shuttle.pos.y - loc.y;
            if (dx * dx + dy * dy > r2) continue;

            if (random.nextFloat() < PULSE_EXPLODE_CHANCE) wreck(shuttle, crewed);
            else shuttle.disable(DISABLE_MIN + random.nextFloat() * (DISABLE_MAX - DISABLE_MIN), random);
        }
    }

    private void wreck(Shuttle shuttle, boolean crewed) {
        shuttle.explode(traffic);
        if (crewed && !ShuttleHullPool.isRemnant(shuttle.faction) && traffic.getRandom().nextFloat() < POD_CHANCE) wrecked.add(shuttle);
    }

    // pods join the pod list after the hit loops, so no list changes while it is walked
    private void dropPods() {
        for (Shuttle shuttle : wrecked) traffic.getFlights().getPods().launchFromWreck(shuttle.pos, shuttle.getFacing(), shuttle.faction);
        wrecked.clear();
    }

    private static boolean canBeHit(Shuttle shuttle) {
        return !shuttle.isDone() && !shuttle.isDown() && !shuttle.isWaiting() && !shuttle.isWrecked();
    }

    // ExplosionEntityPlugin.getRenderRange is the entity radius plus three blast radii, params itself is protected
    private static float getBlastRadius(SectorEntityToken entity, CustomCampaignEntityPlugin plugin) {
        float radius = (plugin.getRenderRange() - entity.getRadius()) / 3f;
        return radius > 0f ? radius : FALLBACK_BLAST_RADIUS;
    }
}
