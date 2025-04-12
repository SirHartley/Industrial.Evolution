package indevo.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.StringHelper;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import static com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl.getWeightForMarketSizeStatic;

public class SetMarketGrowthProgress implements BaseCommand {
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

        if (args.matches("[01]") || args.matches("\\d\\.\\d{2}")) {
            float value = Float.parseFloat(args);
            if (value < 0 || value > 1) {
                Console.showMessage("Given value must be between 0 and 1");
                return CommandResult.BAD_SYNTAX;
            }
        } else {
            Console.showMessage("Required format: SetMarketGrowth 0.43 to set market growth process to 43%");
            return CommandResult.BAD_SYNTAX;
        }

        float value = Float.parseFloat(args);

        PopulationComposition pop = market.getPopulation();

        float min = getWeightForMarketSizeStatic(market.getSize());
        float max = getWeightForMarketSizeStatic(market.getSize() + 1);
        float targetWeight = min + (max - min) * value;

        pop.setWeight(targetWeight);
        pop.normalize();

        Console.showMessage("Set market growth process to " + StringHelper.getFloatToIntStrx100(value) + "%");
        return CommandResult.SUCCESS;


    }
}
