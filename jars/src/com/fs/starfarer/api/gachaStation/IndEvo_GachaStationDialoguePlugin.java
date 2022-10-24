package com.fs.starfarer.api.gachaStation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.ListMap;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndEvo_GachaStationDialoguePlugin implements InteractionDialogPlugin {

    public static final String HAS_RESTORED_STATION = "$IndEvo_stationRepaired";
    public static final int RARE_PART_COST_AMT = 100;

    public static final int METALS_REPAIR_COST = 10000;
    public static final int MACHINERY_REPAIR_COST = 1000;
    public static final int PARTS_REPAIR_COST = 4000;

    private enum Option {
        MAIN,
        INITIAL_PAY_TO_RESTORE,
        PARTS_SELECTOR,
        SELECT_SHIPS,
        CONFIRM,
        LEAVE
    }

    protected InteractionDialogAPI dialog;
    protected TextPanelAPI text;
    protected OptionPanelAPI options;

    private List<FleetMemberAPI> selectedShips = new ArrayList<>();
    private float partsToSacrifice = 0f;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.options = dialog.getOptionPanel();
        this.text = dialog.getTextPanel();

        displayDefaultOptions();
    }

    public boolean isRepaired(){
       return dialog.getInteractionTarget().getMemoryWithoutUpdate().getBoolean(HAS_RESTORED_STATION);
    }

    public void setRepaired(){
        dialog.getInteractionTarget().getMemoryWithoutUpdate().set(HAS_RESTORED_STATION, true);
    }

    public void displayDefaultOptions() {
        text.clear();
        addDefaultTooltip();

        OptionPanelAPI opts = options;
        opts.clearOptions();

        boolean isEnabled;
        Option option;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        CargoAPI cargo = playerFleet.getCargo();

        if (isRepaired()){
            addPostRestoreTooltip();

            option = Option.SELECT_SHIPS;
            isEnabled = playerFleet.getFleetData().getNumMembers() > 1;

            opts.addOption("Select ships to sacrifice", option);
            opts.setEnabled(option, isEnabled);
            if(isEnabled) opts.setTooltip(option, "Select ships to offer to the machine, for it may bestow a blessing of silicone and steel made anew");
            else opts.setTooltip(option, "You can not sacrifice the only ship in your fleet, as tempting as it may be");

            float partsAvailable = cargo.getCommodityQuantity(IndEvo_Items.PARTS);
            opts.addSelector("Sacrifice Starship Components", Option.PARTS_SELECTOR, Misc.getHighlightColor(),
                    300f,
                    50f,
                    0f,
                    partsAvailable,
                    ValueDisplayMode.VALUE,
                    partsAvailable >= 1f ?
                            "Offer ship components - know that the residing mind might consider them inferior to the sanctified metal and soul of a true ship."
                            : "You do not have any ship components to sacrifice");

            option = Option.CONFIRM;
            isEnabled = (!selectedShips.isEmpty() || partsToSacrifice > 0) && playerFleet.getCargo().getCommodityQuantity(IndEvo_Items.RARE_PARTS) >= RARE_PART_COST_AMT;

            opts.addOption("Start the machine", option);
            opts.setEnabled(option, isEnabled);
            if(isEnabled) opts.setTooltip(option, "Pray to the mechanic deity to grant upon you a rare print from the deepest parts of the ancient mnemonic relays");
            else {
                opts.setTooltip(option, "You do not have a sacrifice selected or do not have sufficient relic components to offer upon the altar. (Cost: " + RARE_PART_COST_AMT + " Relic Components)");
                opts.setTooltipHighlightColors(option, Misc.getHighlightColor());
                opts.setTooltipHighlights(option, "(Cost: " + RARE_PART_COST_AMT + " Relic Components)");
            }
        } else {
            addPreRestoreTooltip();

            option = Option.INITIAL_PAY_TO_RESTORE;

            isEnabled = cargo.getCommodityQuantity(Commodities.METALS) >= METALS_REPAIR_COST
                    && cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY) >= MACHINERY_REPAIR_COST
                    && cargo.getCommodityQuantity(IndEvo_Items.PARTS) >= PARTS_REPAIR_COST;

            opts.addOption("Restore the machine to function", option);
            opts.setEnabled(option, isEnabled);
            if(isEnabled) opts.setTooltip(option, "Deliver the offerings to the monolithic drop-off zones and perform the initialization rites");
            else opts.setTooltip(option, "Your cargo contains insufficient offerings to restore this temple to its former glory");
        }

        option = Option.LEAVE;
        opts.addOption("Leave", option);
        opts.setShortcut(option, Keyboard.KEY_ESCAPE, false, false, false, true);
    }


    @Override
    public void optionSelected(String optionText, Object optionData) {
        options.clearOptions();
        Option opt = (Option) optionData;

        switch (opt) {
            case MAIN:
                displayDefaultOptions();
                break;
            case INITIAL_PAY_TO_RESTORE:
                text.addPara("As your crew chants the last verses of the inscribed prayers, massive, multi-segmented arms unfold from the altar-like walls and slowly begin transferring the offerings into the multitude of openings formed by their emergence.");
                text.addPara("Billowing red flames erupt from long dead braziers. A cacophony of clicking and grinding accompanies them, echoing through the cathedral-like space in a grotesque mockery of a choir.");
                text.addPara("Your crew watches on as the long dead machine revives itself, heaving in effort as it shakes off the weight of centuries with rasped breath.");

                CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                cargo.removeCommodity(Commodities.METALS, METALS_REPAIR_COST);
                cargo.removeCommodity(Commodities.HEAVY_MACHINERY, MACHINERY_REPAIR_COST);
                cargo.removeCommodity(IndEvo_Items.PARTS, PARTS_REPAIR_COST);

                AddRemoveCommodity.addCommodityLossText(Commodities.METALS, METALS_REPAIR_COST, text);
                AddRemoveCommodity.addCommodityLossText(Commodities.HEAVY_MACHINERY, MACHINERY_REPAIR_COST, text);
                AddRemoveCommodity.addCommodityLossText(IndEvo_Items.PARTS, PARTS_REPAIR_COST, text);

                setRepaired();

                options.addOption("Continue", Option.MAIN);
                break;
            case SELECT_SHIPS:
                break;
            case CONFIRM:
                break;
            case LEAVE:
                dialog.dismiss();
                break;
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {

    }

    @Override
    public void advance(float amount) {
        OptionPanelAPI panel = dialog.getOptionPanel();

        if(!panel.hasOption(Option.CONFIRM)) return;

        partsToSacrifice = panel.getSelectorValue(Option.PARTS_SELECTOR);
        boolean isEnabled = (!selectedShips.isEmpty() || partsToSacrifice > 0) && Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(IndEvo_Items.RARE_PARTS) >= RARE_PART_COST_AMT;
        panel.setEnabled(Option.CONFIRM, isEnabled);
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
        return null;
    }

    public void addDefaultTooltip(){
        text.addPara("Long banners of decaying synth-weave illuminated by chandeliers and wall sconces line the massive landing bays, inspiring awe amongst your crew despite their tattered appearance.");
        text.addPara("Through the omnipresent religious iconography, it becomes clear that the soul contained in the reliquary core is very old - and very not human.");
        text.addPara("\"For in its eternal glory, it shall reward sacrifice with blessings.\"\n");
    }

    public void addPreRestoreTooltip(){
        text.addPara("The central chamber is lined by engraved golden walls detailing the journey of an ancient being, " +
                "constructing a shell befitting its grand image as it extinguishes star after star to fuel itself, " +
                "forever running from a searching golden eye.");

        text.addPara("Your shipboard historian points out some smaller icons depicting robed humans presenting it with strangely specific offerings.");

        Misc.showCost(text, null, null,
                new String[]{Commodities.METALS, Commodities.HEAVY_MACHINERY, IndEvo_Items.PARTS},
                new int[]{METALS_REPAIR_COST, MACHINERY_REPAIR_COST, PARTS_REPAIR_COST});
    }

    public void addPostRestoreTooltip(){

    }

    private Map<String, Float> createHullIdSelection() {
        Map<String, Float> hullMap = new HashMap<>();
        WeightedRandomPicker<ShipHullSpecAPI> picker = new WeightedRandomPicker<>();
        ListMap<ShipHullSpecAPI> specAPIListMap = getHullSizeHullSpecListMap();

        for (ShipAPI.HullSize size : new ShipAPI.HullSize[]{
                ShipAPI.HullSize.FRIGATE,
                ShipAPI.HullSize.DESTROYER,
                ShipAPI.HullSize.CRUISER,
                ShipAPI.HullSize.CAPITAL_SHIP}) {

            picker.addAll(specAPIListMap.get(size.toString()));

            if (picker.isEmpty()) continue;

            ShipHullSpecAPI spec = picker.pick();
            ShipVariantAPI var = Global.getSettings().createEmptyVariant(Misc.genUID(), spec);
            hullMap.put(spec.getHullId(), Global.getFactory().createFleetMember(FleetMemberType.SHIP, var).getDeploymentPointsCost());
            picker.clear();
        }

        return hullMap;
    }

    private ListMap<ShipHullSpecAPI> getHullSizeHullSpecListMap() {
        ListMap<ShipHullSpecAPI> lm = new ListMap<>();
        for (ShipAPI.HullSize size : new ShipAPI.HullSize[]{
                ShipAPI.HullSize.FRIGATE,
                ShipAPI.HullSize.DESTROYER,
                ShipAPI.HullSize.CRUISER,
                ShipAPI.HullSize.CAPITAL_SHIP}) {
            for (ShipHullSpecAPI spec : IndEvo_IndustryHelper.getAllLearnableShipHulls()) {
                if (spec.getHullSize().equals(size)) lm.getList(size.toString()).add(spec);
            }
        }

        return lm;
    }
}
