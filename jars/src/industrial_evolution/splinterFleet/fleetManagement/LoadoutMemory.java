package industrial_evolution.splinterFleet.fleetManagement;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

public class LoadoutMemory {
    public static final String LOADOUT_STORE_MEMORY_KEY = "$SplinterFleetLoadoutStoreMemoryReference";

    public static Map<Integer, Loadout> getLoadoutMap() {
        Map<Integer, Loadout> loadoutMap;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(LOADOUT_STORE_MEMORY_KEY))
            loadoutMap = (Map<Integer, Loadout>) mem.get(LOADOUT_STORE_MEMORY_KEY);
        else {
            loadoutMap = new HashMap<>();
            for (int i : Arrays.asList(1, 2, 3))
                loadoutMap.put(i, new Loadout()); //initial setup to make sure it's never empty

            mem.set(LOADOUT_STORE_MEMORY_KEY, loadoutMap);
        }

        return loadoutMap;
    }

    public static boolean hasLoadout(int num) {
        return !getLoadoutMap().get(num).shipVariantList.isEmpty();
    }

    public static Loadout getLoadout(int num) {
        return getLoadoutMap().get(num);
    }

    public static void setLoadout(int num, Loadout loadout) {
        Map<Integer, Loadout> loadoutMap = getLoadoutMap();
        loadoutMap.put(num, loadout);
    }

    public static class Loadout {
        public String id = Misc.genUID();
        public List<ShipVariantAPI> shipVariantList = new ArrayList<>();
        public Behaviour.FleetBehaviour behaviour = Behaviour.FleetBehaviour.STAY;
        public CargoAPI targetCargo = Global.getFactory().createCargo(true);
        public String transportTargetMarket = null;

        public Loadout() {
        }

        public Loadout(List<ShipVariantAPI> shipVariantAPIS, Behaviour.FleetBehaviour behaviour, CargoAPI cargo) {
            this.shipVariantList = shipVariantAPIS;
            this.behaviour = behaviour;
            this.targetCargo = cargo;
        }

        public void addToMembersList(FleetMemberAPI member) {
            shipVariantList.add(member.getVariant());
        }

        public void removeMember(FleetMemberAPI member) {
            shipVariantList.remove(member.getVariant());
        }

        public void setTargetCargoCopy(CargoAPI cargoToCopy) {
            targetCargo = cargoToCopy.createCopy();
        }
    }
}
