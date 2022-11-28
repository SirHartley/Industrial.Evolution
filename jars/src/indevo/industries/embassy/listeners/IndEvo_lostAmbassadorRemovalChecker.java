package indevo.industries.embassy.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import indevo.ids.Ids;
import indevo.industries.embassy.industry.IndEvo_embassy;
import indevo.utils.timers.NewDayListener;
import com.fs.starfarer.api.util.Misc;

public class IndEvo_lostAmbassadorRemovalChecker implements NewDayListener {

    public final FactionAPI faction;
    protected final PersonAPI person;
    protected final SpecialItemData specialItem;
    protected boolean done = false;

    public IndEvo_lostAmbassadorRemovalChecker(PersonAPI ambassadorPerson, SpecialItemData specialItem) {
        this.person = ambassadorPerson;
        this.specialItem = specialItem;
        this.faction = ambassadorPerson.getFaction();
    }

    @Override
    public void onNewDay() {
        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (market.hasIndustry(Ids.EMBASSY)) {
                IndEvo_embassy emb = (IndEvo_embassy) market.getIndustry(Ids.EMBASSY);
                PersonAPI pers = emb.getAmbassadorItemData() != null ? emb.getAmbassadorItemData().getPerson() : null;

                if (pers != null && pers == person) {
                    emb.setSpecialItem(null);
                    IndEvo_ambassadorPersonManager.removeAmbassadorFromMarket(market);
                    IndEvo_ambassadorPersonManager.displayMessage("ambassadorRetrieved", "ambassadorRetrievedFlavour", person, 0f);
                    setDone();
                    break;
                }
            }
        }
    }

    public void setDone() {
        Global.getSector().getListenerManager().removeListener(this);
    }
}
