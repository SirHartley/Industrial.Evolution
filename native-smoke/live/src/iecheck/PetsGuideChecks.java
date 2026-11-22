package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import indevo.config.PlayerSettings;
import indevo.config.Setting;
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
import indevo.industries.petshop.plugins.PetsPanel;
import indevo.industries.petshop.plugins.PetsView;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

// pet guidance stages 1 and 2 (PR #243): empty texts, locked Family tree and Planner with their buttons, unlock by the first young
// and by a raised young growing up, the in flight panel, hearts and mood hover texts; holds on the Pets tab for hover shots
// stage 3 (PR #247): the card's next heart line in every state, the ? glow per tab, the Husbandry tour and IndEvo_petTour
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

    // part of the card's next heart line (text, number or color), null at 5 hearts
    static Object heartLine(Pet pet, String part, Class<?> type) {
        try {
            Class<?> line = Class.forName(PetCard.class.getName() + "$HeartLine", false, PetCard.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PetCard.class, MethodHandles.lookup());
            Object next = lookup.findStatic(PetCard.class, "nextHeart", MethodType.methodType(line, Pet.class)).invoke(pet);
            return next == null ? null : lookup.findVirtual(line, part, MethodType.methodType(type)).invoke(next);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static String nextHeart(Pet pet) {
        return (String) heartLine(pet, "text", String.class);
    }

    static Color heartColor(Pet pet) {
        return (Color) heartLine(pet, "color", Color.class);
    }

    static void starving(Pet pet, boolean on) {
        try {
            MethodHandles.privateLookupIn(Pet.class, MethodHandles.lookup()).findVarHandle(Pet.class, "isStarving", boolean.class).set(pet, on);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static PetsDialogPlugin plugin() {
        InteractionDialogAPI d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        return d != null && d.getPlugin() instanceof PetsDialogPlugin plugin ? plugin : null;
    }

    static PetsPanel panel() {
        PetsDialogPlugin plugin = plugin();
        if (plugin == null) return null;
        try {
            return (PetsPanel) MethodHandles.privateLookupIn(PetsDialogPlugin.class, MethodHandles.lookup()).findVarHandle(PetsDialogPlugin.class, "panel", PetsPanel.class).get(plugin);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static VarHandle handle(Class<?> owner, String name, Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findVarHandle(owner, name, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // the running tour step 1 to 4, 0 before it starts, -1 once it ended, -9 with no panel
    static int tourStep() {
        PetsPanel p = panel();
        if (p == null) return -9;
        try {
            Class<?> type = Class.forName("indevo.industries.petshop.plugins.PetTour", false, PetsPanel.class.getClassLoader());
            Object tour = handle(PetsPanel.class, "tour", type).get(p);
            int step = (int) handle(type, "step", int.class).get(tour);
            float end = (float) handle(type, "end", float.class).get(tour);
            return end >= 0f ? -1 : step;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // the tab whose ? pulses, -1 for none, -9 with no panel
    static int helpTab() {
        PetsPanel p = panel();
        return p == null ? -9 : (int) handle(PetsPanel.class, "helpTab", int.class).get(p);
    }

    static boolean tourSetting() {
        Boolean stored = lunalib.lunaSettings.LunaSettings.getBoolean(PlayerSettings.MOD_ID, Setting.PET_TOUR.id);
        return Setting.PET_TOUR.enabled() && Boolean.TRUE.equals(stored);
    }

    static String tourLine() {
        return "tour step " + tourStep() + ", help tab " + helpTab() + ", IndEvo_petTour " + Setting.PET_TOUR.enabled() + " / luna "
                + lunalib.lunaSettings.LunaSettings.getBoolean(PlayerSettings.MOD_ID, Setting.PET_TOUR.id);
    }

    static void labels(Object node, String text, List<LabelAPI> out) {
        if (node instanceof LabelAPI l && text.equals(l.getText())) out.add(l);
        for (Object child : UiAudit.children(node)) labels(child, text, out);
    }

    static void buttonsIn(Object node, List<ButtonAPI> out) {
        if (node instanceof ButtonAPI b) out.add(b);
        for (Object child : UiAudit.children(node)) buttonsIn(child, out);
    }

    // runs the enabled button under the n-th label that reads exactly text: habitat and adult rows
    static boolean clickNth(Object root, String text, int n) {
        List<LabelAPI> found = new ArrayList<>();
        labels(root, text, found);
        if (found.size() <= n) return false;
        PositionAPI lp = found.get(n).getPosition();
        float x = lp.getCenterX(), y = lp.getCenterY();
        List<ButtonAPI> all = new ArrayList<>();
        buttonsIn(root, all);
        for (ButtonAPI b : all) {
            PositionAPI p = b.getPosition();
            if (b.isEnabled() && b.getCustomData() instanceof Runnable action && x >= p.getX() && x <= p.getX() + p.getWidth() && y >= p.getY() && y <= p.getY() + p.getHeight()) {
                action.run();
                return true;
            }
        }
        return false;
    }

    static Object root() {
        UiAudit.Found f = UiAudit.find();
        return f == null ? null : f.content();
    }

    // opens the panel fresh on tab, after the old one is gone
    static void reopen(List<Step> steps, int tab, String selected) {
        steps.add(c -> {
            c.keepDialog(true);
            UiAudit.dismissDialog();
            c.frames(10);
        });
        steps.add(c -> {
            PetsDialogPlugin.show(shop(c), view(c, tab, selected));
            c.frames(120);
        });
    }

    static void shot(List<Step> steps, String name) {
        steps.add(c -> {
            targets(name);
            c.frames(15);
        });
        steps.add(c -> c.shot(name));
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
            // the tour defaults on and would cover the stage 1 Husbandry views
            Colony.setting(Setting.PET_TOUR, false);
            Colony.setting(Setting.PET_MOTION, true);
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

        stage3(steps);
        if (System.getProperty("ie.switches", "").contains("tourhold")) return steps;

        // hold on the Pets tab for hovers; the lock lets the ? pulse again for the mouse over check
        steps.add(c -> cmd("lock"));
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

    static final String[] HEART_CARDS = {"Newbie", "Supplied", "Stored", "Moved", "Hungry", "Threeheart", "Fourheart", "Fiveheart"};

    static void stage3(List<Step> steps) {
        boolean tourHold = System.getProperty("ie.switches", "").contains("tourhold");

        // card next heart line: every state, the paused ones in the negative colour
        steps.add(c -> {
            Pet stored = pet(c, "doge", "Stored", 300f);
            FleetMemberAPI ship = stored.assignedFleetMember;
            stored.place(null, shop(c));
            if (ship != null) Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(ship);
            starving(pet(c, "doge", "Hungry", 300f), true);
            Pet five = pet(c, "doge", "Fiveheart", 300f);
            five.setFavourite(true);
            cmd("hearts " + five.id + " 5");

            Color neg = indevo.utils.ui.Ui.neg(), gray = indevo.utils.ui.Ui.gray();
            for (String name : HEART_CARDS) {
                Pet p = get(c, name);
                c.note("HEART " + name + ": hearts " + p.getHearts() + ", fav " + p.favourite + ", aboard " + p.isAboard() + " | " + nextHeart(p) + " | "
                        + heartLine(p, "number", String.class) + " | " + heartColor(p));
            }
            c.expect(nextHeart(get(c, "Newbie")).matches("Next heart in \\d+ days?") && gray.equals(heartColor(get(c, "Newbie"))), "fresh: Next heart in N days, grey");
            c.expect(nextHeart(get(c, "Supplied")).matches("Next heart in \\d+ days? \\(Supplies\\)"), "on Supplies: (Supplies)");
            c.expect("Grows only on a ship".equals(nextHeart(stored)) && gray.equals(heartColor(stored)), "stored: Grows only on a ship, grey");
            c.expect("Paused while Unsettled".equals(nextHeart(get(c, "Moved"))) && neg.equals(heartColor(get(c, "Moved"))), "Unsettled: paused, negative colour");
            c.expect("Paused while starving".equals(nextHeart(get(c, "Hungry"))) && neg.equals(heartColor(get(c, "Hungry"))), "starving: paused, negative colour");
            c.expect(five.getHearts() == 5 && nextHeart(five) == null, "5 hearts: no line (" + five.getHearts() + ")");
            String days = (String) heartLine(get(c, "Newbie"), "number", String.class);
            c.expect(days != null && days.matches("\\d+") && nextHeart(get(c, "Newbie")).contains(days), "the day count is the highlighted number (" + days + ")");
        });
        if (!tourHold) for (String name : HEART_CARDS) {
            // capture names drop capitals
            UiViews.view(steps, "guide-heart-" + name.toLowerCase(),c -> PetsDialogPlugin.show(shop(c), view(c, PetsView.TAB_PETS, name)), (c, f) -> {
                String line = nextHeart(get(c, name));
                if (line != null) c.expect(has(f, line), name + " card shows \"" + line + "\"");
                else c.expect(!has(f, "Next heart") && !has(f, "Star it") && !has(f, "Answer its wants"), name + " card shows no heart line");
            });
        }

        // ? glow: once per tab, the first time it opens; a tab change ends it
        if (!tourHold) {
            steps.add(c -> cmd("lock"));
            reopen(steps, PetsView.TAB_PETS, "Newbie");
            steps.add(c -> c.expect(helpTab() == PetsView.TAB_PETS, "first Pets tab: the ? pulses (" + tourLine() + ")"));
            shot(steps, "guide-glow-pets-a");
            shot(steps, "guide-glow-pets-b");
            steps.add(c -> {
                c.expect(UiAudit.clickPrefix(root(), "Husbandry"), "click the Husbandry tab");
                c.frames(30);
            });
            steps.add(c -> c.expect(helpTab() == PetsView.TAB_HUSBANDRY, "first Husbandry tab: its ? pulses, the Pets one stopped (" + tourLine() + ")"));
            shot(steps, "guide-glow-husb");
            steps.add(c -> {
                c.expect(UiAudit.clickPrefix(root(), "Pets"), "click the Pets tab");
                c.frames(30);
            });
            steps.add(c -> c.expect(helpTab() == -1, "Pets tab again: no pulse (" + tourLine() + ")"));
            shot(steps, "guide-glow-pets-again");
            reopen(steps, PetsView.TAB_PETS, "Newbie");
            steps.add(c -> c.expect(helpTab() == -1, "panel opened again: no pulse (" + tourLine() + ")"));

            // IndEvo_petMotion off: the ? is still and bright
            steps.add(c -> {
                Colony.setting(Setting.PET_MOTION, false);
                cmd("lock");
            });
            reopen(steps, PetsView.TAB_PETS, "Newbie");
            steps.add(c -> c.expect(helpTab() == PetsView.TAB_PETS, "motion off: the ? lights (" + tourLine() + ")"));
            shot(steps, "guide-glow-still-a");
            shot(steps, "guide-glow-still-b");
            steps.add(c -> Colony.setting(Setting.PET_MOTION, true));
        }

        // Husbandry tour, the help seen set cleared so the ? would pulse if the tour let it
        steps.add(c -> {
            cmd("lock");
            Colony.setting(Setting.PET_TOUR, true);
            c.note("broods " + shop(c).getBroods().size() + " of " + shop(c).getHabitatCount() + " habitats");
            c.expect(tourSetting(), "IndEvo_petTour on");
        });
        if (tourHold) {
            reopen(steps, PetsView.TAB_HUSBANDRY, null);
            steps.add(c -> {
                c.note(tourLine());
                targets("guide-tourhold");
                log.info("LIVE_CHECK hold");
                c.frames(100000000);
            });
            return;
        }

        // Next through all four steps, then Done
        reopen(steps, PetsView.TAB_HUSBANDRY, null);
        steps.add(c -> {
            c.expect(tourStep() == 1, "Husbandry opens on tour step 1 (" + tourLine() + ")");
            c.expect(helpTab() == -1, "no ? pulse under the tour (" + tourLine() + ")");
            c.expect(!buttons(root(), "Next").isEmpty() && !buttons(root(), "Skip").isEmpty() && buttons(root(), "Done").isEmpty(), "step 1 has Next and Skip");
            c.expect(has(UiAudit.find(), "Two pets in one habitat raise a spawn. Pick a free one."), "step 1 text");
        });
        shot(steps, "guide-tour-1");
        String[] texts = {null, null, "Pick two adults of one species. No match shows pets without a partner here.","These set the odds for each young.", "Pair starts the incubation. Pick two adults first."};
        for (int s = 2; s <= 4; s++) {
            int step = s;
            steps.add(c -> {
                c.expect(UiAudit.click(root(), "Next"), "click Next to step " + step);
                c.frames(20);
            });
            steps.add(c -> {
                c.expect(tourStep() == step, "tour step " + step + " (" + tourLine() + ")");
                c.expect(has(UiAudit.find(), texts[step]), "step " + step + " text: " + texts[step]);
                c.expect(tourSetting(), "IndEvo_petTour still on mid tour");
            });
            shot(steps, "guide-tour-" + step);
        }
        steps.add(c -> {
            c.expect(buttons(root(), "Next").isEmpty() && buttons(root(), "Skip").isEmpty() && buttons(root(), "Done").size() == 1, "step 4 has only Done");
            c.expect(UiAudit.click(root(), "Done"), "click Done");
            c.frames(30);
        });
        steps.add(c -> {
            c.expect(tourStep() == -1, "Done ends the tour (" + tourLine() + ")");
            c.expect(!tourSetting() && Boolean.FALSE.equals(lunalib.lunaSettings.LunaSettings.getBoolean(PlayerSettings.MOD_ID, Setting.PET_TOUR.id)), "Done switches IndEvo_petTour off in LunaLib");
            c.note("after the tour: " + tourLine());
        });
        shot(steps, "guide-tour-done");
        reopen(steps, PetsView.TAB_HUSBANDRY, null);
        steps.add(c -> c.expect(tourStep() == 0, "setting off: no tour on the next opening (" + tourLine() + ")"));

        // Skip
        steps.add(c -> Colony.setting(Setting.PET_TOUR, true));
        reopen(steps, PetsView.TAB_HUSBANDRY, null);
        steps.add(c -> {
            c.expect(tourStep() == 1 && UiAudit.click(root(), "Skip"), "click Skip on step 1");
            c.frames(30);
        });
        steps.add(c -> c.expect(tourStep() == -1 && !tourSetting(), "Skip ends the tour and switches the setting off (" + tourLine() + ")"));

        // escape: the dialog's close ends the tour and keeps the panel
        steps.add(c -> Colony.setting(Setting.PET_TOUR, true));
        reopen(steps, PetsView.TAB_HUSBANDRY, null);
        steps.add(c -> {
            c.expect(tourStep() == 1, "tour on step 1 again");
            plugin().close();
            c.frames(30);
        });
        steps.add(c -> {
            c.expect(tourStep() == -1 && !tourSetting(), "escape ends the tour and switches the setting off (" + tourLine() + ")");
            c.expect(panel() != null && panel().isOpen(), "escape leaves the panel open");
        });
        shot(steps, "guide-tour-escaped");

        // the panel closed mid tour keeps the setting and starts again on the next opening
        steps.add(c -> Colony.setting(Setting.PET_TOUR, true));
        reopen(steps, PetsView.TAB_HUSBANDRY, null);
        steps.add(c -> {
            c.expect(UiAudit.click(root(), "Next"), "Next to step 2");
            c.frames(20);
        });
        steps.add(c -> {
            c.expect(tourStep() == 2, "tour on step 2 before closing");
            UiAudit.close(UiAudit.find());
            c.frames(30);
        });
        steps.add(c -> {
            UiAudit.dismissDialog();
            c.frames(10);
        });
        steps.add(c -> c.expect(tourSetting(), "closing the panel mid tour keeps IndEvo_petTour on (" + tourLine() + ")"));
        reopen(steps, PetsView.TAB_HUSBANDRY, null);
        steps.add(c -> c.expect(tourStep() == 1, "the next opening starts the tour on step 1 (" + tourLine() + ")"));

        // the tour follows the player: a free habitat, two adults, the chart lit with them, Pair starts the brood and ends it
        steps.add(c -> {
            c.note("habitat labels: Empty x" + count(root(), "Empty"));
            c.expect(clickNth(root(), "Empty", 1), "click the second free habitat");
            c.frames(20);
        });
        steps.add(c -> c.expect(tourStep() == 2, "picking a habitat moves to step 2 (" + tourLine() + ")"));
        shot(steps, "guide-tour-act-habitat");
        steps.add(c -> {
            c.expect(clickNth(root(), "Rex", 0), "pick Rex");
            c.frames(20);
        });
        steps.add(c -> {
            c.expect(tourStep() == 2, "one adult keeps step 2");
            c.expect(clickNth(root(), "Lia", 0), "pick Lia");
            c.frames(20);
        });
        steps.add(c -> c.expect(tourStep() == 3, "two adults move to step 3 (" + tourLine() + ")"));
        shot(steps, "guide-tour-act-dials");
        steps.add(c -> {
            c.expect(UiAudit.click(root(), "Next"), "Next to step 4");
            c.frames(20);
        });
        steps.add(c -> {
            c.expect(tourStep() == 4 && has(UiAudit.find(), "Pair starts the incubation. It takes "), "step 4 names the days (" + tourLine() + ")");
            c.put("broods", shop(c).getBroods().size());
        });
        shot(steps, "guide-tour-act-pair");
        steps.add(c -> {
            c.expect(UiAudit.click(root(), "Pair"), "click Pair");
            c.frames(30);
        });
        steps.add(c -> {
            c.expect(shop(c).getBroods().size() == (int) c.get("broods") + 1, "Pair starts the brood (" + shop(c).getBroods().size() + ")");
            c.expect(tourStep() == -1 && !tourSetting(), "Pair ends the tour and switches the setting off (" + tourLine() + ")");
        });
        shot(steps, "guide-tour-act-paired");

        // motion off: the lit line is still and bright
        steps.add(c -> {
            Colony.setting(Setting.PET_MOTION, false);
            Colony.setting(Setting.PET_TOUR, true);
        });
        reopen(steps, PetsView.TAB_HUSBANDRY, null);
        steps.add(c -> c.expect(tourStep() == 1, "motion off: tour on step 1"));
        shot(steps, "guide-tour-still-a");
        shot(steps, "guide-tour-still-b");
        steps.add(c -> {
            UiAudit.click(root(), "Skip");
            Colony.setting(Setting.PET_MOTION, true);
            c.frames(20);
        });
        steps.add(c -> c.note("end of tour checks: " + tourLine()));
    }

    static int count(Object root, String text) {
        List<LabelAPI> found = new ArrayList<>();
        labels(root, text, found);
        return found.size();
    }
}
