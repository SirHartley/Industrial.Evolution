package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 3.3: habitats and broods at player and npc Animal Centers, the young, inbreeding
public final class PetsBroodChecks {
    private static final Logger log = Global.getLogger(PetsBroodChecks.class);

    private PetsBroodChecks() {
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

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    // an adult on its own ship
    private static Pet adult(Ctx c, String species, String name, float age) {
        Pet pet = new Pet(species, name);
        manager().register(pet);
        pet.place(ship("ISS " + name), null);
        pet.age = age;
        c.put(name, pet.id);
        return pet;
    }

    private static Pet get(Ctx c, String key) {
        return manager().get((String) c.get(key));
    }

    private static PetShop shop(MarketAPI market) {
        if (!market.hasIndustry(Ids.PET_STORE)) market.addIndustry(Ids.PET_STORE);
        PetShop shop = (PetShop) market.getIndustry(Ids.PET_STORE);
        shop.finishBuildingOrUpgrading();
        return shop;
    }

    private static PetShop playerShop(Ctx c) {
        return (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
    }

    private static PetShop npcShop() {
        return (PetShop) Global.getSector().getEconomy().getMarket("jangala").getIndustry(Ids.PET_STORE);
    }

    private static PetShop betaShop(Ctx c) {
        return (PetShop) c.market("b").getIndustry(Ids.PET_STORE);
    }

    private static String young(PetBrood brood) {
        List<String> list = new ArrayList<>();
        for (Pet p : brood.getYoung()) list.add(p.name + (p.sheet.malformed ? " malformed" : "") + " " + p.sheet.traits.keySet());
        return list.toString();
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            PetShop own = shop(c.market("a"));
            PetShop npc = shop(Global.getSector().getEconomy().getMarket("jangala"));
            PetShop beta = shop(c.market("b"));
            beta.setAICoreId(Commodities.BETA_CORE);
            Global.getSector().getPlayerFleet().getCargo().addCommodity("food", 1000f);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(100000f);

            c.expect(own.getHabitatCount() == 4 && own.getFee() == 0f, "player center: 4 habitats, no fee (" + own.getHabitatCount() + ", " + own.getFee() + ")");
            c.expect(npc.getHabitatCount() == 2 && npc.getFee() == 3000f, "npc center: 2 habitats, 3,000 a month (" + npc.getHabitatCount() + ", " + npc.getFee() + ")");

            // blocks
            Pet pup = adult(c, "doge", "Pup", 1f);
            c.expect("Still Young.".equals(own.parentBlock(pup)), "a Young pet cannot brood (" + own.parentBlock(pup) + ")");
            Pet rex = adult(c, "doge", "Rex", 400f);
            Pet ham = adult(c, "hampter", "Ham", 8f);
            c.expect(own.parentBlock(rex) == null && "Two adults of one species.".equals(own.pairBlock(rex, ham)), "two species cannot brood (" + own.pairBlock(rex, ham) + ")");
            Pet away = adult(c, "doge", "Away", 400f);
            away.place(null, npc);
            c.expect(own.parentBlock(away) != null && own.parentBlock(away).startsWith("In storage at"), "a pet in storage elsewhere cannot brood here (" + own.parentBlock(away) + ")");

            // a doge brood at the player center
            Pet lia = adult(c, "doge", "Lia", 400f);
            own.startBrood(rex, lia, 0);
            PetBrood db = own.getBrood(0);
            c.expect(db != null && rex.storage == own && lia.storage == own && rex.assignedFleetMember == null, "brood started: the parents are held by the center");
            c.note("doge brood days " + db.days + " (pace " + rex.getData().pace + ")");
            c.expect(db.days == 30f || db.days == 60f || db.days == 90f, "doge brood days by pace (" + db.days + ")");
            c.put("doge0", "set");

            // a hamster brood
            Pet ham2 = adult(c, "hampter", "Ham2", 8f);
            own.startBrood(ham, ham2, 1);
            c.near(own.getBrood(1).days, 7f, 0.01f, "hamster brood days 7");

            // cancel: parents to storage here, Unsettled
            Pet c1 = adult(c, "doge", "Can1", 400f);
            Pet c2 = adult(c, "doge", "Can2", 400f);
            own.startBrood(c1, c2, 2);
            own.cancelBrood(own.getBrood(2));
            c.expect(own.getBrood(2) == null && c1.storage == own && c2.storage == own && c1.isUnsettled() && c2.isUnsettled(), "cancel: the parents go to storage here, Unsettled (" + c1.unsettledDays + ")");

            // siblings: two adults of one pair of registered parents
            Pet mom = adult(c, "doge", "Mom", 600f);
            Pet dad = adult(c, "doge", "Dad", 600f);
            Pet sis = adult(c, "doge", "Sis", 400f);
            Pet bro = adult(c, "doge", "Bro", 400f);
            sis.parentA = mom.id;
            sis.parentB = dad.id;
            bro.parentA = mom.id;
            bro.parentB = dad.id;
            c.note("sibling inbreeding " + PetShop.getInbreeding(sis, bro) + ", unrelated " + PetShop.getInbreeding(rex, lia));
            c.expect(PetShop.getInbreeding(sis, bro) > 0f && PetShop.getInbreeding(rex, lia) == 0f, "siblings are related, Rex and Lia are not");
            own.startBrood(sis, bro, 3);

            // npc center: two habitats, fee
            Pet n1 = adult(c, "doge", "Npc1", 400f);
            Pet n2 = adult(c, "doge", "Npc2", 400f);
            npc.startBrood(n1, n2, 0);
            c.expect(npc.getBrood(0) != null, "a brood at the npc center");

            // Beta core: parents in a habitat do not age
            Pet b1 = adult(c, "doge", "Beta1", 400f);
            Pet b2 = adult(c, "doge", "Beta2", 400f);
            beta.startBrood(b1, b2, 0);
            c.put("rexAge", rex.age);
            c.put("b1Age", b1.age);
            c.days(8f);
        });
        steps.add(c -> {
            PetShop own = playerShop(c);
            PetBrood hb = own.getBrood(1);
            c.expect(hb != null && hb.isReady() && hb.getYoung().size() >= 1 && hb.getYoung().size() <= 3, "hamster brood ready after 7 days with 1 to 3 young (" + (hb == null ? "none" : young(hb)) + ")");
            c.expect(get(c, "Ham").storage == null || get(c, "Ham").storage == own, "hamster parents released (" + get(c, "Ham").storage + ")");
            if (hb != null && hb.isReady()) {
                List<Pet> ys = new ArrayList<>(hb.getYoung());
                Pet first = ys.get(0);
                FleetMemberAPI to = ship("ISS Young Home");
                own.placeYoung(hb, first, to, false);
                c.expect(first.assignedFleetMember == to && manager().get(first.id) == first && first.isYoung(), "a young placed on a ship is registered and Young (" + first.age + " days)");
                c.expect(first.parentA != null && first.parentB != null, "a young knows its parents");
                if (ys.size() > 1) {
                    own.placeYoung(hb, ys.get(1), null, false);
                    c.expect(ys.get(1).storage == own, "a young placed in storage is in storage here");
                }
                for (int i = 2; i < ys.size(); i++) own.placeYoung(hb, ys.get(i), null, false);
                c.expect(own.getBrood(1) == null, "the habitat is free once every young is placed");
            }
            c.days(54f);
        });
        steps.add(c -> {
            PetShop own = playerShop(c);
            Pet rex = get(c, "Rex");
            c.expect(rex.age > (Float) c.get("rexAge") + 50f, "a parent at a center without a Beta core ages (" + rex.age + ")");
            Pet b1 = get(c, "Beta1");
            c.near(b1.age, (Float) c.get("b1Age"), 0.01f, "a Beta core stops ageing in habitats");
            PetBrood db = own.getBrood(0);
            c.expect(db != null && db.isReady() && db.getYoung().size() >= 1 && db.getYoung().size() <= 3, "doge brood ready after its days with 1 to 3 young (" + (db == null ? "none" : young(db)) + ")");
            PetBrood sb = own.getBrood(3);
            int malformed = 0, total = 0;
            if (sb != null) for (Pet y : sb.getYoung()) {
                total++;
                if (y.sheet.malformed) malformed++;
            }
            int plain = 0;
            if (db != null) for (Pet y : db.getYoung()) if (y.sheet.malformed) plain++;
            c.note("sibling brood " + (sb == null ? "none" : young(sb)) + ", malformed " + malformed + " of " + total);
            c.expect(plain == 0, "no Malformed young from unrelated parents");
            c.put("sibMalformed", (float) malformed);
            // npc fee at the month end
            int day = Global.getSector().getClock().getDay();
            c.days(32f - day + 1.5f);
        });
        steps.add(c -> {
            MonthlyReport report = SharedData.getData().getPreviousReport();
            List<String> names = new ArrayList<>();
            collect(report.getRoot(), names, "");
            String fee = names.stream().filter(s -> s.toLowerCase().contains("habitat")).findFirst().orElse("none");
            c.note("month report lines with habitat: " + fee);
            c.expect(fee.contains("3000") || fee.contains("3,000"), "the npc center bills 3,000 for one busy habitat (" + fee + ")");
            c.keepDialog(true);
            c.expect(indevo.industries.petshop.plugins.PetsDialogPlugin.show(playerShop(c), new indevo.industries.petshop.plugins.PetsView()), "the Pets panel of the player center opens");
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }

    private static void collect(MonthlyReport.FDNode node, List<String> out, String path) {
        if (node == null) return;
        String here = path + "/" + node.name;
        if (node.upkeep > 0f || node.income > 0f) out.add(here + " " + Math.round(node.upkeep));
        for (MonthlyReport.FDNode child : node.getChildren().values()) collect(child, out, here);
    }
}
