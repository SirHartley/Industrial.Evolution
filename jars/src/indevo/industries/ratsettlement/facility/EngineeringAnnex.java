package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.economy.RelicSpeedUpTarget;
import indevo.ids.Ids;
import indevo.industries.engineeringhub.ReverseEngineering;
import indevo.industries.ratsettlement.dialogue.AnnexDialogPlugin;
import indevo.industries.ratsettlement.dialogue.AnnexView;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.RelicHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// one hull class at a time at hub speed, no ai cores, no hullmod data; progress is the hub's shared map
public class EngineeringAnnex extends SettlementFacility implements RelicSpeedUpTarget {

    private static final String OPEN = "open";

    // base hull id of the locked class, null while none is set
    private String hullId = null;
    private ShipVariantAPI inWork = null;
    private String inWorkName = null;
    private int daysPassed = 0;
    private int daysRequired = 0;
    // factory cargo, not a submarket, so the drop off stays out of the cargo screen
    private CargoAPI dropOff = null;
    private Set<String> fromFleet = new HashSet<>();

    public record Payment(SpecialItemData item, boolean fleet, int count) {
    }

    @Override
    protected Setting parentSetting() {
        return Setting.ENGHUB;
    }

    @Override
    protected void onNewDay() {
        if (hullId == null) return;
        if (getHullSpec() == null) {
            inWork = null;
            returnDropOff();
            hullId = null;
            return;
        }

        if (inWork != null) {
            daysPassed++;
            if (daysPassed < daysRequired) return;
            finishShip();
        }

        if (isReady()) returnDropOff();
        else startNext();
    }

    private void startNext() {
        List<FleetMemberAPI> ships = getDropOffCargo().getMothballedShips().getMembersListCopy();
        if (ships.isEmpty()) return;

        FleetMemberAPI ship = ships.get(0);
        getDropOffCargo().getMothballedShips().removeFleetMember(ship);
        fromFleet.remove(ship.getId());
        inWorkName = ship.getShipName();
        inWork = MiscIE.stripShipToCargoAndReturnVariant(ship, getStorageCargo());
        daysPassed = 0;
        daysRequired = ReverseEngineering.getBaseDays(inWork.getHullSize());

        Global.getSector().getCampaignUI().addMessage("Reverse engineering has begun for a %s at the %s.", Misc.getTextColor(),
                inWork.getHullSpec().getNameWithDesignationWithDashClass(), getName(), Misc.getHighlightColor(), Misc.getHighlightColor());
    }

    private void finishShip() {
        String name = inWork.getHullSpec().getNameWithDesignationWithDashClass();
        ReverseEngineering.addProgress(hullId, ReverseEngineering.getValue(inWork, 0));
        inWork = null;
        daysPassed = 0;
        daysRequired = 0;

        MessageIntel intel = new MessageIntel("Reverse engineering of the %s has finished.", Misc.getTextColor(), new String[]{name}, Misc.getHighlightColor());
        intel.addLine(BaseIntelPlugin.BULLET + "The current progress is: %s", Misc.getHighlightColor(), new String[]{Math.round(getProgress() * 100) + "%"});
        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "revBP"));
        intel.setSound(BaseIntelPlugin.getSoundMinorMessage());
        Global.getSector().getCampaignUI().addMessage(intel);
    }

    private void returnDropOff() {
        CargoAPI storage = getStorageCargo();
        for (FleetMemberAPI ship : getDropOffCargo().getMothballedShips().getMembersListCopy()) {
            getDropOffCargo().getMothballedShips().removeFleetMember(ship);
            storage.getMothballedShips().addFleetMember(ship);
        }

        fromFleet.clear();
    }

    // demolished: the ship in work is lost, the drop off goes back to storage
    @Override
    public void unapply() {
        inWork = null;
        returnDropOff();
        hullId = null;
    }

    public String getHullId() {
        return hullId;
    }

    public ShipHullSpecAPI getHullSpec() {
        return ReverseEngineering.getHullSpecOrNull(hullId);
    }

    public ShipVariantAPI getInWork() {
        return inWork;
    }

    public String getInWorkName() {
        return inWorkName;
    }

    public float getInWorkValue() {
        return inWork == null ? 0f : ReverseEngineering.getValue(inWork, 0);
    }

    public static float getValue(FleetMemberAPI ship) {
        return ReverseEngineering.getValue(ship.getVariant(), 0);
    }

    public float getDropOffValue() {
        float sum = 0f;
        for (FleetMemberAPI ship : getDropOff()) sum += getValue(ship);
        return sum;
    }

    // ships without D-mods still needed after the ship in work and the drop off
    public int getShipsNeeded() {
        ShipHullSpecAPI spec = getHullSpec();
        if (spec == null) return 0;

        float clean = ReverseEngineering.getCleanValue(spec.getHullSize());
        float left = 1f - getProgress() - getInWorkValue() - getDropOffValue();
        if (clean <= 0f || left <= 0.001f) return 0;
        return (int) Math.ceil(left / clean - 0.001f);
    }

    public float getProgress() {
        return hullId == null ? 0f : ReverseEngineering.getProgress(hullId);
    }

    public boolean isReady() {
        return hullId != null && getProgress() >= 1f;
    }

    public List<FleetMemberAPI> getDropOff() {
        return getDropOffCargo().getMothballedShips().getMembersListCopy();
    }

    public boolean isFromFleet(FleetMemberAPI ship) {
        return fromFleet.contains(ship.getId());
    }

    private CargoAPI getDropOffCargo() {
        if (dropOff == null) dropOff = Global.getFactory().createCargo(true);
        if (dropOff.getMothballedShips() == null) dropOff.initMothballedShips("player");
        return dropOff;
    }

    // any class below 100%, never the player's own ship
    public boolean canTake(FleetMemberAPI ship) {
        if (ship.isFighterWing() || ship.isFlagship() || ReverseEngineering.getCleanValue(ship.getHullSpec().getHullSize()) <= 0f) return false;
        return ReverseEngineering.getProgress(ReverseEngineering.getBaseHullId(ship.getHullSpec())) < 1f;
    }

    public boolean accepts(FleetMemberAPI ship) {
        return canTake(ship) && (hullId == null || hullId.equals(ReverseEngineering.getBaseHullId(ship.getHullSpec())));
    }

    // ships the panel lists from the fleet and settlement storage, the drop off first; anyClass ignores the locked class
    public List<FleetMemberAPI> getCandidates(boolean anyClass) {
        List<FleetMemberAPI> ships = new ArrayList<>(getDropOff());
        for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (anyClass ? canTake(ship) : accepts(ship)) ships.add(ship);
        }

        for (FleetMemberAPI ship : getStorageCargo().getMothballedShips().getMembersListCopy()) {
            if (anyClass ? canTake(ship) : accepts(ship)) ships.add(ship);
        }

        return ships;
    }

    public void put(FleetMemberAPI ship) {
        if (isReady() || !accepts(ship)) throw new IllegalStateException(ship.getHullId() + " put in the Engineering Annex for class " + hullId);

        FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();
        if (fleet.getMembersListCopy().contains(ship)) {
            PersonAPI captain = ship.getCaptain();
            if (captain != null && !captain.isDefault() && !captain.isPlayer() && !captain.isAICore()) ship.setCaptain(null);
            fleet.removeFleetMember(ship);
            fromFleet.add(ship.getId());
        } else getStorageCargo().getMothballedShips().removeFleetMember(ship);

        getDropOffCargo().getMothballedShips().addFleetMember(ship);
        if (hullId == null) hullId = ReverseEngineering.getBaseHullId(ship.getHullSpec());
    }

    // back where it came from; a class nothing was done for yet is set free again
    public void takeBack(FleetMemberAPI ship) {
        getDropOffCargo().getMothballedShips().removeFleetMember(ship);
        if (fromFleet.remove(ship.getId())) Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
        else getStorageCargo().getMothballedShips().addFleetMember(ship);

        if (getDropOff().isEmpty() && inWork == null && getProgress() <= 0f) hullId = null;
    }

    public List<Payment> getPayments() {
        List<Payment> payments = new ArrayList<>();
        if (hullId == null) return payments;

        addPayments(payments, getStorageCargo(), false);
        addPayments(payments, Global.getSector().getPlayerFleet().getCargo(), true);
        return payments;
    }

    private void addPayments(List<Payment> payments, CargoAPI cargo, boolean fleet) {
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData item = stack.getSpecialDataIfSpecial();
            if (ReverseEngineering.paysFor(item, hullId)) payments.add(new Payment(item, fleet, (int) stack.getSize()));
        }
    }

    public int getRelicCost() {
        return hullId == null ? 0 : ReverseEngineering.getRelicCost(hullId);
    }

    // payment null pays with relic components; the blueprint goes to settlement storage
    public boolean finish(Payment payment) {
        if (!isReady() || getHullSpec() == null) return false;

        MarketAPI market = getSettlementMarket();
        if (payment == null) {
            int cost = getRelicCost();
            if (!RelicHelper.canAfford(market, cost)) return false;
            RelicHelper.take(market, cost);
        } else {
            CargoAPI cargo = payment.fleet() ? Global.getSector().getPlayerFleet().getCargo() : getStorageCargo();
            if (!ReverseEngineering.paysFor(payment.item(), hullId) || cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, payment.item()) < 1f) return false;
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, payment.item(), 1);
        }

        ReverseEngineering.removeProgress(hullId);
        ReverseEngineering.deliver(hullId, market, getStorageCargo());
        returnDropOff();
        hullId = null;
        return true;
    }

    // the class's data is wiped unless the player owns a functional Engineering Hub somewhere
    public void abandon() {
        if (hullId != null && !ownsHub()) ReverseEngineering.removeProgress(hullId);

        inWork = null;
        daysPassed = 0;
        daysRequired = 0;
        returnDropOff();
        hullId = null;
    }

    public static boolean ownsHub() {
        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            Industry hub = market.getIndustry(Ids.ENGHUB);
            if (hub != null && hub.isFunctional()) return true;
        }

        return false;
    }

    public String getSettlementName() {
        return getSettlementMarket().getName();
    }

    @Override
    public MarketAPI getMarket() {
        return getSettlementMarket();
    }

    @Override
    public String getSpeedUpId() {
        return getID();
    }

    @Override
    public String getSpeedUpLabel() {
        return "Accelerate";
    }

    @Override
    public String getSpeedUpLore() {
        return "Domain scanners can see through armour plating that your engineers would otherwise have to peel away layer by layer.";
    }

    @Override
    public int getSpeedUpDaysLeft() {
        return inWork != null ? Math.max(daysRequired - daysPassed, 0) : 0;
    }

    @Override
    public int getSpeedUpDaysTotal() {
        return inWork != null ? daysRequired : 0;
    }

    @Override
    public void speedUp(int days) {
        daysPassed += days;
    }

    @Override
    protected void addOptions(InteractionDialogAPI dialog) {
        addOption(dialog, "Visit the Engineering Annex", OPEN);
    }

    @Override
    protected void onOption(InteractionDialogAPI dialog, String action) {
        openPanel(dialog, onReturn -> new AnnexDialogPlugin(this, new AnnexView(), onReturn));
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Reverse engineers one hull class at a time, one ship after the other, at Engineering Hub speed. Blueprint data is shared with your Engineering Hubs.", 0f);
        tooltip.addPara("At %s, overwrite a ship blueprint of the same hull size or pay %s. No AI cores, no hullmod data.", 10f, Misc.getHighlightColor(),
                "100%", RelicHelper.getName());
    }
}
