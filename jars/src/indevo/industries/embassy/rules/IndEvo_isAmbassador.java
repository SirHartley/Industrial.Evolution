package indevo.industries.embassy.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import indevo.industries.embassy.IndEvo_AmbassadorItemHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_isAmbassador extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String post = memoryMap.get(MemKeys.LOCAL).getString("$postId");
        if (post == null) return false;
        return post.equals(IndEvo_AmbassadorItemHelper.POST_AMBASSADOR);
    }
}