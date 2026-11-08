package indevo.industries.salvageyards.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.config.Setting;
import indevo.ids.ItemIds;
import indevo.utils.helper.BillingHelper;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.RelicHelper;
import indevo.utils.helper.StringHelper;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;

public class IndEvo_InitSYMoveSModDiag extends BaseCommandPlugin implements InteractionDialogPlugin {

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

    public static final float RELICS_PER_DP = 5f;
    public static final String LORE = "The modification is woven through the ship's frame. Domain micro-assemblers can separate it at the joints and rebuild it inside a compatible hull.";

    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;

    protected float costMult = 1f;

    protected FleetMemberAPI originMember = null;
    protected FleetMemberAPI targetMember = null;
    protected Set<String> selectedMods = new LinkedHashSet<>();

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        originalPlugin = dialog.getPlugin();
        dialog.setPlugin(this);
        dialog.setPromptText("Move S-mods between ships");

        init(dialog);
        return true;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        costMult = IndEvo_InitSYMoveDModDiag.getCostMult(getMarket());
        optionSelected(null, Option.MAIN);
    }

    public static boolean hasValidPair(MarketAPI market) {
        Set<FleetMemberAPI> members = getMembers(market);

        for (FleetMemberAPI m : members) {
            if (!getSModList(m).isEmpty() && !getValidTargets(m, members).isEmpty()) return true;
        }

        return false;
    }

    private MarketAPI getMarket() {
        return Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
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
                opts.addOption("Select source ship", Option.SELECT_FROM);

                opts.addOption("Select target ship", Option.SELECT_TO);
                boolean optToAllowed = originMember != null;
                opts.setEnabled(Option.SELECT_TO, optToAllowed);
                if (!optToAllowed) opts.setTooltip(Option.SELECT_TO, "Select the source ship first.");
                else opts.setTooltip(Option.SELECT_TO, "Same size and manufacturer, with the hullmod installed and a free S-mod slot.");

                opts.addOption("Select S-mods", Option.SELECT_MODS);
                boolean optModsAllowed = targetMember != null && originMember != null;
                opts.setEnabled(Option.SELECT_MODS, optModsAllowed);
                if (!optModsAllowed) opts.setTooltip(Option.SELECT_MODS, "Select the target ship first.");

                opts.addOption("Confirm", Option.CONFIRM);
                boolean optConfirmAllowed = optModsAllowed && !selectedMods.isEmpty();
                boolean canAfford = BillingHelper.canAfford(getTotalCredits()) && RelicHelper.canAfford(getMarket(), getTotalRelics());
                opts.setEnabled(Option.CONFIRM, optConfirmAllowed && canAfford);
                if (!optConfirmAllowed) opts.setTooltip(Option.CONFIRM, "Select both ships and the S-mods to transfer.");
                else addConfirmTooltip(opts);

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
                selectedMods.clear();
                updatePanel();
                displayHullmodOptions();
                break;
            case RETURN_BASE:
                optionSelected(null, Option.MAIN);
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

    private void addConfirmTooltip(OptionPanelAPI opts) {
        final int relics = getTotalRelics();
        final int credits = getTotalCredits();
        final int count = selectedMods.size();

        opts.addOptionTooltipAppender(Option.CONFIRM, new OptionPanelAPI.OptionTooltipCreator() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean hadOtherText) {
                boolean enoughCredits = BillingHelper.canAfford(credits);

                tooltip.addPara("Moves %s to the target hull. The origin hull loses " + (count == 1 ? "it." : "them."), 0f, Misc.getHighlightColor(), count + " " + (count == 1 ? "S-mod" : "S-mods"));
                RelicHelper.addCostLine(tooltip, getMarket(), relics, 10f);
                tooltip.addPara("Credits: %s", 3f, enoughCredits ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(), Misc.getDGSCredits(credits));
                tooltip.addPara(LORE, Misc.getGrayColor(), 10f);
            }
        });
    }

    private void updatePanel() {
        TextPanelAPI panel = dialog.getTextPanel();
        panel.clear();

        panel.addPara("The salvage yards can move S-mods between ships of the same size and manufacturer.\n\n");

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

        String relicName = RelicHelper.getName();

        if (originMember != null && targetMember != null) {
            String relicStr = getRelicsPerMod() + " " + relicName;
            String creditStr = Misc.getDGSCredits(getCreditsPerMod());
            panel.addPara("Cost per S-mod: %s and %s\n", Misc.getHighlightColor(), relicStr, creditStr);
        } else panel.addPara("Select both ships to see the cost.");

        if (originMember != null)
            panel.addPara("Transfer from " + originMember.getShipName() + " - " + originMember.getHullSpec().getHullName());
        if (targetMember != null) {
            panel.addPara("Transfer to " + targetMember.getShipName() + " - " + targetMember.getHullSpec().getHullName());

            String used = Misc.getCurrSpecialMods(targetMember.getVariant()) + " of " + getMaxSMods(targetMember);
            panel.addPara("%s has %s S-mods.", Misc.getTextColor(), Misc.getHighlightColor(), targetMember.getShipName(), used);
            panel.setHighlightColorsInLastPara(Misc.getTextColor(), Misc.getHighlightColor());
        }

        if (!selectedMods.isEmpty()) {
            TooltipMakerAPI tooltip = panel.beginTooltip();

            tooltip.beginTable(getMarket().getFaction(), 20f, "Selected S-mods", 300f);
            for (String s : selectedMods) tooltip.addRow(Global.getSettings().getHullModSpec(s).getDisplayName());
            tooltip.addTable("No S-mods selected.", 0, 3f);

            panel.addTooltip();

            int relics = getTotalRelics();
            int available = RelicHelper.getAvailable(getMarket());
            String relicStr = relics + " " + relicName;
            String creditStr = Misc.getDGSCredits(getTotalCredits());

            panel.addPara("\nCost: " + relicStr + " (Available: " + available + "), " + creditStr);
            Highlights total = new Highlights();
            total.setText(relicStr, creditStr);
            total.setColors(available >= relics ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                    BillingHelper.canAfford(getTotalCredits()) ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
            panel.setHighlightsInLastPara(total);
        }

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.setFontInsignia();
    }

    private void displayHullmodOptions() {
        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        boolean full = selectedMods.size() >= getFreeSlots(targetMember);

        for (String s : getTransferableMods(originMember, targetMember)) {
            String name = Global.getSettings().getHullModSpec(s).getDisplayName();
            Object data = Option.MOD_IDENT.toString() + s;

            if (selectedMods.contains(s)) opts.addOption(name, data, Misc.getHighlightColor(), null);
            else {
                opts.addOption(name, data);

                if (full) {
                    opts.setEnabled(data, false);
                    opts.setTooltip(data, targetMember.getShipName() + " has no free S-mod slot left.");
                }
            }
        }

        opts.addOption("Return", Option.RETURN_BASE);
        opts.setShortcut(Option.RETURN_BASE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private static Set<FleetMemberAPI> getMembers(MarketAPI market) {
        Set<FleetMemberAPI> members = new LinkedHashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());

        CargoAPI storageCargo = MiscIE.getStorageCargo(market);
        if (storageCargo != null) {
            storageCargo.initMothballedShips("player");
            members.addAll(storageCargo.getMothballedShips().getMembersListCopy());
        }

        return members;
    }

    private static List<String> getSModList(FleetMemberAPI member) {
        List<String> list = new ArrayList<>();

        for (HullModSpecAPI spec : Misc.getCurrSpecialModsList(member.getVariant())) {
            if (!spec.hasTag(Tags.HULLMOD_NO_BUILD_IN)) list.add(spec.getId());
        }

        return list;
    }

    private static List<String> getTransferableMods(FleetMemberAPI from, FleetMemberAPI to) {
        List<String> list = new ArrayList<>();
        ShipVariantAPI target = to.getVariant();

        for (String id : getSModList(from)) {
            if (isInstalledAsNormalMod(target, id)) list.add(id);
        }

        return list;
    }

    // the refit already validated the mod on this hull, campaign has no ShipAPI for isApplicableToShip
    private static boolean isInstalledAsNormalMod(ShipVariantAPI variant, String id) {
        return variant.getHullMods().contains(id)
                && !variant.getPermaMods().contains(id)
                && !variant.getHullSpec().getBuiltInMods().contains(id);
    }

    private static Set<FleetMemberAPI> getValidTargets(FleetMemberAPI origin, Set<FleetMemberAPI> members) {
        Set<FleetMemberAPI> targets = new LinkedHashSet<>();

        for (FleetMemberAPI m : members) {
            if (m.getId().equals(origin.getId())) continue;
            if (!IndEvo_InitSYMoveDModDiag.isSameSizeAndTechtype(origin, m)) continue;
            if (getFreeSlots(m) <= 0) continue;
            if (getTransferableMods(origin, m).isEmpty()) continue;

            targets.add(m);
        }

        return targets;
    }

    private static int getMaxSMods(FleetMemberAPI member) {
        return Misc.getMaxPermanentMods(member, Global.getSector().getPlayerStats());
    }

    private static int getFreeSlots(FleetMemberAPI member) {
        return getMaxSMods(member) - Misc.getCurrSpecialMods(member.getVariant());
    }

    private int getRelicsPerMod() {
        if (targetMember == null) return 0;
        return Math.round(RELICS_PER_DP * targetMember.getUnmodifiedDeploymentPointsCost());
    }

    private float getCreditsPerMod() {
        if (originMember == null) return 0;
        return IndEvo_InitSYMoveDModDiag.getCreditCostPerMod(originMember, getSModList(originMember).size(), costMult);
    }

    private int getTotalRelics() {
        return getRelicsPerMod() * selectedMods.size();
    }

    private int getTotalCredits() {
        return Math.round(getCreditsPerMod() * selectedMods.size());
    }

    private void initOriginHullPicker() {
        Set<FleetMemberAPI> members = getMembers(getMarket());
        List<FleetMemberAPI> validSelectionList = new ArrayList<>();

        for (FleetMemberAPI m : members) {
            if (!getSModList(m).isEmpty() && !getValidTargets(m, members).isEmpty()) validSelectionList.add(m);
        }

        showPicker("Select source ship", validSelectionList, true);
    }

    private void initTargetHullPicker() {
        List<FleetMemberAPI> validSelectionList = new ArrayList<>(getValidTargets(originMember, getMembers(getMarket())));
        showPicker("Select target ship", validSelectionList, false);
    }

    private void showPicker(String title, List<FleetMemberAPI> list, final boolean origin) {
        int shipsPerRow = Setting.SHIP_PICKER_ROW_COUNT.intValue();
        int rows = list.size() > shipsPerRow ? (int) Math.ceil(list.size() / (float) shipsPerRow) : 1;
        int cols = Math.max(Math.min(list.size(), shipsPerRow), 4);

        dialog.showFleetMemberPickerDialog(title, "Confirm", "Cancel", rows, cols, 88f, true, false, list, new FleetMemberPickerListener() {
            @Override
            public void pickedFleetMembers(List<FleetMemberAPI> members) {
                if (members == null || members.isEmpty()) return;

                if (origin) {
                    originMember = members.get(0);
                    targetMember = null;
                } else targetMember = members.get(0);

                selectedMods.clear();
                optionSelected(null, Option.MAIN);
            }

            @Override
            public void cancelledFleetMemberPicking() {
            }
        });

        optionSelected(null, Option.MAIN);
    }

    private void finalizeSelection() {
        TextPanelAPI panel = dialog.getTextPanel();
        int relics = getTotalRelics();
        int credits = getTotalCredits();

        RelicHelper.take(getMarket(), relics);
        AddRemoveCommodity.addCommodityLossText(ItemIds.RARE_PARTS, relics, panel);
        BillingHelper.chargeNow(credits);
        AddRemoveCommodity.addCreditsLossText(credits, panel);

        IndEvo_InitSYMoveDModDiag.cycleToCustomVariant(originMember);
        IndEvo_InitSYMoveDModDiag.cycleToCustomVariant(targetMember);
        originMember.getVariant().setSource(VariantSource.REFIT);
        targetMember.getVariant().setSource(VariantSource.REFIT);

        for (String id : selectedMods) {
            originMember.getVariant().removePermaMod(id);
            originMember.getVariant().removeMod(id);
            targetMember.getVariant().addPermaMod(id, true);
        }

        originMember.updateStats();
        targetMember.updateStats();

        String mods = selectedMods.size() + (selectedMods.size() == 1 ? " S-mod" : " S-mods");

        panel.setFontSmallInsignia();
        panel.addPara("Removed " + mods + " from " + originMember.getShipName() + " - " + originMember.getHullSpec().getHullName(), Misc.getNegativeHighlightColor());
        panel.addPara("Added " + mods + " to " + targetMember.getShipName() + " - " + targetMember.getHullSpec().getHullName(), Misc.getPositiveHighlightColor());
        panel.setFontInsignia();

        returnToMenu();
    }

    private void returnToMenu() {
        dialog.setPlugin(originalPlugin);
        dialog.setPromptText("You decide to...");
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        FireAll.fire(null, dialog, memoryMap, "IndEvo_YardsBaseMenu");
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
