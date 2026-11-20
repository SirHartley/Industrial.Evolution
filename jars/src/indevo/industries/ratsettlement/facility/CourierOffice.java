package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.courierport.dialogue.CourierPortDialoguePlugin;

// no state; ShippingManager checks for this facility through RatSettlements and bills the settlement market
public class CourierOffice extends SettlementFacility {

    private static final String OPEN = "open";

    @Override
    protected Setting parentSetting() {
        return Setting.PRIVATEPORT;
    }

    @Override
    protected void addOptions(InteractionDialogAPI dialog) {
        addOption(dialog, "Manage shipping contracts", OPEN);
    }

    @Override
    protected void onOption(InteractionDialogAPI dialog, String action) {
        openPanel(dialog, CourierPortDialoguePlugin::new);
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("A Courier Port office. Shipping contracts run and are managed from here without a Courier Port colony, at the same costs.", 0f);
        tooltip.addPara("Costs are billed to the settlement on the monthly report.", Misc.getGrayColor(), 10f);
    }
}
