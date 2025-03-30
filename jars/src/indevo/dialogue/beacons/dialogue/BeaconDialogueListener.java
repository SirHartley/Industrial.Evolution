package indevo.dialogue.beacons.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

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
            boolean hasLeaveOption = dialog.getOptionPanel().hasOption("beaconLeave");

            if (hasLeaveOption) dialog.getOptionPanel().removeOption("beaconLeave");
            dialog.getOptionPanel().addOption("Dismantle the warning beacon", "IndEvo_beacon_destroyConfirm");
            if (hasLeaveOption) dialog.getOptionPanel().addOption("Leave", "beaconLeave");
        }
    }
}
