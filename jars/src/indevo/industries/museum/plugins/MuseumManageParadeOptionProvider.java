package indevo.industries.museum.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.museum.data.ParadeFleetProfile;
import indevo.industries.museum.industry.Museum;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

import java.awt.*;
import java.util.List;

public class MuseumManageParadeOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    //recall - set parade loc outside viewport (angle to old loc) and set return assignment (forceReturnToSource)
    //create - set up new parade, data: Name, Target planet, Ships, duration (add button: repeat)
    //stop parades & resume random parades

    //make sure to have contingency for multiple parades

    public static final float WIDTH = 600f;
    public static final float HEIGHT = Global.getSettings().getScreenHeight() - 300f;
    protected static final float BUTTON_30 = 28, BUTTON_80 = 80, BUTTON_120 = 120, BUTTON_150 = 150f, BUTTON_100 = 100f;

    public static final float ENTRY_HEIGHT = 166; //MUST be even
    public static final float ENTRY_WIDTH = WIDTH - 10f; //MUST be even

    public static void register(){
        Global.getSector().getListenerManager().addListener(new MuseumManageParadeOptionProvider(), true);
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.MUSEUM;
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        Museum museum = ((Museum) opt.ind);

        //update so list isn't empty if opened the first time
        List<ParadeFleetProfile> profiles = museum.getParadeFleetProfiles();
        int maxParades = museum.getMaxParades();
        if (profiles.size() < maxParades) while (profiles.size() < maxParades) profiles.add(new ParadeFleetProfile(museum));

        ManageParadeDialoguePlugin delegate = new ManageParadeDialoguePlugin(museum);
        ui.showDialog(null, delegate);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        Color hl = Misc.getHighlightColor();
        float opad = 10f;

        tooltip.addPara("Manage the parade fleets your museum dispatches. Stop, resume them, or set up your own configurations.", 0f);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Manage Parades...";
    }
}