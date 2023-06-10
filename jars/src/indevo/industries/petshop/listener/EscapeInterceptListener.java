package indevo.industries.petshop.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
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
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(BLOCK_ESC)) return;

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (input.getEventValue() == Keyboard.KEY_ESCAPE) {
                input.consume();
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
