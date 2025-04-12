package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventMouseButton;
import com.fs.starfarer.api.input.InputEventType;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import indevo.items.consumables.itemAbilities.missiles.BaseMissileConsumableAbilityPlugin;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class OnClickAbilityInputListener implements MissileTargetUIKeypressListener, CampaignInputListener {

    public boolean active = false;
    public MissileCampaignRenderer renderer;
    public AbilityPlugin plugin;

    public OnClickAbilityInputListener(AbilityPlugin plugin) {
        this.plugin = plugin;
    }

    public void activate(){
        MissileActivationManager.getInstanceOrRegister().setCurrentListener(this);
        Global.getSector().getListenerManager().addListener(this);

        active = true;

        AbilitySpecAPI spec = plugin.getSpec();
        boolean isAOE = spec.hasTag("aoe");
        boolean isArty = spec.hasTag("artillery");

        //see OnKeyPressAbilityInputListener for comment
        renderer = new MissileSkillshotTargetingReticuleRenderer();
        if (isArty) renderer = new ArtilleryAOEReticuleRenderer();
        else if (isAOE) new MissileAOETargetingReticuleRenderer();

        LunaCampaignRenderer.addRenderer(renderer);
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (!active) return;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();

        //reset if there is an active dialogue to avoid weird rendering issues
        if (ui.getCurrentInteractionDialog() != null) {
            reset();
            return;
        }

        for (InputEventAPI input : events) {
            //if it's anything other than a mouse or spacebar we abort
            if (input.isKeyboardEvent() && input.getEventValue() != Keyboard.KEY_SPACE && input.getEventValue() != Keyboard.KEY_LSHIFT){
                MissileActivationManager.getInstanceOrRegister().deregisterListenerOnNextTick(this);
                reset();
                return;
            }

            //we eat the down event so it doesn't accidentally trigger something like movement
            if (input.getEventType().equals(InputEventType.MOUSE_DOWN) && input.getEventValue() == InputEventMouseButton.LEFT) {
                input.consume();
                continue;
            }

            if (input.getEventType().equals(InputEventType.MOUSE_UP) && input.getEventValue() == InputEventMouseButton.LEFT) {
                if (!renderer.isValidPosition()){
                    Global.getSoundPlayer().playUISound("IndEvo_denied_buzzer", 1f, 1f);
                    input.consume();
                    return;
                }

                ((BaseMissileConsumableAbilityPlugin) plugin).forceActivation();
                plugin.setCooldownLeft(plugin.getSpec().getDeactivationCooldown());

                active = false;
                renderer.setDone();
                renderer = null;

                input.consume();

                return;
            }
        }
    }

    public void reset() {
        active = false;
        if (renderer != null) renderer.setDone();
        renderer = null;
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {

    }

    @Override
    public boolean isActive() {
        return active;
    }
}