package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import indevo.items.consumables.entities.ConcussiveMissileEntityPlugin;
import indevo.items.consumables.entities.InterceptMissileEntityPlugin;
import indevo.items.consumables.terrain.SmokeCloudTerrain;
import indevo.utils.helper.StringHelper;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class InterceptMissileAbilityPlugin extends BaseMissileConsumableAbilityPlugin{

    public static final float MISSILE_SPEED = 2000f;
    public static final float MISSILE_DUR = 4f;
    public static final String MISSILE_ID = "IndEvo_consumable_missile_intercept";

    @Override
    protected void activateImpl() {
        Vector2f mousePos = new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));

        float angleToMouse = Misc.getAngleInDegrees(getFleet().getLocation(), mousePos);

        //IndEvo_consumable_missile_explosive
        BaseMissileEntityPlugin.ConsumableMissileParams params = new BaseMissileEntityPlugin.ConsumableMissileParams(getFleet(), angleToMouse, MISSILE_DUR, MISSILE_SPEED, null);
        SectorEntityToken t = getFleet().getContainingLocation().addCustomEntity(Misc.genUID(), null,MISSILE_ID,null, params);
        t.setLocation(getFleet().getLocation().x, getFleet().getLocation().y);
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;
        float spad = 3f;
        Color hl = Misc.getHighlightColor();

        tooltip.addPara("Explodes within a %s radius upon hitting a fleet, stunning everything caught for %s, interdicting for %s, and increasing sensor profile by %s for %s.", opad, hl,
                Math.round(InterceptMissileEntityPlugin.INTERDICT_RANGE) + "su",
                Math.round(InterceptMissileEntityPlugin.STUN_SECONDS) + "seconds",
                Math.round(InterceptMissileEntityPlugin.INTERDICT_SECONDS) + "seconds",
                Math.round(InterceptMissileEntityPlugin.TRACE_PROFILE_INCREASE) + "",
                Math.round(InterceptMissileEntityPlugin.TRACE_SECONDS) + "seconds");

        tooltip.addPara("Travel speed: %s", opad, Color.RED, "Fast");
        tooltip.addPara("Deployment type: %s", spad, Color.CYAN, "On-Hit");
        tooltip.addPara("Rearming duration: %s", spad, hl, Math.round(getCooldownDays()) + StringHelper.getDayOrDays(Math.round(getCooldownDays())));
    }
}
