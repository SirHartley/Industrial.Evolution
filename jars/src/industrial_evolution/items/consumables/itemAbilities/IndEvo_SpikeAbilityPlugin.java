package industrial_evolution.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import industrial_evolution.items.consumables.entities.SpikeEntityPlugin;
import industrial_evolution.items.consumables.entityAbilities.InterdictionMineAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class IndEvo_SpikeAbilityPlugin extends IndEvo_BaseConsumableAbilityPlugin {

    @Override
    protected void activateImpl() {
        SectorEntityToken t = entity.getContainingLocation().addCustomEntity(Misc.genUID(), "Drive Bubble Spike", "IndEvo_field_spike", null, 15f, 0f, 0f);
        t.setLocation(entity.getLocation().x, entity.getLocation().y);
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

        if(!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }

        tooltip.addPara("Drops a drive bubble spike at the current location. " +
                        "The spike arms after %s and activates when a hostile fleet enters within %s of it, firing an interdiction pulse with a %s range. The pulse does not discern between friend or foe, so make sure you are not in range.", opad, highlight,
                Math.round(SpikeEntityPlugin.TIME_TO_ARM) + " seconds", Math.round(SpikeEntityPlugin.TRIGGER_DETECTION_RANGE) + " SU", Math.round(InterdictionMineAbility.BASE_RANGE) + " SU");
    }
}
