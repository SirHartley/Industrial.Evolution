package indevo.dialogue.beacons.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.WarningBeaconIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IndEvo_RemoveCurrentInteractionTarget extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        if (dialog.getInteractionTarget() != null) {
            SectorEntityToken t = dialog.getInteractionTarget();
            if (t.hasTag(Tags.WARNING_BEACON)){
                for (IntelInfoPlugin p : new ArrayList<>(Global.getSector().getIntelManager().getIntel())){
                    if (p instanceof WarningBeaconIntel && p.getMapLocation(null) == t) Global.getSector().getIntelManager().removeIntel(p);
                }
            }

            Misc.fadeAndExpire(t, 1f);
            return true;
        }

        return false;
    }
}
