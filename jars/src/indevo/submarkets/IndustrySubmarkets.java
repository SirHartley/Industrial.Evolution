package indevo.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import indevo.ids.Ids;
import indevo.industries.SharedSubmarketUserAPI;
import indevo.industries.warehouses.data.WarehouseConstants;
import indevo.utils.helper.MiscIE;
import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class IndustrySubmarkets {

    private static final Logger log = Global.getLogger(IndustrySubmarkets.class);

    private static final Map<String, Owner> OWNERS = new LinkedHashMap<>();

    static {
        OWNERS.put(Ids.REPSTORAGE, byId(Ids.REPAIRDOCKS, false, true));
        OWNERS.put(Ids.REQMARKET, byId(Ids.REQCENTER, false, false));
        OWNERS.put(Ids.DECSTORAGE, byId(Ids.DECONSTRUCTOR, false, true));
        OWNERS.put(Ids.ENGSTORAGE, byId(Ids.ENGHUB, false, true));
        OWNERS.put(Ids.PETMARKET, byId(Ids.PET_STORE, false, false));
        OWNERS.put(Ids.MUSEUM_SUBMARKET, byId(Ids.MUSEUM, true, true));
        OWNERS.put(Ids.SHAREDSTORAGE, new Owner(ind -> ind instanceof SharedSubmarketUserAPI, true, true));
        for (int i = 1; i <= WarehouseConstants.MAX_ADDITIONAL_SUBMARKETS; i++) {
            OWNERS.put(Ids.WAREHOUSE_SUBMARKET + "_" + i, byId(Ids.WAREHOUSES, true, true));
        }
    }

    private record Owner(Predicate<Industry> owns, boolean playerOnly, boolean keepCargo) {
    }

    private IndustrySubmarkets() {
    }

    public static void ensure(Industry ind, String submarketId) {
        Owner owner = owner(submarketId);
        if (!owner.owns().test(ind)) throw new IllegalStateException(ind.getId() + " does not own submarket " + submarketId);

        MarketAPI market = ind.getMarket();
        if (owner.playerOnly() && !market.isPlayerOwned()) remove(market, submarketId);
        else if (ind.isFunctional()) market.addSubmarket(submarketId);
    }

    public static void release(Industry leaving) {
        MarketAPI market = leaving.getMarket();

        for (Map.Entry<String, Owner> entry : OWNERS.entrySet()) {
            Owner owner = entry.getValue();
            if (!owner.owns().test(leaving) || hasOwner(market, owner, leaving)) continue;

            remove(market, entry.getKey());
        }
    }

    public static boolean remove(MarketAPI market, String submarketId) {
        SubmarketAPI submarket = market.getSubmarket(submarketId);
        if (submarket == null) return true;
        if (owner(submarketId).keepCargo() && !moveCargoToStorage(market, submarket)) return false;

        market.removeSubmarket(submarketId);
        return true;
    }

    public static void removeOrphans() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                Owner owner = OWNERS.get(submarket.getSpecId());
                if (owner != null && !hasOwner(market, owner, null)) remove(market, submarket.getSpecId());
            }
        }
    }

    private static boolean hasOwner(MarketAPI market, Owner owner, Industry except) {
        for (Industry ind : market.getIndustries()) {
            if (ind != except && owner.owns().test(ind)) return true;
        }

        return false;
    }

    private static boolean moveCargoToStorage(MarketAPI market, SubmarketAPI from) {
        SubmarketAPI storage = MiscIE.getStorage(market);
        if (storage == null) {
            log.warn("No storage on " + market.getName() + ", keeping " + from.getSpecId() + " so its cargo is not lost");
            return false;
        }

        CargoAPI fromCargo = from.getCargo();
        CargoAPI toCargo = storage.getCargo();
        fromCargo.initMothballedShips(Factions.PLAYER);
        toCargo.initMothballedShips(Factions.PLAYER);

        boolean hasContent = !fromCargo.isEmpty() || !fromCargo.getMothballedShips().getMembersListCopy().isEmpty();
        if (hasContent && storage.getPlugin() instanceof StoragePlugin storagePlugin) storagePlugin.setPlayerPaidToUnlock(true);

        for (FleetMemberAPI ship : fromCargo.getMothballedShips().getMembersListCopy()) {
            toCargo.getMothballedShips().addFleetMember(ship);
        }

        toCargo.addAll(fromCargo);
        fromCargo.clear();
        fromCargo.getMothballedShips().clear();
        return true;
    }

    private static Owner owner(String submarketId) {
        Owner owner = OWNERS.get(submarketId);
        if (owner == null) throw new IllegalArgumentException("No owner registered for submarket " + submarketId);
        return owner;
    }

    private static Owner byId(String industryId, boolean playerOnly, boolean keepCargo) {
        return new Owner(ind -> industryId.equals(ind.getId()), playerOnly, keepCargo);
    }
}
