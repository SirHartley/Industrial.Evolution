package industrial_evolution.items.consumables.itemAbilities;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface IndEvo_SingleUseItemAbility {
    public void removeTriggerItem();
    public String getItemID();
    public void addTooltip(TooltipMakerAPI tooltip, boolean forItem);
}
