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

    public EscapeInterceptListener() {

    }

    @Override
    public int getListenerInputPriority() {
        return 99999;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        if (!memory.contains(BLOCK_ESC)) return;

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (input.getEventValue() == Keyboard.KEY_ESCAPE) {
                input.consume();
                if (memory.get(BLOCK_ESC) instanceof EscapeBlockingDialoguePluginAPI) ((EscapeBlockingDialoguePluginAPI) memory.get(BLOCK_ESC)).close();
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
