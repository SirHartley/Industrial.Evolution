package indevo.industries.embassy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import indevo.ids.Ids;
import indevo.items.specialitemdata.AmbassadorItemData;
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

    public static FactionAPI getFactionForItem(SpecialItemData itemData) {
        if (itemData instanceof AmbassadorItemData) {
            AmbassadorItemData item = (AmbassadorItemData) itemData;
            return item.getPerson() != null ? item.getPerson().getFaction() : null;
        }

        return null;
    }

    public static PersonAPI getPersonForItem(SpecialItemData itemData) {
        if (itemData instanceof AmbassadorItemData) {
            AmbassadorItemData item = (AmbassadorItemData) itemData;
            return item.getPerson();
        }

        return null;
    }
}
