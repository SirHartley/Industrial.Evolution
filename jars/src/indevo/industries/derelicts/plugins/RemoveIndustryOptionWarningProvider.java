package indevo.industries.derelicts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.impl.campaign.econ.MildClimate;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.StringHelper;
import indevo.utils.plugins.TagBasedSimplifiedIndustryOptionProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Refunds only the current upgrade, whereas vanilla refunds the entire upgrade tree - not required for my use case
 */

public class RemoveIndustryOptionWarningProvider extends TagBasedSimplifiedIndustryOptionProvider {

    public static void register(){
        Global.getSector().getListenerManager().addListener(new RemoveIndustryOptionWarningProvider(), true);
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        CustomDialogDelegate delegate = new BaseCustomDialogDelegate() {
            @Override
            public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
                TooltipMakerAPI info = panel.createUIElement(500f, 150f, false);

                info.setParaInsigniaLarge();
                float refund = Global.getSettings().getFloat("industryRefundFraction");

                info.addPara("Shutting down " + opt.ind.getCurrentName() +
                        " will refund you %s of the total construction cost, or %s, and will take effect immediately.", 10f, Misc.getHighlightColor(),
                        StringHelper.getAbsPercentString(refund, false),
                        Misc.getDGSCredits(opt.ind.getSpec().getCost() * refund));

                info.addPara("This structure can not be rebuilt.", Misc.getNegativeHighlightColor(), 20f);
                panel.addUIElement(info).inTL(0,0);
            }

            @Override
            public boolean hasCancelButton() {
                return true;
            }

            @Override
            public void customDialogConfirm() {
                float refund = Global.getSettings().getFloat("industryRefundFraction");

                opt.ind.getMarket().removeIndustry(opt.ind.getId(), MarketAPI.MarketInteractionMode.REMOTE, false);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(opt.ind.getSpec().getCost() * refund);
            }

            @Override
            public String getCancelText() {
                return "Cancel";
            }

            @Override
            public String getConfirmText() {
                return "Shut down";
            }
        };
        ui.showDialog(500f, 150f, delegate);
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            float refund = Global.getSettings().getFloat("industryRefundFraction");

            tooltip.addPara("Can not be rebuilt once dismantled.", Misc.getNegativeHighlightColor(), 0f);

            tooltip.addPara("Shut down all operations for a %s refund.", 10f, Misc.getHighlightColor(),
                    Misc.getDGSCredits(opt.ind.getSpec().getCost() * refund));

            tooltip.addPara("Equivalent to downgrading and then shutting down for upgraded industries and structures. Refunds %s of the construction costs.", 10f, Misc.getHighlightColor(),
                    StringHelper.getAbsPercentString(refund, false));
        }
    }

    //should really fix this since I override it here but eh...
    @Override
    public void createTooltip(TooltipMakerAPI tooltip) {

    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Shut down...";
    }

    @Override
    public String getTargetTag() {
        return "IndEvo_showDeconstructWarning";
    }

    @Override
    public java.util.List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (!isSuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData(getOptionLabel(ind), CUSTOM_PLUGIN, ind, this);
        opt.color = ind.getMarket().getFaction().getBaseUIColor();
        if (!ind.canShutDown()) opt.enabled = false;
        result.add(opt);

        return result;
    }
}
