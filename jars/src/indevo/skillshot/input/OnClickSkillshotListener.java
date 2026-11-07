package indevo.skillshot.input;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventMouseButton;
import com.fs.starfarer.api.input.InputEventType;
import indevo.skillshot.SkillshotFramework;
import indevo.skillshot.SkillshotSettings;
import indevo.skillshot.ability.SkillshotAbility;
import indevo.skillshot.render.SkillshotRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class OnClickSkillshotListener implements SkillshotInputListener, CampaignInputListener {

    protected SkillshotAbility ability;
    protected SkillshotRenderer renderer;

    public OnClickSkillshotListener(SkillshotAbility ability) {
        this.ability = ability;
    }

    public void activate() {
        renderer = ability.createReticule();
        LunaCampaignRenderer.addRenderer(renderer);

        Global.getSector().getListenerManager().addListener(this, true);
        SkillshotActivationManager.getInstanceOrRegister().setCurrentListener(this);
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (!isActive()) return;

        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) {
            reset();
            return;
        }

        for (InputEventAPI input : events) {
            if (input.getEventType().equals(InputEventType.KEY_DOWN)
                    && input.getEventValue() != Keyboard.KEY_SPACE && input.getEventValue() != Keyboard.KEY_LSHIFT) {
                reset();
                return;
            }

            // eat the down event so it can't double as a movement order
            if (input.getEventType().equals(InputEventType.MOUSE_DOWN) && input.getEventValue() == InputEventMouseButton.LEFT) {
                input.consume();
                continue;
            }

            if (input.getEventType().equals(InputEventType.MOUSE_UP) && input.getEventValue() == InputEventMouseButton.LEFT) {
                input.consume();

                if (!renderer.isValidPosition()) {
                    Global.getSoundPlayer().playUISound(SkillshotSettings.SOUND_INVALID_TARGET, 1f, 1f);
                    return;
                }

                SkillshotFramework.log("Firing skillshot on click: " + ability.getId());

                ability.forceActivation();
                reset();
                return;
            }
        }
    }

    @Override
    public void reset() {
        if (renderer != null) renderer.setDone();
        renderer = null;

        // input listeners run from a copy of the listener list, removing during input is safe
        Global.getSector().getListenerManager().removeListener(this);
    }

    @Override
    public boolean isActive() {
        return renderer != null;
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
    }
}
