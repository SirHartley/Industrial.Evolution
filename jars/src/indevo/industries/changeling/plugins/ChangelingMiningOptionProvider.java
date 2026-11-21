package indevo.industries.changeling.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.changeling.dialogue.ChangelingIndustryDialogueDelegate;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustryData;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChangelingMiningOptionProvider extends BaseIndustryOptionProvider {

    public static Object CUSTOM_PLUGIN = new Object();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(ChangelingMiningOptionProvider.class)) {
            listeners.addListener(new ChangelingMiningOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        return super.isUnsuitable(ind, allowUnderConstruction)
                || !isSuitable(ind);
    }

    public boolean isSuitable(Industry ind){
        boolean isTarget = Specialization.MINING.appliesTo(ind);
        boolean isPlayerOwned = ind.getMarket().isPlayerOwned();

        return isTarget && Setting.SWITCHABLEMINING.enabled() && isPlayerOwned;
    }

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData("Change Specialization...", CUSTOM_PLUGIN, ind, this);
        opt.color = new Color(150, 100, 255, 255);
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            tooltip.addSectionHeading("Change the specialization of this mine", Alignment.MID, 0f);
            tooltip.addPara("A specialized mine has %s on a certain commodity but suffers a heavy malus on all others.", 10f, Misc.getHighlightColor(), "bonus output");
            tooltip.addPara("The specialization can be changed or reverted at any size.", 3f);
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == CUSTOM_PLUGIN) {
            ChangelingIndustryDialogueDelegate.show(ui, opt.ind, Specialization.MINING);
        }
    }

    @Override
    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
        if (ind.getMarket() == null || !Specialization.MINING.appliesTo(ind) || !Specialization.MINING.isSpecialized(ind.getMarket())) return;
        SubIndustryData data = Specialization.MINING.getData(ind.getMarket());

        float opad = 10f;
        tooltip.addSectionHeading("Specialized Mine", Alignment.MID, opad);
        tooltip.addPara(Global.getSettings().getDescription(data.descriptionID, Description.Type.CUSTOM).getText2(), opad);
    }
}
