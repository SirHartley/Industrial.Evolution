package indevo.industries.academy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static indevo.industries.academy.rules.IndEvo_AcademyVariables.*;

public class IndEvo_OptionStartsWith extends BaseCommandPlugin {

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_OptionStartsWith.class).info(Text);
        }
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String substring = params.get(0).getString(memoryMap);

        String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
        if (option == null) return false;

        //This is only for my use case, commit selected person to memory
        if (option.startsWith(PERSON_OPTION_PREFIX)) {
            String priorRuleId = params.get(1).getString(memoryMap);
            memoryMap.get(MemKeys.LOCAL).set(SELECTED_PERSON, getSelectedPerson(priorRuleId, memoryMap), EXPIRE_TIME);
        }

        return option.startsWith(substring);
    }

    public PersonAPI getSelectedPerson(String ruleId, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        ArrayList<ArrayList<PersonAPI>> splitList = (ArrayList<ArrayList<PersonAPI>>) memory.get(PERSON_LIST_CONTAINER);

        debugMessage("getSelectedPerson splitList size: " + splitList.size());
        String personID = ruleId.substring(PERSON_OPTION_PREFIX.length());
        debugMessage("rule ID " + ruleId);
        debugMessage("person ID " + personID);

        for (ArrayList<PersonAPI> list : splitList) {
            for (PersonAPI entry : list) {
                debugMessage("Compare: " + entry.getId());
                if (entry.getId().equals(personID)) {
                    return entry;
                }
            }
        }

        Global.getLogger(IndEvo_CreatePersonSelectionList.class).error("[Industrial.Evolution] IndEvo_OptionStartsWith could not find matching person to selected!");
        return null;
    }
}
