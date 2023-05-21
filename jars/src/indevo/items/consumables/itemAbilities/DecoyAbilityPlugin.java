package indevo.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.entityAbilities.DecoyMineAbility;

import java.awt.*;

public class DecoyAbilityPlugin extends BaseConsumableAbilityPlugin {

    @Override
    protected void activateImpl() {
        SectorEntityToken t = entity.getContainingLocation().addCustomEntity(Misc.genUID(), "Decoy", "IndEvo_decoy_mine", null, 15f, 0f, 0f);
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

        if (!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }

        tooltip.addPara("Drops a powerful signal emitter at the current location. " +
                        "It will broadcast a signal across all bands in a %s to attract the attention of unoccupied patrol fleets. Patrols usually investigate between %s. " +
                        "Does not work if the target fleet currently has other priorities.", opad, highlight,
                Math.round(DecoyMineAbility.BASE_RANGE) + " SU range", Math.round(DecoyMineAbility.BASE_SEARCH_DAYS) + " to " + Math.round(DecoyMineAbility.BASE_SEARCH_DAYS * 2) + " days");
    }
}