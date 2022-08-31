package com.fs.starfarer.api.impl.campaign.rulecmd.portRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_PrivatePort;
import com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingManager;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.rulecmd.portRules.IndEvo_ShippingVariables.*;
@Deprecated
public class IndEvo_CreateShippingSelectionList extends BaseCommandPlugin {

    private static void log(String Text) {
        Global.getLogger(IndEvo_CreateShippingSelectionList.class).info(Text);
    }

    private boolean debug = false;

    private void debugMessage(String text) {
        if (debug) log(text);
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (Global.getSettings().isDevMode()) debug = true;
        IndEvo_ShippingVariables.actionTypes actionType = IndEvo_ShippingVariables.actionTypes.valueOf(params.get(0).getString(memoryMap));

        boolean isCheckOnly = false;

        try {
            isCheckOnly = params.get(1).getString(memoryMap) != null;
        } catch (IndexOutOfBoundsException e) {
            debugMessage("IndexOutOfBoundsException for list validity check, this is expected");
        }

        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);

        List<MarketAPI> marketList = null;
        List<SubmarketAPI> subMarketList = null;
        List<IndEvo_PrivatePort.ShippingContainer> containerList = null;

        IndEvo_PrivatePort.ShippingContainer container = getCurrentShippingContainer(memory);

        //for all types: get the appropriate listing and store it in the memory
        switch (actionType) {
            case ORIGIN_MARKET:
                marketList = getEligibleMarketList(container, actionType);
                if (isCheckOnly) return isEligibleList(marketList);

                memory.set(CURRENT_LIST, splitList(marketList, 4), EXPIRE_TIME);
                createNewContainer(memory);
                break;

            case TARGET_MARKET:
                //for target market, remove origin market from list
                marketList = getEligibleMarketList(container, actionType);

                MarketAPI checkMarket = container.getOriginMarket();
                if (checkMarket != null) marketList.remove(checkMarket);

                if (isCheckOnly) return isEligibleList(marketList);

                memory.set(CURRENT_LIST, splitList(marketList, 4), EXPIRE_TIME);
                break;

            case ORIGIN_SUBMARKET:
                MarketAPI originMarket = getCurrentShippingContainer(memory).getOriginMarket();
                subMarketList = getEligibleSubMarketList(originMarket, container, actionType);
                if (isCheckOnly) return isEligibleList(subMarketList);

                memory.set(CURRENT_LIST, splitList(subMarketList, 4), EXPIRE_TIME);
                break;

            case TARGET_SUBMARKET:
                MarketAPI destinationMarket = container.getTargetMarket();

                subMarketList = getEligibleSubMarketList(destinationMarket, container, actionType);
                if (isCheckOnly) return isEligibleList(subMarketList);

                memory.set(CURRENT_LIST, splitList(subMarketList, 4), EXPIRE_TIME);
                break;

            case ALL_CONTAINERS:
                containerList = IndEvo_ShippingManager.getCurrentInstance().getContainerList();
                if (isCheckOnly) return isEligibleList(containerList);

                memory.set(CURRENT_LIST, splitList(containerList, 4), EXPIRE_TIME);
                break;

            default:
                throw new IllegalArgumentException("Unexpected argument: " + actionType);
        }

        memory.set(ACTION_TYPE, actionType);
        memory.set(CURRENT_SUBLIST_PAGE, 1, EXPIRE_TIME);

        return true;
    }

    private void createNewContainer(MemoryAPI memory) {
        memory.set(CURRENT_SHIPPING_CONTAINER, new IndEvo_PrivatePort.ShippingContainer());
    }

    public static IndEvo_PrivatePort.ShippingContainer getCurrentShippingContainer(MemoryAPI memory) {
        if (memory.contains(CURRENT_SHIPPING_CONTAINER)) {
            return (IndEvo_PrivatePort.ShippingContainer) memory.get(CURRENT_SHIPPING_CONTAINER);
        } else {
            return new IndEvo_PrivatePort.ShippingContainer();
        }
    }

    private boolean isEligibleList(List<?> check) {
        return !check.isEmpty();
    }

    //Split a long list into as many parts as the "itemsPerPage" counter needs to be kept
    public List<List<Object>> splitList(List<?> list, int entriesPerPage) {

        debugMessage("Splitting List into " + (int) Math.ceil(list.size() / (entriesPerPage * 1f)) + " parts");

        int i = 0;
        List<List<Object>> splitList = new ArrayList<>();
        while (i < list.size()) {
            int nextInc = Math.min(list.size() - i, entriesPerPage);
            List<Object> batch = new ArrayList<>(list.subList(i, i + nextInc));
            splitList.add(batch);
            i = i + nextInc;
        }

        return splitList;
    }

    public static List<SubmarketAPI> getEligibleSubMarketList(MarketAPI market, IndEvo_PrivatePort.ShippingContainer container, actionTypes type) {
        boolean debug = false;
        if (Global.getSettings().isDevMode()) debug = true;

        Set<SubmarketAPI> finalSet = new HashSet<>();
        Set<String> whitelist = IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.SHIPPING_LIST);

        switch (type) {
            case ORIGIN_SUBMARKET:
                //origin submarket doesn't need container check
                if (market != null) {
                    for (SubmarketAPI sub : market.getSubmarketsCopy()) {
                        CargoAPI cargo = sub.getCargoNullOk();

                        if (sub.getPlugin().isFreeTransfer()
                                && cargo != null
                                && (!cargo.isEmpty() || !cargo.getMothballedShips().getMembersListCopy().isEmpty())
                                && whitelist.contains(sub.getSpecId())) finalSet.add(sub);
                    }
                }
                break;
            case TARGET_SUBMARKET:
                if (market != null) {
                    if (debug) log("getting target Submarkets");

                    //if there is a container, check if the submarket has legal cargo:
                    if (container != null
                            && container.getOriginMarket() != null
                            && container.getOriginSubmarketId() != null) {

                        if (debug) log("Full check for market " + market.getName());

                        MarketAPI originMarket = container.getOriginMarket();
                        SubmarketAPI originSub = originMarket.getSubmarket(container.getOriginSubmarketId());

                        if (debug)
                            log("originMarket " + originMarket.getName() + " originSubmarket " + originSub.getNameOneLine());

                        for (SubmarketAPI sub : market.getSubmarketsCopy()) {
                            if (!sub.getPlugin().isFreeTransfer() || sub.getSpecId().equals(Submarkets.LOCAL_RESOURCES))
                                continue;

                            //without this, uninitialized submarkets return false on cargo checks
                            boolean matchingCargoScreen = sub.getPlugin().showInCargoScreen() && originSub.getPlugin().showInCargoScreen();
                            boolean matchingFleetScreen = sub.getPlugin().showInFleetScreen() && originSub.getPlugin().showInFleetScreen();
                            boolean intersect = matchingCargoScreen || matchingFleetScreen;
                            boolean hasLegalCargo = false;

                            if (matchingCargoScreen) {
                                for (CargoStackAPI stack : originSub.getCargo().getStacksCopy()) {
                                    if (!sub.getPlugin().isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_SELL)) {
                                        hasLegalCargo = true;
                                        break;
                                    }
                                }
                            }

                            if (matchingFleetScreen) {
                                for (FleetMemberAPI member : originSub.getCargo().getMothballedShips().getMembersListCopy()) {
                                    if (!sub.getPlugin().isIllegalOnSubmarket(member, SubmarketPlugin.TransferAction.PLAYER_SELL)) {
                                        hasLegalCargo = true;
                                        break;
                                    }
                                }
                            }

                            if (debug)
                                log("Submarket " + sub.getNameOneLine() + " - matchingCargoScreen " + matchingCargoScreen + " matchingFleetScreen " + matchingFleetScreen + " has Legal cargo " + hasLegalCargo);

                            if (sub.getPlugin().isFreeTransfer()
                                    && whitelist.contains(sub.getSpecId())
                                    && intersect
                                    && hasLegalCargo) {

                                if (debug) log("passed, adding");
                                finalSet.add(sub);
                            }
                        }
                    } else {
                        if (debug) log("Dumb check");
                        //otherwise, do a dumb check
                        for (SubmarketAPI sub : market.getSubmarketsCopy()) {
                            if (sub.getPlugin().isFreeTransfer()
                                    && whitelist.contains(sub.getSpecId())) {

                                if (debug) log("adding " + sub.getNameOneLine());
                                finalSet.add(sub);
                            }
                        }
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + type);
        }

        if (debug) log("final set size " + finalSet.size());

        return new ArrayList<>(finalSet);
    }

    public static List<MarketAPI> getEligibleMarketList(IndEvo_PrivatePort.ShippingContainer container, actionTypes action) {
        Set<MarketAPI> finalSet = new HashSet<>();

        switch (action) {
            case ORIGIN_MARKET:
                // Select {from} (allow any planet with storage with stuff in it)
                for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                    if (market.isHidden()) continue;

                    if (getEligibleSubMarketList(market, container, actionTypes.ORIGIN_SUBMARKET).size() > 0)
                        finalSet.add(market);
                }
                break;

            case TARGET_MARKET:
                //Select {to} (Allow any faction market with unlocked storage)
                for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
                    if (market.isHidden()) continue;

                    if (getEligibleSubMarketList(market, container, actionTypes.TARGET_SUBMARKET).size() > 0)
                        finalSet.add(market);
                }

                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + action);
        }

        return new ArrayList<>(finalSet);
    }
}