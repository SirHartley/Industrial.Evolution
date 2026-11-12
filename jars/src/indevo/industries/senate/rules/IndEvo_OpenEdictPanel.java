package indevo.industries.senate.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.senate.dialogue.EdictPanelDelegate;
import indevo.industries.senate.dialogue.EdictView;

import java.util.List;
import java.util.Map;

public class IndEvo_OpenEdictPanel extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || dialog.getInteractionTarget() == null) return false;

        MarketAPI market = dialog.getInteractionTarget().getMarket();
        if (market == null) return false;

        EdictPanelDelegate.show(dialog, market, new EdictView());
        return true;
    }
}
