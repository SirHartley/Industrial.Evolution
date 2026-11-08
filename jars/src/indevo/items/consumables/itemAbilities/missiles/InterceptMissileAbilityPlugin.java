package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import indevo.items.consumables.entities.InterceptMissileEntityPlugin;
import indevo.items.consumables.fleet.MissileAIReactionManager;
import indevo.utils.helper.StringHelper;
import indevo.skillshot.GuideLineStyle;
import indevo.skillshot.render.DirectionReticuleRenderer;
import indevo.skillshot.render.SkillshotRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class InterceptMissileAbilityPlugin extends BaseMissileConsumableAbilityPlugin{

    public static final float MISSILE_SPEED = 2000f;
    public static final float MISSILE_DUR = 4f;
    public static final String MISSILE_ID = "IndEvo_consumable_missile_intercept";

    @Override
    public SkillshotRenderer createReticule() {
        return new DirectionReticuleRenderer()
                .withTrajectory()
                .withLength(MISSILE_SPEED * MISSILE_DUR)
                .withLineStyle(GuideLineStyle.DOTTED);
    }

    @Override
    protected void onSkillshotFired(Vector2f target, float angle) {
        BaseMissileEntityPlugin.ConsumableMissileParams params = new BaseMissileEntityPlugin.ConsumableMissileParams(getFleet(), angle, MISSILE_DUR, MISSILE_SPEED, null);
        SectorEntityToken t = getFleet().getContainingLocation().addCustomEntity(Misc.genUID(), null,MISSILE_ID,getFleet() != null ? getFleet().getFaction().getId() : null, params);
        t.setLocation(getFleet().getLocation().x, getFleet().getLocation().y);

        MissileAIReactionManager.reportFleetUsedMissile(getFleet(), MISSILE_ID);
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;
        float spad = 3f;
        Color hl = Misc.getHighlightColor();

        tooltip.addPara("Explodes within a %s radius upon hitting a fleet, stunning everything caught for %s, interdicting for %s, and increasing sensor profile by %s for %s.", opad, hl,
                Math.round(InterceptMissileEntityPlugin.INTERDICT_RANGE) + "units",
                Math.round(InterceptMissileEntityPlugin.STUN_SECONDS) + " seconds",
                Math.round(InterceptMissileEntityPlugin.INTERDICT_SECONDS) + " seconds",
                Math.round(InterceptMissileEntityPlugin.TRACE_PROFILE_INCREASE) + "",
                Math.round(InterceptMissileEntityPlugin.TRACE_SECONDS) + " seconds");
        tooltip.addPara("The smart trigger ignores debris and meteors.", spad);

        tooltip.addPara("Travel speed: %s", opad, Color.RED, "Fast");
        tooltip.addPara("Deployment type: %s", spad, Color.CYAN, "On-Hit");
        tooltip.addPara("Rearming duration: %s", spad, hl, (int) Math.ceil(getCooldownDays()) +" "+ StringHelper.getDayOrDays((int) Math.ceil(getCooldownDays())));
    }
}
