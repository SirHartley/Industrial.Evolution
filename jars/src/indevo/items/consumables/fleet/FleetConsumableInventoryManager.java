package indevo.items.consumables.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.FleetSpawnListener;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.items.consumables.ItemAbilityHelper;

import java.util.HashMap;
import java.util.Map;

public class FleetConsumableInventoryManager implements FleetSpawnListener {

    public static final String MISSILE_INVENTORY_KEY = "$IndEvo_MissileInventory";

    public static Map<String, Float> FP_PER_MISSILE_MAP = new HashMap<String, Float>(){{
        put(ItemIds.CONSUMABLE_MISSILE_EXPLOSIVE, 100f);
        put(ItemIds.CONSUMABLE_MISSILE_INTERCEPT, 50f);
        put(ItemIds.CONSUMABLE_MISSILE_CONCUSSIVE, 70f);
        put(ItemIds.CONSUMABLE_MISSILE_SMOKE, 40f);
    }};

    public static void register(){
        Global.getSector().getListenerManager().addListener(new FleetConsumableInventoryManager(), true);
    }

    @Override
    public void reportFleetSpawnedToListener(CampaignFleetAPI fleet) {
        float fleetPoints = fleet.getFleetPoints();
        Map<String, Integer> missileInventory = new HashMap<>();

        for (Map.Entry<String, Float> entry : FP_PER_MISSILE_MAP.entrySet()){
            int toAdd = 0;
            if (fleetPoints > entry.getValue()) {
                toAdd = (int) Math.ceil(fleetPoints / entry.getValue());
                if (toAdd > 0) fleet.addAbility(ItemAbilityHelper.toggle(entry.getKey()));
            }

            missileInventory.put(entry.getKey(), toAdd);
        }

        fleet.getMemoryWithoutUpdate().set(MISSILE_INVENTORY_KEY, missileInventory);
        if (Misc.caresAboutPlayerTransponder(fleet)) fleet.getMemoryWithoutUpdate().set(MissileMemFlags.USES_MISSILES, true);
    }

    public static Map<String, Integer> getInventoryForFleet(CampaignFleetAPI fleet){
        Map<String, Integer> inventory = (Map<String, Integer>) fleet.getMemoryWithoutUpdate().get(MISSILE_INVENTORY_KEY);
        if (inventory == null) inventory = new HashMap<>();
        return inventory;
    }

    public static boolean hasItemOfType(String type, CampaignFleetAPI fleet){
        Map<String, Integer> inventory = getInventoryForFleet(fleet);
        return inventory.get(type) != null && inventory.get(type) > 0;
    }

    public static void adjustInventoryAmount(String type, CampaignFleetAPI fleet, int amt){
        Map<String, Integer> inventory = getInventoryForFleet(fleet);
        if (inventory.get(type) != null) inventory.put(type, inventory.get(type) + amt);
    }
}
