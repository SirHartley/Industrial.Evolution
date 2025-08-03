package indevo.dialogue.sidepanel;

import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;

public class ButtonReportingDialogueDelegate extends BaseCustomDialogDelegate {

    public void reportButtonPressed(Object id) {
        if (id instanceof ButtonAction) ((ButtonAction) id).execute();
    }

}
