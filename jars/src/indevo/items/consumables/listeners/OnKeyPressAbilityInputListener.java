package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventType;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import indevo.items.consumables.itemAbilities.missiles.BaseMissileConsumableAbilityPlugin;
import indevo.utils.ModPlugin;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

import java.util.List;

public class OnKeyPressAbilityInputListener implements MissileTargetUIKeypressListener, CampaignInputListener {

    //should really be using pressButton through the API instead of whatever this is

    public int lastSlotVal = -1;
    public boolean active = false;
    public MissileCampaignRenderer renderer;

    public static OnKeyPressAbilityInputListener getInstanceOrRegister() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        OnKeyPressAbilityInputListener listener;

        if (!manager.hasListenerOfClass(OnKeyPressAbilityInputListener.class)) {
            listener = new OnKeyPressAbilityInputListener();
            manager.addListener(listener, false);
        } else listener = manager.getListeners(OnKeyPressAbilityInputListener.class).get(0);

        return listener;
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.getCurrentInteractionDialog() != null) {
            reset();
            return;
        }

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (input.getEventType().equals(InputEventType.KEY_DOWN)) {
                if (isActive()) return; //can't press the same key twice through macros

                int eventVal = input.getEventValue();

                if (eventVal > 1 && eventVal < 11) { //1 to 9
                    int slotVal = eventVal - 2;

                    PersistentUIDataAPI.AbilitySlotAPI slot = Global.getSector().getUIData().getAbilitySlotsAPI().getCurrSlotsCopy().get(slotVal);
                    String ability = Global.getSector().getPlayerFleet().isInHyperspace() ? slot.getInHyperAbilityId() : slot.getAbilityId();

                    if (ability == null || ability.isEmpty()) return;

                    AbilitySpecAPI spec = Global.getSettings().getAbilitySpec(ability);
                    boolean isMissile = spec.hasTag("indevo_missile");
                    boolean isAOE = spec.hasTag("aoe");

                    if (isMissile && Global.getSector().getPlayerFleet().getAbility(ability).isUsable()) {
                        active = true;
                        lastSlotVal = eventVal;
                        MissileActivationManager.getInstanceOrRegister().setCurrentListener(this);

                        renderer = isAOE ? new MissileAOETargetingReticuleRenderer() : new MissileSkillshotTargetingReticuleRenderer();
                        LunaCampaignRenderer.addRenderer(renderer);
                        input.consume();
                    }
                }
            } else if (input.getEventType().equals(InputEventType.KEY_UP)) {
                if (isActive() && input.getEventValue() == lastSlotVal) {

                    AbilityPlugin p = Global.getSector().getPlayerFleet().getAbility( Global.getSector().getUIData().getAbilitySlotsAPI().getCurrSlotsCopy().get(lastSlotVal -2).getAbilityId());
                    ((BaseMissileConsumableAbilityPlugin) p).forceActivation();
                    p.setCooldownLeft(p.getSpec().getDeactivationCooldown());

                    active = false;
                    lastSlotVal = -1;
                    renderer.setDone();
                    renderer = null;

                    input.consume();
                }
            }
        }
    }

    public void reset(){
        active = false;
        lastSlotVal = -1;
        if(renderer != null) renderer.setDone();
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