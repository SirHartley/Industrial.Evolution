package indevo.industries.museum.data;

import java.awt.*;

public class MuseumConstants {

    //submarket
    public static final int MAX_ADDITIONAL_SUBMARKETS = 5;
    public static final Color SUBMARKET_COLOUR = new Color(199, 10, 63,255);

    //income
    public static final float MAX_ADDITIONAL_CREDITS = 1950;
    public static final float MIN_ADDITIONAL_CREDITS = 50f;
    public static final float DEFAULT_INCOME_MULT = 1f;

    //parades
    public static final int DEFAULT_MAX_PARADES = 1;
    public static final int[] PARADE_DAY_OPTIONS = new int[]{31,62,93,124};
    public static final int DEFAULT_PARADE_MEMBERS_MAX = 15;
    public static final int MIN_SHIPS_FOR_PARADE = 3;
    public static final int TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION = 20;
    public static final String ON_PARADE_TAG = "indEvo_on_parade";
    public static final String PARADE_FLEET_NAMES = "data/strings/parade_fleet_names.csv";
    public static final int PARADE_FLEET_IMMIGRATION_BONUS = 5;
    public static final int PARADE_FLEET_STABILITY_BONUS = 1;

    //AI Cores
    public static final int ALPHA_CORE_EXTRA_PARADES = 1;
    public static final int BETA_CORE_INCOME_PER_STABILITY = 20000;
    public static final int BETA_CORE_INCOME_PER_POINT_IMMIGRATION = 3000;
    public static final float GAMMA_CORE_INCOME_MULT = 1.3f;
}
