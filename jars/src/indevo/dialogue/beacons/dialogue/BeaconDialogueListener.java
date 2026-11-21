package indevo.dialogue.beacons.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lwjgl.input.Keyboard;

public class BeaconDialogueListener extends BaseCampaignEventListener {

    public BeaconDialogueListener(boolean permaRegister) {
        super(permaRegister);
    }

    public static void register() {
        Global.getSector().addTransientListener(new BeaconDialogueListener(false));
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        super.reportShownInteractionDialog(dialog);

        if (dialog == null) return;

        SectorEntityToken target = dialog.getInteractionTarget();

        if (target != null && target.hasTag(Tags.WARNING_BEACON) && target.isInHyperspace()) {
            OptionPanelAPI options = dialog.getOptionPanel();
            boolean hasLeaveOption = options.hasOption("beaconLeave");

            if (hasLeaveOption) {
                options.removeOption("beaconLeave");
                // removeOption keeps the removed last button as the anchor, next addOption throws, rebuild resets it
                options.restoreSavedOptions(options.getSavedOptionList());
            }

            options.addOption("Dismantle the warning beacon", "IndEvo_beacon_destroyConfirm");

            if (hasLeaveOption) {
                options.addOption("Leave", "beaconLeave");
                options.setShortcut("beaconLeave", Keyboard.KEY_ESCAPE, false, false, false, true);
            }
        }
    }
}
