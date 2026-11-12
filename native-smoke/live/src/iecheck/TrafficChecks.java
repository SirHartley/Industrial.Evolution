package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.renderers.ShuttleTrafficRenderer;
import indevo.config.Setting;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// counts craft per route and market at the default setting, and how fast they really move
public final class TrafficChecks {
    private TrafficChecks() {
    }

    private static ShuttleTraffic traffic() {
        Object renderer = LunaCampaignRenderer.getRendererOfClass(ShuttleTrafficRenderer.class);
        return renderer == null ? null : ((ShuttleTrafficRenderer) renderer).getTraffic();
    }

    private static String name(indevo.ambience.shuttletraffic.ShuttleAnchor anchor) {
        if (anchor == null) return "-";
        return anchor.entity != null ? anchor.entity.getName() : anchor.type.name();
    }

    private static void report(Ctx c, String tag) {
        ShuttleTraffic t = traffic();
        if (t == null) {
            c.note(tag + " no traffic");
            return;
        }

        Map<String, int[]> byKind = new TreeMap<>();
        Map<String, int[]> byMarket = new TreeMap<>();
        for (ShuttleRoute route : t.getNetwork().routes.values()) {
            int[] k = byKind.computeIfAbsent(route.kind.name(), x -> new int[4]);
            k[0]++;
            k[1] += route.demand;
            k[2] += route.max;
            k[3] += route.active;
            int[] m = byMarket.computeIfAbsent(name(route.a), x -> new int[3]);
            m[0] += route.demand;
            m[1] += route.max;
            m[2] += route.active;
        }

        Map<String, Integer> craftByKind = new TreeMap<>();
        Map<String, Integer> byState = new TreeMap<>();
        Map<String, Integer> byGroup = new TreeMap<>();
        for (Shuttle s : t.getShuttles()) {
            craftByKind.merge(s.getRoute() != null ? s.getRoute().kind.name() : "no route", 1, Integer::sum);
            byState.merge(s.getState().name(), 1, Integer::sum);
            byGroup.merge(String.valueOf(s.getGroup()), 1, Integer::sum);
        }

        c.note(tag + " craft " + t.getShuttles().size() + ", pods " + t.getPods().size() + ", cap " + t.getBudget().getCap()
                + ", routes " + t.getNetwork().routes.size() + ", fleet routes " + t.getFleetRoutes().size());
        StringBuilder kinds = new StringBuilder();
        for (Map.Entry<String, int[]> e : byKind.entrySet()) {
            int[] v = e.getValue();
            kinds.append(e.getKey()).append(" routes ").append(v[0]).append(" demand ").append(v[1]).append(" max ").append(v[2]).append(" active ").append(v[3]).append("; ");
        }
        c.note(tag + " routes by kind: " + kinds);
        StringBuilder markets = new StringBuilder();
        for (Map.Entry<String, int[]> e : byMarket.entrySet()) {
            int[] v = e.getValue();
            markets.append(e.getKey()).append(" demand ").append(v[0]).append(" max ").append(v[1]).append(" active ").append(v[2]).append("; ");
        }
        c.note(tag + " routes by start market: " + markets);
        StringBuilder hops = new StringBuilder();
        for (ShuttleRoute route : t.getNetwork().routes.values()) {
            if (route.kind == ShuttleRoute.Kind.HOP) hops.append(name(route.a)).append(" -> ").append(name(route.b)).append(" ").append(route.active).append("/").append(route.max).append("; ");
        }
        c.note(tag + " local hops: " + hops);
        c.note(tag + " craft by kind " + craftByKind + ", by state " + byState + ", by group " + byGroup);
        near(c, tag, t);
    }

    // what the player sees: craft near the player fleet (default zoom shows about 1600 x 900 su), and near each market
    private static void near(Ctx c, String tag, ShuttleTraffic t) {
        Vector2f player = Global.getSector().getPlayerFleet().getLocation();
        Map<String, Integer> view = new TreeMap<>();
        int in1000 = 0, in2000 = 0;
        for (Shuttle s : t.getShuttles()) {
            float d = Vector2f.sub(s.pos, player, new Vector2f()).length();
            if (d < 2000f) in2000++;
            if (d < 1000f) {
                in1000++;
                view.merge(s.getRoute() != null ? s.getRoute().kind.name() : String.valueOf(s.getGroup()), 1, Integer::sum);
            }
        }
        Map<String, Integer> markets = new TreeMap<>();
        for (indevo.ambience.shuttletraffic.ShuttleAnchor anchor : t.getNetwork().marketAnchors) {
            int n = 0;
            for (Shuttle s : t.getShuttles()) {
                if (Vector2f.sub(s.pos, anchor.getLocation(), new Vector2f()).length() < anchor.getRadius() + 800f) n++;
            }
            markets.put(name(anchor), n);
        }
        c.note(tag + " near player: within 1000 su " + in1000 + " " + view + ", within 2000 su " + in2000 + "; within radius + 800 of each market " + markets);
    }

    // fleets parked at markets: the ones fleet service shuttles fly to, and their speed against the 40 su/s gate
    private static void fleets(Ctx c, String tag) {
        ShuttleTraffic t = traffic();
        if (t == null) return;
        int passive = 0, fast = 0, visible = 0;
        StringBuilder sb = new StringBuilder();
        for (com.fs.starfarer.api.campaign.CampaignFleetAPI fleet : t.getLocation().getFleets()) {
            if (fleet.isPlayerFleet()) continue;
            com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI a = fleet.getCurrentAssignment();
            if (a == null || a.getAssignment() != com.fs.starfarer.api.campaign.FleetAssignment.ORBIT_PASSIVE) continue;
            passive++;
            float v = fleet.getVelocity().length();
            if (v >= 40f) fast++;
            boolean seen = fleet.getVisibilityLevelToPlayerFleet() != com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel.NONE;
            if (seen) visible++;
            String target = a.getTarget() != null ? a.getTarget().getName() : "-";
            float dist = a.getTarget() != null ? com.fs.starfarer.api.util.Misc.getDistance(fleet, a.getTarget()) : -1f;
            if (sb.length() < 1500) sb.append(fleet.getMemoryWithoutUpdate().getString(com.fs.starfarer.api.impl.campaign.ids.MemFlags.MEMORY_KEY_FLEET_TYPE))
                    .append("@").append(target).append(" v ").append(Math.round(v)).append(" d ").append(Math.round(dist)).append(seen ? "" : " unseen").append("; ");
        }
        c.note(tag + " orbit_passive fleets " + passive + ", at 40+ su/s " + fast + ", visible " + visible + ", fleet routes " + t.getFleetRoutes().size() + ": " + sb);
    }

    // craft around each market over the samples of its system: one sample swings too much to compare markets
    private static final Map<String, int[]> around = new HashMap<>();

    private static void sample(String entityId) {
        int[] sum = around.computeIfAbsent(entityId, x -> new int[2]);
        sum[0] += around(Global.getSector().getEntityById(entityId));
        sum[1]++;
    }

    private static float mean(String entityId) {
        int[] sum = around.get(entityId);
        return sum == null || sum[1] == 0 ? 0f : sum[0] / (float) sum[1];
    }
    private static final Map<Shuttle, Vector2f> start = new IdentityHashMap<>();
    private static final Map<Shuttle, Float> startAge = new IdentityHashMap<>();

    private static void markSpeeds() {
        start.clear();
        startAge.clear();
        ShuttleTraffic t = traffic();
        if (t == null) return;
        for (Shuttle s : t.getShuttles()) {
            start.put(s, new Vector2f(s.pos));
            startAge.put(s, s.getAge());
        }
    }

    // world speed over the sampled window per kind: count, mean, below 5 su/s, below 50% of cruise
    private static void speeds(Ctx c, String tag) {
        ShuttleTraffic t = traffic();
        if (t == null) return;
        Map<String, float[]> byKind = new TreeMap<>();
        List<String> slow = new ArrayList<>();
        for (Shuttle s : t.getShuttles()) {
            Vector2f p = start.get(s);
            if (p == null || s.isDown() || s.isWaiting()) continue;
            float dt = s.getAge() - startAge.get(s);
            if (dt <= 0.5f) continue;
            float v = Vector2f.sub(s.pos, p, new Vector2f()).length() / dt;
            String kind = s.getRoute() != null ? s.getRoute().kind.name() : "no route";
            float[] k = byKind.computeIfAbsent(kind, x -> new float[5]);
            k[0]++;
            k[1] += v;
            if (v < 5f) k[2]++;
            if (v < 0.5f * s.getCruiseSpeed()) k[3]++;
            k[4] += s.getCruiseSpeed();
            if (v < 0.5f * s.getCruiseSpeed() && slow.size() < 12) slow.add(kind + " " + s.getState() + " v " + Math.round(v) + " cruise " + Math.round(s.getCruiseSpeed()) + " age " + Math.round(s.getAge()));
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, float[]> e : byKind.entrySet()) {
            float[] k = e.getValue();
            sb.append(e.getKey()).append(" n ").append((int) k[0]).append(" mean ").append(Math.round(k[1] / k[0])).append(" cruise ").append(Math.round(k[4] / k[0]))
                    .append(" under5 ").append((int) k[2]).append(" underHalf ").append((int) k[3]).append("; ");
        }
        c.note(tag + " world speed by kind: " + sb);
        if (!slow.isEmpty()) c.note(tag + " slow craft: " + slow);
    }

    // the player sits 500 su off the market like a parked fleet; the base save's move destination would fly it off otherwise
    private static SectorEntityToken pin;
    private static float pinX = 400f;
    private static float pinY = 300f;

    private static void pin() {
        if (pin == null) return;
        com.fs.starfarer.api.campaign.CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        float x = pin.getLocation().x + pinX;
        float y = pin.getLocation().y + pinY;
        player.setLocation(x, y);
        player.setMoveDestination(x, y);
        player.setVelocity(0f, 0f);
    }

    private static void wait(Ctx c, int frames, String id, Runnable atEnd) {
        int[] n = {0};
        c.until(() -> n[0] >= frames, 100f, id, () -> {
            pin();
            if (++n[0] == frames && atEnd != null) atEnd.run();
        });
    }

    private static void system(List<Step> steps, String entityId, String tag) {
        steps.add(c -> {
            SectorEntityToken e = Global.getSector().getEntityById(entityId);
            Colony.jump(e.getContainingLocation(), e.getLocation().x + 400f, e.getLocation().y + 300f);
            pin = e;
            pinX = 400f;
            pinY = 300f;
            wait(c, 120, tag + " entered", () -> report(c, tag + " t0"));
        });
        steps.add(c -> wait(c, 60, tag + " mark", TrafficChecks::markSpeeds));
        steps.add(c -> wait(c, 600, tag + " 10 s", () -> {
            report(c, tag + " t10s");
            sample(entityId);
            fleets(c, tag + " t10s");
            speeds(c, tag + " t10s");
        }));
        steps.add(c -> c.shot("traffic_" + tag));
        for (int i = 1; i <= 2; i++) {
            int round = i;
            steps.add(c -> wait(c, 1800, tag + " 30 s round " + round, () -> {
                report(c, tag + " +" + round * 30 + "s");
                sample(entityId);
            }));
        }
        steps.add(c -> wait(c, 60, tag + " mark2", TrafficChecks::markSpeeds));
        steps.add(c -> wait(c, 600, tag + " 10 s again", () -> {
            speeds(c, tag + " late");
            fleets(c, tag + " late");
            ShuttleTraffic t = traffic();
            c.expect(t != null && t.getShuttles().size() > 0, tag + " has craft (" + (t == null ? -1 : t.getShuttles().size()) + ")");
            sample(entityId);
            c.note(tag + " craft within radius + 800 of " + Global.getSector().getEntityById(entityId).getName() + ": mean " + mean(entityId) + " of " + around.get(entityId)[1] + " samples");
        }));
    }

    private static int around(SectorEntityToken e) {
        ShuttleTraffic t = traffic();
        int n = 0;
        if (t != null) for (Shuttle s : t.getShuttles()) if (!s.isDown() && Vector2f.sub(s.pos, e.getLocation(), new Vector2f()).length() < e.getRadius() + 800f) n++;
        return n;
    }

    // what a player gets: enter the system far from the hub, let it run, cross the near range, arrive 10 s later
    private static void approach(List<Step> steps, String entityId, String tag, int want) {
        int[] arrival = {0};
        steps.add(c -> {
            SectorEntityToken e = Global.getSector().getEntityById(entityId);
            Vector2f loc = e.getLocation();
            float len = Math.max(1f, loc.length());
            pin = e;
            pinX = loc.x / len * 9000f;
            pinY = loc.y / len * 9000f;
            Colony.jump(e.getContainingLocation(), loc.x + pinX, loc.y + pinY);
            wait(c, 3600, tag + " approach: 60 s far out", () -> {
                ShuttleTraffic t = traffic();
                c.note(tag + " approach far out: " + (t == null ? -1 : t.getShuttles().size()) + " craft in the system, " + around(e) + " at the hub");
            });
        });
        steps.add(c -> {
            SectorEntityToken e = Global.getSector().getEntityById(entityId);
            float scale = (e.getRadius() + 3600f) / 9000f;
            pinX *= scale;
            pinY *= scale;
            wait(c, 600, tag + " approach: 10 s inside the near range", () -> c.note(tag + " approach 3600 su out for 10 s: " + around(e) + " at the hub"));
        });
        steps.add(c -> {
            SectorEntityToken e = Global.getSector().getEntityById(entityId);
            pinX = 400f;
            pinY = 300f;
            wait(c, 30, tag + " approach: arrival", () -> {
                arrival[0] = around(e);
                report(c, tag + " approach arrival");
                fleets(c, tag + " approach arrival");
            });
        });
        steps.add(c -> c.shot("approach_" + tag));
        steps.add(c -> wait(c, 600, tag + " approach: 10 s at the hub", () -> c.note(tag + " approach +10 s: " + around(Global.getSector().getEntityById(entityId)) + " at the hub")));
        steps.add(c -> wait(c, 1200, tag + " approach: 30 s at the hub", () -> {
            int late = around(Global.getSector().getEntityById(entityId));
            c.note(tag + " approach +30 s: " + late + " at the hub");
            c.expect(arrival[0] >= want, tag + " greets the player with " + want + " craft or more on arrival (" + arrival[0] + ", " + late + " after 30 s)");
        }));
    }

    public static List<Step> approachSteps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> Colony.setting(Setting.SHUTTLE_TRAFFIC, 80));
        approach(steps, "chicomoztoc", "aztlan", 20);
        approach(steps, "sindria", "askonia", 16);
        approach(steps, "jangala", "corvus", 8);
        return steps;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        around.clear();
        steps.add(c -> {
            Colony.setting(Setting.SHUTTLE_TRAFFIC, 80);
            c.note("setting " + Setting.SHUTTLE_TRAFFIC.id + " 80");
        });
        system(steps, "chicomoztoc", "aztlan");
        system(steps, "arcadia_station", "arcadia");
        system(steps, "sindria", "askonia");
        system(steps, "jangala", "corvus");
        steps.add(c -> c.expect(mean("chicomoztoc") >= 2f * Math.max(1f, mean("arcadia_station")),
                "Chicomoztoc at least twice as busy as Citadel Arcadia (means: Chicomoztoc " + mean("chicomoztoc") + ", Sindria " + mean("sindria") + ", Jangala " + mean("jangala")
                        + ", Citadel Arcadia " + mean("arcadia_station") + ")"));
        return steps;
    }
}
