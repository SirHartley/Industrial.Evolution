package com.fs.starfarer.api.impl.campaign.rulecmd.portRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
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

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.rulecmd.portRules.IndEvo_ShippingVariables.CURRENT_SHIPPING_CONTAINER;
@Deprecated
public class IndEvo_ShippingCargoRemovalSelection extends BaseCommandPlugin implements InteractionDialogPlugin {

    private enum Option {
        MAIN,
        CANCEL,
        INSURE,
        DELAY,
        DELAY_CONFIRM,
        DELAY_CANCEL,
        BACK
    }

    protected SectorEntityToken entity;
    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;
    protected IndEvo_PrivatePort.ShippingContainer container;
    private int delay = 0;

    public static Logger log = Global.getLogger(IndEvo_ShippingCargoRemovalSelection.class);
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

        dialog.setPromptText("Configure the contract");

        dialog.setPlugin(this);
        init(dialog);
        optionSelected(null, Option.MAIN);
        return true;
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        OptionPanelAPI opts = dialog.getOptionPanel();

        opts.clearOptions();

        Option option = Option.valueOf(optionData.toString());

        switch (option) {
            case MAIN:
                opts.addOption("Cancel the Contract", Option.CANCEL);
                opts.addOptionConfirmation(Option.CANCEL, "Cancelling the contract is not reversible - are you sure?", "Confirm", "Return");

                if (!container.isInsured()) {
                    opts.addOption("Insure the Contract", Option.INSURE);
                    opts.addOptionConfirmation(Option.INSURE, "Insure future shipments for " + Misc.getDGSCredits(container.getInsuranceCost(container.getTargetCargo())) + "? Insurance costs will be added to future shipment fees.", "Confirm", "Return");

                    if (IndEvo_ShippingManager.isFraudTimeout()) {
                        opts.setEnabled(Option.INSURE, false);
                        opts.setTooltip(Option.INSURE, "You have committed insurance fraud and are banned from insuring shipments for another " + IndEvo_ShippingManager.getFraudTimeoutAmt() + " days");
                    }
                } else {
                    opts.addOption("Cancel insurance on future shipments", Option.INSURE);
                }

                String delstr = getDelay() == 0 ? "Delay the next shipment" : "Delay by " + getDelayDayStr();
                opts.addOption(delstr, Option.DELAY);
                opts.setEnabled(Option.DELAY, !container.isDelayed());
                if (container.isDelayed())
                    opts.setTooltip(Option.DELAY, "Cancel the current delay before setting a new one.");

                if (delay != 0 && !container.isDelayed()) opts.addOption("Confirm new delay", Option.DELAY_CONFIRM);

                /*opts.setEnabled(Option.DELAY_CONFIRM, delay != 0 && !container.isDelayed());
                if (delay == 0) opts.setTooltip(Option.DELAY_CONFIRM, "Set a new delay to confirm.");
                if (container.isDelayed()) opts.setTooltip(Option.DELAY_CONFIRM, "Cancel the current delay before setting a new one.");*/

                opts.addOption("Cancel the current delay", Option.DELAY_CANCEL);
                opts.setEnabled(Option.DELAY_CANCEL, container.isDelayed());
                if (!container.isDelayed()) opts.setTooltip(Option.DELAY_CANCEL, "Shipment is not delayed.");

                opts.addOption("Return", Option.BACK);
                opts.setShortcut(Option.BACK, Keyboard.KEY_ESCAPE, false, false, false, true);

                updatePanel();
                break;

            case CANCEL:
                container.setMarkedForRemoval(true);
                returnToMenu();
                break;
            case INSURE:

                if (container.isInsured()) {
                    container.setInsured(false);
                } else {
                    container.setInsured(true);
                }

                optionSelected(null, Option.MAIN);
                break;
            case DELAY:
                incrementRecurrentMonths();
                optionSelected(null, Option.MAIN);
                break;
            case DELAY_CONFIRM:
                container.setDelayByDays(getDelay());
                optionSelected(null, Option.MAIN);
                break;
            case DELAY_CANCEL:
                container.setDelayByDays(0);
                optionSelected(null, Option.MAIN);
                break;
            case BACK:
                returnToMenu();
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + option);
        }
    }

    private int getDelay() {
        List<Integer> intList = new ArrayList<>(Arrays.asList(0, 14, 31, 62, 93));
        return intList.get(delay);
    }

    private String getDelayDayStr() {

        int delay = container.isDelayed() ? container.getDelayByDays() : getDelay();

        int pt1 = delay >= 31 ? (int) Math.ceil(delay / 31f) : getDelay();
        String pt2 = delay < 31 ? "days" : "months";

        return pt1 + " " + pt2;
    }

    private void incrementRecurrentMonths() {
        if (delay > 3) {
            delay = 0;
        } else {
            delay++;
        }
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

            tooltip.beginGridFlipped(300, 1, 80f, 10f);
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
            panel.addPara("    None - No items selected.");
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
            panel.addPara("    None - No ships selected.");
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

        String delay = container.isDelayed() ? "Shipment delayed by %s." : "No delay.";
        tooltip.addPara(delay, pad, Misc.getHighlightColor(), getDelayDayStr());

        tooltip.addPara("Total cost (rounded): %s", pad, Misc.getPositiveHighlightColor(), Misc.getDGSCredits(container.getTotalShippingCost(cargo)));
        tooltip.beginGridFlipped(300, 1, 60f, 3f);
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

    private void returnToMenu() {
        dialog.setPlugin(originalPlugin);
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        FireAll.fire("IndEvo_IntitiateShippingMenu", dialog, memoryMap, "IndEvo_ShippingOptions");
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
