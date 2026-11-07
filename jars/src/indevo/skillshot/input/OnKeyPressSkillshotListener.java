package indevo.skillshot.input;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventType;
import indevo.skillshot.SkillshotFramework;
import indevo.skillshot.SkillshotSettings;
import indevo.skillshot.ability.SkillshotAbility;
import indevo.skillshot.render.SkillshotRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class OnKeyPressSkillshotListener implements SkillshotInputListener, CampaignInputListener {

    public static final int FIRST_SLOT_KEY = Keyboard.KEY_1;
    public static final int LAST_SLOT_KEY = Keyboard.KEY_9;

    protected int heldSlotKey = -1;
    protected SkillshotAbility heldAbility;
    protected SkillshotRenderer renderer;

    public static OnKeyPressSkillshotListener getInstanceOrRegister() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        List<OnKeyPressSkillshotListener> listeners = manager.getListeners(OnKeyPressSkillshotListener.class);
        if (!listeners.isEmpty()) return listeners.get(0);

        OnKeyPressSkillshotListener listener = new OnKeyPressSkillshotListener();
        manager.addListener(listener, true);
        return listener;
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        // ctrl is the vanilla slot reassign modifier
        boolean ctrlPressed = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null || ctrlPressed) {
            reset();
            return;
        }

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (input.getEventType().equals(InputEventType.KEY_DOWN)) {
                // repeat key down while aiming must not become a button press, the key up can still come this frame
                if (isActive()) {
                    if (input.getEventValue() == heldSlotKey) input.consume();
                    continue;
                }

                onSlotKeyDown(input);
            } else if (input.getEventType().equals(InputEventType.KEY_UP)) {
                if (isActive() && input.getEventValue() == heldSlotKey) onSlotKeyUp(input);
            }
        }
    }

    protected void onSlotKeyDown(InputEventAPI input) {
        int keyValue = input.getEventValue();
        if (keyValue < FIRST_SLOT_KEY || keyValue > LAST_SLOT_KEY) return;

        SkillshotAbility ability = getSlottedSkillshotAbility(keyValue - FIRST_SLOT_KEY);
        if (ability == null || !ability.isUsable()) return;

        // left unconsumed, the ui turns it into an ordinary button press
        if (!ability.showReticuleOnActivation()) return;

        SkillshotFramework.log("Starting hotkey targeting for " + ability.getId());

        heldSlotKey = keyValue;
        heldAbility = ability;

        renderer = ability.createReticule();
        LunaCampaignRenderer.addRenderer(renderer);

        SkillshotActivationManager.getInstanceOrRegister().setCurrentListener(this);

        input.consume();
    }

    protected void onSlotKeyUp(InputEventAPI input) {
        if (!renderer.isValidPosition()) {
            Global.getSoundPlayer().playUISound(SkillshotSettings.SOUND_INVALID_TARGET, 1f, 1f);
            reset();
            return;
        }

        SkillshotFramework.log("Firing skillshot on key release: " + heldAbility.getId());

        // fires the ability seen on key down, the slot can change while held (hyperspace transit)
        heldAbility.forceActivation();
        reset();

        input.consume();
    }

    protected SkillshotAbility getSlottedSkillshotAbility(int slotIndex) {
        List<PersistentUIDataAPI.AbilitySlotAPI> slots = Global.getSector().getUIData().getAbilitySlotsAPI().getCurrSlotsCopy();
        if (slotIndex < 0 || slotIndex >= slots.size()) return null;

        PersistentUIDataAPI.AbilitySlotAPI slot = slots.get(slotIndex);
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        // the bar swaps in a different ability per slot while in hyperspace
        String abilityId = fleet.isInHyperspace() ? slot.getInHyperAbilityId() : slot.getAbilityId();
        if (abilityId == null || abilityId.isEmpty()) return null;

        if (!Global.getSettings().getAbilitySpec(abilityId).hasTag(SkillshotSettings.TAG_SKILLSHOT)) return null;

        AbilityPlugin plugin = fleet.getAbility(abilityId);
        if (plugin == null) return null;

        if (!(plugin instanceof SkillshotAbility ability)) {
            throw new IllegalStateException("IndEvo skillshot hotkey: ability " + abilityId + " is tagged "
                    + SkillshotSettings.TAG_SKILLSHOT + " but its plugin is not a SkillshotAbility");
        }

        return ability;
    }

    @Override
    public void reset() {
        heldSlotKey = -1;
        heldAbility = null;

        if (renderer != null) renderer.setDone();
        renderer = null;
    }

    @Override
    public boolean isActive() {
        return heldAbility != null;
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
    }
}
