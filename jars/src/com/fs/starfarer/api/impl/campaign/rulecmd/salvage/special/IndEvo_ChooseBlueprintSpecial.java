package com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.fs.starfarer.api.IndEvo_IndustryHelper.stripShipNoCargo;
import static com.fs.starfarer.api.impl.campaign.ids.Items.SHIP_BP;

public class IndEvo_ChooseBlueprintSpecial extends BaseSalvageSpecial {
    //choose a blueprint from a selection of 2-4 hulls
    //ref: SalvageSpecialAssigner, SleeperPodsSpecial,

    public static final String SELECT = "select";
    public static final String NOT_NOW = "not_now";

    public static class ChooseBlueprintSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public final ShipAPI.HullSize hullSize;
        public final Set<String> hullIds;

        public ChooseBlueprintSpecialData(ShipAPI.HullSize hullSize, int amt) {
            this.hullSize = hullSize;
            this.hullIds = createHullIdSelection(hullSize, amt);
        }

        private Set<String> createHullIdSelection(ShipAPI.HullSize hullsize, int amount) {
            Set<String> hullSet = new HashSet<>();
            WeightedRandomPicker<ShipHullSpecAPI> picker = new WeightedRandomPicker<>();

            for (ShipHullSpecAPI spec : IndEvo_IndustryHelper.getAllRareShipHulls()) {
                if (spec.getHullSize().equals(hullsize)) picker.add(spec);
            }

            for (int i = 0; i < amount; i++) {
                hullSet.add(picker.pickAndRemove().getHullId());
            }

            return hullSet;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new IndEvo_ChooseBlueprintSpecial();
        }
    }

    private ChooseBlueprintSpecialData data;

    public IndEvo_ChooseBlueprintSpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);

        data = (ChooseBlueprintSpecialData) specialData;
        ShipAPI.HullSize size = data.hullSize;
        String sizeStr = "shit broke";

        switch (size) {
            case FRIGATE:
            case DESTROYER:
            case CRUISER:
                sizeStr = size.name().toLowerCase();
                break;
            case CAPITAL_SHIP:
                sizeStr = "capital ship";
                break;
        }

        addText("Combing through long broken equipment, your salvage crews " +
                "find a semi-functional blueprint database. It seems to contain a selection of " + sizeStr + " - sized hulls. You might be able to download one of them onto the slotted blank.");

        displayBaseOptions();
    }

    public void displayBaseOptions() {
        options.clearOptions();
        options.addOption("Attempt the download", SELECT);
        options.addOption("Not now", NOT_NOW);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (SELECT.equals(optionData)) initBlueprintHullPicker();
        else if (NOT_NOW.equals(optionData)) {
            addText("You leave. " +
                    "The database remains behind, to be used or disassembled for parts.");

            setEndWithContinue(false);
            setShowAgain(true);
            setDone(true);
        }
    }

    private void initBlueprintHullPicker() {
        List<FleetMemberAPI> fleetMemberList = new ArrayList<>();

        for (String id : data.hullIds) {
            FleetMemberAPI ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, id + "_Hull");
            stripShipNoCargo(ship);
            fleetMemberList.add(ship);
        }

        dialog.showFleetMemberPickerDialog("Select a blueprint", "Confirm", "Cancel", 1, Math.max(data.hullIds.size(), 4), 88f, true, false, fleetMemberList, new FleetMemberPickerListener() {

            @Override
            public void pickedFleetMembers(List<FleetMemberAPI> members) {
                if (members != null && !members.isEmpty()) {

                    SpecialItemData data = new SpecialItemData(SHIP_BP, members.get(0).getHullId());
                    Global.getSector().getPlayerFleet().getCargo().addSpecial(data, 1);

                    addText("The download successfully concludes. " +
                            "However, before you have a chance to find another blank, the database shorts out and finally dies for good.\n");

                    String s = "Added: 1x " + members.get(0).getHullSpec().getHullNameWithDashClass() + " Blueprint";
                    addSmallText(s);
                    highlightLastPara(Misc.getPositiveHighlightColor(), s);

                    setDone(true);
                    setShowAgain(false);
                    setEndWithContinue(false);

                    getOptions().clearOptions();
                    getOptions().addOption("Continue", null);

                } else {
                    cancelledFleetMemberPicking();
                }
            }

            @Override
            public void cancelledFleetMemberPicking() {
                addText("You abort the download. " +
                        "The database emits a horrifying crunching noise - but seems to remain functional.");

                displayBaseOptions();
            }
        });
    }

    public OptionPanelAPI getOptions() {
        return options;
    }

    public void addSmallText(String s) {
        text.setFontSmallInsignia();
        addText(s);
        text.setFontInsignia();
    }

    public void highlightLastPara(Color colour, String highlights) {
        text.highlightInLastPara(colour, highlights);
    }

    private MemoryAPI getMemory() {
        return BaseCommandPlugin.getEntityMemory(dialog.getPlugin().getMemoryMap());
    }

}
