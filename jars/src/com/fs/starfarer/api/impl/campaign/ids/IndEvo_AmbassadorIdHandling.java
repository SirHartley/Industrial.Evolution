package com.fs.starfarer.api.impl.campaign.ids;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.impl.items.specItemDataExt.IndEvo_AmbassadorItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;

import java.util.Set;

public class IndEvo_AmbassadorIdHandling {

    public static final String RANK_AMBASSADOR = "factionRep";
    public static final String POST_AMBASSADOR = "ambassador";
    public static final String KEY_AMBASSADOR = "$deconomics_ambAllowed_";

    public static String getFactionMemoryKey(FactionAPI faction) {
        return KEY_AMBASSADOR + faction.getId();
    }

    public static Set<String> getAllowedFactionID() {
        Set<String> allowedList = IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.EMBASSY_LIST);
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();

        for (String key : memory.getKeys()) {
            if (key.contains(KEY_AMBASSADOR) && memory.getBoolean(key))
                allowedList.add(key.substring(KEY_AMBASSADOR.length()));
        }

        return allowedList;

    }

    public static FactionAPI getFactionForItem(SpecialItemData itemData) {
        if (itemData instanceof IndEvo_AmbassadorItemData) {
            IndEvo_AmbassadorItemData item = (IndEvo_AmbassadorItemData) itemData;
            return item.getPerson() != null ? item.getPerson().getFaction() : null;
        }

        return null;
    }

    public static PersonAPI getPersonForItem(SpecialItemData itemData) {
        if (itemData instanceof IndEvo_AmbassadorItemData) {
            IndEvo_AmbassadorItemData item = (IndEvo_AmbassadorItemData) itemData;
            return item.getPerson();
        }

        return null;
    }
}
