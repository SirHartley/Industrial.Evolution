package indevo.console;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class IndEvo_clearDisruption implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInMarket()) {
            Console.showMessage(CommonStrings.ERROR_MARKET_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        MarketAPI market = context.getMarket();

        if (market == null) {
            Console.showMessage(CommonStrings.ERROR_MARKET_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        int count = 0;

        for (Industry ind : market.getIndustries()) {
            if (ind.isDisrupted()) {
                ind.setDisrupted(0);
                count++;
            }
        }

        Console.showMessage("Restored functionality to " + count + " Structures/Industries");
        return CommandResult.SUCCESS;


    }

}
