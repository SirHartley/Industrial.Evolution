package industrial_evolution.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import industrial_evolution.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

import static industrial_evolution.campaign.rulecmd.IndEvo_AddSpacerFineMenuPoint.PENALTY_AMT_FRACTION;
import static industrial_evolution.campaign.rulecmd.salvage.special.IndEvo_CreditStashSpecial.STOLEN_CREDIT_AMT_KEY;

public class IndEvo_DeductSpacerCreditsAndSetNeutral extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return false;

        float amt = fleet.getMemoryWithoutUpdate().getFloat(STOLEN_CREDIT_AMT_KEY);
        float penalty = amt * PENALTY_AMT_FRACTION;
        float total = amt + penalty;

        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(total);
        AddRemoveCommodity.addCreditsLossText(Math.round(total), dialog.getTextPanel());

        fleet.setName("No longer " + IndEvo_StringHelper.lcFirst(fleet.getName()));
        fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
        fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
        fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);

        fleet.removeFirstAssignmentIfItIs(FleetAssignment.INTERCEPT);
        return true;
    }
}