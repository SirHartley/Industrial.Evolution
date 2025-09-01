package indevo.industries.courierport;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.Settings;

import java.util.ArrayList;
import java.util.List;

public class ShippingCostCalculator {
    public static final float COST_PER_SPACE = Settings.getInt(Settings.COURIER_COST_PER_CARGO_SPACE);;
    public static final float COST_PER_SHIP_SPACE = Settings.getInt(Settings.COURIER_COST_PER_SHIP_DP);;
    public static final float CONTRACT_BASE_FEE = Settings.getInt(Settings.COURIER_BASE_FEE);;
    public static final float COST_PER_LY_SETTING_MULT = Settings.getFloat(Settings.COURIER_COST_PER_LY_MULT);
    public static final float COST_PER_LY_MULT = 250f; //unaffected by setting cause hard to explain to player

    //AI cores
    public static final float DISTANCE_MULT_REDUCTION = 0.8f;
    public static final float TOTAL_FEE_REDUCTION = 0.9f;
    public static final String AI_CORE_ID_STRING = "$IndEvo_CurrentShippingCore";

    public static float getCostForCargoSpace(int space, float lyMult) {
        return space * COST_PER_SPACE * lyMult * getAlphaCoreRed();
    }

    public static float getCostForShipSpace(int dp, float lyMult) {
        return dp * COST_PER_SHIP_SPACE * lyMult * getAlphaCoreRed();
    }

    public static float getLYMult(ShippingContract contract) {
        if (contract.getFromMarket() == null || contract.getToMarket() == null) return 0f;

        return getLYMult(contract.getFromMarket().getPrimaryEntity(), contract.getToMarket().getPrimaryEntity());
    }

    public static float getLYMult(SectorEntityToken from, SectorEntityToken to) {
        if (from == null || to == null) return 0f;

        float ly = Misc.getDistanceLY(from, to);
        float lyMult = (float) Math.round((1f + ((ly * Math.pow(1000f, 1f + (ly / COST_PER_LY_MULT))) / 10000f)) * 10f) / 10f;

        float red = ShippingTargetHelper.getMemoryAICoreId().equals(Commodities.BETA_CORE) ? DISTANCE_MULT_REDUCTION : 1f;
        lyMult *= red;
        lyMult *= COST_PER_LY_SETTING_MULT;

        return lyMult;
    }

    public static float getTotalContractCost(CargoAPI cargo, ShippingContract contract) {
        return (getSpecificShipCostForCargo(cargo, contract, false) + getSpecificCargoCostForCargo(cargo, contract, false) + CONTRACT_BASE_FEE);
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


    public static float getCostForStack(CargoStackAPI stack) {
        return (getStackSpace(stack) * COST_PER_SPACE);
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
                totalValue += v.getHullSpec().getFleetPoints();
            }
        }

        return totalValue * COST_PER_SHIP_SPACE * getAlphaCoreRed();
    }

    private static float getBaseStackCargoSpaceCost(CargoAPI cargo) {
        float totalSpace = 0f;

        for (CargoStackAPI s : cargo.getStacksCopy()) {
            totalSpace += getStackSpace(s);
        }

        return totalSpace * COST_PER_SPACE * getAlphaCoreRed();
    }

    private static float getAlphaCoreRed() {
        return ShippingTargetHelper.getMemoryAICoreId().equals(Commodities.ALPHA_CORE) ? TOTAL_FEE_REDUCTION : 1f;
    }

}
