package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.itemAbilities.BaseConsumableAbilityPlugin;
import indevo.items.consumables.listeners.TargetingReticuleInputListener;

import java.awt.*;

public abstract class BaseMissileConsumableAbilityPlugin extends BaseConsumableAbilityPlugin {

    @Override
    public boolean isUsable() {
        boolean otherMissileActive = TargetingReticuleInputListener.getInstanceOrRegister().missileActive;
        boolean dialogueActive = Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;

        return super.isUsable() && !otherMissileActive && !dialogueActive;
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

        tooltip.addSectionHeading("Item Effect", Alignment.MID, opad);

        addTooltip(tooltip);

        boolean otherMissileActive = TargetingReticuleInputListener.getInstanceOrRegister().missileActive;
        boolean dialogueActive = Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;

        if (otherMissileActive) tooltip.addPara("Already deploying a missile!", opad, Misc.getNegativeHighlightColor());
        if (dialogueActive) tooltip.addPara("Can only be activated from the ability bar.",  opad, Misc.getNegativeHighlightColor());
    }

    public void forceActivation(){
        activateImpl();
        removeTriggerItem();
    }

    public abstract void addTooltip(TooltipMakerAPI tooltip);

    @Override
    protected void applyEffect(float amount, float level) {

    }

    @Override
    protected void deactivateImpl() {

    }

    @Override
    protected void cleanupImpl() {

    }
}
