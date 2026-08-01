package skillshot.input;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventMouseButton;
import com.fs.starfarer.api.input.InputEventType;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.input.Keyboard;
import skillshot.SkillshotFramework;
import skillshot.SkillshotSettings;
import skillshot.ability.SkillshotAbility;
import skillshot.render.SkillshotRenderer;

import java.util.List;

/**
 * The click path: the player clicked the ability button, so aiming stays open until they click the
 * map. Registered per session by {@link skillshot.ability.BaseSkillshotAbility#pressButton()} and
 * torn down as soon as the shot resolves.
 */
public class OnClickSkillshotListener implements SkillshotInputListener, CampaignInputListener {

    protected boolean active = false;
    protected SkillshotRenderer renderer;
    protected SkillshotAbility ability;

    public OnClickSkillshotListener(SkillshotAbility ability) {
        this.ability = ability;
    }

    public void activate() {
        SkillshotActivationManager.getInstanceOrRegister().setCurrentListener(this);
        Global.getSector().getListenerManager().addListener(this);

        active = true;

        renderer = ability.createReticule();
        LunaCampaignRenderer.addRenderer(renderer);
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (!active) return;

        //bail on an opened dialog, the reticule would render over it
        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) {
            reset();
            return;
        }

        for (InputEventAPI input : events) {
            //any key other than the pause/hold-modifier keys cancels
            if (input.isKeyboardEvent() && input.getEventValue() != Keyboard.KEY_SPACE && input.getEventValue() != Keyboard.KEY_LSHIFT) {
                SkillshotActivationManager.getInstanceOrRegister().deregisterListenerOnNextTick(this);
                reset();
                return;
            }

            //eat the down event so it can't double as a movement order
            if (input.getEventType().equals(InputEventType.MOUSE_DOWN) && input.getEventValue() == InputEventMouseButton.LEFT) {
                input.consume();
                continue;
            }

            if (input.getEventType().equals(InputEventType.MOUSE_UP) && input.getEventValue() == InputEventMouseButton.LEFT) {
                if (!renderer.isValidPosition()) {
                    Global.getSoundPlayer().playUISound(SkillshotSettings.SOUND_INVALID_TARGET, 1f, 1f);
                    input.consume();
                    return;
                }

                SkillshotFramework.log("Firing skillshot on click: " + ability.getId());

                ability.forceActivation();
                ability.setCooldownLeft(ability.getSpec().getDeactivationCooldown());

                active = false;
                renderer.setDone();
                renderer = null;

                input.consume();
                return;
            }
        }
    }

    @Override
    public void reset() {
        active = false;
        if (renderer != null) renderer.setDone();
        renderer = null;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
    }
}
