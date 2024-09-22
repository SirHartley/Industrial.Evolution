package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entities.BaseMissileEntityPlugin;
import indevo.items.consumables.entities.ConcussiveMissileEntityPlugin;
import indevo.items.consumables.entities.SmokeCloudEntityPlugin;
import indevo.items.consumables.terrain.SmokeCloudTerrain;
import indevo.utils.helper.StringHelper;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class ConcussiveMissileAbilityPlugin extends BaseMissileConsumableAbilityPlugin{

    public static final float MISSILE_SPEED = 2000f;
    public static final float MISSILE_DUR = 4f;
    public static final String MISSILE_ID = "IndEvo_consumable_missile_concussive";

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

        tooltip.addPara("Violently launches fleets within %s away from the explosion, causing %s. More effective on small or fast-moving fleets.", opad, hl,
                Math.round(ConcussiveMissileEntityPlugin.SHOVE_RANGE) + "su",
                "no damage");

        tooltip.addPara("Travel speed: %s", opad, Color.GREEN, "Medium");
        tooltip.addPara("Deployment type: %s", spad, Color.ORANGE, "AOE");
        tooltip.addPara("Rearming duration: %s", spad, hl, Math.ceil(getCooldownDays()) +" "+ StringHelper.getDayOrDays(Math.round(getCooldownDays())));
    }
}
