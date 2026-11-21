package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import indevo.utils.helper.MiscIE;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 3.5: gene scan cost and reading, then a held Animal Center for the reveal on the card, Husbandry and the New young popup
public final class PetsScanChecks {
    private static final Logger log = Global.getLogger(PetsScanChecks.class);

    private PetsScanChecks() {
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

    // an unread pet keeps its rolled sheet; at least one talent so there is something to read
    private static Pet pet(Ctx c, String name, boolean clean, Object... idLevel) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName("ISS " + name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        Pet pet = new Pet("doge", name);
        manager().register(pet);
        pet.place(member, null);
        pet.age = 400f;
        if (clean) for (String id : new ArrayList<>(pet.sheet.traits.keySet())) pet.setTrait(id, 0);
        for (int i = 0; i < idLevel.length; i += 2) cmd("trait " + pet.id + " " + idLevel[i] + " " + idLevel[i + 1]);
        if (!clean && pet.sheet.traits.isEmpty()) {
            cmd("trait " + pet.id + " runner 1");
            pet.sheet.known.remove("runner");
        }
        c.put(name, pet.id);
        return pet;
    }

    private static void relics(MarketAPI market, float storage, float cargo) {
        CargoAPI s = MiscIE.getStorageCargo(market);
        CargoAPI f = Global.getSector().getPlayerFleet().getCargo();
        s.removeCommodity(ItemIds.RARE_PARTS, s.getCommodityQuantity(ItemIds.RARE_PARTS));
        f.removeCommodity(ItemIds.RARE_PARTS, f.getCommodityQuantity(ItemIds.RARE_PARTS));
        s.addCommodity(ItemIds.RARE_PARTS, storage);
        f.addCommodity(ItemIds.RARE_PARTS, cargo);
    }

    private static String relicLine(MarketAPI market) {
        return "storage " + (int) MiscIE.getStorageCargo(market).getCommodityQuantity(ItemIds.RARE_PARTS) + ", cargo " + (int) Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            PetShop shop = shop(a);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(100000f);
            c.expect(MiscIE.getStorageCargo(a) != null, "the player colony has storage");

            // cost: storage first, then cargo
            Pet t1 = pet(c, "Scan1", false);
            c.expect(!t1.isRead(), "an unread pet is not read (" + t1.sheet.traits + " known " + t1.sheet.known + ")");
            relics(a, 12f, 3f);
            c.expect(shop.getRelics() == 15 && shop.canAffordScan(), "15 relics here can pay a scan (" + shop.getRelics() + ")");
            shop.geneScan(t1);
            c.expect(t1.isRead() && t1.sheet.flawsRead && t1.sheet.known.containsAll(t1.sheet.traits.keySet()), "a scan reads every trait and the flaws");
            c.expect(relicLine(a).equals("storage 2, cargo 3"), "10 relics are taken from storage first (" + relicLine(a) + ")");
            c.expect(shop.getRelics() == 5 && !shop.canAffordScan(), "5 relics cannot pay a scan");
            Pet t2 = pet(c, "Scan2", false);
            relics(a, 8f, 3f);
            shop.geneScan(t2);
            c.expect(relicLine(a).equals("storage 0, cargo 1"), "storage short: the rest comes from cargo (" + relicLine(a) + ")");
            c.expect(t2.isRead(), "the second scan reads the pet");

            // console: free
            Pet t3 = pet(c, "Scan3", false);
            relics(a, 0f, 0f);
            cmd("scan " + t3.id);
            c.expect(t3.isRead() && shop.getRelics() == 0, "indevo_pet scan reads everything for free");

            // the held panel: unread pets with the fleet, one stored elsewhere, a read and an unread parent, a ready brood
            pet(c, "Gene", true, "runner", 1, "capacitor", 2, "plating", 1, "loud", 1);
            pet(c, "Gene2", true, "runner", 2, "far_sight", 1, "ward", 1, "glutton", 1);
            pet(c, "Alpha", true, "capacitor", 3, "plating", 1, "loud", 1);
            pet(c, "Unread1", true, "capacitor", 1, "vent", 2, "sullen", 1);
            PetShop npc = shop(Global.getSector().getEconomy().getMarket("jangala"));
            Pet away = pet(c, "Away", false);
            FleetMemberAPI ship = away.assignedFleetMember;
            away.place(null, npc);
            if (ship != null) Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(ship);
            // a shared talent, so a Sum young is likely
            Pet r1 = pet(c, "Run1", true, "quick_trigger", 2, "savant", 1);
            Pet r2 = pet(c, "Run2", true, "quick_trigger", 2, "bulwark", 1);
            shop.startBrood(r1, r2, 0);
            c.expect(shop.getBrood(0) != null, "a brood in habitat 1");
            c.days(61f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            PetShop shop = (PetShop) a.getIndustry(Ids.PET_STORE);
            String sw = System.getProperty("ie.switches", "");
            c.expect(shop.getBrood(0) != null && shop.getBrood(0).isReady(), "the brood is ready");
            for (Pet y : shop.getBrood(0).getYoung()) c.note("young " + y.name + " " + y.sheet.traits + " known " + y.sheet.known + " flawsRead " + y.sheet.flawsRead);
            // SWITCHES=hideyoung: the young know nothing, so a Sum shows only after its scan
            if (sw.contains("hideyoung")) for (Pet y : shop.getBrood(0).getYoung()) y.sheet.known.clear();
            // SWITCHES=sumyoung: the first young is a plain Sum (Quick trigger 4, no mutation), so its kind shows after the scan
            if (sw.contains("sumyoung")) {
                Pet y = shop.getBrood(0).getYoung().get(0);
                if (y.sheet.from == null) y.sheet.from = new java.util.HashMap<>();
                for (String id : new ArrayList<>(y.sheet.from.keySet())) if (indevo.industries.petshop.memory.PetSheet.FROM_MUTATION.equals(y.sheet.from.get(id))) {
                    y.sheet.from.remove(id);
                    y.sheet.traits.remove(id);
                }
                y.sheet.malformed = false;
                y.sheet.traits.put("IndEvo_pt_quick_trigger", 4);
                y.sheet.from.put("IndEvo_pt_quick_trigger", indevo.industries.petshop.memory.PetSheet.FROM_SUM);
                y.sheet.known.clear();
                c.note("sum young " + y.name + " " + y.sheet.traits + " from " + y.sheet.from);
            }
            Pet alpha = manager().get((String) c.get("Alpha"));
            c.note("Alpha flawsRead " + alpha.sheet.flawsRead + ", read " + alpha.isRead());
            // nothing read on the hold pets, several rows each for the reveal
            for (String n : new String[]{"Gene", "Gene2", "Unread1", "Run1", "Run2"}) {
                Pet p = manager().get((String) c.get(n));
                p.sheet.known.clear();
                p.sheet.flawsRead = false;
                c.note(n + " " + p.sheet.traits);
            }
            PetsView view = new PetsView();
            // log relics and read states as they change, to tie each charge to a scan; and the panel reveal steps with real time
            Global.getSector().addTransientScript(new com.fs.starfarer.api.EveryFrameScript() {
                String last = "";
                String lastReveal = "";
                long begun = 0L;

                public boolean isDone() {
                    return false;
                }

                public boolean runWhilePaused() {
                    return true;
                }

                public void advance(float amount) {
                    StringBuilder s = new StringBuilder("relics " + shop.getRelics());
                    for (String n : new String[]{"Alpha", "Gene", "Gene2", "Unread1", "Run1", "Run2"}) {
                        Pet p = manager().get((String) c.get(n));
                        if (p != null) s.append(", ").append(n).append(p.isRead() ? " read" : " unread");
                    }
                    if (!s.toString().equals(last)) log.info("LIVE_CHECK note: " + s);
                    last = s.toString();
                    List<Pet> pets = new ArrayList<>();
                    for (String n : new String[]{"Alpha", "Gene", "Gene2", "Unread1", "Run1", "Run2"}) pets.add(manager().get((String) c.get(n)));
                    String now = indevo.industries.petshop.plugins.RevealProbe.state(view, pets);
                    if (!now.equals(lastReveal)) {
                        long t = System.nanoTime() / 1000000L;
                        if (lastReveal.equals("idle") || lastReveal.isEmpty()) begun = t;
                        log.info("LIVE_CHECK note: reveal " + now + " at " + (t - begun) + " ms");
                        lastReveal = now;
                    }
                }
            });
            // SWITCHES=short: 5 relics, under the cost; else enough for every scan in the hold
            relics(a, sw.contains("short") ? 5f : 60f, 0f);
            // SWITCHES=nomotion: IndEvo_petMotion off
            if (sw.contains("nomotion")) Colony.setting(indevo.config.Setting.PET_MOTION, false);
            c.keepDialog(true);
            c.expect(PetsDialogPlugin.show(shop, view), "the Pets panel of the player center opens");
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
