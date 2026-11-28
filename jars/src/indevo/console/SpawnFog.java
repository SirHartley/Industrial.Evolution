package indevo.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import indevo.exploration.fog.FogWeatherManager;
import indevo.exploration.fog.terrain.FogTerrainPlugin.Kind;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.Random;

public class SpawnFog implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        Kind kind = Kind.SPELL;
        boolean ambush = false;
        boolean cache = false;
        for (String arg : args.trim().toLowerCase().split("\\s+")) {
            switch (arg) {
                case "" -> {}
                case "spell" -> kind = Kind.SPELL;
                case "bank" -> kind = Kind.BANK;
                case "ambush" -> ambush = true;
                case "cache" -> cache = true;
                default -> {
                    Console.showMessage("Unknown argument " + arg + ". Use [spell] or [bank], optionally [ambush] and, for a bank, [cache].");
                    return CommandResult.BAD_SYNTAX;
                }
            }
        }

        if (!(Global.getSector().getPlayerFleet().getContainingLocation() instanceof StarSystemAPI system)) {
            Console.showMessage("Fog can only be spawned in a star system.");
            return CommandResult.WRONG_CONTEXT;
        }
        if (!FogWeatherManager.hasRoom(system, kind)) {
            Console.showMessage(system.getName() + (kind == Kind.SPELL ? " already has fog." : " already has a spell or " + FogWeatherManager.MAX_BANKS_PER_SYSTEM + " banks."));
            return CommandResult.ERROR;
        }

        cache &= kind == Kind.BANK;
        if (FogWeatherManager.spawn(system, kind, new Random(), ambush, cache) == null) {
            Console.showMessage("No spot in " + system.getName() + " is clear of the banks already there.");
            return CommandResult.ERROR;
        }
        Console.showMessage("Spawned fog (" + kind.name().toLowerCase() + (ambush ? ", with lurkers" : "") + (cache ? ", with a cache" : "") + ") in " + system.getName() + ". It rolls in over " + (int) FogWeatherManager.ROLL_IN_DAYS + " days.");
        return CommandResult.SUCCESS;
    }
}
