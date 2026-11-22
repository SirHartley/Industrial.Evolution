package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.assembler.industry.VariableAssembler;
import indevo.items.VPCItemPlugin;
import indevo.items.memory.VPCCommodityRepo.Commodity;

import java.util.LinkedHashMap;
import java.util.Map;

public class FabricatorBay extends SettlementFacility {

    public static final float OUTPUT_MULT = 0.5f;
    public static final int SIZE_MULT = 1;
    private static final String LOAD = "load";

    private SpecialItemData vpc = null;

    @Override
    protected Setting parentSetting() {
        return Setting.ADASSEM;
    }

    @Override
    protected void onUnapply() {
        if (vpc != null) getStorageCargo().addSpecial(vpc, 1f);
        vpc = null;
    }

    @Override
    public CargoAPI addToMonthlyCargo(CargoAPI current) {
        if (vpc == null) return null;

        CargoAPI out = Global.getFactory().createCargo(true);
        for (Map.Entry<String, Integer> e : getDeposits().entrySet()) out.addCommodity(e.getKey(), e.getValue());
        return out;
    }

    private Map<String, Integer> getDeposits() {
        Map<String, Integer> map = new LinkedHashMap<>();
        float mult = VPCItemPlugin.getDepositMult(vpc) * OUTPUT_MULT;
        for (Commodity c : VPCItemPlugin.getOutputs(vpc)) {
            int amount = VariableAssembler.getDepositAmount(c.id(), c.getDeposit(Ids.ADASSEM), VariableAssembler.LOG_INCREASE, SIZE_MULT, mult);
            if (amount > 0) map.put(c.id(), amount);
        }

        return map;
    }

    @Override
    protected boolean hasOptions() {
        return true;
    }

    @Override
    protected void addOptions(InteractionDialogAPI dialog) {
        addOption(dialog, vpc == null ? "Load a VPC into the Fabricator Bay" : "Swap the VPC in the Fabricator Bay", LOAD);
    }

    @Override
    protected void onOption(InteractionDialogAPI dialog, String action) {
        CargoAPI fleet = Global.getSector().getPlayerFleet().getCargo();
        CargoAPI storage = getStorageCargo();

        CargoAPI choices = Global.getFactory().createCargo(true);
        addVPCs(storage, choices);
        addVPCs(fleet, choices);

        if (choices.isEmpty()) {
            dialog.getTextPanel().addPara("You have no Variable Production Chip in your fleet or the settlement storage.", Misc.getNegativeHighlightColor());
            return;
        }

        dialog.showCargoPickerDialog("Select a VPC", "Load", "Cancel", true, 300f, choices, new CargoPickerListener() {
            @Override
            public void pickedCargo(CargoAPI cargo) {
                for (CargoStackAPI stack : cargo.getStacksCopy()) {
                    if (!stack.isSpecialStack() || stack.getSize() < 1) continue;
                    load(dialog, stack.getSpecialDataIfSpecial(), storage, fleet);
                    return;
                }
            }

            @Override
            public void cancelledCargoSelection() {
            }

            @Override
            public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
                panel.addPara("Pick one VPC. Each month the bay deposits %s of what a Variable Assembler on a size 3 colony makes into settlement storage.", 0f, Misc.getHighlightColor(), "half");
                if (vpc != null) panel.addPara("The loaded %s goes back to settlement storage.", 10f, Misc.getHighlightColor(), VPCItemPlugin.getDisplayName(vpc));
            }
        });
    }

    private void load(InteractionDialogAPI dialog, SpecialItemData picked, CargoAPI storage, CargoAPI fleet) {
        SpecialItemData item = new SpecialItemData(picked.getId(), picked.getData());
        if (storage.getQuantity(CargoAPI.CargoItemType.SPECIAL, item) >= 1) storage.removeItems(CargoAPI.CargoItemType.SPECIAL, item, 1f);
        else if (fleet.getQuantity(CargoAPI.CargoItemType.SPECIAL, item) >= 1) fleet.removeItems(CargoAPI.CargoItemType.SPECIAL, item, 1f);
        else throw new IllegalStateException("picked VPC " + item.getData() + " is in neither fleet nor settlement storage");

        if (vpc != null) storage.addSpecial(vpc, 1f);
        vpc = item;

        dialog.getTextPanel().addPara("Loaded the %s into the Fabricator Bay.", Misc.getTextColor(), Misc.getHighlightColor(), VPCItemPlugin.getDisplayName(vpc));
    }

    private static void addVPCs(CargoAPI from, CargoAPI to) {
        for (CargoStackAPI stack : from.getStacksCopy()) {
            if (stack.isSpecialStack() && VPCItemPlugin.isValid(stack.getSpecialDataIfSpecial())) to.addFromStack(stack);
        }
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Holds one Variable Production Chip. Each month it deposits %s of what a Variable Assembler on a size 3 colony makes into settlement storage. No demand, no supply.",
                0f, Misc.getHighlightColor(), "half");

        if (!isFunctional()) return;

        if (vpc == null) {
            tooltip.addPara("No VPC loaded. Load one from the settlement menu.", Misc.getNegativeHighlightColor(), 10f);
            return;
        }

        tooltip.addPara("Loaded: %s", 10f, Misc.getHighlightColor(), VPCItemPlugin.getDisplayName(vpc));
        for (Map.Entry<String, Integer> e : getDeposits().entrySet()) {
            tooltip.addPara("%s " + Global.getSettings().getCommoditySpec(e.getKey()).getName() + " per month", 3f, Misc.getHighlightColor(), e.getValue() + "");
        }
    }
}
