package indevo.utils.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import java.awt.*;
import java.text.Normalizer;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.characters.FullName.Gender.MALE;

/**
 * @Author: Histidine
 * Adapted for use with permission
 */

public class StringHelper {

	/*how to use:
		String string = StringHelper.getString("exerelin_invasion", "intelDesc");
		String attackerName = attacker.getDisplayNameWithArticle();
		String defenderName = defender.getDisplayNameWithArticle();
		int numFleets = (int) getOrigNumFleets();

		Map<String, String> sub = new HashMap<>();
		sub.put("$theFaction", attackerName);
		sub.put("$TheFaction", Misc.ucFirst(attackerName));
		sub.put("$theTargetFaction", defenderName);
		sub.put("$TheTargetFaction", Misc.ucFirst(defenderName));
		sub.put("$market", target.getName());
		sub.put("$isOrAre", attacker.getDisplayNameIsOrAre());
		sub.put("$location", locationName);
		sub.put("$strDesc", strDesc);
		sub.put("$numFleets", numFleets + "");
		sub.put("$fleetsStr", numFleets > 1 ? StringHelper.getString("fleets") : StringHelper.getString("fleet"));
		string = StringHelper.substituteTokens(string, sub);

		//"intelDesc":"$TheFaction $isOrAre invading $market in the $location, held by $theTargetFaction. The invasion forces are projected to be $strDesc and are likely comprised of $numFleets $fleetsStr.",
*/

    public static final String FLEET_ASSIGNMENT_CATEGORY = "indEvo_fleetAssignments";

    public static final String HR = "-----------------------------------------------------------------------------";

    public static String getString(String category, String id, boolean ucFirst) {
        String str = "";
        try {
            str = Global.getSettings().getString(category, id);
        } catch (Exception ex) {
            // could be a string not found
            //str = ex.toString();  // looks really silly
            Global.getLogger(StringHelper.class).warn(ex);
            return "[INVALID]" + id;
        }
        if (ucFirst) str = Misc.ucFirst(str);
        return str;
    }

    public static String getString(String category, String id) {
        return getString(category, id, false);
    }

    public static String getString(String id, boolean ucFirst) {
        return getString("IndEvo_misc", id, ucFirst);
    }

    public static String getString(String id) {
        return getString("IndEvo_misc", id, false);
    }

    public static String ucFirstIgnore$(String str) {
        if (str == null) return "Null";
        if (str.isEmpty()) return "";
        if (str.charAt(0) != '$') return Misc.ucFirst(str);
        return ("" + str.charAt(0)) + ("" + str.charAt(1)).toUpperCase() + str.substring(2);
    }

    /**
     * @param toModify
     * @param token
     * @param replace
     * @param ucFormToo In addition to replacing $token with $replace, also replace $Token with $Replace
     * @return
     */
    public static String substituteToken(String toModify, String token, String replace, boolean ucFormToo) {
        String str = toModify.replaceAll("\\" + token, replace);
        if (ucFormToo) str = str.replaceAll("\\" + ucFirstIgnore$(token), Misc.ucFirst(replace));
        return str;
    }

    public static String substituteToken(String toModify, String token, String replace) {
        return toModify.replaceAll("\\" + token, replace);
    }

    public static String substituteTokens(String toModify, Map<String, String> replacements) {
        for (Map.Entry<String, String> tmp : replacements.entrySet()) {
            toModify = substituteToken(toModify, tmp.getKey(), tmp.getValue());
        }
        return toModify;
    }

    public static String substituteTokens(String toModify, List<Pair<String, String>> replacements) {
        for (Pair<String, String> tmp : replacements) {
            toModify = substituteToken(toModify, tmp.one, tmp.two);
        }
        return toModify;
    }

    public static String getStringAndSubstituteToken(String category, String id, String token, String replace) {
        return getStringAndSubstituteToken(category, id, token, replace, false);
    }

    public static String getStringAndSubstituteToken(String category, String id, String token, String replace, boolean ucFormToo) {
        String str = getString(category, id);
        return substituteToken(str, token, replace, ucFormToo);
    }

    public static String getStringAndSubstituteToken(String id, String token, String replace) {
        return getStringAndSubstituteToken("IndEvo_misc", id, token, replace, false);
    }

    public static String getStringAndSubstituteToken(String id, String token, String replace, boolean ucFormToo) {
        return getStringAndSubstituteToken("IndEvo_misc", id, token, replace, ucFormToo);
    }

    public static String getStringAndSubstituteTokens(String category, String id, List<Pair<String, String>> replacements) {
        String str = getString(category, id);
        return substituteTokens(str, replacements);
    }

    public static String getStringAndSubstituteTokens(String category, String id, Map<String, String> replacements) {
        String str = getString(category, id);
        return substituteTokens(str, replacements);
    }

    public static String getStringAndSubstituteTokens(String id, List<Pair<String, String>> replacements) {
        return getStringAndSubstituteTokens("IndEvo_misc", id, replacements);
    }

    public static String getStringAndSubstituteTokens(String id, Map<String, String> replacements) {
        return getStringAndSubstituteTokens("IndEvo_misc", id, replacements);
    }

    public static String substituteFactionTokens(String str, String factionId) {
        return substituteFactionTokens(str, Global.getSector().getFaction(factionId));
    }

    /**
     * Replaces {@code $faction} and {@code $theFaction} substrings
     * (and the uppercase versions thereof) in {@code str}.
     *
     * @param str
     * @param faction
     * @return
     */
    public static String substituteFactionTokens(String str, FactionAPI faction) {
        Map<String, String> replacements = new HashMap<>();
        String name = getFactionShortName(faction);
        String theName = faction.getDisplayNameWithArticle();
        replacements.put("$faction", name);
        replacements.put("$Faction", Misc.ucFirst(name));
        replacements.put("$theFaction", theName);
        replacements.put("$TheFaction", Misc.ucFirst(theName));

        return substituteTokens(str, replacements);
    }

    public static Highlights getFactionHighlights(String factionId) {
        return getFactionHighlights(Global.getSector().getFaction(factionId));
    }

    public static Highlights getFactionHighlights(FactionAPI faction) {
        Highlights hl = new Highlights();
        String name = getFactionShortName(faction);
        String theName = faction.getDisplayNameWithArticle();
        hl.setText(theName, Misc.ucFirst(theName), name, Misc.ucFirst(name));

        return hl;
    }

    public static String getFactionShortName(FactionAPI faction) {
        if (faction.isPlayerFaction() && !Misc.isPlayerFactionSetUp()) {
            return StringHelper.getString("player");
        } else {
            String name = faction.getEntityNamePrefix();
            if (name == null || name.isEmpty()) {
                name = faction.getDisplayName();
            }

            return name;
        }
    }

    public static String getFleetAssignmentString(String id, String target, String missionType) {
        String str = getString(FLEET_ASSIGNMENT_CATEGORY, id);
        if (target != null) str = substituteToken(str, "$target", target);
        if (missionType != null)
            str = substituteToken(str, "$missionType", getString(FLEET_ASSIGNMENT_CATEGORY, missionType));
        return str;
    }

    public static String getFleetAssignmentString(String id, String target) {
        return getFleetAssignmentString(id, target, null);
    }

    public static String getShipOrFleet(CampaignFleetAPI fleet) {
        String fleetOrShip = getString("IndEvo_misc", "fleet");
        if (fleet != null) {
            if (fleet.getFleetData().getMembersListCopy().size() == 1) {
                fleetOrShip = getString("IndEvo_misc", "ship");
                if (fleet.getFleetData().getMembersListCopy().get(0).isFighterWing()) {
                    fleetOrShip = getString("IndEvo_misc", "fighterWing");
                }
            }
        }
        return fleetOrShip;
    }

    // http://stackoverflow.com/a/15191508
    // see https://bitbucket.org/Histidine/exerelin/issues/1/marketarchtype-java-somehow-confuses-the for why this is used
    public static String flattenToAscii(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        string = Normalizer.normalize(string, Normalizer.Form.NFD);
        for (char c : string.toCharArray()) {
            if (c <= '\u007F') sb.append(c);
        }
        return sb.toString();
    }

    public static List<String> factionIdListToFactionNameList(List<String> factionIds, boolean ucFirst) {
        List<String> result = new ArrayList<>();
        for (String factionId : factionIds) {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            String name = faction.getDisplayName();
            if (ucFirst)
                name = Misc.ucFirst(name);
            result.add(name);
        }
        return result;
    }

    public static String writeStringCollection(Collection<String> strings) {
        return writeStringCollection(strings, false, false);
    }

    public static String writeStringCollection(Collection<String> strings, boolean includeAnd, boolean oxfordComma) {
        String str = "";
        int num = 0;
        for (String entry : strings) {
            str += entry;
            num++;
            if (num < strings.size()) {
                if (oxfordComma || !includeAnd || num <= strings.size() - 1)
                    str += ", ";
                if (includeAnd)
                    str += StringHelper.getString("and") + " ";
            }
        }
        return str;
    }

    public static void addFactionNameTokensCustom(List<Pair<String, String>> tokens, String str, FactionAPI faction) {
        if (faction != null) {
            String factionName = faction.getDisplayName();
            String strUc = Misc.ucFirst(str);

            tokens.add(new Pair<>("$" + str + "Long", faction.getDisplayNameLong()));
            tokens.add(new Pair<>("$" + strUc + "Long", Misc.ucFirst(faction.getDisplayNameLong())));
            tokens.add(new Pair<>("$the" + strUc + "Long", faction.getDisplayNameLongWithArticle()));
            tokens.add(new Pair<>("$The" + strUc + "Long", Misc.ucFirst(faction.getDisplayNameLongWithArticle())));

            tokens.add(new Pair<>("$" + str + "IsOrAre", faction.getDisplayNameIsOrAre()));

            tokens.add(new Pair<>("$" + str, factionName));
            tokens.add(new Pair<>("$" + strUc, Misc.ucFirst(factionName)));
            tokens.add(new Pair<>("$the" + strUc, faction.getDisplayNameWithArticle()));
            tokens.add(new Pair<>("$The" + strUc, Misc.ucFirst(faction.getDisplayNameWithArticle())));
        }
    }

    public static String getAbsPercentString(Float forFloat, boolean oneMinusFloat) {
        return oneMinusFloat ? Math.abs(Math.round((1.0f - forFloat) * 100f)) + "%" : Math.abs(Math.round(forFloat * 100f)) + "%";
    }

    public static String getFloatToIntStrx100(Float forFloat) {
        return Math.round(forFloat * 100f) + "";
    }

    public static String getDayOrDays(Integer days) {
        String day = StringHelper.getString("days");
        if (days == 1) day = StringHelper.getString("day");

        return day;
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
        return getHeOrShe(person.getGender());
    }

    public static String getHimOrHer(PersonAPI person) {
        return getHimOrHer(person.getGender());
    }

    public static String getHeOrShe(FullName.Gender gender) {
        return gender == MALE ? StringHelper.getString("he") : StringHelper.getString("she");
    }

    public static String getHimOrHer(FullName.Gender gender) {
        return gender == MALE ? StringHelper.getString("him") : StringHelper.getString("her");
    }

    public static String lcFirst(String str) {
        if (str == null) return "Null";
        if (str.isEmpty()) return "";
        return ("" + str.charAt(0)).toLowerCase() + str.substring(1);
    }

}
