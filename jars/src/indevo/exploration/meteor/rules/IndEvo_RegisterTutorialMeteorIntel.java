package indevo.exploration.meteor.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.intel.MeteorShowerLocationIntel;

import java.util.List;
import java.util.Map;

public class IndEvo_RegisterTutorialMeteorIntel extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getIntelManager().addIntel(new MeteorShowerLocationIntel(Global.getSector().getPlayerFleet().getContainingLocation(), 4f, MeteorSwarmManager.MeteroidShowerType.ASTEROID, 3));
        return true;
    }
}
