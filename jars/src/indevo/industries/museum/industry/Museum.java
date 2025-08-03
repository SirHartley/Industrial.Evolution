package indevo.industries.museum.industry;

import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.ids.Ids;
import indevo.industries.museum.submarket.MuseumSubmarketData;
import indevo.submarkets.RemovablePlayerSubmarketPluginAPI;

import java.util.ArrayList;
import java.util.List;

public class Museum extends BaseIndustry {

    public static final int MAX_ADDITIONAL_SUBMARKETS = 5;
    //Museum
    // X - store rare ships
    // - adds small flat income dep. on ship value (calculate diff from average value of a ship in its class)
    // - once value limit reached, add +1 stab and some immigration
    // - organize parade fleets that temp. increase the stability and immigration of the planet they orbit
    // X - add up to 5 customizable storage spaces to your colony
    //
    // - Gamma: Increase income per value share
    // - Beta: Increase stability and immigration of local planet
    // - Alpha: second parade fleet (if sufficient ships)

    private List<MuseumSubmarketData> submarkets = new ArrayList<>();
    private SubmarketAPI mainMuseumSubmarket;

    @Override
    public void apply() {
        super.apply(true);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        //Color color = new Color(199, 10, 63,255);

        if (isFunctional() && mainMuseumSubmarket == null && market.isPlayerOwned()) {
            market.addSubmarket(Ids.MUSEUM_SUBMARKET);
            mainMuseumSubmarket = market.getSubmarket(Ids.MUSEUM_SUBMARKET);
        } else if (!market.isPlayerOwned() && mainMuseumSubmarket != null) {
            notifyBeingRemoved(null, false);
        }
    }

    public SubmarketAPI addSubmarket(MuseumSubmarketData data){
        submarkets.add(data); //must be before addition, submarket plugin checks for data on init
        market.addSubmarket(data.submarketID);

        return market.getSubmarket(data.submarketID);
    }

    public void removeSubmarket(MuseumSubmarketData data){
        ((RemovablePlayerSubmarketPluginAPI) market.getSubmarket(data.submarketID).getPlugin()).notifyBeingRemoved();
        market.removeSubmarket(data.submarketID);
        submarkets.remove(data);
    }

    public void removeSubmarkets(){
        if (mainMuseumSubmarket != null) {
            ((RemovablePlayerSubmarketPluginAPI) mainMuseumSubmarket.getPlugin()).notifyBeingRemoved();
            market.removeSubmarket(Ids.MUSEUM_SUBMARKET);
        }

        for (MuseumSubmarketData data : new ArrayList<>(submarkets)){
            ((RemovablePlayerSubmarketPluginAPI) market.getSubmarket(data.submarketID).getPlugin()).notifyBeingRemoved();
            market.removeSubmarket(data.submarketID);
            submarkets.remove(data);
        }
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        removeSubmarkets();
    }

    public List<MuseumSubmarketData> getSubmarkets() {
        return submarkets;
    }

    public MuseumSubmarketData getData(SubmarketPlugin forPlugin){
        for (MuseumSubmarketData data : submarkets) if (data.submarketID.equals(forPlugin.getSubmarket().getSpecId())) return data;
        return null;
    }
}
