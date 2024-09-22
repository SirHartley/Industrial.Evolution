package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import indevo.items.consumables.entities.SmokeCloudEntityPlugin;
import indevo.items.consumables.terrain.SmokeCloudTerrain;
import indevo.utils.helper.StringHelper;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SmokeMissileAbilityPlugin extends BaseMissileConsumableAbilityPlugin{

    public static final float MISSILE_SPEED = 750f;
    public static final float MISSILE_DUR = 4f;
    public static final String MISSILE_ID = "IndEvo_consumable_missile_smoke";

    @Override
    protected void activateImpl() {
        Vector2f mousePos = new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));

        float angleToMouse = Misc.getAngleInDegrees(getFleet().getLocation(), mousePos);

        //IndEvo_consumable_missile_explosive
        BaseMissileEntityPlugin.ConsumableMissileParams params = new BaseMissileEntityPlugin.ConsumableMissileParams(getFleet(), angleToMouse, MISSILE_DUR, MISSILE_SPEED, mousePos);
        SectorEntityToken t = getFleet().getContainingLocation().addCustomEntity(Misc.genUID(), null,MISSILE_ID,null, params);
        t.setLocation(getFleet().getLocation().x, getFleet().getLocation().y);
    }


    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;
        float spad = 3f;
        Color hl = Misc.getHighlightColor();

        tooltip.addPara("Blankets a %s area with an impenetrable chaff cloud that reduces sensor profile by up to %s and slows fleets by up to %s depending on size.", opad, hl,
                Math.round(SmokeCloudEntityPlugin.BASE_RADIUS) + "su",
                StringHelper.getAbsPercentString(SmokeCloudTerrain.VISIBLITY_MULT, false),
                StringHelper.getAbsPercentString(SmokeCloudTerrain.BURN_PENALTY_MULT, false));

        tooltip.addPara("Travel speed: %s", opad, Color.GREEN, "Medium");
        tooltip.addPara("Deployment type: %s", spad, Color.ORANGE, "AOE");
        tooltip.addPara("Rearming duration: %s", spad, hl, Math.ceil(getCooldownDays()) +" "+ StringHelper.getDayOrDays(Math.round(getCooldownDays())));
    }
}
