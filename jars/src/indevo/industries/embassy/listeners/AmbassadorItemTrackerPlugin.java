package indevo.industries.embassy.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.embassy.AmbassadorItemHelper;
import indevo.industries.embassy.scripts.AmbassadorMurderConsequences;
import indevo.items.specialitemdata.AmbassadorItemData;
import indevo.utils.helper.MiscIE;
import indevo.utils.timers.NewDayListener;

import static indevo.industries.embassy.listeners.AmbassadorPersonManager.adjustRelationship;
import static indevo.industries.embassy.listeners.AmbassadorPersonManager.displayMessage;

public class AmbassadorItemTrackerPlugin implements NewDayListener {

    private static final float NO_PENALTY = 0f;
    private static final float DELAY_1_REP_PENALTY = -0.1f;
    private static final float DELAY_2_REP_PENALTY = -0.15f;
    private static final float DELAY_3_REP_PENALTY = -0.2f;
    private static final float MURDER_REP_PENALTY = -0.3f;

    public final FactionAPI faction;
    protected final PersonAPI person;
    protected final AmbassadorItemData specialItem;
    private CargoAPI lastKnownLocation = null;
    private boolean isDone = false;

    private int daysPassed = 1;

    public AmbassadorItemTrackerPlugin(PersonAPI ambassadorPerson, AmbassadorItemData specialItem) {
        this.person = ambassadorPerson;
        this.specialItem = specialItem;
        this.faction = ambassadorPerson.getFaction();
    }

    @Override
    public void onNewDay() {
        daysPassed++;

        if (!isDone) if (!findAmbassadorItem(false)) checkForIllegalPresence();
        if (!isDone) checkForWarConsequences();
        if (!isDone) checkForTimeConsequences();
    }

    private boolean findAmbassadorItem(boolean returnHome) {
        //check all storage locations for the item, starting with the most likely
        AmbassadorItemData amb = specialItem;
        CargoAPI targetCargo;

        targetCargo = lastKnownLocation;
        if (checkCargo(targetCargo, amb, returnHome)) return updateKnownLocation(targetCargo, returnHome);

        targetCargo = Global.getSector().getPlayerFleet().getCargo();
        if (checkCargo(targetCargo, amb, returnHome)) return updateKnownLocation(targetCargo, returnHome);

        //has it been installed?
        if (getEmptyEmbassyMarket() != null) {
            setSuccessful();
            return true;
        }

        //oh god it's not in the cargo or installed, where the fuck did you put it
        //some random station maybe?
        for (SectorEntityToken pod : Global.getSector().getEntitiesWithTag(Tags.STATION)) {

            targetCargo = pod.getCargo();
            if (checkCargo(targetCargo, amb, returnHome)) return updateKnownLocation(targetCargo, returnHome);

            MarketAPI m = pod.getMarket();
            if (m != null && m.hasSubmarket(Submarkets.SUBMARKET_STORAGE)) {
                targetCargo = MiscIE.getStorageCargo(m);
                if (checkCargo(targetCargo, amb, returnHome)) return updateKnownLocation(targetCargo, returnHome);
            }
        }

        //is it in a market cargo?
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m != null && m.hasSubmarket(Submarkets.SUBMARKET_STORAGE)) {
                targetCargo = MiscIE.getStorageCargo(m);
                if (checkCargo(targetCargo, amb, returnHome)) return updateKnownLocation(targetCargo, returnHome);
            }
        }

        return false;
    }

    private boolean checkForIllegalPresence() {
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        AmbassadorItemData amb = specialItem;
        //past here is fuck up moment

        //they wouldn't sell it, right? It's not even worth anything.
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (SubmarketAPI subMarket : market.getSubmarketsCopy()) {
                if (!subMarket.getSpecId().equals(Submarkets.SUBMARKET_STORAGE) && checkCargo(subMarket.getCargo(), amb, true)) {
                    adjustRelationship(playerFaction, faction, MURDER_REP_PENALTY);
                    displayMessage("ambassadorSold", "ambassadorSoldFlavour", person, MURDER_REP_PENALTY);
                    Global.getSector().addScript(new AmbassadorMurderConsequences(person));
                    setFailed();
                    return true;
                }
            }
        }

        //Spaced in a cargo pod? Suffocation time
        //he was a good man, in this system
        for (SectorEntityToken pod : Global.getSector().getCurrentLocation().getAllEntities()) {
            if (checkCargo(pod.getCargo(), amb, true)) {
                adjustRelationship(playerFaction, faction, MURDER_REP_PENALTY);
                displayMessage("ambassadorSuffocated", "ambassadorSuffocatedFlavour", person, MURDER_REP_PENALTY);
                Global.getSector().addScript(new AmbassadorMurderConsequences(person));
                setFailed();
                return true;
            }
        }

        //he was a good man, in another system
        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (SectorEntityToken pod : location.getAllEntities()) {
                if (checkCargo(pod.getCargo(), amb, true)) {
                    adjustRelationship(playerFaction, faction, MURDER_REP_PENALTY);
                    displayMessage("ambassadorSuffocated", "ambassadorSuffocatedFlavour", person, MURDER_REP_PENALTY);
                    Global.getSector().addScript(new AmbassadorMurderConsequences(person));
                    setFailed();
                    return true;
                }
            }
        }

        //nowhere? throw a warning and remove the plugin.
        displayMessage("ambassadorLost", "ambassadorLostFlavour", person, NO_PENALTY);
        setLost();

        return false;
    }

    private void checkForWarConsequences() {
        if (AmbassadorPersonManager.getListOfIncativeFactions().contains(faction)) {
            displayMessage("ambassadorSuicide", "ambassadorSuicideFlavour", person, NO_PENALTY);
            findAmbassadorItem(true); //return home removes the person when home planet is no longer of the original faction
            return;
        }

        if (Global.getSector().getPlayerFaction().getRelationship(faction.getId()) <= -0.5F) {
            displayMessage("ambassadorChickenedOut", "ambassadorChickenedOutFlavour", person, NO_PENALTY);
            findAmbassadorItem(true);
            return;
        }

        String memKey = AmbassadorItemHelper.getFactionMemoryKey(faction);
        if (Global.getSector().getMemory().contains(memKey) && !Global.getSector().getMemory().getBoolean(memKey)) {
            displayMessage("ambassadorRecall", "ambassadorRecallFlavour", person, NO_PENALTY);
            findAmbassadorItem(true);
        }
    }

    private void checkForTimeConsequences() {
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();

        switch (daysPassed) {
            case 31:
                displayMessage("warning", null, person, NO_PENALTY);
                break;
            case 62:
                adjustRelationship(playerFaction, faction, DELAY_1_REP_PENALTY);
                displayMessage("delay1", null, person, DELAY_1_REP_PENALTY);
                break;
            case 93:
                adjustRelationship(playerFaction, faction, DELAY_2_REP_PENALTY);
                displayMessage("delay2", null, person, DELAY_2_REP_PENALTY);
                break;
            case 124:
                adjustRelationship(playerFaction, faction, DELAY_3_REP_PENALTY);
                displayMessage("delay3", null, person, DELAY_3_REP_PENALTY);
                findAmbassadorItem(true);
                break;
        }
    }

    public MarketAPI getEmptyEmbassyMarket() {
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();

        for (MarketAPI market : Misc.getFactionMarkets(playerFaction)) {
            if (market.hasIndustry(Ids.EMBASSY)
                    && market.getIndustry(Ids.EMBASSY).getSpecialItem() != null
                    && market.getIndustry(Ids.EMBASSY).getSpecialItem().equals(specialItem)
                    && AmbassadorPersonManager.getAmbassador(market) == null) {

                return market;
            }
        }
        return null;
    }

    private boolean checkCargo(CargoAPI cargo, AmbassadorItemData data, boolean removeWhenFound) {
        if (cargo == null) return false;

        if (cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, data) > 0) {
            if (removeWhenFound) cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1);
            return true;
        } else return false;
    }

    private boolean updateKnownLocation(CargoAPI cargo, boolean returnHome) {
        if (returnHome) setReturned();
        lastKnownLocation = cargo;
        return true;
    }

    public void setSuccessful() {
        MarketAPI m = getEmptyEmbassyMarket();
        if (m != null) {
            AmbassadorPersonManager.addAmbassadorToMarket(person, m);
            setDone();
        } else
            Global.getLogger(AmbassadorItemTrackerPlugin.class).error("AmbassadorItemTrackerPlugin setSuccessful null value on getEmptyEmbassy");
    }

    public void setReturned() {
        MarketAPI market = AmbassadorPersonManager.getOriginalMarket(person);

        if (market == null || market.getFaction() != faction) {
            setFailed();
            return;
        }

        if (AmbassadorPersonManager.getAmbassador(market) != null) {
            AmbassadorPersonManager.deleteAmbassador(market);
        }

        AmbassadorPersonManager.addAmbassadorToMarket(person, market);
        setDone();
    }

    public void setFailed() {
        Global.getSector().getImportantPeople().removePerson(person);
        setDone();
    }

    public void setLost() {
        Global.getSector().getListenerManager().addListener(new LostAmbassadorRemovalChecker(person, specialItem));
        Global.getSector().getImportantPeople().removePerson(person);
        setDone();
    }

    public void setDone() {
        isDone = true;
        Global.getSector().getListenerManager().removeListener(this);
    }
}
