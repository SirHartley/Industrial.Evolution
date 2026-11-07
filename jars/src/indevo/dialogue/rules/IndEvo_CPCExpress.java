package indevo.dialogue.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.missions.CustomProductionContract;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;

import java.awt.Color;
import java.util.List;
import java.util.Map;

public class IndEvo_CPCExpress extends BaseCommandPlugin {

    public static final String OPTION_EXPRESS = "IndEvo_cpcExpress";
    public static final float RELICS_PER_DAY = 1f;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        switch (action) {
            case "valid":
                return getContract(params, memoryMap) != null;
            case "options":
                setupOptions(dialog);
                return true;
            case "apply":
                CustomProductionContract contract = getContract(params, memoryMap);
                if (contract == null) return false;
                apply(dialog, contract);
                return true;
        }

        return false;
    }

    private static CustomProductionContract getContract(List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.size() < 2) return null;
        Misc.VarAndMemory handle = params.get(1).getVarNameAndMemory(memoryMap);
        Object ref = handle.memory.get(handle.name);
        return ref instanceof CustomProductionContract ? (CustomProductionContract) ref : null;
    }

    private static void setupOptions(InteractionDialogAPI dialog) {
        final int cost = getCost();
        final int available = getAvailable();
        final boolean canPay = available >= cost;
        final String relicName = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();
        final int days = Math.round(CustomProductionContract.PROD_DAYS);
        final int expressDays = Math.round(CustomProductionContract.PROD_DAYS - getExpressElapsed());

        OptionPanelAPI options = dialog.getOptionPanel();
        options.setOptionText("Express Delivery (" + cost + " " + relicName + ")", OPTION_EXPRESS);
        options.setEnabled(OPTION_EXPRESS, canPay);
        options.addOptionTooltipAppender(OPTION_EXPRESS, new OptionPanelAPI.OptionTooltipCreator() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean hadOtherText) {
                Color hl = Misc.getHighlightColor();
                Color costColor = canPay ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
                tooltip.addPara("Halves the delivery time from %s to %s days.", 0f, hl, days + "", expressDays + "");
                tooltip.addPara("Cost: %s (Available: " + available + ")", 3f, costColor, cost + " " + relicName);
            }
        });
    }

    private static void apply(InteractionDialogAPI dialog, CustomProductionContract contract) {
        int cost = getCost();
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo.getCommodityQuantity(ItemIds.RARE_PARTS) < cost) return;

        cargo.removeCommodity(ItemIds.RARE_PARTS, cost);
        // accept resets the stage timer, so this has to run after it
        contract.getData(CustomProductionContract.Stage.WAITING).elapsed = getExpressElapsed();

        TextPanelAPI text = dialog.getTextPanel();
        AddRemoveCommodity.addCommodityLossText(ItemIds.RARE_PARTS, cost, text);
        int daysLeft = Math.round(CustomProductionContract.PROD_DAYS - getExpressElapsed());
        text.addPara("Delivery in %s days.", Misc.getHighlightColor(), daysLeft + "");
    }

    private static float getExpressElapsed() {
        return CustomProductionContract.PROD_DAYS / 2f;
    }

    private static int getCost() {
        return Math.round(CustomProductionContract.PROD_DAYS * RELICS_PER_DAY);
    }

    private static int getAvailable() {
        return Math.round(Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS));
    }
}
