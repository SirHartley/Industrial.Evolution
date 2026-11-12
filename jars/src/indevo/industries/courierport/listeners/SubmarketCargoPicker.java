package indevo.industries.courierport.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingCostCalculator;

public class SubmarketCargoPicker {
    ShippingContract contract;
    SubmarketAPI from;
    SubmarketAPI to;

    public SubmarketCargoPicker(final InteractionDialogAPI dialogue, final ShippingContract contract, final Runnable onClose) {
        this.from = contract.getFromSubmarket();
        this.to = contract.getToSubmarket();
        this.contract = contract;
        if (from == null || to == null) {
            onClose.run();
            return;
        }

        final float width = 250f;

        dialogue.showCargoPickerDialog("Select cargo to transport", "Confirm", "Cancel", false, width, getValidCargoCopy(), new CargoPickerListener() {
            @Override
            public void pickedCargo(CargoAPI cargo) {
                contract.clearTargetCargo();
                contract.addToCargo(cargo);
                onClose.run();
            }

            @Override
            public void cancelledCargoSelection() {
                onClose.run();
            }

            @Override
            public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
                float opad = 10f;
                //panel.addImage("cargo_loading", width * 1f, 3f);
                panel.addPara("Select the cargo you would like to ship. Items that are illegal in the target storage are not displayed.", opad);

                panel.setGridFontDefault();
                panel.beginGridFlipped(300f, 1, 70f, 5f);

                int i = 0;
                float total = 0f;

                for (CargoStackAPI stack : cargo.getStacksCopy()) {
                    if (stack.getSize() == 0) continue;

                    float cost = ShippingCostCalculator.getCostForStack(stack);
                    total += cost;

                    if (i < 10) {
                        panel.addToGrid(0, i, (int) stack.getSize() + " units " + stack.getDisplayName(), Misc.getDGSCredits(cost));
                        i++;
                    }
                }
                panel.addGrid(opad);
                if (i > 9) {
                    panel.addPara("... and " + (cargo.getStacksCopy().size() - 10) + " more.", 3f);
                }
                panel.setParaFontDefault();

                panel.addPara("Total cost: %s", opad, Misc.getPositiveHighlightColor(), Misc.getDGSCredits(total));
            }
        });
    }

    private CargoAPI getValidCargoCopy() {
        final CargoAPI copy = Global.getFactory().createCargo(false);

        for (CargoStackAPI stack : from.getCargo().createCopy().getStacksCopy()) {
            if (!to.getPlugin().isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_SELL)) {
                copy.addFromStack(stack);
            }
        }

        return copy;
    }
}
