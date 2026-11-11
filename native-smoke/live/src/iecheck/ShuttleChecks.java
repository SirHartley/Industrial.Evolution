package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleBudget;
import indevo.ambience.shuttletraffic.renderers.ShuttleTrafficRenderer;
import indevo.config.Setting;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ShuttleChecks {
    private ShuttleChecks() {
    }

    private static int count() {
        Object renderer = LunaCampaignRenderer.getRendererOfClass(ShuttleTrafficRenderer.class);
        if (renderer == null) return -1;
        return ((ShuttleTrafficRenderer) renderer).getTraffic().getShuttles().size();
    }

    private static List<Shuttle> shuttles() {
        Object renderer = LunaCampaignRenderer.getRendererOfClass(ShuttleTrafficRenderer.class);
        return renderer == null ? List.of() : ((ShuttleTrafficRenderer) renderer).getTraffic().getShuttles();
    }

    private static int groupCount(ShuttleBudget.Group group) {
        int count = 0;
        for (Shuttle shuttle : shuttles()) if (shuttle.getGroup() == group) count++;
        return count;
    }

    // leaders by route kind, formation followers and race rivals have no route
    private static String kinds() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Shuttle shuttle : shuttles()) {
            String key = shuttle.getRoute() != null ? shuttle.getRoute().kind.name() : "no route";
            counts.merge(key, 1, Integer::sum);
        }
        return counts.toString();
    }

    private static void timed(Ctx c, int frames, String key) {
        long[] t = {0L, 0L};
        c.until(() -> t[1] >= frames, 100f, key + " ran " + frames + " frames", () -> {
            if (t[1]++ == 0L) t[0] = System.nanoTime();
            if (t[1] == frames) c.note(key + " " + (System.nanoTime() - t[0]) / 1000000f / frames + " ms per frame, " + count() + " shuttles");
        });
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            SectorEntityToken sindria = Global.getSector().getEntityById("sindria");
            Colony.jump(sindria.getContainingLocation(), sindria.getLocation().x + 500f, sindria.getLocation().y);
            Colony.setting(Setting.SHUTTLE_TRAFFIC, 0);
            timed(c, 1800, "traffic 0");
        });
        steps.add(c -> {
            Colony.setting(Setting.SHUTTLE_TRAFFIC, 80);
            timed(c, 1200, "traffic 80 warm up");
        });
        steps.add(c -> timed(c, 3600, "traffic 80"));
        steps.add(c -> c.expect(count() > 0, "shuttles fly in Askonia (" + count() + ")"));
        steps.add(c -> {
            c.note("traffic 80 by kind: " + kinds());
            c.expect(groupCount(ShuttleBudget.Group.LOCAL) > 0, "local flights still launch in Askonia (" + kinds() + ")");
        });
        return steps;
    }
}
