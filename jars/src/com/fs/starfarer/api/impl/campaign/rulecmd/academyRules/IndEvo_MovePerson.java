package com.fs.starfarer.api.impl.campaign.rulecmd.academyRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_Academy;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.academyRules.IndEvo_AcademyVariables.*;
import static com.fs.starfarer.api.impl.campaign.rulecmd.academyRules.IndEvo_CreatePersonSelectionList.getMarket;

public class IndEvo_MovePerson extends BaseCommandPlugin {

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_MovePerson.class).info(Text);
        }
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);

        if (memory.getString(CURRENT_PERSON_TYPE) == null || memory.getString(CURRENT_ACTION_TYPE) == null) {
            return false;
        }

        personTypes personType = personTypes.valueOf(memory.getString(CURRENT_PERSON_TYPE));
        ActionTypes actionType = ActionTypes.valueOf(memory.getString(CURRENT_ACTION_TYPE));

        String direction = "none";
        try {
            direction = params.get(0).getString(memoryMap); //better or worse
        } catch (IndexOutOfBoundsException e) {
            debugMessage("IndexOutOfBoundsException for person moving, this is expected");
        }

        debugMessage("Moving person, variables: actionType: " + actionType + " personType: " + personType + " dir: " + direction);

        PersonAPI person = (PersonAPI) memory.get(SELECTED_PERSON);

        MarketAPI market = getMarket(memoryMap);
        IndEvo_Academy academy = (IndEvo_Academy) market.getIndustry(IndEvo_ids.ACADEMY);

        switch (actionType) {
            case TRAIN:
                switch (personType) {
                    case OFFICER:
                        switch (direction) {
                            case "better":
                                academy.setOfficerForTraining(person, IndEvo_Academy.trainingDirection.BETTER);
                            case "worse":
                                academy.setOfficerForTraining(person, IndEvo_Academy.trainingDirection.WEAKER);
                        }
                        break;
                    case ADMIN:
                        academy.setAdminInTraining(person);
                        break;
                }
                break;

            case STORE:
                switch (personType) {
                    case OFFICER:
                        academy.storeOfficer(person);
                        break;
                    case ADMIN:
                        academy.storeAdmin(person);
                        break;
                }
                break;

            case RETRIEVE:
                switch (personType) {
                    case OFFICER:
                        academy.returnOfficer(person);
                        break;
                    case ADMIN:
                        academy.returnAdmin(person);
                        break;
                }
                break;

            case ABORT:
                switch (personType) {
                    case OFFICER:
                        switch (direction) {
                            case "fleet":
                                academy.abortOfficerTraining(false);
                                break;
                            case "storage":
                                academy.abortOfficerTraining(true);
                                break;
                        }

                        break;
                    case ADMIN:
                        switch (direction) {
                            case "fleet":
                                academy.abortAdminTraining(false);
                                break;
                            case "storage":
                                academy.abortAdminTraining(true);
                                break;
                        }
                        break;
                }
                break;
        }

        return true;
    }
}

