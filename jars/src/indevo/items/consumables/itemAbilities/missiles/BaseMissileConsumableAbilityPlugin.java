package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.itemAbilities.BaseConsumableAbilityPlugin;
import indevo.items.consumables.listeners.TargetingReticuleInputListener;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public abstract class BaseMissileConsumableAbilityPlugin extends BaseConsumableAbilityPlugin {

    @Override
    public boolean isUsable() {
        boolean otherMissileActive = TargetingReticuleInputListener.getInstance().missileActive;
        return super.isUsable() && !otherMissileActive;
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

        addTooltip(tooltip);

        boolean otherMissileActive = TargetingReticuleInputListener.getInstance().missileActive;
        if (otherMissileActive) tooltip.addPara("fire current missile before trying to shoot the next u dumbass", 10f);
        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) tooltip.addPara("can only be used from the ability bar", 10f);

    }

    public void forceActivation(){
        activateImpl();
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
