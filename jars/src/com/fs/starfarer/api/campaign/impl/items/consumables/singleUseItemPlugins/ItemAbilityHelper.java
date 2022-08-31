package com.fs.starfarer.api.campaign.impl.items.consumables.singleUseItemPlugins;

import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;

import java.util.HashMap;
import java.util.Map;

public class ItemAbilityHelper {
    public static Map<String, String> ID_MAP = new HashMap<String, String>(){{
        put(IndEvo_Items.CONSUMABLE_DRONES, IndEvo_ids.ABILITY_DRONES);
        put(IndEvo_Items.CONSUMABLE_LOCATOR, IndEvo_ids.ABILITY_LOCATOR);
        put(IndEvo_Items.CONSUMABLE_NANITES, IndEvo_ids.ABILITY_NANITES);
        put(IndEvo_Items.CONSUMABLE_SCOOP, IndEvo_ids.ABILITY_SCOOP);
        put(IndEvo_Items.CONSUMABLE_SPIKE, IndEvo_ids.ABILITY_SPIKE);
        put(IndEvo_Items.CONSUMABLE_STABILIZER, IndEvo_ids.ABILITY_STABILIZER);
        put(IndEvo_Items.CONSUMABLE_SUPERCHARGER, IndEvo_ids.ABILITY_SUPERCHARGER);
        put(IndEvo_Items.CONSUMABLE_DECOY, IndEvo_ids.ABILITY_DECOY);
    }};

    public static String toggle(String s){
        if(ID_MAP.values().contains(s)){
            for (Map.Entry<String, String> e : ID_MAP.entrySet()){
                if (e.getValue().equals(s)) return e.getKey();
            }
        }

        return ID_MAP.get(s);
    }
}
