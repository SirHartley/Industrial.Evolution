package indevo.submarkets.script;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import indevo.ids.Ids;
import indevo.submarkets.DynamicSubmarket;
import indevo.submarkets.impl.SharedSubmarketPlugin;
import indevo.utils.helper.MiscIE;

//what the fuck is this trash script
//update 2025: what the fuck

public class SubMarketAddOrRemovePlugin implements EveryFrameScript {

    protected final MarketAPI market;
    protected final boolean remove;
    protected final String submarketID;

    protected boolean done = false;

    public SubMarketAddOrRemovePlugin(MarketAPI market, String submarketID, boolean removeSubmarket) {
        this.market = market;
        this.remove = removeSubmarket;
        this.submarketID = submarketID;

    }

    public void advance(float amount) {
        if (market.hasSubmarket(submarketID) && remove) {
            removeSubMarket();
        } else if (!market.hasSubmarket(submarketID) && !remove) {
            addSubMarket();
        }

        setDone();
    }

    public void addSubMarket() {
        market.addSubmarket(submarketID);
        setDone();
    }

    public void removeSubMarket() {
        String id;

        switch (submarketID) {
            case Ids.PET_STORE:
                id = Ids.PET_STORE;
                if (!market.hasIndustry(Ids.PET_STORE) && market.hasSubmarket(id)) {
                    removeSubmarket(id, false);
                    break;
                }
            case Ids.REPSTORAGE:
                id = Ids.REPSTORAGE;
                if (!market.hasIndustry(Ids.REPAIRDOCKS) && market.hasSubmarket(id)) {
                    removeSubmarket(id, true);
                    break;
                }
            case Ids.REQMARKET:
                id = Ids.REQMARKET;
                if (!market.hasIndustry(Ids.REQCENTER) && market.hasSubmarket(id)) {
                    removeSubmarket(id, false);
                    break;
                }
            case Ids.DECSTORAGE:
                id = Ids.DECSTORAGE;
                if (!market.hasIndustry(Ids.DECONSTRUCTOR) && market.hasSubmarket(id)) {
                    removeSubmarket(id, true);
                    setDone();
                    break;
                }

            case Ids.ENGSTORAGE:
                id = Ids.ENGSTORAGE;
                if (!market.hasIndustry(Ids.ENGHUB) && market.hasSubmarket(id)) {
                    removeSubmarket(id, true);
                    setDone();
                    break;
                }
            case Ids.SHAREDSTORAGE:
                id = Ids.SHAREDSTORAGE;

                boolean hasUser = !SharedSubmarketPlugin.getSharedStorageUsers(market).isEmpty();
                if (!hasUser && market.hasSubmarket(id)) {
                    removeSubmarket(id, true);
                    break;
                }
            default:
                break;
        }
    }

    private void removeSubmarket(String id, boolean transferCargo) {
        if (transferCargo) moveContentsToStorage(id);

        ((DynamicSubmarket) market.getSubmarket(id).getPlugin()).prepareForRemoval();
        market.removeSubmarket(id);
        setDone();
    }

    private void moveContentsToStorage(String fromId) {
        //have to move the cargo contents before removal as they would otherwise be lost

        if (MiscIE.getStorageCargo(market) != null) {
            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            SubmarketAPI fromSub = market.getSubmarket(fromId);

            CargoAPI fromCargo = fromSub.getCargo();
            CargoAPI toCargo = storage.getCargo();

            fromCargo.initMothballedShips("player");
            toCargo.initMothballedShips("player");

            if (!fromCargo.isEmpty() || !fromCargo.getMothballedShips().getMembersListCopy().isEmpty())
                ((StoragePlugin) storage.getPlugin()).setPlayerPaidToUnlock(true);

            //transfer ships
            for (FleetMemberAPI ship : fromCargo.getMothballedShips().getMembersListCopy()) {
                toCargo.getMothballedShips().addFleetMember(ship);
            }

            //transfer cargo
            toCargo.addAll(fromCargo);

            //clear it
            fromCargo.clear();
            fromCargo.getMothballedShips().clear();
        } else {
            Global.getLogger(SubMarketAddOrRemovePlugin.class).warn("Could not locate a storage submarket to transfer items to!");
        }
    }

    public void setDone() {
        done = true;
    }

    public boolean isDone() {
        return done;
    }

    public boolean runWhilePaused() {
        return true;
    }


}
