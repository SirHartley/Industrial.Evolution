package skillshot.example;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import skillshot.SkillshotFramework;
import skillshot.ability.BaseSkillshotAbility;
import skillshot.render.DirectionReticuleRenderer;
import skillshot.render.SkillshotRenderer;

/**
 * A complete, working skillshot ability - the shortest thing that proves the loop end to end. Aim,
 * fire, and a marker briefly appears where you aimed.
 * <p>
 * Copy this as the starting point for a real one: swap the reticule, put your payload in
 * {@link #onSkillshotFired}.
 *
 * <pre>
 * abilities.csv: tags "skillshot", plugin skillshot.example.ExampleSkillshotAbility
 * </pre>
 */
public class ExampleSkillshotAbility extends BaseSkillshotAbility {

    @Override
    public SkillshotRenderer createReticule() {
        //for an ability that lands on a spot instead, return new AreaReticuleRenderer(400f) - or
        //new ValidatedAreaReticuleRenderer(400f, new MarketProximityValidator(500f)) to forbid
        //firing near inhabited worlds
        return new DirectionReticuleRenderer();
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        SkillshotFramework.log("Example skillshot fired at " + worldTarget + " (" + angleFromFleet + " degrees)");

        SectorEntityToken marker = getFleet().getContainingLocation().createToken(worldTarget);
        getFleet().getContainingLocation().addEntity(marker);
        Misc.fadeAndExpire(marker, 1f);
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;

        tooltip.addTitle(spec.getName());
        tooltip.addPara("Hold the ability key to aim, release to fire. Clicking the ability works too - "
                + "click again on the map to commit.", opad);
    }
}
