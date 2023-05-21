package indevo.industries.academy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.academy.industry.Academy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static indevo.industries.academy.rules.IndEvo_AcademyVariables.*;

public class IndEvo_CreatePersonSelectionList extends BaseCommandPlugin {

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_CreatePersonSelectionList.class).info(Text);
        }
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        ActionTypes actionType = ActionTypes.valueOf(params.get(0).getString(memoryMap));
        personTypes personType = personTypes.valueOf(params.get(1).getString(memoryMap));
        boolean isCheckOnly = false;

        try {
            isCheckOnly = params.get(2).getString(memoryMap) != null;
        } catch (IndexOutOfBoundsException e) {
            debugMessage("IndexOutOfBoundsException for list validity check, this is expected");
        }


        MarketAPI market = getMarket(memoryMap);
        Academy academy = (Academy) market.getIndustry(Ids.ACADEMY);

        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        ArrayList<PersonAPI> personList = new ArrayList<>();

        //for all types: get the appropriate listing and store it in the memory
        switch (personType) {
            case ADMIN:
                personList = getEligibleAdminPersonList(academy, actionType);
                break;
            case OFFICER:
                personList = getEligibleOfficerPersonList(academy, actionType);
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + personType);
        }

        if (isCheckOnly) {
            debugMessage("Checking if list is valid: " + (personList.size() > 0));
            return personList.size() > 0;
        }

        memory.set(CURRENT_ACTION_TYPE, actionType.toString(), EXPIRE_TIME);
        memory.set(CURRENT_PERSON_TYPE, personType.toString(), EXPIRE_TIME);
        memory.set(PERSON_LIST_CONTAINER, splitList(personList, 4), EXPIRE_TIME);
        memory.set(CURRENT_SUBLIST_PAGE, 1, EXPIRE_TIME);

        return true;
    }

    public static MarketAPI getMarket(Map<String, MemoryAPI> memoryMap) {
        MarketAPI market;
        if (memoryMap.get(MemKeys.LOCAL).get("$id").equals("station_galatia_academy"))
            market = Global.getSector().getEconomy().getMarket(ACADEMY_MARKET_ID);
        else
            market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        return market;
    }

    //Split a long list into as many parts as the "itemsPerPage" counter needs to be kept
    public static ArrayList<ArrayList<PersonAPI>> splitList(ArrayList<PersonAPI> list, int personsPerPage) {

        debugMessage("Splitting List into " + (int) Math.ceil(list.size() / (personsPerPage * 1f)) + " parts");

        int i = 0;
        ArrayList<ArrayList<PersonAPI>> splitList = new ArrayList<>();
        while (i < list.size()) {
            int nextInc = Math.min(list.size() - i, personsPerPage);
            ArrayList<PersonAPI> batch = new ArrayList<>(list.subList(i, i + nextInc));
            splitList.add(batch);
            i = i + nextInc;
        }

        return splitList;
    }

    private ArrayList<PersonAPI> getEligibleAdminPersonList(Academy academy, ActionTypes action) {
        ArrayList<PersonAPI> finalList = new ArrayList<>();

        switch (action) {
            case TRAIN:
                for (PersonAPI admin : academy.getAdminStorage()) {
                    if (academy.canTrainAdminPerson(admin)) {
                        finalList.add(admin);
                    }
                }

                for (AdminData admin : Global.getSector().getCharacterData().getAdmins()) {
                    if (academy.canTrainAdminPerson(admin.getPerson())) {
                        finalList.add(admin.getPerson());
                    }
                }
                break;

            case STORE:
                for (AdminData admin : Global.getSector().getCharacterData().getAdmins()) {
                    //check if it's installed anywhere
                    boolean notInstalled = true;
                    for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
                        if (market.getAdmin().getId().equals(admin.getPerson().getId())) {
                            notInstalled = false;
                        }
                    }

                    if (notInstalled) {
                        finalList.add(admin.getPerson());
                    }
                }
                break;

            case RETRIEVE:
                finalList.addAll(academy.getAdminStorage());
                break;
        }
        debugMessage("Getting eligible Admins, returning list with " + finalList.size() + " entries");
        return finalList;
    }

    private ArrayList<PersonAPI> getEligibleOfficerPersonList(Academy academy, ActionTypes action) {
        ArrayList<PersonAPI> finalList = new ArrayList<>();

        switch (action) {
            case TRAIN:
                for (PersonAPI officer : academy.getOfficerStorage()) {
                    if (academy.isOfficerTrainingAllowed(officer)) {
                        finalList.add(officer);
                    }
                }

                for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                    if (academy.isOfficerTrainingAllowed(officer.getPerson())) {
                        finalList.add(officer.getPerson());
                    }
                }
                break;

            case STORE:
                for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                    if (Misc.isUnremovable(officer.getPerson())) continue;

                    finalList.add(officer.getPerson());
                }
                break;

            case RETRIEVE:
                finalList.addAll(academy.getOfficerStorage());
                break;
        }
        debugMessage("Getting eligible officers, returning list with " + finalList.size() + " entries");
        return finalList;
    }
}



