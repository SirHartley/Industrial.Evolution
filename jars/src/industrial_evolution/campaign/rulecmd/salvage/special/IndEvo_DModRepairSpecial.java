package industrial_evolution.campaign.rulecmd.salvage.special;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.DModManager.getNumNonBuiltInDMods;
import static industrial_evolution.industries.IndEvo_dryDock.getBaseShipHullSpec;
import static industrial_evolution.industries.IndEvo_dryDock.getListNonBuiltInDMods;

public class IndEvo_DModRepairSpecial extends BaseSalvageSpecial {

    /*Repair a certain amount of D-Mods by sacrificing a nanoforge
        No nanoforge dModRemovalAmt D-Mods removed
        Sacrifice a Degraded NF: double amt
        Sacrifice a Pristine NF: quadruple amt
        Choose ships (one or multiple)
        Remove at random from the ships
        */

    public static final String SELECT_NONE = "select_n";
    public static final String SELECT_DEGRADED = "select_d";
    public static final String SELECT_PRISTINE = "select_p";
    public static final String NOT_NOW = "not_now";

    public static final int PRISTINE_ADDITION = 3;
    public static final int CORRUPTED_ADDITION = 1;
    public String selectedForge = "";

    public static class DModRepairSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public final int dModRemovalAmt;

        public DModRepairSpecialData(int dModRemovalAmt) {
            this.dModRemovalAmt = dModRemovalAmt;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new IndEvo_DModRepairSpecial();
        }
    }

    private IndEvo_DModRepairSpecial.DModRepairSpecialData data;

    public IndEvo_DModRepairSpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);
        data = (IndEvo_DModRepairSpecial.DModRepairSpecialData) specialData;
        if (data.dModRemovalAmt == 0) initNothing();

        String shape = "rather bad";
        if (data.dModRemovalAmt > 1f) shape = "passable";
        if (data.dModRemovalAmt > 2f) shape = "surprisingly good";

        addText("While making a preliminary assessment, your salvage crews " +
                "find an old automated repair facility. It appears to be in " + shape + " shape." +
                "\nThe assembly is missing a Nanoforge to produce more specialized parts.");

        displayBaseOptions();
    }


    public void displayBaseOptions() {
        options.clearOptions();

        boolean hasDModFleetMember = false;
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (DModManager.getNumNonBuiltInDMods(member.getVariant()) > 0) {
                hasDModFleetMember = true;
                break;
            }
        }

        if (hasDModFleetMember) {
            addText("It might be able to restore some of the permanent damage on your ships.");

            options.addOption("Go ahead without a Nanoforge", SELECT_NONE);

            options.addOption("Use a Corrupted Nanoforge", SELECT_DEGRADED);
            if (playerFleet.getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(Items.CORRUPTED_NANOFORGE, null)) < 1) {
                options.setTooltip(SELECT_DEGRADED, "You do not have a Corrupted Nanoforge");
                options.setEnabled(SELECT_DEGRADED, false);
            } else
                options.setTooltip(SELECT_DEGRADED, "This will not destroy the Corrupted Nanoforge.");
            //options.addOptionConfirmation(SELECT_DEGRADED, "This will destroy the Corrupted Nanoforge - are you sure?", "Confirm", "Return");

            options.addOption("Use a Pristine Nanoforge", SELECT_PRISTINE);
            if (playerFleet.getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(Items.PRISTINE_NANOFORGE, null)) < 1) {
                options.setTooltip(SELECT_PRISTINE, "You do not have a Pristine Nanoforge");
                options.setEnabled(SELECT_PRISTINE, false);
            } else
                options.setTooltip(SELECT_PRISTINE, "This will not destroy the Pristine Nanoforge");
            //options.addOptionConfirmation(SELECT_PRISTINE, "This will destroy the Pristine Nanoforge - are you sure?", "Confirm", "Return");

            options.addOption("Not now", NOT_NOW);
        } else {
            addText("You do not have any ships with permanent damage that would need restoration.");
            options.addOption("Leave", NOT_NOW);
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (SELECT_NONE.equals(optionData)) {
            selectedForge = "";
            initHullPicker();
        } else if (SELECT_DEGRADED.equals(optionData)) {
            selectedForge = Items.CORRUPTED_NANOFORGE;
            initHullPicker();
        } else if (SELECT_PRISTINE.equals(optionData)) {
            selectedForge = Items.PRISTINE_NANOFORGE;
            initHullPicker();
        } else if (NOT_NOW.equals(optionData)) {
            addText("You leave. " +
                    "The silent machinery remains behind, to be used or disassembled for parts.");
            setEndWithContinue(false);
            setShowAgain(true);
            setDone(true);
        }
    }

    private void initHullPicker() {
        List<FleetMemberAPI> fleetMemberList = new ArrayList<>();

        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (DModManager.getNumNonBuiltInDMods(member.getVariant()) > 0) fleetMemberList.add(member);
        }

        int rows = fleetMemberList.size() > 8 ? (int) Math.ceil(fleetMemberList.size() / 8f) : 1;
        int cols = Math.min(fleetMemberList.size(), 8);
        cols = Math.max(cols, 4);

        dialog.showFleetMemberPickerDialog("Select Ships to repair", "Confirm", "Cancel", rows,
                cols, 88f, true, true, fleetMemberList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {

                            addText("The docks go to work as if they had not been abandoned for decades, removing and replacing damaged " +
                                    "parts with surgical presicion. One has to wonder why installations like this are no longer present on every planet in the sector." +
                                    "\n\nThe answer to this is revealed a few minutes after the restorations have finished, " +
                                    "as the entire assemly suddenly engages in rapid unscheduled disassembly. Ironically, it does not seem to be the most reliable piece of tech.\n\n");

                            int amt = getData().dModRemovalAmt;
                            String selectedForge = getSelectedForge();

                            if (!selectedForge.isEmpty()) {
                                if (selectedForge.equals(Items.CORRUPTED_NANOFORGE)) amt += CORRUPTED_ADDITION;
                                else if (selectedForge.equals(Items.PRISTINE_NANOFORGE)) amt += PRISTINE_ADDITION;
                            }

                            WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>(getRandom());
                            picker.addAll(members);
                            Map<FleetMemberAPI, Integer> removedMap = new HashMap<>();

                            for (int i = 0; i < amt; i++) {
                                FleetMemberAPI member = picker.pick();
                                removeOneDMod(member);
                                addOrIncrement(removedMap, member, 1);

                                if (DModManager.getNumNonBuiltInDMods(member.getVariant()) < 1) picker.remove(member);
                                if (picker.isEmpty()) break;
                            }

                            getText().setFontSmallInsignia();
                            for (Map.Entry<FleetMemberAPI, Integer> e : removedMap.entrySet()) {
                                String s = e.getKey().getShipName() + ", " + e.getKey().getHullSpec().getHullNameWithDashClass() + ": Repaired " + e.getValue() + " D-Mods";
                                getText().addParagraph(s);
                                getText().highlightInLastPara(Misc.getPositiveHighlightColor(), s);
                            }
                            getText().setFontInsignia();

                            setEndWithContinue(false);
                            setDone(true);
                            setShowAgain(false);

                            getOptions().clearOptions();
                            getOptions().addOption("Continue", null);

                        } else {
                            cancelledFleetMemberPicking();
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        addText("You decide to let the old machinery rest in peace - it may be for the best.");
                        displayBaseOptions();
                    }
                });
    }

    public OptionPanelAPI getOptions() {
        return options;
    }

    public Random getRandom() {
        return random;
    }

    public String getSelectedForge() {
        return selectedForge;
    }

    public static void addOrIncrement(Map<FleetMemberAPI, Integer> map, FleetMemberAPI id, int increaseBy) {
        if (!map.containsKey(id)) {
            map.put(id, increaseBy);
        } else {
            map.put(id, map.get(id) + increaseBy);
        }
    }

    public static void removeOneDMod(FleetMemberAPI member) {
        ShipVariantAPI var = member.getVariant();

        List<HullModSpecAPI> d = getListNonBuiltInDMods(var);
        int rnd = new Random().nextInt(d.size());

        var.removePermaMod(d.get(rnd).getId());
        if (getNumNonBuiltInDMods(var) < 1) var.setHullSpecAPI(getBaseShipHullSpec(var, false));

        member.setVariant(var, false, true);
        member.updateStats();
    }

    public VisualPanelAPI getVisual() {
        return visual;
    }

    public IndEvo_DModRepairSpecial.DModRepairSpecialData getData() {
        return data;
    }

    public TextPanelAPI getText() {
        return text;
    }

    private MemoryAPI getMemory() {
        return BaseCommandPlugin.getEntityMemory(dialog.getPlugin().getMemoryMap());
    }
}
