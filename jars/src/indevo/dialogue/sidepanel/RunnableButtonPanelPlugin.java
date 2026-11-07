package indevo.dialogue.sidepanel;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;

// buttons made in this panel's createUIElement report here, a Runnable button id runs on press
public class RunnableButtonPanelPlugin extends BaseCustomUIPanelPlugin {

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId instanceof Runnable r) r.run();
    }
}
