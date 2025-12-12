package indevo.industries.museum.data;

import indevo.utils.helper.Settings;

import java.awt.*;

public class MuseumConstants {

    public static final Boolean MUSEUM_ENABLED = Settings.getBoolean(Settings.METEOR_ENABLED);

    //submarket
    public static final Color SUBMARKET_COLOUR = new Color(199, 10, 63,255);

    //income
    public static final float MAX_ADDITIONAL_CREDITS = Settings.getFloat(Settings.MUSEUM_MAX_ADDITIONAL_CREDITS);
    public static final float MIN_ADDITIONAL_CREDITS = Settings.getFloat(Settings.MUSEUM_MIN_ADDITIONAL_CREDITS);
    public static final float DEFAULT_INCOME_MULT = 1f; //no setting

    //parades
    public static final int DEFAULT_MAX_PARADES = Settings.getInt(Settings.MUSEUM_DEFAULT_MAX_PARADES);
    public static final int[] PARADE_DAY_OPTIONS = new int[]{31,62,93,124}; //no setting
    public static final int DEFAULT_PARADE_MEMBERS_MAX = Settings.getInt(Settings.MUSEUM_DEFAULT_PARADE_MEMBERS_MAX);
    public static final int MIN_SHIPS_FOR_PARADE = Settings.getInt(Settings.MUSEUM_MIN_SHIPS_FOR_PARADE);
    public static final int TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION = Settings.getInt(Settings.MUSEUM_TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION);
    public static final String ON_PARADE_TAG = "indEvo_on_parade"; //no setting
    public static final String PARADE_FLEET_NAMES = "data/strings/parade_fleet_names.csv"; //no setting
    public static final int PARADE_FLEET_IMMIGRATION_BONUS = Settings.getInt(Settings.MUSEUM_PARADE_FLEET_IMMIGRATION_BONUS);
    public static final int PARADE_FLEET_STABILITY_BONUS = Settings.getInt(Settings.MUSEUM_PARADE_FLEET_STABILITY_BONUS);
    public static final String FLEET_RETURNING_TAG = "indEvo_parade_returning"; //no setting
    public static final int IMPROVE_EXTRA_PARADES = Settings.getInt(Settings.MUSEUM_IMPROVE_EXTRA_PARADES);

    //AI Cores
    public static final int ALPHA_CORE_EXTRA_PARADES = Settings.getInt(Settings.MUSEUM_ALPHA_CORE_EXTRA_PARADES);
    public static final int BETA_CORE_INCOME_PER_STABILITY = Settings.getInt(Settings.MUSEUM_BETA_CORE_INCOME_PER_STABILITY);
    public static final int BETA_CORE_INCOME_PER_POINT_IMMIGRATION = Settings.getInt(Settings.MUSEUM_BETA_CORE_INCOME_PER_POINT_IMMIGRATION);
    public static final float GAMMA_CORE_INCOME_MULT = Settings.getFloat(Settings.MUSEUM_GAMMA_CORE_INCOME_MULT);

    public static final float UNIQUE_SHIP_VALUE_MULT = Settings.getFloat(Settings.MUSEUM_UNIQUE_SHIP_VALUE_MULT);
}
