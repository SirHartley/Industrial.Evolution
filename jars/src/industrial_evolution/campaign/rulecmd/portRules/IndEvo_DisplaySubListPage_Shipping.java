package com.fs.starfarer.api.impl.campaign.rulecmd.portRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_PrivatePort;
import com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.portRules.IndEvo_ShippingVariables.*;
@Deprecated
public class IndEvo_DisplaySubListPage_Shipping extends BaseCommandPlugin {

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_DisplaySubListPage_Shipping.class).info(Text);
        }
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        OptionPanelAPI opts = dialog.getOptionPanel();

        if (memory.get(CURRENT_LIST) == null || memory.getString(ACTION_TYPE) == null) {
            return false;
        }

        int pageNumber = Integer.parseInt(params.get(0).getString(memoryMap));
        actionTypes type = actionTypes.valueOf(memory.getString(ACTION_TYPE));

        List<List<Object>> splitList = (List<List<Object>>) memory.get(CURRENT_LIST);

        debugMessage("Load Page: " + pageNumber);
        debugMessage("Array has amount: " + splitList.size());
        debugMessage("current action " + type.toString());

        opts.clearOptions();

        for (Object o : splitList.get(pageNumber - 1)) {
            if (o instanceof MarketAPI) {
                generateMarketTooltip((MarketAPI) o, type, dialog);
            } else if (o instanceof SubmarketAPI) {
                generateSubMarketTooltip((SubmarketAPI) o, type, dialog);
            } else if (o instanceof IndEvo_PrivatePort.ShippingContainer) {
                generateContainerTooltip((IndEvo_PrivatePort.ShippingContainer) o, type, dialog);
            } else {
                throw new IllegalArgumentException("Unexpected class hullSize for list creation : " + o.getClass());
            }
        }

        //id
        //IndEvo_shippingSelector_LoadCurrent
        //IndEvo_shippingSelector_TriggerNext
        //IndEvo_shippingSelector_TriggerPrevious

        opts.addOption("Next Page", "IndEvo_shippingSelector_TriggerNext");
        opts.setEnabled("IndEvo_shippingSelector_TriggerNext", pageNumber < splitList.size());
        opts.addOption("Previous Page", "IndEvo_shippingSelector_TriggerPrevious");
        opts.setEnabled("IndEvo_shippingSelector_TriggerPrevious", pageNumber > 1);
        opts.addOption("Return", "IndEvo_IntitiateShippingMenu");
        opts.setShortcut("IndEvo_IntitiateShippingMenu", Keyboard.KEY_ESCAPE, false, false, false, false);

        return true;
    }

    private void generateContainerTooltip(IndEvo_PrivatePort.ShippingContainer container, actionTypes action, InteractionDialogAPI dialog) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        String optionId = action + container.getId();

        String target = container.getTargetMarket().getName();
        String origin = container.getOriginMarket().getName();
        String stackStr = IndEvo_ShippingFleetAssignmentAI.EconomyRouteData.getCargoList(container.getTargetCargo());

        String text = origin + "  to  " + target + " (" + stackStr + ")";

        opts.addOption(text, optionId, container.getOriginMarket().getTextColorForFactionOrPlanet(), null);
        /*sadly, in-text colouring doesn't seem to work.
        opts.setTooltipHighlights(optionId, stats.getLevel() + "", personality);
        opts.setTooltipHighlightColors(optionId, hl, colourByPersonality.get(personality));*/

    }

    private void generateMarketTooltip(MarketAPI market, actionTypes action, InteractionDialogAPI dialog) {
        OptionPanelAPI opts = dialog.getOptionPanel();
        String optionId = action + market.getId();

        String starSystem = market.getStarSystem().getBaseName();
        String marketName = market.getName();
        String factionName = market.getFaction().getDisplayName();
        String sizeDesc = "Size " + market.getSize();

        String text = starSystem + " - " + marketName + " (" + sizeDesc + ", " + factionName + ")";

        opts.addOption(text, optionId, market.getTextColorForFactionOrPlanet(), null);
        /*sadly, in-text colouring doesn't seem to work.
        opts.setTooltipHighlights(optionId, stats.getLevel() + "", personality);
        opts.setTooltipHighlightColors(optionId, hl, colourByPersonality.get(personality));*/

    }

    private void generateSubMarketTooltip(SubmarketAPI submarket, actionTypes action, InteractionDialogAPI dialog) {
        Color hl = Misc.getHighlightColor();
        OptionPanelAPI opts = dialog.getOptionPanel();

        String optionId = action + submarket.getSpecId();
        int cargoItemAmount = submarket.getCargo().getStacksCopy().size();
        int shipItemAmount = submarket.getCargo().getMothballedShips().getMembersListCopy().size();

        String name = submarket.getNameOneLine();
        String cargoString = cargoItemAmount > 0 ? cargoItemAmount + " items" : "";
        String shipString = shipItemAmount > 0 ? shipItemAmount + " ships" : "";
        String and = shipItemAmount > 0 && cargoItemAmount > 0 ? " and " : "";
        String contains = shipItemAmount > 0 || cargoItemAmount > 0 ? ", contains " : "";

        String text = name + contains + cargoString + and + shipString;
        Color color = submarket.getFaction().getId().equals("player") ? Global.getSector().getPlayerFaction().getBrightUIColor() : submarket.getFaction().getColor();

        opts.addOption(text, optionId, color, null);

        /*sadly, in-text colouring doesn't seem to work.
        opts.setTooltipHighlights(optionId, stats.getLevel() + "", personality);
        opts.setTooltipHighlightColors(optionId, hl, colourByPersonality.get(personality));*/
    }

}