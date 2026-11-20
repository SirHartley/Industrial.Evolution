package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleNetwork;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.renderers.ShuttleTrafficRenderer;
import indevo.config.Setting;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// shuttle rush hours (PR #214): in Askonia at setting 80, the traffic day counter moves with the clock and each market's
// rush multiplier and liveliness swing between daily rebuilds, inside 1 +- RUSH_SWING
public final class TrafficRushChecks {
    private TrafficRushChecks() {
    }

    private static ShuttleTraffic traffic() {
        Object renderer = LunaCampaignRenderer.getRendererOfClass(ShuttleTrafficRenderer.class);
        return renderer == null ? null : ((ShuttleTrafficRenderer) renderer).getTraffic();
    }

    // market id -> liveliness of its anchor
    private static Map<String, Float> sample(Ctx c, String tag) {
        ShuttleTraffic t = traffic();
        Map<String, Float> map = new TreeMap<>();
        if (t == null) {
            c.expect(false, tag + ": shuttle traffic is running");
            return map;
        }

        StringBuilder sb = new StringBuilder();
        for (ShuttleAnchor a : t.getNetwork().marketAnchors) {
            if (a.market == null) continue;
            String id = a.market.getId();
            map.put(id, a.liveliness);
            sb.append(id).append(" rush ").append(String.format("%.3f", ShuttleNetwork.getRushHour(id, t.getDays()))).append(" lively ").append(String.format("%.3f", a.liveliness)).append("; ");
        }
        c.note(tag + ": traffic days " + t.getDays() + ", clock " + Global.getSector().getClock().getDateString() + ", " + sb);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.setting(Setting.SHUTTLE_TRAFFIC, 80);
            SectorEntityToken e = Global.getSector().getEntityById("sindria");
            Colony.jump(e.getContainingLocation(), e.getLocation().x + 400f, e.getLocation().y + 300f);
            c.frames(120);
        });
        steps.add(c -> c.days(1f));
        steps.add(c -> {
            ShuttleTraffic t = traffic();
            c.put("days0", t == null ? -1f : t.getDays());
            c.put("s0", sample(c, "first sample"));
            c.days(1.6f);
        });
        steps.add(c -> {
            c.put("s1", sample(c, "+1.6 days"));
            c.days(1.6f);
        });
        steps.add(c -> {
            ShuttleTraffic t = traffic();
            Map<String, Float> s0 = (Map<String, Float>) c.get("s0");
            Map<String, Float> s1 = (Map<String, Float>) c.get("s1");
            Map<String, Float> s2 = sample(c, "+3.2 days");
            float days0 = (Float) c.get("days0");
            float moved = t.getDays() - days0;
            c.expect(t.getDays() < 1e6f && !Float.isInfinite(t.getDays()), "the traffic day counter is a real count, not the clock's MAX_VALUE (" + t.getDays() + ")");
            c.near(moved, 3.2f, 0.4f, "the traffic day counter moves with the clock (" + moved + " days)");
            c.expect(!s0.isEmpty(), "Askonia has market anchors (" + s0.keySet() + ")");

            int swung = 0;
            boolean inRange = true;
            for (String id : s0.keySet()) {
                float a = s0.get(id), b = s1.getOrDefault(id, a), d = s2.getOrDefault(id, a);
                if (Math.abs(a - b) > 0.005f || Math.abs(b - d) > 0.005f) swung++;
                for (float x = 0f; x <= 10f; x += 0.5f) {
                    float r = ShuttleNetwork.getRushHour(id, days0 + x);
                    inRange &= r >= 1f - ShuttleNetwork.RUSH_SWING - 0.001f && r <= 1f + ShuttleNetwork.RUSH_SWING + 0.001f;
                }
            }
            c.expect(swung == s0.size(), "every Askonia market's liveliness changes across the daily rebuilds (" + swung + " of " + s0.size() + ")");
            c.expect(inRange, "the rush multiplier stays within 1 +- " + ShuttleNetwork.RUSH_SWING);
        });
        return steps;
    }
}
