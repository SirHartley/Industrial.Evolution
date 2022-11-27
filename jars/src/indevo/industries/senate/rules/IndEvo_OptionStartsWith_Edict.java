package indevo.industries.senate.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

import static indevo.industries.senate.rules.IndEvo_EdictVariables.*;

public class IndEvo_OptionStartsWith_Edict extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String substring = params.get(0).getString(memoryMap);

        String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
        if (option == null) return false;

        //This is only for my use case, commit selected person to memory
        if (option.startsWith(EDICT_OPTION_PREFIX)) {
            String edictId = params.get(1).getString(memoryMap).substring(EDICT_OPTION_PREFIX.length());
            memoryMap.get(MemKeys.LOCAL).set(SELECTED_EDICT, edictId, EXPIRE_TIME);
        }

        return option.startsWith(substring);
    }
}
