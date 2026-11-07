package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import indevo.items.consumables.entities.ConcussiveMissileEntityPlugin;
import indevo.items.consumables.fleet.MissileAIReactionManager;
import indevo.utils.helper.StringHelper;
import indevo.skillshot.render.AreaReticuleRenderer;
import indevo.skillshot.render.SkillshotRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class ConcussiveMissileAbilityPlugin extends BaseMissileConsumableAbilityPlugin{

    public static final float MISSILE_SPEED = 2000f;
    public static final float MISSILE_DUR = 4f;
    public static final String MISSILE_ID = "IndEvo_consumable_missile_concussive";

    @Override
    public SkillshotRenderer createReticule() {
        return new AreaReticuleRenderer();
    }

    @Override
    protected void onSkillshotFired(Vector2f target, float angle) {
        BaseMissileEntityPlugin.ConsumableMissileParams params = new BaseMissileEntityPlugin.ConsumableMissileParams(getFleet(), angle, MISSILE_DUR, MISSILE_SPEED, target);
        SectorEntityToken t = getFleet().getContainingLocation().addCustomEntity(Misc.genUID(), null,MISSILE_ID, getFleet() != null ? getFleet().getFaction().getId() : null, params);
        t.setLocation(getFleet().getLocation().x, getFleet().getLocation().y);

        MissileAIReactionManager.reportFleetUsedMissile(getFleet(), MISSILE_ID);
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;
        float spad = 3f;
        Color hl = Misc.getHighlightColor();

        tooltip.addPara("Violently launches fleets within %s away from the explosion, causing %s. More effective on small or fast-moving fleets.", opad, hl,
                Math.round(ConcussiveMissileEntityPlugin.SHOVE_RANGE) + "units",
                "no damage");

        tooltip.addPara("Travel speed: %s", opad, Color.GREEN, "Medium");
        tooltip.addPara("Deployment type: %s", spad, Color.ORANGE, "AOE");
        tooltip.addPara("Rearming duration: %s", spad, hl, (int) Math.ceil(getCooldownDays()) +" "+ StringHelper.getDayOrDays((int) Math.ceil(getCooldownDays())));
    }
}
