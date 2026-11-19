package indevo.industries.petshop.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class EscapeInterceptListener implements CampaignInputListener {
    public static final String BLOCK_ESC = "$IndEvo_BlockESC";

    // sector memory is saved and a dialog plugin holds its whole panel ui, so memory only gets a flag
    private static EscapeBlockingDialoguePluginAPI blocker;

    public EscapeInterceptListener() {

    }

    public static void block(EscapeBlockingDialoguePluginAPI plugin) {
        blocker = plugin;
        Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, true, 1f);
    }

    @Override
    public int getListenerInputPriority() {
        return 99999;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        if (!memory.contains(BLOCK_ESC)) {
            blocker = null;
            return;
        }
        // a flag loaded from a save has no plugin behind it
        if (blocker == null) return;

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (input.getEventValue() == Keyboard.KEY_ESCAPE) {
                input.consume();
                // vanilla pickers close on key down, the key up then reaches the panel that reopened
                if (!input.isKeyDownEvent()) continue;
                blocker.close();
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
