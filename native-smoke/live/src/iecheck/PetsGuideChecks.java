package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.ui.ButtonAPI;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.listener.PetStatusManager.Unlock;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.plugins.PetCard;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

// pet guidance stages 1 and 2 (PR #243): empty texts, locked Family tree and Planner with their buttons, unlock by the first young
// and by a raised young growing up, the in flight panel, hearts and mood hover texts; holds on the Pets tab for hover shots
public final class PetsGuideChecks {
    private static final Logger log = Global.getLogger(PetsGuideChecks.class);
    static final String TREE_REASON = Unlock.TREE.reason;
    static final String PLAN_REASON = Unlock.PLAN.reason;

    private PetsGuideChecks() {
    }

    static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    static PetShop shop(Ctx c) {
        return (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
    }

    static FleetMemberAPI ship(String name) {
        FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        m.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
        m.getRepairTracker().setCR(0.7f);
        return m;
    }

    static Pet pet(Ctx c, String species, String name, float age) {
        Pet pet = new Pet(species, name);
        manager().register(pet);
        pet.place(ship("ISS " + name), null);
        pet.age = age;
        c.put(name, pet.id);
        return pet;
    }

    static Pet get(Ctx c, String name) {
        return manager().get((String) c.get(name));
    }

    static PetsView view(Ctx c, int tab, String selected) {
        PetsView v = selected == null ? new PetsView() : new PetsView((String) c.get(selected));
        v.tab = tab;
        return v;
    }

    @SuppressWarnings("unchecked")
    static void pair(PetsView v, String a, String b) {
        try {
            List<String> list = (List<String>) MethodHandles.privateLookupIn(PetsView.class, MethodHandles.lookup()).findVarHandle(PetsView.class, "pair", List.class).get(v);
            list.clear();
            list.add(a);
            list.add(b);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void planSpecies(PetsView v, String species) {
        try {
            MethodHandles.privateLookupIn(PetsView.class, MethodHandles.lookup()).findVarHandle(PetsView.class, "planSpecies", String.class).set(v, species);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // text of the card's next heart line, null at 5 hearts
    static String nextHeart(Pet pet) {
        try {
            Class<?> line = Class.forName(PetCard.class.getName() + "$HeartLine", false, PetCard.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PetCard.class, MethodHandles.lookup());
            Object next = lookup.findStatic(PetCard.class, "nextHeart", MethodType.methodType(line, Pet.class)).invoke(pet);
            return next == null ? null : (String) lookup.findVirtual(line, "text", MethodType.methodType(String.class)).invoke(next);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static String moodTip(Pet pet) {
        try {
            return (String) MethodHandles.privateLookupIn(PetCard.class, MethodHandles.lookup())
                    .findStatic(PetCard.class, "moodTip", MethodType.methodType(String.class, Pet.class, PetMood.class)).invoke(pet, pet.getMood());
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    // every button with this text, its enabled state
    static List<Boolean> buttons(Object root, String text) {
        List<Boolean> out = new ArrayList<>();
        for (String t : UiAudit.targets(UiAudit.find())) {
            if (!t.startsWith("BTN ")) continue;
            String[] parts = t.split(" ", 4);
            if (parts.length == 4 && parts[3].equals(text)) out.add(parts[1].equals("on"));
        }
        return out;
    }

    static boolean has(UiAudit.Found f, String text) {
        return UiAudit.label(f.content(), text) != null;
    }

    static void targets(String name) {
        for (String t : UiAudit.targets(UiAudit.find())) log.info("LIVE_CHECK note: TARGET " + name + ": " + t);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            if (!a.hasIndustry(Ids.PET_STORE)) a.addIndustry(Ids.PET_STORE);
            shop(c).finishBuildingOrUpgrading();
            Global.getSector().getPlayerFleet().getCargo().addCommodity("food", 2000f);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(200000f);
            cmd("lock");
            c.expect(!manager().isOpen(Unlock.TREE) && !manager().isOpen(Unlock.PLAN), "indevo_pet lock: Family tree and Planner locked");
            c.note("pets " + manager().getPets().size());
        });

        // 1 no pets, locked tabs
        UiViews.view(steps, "guide-nopets", c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_PETS, null)), (c, f) -> {
            c.expect(has(f, "No pets yet. Animal Centers sell Cryochambers and offer pets for adoption."), "no pets text");
            List<Boolean> tree = buttons(f, "Family tree"), plan = buttons(f, "Planner");
            c.expect(!tree.isEmpty() && !tree.get(0) && !plan.isEmpty() && !plan.get(0), "Family tree and Planner tabs grey (" + tree + ", " + plan + ")");
            List<Boolean> six = new ArrayList<>();
            for (String label : new String[]{"Pets 0", "Husbandry 0 / 4", "Adoption", "Bestiary"}) six.addAll(buttons(f, label));
            c.note("tabs " + six);
            targets("guide-nopets");
        });

        // 2 husbandry with only a young, then with one adult
        steps.add(c -> pet(c, "doge", "Pip", 1f));
        UiViews.view(steps, "guide-husb-noadults", c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_HUSBANDRY, null)),
                (c, f) -> c.expect(has(f, "No adults here. A pet can pair once it is no longer Young."), "Husbandry with no adults text"));
        steps.add(c -> pet(c, "doge", "Rex", 400f));
        UiViews.view(steps, "guide-husb-nopair", c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_HUSBANDRY, null)),
                (c, f) -> c.expect(has(f, "No two adults of one species here. Buy or adopt a second one."), "Husbandry with no pair text"));

        // 4 locked buttons: pet card, Bestiary, Husbandry pair card
        UiViews.view(steps, "guide-locked-card", c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_PETS, "Rex")), (c, f) -> {
            List<Boolean> tree = buttons(f, "Family tree");
            c.expect(tree.size() >= 2 && !tree.contains(true), "pet card Family tree button grey with the tab (" + tree + ")");
            targets("guide-locked-card");
        });
        UiViews.view(steps, "guide-locked-bestiary", c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_BEAST, "Rex")), (c, f) -> {
            List<Boolean> tree = buttons(f, "Family tree");
            c.expect(tree.size() >= 2 && !tree.contains(true), "Bestiary Family tree button grey (" + tree + ")");
            targets("guide-locked-bestiary");
        });
        steps.add(c -> pet(c, "doge", "Lia", 400f));
        UiViews.view(steps, "guide-locked-pair", c -> {
            PetsView v = view(c, PetsView.TAB_HUSBANDRY, null);
            pair(v, (String) c.get("Rex"), (String) c.get("Lia"));
            PetsDialogPlugin.show(shop(c), v);
        }, (c, f) -> {
            List<Boolean> tree = buttons(f, "Family tree"), add = buttons(f, "Add to plan");
            c.expect(tree.size() >= 2 && !tree.contains(true), "pair card Family tree grey (" + tree + ")");
            c.expect(add.size() == 1 && !add.get(0), "Add to plan grey (" + add + ")");
            targets("guide-locked-pair");
        });

        // SWITCHES=lockedhold: stop here, the panel left open on the Pets tab in the locked state for hovers
        if (System.getProperty("ie.switches", "").contains("lockedhold")) {
            steps.add(c -> {
                c.keepDialog(true);
                UiAudit.dismissDialog();
                c.frames(10);
            });
            steps.add(c -> {
                PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_PETS, "Rex"));
                c.frames(120);
            });
            steps.add(c -> {
                targets("guide-lockedhold");
                log.info("LIVE_CHECK hold");
                c.frames(100000000);
            });
            return steps;
        }

        // 5 the first young opens the Family tree
        steps.add(c -> {
            Pet h1 = pet(c, "hampter", "Hamone", 8f), h2 = pet(c, "hampter", "Hamtwo", 8f);
            shop(c).startBrood(h1, h2, 0);
            c.days(8f);
        });
        UiViews.view(steps, "guide-young-placed", c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_HUSBANDRY, null)), (c, f) -> {
            PetBrood b = shop(c).getBrood(0);
            c.expect(b != null && b.isReady() && !b.getYoung().isEmpty(), "the hamster brood is ready");
            if (b == null || b.getYoung().isEmpty()) return;
            Pet young = b.getYoung().get(0);
            shop(c).placeYoung(b, young, ship("ISS Firstborn"), false);
            int n = 0;
            while (!b.getYoung().isEmpty()) shop(c).placeYoung(b, b.getYoung().get(0), ship("ISS Litter" + (++n)), false);
            c.put("Young", young.id);
            c.expect(manager().isOpen(Unlock.TREE) && !manager().isOpen(Unlock.PLAN), "placing the first young opens the Family tree only");
        });
        steps.add(c -> c.frames(90));
        steps.add(c -> c.shot("guide-tree-unlocked-message"));
        UiViews.view(steps, "guide-tree-open", c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_TREE, "Rex")), (c, f) -> {
            List<Boolean> tree = buttons(f, "Family tree"), plan = buttons(f, "Planner"), pp = buttons(f, "Plan pairing");
            c.expect(!tree.isEmpty() && !tree.contains(false), "Family tree tab and card button on (" + tree + ")");
            c.expect(!plan.isEmpty() && !plan.get(0) && !pp.isEmpty() && !pp.get(0), "Planner and Plan pairing still grey (" + plan + ", " + pp + ")");
            targets("guide-tree-open");
        });

        // the young grows up through the console, the Planner opens with the panel
        steps.add(c -> {
            Pet young = manager().get((String) c.get("Young"));
            cmd("age " + young.id + " " + (int) Math.ceil(young.getYoungDays() - young.age + 1f));
            c.note("young " + young.name + " age " + young.age + ", young " + young.isYoung());
            c.expect(!young.isYoung(), "indevo_pet age takes the young past Young");
        });
        UiViews.view(steps, "guide-planner-open", c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_TREE, "Rex")), (c, f) -> {
            c.expect(manager().isOpen(Unlock.PLAN), "opening the panel after the young grew up opens the Planner");
            List<Boolean> plan = buttons(f, "Planner"), pp = buttons(f, "Plan pairing");
            c.expect(!plan.isEmpty() && plan.get(0) && !pp.isEmpty() && pp.get(0), "Planner tab and Plan pairing on (" + plan + ", " + pp + ")");
        });
        steps.add(c -> c.frames(90));
        steps.add(c -> c.shot("guide-planner-unlocked-message"));

        // 2 Planner with no plans
        UiViews.view(steps, "guide-planner-empty", c -> {
            PetsView v = view(c, PetsView.TAB_PLAN, null);
            planSpecies(v, "doge");
            PetsDialogPlugin.show(shop(c), v);
        }, (c, f) -> c.expect(has(f, "No plans for this species. Use New plan, or Plan pairing on a pet in the Family tree."), "Planner with no plans text"));

        // 6 in flight through the ability: no center
        UiViews.view(steps, "guide-flight", c -> PetsDialogPlugin.show(null, view(c, PetsView.TAB_PETS, "Rex")), (c, f) -> {
            List<Boolean> husb = buttons(f, "Husbandry"), adopt = buttons(f, "Adoption");
            c.expect(husb.size() == 1 && !husb.get(0) && adopt.size() == 1 && !adopt.get(0), "in flight: Husbandry and Adoption grey (" + husb + ", " + adopt + ")");
            targets("guide-flight");
        });

        // 1 hearts and mood texts
        steps.add(c -> {
            Pet fresh = pet(c, "doge", "Newbie", 300f);
            Pet three = pet(c, "doge", "Threeheart", 300f);
            cmd("hearts " + three.id + " 3");
            three.setFavourite(false);
            Pet four = pet(c, "doge", "Fourheart", 300f);
            for (Pet p : manager().getFavourites()) if (p != four) p.setFavourite(false);
            four.setFavourite(true);
            cmd("hearts " + four.id + " 4");
            Pet moved = pet(c, "doge", "Moved", 300f);
            moved.unsettledDays = 3f;
            Pet sup = pet(c, "doge", "Supplied", 300f);
            sup.lastMealSupplies = true;

            for (Pet p : List.of(fresh, three, four, moved, sup))
                c.note("TIP " + p.name + ": hearts " + p.getHearts() + ", fav " + p.favourite + ", mood " + p.getMood() + " | next: " + nextHeart(p) + " | mood tip: " + moodTip(p));
            c.expect(nextHeart(fresh).startsWith("Next heart in "), "fresh pet: next heart in days");
            c.expect(nextHeart(three).equals("Star it to pass 3 hearts"), "3 hearts, not a favourite: star it");
            c.expect(nextHeart(four).startsWith("Answer its wants, about "), "favourite at 4: answer its wants");
            c.expect(nextHeart(sup).startsWith("Next heart in ") && nextHeart(sup).endsWith(" (Supplies)"), "on Supplies: next heart line names it");
            c.expect(nextHeart(moved).equals("Paused while Unsettled") && moodTip(moved).equals("Moved to a new ship. Its talents are off for 3 days."), "Unsettled: hearts and mood tip");
            c.expect(moodTip(sup) != null && moodTip(sup).startsWith("It ate Supplies. Hearts grow at "), "on Supplies: mood tip");
        });

        // hold on the Pets tab for hovers
        steps.add(c -> {
            c.keepDialog(true);
            UiAudit.dismissDialog();
            c.frames(10);
        });
        steps.add(c -> {
            PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_PETS, "Newbie"));
            c.frames(120);
        });
        steps.add(c -> {
            targets("guide-hold");
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
