package indevo.industries.courierport;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.utils.helper.Misc;

import java.util.List;

public class ShippingCargoManager {

    public static CargoAPI getTargetCargoFromOrigin(ShippingContract contract, boolean removeWhenFound) {
        ShippingContract.Scope scope = contract.scope;

        boolean ships = scope != ShippingContract.Scope.ALL_CARGO && scope != ShippingContract.Scope.SPECIFIC_CARGO;
        boolean cargo = scope != ShippingContract.Scope.ALL_SHIPS && scope != ShippingContract.Scope.SPECIFIC_SHIPS;
        boolean specific = scope.toString().toLowerCase().contains("specific");

        SubmarketAPI fromSubmarket = contract.getFromSubmarket();
        SubmarketAPI toSubmaket = contract.getToSubmarket();

        boolean canHoldShips = toSubmaket.getPlugin().showInFleetScreen() && toSubmaket.getPlugin().showInFleetScreen();
        boolean canHoldCargo = toSubmaket.getPlugin().showInCargoScreen() && toSubmaket.getPlugin().showInCargoScreen();

        CargoAPI fromSubmarketCargo = removeWhenFound ? fromSubmarket.getCargo() : Misc.getCargoCopy(fromSubmarket.getCargo());
        CargoAPI shippingCargo = Global.getFactory().createCargo(true);

        fromSubmarketCargo.initMothballedShips("player");
        shippingCargo.initMothballedShips("player");

        if (cargo && canHoldCargo) {
            if (specific) {
                for (CargoStackAPI stack : contract.targetCargo.getStacksCopy()) {
                    if (toSubmaket.isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_SELL)) continue;

                    float stackTargetQuantity = stack.getSize();
                    float stackActualQuantity = fromSubmarketCargo.getQuantity(stack.getType(), stack.getData());

                    if (stackActualQuantity <= stackTargetQuantity) {
                        //not enough, add entire stack
                        shippingCargo.addItems(stack.getType(), stack.getData(), stackActualQuantity);
                        fromSubmarketCargo.removeItems(stack.getType(), stack.getData(), stackActualQuantity);
                    } else {
                        //there is enough, remove target quantity
                        shippingCargo.addItems(stack.getType(), stack.getData(), stackTargetQuantity);
                        fromSubmarketCargo.removeItems(stack.getType(), stack.getData(), stackTargetQuantity);
                    }
                }
            } else {
                for (CargoStackAPI stack : fromSubmarketCargo.getStacksCopy()) {
                    if (!toSubmaket.isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_SELL)) {
                        shippingCargo.addFromStack(stack);
                        fromSubmarketCargo.removeStack(stack);
                    }
                }
            }

            fromSubmarketCargo.removeEmptyStacks();
            fromSubmarketCargo.sort();

            //also makes stockpile costs go brrt
            PlayerMarketTransaction transaction = new PlayerMarketTransaction(contract.getFromMarket(), fromSubmarket, CampaignUIAPI.CoreUITradeMode.NONE);
            transaction.getBought().addAll(shippingCargo);
            if (removeWhenFound) fromSubmarket.getPlugin().reportPlayerMarketTransaction(transaction);
        }

        if (ships && canHoldShips) {
            List<FleetMemberAPI> membersListCopy = fromSubmarketCargo.getMothballedShips().getMembersListCopy();

            if (specific) {
                for (ShipVariantAPI v : contract.variantList) {
                    FleetMemberAPI match = null;

                    //exact match
                    for (FleetMemberAPI m : membersListCopy) {
                        if (m.getVariant().getHullVariantId().equals(v.getHullVariantId())) {
                            match = m;
                            break;
                        }
                    }

                    //approx. match
                    if (match == null) for (FleetMemberAPI m : membersListCopy) {
                        if (hullsEqual(m.getVariant(), v)) {
                            match = m;
                            break;
                        }
                    }

                    if (match != null) {
                        shippingCargo.getMothballedShips().addFleetMember(match);
                        fromSubmarketCargo.getMothballedShips().removeFleetMember(match);
                    }
                }
            } else {
                for (FleetMemberAPI m : membersListCopy) {
                    shippingCargo.getMothballedShips().addFleetMember(m);
                    fromSubmarketCargo.getMothballedShips().removeFleetMember(m);
                }
            }
        }

        return shippingCargo;
    }

    private static boolean hullsEqual(ShipVariantAPI one, ShipVariantAPI two) {
        String oneBaseHullId = getBaseShipHullSpec(one.getHullSpec()).getHullId();
        String twoBaseHullId = getBaseShipHullSpec(two.getHullSpec()).getHullId();

        return one.getHullVariantId().equals(two.getHullVariantId()) || one.getHullSpec().getHullId().equals(two.getHullSpec().getHullId()) || oneBaseHullId.equals(twoBaseHullId);
    }

    private static ShipHullSpecAPI getBaseShipHullSpec(ShipHullSpecAPI spec) {
        ShipHullSpecAPI base = spec.getDParentHull();
        if (!spec.isDefaultDHull() && !spec.isRestoreToBase()) base = spec;
        if (spec.isRestoreToBase()) base = spec.getBaseHull();
        return base;
    }
}
