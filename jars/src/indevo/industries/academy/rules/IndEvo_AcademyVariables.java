package indevo.industries.academy.rules;

public class IndEvo_AcademyVariables {
    public static final String PERSON_LIST_CONTAINER = "$IndEvo_personList";
    public static final String CURRENT_SUBLIST_PAGE = "$IndEvo_subListPage";
    public static final String CURRENT_ACTION_TYPE = "$IndEvo_actionType";
    public static final String CURRENT_PERSON_TYPE = "$IndEvo_personType";
    public static final String PERSON_OPTION_PREFIX = "IndEvo_SelectPerson_";
    public static final String SELECTED_PERSON = "$IndEvo_selectedPerson";
    public static final Float EXPIRE_TIME = 0f;

    public static final String ACADEMY_MARKET_ID = "IndEvo_academyMarket";


    public enum ActionTypes {
        TRAIN,
        STORE,
        RETRIEVE,
        ABORT
    }

    public enum personTypes {
        OFFICER,
        ADMIN
    }
}
