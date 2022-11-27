package indevo.console;

import indevo.exploration.salvage.utils.IndEvo_SalvageSpecialAssigner;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.Map;

public class IndEvo_assignSalvageSpecials implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        IndEvo_SalvageSpecialAssigner assigner = new IndEvo_SalvageSpecialAssigner();
        assigner.init();

        Console.showMessage("Removed Totals:");
        for (Map.Entry<String, Float> e : assigner.removedSpecialMap.entrySet()) {
            Console.showMessage(e.getKey() + " - " + Math.round(e.getValue()));
        }

        Console.showMessage("Added Totals:");
        for (Map.Entry<String, Float> e : assigner.specialMap.entrySet()) {
            Console.showMessage(e.getKey() + " - " + Math.round(e.getValue()));
        }

        return CommandResult.SUCCESS;
    }
}
