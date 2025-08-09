package indevo.industries.museum.industry;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.industries.museum.submarket.MuseumSubmarketData;
import indevo.submarkets.RemovablePlayerSubmarketPluginAPI;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Museum extends BaseIndustry {

    public static final int MAX_ADDITIONAL_SUBMARKETS = 5;
    public static final float MAX_ADDITIONAL_CREDITS = 1950;
    public static final float MIN_ADDITIONAL_CREDITS = 50f;
    //Museum
    // X - store rare ships
    // X - adds small flat income dep. on ship value (calculate diff from average value of a ship in its class)
    // - organize parade fleets that temp. increase the stability and immigration of the planet they orbit
    // X - add up to 5 customizable storage spaces to your colony
    //
    // - Gamma: Increase income to 3000 max
    // - Beta: Increase stability and immigration of local planet by x per 10k?
    // - Alpha: second parade fleet (if sufficient ships)

    private List<MuseumSubmarketData> submarkets = new ArrayList<>();
    private SubmarketAPI mainMuseumSubmarket;

    boolean expanded = false; //tooltip

    private Map<ShipAPI.HullSize, Pair<Float, Float>> hullSizeValueMap = new HashMap<>(); //a = maxShipValue, b = average for hull size,
    private List<CampaignFleetAPI> paradeFleets = new ArrayList<>();

    @Override
    public void apply() {
        super.apply(true);

        if (!market.isPlayerOwned()) return;

        //update ship hull values
        List<ShipHullSpecAPI> specList = MiscIE.getAllLearnableShipHulls(); //only learnable cause getting all fucks up the value statistics something fierce

        for (ShipAPI.HullSize size : new ArrayList<>(Arrays.asList(ShipAPI.HullSize.FRIGATE, ShipAPI.HullSize.DESTROYER, ShipAPI.HullSize.CRUISER, ShipAPI.HullSize.CAPITAL_SHIP))){
            float total = 0f;
            int specNum = 0;
            Pair<Float, Float> valuePair = new Pair<>(0f,0f); //a = maxShipValue, b = average for hull size

            for (ShipHullSpecAPI spec : specList){
                if (spec.getHullSize() != size || spec.getBaseValue() > 2000000) continue; //any ship worth more than 2 mil gets cut to avoid modded brainrot bloating the statistics

                specNum++;

                float value = spec.getBaseValue();
                if (value > valuePair.one) valuePair.one = value;
                total += value;
            }

            valuePair.two = total / specNum;
            hullSizeValueMap.put(size, valuePair);
        }

        income.modifyFlat(getModId(), getTotalShipValue(), "Exhibition Income");
    }

    @Override
    public void unapply() {
        super.unapply();

        income.unmodify(getModId());
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

    public void addParadeFleetTooltip(CargoAPI cargo, TooltipMakerAPI tooltip){
        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 10.0F;
        float spad = 5f;

        tooltip.addSectionHeading("Parade Fleets", color, dark, Alignment.MID, spad);

        tooltip.addPara("There are currently %s.", opad, Misc.getHighlightColor(), "no active parade fleets.");
    }

    public void addShipStorageValueTooltip(CargoAPI cargo, TooltipMakerAPI tooltip, boolean expanded) {
        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 5.0F;
        int maxItems = expanded ? 50 : 7;

        tooltip.addSectionHeading("Exhibition entries", color, dark, Alignment.MID, opad);

        tooltip.addPara("This chart shows the %s of each ship in storage. Duplicates are not exhibited.", 10f, Misc.getHighlightColor(), "contribution");

        //faction for colours, height of each row, [column 1 header, column 1 width, column 2 header, column 2 width, column 3...)
        tooltip.beginTable(marketFaction, 20f, "Ship Type", 180f, "Interest", 110f, "Income", 100f);

        //sort highest to lowest
        Map<ShipHullSpecAPI, Float> specValueMap = new LinkedHashMap<>();
        cargo.initMothballedShips(Factions.PLAYER);
        List<FleetMemberAPI> members = cargo.getMothballedShips().getMembersListCopy();
        members.sort(Comparator.comparingDouble(this::getValueForShip).reversed());

        for (FleetMemberAPI member : members) {
            specValueMap.put(member.getHullSpec(), getValueForShip(member));
        }

        int i = 0;
        for (Map.Entry<ShipHullSpecAPI, Float> e : specValueMap.entrySet()) {
            i++;

            //define what you want in the row
            float value = e.getValue();
            String designation = e.getKey().getHullName();

            String interest = value < 0.3 ? "low"
                    : value < 0.6 ? "average"
                    : value < 0.8 ? "high"
                    : "extreme";

            String credits = Misc.getDGSCredits(getCreditsForValue(value));

            //add the row
            tooltip.addRow(designation, interest, credits);

            if(i > maxItems) break;
        }

        int itemsInList = specValueMap.size() - maxItems - 1;

        //add the table to the tooltip
        tooltip.addTable("No ships in storage.", Math.max(itemsInList, 0), opad);
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        if (market.isPlayerOwned()) {
            addParadeFleetTooltip(mainMuseumSubmarket.getCargo(), tooltip);
            addShipStorageValueTooltip(mainMuseumSubmarket.getCargo(), tooltip, expanded);
        }
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
        float val = MathUtils.clamp(valueAboveAverage / maxAboveAverage, 0, 1);

        ModPlugin.log("Getting Museum Ship Value: " + member.getHullSpec().getHullId() + "\ncost: " + member.getHullSpec().getBaseValue() + "\nmax / avg " + valuePair.one + " / " + valuePair.two + "\nvalue: " + val);

        return val;
    }

    @Override
    public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        this.expanded = expanded;
        super.createTooltip(mode, tooltip, expanded);
    }

    @Override
    public boolean isTooltipExpandable() {
        return mainMuseumSubmarket != null && mainMuseumSubmarket.getCargo().getMothballedShips().getMembersListCopy().size() > 10;
    }

    public float getCreditsForValue(float value){
        return MIN_ADDITIONAL_CREDITS + MAX_ADDITIONAL_CREDITS * value;
    }

    public float getTotalShipValue(){
        float val = 0f;

        CargoAPI cargo = mainMuseumSubmarket.getCargo();
        cargo.initMothballedShips(Factions.PLAYER);

        //dirty duplicate removal
        Map<ShipHullSpecAPI, Float> specValueMap = new LinkedHashMap<>();
        for (FleetMemberAPI member : cargo.getMothballedShips().getMembersListCopy()) specValueMap.put(member.getHullSpec(), getValueForShip(member));
        for (Float value : specValueMap.values()) val += value;

        return val;
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
