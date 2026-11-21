package indevo.exploration.meteor.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.intel.MeteorShowerSpawningLocationIntel;

import java.util.List;
import java.util.Map;

public class IndEvo_RegisterTutorialMeteorIntel extends BaseCommandPlugin {

    public static final int SHOWER_DELAY_DAYS = 3;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
        MeteorSwarmManager.reserveForShower(loc, SHOWER_DELAY_DAYS);
        Global.getSector().getIntelManager().addIntel(new MeteorShowerSpawningLocationIntel(loc, 4f, MeteorSwarmManager.MeteroidShowerType.ASTEROID, SHOWER_DELAY_DAYS));
        return true;
    }
}
