package indevo.industries.petshop.script;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.utils.ModPlugin;
import indevo.utils.memory.SessionTransientMemory;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

public class EscBlockerMemoryIterator implements EveryFrameScript {

    @Override
    public boolean isDone() {
        return !Global.getSector().getCampaignUI().isShowingDialog();
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        ModPlugin.log("present");

        if (mem.contains(BLOCK_ESC)) {
            mem.set(BLOCK_ESC, true, mem.getExpire(BLOCK_ESC) - 0.99f); //manually iterate that fucking key so it goes away after two frames I am so sick of this
            if (mem.getExpire(BLOCK_ESC) <= 0f) mem.unset(BLOCK_ESC);
        }
    }
}
