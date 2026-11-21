package indevo.industries.derelicts.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.derelicts.RiftBlockers;
import indevo.industries.derelicts.RiftTargetArea;
import indevo.industries.derelicts.RiftTargetMode;
import indevo.industries.derelicts.RiftTargets;
import indevo.industries.derelicts.dialogue.RiftPreciseConfirmDialog;
import indevo.industries.derelicts.dialogue.RiftPrecisePicker;
import indevo.industries.derelicts.dialogue.RiftTargetMap;
import indevo.industries.derelicts.industry.RiftGenerator;
import indevo.utils.helper.RelicHelper;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class IndEvo_RiftGen extends BaseCommandPlugin {

    public static final String PRECISE_PICK_OPTION = "IndEvo_RiftTarget_PRECISE_pick";
    public static final String PRECISE_LORE = "The targeting array can hold a single system steady, but only after its Domain era focusing lattice has been rebuilt from scratch.";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String verb = params.get(0).getString(memoryMap);
        RiftGenerator gen = getGenerator(memoryMap);

        switch (verb) {
            case "gate" -> gate(dialog.getOptionPanel(), gen, params.get(1).getString(memoryMap));
            case "setupTargetMenu" -> setupTargetMenu(dialog, gen);
            case "showArea" -> showArea(dialog, gen, getMode(params, memoryMap));
            case "fire" -> gen.fire(getMode(params, memoryMap), Misc.random);
            case "pickTarget" -> RiftPrecisePicker.show(dialog, memoryMap, gen);
            case "showPick" -> showPick(dialog, gen, getPicked(gen, memoryMap));
            case "confirmPrecise" -> confirmPrecise(dialog, memoryMap, gen, getPicked(gen, memoryMap));
            default -> throw new IllegalArgumentException("unknown IndEvo_RiftGen verb " + verb + " in " + ruleId);
        }

        return true;
    }

    private static RiftGenerator getGenerator(Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        if (market != null && market.getIndustry(Ids.RIFTGEN) instanceof RiftGenerator gen) return gen;
        throw new IllegalStateException("IndEvo_RiftGen on a market without a rift generator, rules must check IndEvo_marketHasIndustry first");
    }

    private static RiftTargetMode getMode(List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        return RiftTargetMode.valueOf(params.get(1).getString(memoryMap));
    }

    private static StarSystemAPI getPicked(RiftGenerator gen, Map<String, MemoryAPI> memoryMap) {
        StarSystemAPI system = RiftPrecisePicker.getPicked(memoryMap);
        if (system == null || !gen.getTargets().isPreciseTarget(system))
            throw new IllegalStateException("no valid precise rift target picked on " + gen.getMarket().getId() + ": " + system);
        return system;
    }

    private static void gate(OptionPanelAPI options, RiftGenerator gen, String optionId) {
        RiftBlockers.Reason reason = gen.getBlockReason();
        if (reason != null) disable(options, optionId, reason);
    }

    private static void disable(OptionPanelAPI options, String optionId, RiftBlockers.Reason reason) {
        options.setEnabled(optionId, false);
        options.setTooltip(optionId, reason.text());
        if (reason.highlights().length == 0) return;

        Color[] colors = new Color[reason.highlights().length];
        Arrays.fill(colors, Misc.getHighlightColor());
        options.setTooltipHighlights(optionId, reason.highlights());
        options.setTooltipHighlightColors(optionId, colors);
    }

    // runs after every IndEvo_RiftGenTargetOptions row has added its option
    private static void setupTargetMenu(InteractionDialogAPI dialog, RiftGenerator gen) {
        OptionPanelAPI options = dialog.getOptionPanel();
        MarketAPI market = gen.getMarket();
        RiftTargets targets = gen.getTargets();

        for (RiftTargetMode mode : RiftTargetMode.values()) {
            String id = mode.getPanelOptionId();
            if (!options.hasOption(id)) continue;

            if (mode == RiftTargetMode.COLONY && !targets.hasOtherColonySystem()) {
                disable(options, id, new RiftBlockers.Reason("You have no colony in another star system."));
                continue;
            }

            RiftTargetArea area = targets.getArea(mode);
            if (area.isEmpty()) disable(options, id, new RiftBlockers.Reason("No star system in that direction can take the planet."));
            else options.addOptionTooltipAppender(id, (tooltip, hadOtherText) -> RiftTargetMap.addAreaTooltip(tooltip, market, area));
        }

        if (!options.hasOption(PRECISE_PICK_OPTION)) return;

        dialog.setOptionColor(PRECISE_PICK_OPTION, Misc.getPositiveHighlightColor());
        if (targets.getPreciseTargets().isEmpty()) {
            disable(options, PRECISE_PICK_OPTION, new RiftBlockers.Reason("No star system in range can take the planet."));
            return;
        }

        options.addOptionTooltipAppender(PRECISE_PICK_OPTION, (tooltip, hadOtherText) -> addPreciseTooltip(tooltip, gen));
        if (!RelicHelper.canAfford(market, gen.getPreciseCost())) options.setEnabled(PRECISE_PICK_OPTION, false);
    }

    private static void addPreciseTooltip(TooltipMakerAPI tooltip, RiftGenerator gen) {
        tooltip.addPara("Pick the exact star system, within %s light years.", 0f, Misc.getHighlightColor(), Math.round(gen.getRangeLY()) + "");
        RelicHelper.addCostLine(tooltip, gen.getMarket(), gen.getPreciseCost(), 10f);
        tooltip.addPara(PRECISE_LORE, Misc.getGrayColor(), 10f);
    }

    private static void showArea(InteractionDialogAPI dialog, RiftGenerator gen, RiftTargetMode mode) {
        RiftTargetArea area = gen.getTargets().getArea(mode);
        if (area.isEmpty()) throw new IllegalStateException("rift mode page " + mode + " opened on " + gen.getMarket().getId() + " without targets, the option should be disabled");

        RiftTargetMap.addAreaText(dialog.getTextPanel(), area);
    }

    private static void showPick(InteractionDialogAPI dialog, RiftGenerator gen, StarSystemAPI system) {
        String distance = Misc.getRoundedValueMaxOneAfterDecimal(RiftPrecisePicker.getDistanceLY(gen.getMarket(), system));
        dialog.getTextPanel().addPara("The targeting array locks onto the %s, %s light years away.", Misc.getHighlightColor(), system.getNameWithLowercaseType(), distance);
    }

    private static void confirmPrecise(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, RiftGenerator gen, StarSystemAPI system) {
        if (gen.getPreciseCost() <= 0) RiftPreciseConfirmDialog.fire(dialog, memoryMap, gen, system);
        else new RiftPreciseConfirmDialog(dialog, memoryMap, gen, system).show(dialog);
    }
}
