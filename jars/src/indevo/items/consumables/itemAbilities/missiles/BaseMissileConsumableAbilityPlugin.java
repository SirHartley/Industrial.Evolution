package indevo.items.consumables.itemAbilities.missiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.itemAbilities.BaseConsumableAbilityPlugin;
import indevo.items.consumables.listeners.MissileActivationManager;
import indevo.items.consumables.listeners.OnClickAbilityInputListener;
import indevo.items.consumables.listeners.OnKeyPressAbilityInputListener;
import indevo.utils.ModPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public abstract class BaseMissileConsumableAbilityPlugin extends BaseConsumableAbilityPlugin {

    @Override
    public boolean isUsable() {
        boolean otherMissileActive = MissileActivationManager.getInstanceOrRegister().hasActiveListener();
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

            tooltip.addSectionHeading("Item Effect", Alignment.MID, opad);
        }

        addTooltip(tooltip);

        boolean otherMissileActive = MissileActivationManager.getInstanceOrRegister().hasActiveListener();
        boolean dialogueActive = Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;

        if (otherMissileActive) tooltip.addPara("Already deploying a missile!", opad, Misc.getNegativeHighlightColor());
        if (dialogueActive) tooltip.addPara("Can only be activated from the ability bar.",  opad, Misc.getNegativeHighlightColor());
    }

    //only gets called on click in this case as the OnKeyPressAbilityInputListener intercepts key presses on it
    public void pressButton() {
        if (isUsable() && !turnedOn) {
            if (entity.isPlayerFleet()) {
                String soundId = getOnSoundUI();
                if (soundId != null) {
                    if (PLAY_UI_SOUNDS_IN_WORLD_SOURCES) {
                        Global.getSoundPlayer().playSound(soundId, 1f, 1f, Global.getSoundPlayer().getListenerPos(), new Vector2f());
                    } else {
                        Global.getSoundPlayer().playUISound(soundId, 1f, 1f);
                    }
                }

                final AbilityPlugin p = this;

                ModPlugin.log("Reporting valid missile click: " + getId());
                //only activate through UI on player fleet!
                //we have to delay the activation of this by one frame because the UI takes priority over input listeners with priority 0 and we want the key press to run first
                Global.getSector().getScripts().add(new DelayedActionScript(0f) {
                    @Override
                    public void doAction() {
                        //register listener and go through default programmatic way
                        OnClickAbilityInputListener listener = new OnClickAbilityInputListener(p);
                        if (!MissileActivationManager.getInstanceOrRegister().hasActiveListener()) {
                            ModPlugin.log("Missile activation manager has no active listener, registering new onClickListener");
                            listener.activate();
                        }
                    }
                });
            }
        }
    }

    //avoid the normal way of activation it since it's used by the click behaviour
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
