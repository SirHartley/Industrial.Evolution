package com.fs.starfarer.api.plugins.timers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IndEvo_raidTimeout implements EconomyTickListener {

    public static final String RAID_TIMEOUT_KEY = "$IndEvo_RaidTimeout";
    public static final String AI_RAID_TIMEOUT_KEY = "$IndEvo_RaidTimeout_AI";

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_raidTimeout.class).info(Text);
        }
    }

    public static void addRaidedSystem(StarSystemAPI starSystem, Float timeout, boolean AI) {
        String key = AI ? AI_RAID_TIMEOUT_KEY : RAID_TIMEOUT_KEY;
        Map<String, Float> raidTimeoutMap = IndEvo_IndustryHelper.getMapFromMemory(key);

        String id = starSystem.getId();

        if (!raidTimeoutMap.containsKey(id)) {
            raidTimeoutMap.put(id, timeout);
            debugMessage("Adding new raided system: " + starSystem.getName());

            //have to check for smaller added amount in case a beta core and non beta core base raid it in the same month
        } else if (raidTimeoutMap.get(id) > timeout) {
            raidTimeoutMap.put(id, timeout);
            debugMessage("adjusting existing raided system: " + starSystem.getName());
        }

        IndEvo_IndustryHelper.storeMapInMemory(raidTimeoutMap, key);
    }

    public static boolean containsSystem(StarSystemAPI starSystem, boolean AI) {
        String key = AI ? AI_RAID_TIMEOUT_KEY : RAID_TIMEOUT_KEY;
        String id = starSystem.getId();

        boolean valid = IndEvo_IndustryHelper.getMapFromMemory(key).containsKey(id);
        debugMessage("Target is timed out: " + valid);
        return valid;
    }

    public void reportEconomyTick(int iterIndex) {
        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != lastIterInMonth) return;

        debugMessage("RaidTimeout tick");
        Map<String, Float> raidTimeoutMap = IndEvo_IndustryHelper.getMapFromMemory(RAID_TIMEOUT_KEY);
        Map<String, Float> raidTimeoutMap_AI = IndEvo_IndustryHelper.getMapFromMemory(AI_RAID_TIMEOUT_KEY);

        increment(raidTimeoutMap);
        increment(raidTimeoutMap_AI);

        IndEvo_IndustryHelper.storeMapInMemory(raidTimeoutMap, RAID_TIMEOUT_KEY);
        IndEvo_IndustryHelper.storeMapInMemory(raidTimeoutMap_AI, AI_RAID_TIMEOUT_KEY);
    }

    private void increment(Map<String, Float> raidTimeoutMap) {
        Set<Map.Entry<String, Float>> setOfEntries = new HashSet<>(raidTimeoutMap.entrySet());

        for (Map.Entry<String, Float> entry : setOfEntries) {
            if (entry.getValue() > 1f) {
                raidTimeoutMap.put(entry.getKey(), entry.getValue() - 1f);
            } else {
                raidTimeoutMap.remove(entry.getKey());
            }
        }
    }

    public void reportEconomyMonthEnd() {
    }
}
