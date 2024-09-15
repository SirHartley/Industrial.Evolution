package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import org.lwjgl.util.vector.Vector2f;

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
        SectorEntityToken t = getFleet().getContainingLocation().addCustomEntity(Misc.genUID(), null,MISSILE_ID,null, params);
        t.setLocation(getFleet().getLocation().x, getFleet().getLocation().y);
    }


    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("goes boom", 10f);
    }
}
