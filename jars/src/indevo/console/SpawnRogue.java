package indevo.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.MeteorSwarmManager.MeteroidShowerType;
import indevo.exploration.meteor.rogue.RogueAsteroidSpawner;
import indevo.exploration.meteor.rogue.RogueDrizzleSpawner;
import indevo.exploration.meteor.rogue.RogueFormation;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.Random;

public class SpawnRogue implements BaseCommand {
    private static final String SYNTAX = "indevo_spawnRogue <scatter|cluster|teardrop|wall|chevron|pair|escort|drizzle> [asteroid|ice|irradiated] [treasure]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        if (!(Global.getSector().getPlayerFleet().getContainingLocation() instanceof StarSystemAPI system)) {
            Console.showMessage("Rogue asteroids can only be spawned in a star system.");
            return CommandResult.WRONG_CONTEXT;
        }

        String[] parts = args.trim().isEmpty() ? new String[0] : args.trim().toLowerCase().split("\\s+");
        if (parts.length == 0) {
            Console.showMessage(SYNTAX);
            return CommandResult.BAD_SYNTAX;
        }

        Random random = new Random();
        MeteroidShowerType type = null;
        boolean treasure = false;

        for (int i = 1; i < parts.length; i++) {
            switch (parts[i]) {
                case "asteroid" -> type = MeteroidShowerType.ASTEROID;
                case "ice" -> type = MeteroidShowerType.ICEROID;
                case "irradiated" -> type = MeteroidShowerType.IRRADIOID;
                case "treasure" -> treasure = true;
                default -> {
                    Console.showMessage("Unknown option " + parts[i] + ". " + SYNTAX);
                    return CommandResult.BAD_SYNTAX;
                }
            }
        }

        if (type == null) type = MeteorSwarmManager.pickRogueType(random);

        if (parts[0].equals("drizzle")) {
            if (treasure) {
                Console.showMessage("A drizzle never has treasure. " + SYNTAX);
                return CommandResult.BAD_SYNTAX;
            }

            RogueDrizzleSpawner drizzle = RogueDrizzleSpawner.start(system, type, random);
            Console.showMessage("Started a rogue asteroid drizzle: " + type.name().toLowerCase() + ", " + Math.round(drizzle.durationDays) + " days.");
            return CommandResult.SUCCESS;
        }

        RogueFormation formation = RogueFormation.byId(parts[0]);
        if (formation == null) {
            Console.showMessage("Unknown shape " + parts[0] + ". " + SYNTAX);
            return CommandResult.BAD_SYNTAX;
        }

        if (!RogueAsteroidSpawner.spawn(system, formation, type, true, treasure, random)) {
            Console.showMessage("No start point out of view found, nothing spawned.");
            return CommandResult.ERROR;
        }

        Console.showMessage("Spawned rogue asteroids: " + formation.getId() + ", " + type.name().toLowerCase() + (treasure ? ", with treasure" : "") + ".");
        return CommandResult.SUCCESS;
    }
}
