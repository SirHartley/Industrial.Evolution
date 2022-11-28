package indevo.dialogue.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

import static indevo.exploration.salvage.specials.CreditStashSpecial.STOLEN_CREDIT_AMT_KEY;

public class IndEvo_AddSpacerFineMenuPoint extends BaseCommandPlugin {

    public static final float PENALTY_AMT_FRACTION = 0.2f;
    public static final String OPTION_PAY = "IndEvo_PaySpacerFine";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        float amt = memoryMap.get(MemKeys.ENTITY).getFloat(STOLEN_CREDIT_AMT_KEY);
        float penalty = amt * PENALTY_AMT_FRACTION;
        dialog.getOptionPanel().addOption("Refund the " + Misc.getDGSCredits(amt) + " you took, with an additional " + Misc.getDGSCredits(penalty) + " in interest", OPTION_PAY);

        return true;
    }
}