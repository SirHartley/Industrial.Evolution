package indevo.industries.museum.data;


import java.awt.*;
import indevo.config.Setting;

public class MuseumConstants {

    //submarket
    public static final Color SUBMARKET_COLOUR = new Color(199, 10, 63,255);

    //income
    public static final float MAX_ADDITIONAL_CREDITS = Setting.MUSEUM_MAX_ADDITIONAL_CREDITS.value();
    public static final float MIN_ADDITIONAL_CREDITS = Setting.MUSEUM_MIN_ADDITIONAL_CREDITS.value();
    public static final float DEFAULT_INCOME_MULT = 1f; //no setting

    //parades
    public static final int DEFAULT_MAX_PARADES = Setting.MUSEUM_DEFAULT_MAX_PARADES.intValue();
    public static final int[] PARADE_DAY_OPTIONS = new int[]{31,62,93,124}; //no setting
    public static final int DEFAULT_PARADE_MEMBERS_MAX = Setting.MUSEUM_DEFAULT_PARADE_MEMBERS_MAX.intValue();
    public static final int MIN_SHIPS_FOR_PARADE = Setting.MUSEUM_MIN_SHIPS_FOR_PARADE.intValue();
    public static final int TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION = Setting.MUSEUM_TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION.intValue();
    public static final String ON_PARADE_TAG = "indEvo_on_parade"; //no setting
    public static final String PARADE_FLEET_NAMES = "data/strings/parade_fleet_names.csv"; //no setting
    public static final int PARADE_FLEET_IMMIGRATION_BONUS = Setting.MUSEUM_PARADE_FLEET_IMMIGRATION_BONUS.intValue();
    public static final int PARADE_FLEET_STABILITY_BONUS = Setting.MUSEUM_PARADE_FLEET_STABILITY_BONUS.intValue();
    public static final String FLEET_RETURNING_TAG = "indEvo_parade_returning"; //no setting
    public static final int IMPROVE_EXTRA_PARADES = Setting.MUSEUM_IMPROVE_EXTRA_PARADES.intValue();

    //AI Cores
    public static final int ALPHA_CORE_EXTRA_PARADES = Setting.MUSEUM_ALPHA_CORE_EXTRA_PARADES.intValue();
    public static final int BETA_CORE_INCOME_PER_STABILITY = Setting.MUSEUM_BETA_CORE_INCOME_PER_STABILITY.intValue();
    public static final int BETA_CORE_INCOME_PER_POINT_IMMIGRATION = Setting.MUSEUM_BETA_CORE_INCOME_PER_POINT_IMMIGRATION.intValue();
    public static final float GAMMA_CORE_INCOME_MULT = Setting.MUSEUM_GAMMA_CORE_INCOME_MULT.value();

    public static final float UNIQUE_SHIP_VALUE_MULT = Setting.MUSEUM_UNIQUE_SHIP_VALUE_MULT.value();
}
