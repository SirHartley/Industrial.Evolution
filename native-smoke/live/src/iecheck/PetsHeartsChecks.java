package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.OptionId;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

// pet 2.1: hearts from time aboard, the mascot effect, the nest, escape pods in a real battle
public final class PetsHeartsChecks {
    private static final Logger log = Global.getLogger(PetsHeartsChecks.class);
    static final OptionId[] INTO_BATTLE = {OptionId.ENGAGE, OptionId.PURSUE, OptionId.CONTINUE_INTO_BATTLE, OptionId.INITIATE_BATTLE};
    static final OptionId[] OUT_OF_BATTLE = {OptionId.RECOVERY_CONTINUE, OptionId.LEAVE, OptionId.CONTINUE_LEAVE, OptionId.CONTINUE};
    static FleetInteractionDialogPluginImpl fid;
    static CampaignFleetAPI parked;

    private PetsHeartsChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static FleetMemberAPI ship(String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static Pet pet(String species, String name, FleetMemberAPI on) {
        Pet pet = new Pet(species, name);
        manager().register(pet);
        pet.place(on, null);
        return pet;
    }

    private static Pet get(Ctx c, String key) {
        return manager().get((String) c.get(key));
    }

    private static void hearts(Pet pet, int n) {
        new PetCommand().runCommand("hearts " + pet.id + " " + n, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    // the CR decay bonus the pet hullmod puts on its ship, 0 when there is none
    private static float mascot(Pet pet) {
        FleetMemberAPI member = pet.assignedFleetMember;
        member.updateStats();
        MutableStat.StatMod mod = member.getStats().getCRLossPerSecondPercent().getPercentBonus(pet.getData().hullmod);
        return mod == null ? 0f : mod.value;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            PetShop shop = (PetShop) (c.market("a").hasIndustry(Ids.PET_STORE) ? c.market("a").getIndustry(Ids.PET_STORE) : null);
            if (shop == null) {
                c.market("a").addIndustry(Ids.PET_STORE);
                shop = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            }
            shop.finishBuildingOrUpgrading();
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            cargo.addCommodity("food", 2000f);
            for (String ore : new String[]{"ore", "rare_ore", "metals", "rare_metals", "IndEvo_parts", "heavy_machinery"})
                cargo.removeCommodity(ore, cargo.getCommodityQuantity(ore));

            Pet bond = pet("doge", "Bond", ship("ISS Bond Home"));
            Pet roam = pet("doge", "Roam", null);
            Pet box = pet("doge", "Box", ship("ISS Box Home"));
            box.place(null, shop);
            Pet mover = pet("doge", "Mover", ship("ISS Mover Old"));
            mover.place(ship("ISS Mover New"), null);
            Pet starve = pet("slaghound", "Starve", ship("ISS Starve Home"));
            MethodHandles.privateLookupIn(Pet.class, MethodHandles.lookup()).findVarHandle(Pet.class, "isStarving", boolean.class).set(starve, true);
            Pet slow = pet("doge", "Slow", ship("ISS Slow Home"));
            slow.lastMealSupplies = true;
            for (String k : new String[]{"bond", "roam", "box", "mover", "starve", "slow"}) c.put(k, manager().getPets().stream().filter(p -> p.name.equalsIgnoreCase(k)).findFirst().get().id);

            c.expect(bond.getHearts() == 0 && mascot(bond) == 0f, "a new pet has 0 hearts and no mascot effect (" + mascot(bond) + ")");
            c.near(bond.getDaysToHeart(), 14f, 0.01f, "first heart in 14 days aboard");
            c.near(slow.getDaysToHeart(), 56f, 0.01f, "after a Supplies meal the first heart takes 4x as long");
            c.days(14.5f);
        });
        steps.add(c -> {
            Pet bond = get(c, "bond");
            c.expect(bond.getHearts() == 1, "heart 1 after 14 days aboard (" + bond.bondDays + " bond days)");
            c.near(mascot(bond), -5f, 0.01f, "mascot effect -5% CR decay at 1 heart");
            c.near(get(c, "slow").bondDays, 14.5f * 0.25f, 0.3f, "a pet after a Supplies meal grows at 25%");
            c.near(get(c, "roam").bondDays, 0f, 0.001f, "no growth in the menagerie");
            c.near(get(c, "box").bondDays, 0f, 0.001f, "no growth in storage");
            c.near(get(c, "mover").bondDays, 0f, 0.001f, "no growth while Unsettled");
            c.near(get(c, "starve").bondDays, 0f, 0.001f, "no growth while starving");
            c.shot("petshearts-card-1-heart");
            c.days(46f);
        });
        steps.add(c -> {
            Pet bond = get(c, "bond");
            c.expect(bond.getHearts() == 2, "heart 2 after 60 days aboard (" + bond.bondDays + ")");
            c.expect(get(c, "mover").bondDays > 0f, "the moved pet grows once settled (" + get(c, "mover").bondDays + ")");
            c.days(121f);
        });
        steps.add(c -> {
            Pet bond = get(c, "bond");
            c.expect(bond.getHearts() == 3, "heart 3 after 180 days aboard (" + bond.bondDays + ")");
            c.days(30f);
        });
        steps.add(c -> {
            Pet bond = get(c, "bond");
            c.expect(bond.getHearts() == 3 && bond.bondDays <= 180.01f, "time alone stops at 3 hearts (" + bond.bondDays + ")");
            c.near(mascot(bond), -5f, 0.01f, "mascot still -5% at 3 hearts");

            Pet ham = pet("hampter", "Ham", ship("ISS Ham Home"));
            hearts(ham, 1);
            c.near(mascot(ham), -10f, 0.01f, "hamster mascot x2: -10%");
            hearts(ham, 4);
            c.near(mascot(ham), -13f, 0.01f, "hamster with a nest: -13%");

            hearts(bond, 4);
            c.expect(bond.hasNest() && bond.getHearts() == 4, "4 hearts on its ship: nest");
            c.near(mascot(bond), -6.5f, 0.01f, "nest x1.3: -6.5%");
            FleetMemberAPI home = bond.assignedFleetMember;
            PetShop shop = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            bond.place(null, shop);
            bond.place(home, null);
            c.expect(!bond.isUnsettled() && bond.hasNest(), "storage and back to the same ship keeps the nest, no move");
            bond.place(ship("ISS Bond New"), null);
            c.near(bond.unsettledDays, 90f, 0.01f, "moving a nest pet: Unsettled 90 days");
            c.near(bond.nestRebuildDays, 120f, 0.01f, "the nest needs 120 days to rebuild");
            c.expect(bond.getHearts() == 4 && !bond.hasNest() && mascot(bond) == 0f, "hearts stay over the move, no nest, no effect while Unsettled");
            c.days(91f);
        });
        steps.add(c -> {
            Pet bond = get(c, "bond");
            c.expect(!bond.isUnsettled() && !bond.hasNest() && bond.nestRebuildDays > 25f, "after 91 days: settled, nest still rebuilding (" + bond.nestRebuildDays + ")");
            c.near(mascot(bond), -5f, 0.01f, "settled without a nest: -5%");
            c.days(30f);
        });
        steps.add(c -> {
            Pet bond = get(c, "bond");
            c.expect(bond.hasNest(), "nest back after 120 days (" + bond.nestRebuildDays + ")");
            c.near(mascot(bond), -6.5f, 0.01f, "nest effect back: -6.5%");
            MutableStat.StatMod mod = bond.assignedFleetMember.getStats().getCRLossPerSecondPercent().getPercentBonus(bond.getData().hullmod);
            c.expect(mod != null && "Bond the Space Dog".equals(mod.desc), "the stat modifier is listed under the pet's name (" + (mod == null ? "none" : mod.desc) + ")");
            // a growing pet on Supplies for the card and hover captures: slag hounds want ore, there is none aboard
            Pet thin = pet("slaghound", "Thin", ship("ISS Thin Home"));
            hearts(thin, 1);
            thin.lastMealSupplies = true;
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(c.market("a").getPrimaryEntity());
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }

    private static void pick(Ctx c, OptionId[] options) {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (dialog == null) {
            c.note("no dialog open");
            return;
        }
        boolean picked = false;
        // the loot screen needs a click; leaving applies the same after battle effects
        if (options == OUT_OF_BATTLE && dialog.getOptionPanel().hasOption(OptionId.CONTINUE_LOOT)) {
            c.note("encounter option LEAVE instead of CONTINUE_LOOT");
            fid.optionSelected(null, OptionId.LEAVE);
            c.frames(90);
            return;
        }
        for (OptionId option : options) {
            if (dialog.getOptionPanel().hasOption(option)) {
                c.note("encounter option " + option);
                fid.optionSelected(null, option);
                picked = true;
                break;
            }
        }
        if (!picked) c.note("no matching option, dialog " + dialog.getPlugin().getClass().getSimpleName() + ", options " + Ui.text(dialog.getOptionPanel()).replace('\n', '|'));
        c.frames(90);
    }

    public static List<Step> pods() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            player.getCargo().addCommodity("food", 2000f);
            List<String> ids = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                Pet p = pet("doge", "Pod" + i, ship("ISS Pod " + i));
                hearts(p, 5);
                ids.add(p.id);
            }
            Pet low = pet("doge", "Low", ship("ISS Pod Low"));
            ids.add(low.id);
            // only the pod ships and one keeper fight, the base fleet waits in a holding fleet
            FleetMemberAPI keeper = ship("ISS Keeper");
            player.getFleetData().setFlagship(keeper);
            parked = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet check parking", true);
            for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
                if (m == keeper || m.getShipName() != null && m.getShipName().startsWith("ISS Pod")) continue;
                player.getFleetData().removeFleetMember(m);
                parked.getFleetData().addFleetMember(m);
            }
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            c.put("pods", String.join(",", ids));
            c.put("low", low.id);
            c.expect(manager().get(ids.get(0)).getHearts() == 5, "pod pets have 5 hearts");

            CampaignFleetAPI enemy = Global.getFactory().createEmptyFleet(Factions.PIRATES, "Pet check pirates", true);
            enemy.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hound_Standard"));
            enemy.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hound_Standard"));
            enemy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            enemy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
            player.getContainingLocation().addEntity(enemy);
            enemy.setLocation(player.getLocation().x + 100f, player.getLocation().y);
            enemy.getFleetData().setSyncNeeded();
            enemy.getFleetData().syncIfNeeded();

            PetsFightChecks.fought = false;
            fid = new FleetInteractionDialogPluginImpl();
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(fid, enemy);
            c.frames(60);
        });
        for (int i = 0; i < 4; i++) steps.add(c -> { if (!PetsFightChecks.fought) pick(c, INTO_BATTLE); });
        // after losses the encounter shows its own popups first; ie.switches=podhold leaves them for clicks
        if (System.getProperty("ie.switches", "").contains("podhold")) {
            steps.add(c -> {
                c.expect(PetsFightChecks.fought, "a real battle ran");
                log.info("LIVE_CHECK hold");
                c.frames(100000000);
            });
            return steps;
        }
        for (int i = 0; i < 5; i++) steps.add(c -> pick(c, OUT_OF_BATTLE));
        steps.add(c -> {
            c.expect(PetsFightChecks.fought, "a real battle ran");
            int escaped = 0, died = 0, other = 0;
            for (String id : ((String) c.get("pods")).split(",")) {
                Pet p = manager().get(id);
                if (p.isDead()) died++;
                else if (p.isInMenagerie() && p.shellShockedDays > 6f) escaped++;
                else other++;
                c.note("pod pet " + p.name + ": " + (p.isDead() ? "dead " + p.deathData.cause : p.isInMenagerie() ? "menagerie, shock " + p.shellShockedDays : "on " + (p.assignedFleetMember == null ? "?" : p.assignedFleetMember.getShipName())));
            }
            c.expect(other == 0, "every pod pet either died or escaped to the menagerie Shell shocked (" + other + " other)");
            c.expect(escaped >= 2 && died >= 2, "5-heart pets: some escape, some die (" + escaped + " escaped, " + died + " died of 13, Low included)");
            Pet low = manager().get((String) c.get("low"));
            c.expect(low.isDead(), "a 0-heart pet on a lost ship dies (" + (low.isDead() ? low.deathData.cause : "alive") + ")");
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            for (FleetMemberAPI m : parked.getFleetData().getMembersListCopy()) {
                parked.getFleetData().removeFleetMember(m);
                player.getFleetData().addFleetMember(m);
            }
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            c.keepDialog(false);
            c.frames(30);
        });
        steps.add(c -> {
            c.shot("petspods-messages");
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
