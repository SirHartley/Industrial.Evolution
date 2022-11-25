package com.fs.starfarer.api.impl.campaign.rulecmd.portRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_PrivatePort;
import com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingManager;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.portRules.IndEvo_ShippingVariables.*;
@Deprecated
public class IndEvo_OptionStartsWith_Shipping extends BaseCommandPlugin {

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_OptionStartsWith_Shipping.class).info(Text);
        }
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);

        String substring = params.get(0).getString(memoryMap);
        String option = memory.getString("$option");
        String priorRuleId = params.get(1).getString(memoryMap);

        if (option == null) return false;

        //update Shipping Container inventory
        if (option.startsWith(actionTypes.ORIGIN_MARKET.toString())) {
            String marketID = priorRuleId.substring(actionTypes.ORIGIN_MARKET.toString().length());

            MarketAPI market = Global.getSector().getEconomy().getMarket(marketID);
            IndEvo_PrivatePort.ShippingContainer currentContainer = IndEvo_CreateShippingSelectionList.getCurrentShippingContainer(memory);

            if (market != null) {
                currentContainer.setOriginMarket(market);
            }

            memory.set(CURRENT_SHIPPING_CONTAINER, currentContainer, EXPIRE_TIME);

        } else if (option.startsWith(actionTypes.TARGET_MARKET.toString())) {
            String marketID = priorRuleId.substring(actionTypes.TARGET_MARKET.toString().length());

            MarketAPI market = Global.getSector().getEconomy().getMarket(marketID);
            IndEvo_PrivatePort.ShippingContainer currentContainer = IndEvo_CreateShippingSelectionList.getCurrentShippingContainer(memory);

            if (market != null) {
                currentContainer.setTargetMarket(market);
            }

            memory.set(CURRENT_SHIPPING_CONTAINER, currentContainer, EXPIRE_TIME);

        } else if (option.startsWith(actionTypes.ORIGIN_SUBMARKET.toString())) {
            String submarketId = priorRuleId.substring(actionTypes.ORIGIN_SUBMARKET.toString().length());
            IndEvo_PrivatePort.ShippingContainer currentContainer = IndEvo_CreateShippingSelectionList.getCurrentShippingContainer(memory);
            currentContainer.setOriginSubmarketId(submarketId);

            memory.set(CURRENT_SHIPPING_CONTAINER, currentContainer, EXPIRE_TIME);

        } else if (option.startsWith(actionTypes.TARGET_SUBMARKET.toString())) {
            String submarketId = priorRuleId.substring(actionTypes.TARGET_SUBMARKET.toString().length());
            IndEvo_PrivatePort.ShippingContainer currentContainer = IndEvo_CreateShippingSelectionList.getCurrentShippingContainer(memory);
            currentContainer.setTargetSubmarketId(submarketId);

            memory.set(CURRENT_SHIPPING_CONTAINER, currentContainer, EXPIRE_TIME);

        } else if (option.startsWith(actionTypes.ALL_CONTAINERS.toString())) {
            String containerID = priorRuleId.substring(actionTypes.ALL_CONTAINERS.toString().length());
            IndEvo_PrivatePort.ShippingContainer currentContainer = IndEvo_ShippingManager.getCurrentInstance().getContainer(containerID);

            memory.set(CURRENT_SHIPPING_CONTAINER, currentContainer, EXPIRE_TIME);
        }

        return option.startsWith(substring);
    }
}
