package indevo.items.consumables.itemAbilities;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface SingleUseItemAbility {
    public void removeTriggerItem();

    public String getItemID();

    public void addTooltip(TooltipMakerAPI tooltip, boolean forItem);
}
