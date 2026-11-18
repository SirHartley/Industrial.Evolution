package indevo.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.Collections;

public class PetCommand implements BaseCommand {
    private static final String SYNTAX = "Use indevo_pet list, indevo_pet give <species> [name] or indevo_pet age <pet> <days>.";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        String[] parts = args.trim().split("\\s+", 2);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        PetStatusManager manager = PetStatusManager.getInstance();

        switch (parts[0].toLowerCase()) {
            case "list" -> {
                for (Pet pet : manager.getPets()) {
                    if (pet.isDead()) continue;
                    Console.showMessage(pet.name + " (" + pet.id + "), " + pet.getData().species + ", " + place(pet) + ", " + pet.getMood().label);
                }
                return CommandResult.SUCCESS;
            }
            case "give" -> {
                String[] give = rest.split("\\s+", 2);
                if (give[0].isEmpty() || PetDataRepo.get(give[0]) == null) {
                    Console.showMessage("Unknown species " + give[0] + ". Species ids are the id column of data/campaign/pets.csv.");
                    return CommandResult.BAD_SYNTAX;
                }

                Pet pet = new Pet(give[0], give.length > 1 ? give[1] : PetDataRepo.get(give[0]).species);
                manager.register(pet);
                FleetMemberAPI ship = manager.getFreeShip(Global.getSector().getPlayerFleet(), Collections.emptyList());
                pet.place(ship, null);
                Console.showMessage("Gave " + pet.name + " (" + pet.id + "), " + place(pet) + ".");
                return CommandResult.SUCCESS;
            }
            case "age" -> {
                int split = rest.lastIndexOf(' ');
                Pet pet = split > 0 ? find(manager, rest.substring(0, split).trim()) : null;
                float days;

                try {
                    days = Float.parseFloat(rest.substring(split + 1));
                } catch (NumberFormatException e) {
                    Console.showMessage(SYNTAX);
                    return CommandResult.BAD_SYNTAX;
                }

                if (pet == null) {
                    Console.showMessage("No living pet with that name or id.");
                    return CommandResult.ERROR;
                }

                pet.age += days;
                Console.showMessage(pet.name + " is now " + pet.getAgeString() + " old, life span " + Math.round(pet.lifetime) + " days.");
                return CommandResult.SUCCESS;
            }
            default -> {
                Console.showMessage(SYNTAX);
                return CommandResult.BAD_SYNTAX;
            }
        }
    }

    private static Pet find(PetStatusManager manager, String nameOrId) {
        for (Pet pet : manager.getPets()) {
            if (!pet.isDead() && (pet.id.equals(nameOrId) || pet.name.equalsIgnoreCase(nameOrId))) return pet;
        }

        return null;
    }

    private static String place(Pet pet) {
        if (pet.assignedFleetMember != null) return "on the " + pet.assignedFleetMember.getShipName();
        if (pet.storage != null) return "in storage at " + pet.storage.getMarket().getName();
        return "in the menagerie";
    }
}
