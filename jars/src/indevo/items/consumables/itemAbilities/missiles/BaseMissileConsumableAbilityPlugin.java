package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.ItemAbilityHelper;
import indevo.items.consumables.itemAbilities.SingleUseItemAbility;
import indevo.skillshot.ability.BaseSkillshotAbility;

import java.awt.*;

public abstract class BaseMissileConsumableAbilityPlugin extends BaseSkillshotAbility implements SingleUseItemAbility {

    @Override
    public boolean isUsable() {
        return super.isUsable() && getCargoItemAmt() > 0;
    }

    @Override
    protected void onConsume() {
        removeTriggerItem();
    }

    @Override
    public void removeTriggerItem() {
        ItemAbilityHelper.removeItem(entity, getItemID());
    }

    @Override
    public String getItemID() {
        return ItemAbilityHelper.toggle(id);
    }

    public int getCargoItemAmt() {
        return ItemAbilityHelper.getItemAmount(entity, getItemID());
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        addTooltip(tooltip, false);
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

            tooltip.addSectionHeading("Item Effect", Alignment.MID, opad);
        }

        addTooltip(tooltip);
        addBlockedReasonsToTooltip(tooltip);
    }
}
