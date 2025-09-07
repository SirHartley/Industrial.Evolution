package indevo.industries.warehouses.plugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LinkedStorageManager {
    public static final String MEMORY_KEY = "$IndEvo_LinkedStoragePluginInstance";

    private Map<String, CargoAPI> sharedCargoMap = new HashMap<>();

    public static LinkedStorageManager getInstance(){
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        LinkedStorageManager manager;

        if (mem.contains(MEMORY_KEY)) manager = (LinkedStorageManager) mem.get(MEMORY_KEY);
        else {
            manager = new LinkedStorageManager();
            mem.set(MEMORY_KEY, manager);
        }

        return manager;
    }

    public CargoAPI getSharedCargoForSubmarket(SubmarketAPI submarket){
        String id = submarket.getSpecId();
        CargoAPI cargo;

        if (sharedCargoMap.containsKey(id)) cargo = sharedCargoMap.get(id);
        else {
            cargo = Global.getFactory().createCargo(true);
            sharedCargoMap.put(id, cargo);
        }

        cargo.initMothballedShips(Factions.PLAYER);

        return cargo;
    }

    public void convertToSharedCargo(SubmarketAPI submarket){
        if (isSharedCargo(submarket)) return;

        CargoAPI currentCargo = submarket.getCargo();
        CargoAPI sharedCargo = getSharedCargoForSubmarket(submarket);

        sharedCargo.addAll(currentCargo);
        currentCargo.clear();
        ((BaseSubmarketPlugin) submarket.getPlugin()).setCargo(sharedCargo);
    }

    public void convertToLocalCargo(SubmarketAPI submarket){
        if (!isSharedCargo(submarket)) return;

        CargoAPI sharedCargo = getSharedCargoForSubmarket(submarket);
        CargoAPI localCargo = Global.getFactory().createCargo(true);
        localCargo.initMothballedShips(Factions.PLAYER);

        //if this is the last shared submarket, it gets all the cargo and the one in memory is cleared
        if (getAllPlayerOwnedSubmarketsOfType(submarket, true, true).isEmpty()){
            localCargo.addAll(sharedCargo);
            sharedCargo.clear();
        }

        ((BaseSubmarketPlugin) submarket.getPlugin()).setCargo(localCargo);
    }

    public List<SubmarketAPI> getAllPlayerOwnedSubmarketsOfType(SubmarketAPI submarket, boolean sharedOnly, boolean ignorePassedInstance){
        List<SubmarketAPI> submarketList = new ArrayList<>();

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) if (m.isPlayerOwned() && m.hasSubmarket(submarket.getSpecId())) {
            SubmarketAPI sub = m.getSubmarket(submarket.getSpecId());

            if (ignorePassedInstance && sub == submarket) continue;

            if (sharedOnly && isSharedCargo(sub)) submarketList.add(sub);
            else if (!sharedOnly) submarketList.add(sub);
        }

        return submarketList;
    }

    public boolean isSharedCargo(SubmarketAPI submarket){
        CargoAPI currentCargo = submarket.getCargo();
        CargoAPI sharedCargo = getSharedCargoForSubmarket(submarket);
        return currentCargo == sharedCargo;
    }
}
