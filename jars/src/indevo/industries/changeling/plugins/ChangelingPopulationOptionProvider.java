package indevo.industries.changeling.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.changeling.dialogue.ChangelingIndustryDialogueDelegate;
import indevo.industries.changeling.industry.population.SwitchablePopulation;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChangelingPopulationOptionProvider extends BaseIndustryOptionProvider {

    public static Object CUSTOM_PLUGIN = new Object();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(ChangelingPopulationOptionProvider.class)) {
            listeners.addListener(new ChangelingPopulationOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        return super.isUnsuitable(ind, allowUnderConstruction) || !isSuitable(ind);
    }

    public boolean isSuitable(Industry ind){
        boolean isPop = ind.getId().equals(Industries.POPULATION) && (ind.getMarket().getSize() <= Settings.GOVERNMENT_MAX_SIZE || Global.getSettings().isDevMode());
        boolean isChangeling = ind instanceof SwitchablePopulation;
        boolean canChange = isChangeling && ((SwitchablePopulation) ind).canChange();
        boolean isPlayerOwned = ind.getMarket().isPlayerOwned();

        return (isPop || canChange) && isPlayerOwned;
    }

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData("Change Governance Type", CUSTOM_PLUGIN, ind, this);
        opt.color = new Color(150, 100, 255, 255);
        result.add(opt);

        return result;

    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            tooltip.addPara("Change the local governing style", 0f);

            tooltip.addPara("This is only possible until %s and becomes permanent after %s.", 10f, Misc.getHighlightColor(),
                    "size " + Settings.GOVERNMENT_MAX_SIZE,
                    SwitchablePopulation.DAYS_TO_LOCK + " " + StringHelper.getDayOrDays(SwitchablePopulation.DAYS_TO_LOCK));
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == CUSTOM_PLUGIN) {
            CustomDialogDelegate delegate = new ChangelingIndustryDialogueDelegate(opt.ind, Ids.SWITCHABLE_POPULATION, SwitchablePopulation.industryList);
            ui.showDialog(ChangelingIndustryDialogueDelegate.WIDTH, ChangelingIndustryDialogueDelegate.HEIGHT, delegate);
        }
    }

    @Override
    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
    }
}
