package industrial_evolution.campaign.rulecmd.academyRules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static industrial_evolution.campaign.rulecmd.academyRules.IndEvo_AcademyVariables.*;

public class IndEvo_DesignateSubListPageToLoad extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);

        if (memory.getString(CURRENT_PERSON_TYPE) == null || memory.getString(CURRENT_ACTION_TYPE) == null) {
            return false;
        }

        int maxPages = ((ArrayList<Object>) memory.get(PERSON_LIST_CONTAINER)).size();
        int currentPage;

        if (memory.getString(CURRENT_SUBLIST_PAGE) != null) {
            currentPage = Integer.parseInt(memory.getString(CURRENT_SUBLIST_PAGE));
        } else {
            currentPage = 1;
        }

        switch (action) {
            case "next":
                if (currentPage < maxPages) {
                    currentPage++;
                    memory.set(CURRENT_SUBLIST_PAGE, currentPage);
                    return true;
                }
                break;
            case "prev":
                if (currentPage > 1) {
                    currentPage--;
                    memory.set(CURRENT_SUBLIST_PAGE, currentPage);
                    return true;
                }
                break;
            case "set":
                int page = Integer.parseInt(params.get(1).getString(memoryMap));
                memory.set(CURRENT_SUBLIST_PAGE, page);
                return true;
        }
        return false;
    }
}