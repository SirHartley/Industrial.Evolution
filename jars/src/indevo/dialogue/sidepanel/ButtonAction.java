package indevo.dialogue.sidepanel;

import com.fs.starfarer.api.campaign.CustomDialogDelegate;

public abstract class ButtonAction implements ButtonActionInterface {
    CustomDialogDelegate delegate;

    public ButtonAction(CustomDialogDelegate delegate) {
        this.delegate = delegate;
    }
}
