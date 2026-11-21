package indevo.industries.petshop.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.petshop.listener.PetStatusManager;

import java.util.List;
import java.util.Map;

public class IndEvo_PetDockingLine extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || !Setting.PETS.enabled()) return false;
        return PetStatusManager.getInstance().sayDocking(dialog);
    }
}
