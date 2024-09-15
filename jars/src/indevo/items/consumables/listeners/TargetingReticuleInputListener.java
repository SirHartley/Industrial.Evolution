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
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;

import java.util.List;

public class TargetingReticuleInputListener implements CampaignInputListener {

    public int lastSlotVal = -1;
    public boolean missileActive = false;
    public MissileCampaignRenderer renderer;

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(TargetingReticuleInputListener.class)) manager.addListener(new TargetingReticuleInputListener(), false);
    }

    public static TargetingReticuleInputListener getInstance(){
        return Global.getSector().getListenerManager().getListeners(TargetingReticuleInputListener.class).get(0);
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.getCurrentInteractionDialog() != null) return;

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (input.getEventType().equals(InputEventType.KEY_DOWN)) {
                if (missileActive) return; //no cheating

                int eventVal = input.getEventValue();

                if (eventVal > 1 && eventVal < 10) { //1 to 9
                    int slotVal = eventVal - 2;

                    PersistentUIDataAPI.AbilitySlotAPI slot = Global.getSector().getUIData().getAbilitySlotsAPI().getCurrSlotsCopy().get(slotVal);
                    String ability = Global.getSector().getPlayerFleet().isInHyperspace() ? slot.getInHyperAbilityId() : slot.getAbilityId();

                    if (ability == null || ability.isEmpty()) return;
                    AbilitySpecAPI spec = Global.getSettings().getAbilitySpec(ability);
                    boolean isMissile = spec.hasTag("indevo_missile");
                    boolean isAOE = spec.hasTag("aoe");

                    if (isMissile && Global.getSector().getPlayerFleet().getAbility(ability).isUsable()) {
                        missileActive = true;
                        lastSlotVal = eventVal;

                        renderer = isAOE ? new MissileTargetingReticuleRendererWithAOE() : new MissileTargetingReticuleRenderer();
                        LunaCampaignRenderer.addRenderer(renderer);
                    }
                }
            } else if (input.getEventType().equals(InputEventType.KEY_UP)) {
                if (input.getEventValue() == lastSlotVal) {

                    AbilityPlugin p = Global.getSector().getPlayerFleet().getAbility( Global.getSector().getUIData().getAbilitySlotsAPI().getCurrSlotsCopy().get(lastSlotVal -2).getAbilityId());
                    ((BaseMissileConsumableAbilityPlugin) p).forceActivation();
                    p.setCooldownLeft(p.getSpec().getDeactivationCooldown());

                    missileActive = false;
                    lastSlotVal = -1;
                    renderer.setDone();
                    renderer = null;
                }
            }
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {

    }
}
