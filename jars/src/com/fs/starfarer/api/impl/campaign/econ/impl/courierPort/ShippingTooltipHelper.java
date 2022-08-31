package com.fs.starfarer.api.impl.campaign.econ.impl.courierPort;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;

public class ShippingTooltipHelper {

    public static void addStr(String orig, String add, boolean and){
        if(and) orig += ", ";
        orig += add;
    }

    public static String getDaysMonthString(int days){
        if (days <= 0) return "today";
        if (days < 31) return days + (days == 1 ? " day" : " days");

        String monthOrMonths = (int) Math.round(days / 31) > 1 ? " months" : " month";
        return (int) Math.round(days / 31) + monthOrMonths;
    }

    public static String getCadenceString(int days){
        if (days == 0) return "once";
        if (days < 31) return days + " days";

        String monthOrMonths = (int) Math.round(days / 31) > 1 ? " months" : " month";
        return days > 30 ? (int) Math.round(days / 31) + monthOrMonths : days + " days";
    }

    public static String getShipAmtString(ShippingContract contract){
        if(contract.scope == ShippingContract.Scope.SPECIFIC_SHIPS || contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING)
            return contract.variantList.size() + " ships";
        else if(contract.scope == ShippingContract.Scope.SPECIFIC_CARGO || contract.scope == ShippingContract.Scope.ALL_CARGO) return "no ships";
        return "all ships";

        /*SubmarketAPI sub = contract.getFromSubmarket();
        if(sub == null) return 0;

        sub.getCargo().initMothballedShips("player");
        return sub.getCargo().getMothballedShips().getMembersListCopy().size();*/
    }

    public static String getCargoAmtString(ShippingContract contract){
        SubmarketAPI sub = contract.getFromSubmarket();
        if(sub == null) return "";
        int amt = 0;


        if(contract.scope == ShippingContract.Scope.SPECIFIC_CARGO || contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING) {
            for (CargoStackAPI s : contract.targetCargo.getStacksCopy()){
                amt += s.getSize();
            }

            return amt + " items";
        } else if(contract.scope == ShippingContract.Scope.SPECIFIC_SHIPS || contract.scope == ShippingContract.Scope.ALL_SHIPS) return "no items";
        return "all items";


        /*if(contract.scope == ShippingContract.Scope.SPECIFIC_CARGO || contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING) {
            for (CargoStackAPI s : contract.targetCargo.getStacksCopy()){
                amt += s.getSize();
            }
        } else {
            for (CargoStackAPI s : sub.getCargo().getStacksCopy()){
                amt += s.getSize();
            }
        }

        return amt;*/
    }
}
