package com.fs.starfarer.api.impl.campaign.rulecmd.portRules;
@Deprecated
public class IndEvo_ShippingVariables {

    /*Dialogue mechanics:
        New Object: IndEvo_ShippingContainer

        Select {from} (allow any planet with storage with stuff in it)
                    get dynamic listing of all planets with stored stuff, display size, colour in faction colours
                select Submarket if multiple (other than storage) - otherwise, select only the one that's filled
                    get dynamic listing of all submarkets with stored stuff
                    set IndEvo_ShippingContainer.fromSubmarket
        Select {to} (Allow any planet with unlocked storage)
                    get dynamic listing of all planets with eligible submarkets to deliver to (unlocked, isFreeTransfer)
                select Submarket if multiple (other than storage),
                    get dynamic listing of all submarkets with stored stuff
                    set IndEvo_ShippingContainer.toSubmarket
        Display cargo selection dialogue (Ship selection for ships)
                    check if deliverable is legal in toSubmarket
                    add selected stuff to IndEvo_ShippingContainer cargo
        Display recurrent shipping dialogue (Ship the selected amounts recurrently?  - slider for "every X months" selection, 0 is once
                    set IndEvo_ShippingContainer.recurrentMonths
        Display total cost, time and confirmation option (additionally, option "back" and "return to menu")
            Maybe add option to insure the delivery, adding 20% cost but refunding 80% value in case the shipment is lost
            cost is based on commodity volume (cargo space), and distance
        Create custom intel entry with contract, add "cancel shipment contract" button
        Spawn fleet with assignment, add fleet listener to intel entry*/

    public static final String CURRENT_SHIPPING_CONTAINER = "$IndEvo_shippingContainer";
    public static final String CURRENT_SUBLIST_PAGE = "$IndEvo_ShippingSubListPage";
    public static final String CURRENT_LIST = "$IndEvo_shippingList";

    public static final String ACTION_TYPE = "$IndEvo_ShippingActionType";

    public static final Float EXPIRE_TIME = 0f;

    public enum actionTypes {
        ORIGIN_MARKET,
        ORIGIN_SUBMARKET,
        TARGET_MARKET,
        TARGET_SUBMARKET,
        ALL_CONTAINERS,
    }
}
