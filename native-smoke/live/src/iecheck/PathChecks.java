package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.config.Setting;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.renderers.MeteorSwarmWarningPathRenderer;
import indevo.utils.helper.CircularArc;

import java.util.ArrayList;
import java.util.List;

public final class PathChecks {
    private static int frames = 0;
    private static int redNoMeteors = 0;
    private static int fullRed = 0;
    private static int flips = 0;
    private static int lastRed = 0;
    private static int mostMeteors = 0;
    private static int signs = 0;

    private PathChecks() {
    }

    private static int red(MeteorSwarmWarningPathRenderer renderer) {
        int count = 0;
        for (SectorEntityToken sign : renderer.warningSigns) if (Factions.PIRATES.equals(sign.getFaction().getId())) count++;
        return count;
    }

    private static int meteors(StarSystemAPI system) {
        int count = 0;
        for (CustomCampaignEntityAPI entity : system.getCustomEntities()) {
            String type = entity.getCustomEntityType();
            if (type != null && (type.startsWith("IndEvo_meteor_") || type.startsWith("IndEvo_icy_rock_") || type.startsWith("IndEvo_spicy_rock_"))) count++;
        }
        return count;
    }

    private static MeteorSwarmWarningPathRenderer renderer(StarSystemAPI system) {
        return (MeteorSwarmWarningPathRenderer) system.getMemoryWithoutUpdate().get(MeteorSwarmWarningPathRenderer.MEM_INSTANCE);
    }

    private static void reports(MeteorSwarmWarningPathRenderer renderer, float... angles) {
        for (float angle : angles) renderer.addAngle(angle);
        renderer.advance(1.01f);
    }

    private static void clear(MeteorSwarmWarningPathRenderer renderer) {
        for (SectorEntityToken sign : renderer.warningSigns) sign.setExpired(true);
        renderer.done = true;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            StarSystemAPI system = Colony.planets(2).get(0).getStarSystem();
            c.put("system", system.getId());
            Colony.jump(system, 12000f, 0f);

            // arc from 40 to 130 degrees, 90 degrees of a 40000 radius circle
            CircularArc plain = new CircularArc(new org.lwjgl.util.vector.Vector2f(0f, 0f), 40000f, 40f, 130f);
            MeteorSwarmWarningPathRenderer one = new MeteorSwarmWarningPathRenderer(system, plain);
            one.spawnWarningSigns();
            int total = one.warningSigns.size();
            c.note("plain arc signs " + total);

            reports(one, 85.3f);
            int lone = red(one);
            c.expect(lone <= 3, "lone meteor marks only the signs around it (" + lone + " of " + total + " red)");

            reports(one, 60f, 62f, 70f);
            int band = red(one);
            c.expect(band > 0 && band < total / 2, "a band of meteors marks its part of the path (" + band + " of " + total + " red)");

            reports(one);
            c.expect(red(one) == 0, "no meteors left, no red signs (" + red(one) + " red)");
            clear(one);

            // arc over the 0 degree line, 320 to 50
            CircularArc wrap = new CircularArc(new org.lwjgl.util.vector.Vector2f(0f, 0f), 40000f, 320f, 50f);
            MeteorSwarmWarningPathRenderer two = new MeteorSwarmWarningPathRenderer(system, wrap);
            two.spawnWarningSigns();
            total = two.warningSigns.size();
            reports(two, 358f, 2f);
            int crossing = red(two);
            c.expect(crossing > 0 && crossing < total / 2, "meteors on both sides of 0 degrees mark only their part (" + crossing + " of " + total + " red)");
            clear(two);
            system.getMemoryWithoutUpdate().unset(MeteorSwarmWarningPathRenderer.MEM_INSTANCE);
        });
        steps.add(c -> {
            StarSystemAPI system = Global.getSector().getStarSystem((String) c.get("system"));
            Colony.setting(Setting.METEOR_IMMEDIATE_MODE, true);
            Global.getSector().getMemoryWithoutUpdate().unset(MeteorSwarmManager.MEM_TIMEOUT);
            system.getMemoryWithoutUpdate().unset(MeteorSwarmManager.MEM_TIMEOUT);
            MeteorSwarmManager.getInstance().spawnShower(system, 2f, MeteorSwarmManager.MeteroidShowerType.ASTEROID, false);
            c.until(() -> renderer(system) != null, 5f, "warning path spawned");
        });
        steps.add(c -> {
            StarSystemAPI system = Global.getSector().getStarSystem((String) c.get("system"));
            c.until(() -> renderer(system) == null, 80f, "warning path ends", () -> {
                MeteorSwarmWarningPathRenderer renderer = renderer(system);
                if (renderer == null) return;
                int red = red(renderer);
                int meteors = meteors(system);
                signs = renderer.warningSigns.size();
                frames++;
                mostMeteors = Math.max(mostMeteors, meteors);
                if (red > 0 && meteors == 0) redNoMeteors++;
                if (signs > 0 && red == signs) fullRed++;
                if (Math.abs(red - lastRed) > signs / 2) flips++;
                lastRed = red;
            });
        });
        steps.add(c -> {
            c.note("swarm frames " + frames + ", signs " + signs + ", most meteors " + mostMeteors + ", full red frames " + fullRed + ", red with no meteors " + redNoMeteors + ", big red jumps " + flips);
            c.expect(redNoMeteors <= 200, "path holds no red after the last meteor is gone (" + redNoMeteors + " sampled frames)");
            c.expect(fullRed == 0, "path never goes full red in a real swarm (" + fullRed + " sampled frames)");
            c.expect(flips == 0, "path never flashes, no jump over half the signs (" + flips + ")");
        });
        return steps;
    }
}
