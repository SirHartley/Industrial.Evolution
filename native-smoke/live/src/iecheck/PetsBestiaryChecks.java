package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBestiaryFile;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

// pet 3.9: the Bestiary: seen species (a registered pet, dead or alive, or an offer seen), one known only from the common file, the file written
public final class PetsBestiaryChecks {
    private static final Logger log = Global.getLogger(PetsBestiaryChecks.class);

    private PetsBestiaryChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    private static PetShop shop(MarketAPI market) {
        if (!market.hasIndustry(Ids.PET_STORE)) market.addIndustry(Ids.PET_STORE);
        PetShop shop = (PetShop) market.getIndustry(Ids.PET_STORE);
        shop.finishBuildingOrUpgrading();
        return shop;
    }

    private static Pet pet(Ctx c, String type, String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName("ISS " + name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        Pet pet = new Pet(type, name);
        manager().register(pet);
        pet.place(member, null);
        pet.age = 400f;
        c.put(name, pet.id);
        return pet;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        // SWITCHES=fresh: a game with no pets, only the common file of an earlier run; the Bestiary opens at once
        if (System.getProperty("ie.switches", "").contains("fresh")) {
            steps.add(c -> {
                Colony.pair(c, 6);
                shop(c.market("a"));
                c.note("common file: species " + PetBestiaryFile.get().getSpecies() + ", looks " + PetBestiaryFile.get().getLooks() + ", seen here " + manager().getSeenSpecies());
                c.expect(manager().getSeenSpecies().isEmpty() && !PetBestiaryFile.get().getSpecies().isEmpty(), "a game with no pets, the common file of an earlier one");
                PetsView view = new PetsView();
                view.tab = PetsView.TAB_BEAST;
                c.keepDialog(true);
                c.expect(PetsDialogPlugin.show((PetShop) c.market("a").getIndustry(Ids.PET_STORE), view), "the Pets panel opens on the Bestiary");
                c.frames(60);
            });
            steps.add(c -> {
                log.info("LIVE_CHECK hold");
                c.frames(100000000);
            });
            return steps;
        }
        steps.add(c -> {
            Colony.pair(c, 6);
            shop(c.market("a"));
            PetShop npc = shop(Global.getSector().getEconomy().getMarket("jangala"));
            c.note("common file before: species " + PetBestiaryFile.get().getSpecies() + ", looks " + PetBestiaryFile.get().getLooks());

            Pet rex = pet(c, "doge", "Rex");
            cmd("look " + rex.id + " doge_3");
            cmd("trait " + rex.id + " giant 2");
            Pet hammy = pet(c, "hampter", "Hammy");
            manager().reportPetDied(hammy, PetStatusManager.PetDeathCause.NATURAL);
            // the Adoption tab of Jangala opened
            List<Pet> offers = npc.getOffers();
            manager().seeOffers(offers);
            // SWITCHES=allseen: every species seen on offer, for shots of every tile
            if (System.getProperty("ie.switches", "").contains("allseen")) {
                List<Pet> every = new ArrayList<>();
                for (PetData d : PetDataRepo.getAll()) every.add(new Pet(d.id, "Probe"));
                manager().seeOffers(every);
            }
            List<String> offered = new ArrayList<>();
            for (Pet o : offers) offered.add(o.typeID);
            c.put("offered", String.join(",", offered));

            Set<String> seen = manager().getSeenSpecies();
            c.note("seen " + seen + ", offered " + offered);
            c.expect(seen.contains("doge") && seen.contains("hampter") && seen.containsAll(offered), "seen: a living pet, a dead pet and every species on offer");

            // a species known only from an earlier game: in the common file, not in this game
            String earlier = null;
            List<PetData> all = new ArrayList<>(PetDataRepo.getAll());
            for (PetData d : all) if (!seen.contains(d.id) && !d.isNoSell()) {
                earlier = d.id;
                break;
            }
            c.put("earlier", earlier);
            if (earlier != null) PetBestiaryFile.get().see(Collections.singletonList(earlier), Collections.emptyList());
            manager().seeAll();
            Set<String> file = PetBestiaryFile.get().getSpecies();
            c.note("earlier-game species " + earlier + ", common file species " + file + ", looks " + PetBestiaryFile.get().getLooks());
            c.expect(file.containsAll(seen) && file.contains(earlier), "the common file holds every species seen here and the earlier one");
            c.expect(PetBestiaryFile.get().getLooks().contains("doge_3"), "the common file holds the Black look");
            c.expect(!manager().getSeenSpecies().contains(earlier), "the earlier-game species is not seen in this game");
            c.note("species total " + all.size());
            c.reload();
        });
        steps.add(c -> {
            Set<String> seen = manager().getSeenSpecies();
            List<String> offered = new ArrayList<>(List.of(((String) c.get("offered")).split(",")));
            c.expect(seen.contains("doge") && seen.contains("hampter") && seen.containsAll(offered), "after a save and load the seen species stay, offers included");
            c.expect(PetBestiaryFile.get().getSpecies().contains((String) c.get("earlier")), "the common file still holds the earlier-game species");
            String sw = System.getProperty("ie.switches", "");
            PetsView view = new PetsView((String) c.get("Rex"));
            view.tab = PetsView.TAB_BEAST;
            c.keepDialog(true);
            // SWITCHES=flight: the panel from the ability, no center
            PetShop shop = sw.contains("flight") ? null : (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            c.expect(PetsDialogPlugin.show(shop, view), "the Pets panel opens on the Bestiary" + (shop == null ? " in flight" : ""));
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
