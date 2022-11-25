package com.fs.starfarer.api.impl.campaign.rulecmd.portRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_PrivatePort;
import com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingManager;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.portRules.IndEvo_ShippingVariables.CURRENT_SHIPPING_CONTAINER;
@Deprecated
public class IndEvo_ShippingCargoSelection extends BaseCommandPlugin implements InteractionDialogPlugin {

    private enum Option {
        MAIN,
        LOAD_CARGO,
        LOAD_SHIPS,
        SET_RECURRENT,
        INSURE,
        CONTINUE,
        BACK
    }

    protected SectorEntityToken entity;
    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;
    protected IndEvo_PrivatePort.ShippingContainer container;
    private int recurrentMonths = 0;

    public static final Logger log = Global.getLogger(IndEvo_ShippingCargoSelection.class);
    boolean debug = false;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        debug = Global.getSettings().isDevMode();

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        if (dialog == null) return false;

        entity = dialog.getInteractionTarget();
        originalPlugin = dialog.getPlugin();
        container = getCurrentShippingContainer(memoryMap.get(MemKeys.LOCAL));

        if (container == null) return false;

        dialog.setPromptText("Configure the shipment");

        dialog.setPlugin(this);
        init(dialog);
        return true;
    }


    @Override
    public void optionSelected(String optionText, Object optionData) {

        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        OptionPanelAPI opts = dialog.getOptionPanel();
        SubmarketAPI originSubmarket = container.getOriginMarket().getSubmarket(container.getOriginSubmarketId());
        SubmarketAPI targetSubmarket = container.getTargetMarket().getSubmarket(container.getTargetSubmarketId());

        boolean showOriginInFleetScreen = originSubmarket.getPlugin().showInFleetScreen();
        boolean showOriginInCargoScreen = originSubmarket.getPlugin().showInCargoScreen();

        boolean showTargetInFleetScreen = targetSubmarket.getPlugin().showInFleetScreen();
        boolean showTargetInCargoScreen = targetSubmarket.getPlugin().showInCargoScreen();

        opts.clearOptions();

        Option option = Option.valueOf(optionData.toString());

        switch (option) {
            case MAIN:
                opts.addOption("Load Cargo", Option.LOAD_CARGO);
                opts.setEnabled(Option.LOAD_CARGO, showTargetInCargoScreen && showOriginInCargoScreen);
                if (!showOriginInCargoScreen)
                    opts.setTooltip(Option.LOAD_CARGO, "The origin Storage does not provide cargo.");
                if (!showTargetInCargoScreen)
                    opts.setTooltip(Option.LOAD_CARGO, "The target Storage does not take cargo.");

                opts.addOption("Load Ships", Option.LOAD_SHIPS);
                opts.setEnabled(Option.LOAD_SHIPS, showTargetInFleetScreen && showOriginInFleetScreen);
                if (!showOriginInFleetScreen)
                    opts.setTooltip(Option.LOAD_SHIPS, "The origin Storage does not provide ships.");
                if (!showTargetInFleetScreen)
                    opts.setTooltip(Option.LOAD_SHIPS, "The target Storage does not take ships.");

                String insuredText = container.isInsured() ? "Yes [x]  No [ ]" : "Yes [ ]  No [x]";
                opts.addOption("Insure the shipment? - " + insuredText, Option.INSURE);

                if (IndEvo_ShippingManager.isFraudTimeout()) {
                    opts.setEnabled(Option.INSURE, false);
                    opts.setTooltip(Option.INSURE, "You have committed insurance fraud and are banned from insuring shipments for another " + IndEvo_ShippingManager.getFraudTimeoutAmt() + " days");
                }

                String recurrentText = recurrentMonths > 0 ? "Shipment interval: every " + recurrentMonths + " months." : "Shipment interval: single delivery.";
                opts.addOption(recurrentText, Option.SET_RECURRENT);

                opts.addOption("Confirm", Option.CONTINUE);
                opts.addOption("Return", Option.BACK);
                opts.setShortcut(Option.BACK, Keyboard.KEY_ESCAPE, false, false, false, true);

                updatePanel();
                break;
            case LOAD_CARGO:
                initLoadCargoSelection(originSubmarket);
            case LOAD_SHIPS:
                initLoadShipsSelection(originSubmarket);
                break;
            case INSURE:
                if (container.isInsured()) {
                    container.setInsured(false);
                } else {
                    container.setInsured(true);
                }
                optionSelected(null, Option.MAIN);
                break;
            case SET_RECURRENT:
                incrementRecurrentMonths();
                optionSelected(null, Option.MAIN);
                break;
            case CONTINUE:
                finalizeSelection(memory);
                break;
            case BACK:
                returnToMenu();
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + option);
        }
    }

    private void incrementRecurrentMonths() {
        switch (recurrentMonths) {
            case 0:
            case 1:
            case 2:
                recurrentMonths++;
                break;
            case 3:
                recurrentMonths += 3;
                break;
            case 6:
                recurrentMonths += 6;
                break;
            case 12:
                recurrentMonths = 0;
                break;
        }

        container.setRecurrentEveryMonths(recurrentMonths);
    }

    private void updatePanel() {
        TextPanelAPI panel = dialog.getTextPanel();
        panel.clear();

        addCargoTooltip(panel);
        addContractTooltip(panel);
    }

    private void addCargoTooltip(TextPanelAPI panel) {
        SubmarketAPI targetsubmarket = container.getTargetMarket().getSubmarket(container.getTargetSubmarketId());

        boolean showInFleetScreen = targetsubmarket.getPlugin().showInFleetScreen();
        boolean showInCargoScreen = targetsubmarket.getPlugin().showInCargoScreen();
        float pad = 5f;
        CargoAPI cargo = container.getTargetCargo();

        panel.addPara("Shipment information:");
        panel.setFontSmallInsignia();

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.addPara("Cargo to be shipped:");

        TooltipMakerAPI tooltip = panel.beginTooltip();

        if (showInCargoScreen && !cargo.isEmpty()) {

            tooltip.beginGridFlipped(300, 1, 100f, 10f);
            //panel.beginGrid(150f, 1);
            int i = 0;
            cargo.sort();
            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                tooltip.addToGrid(0, i, stack.getDisplayName(), (int) stack.getSize() + " units");
                i++;

                if (i > 4) {
                    break;
                }
            }
            tooltip.addGrid(pad);
            if (i > 4 && cargo.getStacksCopy().size() > 5) {
                tooltip.addPara("... and " + (cargo.getStacksCopy().size() - 5) + " additional types of cargo.", 10f);
            }
        } else if (showInCargoScreen) {
            panel.addPara("    Select any items you wish to transfer.");
        } else {
            panel.addPara("    None - The destination storage does not allow items.");
        }

        panel.addTooltip();

        panel.addPara("Ship Hulls to be transported:");

        tooltip = panel.beginTooltip();
        if (showInFleetScreen && !cargo.getMothballedShips().getMembersListCopy().isEmpty()) {

            tooltip.beginGridFlipped(300, 1, 30f, 3f);
            //panel.beginGrid(150f, 1);
            int i = 0;

            Map<ShipHullSpecAPI, Integer> shipMap = new HashMap<>();
            for (FleetMemberAPI ship : cargo.getMothballedShips().getMembersListCopy()) {
                ShipHullSpecAPI hs = ship.getHullSpec();
                if (shipMap.containsKey(hs)) {
                    shipMap.put(hs, shipMap.get(hs) + 1);
                } else {
                    shipMap.put(hs, 1);
                }
            }

            for (Map.Entry<ShipHullSpecAPI, Integer> entry : shipMap.entrySet()) {
                tooltip.addToGrid(0, i, entry.getKey().getHullNameWithDashClass() + " ship hull", entry.getValue() + "");
                i++;

                if (i > 4) {
                    break;
                }
            }
            tooltip.addGrid(pad);
            if (i > 4 && shipMap.size() > 5) {
                tooltip.addPara("... and " + (shipMap.size() - 5) + " additional ships.", 3f);
            }
        } else if (showInFleetScreen) {
            panel.addPara("    Select any ships you wish to transfer.");
        } else {
            panel.addPara("    None - The destination storage does not allow ships.");
        }

        panel.addTooltip();

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.setFontInsignia();
    }

    private void addContractTooltip(TextPanelAPI panel) {

        SubmarketAPI originsubmarket = container.getOriginMarket().getSubmarket(container.getOriginSubmarketId());
        SubmarketAPI targetsubmarket = container.getTargetMarket().getSubmarket(container.getTargetSubmarketId());
        float pad = 5f;

        String originNameString = container.getOriginMarket().getName();
        String targetNameString = container.getTargetMarket().getName();

        CargoAPI cargo = container.getTargetCargo();
        int recurrentMonths = container.getRecurrentEveryMonths();

        panel.addPara("Contract information:");
        panel.setFontSmallInsignia();
        panel.addParagraph("-----------------------------------------------------------------------------");
        TooltipMakerAPI tooltip = panel.beginTooltip();

        tooltip.addPara("From: " + originNameString + ", " + originsubmarket.getNameOneLine(),
                2f,
                container.getOriginMarket().getTextColorForFactionOrPlanet(),
                originNameString);

        tooltip.addPara("To:     " + targetNameString + ", " + targetsubmarket.getNameOneLine() + " (" + container.getTransportDistance() + " LY)",
                2f,
                container.getTargetMarket().getTextColorForFactionOrPlanet(),
                targetNameString);

        String recurrent = recurrentMonths == 0 ? "As a single delivery." : "Once every " + recurrentMonths + " months,";
        tooltip.addPara(recurrent, pad, Misc.getHighlightColor(), recurrentMonths + "");

        String insurance = container.isInsured() ? "Insured shipment - %s refund on loss (%s)." : "No Insurance.";
        tooltip.addPara(insurance, pad, Misc.getHighlightColor(), new String[]{Misc.getDGSCredits(container.getValue(cargo)), (int) Math.round(container.getInsuranceRefundPart() * 100) + "%"});

        tooltip.addPara("Total cost (rounded): %s", pad, Misc.getPositiveHighlightColor(), Misc.getDGSCredits(container.getTotalShippingCost(cargo)));
        tooltip.beginGridFlipped(300f, 1, 100f, 3f);
        tooltip.addToGrid(0, 0, "Base fee", Misc.getDGSCredits(1000));
        tooltip.addToGrid(0, 1, "Base cargo cost", Misc.getDGSCredits(container.getBaseStackCargoSpaceCost(cargo)));
        tooltip.addToGrid(0, 2, "Base ship cost", Misc.getDGSCredits(container.getBaseAbstractShipSpaceCost(cargo)));
        tooltip.addToGrid(0, 3, "Distance multiplier", "x" + container.getLYMult());
        if (container.isInsured()) {
            tooltip.addToGrid(0, 4, "Insurance Cost", Misc.getDGSCredits(container.getInsuranceCost(cargo)));
        }
        tooltip.addGrid(pad);

        panel.addTooltip();

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.setFontInsignia();
    }

    //showCargoPickerDialog(String title, String okText, String cancelText,
    //			boolean small, float textPanelWidth, CargoAPI cargo, CargoPickerListener listener);

    private void initLoadCargoSelection(final SubmarketAPI submarket) {

        final float width = 310;

        final CargoAPI copy = Global.getFactory().createCargo(false);
        SubmarketAPI sub = getTargetSubmarket();

        for (CargoStackAPI stack : submarket.getCargo().getStacksCopy()) {
            if (!sub.getPlugin().isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_SELL)) {
                copy.addFromStack(stack);
            }
        }

        dialog.showCargoPickerDialog("Select items to transport", "Confirm", "Cancel", false, width, copy, new CargoPickerListener() {
            public void pickedCargo(CargoAPI cargo) {

                if (!cargo.isEmpty()) {
                    cargo.sort();
                    CargoAPI containerCargo = container.getTargetCargo();
                    containerCargo.clear();

                    for (CargoStackAPI stack : cargo.getStacksCopy()) {
                        containerCargo.addFromStack(stack);
                    }
                } else {
                    container.getTargetCargo().clear();
                }

                optionSelected(null, Option.MAIN);
            }

            public void cancelledCargoSelection() {
                optionSelected(null, Option.MAIN);
            }

            public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
                float opad = 10f;
                panel.addImage(Global.getSector().getPlayerFaction().getLogo(), width * 1f, 3f);
                //panel.addImage("cargo_loading", width * 1f, 3f);
                panel.addPara("Select the cargo you would like to ship. Items that are illegal in the target storage are not displayed.", opad);

                panel.setGridFontDefault();
                panel.beginGridFlipped(300, 1, 100f, 10f);

                int i = 0;
                float total = 0f;

                for (CargoStackAPI stack : cargo.getStacksCopy()) {
                    float cost = container.getCostForStack(stack);
                    total += cost;

                    if (i < 10) {
                        panel.addToGrid(0, i, (int) stack.getSize() + " units " + stack.getDisplayName(), Misc.getDGSCredits(cost));
                        i++;
                    }
                }
                panel.addGrid(opad);
                if (i > 9) {
                    panel.addPara("... and " + (cargo.getStacksCopy().size() - 10) + " more.", 3f);
                }
                panel.setParaFontDefault();

                panel.addPara("Total cost: %s", opad, Misc.getPositiveHighlightColor(), Misc.getDGSCredits(total));
            }
        });
    }

    private void initLoadShipsSelection(SubmarketAPI submarket) {
        List<FleetMemberAPI> fleetMemberList = new ArrayList<>();
        SubmarketAPI sub = getTargetSubmarket();

        for (FleetMemberAPI member : submarket.getCargo().getMothballedShips().getMembersListCopy()) {
            if (!sub.getPlugin().isIllegalOnSubmarket(member, SubmarketPlugin.TransferAction.PLAYER_SELL)) {
                fleetMemberList.add(member);
            }
        }
        dialog.showFleetMemberPickerDialog("Select ships to transport", "Confirm", "Cancel", 3, 7, 88f, true, true, fleetMemberList, new FleetMemberPickerListener() {

            @Override
            public void pickedFleetMembers(List<FleetMemberAPI> members) {
                container.getTargetCargo().getMothballedShips().clear();
                if (members != null && !members.isEmpty()) {
                    for (FleetMemberAPI member : members) {
                        container.getTargetCargo().getMothballedShips().addFleetMember(member);
                    }
                } else {
                    container.getTargetCargo().getMothballedShips().clear();
                }

                optionSelected(null, Option.MAIN);
            }

            @Override
            public void cancelledFleetMemberPicking() {
                optionSelected(null, Option.MAIN);
            }
        });

        optionSelected(null, Option.MAIN);
    }

    private SubmarketAPI getTargetSubmarket() {
        MarketAPI targetMarket = container.getTargetMarket();
        return targetMarket.getSubmarket(container.getTargetSubmarketId());
    }

    private void returnToMenu() {
        dialog.setPlugin(originalPlugin);
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        FireAll.fire("IndEvo_IntitiateShippingMenu", dialog, memoryMap, "IndEvo_ShippingOptions");
    }

    public void finalizeSelection(MemoryAPI memory) {
        /*//remove the finalized ships and cargo from the submarket
        for (CargoStackAPI c : container.getTargetCargo().getStacksCopy()){
            sub.getCargo().removeStack(c);
        }

        for(FleetMemberAPI m : container.getTargetCargo().getMothballedShips().getMembersListCopy()){
            sub.getCargo().getMothballedShips().removeFleetMember(m);
        }*/

        //add it to the wonderful list
        IndEvo_ShippingManager srm = (IndEvo_ShippingManager) Global.getSector().getMemoryWithoutUpdate().get(IndEvo_ShippingManager.KEY);
        if (srm != null) srm.addContainer(container);
        else log.info("could not find IndEvo_ShippingManager");

        returnToMenu();
    }

    public static IndEvo_PrivatePort.ShippingContainer getCurrentShippingContainer(MemoryAPI memory) {
        if (memory.contains(CURRENT_SHIPPING_CONTAINER)) {
            return (IndEvo_PrivatePort.ShippingContainer) memory.get(CURRENT_SHIPPING_CONTAINER);
        } else {
            return new IndEvo_PrivatePort.ShippingContainer();
        }
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        optionSelected(null, Option.MAIN);
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
        if (optionData != null) {
            updatePanel();
        }
    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {

    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return memoryMap;
    }
}
