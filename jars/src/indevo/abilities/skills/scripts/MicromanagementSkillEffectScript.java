package indevo.abilities.skills.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.ui.CustomPanelAPI;

// kept so saves from before MicromanagementRemoteItemPatch load; the sector drops it on the first frame
public class MicromanagementSkillEffectScript implements EveryFrameScript {

    // saved fields of the old kotlin script, unused
    private int frames = 0;
    private CustomPanelAPI newIndustryPanel = null;

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
    }
}
