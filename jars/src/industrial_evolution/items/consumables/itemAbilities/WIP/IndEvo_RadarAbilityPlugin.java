package industrial_evolution.items.consumables.itemAbilities.WIP;

import com.fs.starfarer.api.Global;
import industrial_evolution.items.consumables.itemAbilities.IndEvo_BaseConsumableAbilityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class IndEvo_RadarAbilityPlugin extends IndEvo_BaseConsumableAbilityPlugin {

    @Override
    protected void activateImpl() {

    }

    @Override
    protected void applyEffect(float amount, float level) {

    }

    @Override
    protected void deactivateImpl() {

    }

    @Override
    protected void cleanupImpl() {

    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, boolean forItem) {
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        float opad = 10f;

        //restore a fixed amount of hull instantly 25k HP, cost of 0,2c / HP = 5k item cost, doubled for good measure, 10k item cost
        //Average ship has 5k hull
        //restore 20% CR on all ships up to max 50%

        if(!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }

        tooltip.addPara("Restores up to %s to ships in your fleet (total), and raises combat readiness by %s up to a %s per ship. " +
                        "Starts with the flagship and then goes on to officer-controlled ships, in the " +
                        "order they are placed in the fleet.", opad, highlight,
                "");
    }
}
