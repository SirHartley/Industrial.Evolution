package indevo.industries.museum.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.industries.EngineeringHub;
import indevo.industries.museum.submarket.MuseumSubmarketData;
import indevo.submarkets.RemovablePlayerSubmarketPluginAPI;
import indevo.utils.helper.Settings;
import sound.F;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Museum extends BaseIndustry {

    public static final int MAX_ADDITIONAL_SUBMARKETS = 5;
    public static final float MAX_ADDITIONAL_CREDITS = 2000f;
    public static final float MIN_ADDITIONAL_CREDITS = 50f;
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

    private Map<ShipAPI.HullSize, Pair<Float, Float>> hullSizeValueMap = new HashMap<>(); //a = maxShipValue, b = average for hull size

    @Override
    public void apply() {
        super.apply(true);

        //update ship hull values
        List<ShipHullSpecAPI> specList = Global.getSettings().getAllShipHullSpecs();

        for (ShipAPI.HullSize size : new ArrayList<>(Arrays.asList(ShipAPI.HullSize.FRIGATE, ShipAPI.HullSize.DESTROYER, ShipAPI.HullSize.CRUISER, ShipAPI.HullSize.CAPITAL_SHIP))){
            float total = 0f;
            int specNum = 0;
            Pair<Float, Float> valuePair = new Pair<>(); //a = maxShipValue, b = average for hull size

            for (ShipHullSpecAPI spec : specList){
                if (spec.getHullSize() != size) continue;
                specNum++;

                float value = spec.getBaseValue();
                if (value > valuePair.one) valuePair.one = value;
                total += value;
            }

            valuePair.two = total / specNum;
            hullSizeValueMap.put(size, valuePair);
        }
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

    //todo ORDER THE LIST FIRST!

    public void addShipStorageValueTooltip(CargoAPI cargo, TooltipMakerAPI tooltip, boolean expanded) {
        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 5.0F;
        int maxItems = expanded ? 100 : 10;

        tooltip.addSectionHeading("Exhibition entries", color, dark, Alignment.MID, opad);

        tooltip.addPara("This chart shows the %s of each ship in storage. Only one of each unique ship counts.", 10f, Misc.getHighlightColor(), "contribution");

        //faction for colours, height of each row, [column 1 header, column 1 width, column 2 header, column 2 width, column 3...)
        tooltip.beginTable(marketFaction, 20f, "Ship Hull", 190f, "Public Interest", 100f, "income / month", 100f);

        Map<ShipHullSpecAPI, Float> specValueMap = new LinkedHashMap<>(); //purge duplicates
        for (FleetMemberAPI member : cargo.getMothballedShips().getMembersListCopy()) {
            specValueMap.put(member.getHullSpec(), getValueForShip(member));
        }

        int i = 0;
        for (Map.Entry<ShipHullSpecAPI, Float> e : specValueMap.entrySet()) {
            i++;

            //define what you want in the row
            float value = e.getValue();
            String designation = e.getKey().getHullName();

            String interest = value < 0.05 ? "none"
                    : value < 0.2 ? "low"
                    : value < 0.4 ? "mediocre"
                    : value < 0.6 ? "high"
                    : value < 0.8 ? "very high"
                    : "extreme";

            String credits = Misc.getDGSCredits(Math.max(MAX_ADDITIONAL_CREDITS * value, MIN_ADDITIONAL_CREDITS));

            //add the row
            tooltip.addRow(designation, interest, credits);

            if(i > maxItems) break;
        }

        int itemsInList = cargo.getMothballedShips().getMembersListCopy().size() - 10;

        //add the table to the tooltip
        tooltip.addTable("No ships in storage.", Math.max(itemsInList, 0), opad);
    }

    /**
     *
     * @param member
     * @return value from 0 to 1 relative to the ships value above the average
     */
    public float getValueForShip(FleetMemberAPI member){
        Pair<Float, Float> valuePair = hullSizeValueMap.get(member.getHullSpec().getHullSize()); //one = maxShipValue, two = average for hull size

        float value = member.getBaseValue();
        float valueAboveAverage = value - valuePair.two;
        float maxAboveAverage = valuePair.one - valuePair.two;

        return valueAboveAverage > 0 ? valueAboveAverage / maxAboveAverage : 0f;
    }

    //submarket stuff

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
