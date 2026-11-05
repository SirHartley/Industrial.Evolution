package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.utils.helper.Circle;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.TrigHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ArcMath {
    private ArcMath() {
    }

    private static CircularArc arc(Random random) {
        float radius = 3000f + random.nextFloat() * 7000f;
        Vector2f center = MathUtils.getPointOnCircumference(null, radius, 360f * random.nextFloat());
        float toCenter = Misc.getAngleInDegrees(center, new Vector2f(0, 0));
        float adjustment = 50f + random.nextFloat() * 30f;
        Vector2f start = MeteorSwarmManager.intersectWithGridBoundary(center, MathUtils.clampAngle(toCenter + adjustment));
        Vector2f end = MeteorSwarmManager.intersectWithGridBoundary(center, MathUtils.clampAngle(toCenter - adjustment));
        Circle circle = TrigHelper.findThreePointCircle(start, center, end);
        return new CircularArc(circle, circle.getAngleForPoint(start), circle.getAngleForPoint(end));
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Random random = new Random(42L);
            SectorEntityToken token = Global.getSector().getPlayerFleet().getContainingLocation().createToken(0f, 0f);
            int runs = 4000;
            int early = 0;
            int firstFrames = 0;
            float maxRadius = 0f;
            for (int i = 0; i < runs; i++) {
                CircularArc arc = arc(random);
                maxRadius = Math.max(maxRadius, arc.radius);
                ArcingMovementModule module = new ArcingMovementModule(arc, 100f + random.nextFloat() * 150f);
                module.init(token);
                for (int frame = 0; frame < 300; frame++) {
                    module.advance(1f / 60f);
                    if (module.isMovementFinished()) {
                        early++;
                        if (frame < 10) firstFrames++;
                        if (early <= 5) c.note("early finish at frame " + frame + " radius " + arc.radius + " start " + arc.startAngle + " end " + arc.endAngle + " dir " + arc.getAngleTravelDir());
                        break;
                    }
                }
            }
            c.note("max radius " + maxRadius);
            c.expect(early == 0, "arc movement never finishes within 5 seconds of spawning: " + early + " of " + runs + " did, " + firstFrames + " in the first 10 frames");
        });
        return steps;
    }
}
