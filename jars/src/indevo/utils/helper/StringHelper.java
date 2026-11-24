package indevo.utils.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Pair;

import java.awt.*;

import static com.fs.starfarer.api.characters.FullName.Gender.MALE;

/**
 * @Author: Histidine
 * Adapted for use with permission
 */

public class StringHelper {

    public static String getAbsPercentString(Float forFloat, boolean oneMinusFloat) {
        return oneMinusFloat ? Math.abs(Math.round((1.0f - forFloat) * 100f)) + "%" : Math.abs(Math.round(forFloat * 100f)) + "%";
    }

    public static String getFloatToIntStrx100(Float forFloat) {
        return Math.round(forFloat * 100f) + "";
    }

    public static String getDayOrDays(Integer days) {
        return days == 1 ? "day" : "days";
    }

    public static Pair<String, Color> getRepIntTooltipPair(FactionAPI faction) {
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();

        int repInt = (int) Math.ceil((Math.round(playerFaction.getRelationship(faction.getId()) * 100f)));
        RepLevel level = playerFaction.getRelationshipLevel(faction.getId());
        Color relColor = faction.getRelColor(playerFaction.getId());
        String str = repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";

        return new Pair<>(str, relColor);
    }

    public static String getHeOrShe(PersonAPI person) {
        return person.getGender() == MALE ? "he" : "she";
    }

    public static String getHimOrHer(FullName.Gender gender) {
        return gender == MALE ? "him" : "her";
    }

    // a leading acronym like "CR" keeps its capitals
    public static String lcFirst(String str) {
        if (str == null) return "Null";
        if (str.isEmpty()) return "";
        if (str.length() > 1 && Character.isUpperCase(str.charAt(1))) return str;
        return ("" + str.charAt(0)).toLowerCase() + str.substring(1);
    }

}
