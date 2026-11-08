package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.ambience.shuttletraffic.ShuttleTrafficRenderer;
import indevo.config.Setting;
import indevo.utils.helper.ReflectionUtils;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

import java.util.ArrayList;
import java.util.List;

public final class ShuttleChecks {
    private ShuttleChecks() {
    }

    private static int count() {
        Object renderer = LunaCampaignRenderer.getRendererOfClass(ShuttleTrafficRenderer.class);
        if (renderer == null) return -1;
        return ((List<?>) ReflectionUtils.get("shuttles", renderer)).size();
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
        return steps;
    }
}
