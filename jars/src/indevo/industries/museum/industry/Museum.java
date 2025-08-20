package indevo.industries.museum.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.industries.museum.data.ParadeFleetData;
import indevo.industries.museum.data.MuseumSubmarketData;
import indevo.submarkets.RemovablePlayerSubmarketPluginAPI;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.StringHelper;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Museum extends BaseIndustry implements EconomyTickListener, MarketImmigrationModifier {

    //Museum
    // X - store rare ships
    // X - adds small flat income dep. on ship value (calculate diff from average value of a ship in its class)
    // X - organize parade fleets that temp. increase the stability and immigration of the planet they orbit
    // X - add up to 5 customizable storage spaces to your colony
    //
    // X - Gamma: Increase income by 30%
    // X - Beta: Increase stability and immigration of local planet by x per 10k?
    // X - Alpha: second parade fleet (if sufficient ships)

    public static final int MAX_ADDITIONAL_SUBMARKETS = 5;
    public static final float MAX_ADDITIONAL_CREDITS = 1950;
    public static final float MIN_ADDITIONAL_CREDITS = 50f;
    public static final float GAMMA_CORE_INCOME_MULT = 1.3f;
    public static final float DEFAULT_INCOME_MULT = 1f;

    public static final int DEFAULT_MAX_PARADES = 1;
    public static final int ALPHA_CORE_EXTRA_PARADES = 1;
    public static final int DEFAULT_DAYS = 31;
    public static final int DEFAULT_MEMBERS_MAX = 10;
    public static final int TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION = 20;

    public static final int BETA_CORE_INCOME_PER_STABILITY = 20000;
    public static final int BETA_CORE_INCOME_PER_POINT_IMMIGRATION = 3000;

    public static final String ON_PARADE_TAG = "indEvo_on_parade";
    public static final String PARADE_FLEET_NAMES = "data/strings/parade_fleet_names.csv";
    public static final int PARADE_FLEET_IMMIGRATION_BONUS = 5;
    public static final int PARADE_FLEET_STABILITY_BONUS = 1;

    private List<MuseumSubmarketData> archiveSubMarkets = new ArrayList<>();
    private SubmarketAPI submarket;
    private Map<ShipAPI.HullSize, Pair<Float, Float>> hullSizeValueMap = new HashMap<>(); //a = maxShipValue, b = average for hull size,
    private List<ParadeFleetData> paradeFleetData = new ArrayList<>();
    private Random random = new Random();

    private boolean expanded = false; //tooltip

    private int maxParades = DEFAULT_MAX_PARADES;
    private boolean randomParades = true;

    private float incomeMult = DEFAULT_INCOME_MULT;

    @Override
    public void apply() {
        super.apply(true);

        if (!market.isPlayerOwned()) return;
        updateShipValueStatistics();

        //for parade handling - not needed in npc mode
        Global.getSector().getListenerManager().addListener(this);
        market.addTransientImmigrationModifier(this);

        //income
        income.modifyFlat(getModId(), getTotalShipValue(), "Exhibition Income");
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
        market.removeTransientImmigrationModifier(this);
        income.unmodify(getModId());
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (!isFunctional() || submarket == null || iterIndex != 5) return;

        //activateAndSpawn parade if empty spot

        //clear the expired ones
        int activeParades = 0;

        for (ParadeFleetData data : new ArrayList<>(paradeFleetData)) {
            if (!data.isActive() && data.isExpired()) paradeFleetData.remove(data);
            else if (data.isActive()) activeParades++;
        }

        if (activeParades < maxParades){
            //check if we have a repeating one - there is no expired data on the list at this point
            for (ParadeFleetData data : paradeFleetData) if (!data.isActive() && data.isRepeating()) spawnSpecificParade(data);

            if (!randomParades) return;

            //still not filled? Spawn random parades until list is full
            for (int i = 0; i < maxParades; i++){
                ModPlugin.log("Spawning random parade");
                spawnRandomParade();
            }
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        //Color color = new Color(199, 10, 63,255); //museum submarket colour

        if (isFunctional() && submarket == null && market.isPlayerOwned()) {
            market.addSubmarket(Ids.MUSEUM_SUBMARKET);
            submarket = market.getSubmarket(Ids.MUSEUM_SUBMARKET);
        } else if (!market.isPlayerOwned() && submarket != null) {
            notifyBeingRemoved(null, false);
        }
    }

    public List<CampaignFleetAPI> getParadeFleets() {
        List<CampaignFleetAPI> fleets = new ArrayList<>();

        for (ParadeFleetData data : paradeFleetData) if (data.isActive()) fleets.add(data.getActiveFleet());
        return fleets;
    }

    public void spawnRandomParade(){
        WeightedRandomPicker<String> namePicker = new WeightedRandomPicker<>(random);
        namePicker.addAll(MiscIE.getCSVSetFromMemory(PARADE_FLEET_NAMES));

        String name = namePicker.pick();

        //pick X of the top 20 members that are not currently out on parade
        CargoAPI cargo = submarket.getCargo();
        cargo.initMothballedShips(Factions.PLAYER);
        List<FleetMemberAPI> members = cargo.getMothballedShips().getMembersListCopy();
        members.sort(Comparator.comparingDouble(this::getValueForShip).reversed());

        WeightedRandomPicker<String> fleetMemberPicker = new WeightedRandomPicker<>(random);
        int count = 0;

        for (FleetMemberAPI member : members){
            if (member.getVariant().hasTag(ON_PARADE_TAG)) continue;
            if (count >= TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION) break; //we only get the top X so we don't go on parade with a bunch of trash

            fleetMemberPicker.add(member.getId());
            count++;
        }

        if (fleetMemberPicker.isEmpty()) {
            ModPlugin.log("Aborting parade creation, no fleet members");
            return; //no members, no parade
        }

        List<String> paradeMembers = new ArrayList<>();
        for (int i = 0; i <= DEFAULT_MEMBERS_MAX; i++) {
            if (fleetMemberPicker.isEmpty()) break;
            paradeMembers.add(fleetMemberPicker.pickAndRemove());
        }

        //market target
        WeightedRandomPicker<String> marketPicker = new WeightedRandomPicker<>(random);
        for (MarketAPI m : MiscIE.getMarketsInLocation(market.getContainingLocation(), market.getFactionId())) {
            if (m.hasTag(ON_PARADE_TAG)) continue; //added on spawn, removed with the condition on departure or death - fleetAI

            marketPicker.add(m.getId());
        }

        if (marketPicker.isEmpty()) {
            ModPlugin.log("Aborting parade creation, no target market");
            return; //no market, no parade...
        }

        String targetMarketId = marketPicker.pick();

        ParadeFleetData data = new ParadeFleetData(this, name, targetMarketId,paradeMembers,DEFAULT_DAYS, false);
        paradeFleetData.add(data);
        data.activateAndSpawn();
    }

    public void spawnSpecificParade(ParadeFleetData data){
        CargoAPI cargo = submarket.getCargo();
        cargo.initMothballedShips(Factions.PLAYER);
        List<FleetMemberAPI> members = cargo.getMothballedShips().getMembersListCopy();

        int num = 0;
        for (String memberId : data.fleetMemberIdList) for (FleetMemberAPI m : members) if (m.getId().equals(memberId) && !m.getVariant().hasTag(ON_PARADE_TAG)) num++;

        if (num <= 0) {
            ModPlugin.log("Aborting custom parade creation, no fleet members");
            return; //no members, no parade
        }

        MarketAPI targetMarket = Global.getSector().getEconomy().getMarket(data.targetMarketId);
        if (targetMarket == null) {
            ModPlugin.log("Aborting custom parade creation, no target market");
            return;
        }

        data.activateAndSpawn();
    }

    public void addParadeFleetTooltip(TooltipMakerAPI tooltip){
        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color hl = Misc.getHighlightColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 10.0F;
        float spad = 5f;

        tooltip.addSectionHeading("Parade Fleets", color, dark, Alignment.MID, opad);

        if (!getParadeFleets().isEmpty()) {
            for (CampaignFleetAPI fleet : getParadeFleets()){
                String activity = fleet.getCurrentAssignment().getActionText();
                String name = fleet.getName();

                Color[] hlColours = new Color[]{color, hl};
                tooltip.addPara("%s is currently %s.", opad, hlColours, name, Misc.lcFirst(activity));
            }

        } else tooltip.addPara("There are currently %s.", opad, Misc.getHighlightColor(), "no active parade fleets.");
    }

    public void addShipStorageValueTooltip(CargoAPI cargo, TooltipMakerAPI tooltip, boolean expanded) {
        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 10.0F;
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
        if (market.isPlayerOwned() && submarket != null) {
            addParadeFleetTooltip(tooltip);
            addShipStorageValueTooltip(submarket.getCargo(), tooltip, expanded);
        }
    }

    /**
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
        return submarket != null && submarket.getCargo().getMothballedShips().getMembersListCopy().size() > 10;
    }

    public float getCreditsForValue(float value){
        return MIN_ADDITIONAL_CREDITS + MAX_ADDITIONAL_CREDITS * value;
    }

    public float getTotalShipValue(){
        if (submarket == null) return 0;

        float val = 0f;

        CargoAPI cargo = submarket.getCargo();
        cargo.initMothballedShips(Factions.PLAYER);

        //dirty duplicate removal
        Map<ShipHullSpecAPI, Float> specValueMap = new LinkedHashMap<>();
        for (FleetMemberAPI member : cargo.getMothballedShips().getMembersListCopy()) specValueMap.put(member.getHullSpec(), getCreditsForValue(getValueForShip(member)));
        for (Float value : specValueMap.values()) val += value;

        return val;
    }

    private void updateShipValueStatistics() {
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
    }

    //submarket stuff

    public SubmarketAPI getSubmarket() {
        return submarket;
    }

    public SubmarketAPI addSubmarket(MuseumSubmarketData data){
        archiveSubMarkets.add(data); //must be before addition, submarket plugin checks for data on init
        market.addSubmarket(data.submarketID);

        return market.getSubmarket(data.submarketID);
    }

    public void removeSubmarket(MuseumSubmarketData data){
        ((RemovablePlayerSubmarketPluginAPI) market.getSubmarket(data.submarketID).getPlugin()).notifyBeingRemoved();
        market.removeSubmarket(data.submarketID);
        archiveSubMarkets.remove(data);
    }

    public void removeSubmarkets(){
        if (submarket != null) {
            ((RemovablePlayerSubmarketPluginAPI) submarket.getPlugin()).notifyBeingRemoved();
            market.removeSubmarket(Ids.MUSEUM_SUBMARKET);
        }

        for (MuseumSubmarketData data : new ArrayList<>(archiveSubMarkets)){
            ((RemovablePlayerSubmarketPluginAPI) market.getSubmarket(data.submarketID).getPlugin()).notifyBeingRemoved();
            market.removeSubmarket(data.submarketID);
            archiveSubMarkets.remove(data);
        }
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);

        for (CampaignFleetAPI fleet : new ArrayList<>(getParadeFleets())) {
            fleet.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
        }

        paradeFleetData.clear();
        removeSubmarkets();
    }

    public List<MuseumSubmarketData> getArchiveSubMarkets() {
        return archiveSubMarkets;
    }

    public MuseumSubmarketData getData(SubmarketPlugin forPlugin){
        for (MuseumSubmarketData data : archiveSubMarkets) if (data.submarketID.equals(forPlugin.getSubmarket().getSpecId())) return data;
        return null;
    }

    @Override
    public void reportEconomyMonthEnd() {

    }

    // - Gamma: Increase income to 3000 max
    // - Beta: Increase stability and immigration of local planet by x per 10k?
    // - Alpha: second parade fleet (if sufficient ships)

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP || mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48);

            text.addPara(pre + "Increases the maximum active %s by %s while sufficient ships are available.", 0f, highlight,
                    "parade fleets",
                    "" + ALPHA_CORE_EXTRA_PARADES);

            tooltip.addImageWithText(opad);
            return;
        }

        tooltip.addPara(pre + "Increases the maximum active %s by %s while sufficient ships are available.", opad, highlight,
                "parade fleets",
                "" + ALPHA_CORE_EXTRA_PARADES);
    }

    //Beta: Increase stability and immigration of local planet by x per y

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP || mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);

            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48);

            text.addPara(pre + "Increases stability by %s per %s in museum income. Increases growth by %s per %s in museum income.", 0f, highlight,
                    "" + 1, Misc.getDGSCredits(BETA_CORE_INCOME_PER_STABILITY), "" + 1, Misc.getDGSCredits(BETA_CORE_INCOME_PER_STABILITY));

            tooltip.addImageWithText(opad);
            return;
        }

        tooltip.addPara(pre + "Increases stability by %s per %s in museum income. Increases growth by %s per %s in museum income.", opad, highlight,
                "" + 1, Misc.getDGSCredits(BETA_CORE_INCOME_PER_STABILITY), "" + 1, Misc.getDGSCredits(BETA_CORE_INCOME_PER_STABILITY));

    }
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP || mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48);

            text.addPara(pre + "Increases museum income by %s", 0f, highlight,
                    StringHelper.getAbsPercentString(GAMMA_CORE_INCOME_MULT, true));

            tooltip.addImageWithText(opad);
            return;
        }

        tooltip.addPara(pre + "Increases museum income by %s.", opad, highlight,
                StringHelper.getAbsPercentString(GAMMA_CORE_INCOME_MULT, true));

    }

    @Override
    protected void applyAICoreModifiers() {
        unapplyAICoreModifiers();
        super.applyAICoreModifiers();
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        super.applyAlphaCoreModifiers();
        maxParades = DEFAULT_MAX_PARADES + ALPHA_CORE_EXTRA_PARADES;
    }

    @Override
    protected void applyBetaCoreModifiers() {
        super.applyBetaCoreModifiers();
        market.getStability().modifyFlat(getModId(), (float) Math.floor(getTotalShipValue() / BETA_CORE_INCOME_PER_STABILITY), getNameForModifier());
        //immigration handled in modifyIncoming
    }

    @Override
    protected void applyGammaCoreModifiers() {
        super.applyGammaCoreModifiers();
        incomeMult = GAMMA_CORE_INCOME_MULT;
    }

    @Override
    protected void applyNoAICoreModifiers() {
        super.applyNoAICoreModifiers();
        unapplyAICoreModifiers();
    }

    public void unapplyAICoreModifiers(){
        incomeMult = DEFAULT_INCOME_MULT;
        maxParades = DEFAULT_MAX_PARADES;
        market.getStability().unmodify(getModId());
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (Commodities.BETA_CORE.equals(getAICoreId())) incoming.getWeight().modifyFlat(getModId(), (float) Math.floor(getTotalShipValue() / BETA_CORE_INCOME_PER_POINT_IMMIGRATION), getNameForModifier());
        else incoming.getWeight().unmodify(getModId());
    }
}
