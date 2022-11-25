package industrial_evolution.campaign.rulecmd.edictRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import industrial_evolution.plugins.IndEvo_SessionTransientMemory;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

import static industrial_evolution.IndEvo_IndustryHelper.splitList;
import static industrial_evolution.campaign.rulecmd.edictRules.IndEvo_EdictVariables.*;

public class IndEvo_CreateEdictSelectionList extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        memory.set(EDICT_LIST_CONTAINER, splitList(new ArrayList<>(getEdictIdSet()), 4), EXPIRE_TIME);
        memory.set(CURRENT_SUBLIST_PAGE, 1, EXPIRE_TIME);

        return true;
    }

    public static Set<String> getEdictIdSet() {
        IndEvo_SessionTransientMemory mem = IndEvo_SessionTransientMemory.getInstance();

        if (mem.contains(EDICT_LIST_KEY)) return (Set<String>) mem.getSet(EDICT_LIST_KEY);

        Set<String> condSet = new HashSet<>();

        for (MarketConditionSpecAPI cond : Global.getSettings().getAllMarketConditionSpecs())
            if (cond.getId().contains(EDICT_IDENT_STR)) condSet.add(cond.getId());

        mem.set(EDICT_LIST_KEY, condSet);
        return condSet;
    }
}
