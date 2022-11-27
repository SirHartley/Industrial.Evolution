package indevo.utils.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import indevo.submarkets.IndEvo_DynamicSubmarket;
import indevo.submarkets.IndEvo_SharedStoragePlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;

public class IndEvo_subMarketAddOrRemovePlugin implements EveryFrameScript {

    protected final MarketAPI market;
    protected final boolean remove;
    protected final String submarketID;

    protected boolean done = false;

    public IndEvo_subMarketAddOrRemovePlugin(MarketAPI market, String submarketID, boolean removeSubmarket) {
        this.market = market;
        this.remove = removeSubmarket;
        this.submarketID = submarketID;

    }

    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_subMarketAddOrRemovePlugin.class).info(Text);
        }
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
        debugMessage("adding " + submarketID + " to " + market.getName());

        if (market != null) {
            market.addSubmarket(submarketID);
        }
        setDone();
    }

    public void removeSubMarket() {
        String id;

        switch (submarketID) {
            case IndEvo_ids.REPSTORAGE:
                id = IndEvo_ids.REPSTORAGE;
                if (!market.hasIndustry(IndEvo_ids.REPAIRDOCKS) && market.hasSubmarket(id)) {
                    removeSubmarket(id);
                    break;
                }
            case IndEvo_ids.REQMARKET:
                id = IndEvo_ids.REQMARKET;
                if (!market.hasIndustry(IndEvo_ids.REQCENTER) && market.hasSubmarket(id)) {
                    removeSubmarket(id);
                    break;
                }
            case IndEvo_ids.DECSTORAGE:
                id = IndEvo_ids.DECSTORAGE;
                if (!market.hasIndustry(IndEvo_ids.DECONSTRUCTOR) && market.hasSubmarket(id)) {
                    removeSubmarket(id);
                    setDone();
                    break;
                }

            case IndEvo_ids.ENGSTORAGE:
                id = IndEvo_ids.ENGSTORAGE;
                if (!market.hasIndustry(IndEvo_ids.ENGHUB) && market.hasSubmarket(id)) {
                    removeSubmarket(id);
                    setDone();
                    break;
                }
            case IndEvo_ids.SHAREDSTORAGE:
                id = IndEvo_ids.SHAREDSTORAGE;

                boolean hasUser = !IndEvo_SharedStoragePlugin.getSharedStorageUsers(market).isEmpty();
                if (!hasUser && market.hasSubmarket(id)) {
                    removeSubmarket(id);
                    break;
                }
            default:
                break;
        }
    }

    private void removeSubmarket(String id) {
        moveContentsToStorage(id);

        ((IndEvo_DynamicSubmarket) market.getSubmarket(id).getPlugin()).prepareForRemoval();
        market.removeSubmarket(id);
        setDone();
    }

    private void moveContentsToStorage(String fromId) {
        //have to move the cargo contents before removal as they would otherwise be lost

        if (Misc.getStorageCargo(market) != null) {
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
            Global.getLogger(IndEvo_subMarketAddOrRemovePlugin.class).warn("Could not locate a storage submarket to transfer items to!");
        }
    }

    public void setDone() {
        debugMessage("setDone");
        done = true;
    }

    public boolean isDone() {
        return done;
    }

    public boolean runWhilePaused() {
        return true;
    }


}
