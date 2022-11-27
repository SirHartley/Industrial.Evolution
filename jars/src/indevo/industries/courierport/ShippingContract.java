package indevo.industries.courierport;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

import static indevo.industries.courierport.ShippingTooltipHelper.addStr;

public class ShippingContract {
    public enum Scope {
        EVERYTHING,
        ALL_CARGO,
        ALL_SHIPS,
        SPECIFIC_CARGO,
        SPECIFIC_SHIPS,
        SPECIFIC_EVERYTHING
    }

    public String id;
    public String name;
    public String fromMarketId;
    public String toMarketId;
    public String fromSubmarketId;
    public String toSubmarketId;
    public Scope scope;
    public CargoAPI targetCargo;
    public List<ShipVariantAPI> variantList;
    private int recurrentDays;
    public boolean isActive = true;

    public int elapsedDays = 0;

    public ShippingContract() {
        this.id = Misc.genUID();
        this.name = "Contract " + (ShippingContractMemory.getContractList().size() + 1);
        this.fromMarketId = null;
        this.toMarketId = null;
        this.fromSubmarketId = Submarkets.SUBMARKET_STORAGE;
        this.toSubmarketId = Submarkets.SUBMARKET_STORAGE;
        this.scope = Scope.EVERYTHING;
        this.recurrentDays = 0;
        this.targetCargo = Global.getFactory().createCargo(true);
        this.variantList = new ArrayList<>();
    }

    private ShippingContract(String id, String name, String fromMarketId, String toMarketId, String fromSubmarketId, String toSubmarketId, Scope scope, int recurrentDays, CargoAPI cargo, List<ShipVariantAPI> variantList, boolean active) {
        this.id = id;
        this.name = name;
        this.fromMarketId = fromMarketId;
        this.toMarketId = toMarketId;
        this.fromSubmarketId = fromSubmarketId;
        this.toSubmarketId = toSubmarketId;
        this.scope = scope;
        this.recurrentDays = recurrentDays;
        this.targetCargo = cargo.createCopy();
        this.variantList = new ArrayList<>(variantList);
        this.isActive = active;
    }

    public int getRecurrentDays() {
        return recurrentDays;
    }

    public void setRecurrentDays(int recurrentDays) {
        this.recurrentDays = recurrentDays;
        this.elapsedDays = 0;
    }

    public ShippingContract getCopy(){
        return new ShippingContract(id, name, fromMarketId, toMarketId, fromSubmarketId, toSubmarketId, scope, recurrentDays, targetCargo, variantList, isActive);
    }

    public MarketAPI getFromMarket() {
        return fromMarketId != null
                && Global.getSector().getEconomy().getMarket(fromMarketId) != null
                ? Global.getSector().getEconomy().getMarket(fromMarketId) : null;
    }

    public MarketAPI getToMarket() {
        return toMarketId != null
                && Global.getSector().getEconomy().getMarket(toMarketId) != null
                ? Global.getSector().getEconomy().getMarket(toMarketId) : null;
    }

    public SubmarketAPI getToSubmarket(){
        return toMarketId != null && getToMarket() != null && toSubmarketId != null ?
                Global.getSector().getEconomy().getMarket(toMarketId).getSubmarket(toSubmarketId) : null;
    }

    public SubmarketAPI getFromSubmarket(){
        return fromMarketId != null && getFromMarket() != null && fromSubmarketId != null ?
                Global.getSector().getEconomy().getMarket(fromMarketId).getSubmarket(fromSubmarketId) : null;
    }

    public void clearTargetCargo(){
        targetCargo.clear();
    }

    public void clearTargetShips(){
        targetCargo.initMothballedShips("player");
        targetCargo.getMothballedShips().clear();

        variantList.clear();
    }

    public void addToCargo(CargoAPI cargo){
        targetCargo.addAll(cargo);
    }

    public List<ShipVariantAPI> getShipList() {
        return variantList;
    }

    public void addToShips(List<FleetMemberAPI> members) {
        for (FleetMemberAPI m : members) {
            variantList.add(m.getVariant().clone());
        }
    }

    public boolean isValid(){
        boolean hasFromMarket = getFromMarket() != null && !getFromMarket().isPlanetConditionMarketOnly();
        boolean hasToMarket = getToMarket() != null && !getToMarket().isPlanetConditionMarketOnly();
        boolean hasFromSubMarket = hasFromMarket && fromSubmarketId != null && getFromMarket().hasSubmarket(fromSubmarketId);
        boolean hasToSubMarket = hasToMarket && toSubmarketId != null && getToMarket().hasSubmarket(toSubmarketId);

        return hasFromMarket && hasToMarket && hasFromSubMarket && hasToSubMarket;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ShippingContract && ((ShippingContract) obj).getId().equals(getId());
    }

    public String getInvalidReason(){
        boolean hasFromMarket = getFromMarket() != null && !getFromMarket().isPlanetConditionMarketOnly();
        boolean hasToMarket = getToMarket() != null && !getToMarket().isPlanetConditionMarketOnly();
        boolean hasFromSubMarket = hasFromMarket && fromSubmarketId != null && getFromMarket().hasSubmarket(fromSubmarketId);
        boolean hasToSubMarket = hasToMarket && toSubmarketId != null && getToMarket().hasSubmarket(toSubmarketId);

        String reason = "";
        int i = 0;

        if (!hasFromMarket) {
            addStr(reason, "origin planet not available", false);
            i++;
        }
        if (!hasToMarket) {
            addStr(reason, "destination planet not available", i > 0);
            i++;
        }

        if (hasFromMarket && !hasFromSubMarket) {
            addStr(reason, "origin storage not available", i > 0);
            i++;
        }
        if (hasToMarket && !hasToSubMarket) {
            addStr(reason, "destination storage not available", i > 0);
        }

        return reason;
    }
}
