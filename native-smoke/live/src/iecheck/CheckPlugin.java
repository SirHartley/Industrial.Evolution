package iecheck;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

public class CheckPlugin extends BaseModPlugin {
    private static final Logger log = Global.getLogger(CheckPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        // this mod sorts last, so lunalib has loaded the settings by now
        if ("off".equals(System.getProperty("ie.switches"))) SettingChecks.switchesOff();
        log.info("LIVE_CHECK application loaded");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        String group = System.getProperty("ie.check");
        if (group == null) return;
        log.info("LIVE_CHECK game loaded, group " + group);
        ArtilleryChecks.loaded();
        Global.getSector().addTransientScript(new Runner(group));
    }
}
