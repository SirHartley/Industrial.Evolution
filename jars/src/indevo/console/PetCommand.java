package indevo.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.PetWant;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.Collections;
import java.util.Locale;

public class PetCommand implements BaseCommand {
    private static final String SYNTAX = "Use indevo_pet list, indevo_pet give <species> [name], indevo_pet age <pet> <days>, indevo_pet hearts <pet> <0-5>, indevo_pet want <pet> <attention|play|treat|comfort|shore_leave|come_along>, indevo_pet trait <pet> <trait> <grade>, indevo_pet scan <pet>, indevo_pet look <pet> <look|wild> or indevo_pet title <pet> <veteran|navigator|old_timer|survivor|beloved|pampered|treasure_hunter|admiral|globetrotter|stargazer|clear>.";

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
                    Console.showMessage(pet.name + " (" + pet.id + "), " + pet.getData().species + ", " + place(pet) + ", " + pet.getMood().label + ", " + pet.getHearts() + " hearts"
                            + (pet.favourite ? ", favourite" : "") + (pet.want != null ? ", " + pet.want.label.toLowerCase() : ""));
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
                Console.showMessage(pet.name + " is now " + pet.getAgeString() + " old, life span " + Math.round(pet.getLifetime()) + " days.");
                return CommandResult.SUCCESS;
            }
            case "hearts" -> {
                int split = rest.lastIndexOf(' ');
                Pet pet = split > 0 ? find(manager, rest.substring(0, split).trim()) : null;
                int hearts;

                try {
                    hearts = Integer.parseInt(rest.substring(split + 1));
                } catch (NumberFormatException e) {
                    hearts = -1;
                }

                if (hearts < 0 || hearts > PetConstants.MAX_HEARTS) {
                    Console.showMessage(SYNTAX);
                    return CommandResult.BAD_SYNTAX;
                }

                if (pet == null) {
                    Console.showMessage("No living pet with that name or id.");
                    return CommandResult.ERROR;
                }

                pet.setBondDays(hearts == 0 ? 0f : PetConstants.HEART_DAYS[hearts - 1]);
                Console.showMessage(pet.name + " now has " + pet.getHearts() + " hearts.");
                return CommandResult.SUCCESS;
            }
            case "want" -> {
                int split = rest.lastIndexOf(' ');
                Pet pet = split > 0 ? find(manager, rest.substring(0, split).trim()) : null;
                PetWant want = null;
                for (PetWant w : PetWant.values()) if (w.id().equals(rest.substring(split + 1).toLowerCase())) want = w;

                if (want == null) {
                    Console.showMessage(SYNTAX);
                    return CommandResult.BAD_SYNTAX;
                }

                if (pet == null || !pet.isWithPlayer()) {
                    Console.showMessage("No living pet with that name or id on a ship of your fleet or in the menagerie.");
                    return CommandResult.ERROR;
                }

                if (want == PetWant.TREAT && pet.getData().getFavouriteFood() == null) {
                    Console.showMessage(pet.name + " eats only Supplies and has no treat.");
                    return CommandResult.ERROR;
                }

                if (!manager.canFavourite(pet)) {
                    Console.showMessage("Already " + PetConstants.MAX_FAVOURITES + " favourites. Unstar one on the Pets panel first.");
                    return CommandResult.ERROR;
                }

                // skips the fleet gap and the cooldown, an open want of another pet is dropped
                Pet open = manager.getOpenWant();
                if (open != null) open.want = null;
                pet.setFavourite(true);
                manager.openWant(pet, want);
                Console.showMessage(pet.name + " " + want.label.toLowerCase() + ", " + Math.round(pet.wantDaysLeft) + " days.");
                return CommandResult.SUCCESS;
            }
            case "trait" -> {
                String[] words = rest.split("\\s+");
                int grade;

                try {
                    grade = words.length >= 3 ? Integer.parseInt(words[words.length - 1]) : -1;
                } catch (NumberFormatException e) {
                    grade = -1;
                }

                String traitId = words.length >= 3 ? words[words.length - 2] : "";
                if (!traitId.startsWith(PetDataRepo.TRAIT_PREFIX)) traitId = PetDataRepo.TRAIT_PREFIX + traitId.toLowerCase();
                PetDataRepo.Trait trait = PetDataRepo.getTrait(traitId);

                if (grade < 0 || trait == null) {
                    Console.showMessage(grade < 0 ? SYNTAX : "Unknown trait. Trait ids are the id column of data/campaign/pet_traits.csv, with or without " + PetDataRepo.TRAIT_PREFIX + ".");
                    return CommandResult.BAD_SYNTAX;
                }

                String nameOrId = rest.substring(0, rest.lastIndexOf(words[words.length - 2])).trim();
                Pet pet = find(manager, nameOrId);
                if (pet == null) {
                    Console.showMessage("No living pet with that name or id.");
                    return CommandResult.ERROR;
                }

                pet.setTrait(trait.id(), grade);
                Console.showMessage(grade == 0 ? pet.name + " no longer has " + trait.name() + "." : pet.name + " has " + trait.name() + " at grade " + grade + ", read.");
                return CommandResult.SUCCESS;
            }
            case "scan" -> {
                Pet pet = rest.isEmpty() ? null : find(manager, rest);
                if (pet == null) {
                    Console.showMessage(rest.isEmpty() ? SYNTAX : "No living pet with that name or id.");
                    return rest.isEmpty() ? CommandResult.BAD_SYNTAX : CommandResult.ERROR;
                }

                pet.scan();
                Console.showMessage(pet.name + ": every talent, mutation and flaw is read.");
                return CommandResult.SUCCESS;
            }
            case "look" -> {
                int split = rest.lastIndexOf(' ');
                Pet pet = split > 0 ? find(manager, rest.substring(0, split).trim()) : null;
                String word = rest.substring(split + 1);
                if (split <= 0) {
                    Console.showMessage(SYNTAX);
                    return CommandResult.BAD_SYNTAX;
                }

                if (pet == null) {
                    Console.showMessage("No living pet with that name or id.");
                    return CommandResult.ERROR;
                }

                PetDataRepo.Look look = pet.getData().getLook(word);
                if (look == null && !word.equalsIgnoreCase("wild")) {
                    Console.showMessage("Unknown look for " + pet.getData().species + ". Look ids are the id column of data/campaign/pet_morphs.csv, or wild.");
                    return CommandResult.BAD_SYNTAX;
                }

                pet.sheet.look = look != null ? look.id() : null;
                Console.showMessage(pet.name + " is " + pet.getLookName() + " now.");
                return CommandResult.SUCCESS;
            }
            case "title" -> {
                int split = rest.lastIndexOf(' ');
                Pet pet = split > 0 ? find(manager, rest.substring(0, split).trim()) : null;
                String word = rest.substring(split + 1).toUpperCase(Locale.ROOT);
                PetTitle title = null;
                for (PetTitle t : PetTitle.values()) if (t.name().equals(word)) title = t;

                if (split <= 0 || (title == null && !word.equals("CLEAR"))) {
                    Console.showMessage(SYNTAX);
                    return CommandResult.BAD_SYNTAX;
                }

                if (pet == null) {
                    Console.showMessage("No living pet with that name or id.");
                    return CommandResult.ERROR;
                }

                if (title == null) {
                    pet.getTitleIds().clear();
                    Console.showMessage(pet.name + " has no titles now.");
                } else {
                    pet.earn(title);
                    Console.showMessage(pet.name + " has the title " + title.label + ".");
                }
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
