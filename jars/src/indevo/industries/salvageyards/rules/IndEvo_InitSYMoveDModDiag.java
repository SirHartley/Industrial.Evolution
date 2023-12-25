package indevo.industries.salvageyards.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.industries.RestorationDocks;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;

public class IndEvo_InitSYMoveDModDiag extends BaseCommandPlugin implements InteractionDialogPlugin {

    /*
     * pick ship 1
     * pick ship 2
     * pick D-mods via colour changing selection list
     * confirm*/

    private enum Option {
        MAIN,
        SELECT_FROM,
        SELECT_TO,
        SELECT_MODS,
        MOD_IDENT,
        RETURN_BASE,
        CONFIRM,
        RETURN,
    }

    protected SectorEntityToken entity;
    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;

    protected float costMult = 1f;

    protected FleetMemberAPI originMember = null;
    protected FleetMemberAPI targetMember = null;
    protected Set<String> selectedMods = new HashSet<>();

    public static final float BASE_COST_MULT = Settings.getFloat(Settings.SY_DMOD_MOVE_BASE_COST_MULT);

    public static final Logger log = Global.getLogger(IndEvo_InitSYMoveDModDiag.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (!(dialog instanceof IndEvo_InitSYCustomProductionDiag)) {
            this.dialog = dialog;
            this.memoryMap = memoryMap;
            if (dialog == null) return false;

            entity = dialog.getInteractionTarget();
            originalPlugin = dialog.getPlugin();
            dialog.setPlugin(this);
        }

        dialog.setPromptText("Move D-Mods between ships");

        init(dialog);
        return true;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        log.info("initializing SY dialog plugin");

        float costMod = 1.5f - getMarket().getFaction().getRelationship(Global.getSector().getPlayerFaction().getId());
        costMult = Math.min(1.5f, costMod);
        costMult = Math.max(0.8f, costMod);

        optionSelected(null, Option.MAIN);
    }

    private MarketAPI getMarket() {
        return Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        OptionPanelAPI opts = dialog.getOptionPanel();

        opts.clearOptions();

        if (optionData.toString().startsWith(Option.MOD_IDENT.toString())) {
            String id = optionData.toString().substring(Option.MOD_IDENT.toString().length());

            if (selectedMods.contains(id)) selectedMods.remove(id);
            else selectedMods.add(id);

            updatePanel();
            displayHullmodOptions();
            return;
        }

        Option option = Option.valueOf(optionData.toString());

        switch (option) {
            case MAIN:
                opts.addOption("Select Hull to transfer from", Option.SELECT_FROM);

                opts.addOption("Select Hull to transfer to", Option.SELECT_TO);
                boolean optToAllowed = originMember != null;
                opts.setEnabled(Option.SELECT_TO, optToAllowed);
                if (!optToAllowed) opts.setTooltip(Option.SELECT_TO, "Select the origin hull fist.");
                else opts.setTooltip(Option.SELECT_TO, "Select the ship to move the D-Mods from the original hull to.");

                opts.addOption("Select D-Mods", Option.SELECT_MODS);
                boolean optDModAllowed = targetMember != null && originMember != null;
                opts.setEnabled(Option.SELECT_MODS, optDModAllowed);
                if (!optDModAllowed) opts.setTooltip(Option.SELECT_MODS, "Select a member to transfer to, first.");
                else opts.setTooltip(Option.SELECT_MODS, "Select the D-mods you wish to transfer to the target hull.");


                opts.addOption("Confirm", Option.CONFIRM);
                boolean optConfirmAllowed = targetMember != null && originMember != null && selectedMods != null && !selectedMods.isEmpty();
                opts.setEnabled(Option.CONFIRM, optConfirmAllowed);
                if (!optConfirmAllowed) opts.setTooltip(Option.CONFIRM, "Select all the relevant parameters first.");
                else opts.setTooltip(Option.CONFIRM, null);

                opts.addOption("Return", Option.RETURN);
                opts.setShortcut(Option.RETURN, Keyboard.KEY_ESCAPE, false, false, false, true);

                updatePanel();
                break;
            case SELECT_FROM:
                initOriginHullPicker();
                break;
            case SELECT_TO:
                initTargetHullPicker();
                break;
            case SELECT_MODS:
                selectedMods = new HashSet<>();
                displayHullmodOptions();
                break;
            case RETURN_BASE:
                refreshOptions();
                break;
            case CONFIRM:
                finalizeSelection();
                break;
            case RETURN:
                returnToMenu();
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + option);
        }
    }

    private void updatePanel() {
        TextPanelAPI panel = dialog.getTextPanel();
        panel.clear();

        addTooltip(panel);
    }

    private void displayHullmodOptions() {
        List<String> hmIds = getTransferrableHullmodIds(originMember, targetMember);

        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        for (String s : hmIds) {
            if (selectedMods.contains(s))
                opts.addOption(Global.getSettings().getHullModSpec(s).getDisplayName(), Option.MOD_IDENT.toString() + s, Misc.getHighlightColor(), null);
            else
                opts.addOption(Global.getSettings().getHullModSpec(s).getDisplayName(), Option.MOD_IDENT.toString() + s);
        }

        opts.addOption("Return", Option.RETURN_BASE);
    }

    private Set<FleetMemberAPI> getValidTransferTargetsForMember(FleetMemberAPI member, Set<FleetMemberAPI> fromFleet) {
        Set<FleetMemberAPI> validTargets = new HashSet<>();

        for (FleetMemberAPI m : fromFleet) {
            if (m.getId().equals(member.getId())) continue;

            if (isRestorable(m) && isSameSizeAndTechtype(member, m) && isValidTransferTarget(member, m)) validTargets.add(m);
        }

        return validTargets;
    }

    private boolean isRestorable(FleetMemberAPI member){
        return !member.getVariant().hasTag(Tags.VARIANT_UNRESTORABLE) && !member.getHullSpec().hasTag(Tags.HULL_UNRESTORABLE);
    }

    private boolean isSameSizeAndTechtype(FleetMemberAPI a, FleetMemberAPI b) {
        boolean equalSize = a.getHullSpec().getHullSize().equals(b.getHullSpec().getHullSize());
        boolean equalType = a.getHullSpec().getManufacturer().equals(b.getHullSpec().getManufacturer());

        return equalSize && equalType;
    }

    private boolean isValidTransferTarget(FleetMemberAPI from, FleetMemberAPI to) {
        return compareLists(getDModList(from), getDModList(to));
    }

    private boolean compareLists(List<String> a, List<String> b) {
        for (String s : a) {
            if (!b.contains(s)) return true;
        }

        for (String s : b) {
            if (!a.contains(s)) return true;
        }

        return false;
    }

    private List<String> getDModList(FleetMemberAPI member) {
        List<String> dmodList = new ArrayList<>();
        for (String s : member.getVariant().getHullMods()) {
            if (Global.getSettings().getHullModSpec(s).getTags().contains(Tags.HULLMOD_DMOD)) dmodList.add(s);
        }

        return dmodList;
    }

    private List<String> getTransferrableHullmodIds(FleetMemberAPI from, FleetMemberAPI to) {
        List<String> l1 = getDModList(from);
        l1.removeAll(getDModList(to));

        return l1;
    }

    private Set<FleetMemberAPI> getValidFleetMemberSetForInitialSelection() {
        Set<FleetMemberAPI> combinedFleet = new HashSet<FleetMemberAPI>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());
        CargoAPI storageCargo = Misc.getStorageCargo(getMarket());
        if (storageCargo != null) {
            storageCargo.initMothballedShips("player");
            combinedFleet.addAll(storageCargo.getMothballedShips().getMembersListCopy());
        }

        Set<FleetMemberAPI> validSelectionList = new HashSet<>();

        for (FleetMemberAPI member : combinedFleet) {
            if (getDModList(member).isEmpty() || !isRestorable(member)) continue;

            Set<FleetMemberAPI> validTargetList = getValidTransferTargetsForMember(member, combinedFleet);

            if (!validTargetList.isEmpty()) validSelectionList.add(member);
        }

        return validSelectionList;
    }

    private void refreshOptions() {
        optionSelected(null, Option.MAIN);
    }

    private void initOriginHullPicker() {
        List<FleetMemberAPI> validSelectionList = new ArrayList<>(getValidFleetMemberSetForInitialSelection());

        int shipsPerRow = Settings.getInt(Settings.SHIP_PICKER_ROW_COUNT);
        int rows = validSelectionList.size() > shipsPerRow ? (int) Math.ceil(validSelectionList.size() / (float) shipsPerRow) : 1;
        int cols = Math.min(validSelectionList.size(), shipsPerRow);
        cols = Math.max(cols, 4);

        dialog.showFleetMemberPickerDialog("Select hull to tranfer from", "Confirm", "Cancel", rows,
                cols, 88f, true, false, validSelectionList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {
                            originMember = members.get(0);
                            targetMember = null;
                            selectedMods.clear();

                            updatePanel();
                            refreshOptions();
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                    }
                });


        optionSelected(null, Option.MAIN);
    }

    private void initTargetHullPicker() {
        Set<FleetMemberAPI> combinedFleet = new HashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());

        CargoAPI storageCargo = Misc.getStorageCargo(getMarket());
        if (storageCargo != null) {
            storageCargo.initMothballedShips("player");
            combinedFleet.addAll(storageCargo.getMothballedShips().getMembersListCopy());
        }

        List<FleetMemberAPI> validSelectionList = new ArrayList<>(getValidTransferTargetsForMember(originMember, combinedFleet));

        int shipsPerRow = Settings.getInt(Settings.SHIP_PICKER_ROW_COUNT);
        int rows = validSelectionList.size() > shipsPerRow ? (int) Math.ceil(validSelectionList.size() / (float) shipsPerRow) : 1;
        int cols = Math.min(validSelectionList.size(), shipsPerRow);
        cols = Math.max(cols, 4);

        dialog.showFleetMemberPickerDialog("Select hull to tranfer from", "Confirm", "Cancel", rows,
                cols, 88f, true, false, validSelectionList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {
                            targetMember = members.get(0);
                            selectedMods.clear();
                            updatePanel();
                            refreshOptions();
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                    }
                });


        optionSelected(null, Option.MAIN);
    }

    private void cycleToCustomVariant(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();

        variant = variant.clone();
        variant.setOriginalVariant(null);
        variant.setHullVariantId(Misc.genUID());
        member.setVariant(variant, false, true);
    }

    private void restoreToBase(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();

        variant = variant.clone();
        variant.setOriginalVariant(null);
        variant.setHullVariantId(Misc.genUID());

        variant.setHullSpecAPI(RestorationDocks.getBaseShipHullSpec(member.getVariant(), false));
        member.setVariant(variant, false, true);
    }

    public float getSingleCost() {
        if (originMember == null) return 0;

        float baseVal = RestorationDocks.getBaseShipHullSpec(originMember.getVariant(), true).getBaseValue();
        int numDMods = DModManager.getNumDMods(originMember.getVariant());
        float singleRepairCost = (float) (((baseVal / 2) * (Math.pow(1.2, numDMods) + 1)) / numDMods);

        return singleRepairCost * BASE_COST_MULT * costMult;
    }

    private void addTooltip(TextPanelAPI panel) {

        panel.addPara("\"One mans trash is also another mans trash, but I can do more with it than they ever could!\", he grins.");
        panel.addPara("The salvage yards can transfer D-Mods between ships of similar size and tech-type.\n\n");

        panel.addPara("Contract information:");
        panel.setFontSmallInsignia();

        panel.addParagraph("-----------------------------------------------------------------------------");

        String costMultStr = StringHelper.getAbsPercentString(costMult, false);
        Pair<String, Color> repInt = StringHelper.getRepIntTooltipPair(getMarket().getFaction());

        panel.addPara("Transfer at cost: " + costMultStr + " " + repInt.one);
        Highlights h = new Highlights();
        h.setText(costMultStr, repInt.one);
        h.setColors(Misc.getHighlightColor(), repInt.two);
        panel.setHighlightsInLastPara(h);

        if (originMember != null) panel.addPara("Cost per D-Mod: " + Misc.getDGSCredits(getSingleCost()) + "\n");
        else panel.addPara("Select a ship for cost predictions.");

        if (originMember != null)
            panel.addPara("Transfer from " + originMember.getShipName() + " - " + originMember.getHullSpec().getHullName());
        if (targetMember != null)
            panel.addPara("Transfer to " + targetMember.getShipName() + " - " + targetMember.getHullSpec().getHullName());

        if (!selectedMods.isEmpty()) {
            TooltipMakerAPI tooltip = panel.beginTooltip();

            tooltip.beginTable(getMarket().getFaction(), 20f, "Selected D-Mods", 300f);

            for (String s : selectedMods) {
                String hmName = Global.getSettings().getHullModSpec(s).getDisplayName();
                tooltip.addRow(hmName);
            }

            tooltip.addTable("No D-Mods selected.", 0, 3f);

            float total = getSingleCost() * selectedMods.size();

            panel.addTooltip();

            String totalstr = Misc.getDGSCredits(total);
            panel.addPara("\nTotal cost: " + totalstr);
            panel.highlightInLastPara(Misc.getHighlightColor(), totalstr);
        }

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.setFontInsignia();
    }

    private void returnToMenu() {
        dialog.setPlugin(originalPlugin);
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        FireAll.fire(null, dialog, memoryMap, "IndEvo_YardsBaseMenu");
    }

    public void finalizeSelection() {

        removeCostFromInventory();
        TextPanelAPI panel = dialog.getTextPanel();

        panel.setFontSmallInsignia();

        panel.addPara("Removed " + selectedMods.size() + " D-Mods from " + originMember.getShipName() + " - " + originMember.getHullSpec().getHullName(), Misc.getPositiveHighlightColor());
        panel.addPara("Added " + selectedMods.size() + " D-Mods to " + targetMember.getShipName() + " - " + targetMember.getHullSpec().getHullName(), Misc.getNegativeHighlightColor());

        panel.setFontInsignia();

        returnToMenu();
    }

    private void removeCostFromInventory() {
        CargoAPI fleetCargo = Global.getSector().getPlayerFleet().getCargo();

        //credits
        int total = Math.round(getSingleCost() * selectedMods.size());
        fleetCargo.getCredits().subtract(total);
        AddRemoveCommodity.addCreditsLossText(total, dialog.getTextPanel());

        cycleToCustomVariant(originMember);
        cycleToCustomVariant(targetMember);

        //originalship add
        for (String s : selectedMods) {
            originMember.getVariant().removePermaMod(s);
            targetMember.getVariant().addPermaMod(s);
        }

        if (getDModList(originMember).size() == 0) {
            restoreToBase(originMember);
        }

        if (getDModList(targetMember).size() > 0) {
            setDHull(targetMember);
        }

        originMember.updateStats();
        targetMember.updateStats();
    }

    private void setDHull(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();
        //if (!variant.getHullSpec().isDHull()) {
        variant.setSource(VariantSource.REFIT);

        if (!variant.isDHull()) {
            String dHullId = Misc.getDHullId(variant.getHullSpec());
            ShipHullSpecAPI dHull = Global.getSettings().getHullSpec(dHullId);
            variant.setHullSpecAPI(dHull);
            member.setVariant(variant, false, true);
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
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
