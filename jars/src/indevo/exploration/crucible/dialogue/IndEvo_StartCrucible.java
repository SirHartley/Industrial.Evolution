package indevo.exploration.crucible.dialogue;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin;

import java.util.List;
import java.util.Map;

public class IndEvo_StartCrucible extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        SectorEntityToken crucible = dialog.getInteractionTarget();
        BaseCrucibleEntityPlugin p = (BaseCrucibleEntityPlugin) crucible.getCustomPlugin();
        p.enable();

        return true;
    }
}
