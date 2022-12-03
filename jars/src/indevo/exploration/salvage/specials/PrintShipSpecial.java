package indevo.exploration.salvage.specials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import indevo.ids.ItemIds;
import indevo.items.ForgeTemplateItemPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.industries.derelicts.industry.HullForge;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PrintShipSpecial extends BaseSalvageSpecial {
    //prints a ship from a template

    public static Logger log = Global.getLogger(PrintShipSpecial.class);

    public static final String SELECT = "select";
    public static final String NOT_NOW = "not_now";

    private FleetMemberAPI selected = null;

    public static class PrintShipSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {

        public final int qualityLevel;

        public PrintShipSpecialData(int qualityLevel) {
            this.qualityLevel = qualityLevel;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new PrintShipSpecial();
        }
    }

    private PrintShipSpecial.PrintShipSpecialData data;

    public PrintShipSpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);
        data = (PrintShipSpecial.PrintShipSpecialData) specialData;

        options.clearOptions();

        String shape = "surprisingly good";
        if (data.qualityLevel > 1) shape = "passable";
        if (data.qualityLevel > 2) shape = "rather bad";

        String s = entity instanceof PlanetAPI ? "facility" : "$shortName";

        addText("Your salvage crew is rendered speechless by a massive, almost alien array of technology.\n" +
                "The machinery probably makes up more than half the total mass of the " + s + ".");

        if (playercargoHasForgeTemplate()) {
            addText("It seems to be some kind of experimental Nanoforge with a specialized blueprint reader. A Forge Template fits the equipment as if made for it.\n\n" +
                    "A preliminary inspection reports it to be in " + shape + " shape.");

            displayBaseOptions();

        } else {
            addText("It seems to be some kind of experimental Nanoforge, yet none of your blueprints fit into the equipment.\n\n" +
                    "Consider returning after finding something that might work.");

            setDone(true);
            setEndWithContinue(true);
            setShowAgain(true);
        }
    }

    public void displayBaseOptions() {
        options.clearOptions();
        options.addOption("Slot a Template", SELECT);
        options.addOption("Not now", NOT_NOW);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (SELECT.equals(optionData)) initHullPicker();
        else if (NOT_NOW.equals(optionData)) {
            addText("You leave. " +
                    "The experimental Hull Forge remains behind, to be used or disassembled for parts.");

            setEndWithContinue(false);
            setShowAgain(true);
            setDone(true);
        }
    }

    private SpecialItemData reduceFTStack(FleetMemberAPI member) {
        CargoAPI cargo = playerFleet.getCargo();
        CargoStackAPI stack = getForgeTemplateStackForMember(member);
        SpecialItemData data = ForgeTemplateItemPlugin.incrementForgeTemplateData(stack.getSpecialDataIfSpecial(), -1);

        cargo.removeStack(stack);
        cargo.addSpecial(data, 1);

        return data;
    }

    private void prepareAndAddMember(FleetMemberAPI member) {
        int quality = ForgeTemplateItemPlugin.getForgeTemplateQualityLevel(getForgeTemplateStackForMember(member).getSpecialDataIfSpecial())
                + data.qualityLevel;
        ForgeTemplateItemPlugin.addPrintDefectDMods(member, quality, random);
        HullForge.addBuiltInHullmods(1, member, random);
        //IndustryHelper.finalizeAndUpdateFleetMember(member);

        playerFleet.getFleetData().addFleetMember(member);
    }

    private void initHullPicker() {
        List<FleetMemberAPI> fleetMemberList = getForgeTemplateFleetMemberList();
        int rows = fleetMemberList.size() > 8 ? (int) Math.ceil(fleetMemberList.size() / 8f) : 1;
        int cols = Math.min(fleetMemberList.size(), 8);
        cols = Math.max(cols, 4);

        dialog.showFleetMemberPickerDialog("Select a Hull", "Confirm", "Cancel", rows,
                cols, 88f, true, false, fleetMemberList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {
                            selected = members.get(0);

                            prepareAndAddMember(selected);
                            SpecialItemData data = reduceFTStack(selected);

                            addText("A ship assembles out of thin space in front of your eyes.\n\n" +
                                    "The technology is so far beyond anything you have ever seen, it might as well be magic - " +
                                    "which makes the fires, smoke and blaring alarms all the more concerning.\n\n" +
                                    "This was likely the last ship the machine is ever going to construct.");


                            AddRemoveCommodity.addFleetMemberGainText(selected, getText());

                            if (data.getId().equals(ItemIds.BROKENFORGETEMPLATE))
                                addSmallText("The Forge template has burned out.");

                            getVisual().showFleetMemberInfo(selected, true);

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
                        addText("You decide not to mess with unknown ancient tech for now - maybe for the better.");
                        displayBaseOptions();
                    }
                });
    }

    public OptionPanelAPI getOptions() {
        return options;
    }

    public TextPanelAPI getText() {
        return text;
    }

    public VisualPanelAPI getVisual() {
        return visual;
    }

    private CargoStackAPI getForgeTemplateStackForMember(FleetMemberAPI member) {
        for (CargoStackAPI stack : playerFleet.getCargo().getStacksCopy()) {
            if (!stack.isSpecialStack()) continue;
            if (isForgeTemplateStack(stack) && ForgeTemplateItemPlugin.getForgeTemplateHullID(stack.getSpecialDataIfSpecial()).equals(member.getHullId())) {
                return stack;
            }
        }

        return null;
    }


    private boolean playercargoHasForgeTemplate() {
        CargoAPI cargo = playerFleet.getCargo();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (!stack.isSpecialStack()) continue;
            if (isForgeTemplateStack(stack)) return true;
        }

        return false;
    }

    private boolean isForgeTemplateStack(CargoStackAPI stack) {
        return stack.getSpecialItemSpecIfSpecial().getId().contains("IndEvo_ForgeTemplate");
    }

    private List<FleetMemberAPI> getForgeTemplateFleetMemberList() {
        CargoAPI cargo = playerFleet.getCargo();
        List<FleetMemberAPI> l = new ArrayList<>();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (!stack.isSpecialStack()) continue;
            if (isForgeTemplateStack(stack))
                l.add(ForgeTemplateItemPlugin.createNakedFleetMemberFromForgeTemplate(stack.getSpecialDataIfSpecial()));
        }

        return l;
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
