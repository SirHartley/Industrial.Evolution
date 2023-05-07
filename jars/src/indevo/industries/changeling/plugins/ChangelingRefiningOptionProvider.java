package indevo.industries.changeling.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.changeling.dialogue.ChangelingIndustryDialogueDelegate;
import indevo.industries.changeling.industry.refining.SwitchableRefining;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChangelingRefiningOptionProvider extends BaseIndustryOptionProvider {

    public static Object CUSTOM_PLUGIN = new Object();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(ChangelingRefiningOptionProvider.class)) {
            listeners.addListener(new ChangelingRefiningOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        boolean isTarget = ind.getId().equals(Industries.REFINING);
        boolean isChangeling = ind instanceof SwitchableRefining;
        boolean canChange = isChangeling && ((SwitchableRefining) ind).canChange();

        return super.isUnsuitable(ind, allowUnderConstruction) && !(isTarget || canChange);
    }

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        IndustryOptionData opt = new IndustryOptionData("Change Specialization", CUSTOM_PLUGIN, ind, this);
        opt.color = Color.ORANGE;
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            tooltip.addSectionHeading("Change the specialization of this refinery", Alignment.MID, 0f);
            tooltip.addPara("A specialized refinery has %s on a certain commodity but suffers a heavy malus on all others.", 10f, Misc.getHighlightColor(), "bonus output");
            tooltip.addPara("The specialization can be changed or reverted at any size.", 3f);
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == CUSTOM_PLUGIN) {
            CustomDialogDelegate delegate = new ChangelingIndustryDialogueDelegate(opt.ind, Ids.SWITCHABLE_REFINING, SwitchableRefining.industryList);
            ui.showDialog(ChangelingIndustryDialogueDelegate.WIDTH, ChangelingIndustryDialogueDelegate.HEIGHT, delegate);
        }
    }

    @Override
    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
        if (getIndustryOptions(ind) == null || !(ind instanceof SwitchableRefining)) return;
        if (((SwitchableRefining) ind).getCurrent().isBase()) return;

        float opad = 10f;
        tooltip.addSectionHeading("Specialized Refinery", Alignment.MID, opad);
        tooltip.addPara(((SwitchableRefining) ind).getCurrent().getDescription().getText2(), opad);
    }
}
