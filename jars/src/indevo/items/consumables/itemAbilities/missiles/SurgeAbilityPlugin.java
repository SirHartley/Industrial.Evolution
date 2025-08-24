package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entityAbilities.NoWellSlipstreamAbility;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.abilities.GenerateSlipsurgeAbility.SENSOR_RANGE_MULT;

public class SurgeAbilityPlugin extends BaseMissileConsumableAbilityPlugin{

    public static String TEMP_ABILITY_ID = "IndEvo_ability_temp_generate_slipstream";

    @Override
    protected void activateImpl() {
        Vector2f mousePos = new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));

        float angleToMouse = Misc.getAngleInDegrees(getFleet().getLocation(), mousePos);

        CampaignFleetAPI fleet = getFleet();
        fleet.addAbility(TEMP_ABILITY_ID);
        ((NoWellSlipstreamAbility) fleet.getAbility(TEMP_ABILITY_ID)).activateAtAngle(angleToMouse);
        fleet.removeAbility(TEMP_ABILITY_ID);
    }

    @Override
    public boolean isUsable() {
        return super.isUsable() && getFleet().isInHyperspace() && !Misc.isInAbyss(getFleet());
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;
        float spad = 3f;
        Color hl = Misc.getHighlightColor();

        tooltip.addPara("Creates a powerful, makeshift gravity well to generate a %s in the chosen direction.", opad, hl,
                "slipsurge");

        tooltip.addPara("A slipsurge can allow the fleet to rapidly travel up to %s light-years. "
                        + "Attempting to %s "
                        + "during the transit will decelerate the fleet quickly. Fleet sensor range is "
                        + "reduced by %s during the transit.", opad, hl,
                "15", "move slowly",
                "" + (int)Math.round((1f - SENSOR_RANGE_MULT) * 100f) + "%");

        if (!getFleet().isInHyperspace()) tooltip.addPara("%s",
                opad, Misc.getNegativeHighlightColor(),
                "Only usable in Hyperspace!");

        if (!Misc.isInAbyss(getFleet())) tooltip.addPara("%s",
                opad, Misc.getNegativeHighlightColor(),
                "Does not function in the Abyss!");
    }
}