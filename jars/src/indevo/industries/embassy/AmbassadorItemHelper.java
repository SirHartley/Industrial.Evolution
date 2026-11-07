package indevo.industries.embassy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.utils.helper.MiscIE;

import java.util.Set;

public class AmbassadorItemHelper {

    public static final String RANK_AMBASSADOR = "factionRep";
    public static final String POST_AMBASSADOR = "ambassador";
    public static final String KEY_AMBASSADOR = "$deconomics_ambAllowed_";

    public static String getFactionMemoryKey(FactionAPI faction) {
        return KEY_AMBASSADOR + faction.getId();
    }

    public static Set<String> getAllowedFactionID() {
        Set<String> allowedList = MiscIE.getCSVSetFromMemory(Ids.EMBASSY_LIST);
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();

        for (String key : memory.getKeys()) {
            if (key.contains(KEY_AMBASSADOR) && memory.getBoolean(key))
                allowedList.add(key.substring(KEY_AMBASSADOR.length()));
        }

        return allowedList;

    }

    // data is the person id, the person stays in important people while the item exists
    public static SpecialItemData createItem(PersonAPI person) {
        return new SpecialItemData(ItemIds.AMBASSADOR, person.getId());
    }

    public static boolean isPersonMissing(SpecialItemData itemData) {
        return findPerson(itemData) == null;
    }

    public static PersonAPI getPersonForItem(SpecialItemData itemData) {
        PersonAPI person = findPerson(itemData);
        if (person == null) throw new IllegalStateException("Ambassador item without a person: " + (itemData == null ? null : itemData.getData()));
        return person;
    }

    public static FactionAPI getFactionForItem(SpecialItemData itemData) {
        return getPersonForItem(itemData).getFaction();
    }

    private static PersonAPI findPerson(SpecialItemData itemData) {
        if (itemData == null || itemData.getData() == null) return null;
        return Global.getSector().getImportantPeople().getPerson(itemData.getData());
    }
}
