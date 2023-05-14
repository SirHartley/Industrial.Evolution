package indevo.industries.compatibility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;

import java.util.HashMap;
import java.util.Map;

public class TechTree {
    private static boolean isInstalled() {
        return Global.getSettings().getModManager().isModEnabled("Cryo_but_better");
    }

    public static boolean isAvailable(Industry industry) {
        if (!isInstalled()) return true;

        Map<String, Boolean> researchSaved = (HashMap<String, Boolean>) Global.getSector().getPersistentData().get("researchsaved");
        return researchSaved != null ?  researchSaved.get(industry.getId()) : false;
    }

    public static String getDefaultUnavailableReason(){
        return "Not researched";
    }
}
