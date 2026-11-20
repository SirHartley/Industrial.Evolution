package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.submarkets.IndustrySubmarkets;

// the submarket is saved on the settlement market, so apply runs once and nothing is kept up
public class RequisitionOffice extends SettlementFacility {

    @Override
    protected Setting parentSetting() {
        return Setting.REQCENTER;
    }

    @Override
    public void apply() {
        getSettlementMarket().addSubmarket(Ids.REQMARKET);
    }

    @Override
    public void unapply() {
        IndustrySubmarkets.remove(getSettlementMarket(), Ids.REQMARKET);
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Imports weapons from other factions and stocks consumable missiles. The market has a %s tariff.",
                0f, Misc.getHighlightColor(), "40%");
        tooltip.addPara("Opens with the settlement storage.", Misc.getGrayColor(), 10f);
    }
}
