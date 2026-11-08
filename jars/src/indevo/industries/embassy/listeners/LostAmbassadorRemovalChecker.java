package indevo.industries.embassy.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.utils.timers.NewDayListener;

public class LostAmbassadorRemovalChecker implements NewDayListener {

    public final FactionAPI faction;
    protected final PersonAPI person;
    protected final SpecialItemData specialItem;
    protected boolean done = false;

    public LostAmbassadorRemovalChecker(PersonAPI ambassadorPerson, SpecialItemData specialItem) {
        this.person = ambassadorPerson;
        this.specialItem = specialItem;
        this.faction = ambassadorPerson.getFaction();
    }

    @Override
    public void onNewDay() {
        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (market.hasIndustry(Ids.EMBASSY) && specialItem.equals(market.getIndustry(Ids.EMBASSY).getSpecialItem())) {
                market.getIndustry(Ids.EMBASSY).setSpecialItem(null);
                AmbassadorPersonManager.removeAmbassadorFromMarket(market);
                AmbassadorPersonManager.displayMessage("$personName has been arrested and returned to $factionName space for questioning.", "$HeOrShe is suspected to be compromised.", person, 0f);
                Global.getSector().getImportantPeople().removePerson(person);
                setDone();
                break;
            }
        }
    }

    public void setDone() {
        Global.getSector().getListenerManager().removeListener(this);
    }
}
