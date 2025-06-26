package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import indevo.items.consumables.entities.ExplosiveMissileEntityPlugin;
import indevo.items.consumables.fleet.MissileAIReactionManager;
import indevo.utils.helper.StringHelper;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class ExplosiveMissileAbilityPlugin extends BaseMissileConsumableAbilityPlugin{

    public static final float MISSILE_SPEED = 1000f;
    public static final float MISSILE_DUR = 4f;
    public static final String MISSILE_ID = "IndEvo_consumable_missile_explosive";

    @Override
    protected void activateImpl() {
        Vector2f mousePos = new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));

        float angleToMouse = Misc.getAngleInDegrees(getFleet().getLocation(), mousePos);

        //IndEvo_consumable_missile_explosive
        BaseMissileEntityPlugin.ConsumableMissileParams params = new BaseMissileEntityPlugin.ConsumableMissileParams(getFleet(), angleToMouse, MISSILE_DUR, MISSILE_SPEED, null);
        SectorEntityToken t = getFleet().getContainingLocation().addCustomEntity(Misc.genUID(), null,MISSILE_ID,getFleet() != null ? getFleet().getFaction().getId() : null, params);
        t.setLocation(getFleet().getLocation().x, getFleet().getLocation().y);

        MissileAIReactionManager.reportFleetUsedMissile(getFleet(), MISSILE_ID);
    }


    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;
        float spad = 3f;
        Color hl = Misc.getHighlightColor();

        tooltip.addPara("Explodes and damages fleets within %s on hit.", opad, hl,
                Math.round(ExplosiveMissileEntityPlugin.EXPLOSION_SIZE) + "su");
        tooltip.addPara("The smart trigger ignores debris and meteors.", spad);

        tooltip.addPara("Travel speed: %s", opad, new Color(100,100,255,255), "Slow");
        tooltip.addPara("Deployment type: %s", spad, Color.CYAN, "On-Hit");
        tooltip.addPara("Rearming duration: %s", spad, hl, (int) Math.ceil(getCooldownDays()) +" "+ StringHelper.getDayOrDays((int) Math.ceil(getCooldownDays())));
    }
}
