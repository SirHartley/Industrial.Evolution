package indevo.exploration.gacha.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.gacha.GachaStationDialoguePlugin;

import java.util.List;
import java.util.Map;

public class OpenStorageTabOnGachaStation extends BaseCommandPlugin implements CoreInteractionListener {

    private InteractionDialogAPI dialog;

    /**
     * OpenCoreUI <CoreUITabId>
     */
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        this.dialog = dialog;

        dialog.getOptionPanel().clearOptions();
        CampaignUIAPI.CoreUITradeMode mode = CampaignUIAPI.CoreUITradeMode.OPEN;
        dialog.getVisualPanel().showCore(CoreUITabId.FLEET , dialog.getInteractionTarget(), mode, this);

        Misc.stopPlayerFleet();
        return true;
    }

    public void coreUIDismissed() {
        // update player memory - supplies/fuel the player has, etc
        Global.getSector().getCharacterData().getMemory();
        ((GachaStationDialoguePlugin) dialog.getPlugin()).displayDefaultOptions(true);
    }
}
