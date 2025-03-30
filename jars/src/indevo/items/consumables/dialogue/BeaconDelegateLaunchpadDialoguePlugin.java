package indevo.items.consumables.dialogue;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import indevo.industries.petshop.dialogue.PetNameAndAssignDialogueDelegate;
import indevo.items.consumables.itemAbilities.BeaconAbilityPlugin;

import java.util.Map;

public class BeaconDelegateLaunchpadDialoguePlugin implements InteractionDialogPlugin {
    public InteractionDialogAPI dialog;
    public BeaconAbilityPlugin plugin;

    static enum OptionID {
        INIT,
    }

    public BeaconDelegateLaunchpadDialoguePlugin (BeaconAbilityPlugin p){
        this.plugin = p;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();

        optionSelected(null, BeaconDelegateLaunchpadDialoguePlugin.OptionID.INIT);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == BeaconDelegateLaunchpadDialoguePlugin.OptionID.INIT) {

            dialog.showCustomDialog(PetNameAndAssignDialogueDelegate.WIDTH,
                    100f,
                    new BeaconMessageSetterDialogueDelegate(dialog, plugin));
        }
    }

    public void close() {
        dialog.dismiss();
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {

    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {

    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
