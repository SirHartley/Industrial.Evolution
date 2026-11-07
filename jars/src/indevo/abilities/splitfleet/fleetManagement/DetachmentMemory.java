package indevo.abilities.splitfleet.fleetManagement;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DetachmentMemory {
    public static final String DETACHMENT_STORE_MEMORY_KEY = "$SplinterFleetDetachmentStoreMemoryReference";
    public static final List<Integer> SLOTS = List.of(1, 2);

    public static Map<Integer, CampaignFleetAPI> getFleetMap() {
        Map<Integer, CampaignFleetAPI> loadoutMap;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(DETACHMENT_STORE_MEMORY_KEY))
            loadoutMap = (Map<Integer, CampaignFleetAPI>) mem.get(DETACHMENT_STORE_MEMORY_KEY);
        else {
            loadoutMap = new HashMap<>();
            for (int i : SLOTS) loadoutMap.put(i, null); //initial setup to make sure it's never empty

            mem.set(DETACHMENT_STORE_MEMORY_KEY, loadoutMap);
        }

        return loadoutMap;
    }

    public static void addDetachment(CampaignFleetAPI detachment, int num) {
        getFleetMap().put(num, detachment);
    }

    public static CampaignFleetAPI getDetachment(int num) {
        return getFleetMap().get(num);
    }

    public static int getNumForFleet(CampaignFleetAPI fleet) {
        //walks the stored map so slot 3 entries from older saves still resolve
        for (Map.Entry<Integer, CampaignFleetAPI> e : getFleetMap().entrySet()) {
            if (e.getValue() != null && e.getValue().getId().equals(fleet.getId())) return e.getKey();
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
