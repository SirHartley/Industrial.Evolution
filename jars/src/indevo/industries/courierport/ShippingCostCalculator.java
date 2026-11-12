package indevo.industries.courierport;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import indevo.config.Setting;

public class ShippingCostCalculator {
    public static final float COST_PER_LY_MULT = 250f; //unaffected by setting cause hard to explain to player

    //AI cores
    public static final float DISTANCE_MULT_REDUCTION = 0.8f;
    public static final float TOTAL_FEE_REDUCTION = 0.9f;
    public static final String AI_CORE_ID_STRING = "$IndEvo_CurrentShippingCore";

    public static float getCostForCargoSpace(int space, float lyMult) {
        return space * Setting.COURIER_COST_PER_CARGO_SPACE.value() * lyMult * getAlphaCoreRed();
    }

    public static float getCostForShipSpace(int dp, float lyMult) {
        return dp * Setting.COURIER_COST_PER_SHIP_DP.value() * lyMult * getAlphaCoreRed();
    }

    public static float getLYMult(ShippingContract contract) {
        if (contract.getFromMarket() == null || contract.getToMarket() == null) return 0f;

        return getLYMult(contract.getFromMarket().getPrimaryEntity(), contract.getToMarket().getPrimaryEntity());
    }

    public static float getLYMult(SectorEntityToken from, SectorEntityToken to) {
        return getLYMult(from, to, true);
    }

    public static float getLYMult(SectorEntityToken from, SectorEntityToken to, boolean withBetaCore) {
        if (from == null || to == null) return 0f;

        float ly = Misc.getDistanceLY(from, to);
        float lyMult = (float) Math.round((1f + ((ly * Math.pow(1000f, 1f + (ly / COST_PER_LY_MULT))) / 10000f)) * 10f) / 10f;

        if (withBetaCore && ShippingTargetHelper.getMemoryAICoreId().equals(Commodities.BETA_CORE)) lyMult *= DISTANCE_MULT_REDUCTION;
        lyMult *= Setting.COURIER_COST_PER_LY_MULT.value();

        return lyMult;
    }

    public static float getTotalContractCost(CargoAPI cargo, ShippingContract contract) {
        return (getSpecificShipCostForCargo(cargo, contract, false) + getSpecificCargoCostForCargo(cargo, contract, false) + Setting.COURIER_BASE_FEE.value());
    }

    public static float getSpecificShipCostForCargo(CargoAPI cargo, ShippingContract contract, boolean ignoreLYMult) {
        float total = 0f;
        List<ShipVariantAPI> variantList = new ArrayList<>();
        cargo.initMothballedShips("player");

        for (FleetMemberAPI m : cargo.getMothballedShips().getMembersListCopy()) {
            variantList.add(m.getVariant());
        }

        total += getBaseAbstractShipSpaceCost(variantList);

        float mult = ignoreLYMult ? 1f : getLYMult(contract);
        return total * mult;
    }

    public static float getSpecificCargoCostForCargo(CargoAPI cargo, ShippingContract contract, boolean ignoreLYMult) {
        float total = getBaseStackCargoSpaceCost(cargo);

        float mult = ignoreLYMult ? 1f : getLYMult(contract);
        return total * mult;
    }


    public static float getShipCostForVariants(List<ShipVariantAPI> variants, ShippingContract contract) {
        return getBaseAbstractShipSpaceCost(variants) * getLYMult(contract);
    }

    public static float getCargoSpace(CargoAPI cargo) {
        float space = 0f;
        for (CargoStackAPI s : cargo.getStacksCopy()) space += getStackSpace(s);
        return space;
    }

    public static float getDeploymentPoints(List<ShipVariantAPI> variants) {
        float dp = 0f;
        for (ShipVariantAPI v : variants) dp += v.getHullSpec().getSuppliesToRecover();
        return dp;
    }

    public static float getCostForStack(CargoStackAPI stack) {
        return (getStackSpace(stack) * Setting.COURIER_COST_PER_CARGO_SPACE.value());
    }

    private static float getStackSpace(CargoStackAPI stack) {
        float space = stack.getCargoSpace();

        boolean isCrewOrFuel = stack.isPersonnelStack() || stack.isFuelStack();
        space = isCrewOrFuel ? stack.getSize() * 0.75f : space;

        return space;
    }

    //private methods

    private static float getBaseAbstractShipSpaceCost(List<ShipVariantAPI> list) {
        float totalValue = 0f;

        if (!list.isEmpty()) {
            for (ShipVariantAPI v : list) {
                totalValue += v.getHullSpec().getSuppliesToRecover();
            }
        }

        return totalValue * Setting.COURIER_COST_PER_SHIP_DP.value() * getAlphaCoreRed();
    }

    private static float getBaseStackCargoSpaceCost(CargoAPI cargo) {
        return getCargoSpace(cargo) * Setting.COURIER_COST_PER_CARGO_SPACE.value() * getAlphaCoreRed();
    }

    public static float getAlphaCoreRed() {
        return ShippingTargetHelper.getMemoryAICoreId().equals(Commodities.ALPHA_CORE) ? TOTAL_FEE_REDUCTION : 1f;
    }

}
