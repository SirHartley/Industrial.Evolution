package com.fs.starfarer.api.splinterFleet.plugins.fleetManagement;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DetachmentMemory {
    public static final String DETACHMENT_STORE_MEMORY_KEY = "$SplinterFleetDetachmentStoreMemoryReference";
    public static final String DETACHMENT_LOC_KEY = "$SplinterFleet_DetachmentLocation_";

    public static Map<Integer, CampaignFleetAPI> getFleetMap() {
        Map<Integer, CampaignFleetAPI> loadoutMap;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(DETACHMENT_STORE_MEMORY_KEY))
            loadoutMap = (Map<Integer, CampaignFleetAPI>) mem.get(DETACHMENT_STORE_MEMORY_KEY);
        else {
            loadoutMap = new HashMap<>();
            for (int i : Arrays.asList(1, 2, 3)) loadoutMap.put(i, null); //initial setup to make sure it's never empty

            mem.set(DETACHMENT_STORE_MEMORY_KEY, loadoutMap);
        }

        return loadoutMap;
    }

   /* public static CampaignFleetAPI getDetachmentFromLoc(int num){
        String systemID = Global.getSector().getMemoryWithoutUpdate().getString(DETACHMENT_LOC_KEY + num);
        Global.getSector().getStarSystem()
    }*/

    public static void addDetachment(CampaignFleetAPI detachment, int num) {
        getFleetMap().put(num, detachment);
    }

    public static CampaignFleetAPI getDetachment(int num) {
        return getFleetMap().get(num);
    }

    public static int getNumForFleet(CampaignFleetAPI fleet) {
        for (int i : Arrays.asList(1, 2, 3)) {
            if (isDetachmentActive(i) && getDetachment(i).getId().equals(fleet.getId())) return i;
        }

        return 0;
    }

    public static void removeDetachment(int num) {
        getFleetMap().remove(num);
    }

    public static void removeDetachment(CampaignFleetAPI fleet) {
        int num = DetachmentMemory.getNumForFleet(fleet);
        if (num > 0) DetachmentMemory.removeDetachment(num);
    }

    public static boolean isDetachmentActive(int num) {
        return getFleetMap().get(num) != null;
    }

    public static boolean isAnyDetachmentActive() {
        for (CampaignFleetAPI f : getFleetMap().values()) if (f != null) return true;
        return false;
    }
}
