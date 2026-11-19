package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI.CoreUITradeMode;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.item.PetItemPlugin;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 3.8: looks passed to the young, a young in a Cryochamber (no ageing, sale rehomes, buy-back revives, storage keeps), adoption offers at an npc center
public final class PetsAdoptChecks {
    private static final Logger log = Global.getLogger(PetsAdoptChecks.class);

    private PetsAdoptChecks() {
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

    private static Pet pet(Ctx c, String name, Object... idLevel) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName("ISS " + name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        Pet pet = new Pet("doge", name);
        manager().register(pet);
        pet.place(member, null);
        pet.age = 400f;
        for (String id : new ArrayList<>(pet.sheet.traits.keySet())) pet.setTrait(id, 0);
        for (int i = 0; i < idLevel.length; i += 2) cmd("trait " + pet.id + " " + idLevel[i] + " " + idLevel[i + 1]);
        c.put(name, pet.id);
        return pet;
    }

    private static int chambers(CargoAPI cargo, Pet pet) {
        int n = 0;
        for (CargoStackAPI stack : cargo.getStacksCopy()) if (PetItemPlugin.chamberPet(stack) == pet) n += (int) stack.getSize();
        return n;
    }

    // what the trade screen reports after a sale or a purchase on a submarket
    private static void trade(MarketAPI market, String submarket, Pet pet, boolean sold) {
        PlayerMarketTransaction tx = new PlayerMarketTransaction(market, market.getSubmarket(submarket), CoreUITradeMode.OPEN);
        CargoAPI cargo = Global.getFactory().createCargo(true);
        cargo.addSpecial(new com.fs.starfarer.api.campaign.SpecialItemData(indevo.ids.ItemIds.PET_CHAMBER, PetItemPlugin.chamberData(pet)), 1f);
        if (sold) tx.setSold(cargo);
        else {
            tx.setSold(Global.getFactory().createCargo(true));
            tx.setBought(cargo);
        }
        manager().reportPlayerMarketTransaction(tx);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            PetShop own = shop(c.market("a"));
            PetShop npc = shop(Global.getSector().getEconomy().getMarket("jangala"));
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(100000f);
            Global.getSector().getPlayerFleet().getCargo().addCommodity("food", 500f);
            Global.getSector().getPlayerFleet().getCargo().addCommodity(indevo.ids.ItemIds.RARE_PARTS, 30f);

            // looks: the console sets one, Wild type clears it
            Pet mom = pet(c, "Mom", "capacitor", 2);
            Pet dad = pet(c, "Dad", "plating", 1);
            cmd("look " + mom.id + " doge_1");
            cmd("look " + dad.id + " doge_1");
            c.expect("doge_1".equals(mom.sheet.look) && "doge_1".equals(dad.sheet.look), "indevo_pet look sets Piebald on Mom and Dad (" + mom.getLookName() + ")");
            Pet wild = pet(c, "Wildie");
            cmd("look " + wild.id + " doge_3");
            cmd("look " + wild.id + " wild");
            c.expect(wild.sheet.look == null, "indevo_pet look wild sets Wild type (" + wild.getLookName() + ")");
            Pet other = pet(c, "Spot");
            cmd("look " + other.id + " doge_4");
            // one look parent: the young take its look half the time; a new look comes by mutation a third of the time
            {
                java.util.Random rnd = new java.util.Random(7);
                java.util.Map<String, indevo.industries.petshop.memory.PetDataRepo.Trait> traits = indevo.industries.petshop.memory.PetDataRepo.getTraits();
                int chocolate = 0, wildType = 0, odd = 0;
                for (int i = 0; i < 2000; i++) {
                    indevo.industries.petshop.memory.PetSheet y = indevo.industries.petshop.PetGenetics.rollYoung(other.getData(), other.sheet, wild.sheet, 40f, 0, 0f, 0f, traits, rnd);
                    if ("doge_4".equals(y.look)) chocolate++;
                    else if (y.look == null) wildType++;
                    else odd++;
                }
                c.note("Chocolate x Wild type: Chocolate " + chocolate + ", Wild type " + wildType + ", other " + odd + " of 2000");
                c.expect(odd == 0 && chocolate > 800 && wildType > 800, "a young of one Chocolate parent is Chocolate or Wild type, about half each");
                int newLook = 0, fromMutation = 0, bad = 0;
                for (int i = 0; i < 3000; i++) {
                    indevo.industries.petshop.memory.PetSheet y = indevo.industries.petshop.PetGenetics.rollYoung(wild.getData(), wild.sheet, wild.sheet, 40f, 0, 1f, 0f, traits, rnd);
                    if (y.look != null) {
                        newLook++;
                        if (wild.getData().getLook(y.look) == null) bad++;
                        if (y.from != null && indevo.industries.petshop.memory.PetSheet.FROM_MUTATION.equals(y.from.get(indevo.industries.petshop.memory.PetSheet.LOOK))) fromMutation++;
                    }
                }
                c.note("Wild x Wild at mutation rate 1: new look " + newLook + " of 3000, from mutation " + fromMutation + ", unknown ids " + bad);
                c.expect(newLook > 700 && newLook < 1300 && bad == 0 && fromMutation == newLook, "a new look comes by mutation about a third of the time, a doge look marked Mutation");
            }

            // two broods of Piebald parents; a second pair for the ready brood the hold shows
            own.startBrood(mom, dad, 0);
            Pet a2 = pet(c, "Patch", "runner", 1);
            Pet b2 = pet(c, "Domino", "ward", 1);
            cmd("look " + a2.id + " doge_2");
            cmd("look " + b2.id + " doge_2");
            own.startBrood(a2, b2, 1);

            // adoption: the npc center offers, the player center does not
            c.expect(npc.hasAdoption() && !own.hasAdoption(), "Adoption only at a center the player does not own");
            MarketAPI jangala = npc.getMarket();
            int want = Math.max(3, Math.min(6, jangala.getSize()));
            List<Pet> offers = npc.getOffers();
            List<String> names = new ArrayList<>();
            for (Pet p : offers) names.add(p.name + " " + p.typeID + " " + Math.round(p.age) + "d " + Math.round(npc.getOfferPrice(p)));
            c.note("Jangala size " + jangala.getSize() + ", offers " + names);
            c.expect(offers.size() == want, "offers by market size (" + offers.size() + " of " + want + ")");
            c.put("offers1", names.toString());
            {
                java.util.Set<String> seen = new java.util.HashSet<>();
                java.util.Set<String> living = new java.util.HashSet<>();
                for (Pet p : manager().getPets()) if (!p.isDead()) living.add(p.name);
                boolean unique = true, clash = false;
                for (Pet p : offers) {
                    if (!seen.add(p.name)) unique = false;
                    if (living.contains(p.name)) clash = true;
                }
                c.expect(unique && !clash, "offer names are distinct and not the names of living pets");
            }
            c.days(61f);
        });
        steps.add(c -> {
            PetShop own = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            PetShop npc = (PetShop) Global.getSector().getEconomy().getMarket("jangala").getIndustry(Ids.PET_STORE);
            List<String> names = new ArrayList<>();
            for (Pet p : npc.getOffers()) names.add(p.name + " " + p.typeID + " " + Math.round(p.age) + "d " + Math.round(npc.getOfferPrice(p)));
            c.note("offers after two month ends " + names);
            c.expect(!names.toString().equals(c.get("offers1")), "the offers are new after a month end");

            PetBrood brood = own.getBrood(0);
            c.expect(brood != null && brood.isReady(), "the Piebald brood is ready");
            boolean looks = true;
            for (Pet y : brood.getYoung()) {
                c.note("young " + y.name + " look " + y.sheet.look);
                if (!"doge_1".equals(y.sheet.look)) looks = false;
            }
            c.expect(looks, "young of two Piebald parents are Piebald");
            {
                java.util.Set<String> seen = new java.util.HashSet<>();
                boolean unique = true;
                for (int b = 0; b < 2; b++) {
                    PetBrood br = own.getBrood(b);
                    if (br == null) continue;
                    seen.clear();
                    for (Pet y : br.getYoung()) if (!seen.add(y.name)) unique = false;
                    c.note("brood " + b + " young " + seen);
                }
                c.expect(unique, "young of one brood have distinct names");
                java.util.Set<String> all = new java.util.HashSet<>();
                boolean across = true;
                for (int b = 0; b < 2; b++) {
                    PetBrood br = own.getBrood(b);
                    if (br != null) for (Pet y : br.getYoung()) if (!all.add(y.name)) across = false;
                }
                c.expect(across, "young of two broods waiting at one center have distinct names " + all);
            }

            // one young into a Cryochamber, the rest into storage
            Pet young = brood.getYoung().get(0);
            c.put("young", young.id);
            List<Pet> rest = new ArrayList<>(brood.getYoung());
            own.placeYoung(brood, young, null, true);
            for (Pet y : rest) if (y != young) own.placeYoung(brood, y, null, false);
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            c.expect(young.inChamber && chambers(cargo, young) == 1, "the young sits in a Cryochamber in the fleet cargo");
            c.expect(young.assignedFleetMember == null && young.storage == null && !young.isWithPlayer(), "a young in a Cryochamber is listed nowhere");
            c.note("sale value " + manager().getSaleValue(young) + ", species value " + young.getData().value);
            c.put("age", young.age);
            c.days(20f);
        });
        steps.add(c -> {
            Pet young = manager().get((String) c.get("young"));
            float age = (Float) c.get("age");
            c.near(young.age, age, 0.01f, "a young in a Cryochamber does not age");
            MarketAPI jangala = Global.getSector().getEconomy().getMarket("jangala");
            trade(jangala, Submarkets.SUBMARKET_STORAGE, young, true);
            c.expect(!young.isDead() && young.inChamber, "a Cryochamber put in storage keeps its pet");
            trade(jangala, Submarkets.SUBMARKET_OPEN, young, true);
            c.expect(young.isDead(), "selling the Cryochamber rehomes the young (" + (young.deathData == null ? "no death data" : young.deathData.toString()) + ")");
            trade(jangala, Submarkets.SUBMARKET_OPEN, young, false);
            c.expect(!young.isDead() && young.inChamber, "buying the Cryochamber back revives the young in its chamber");
            c.reload();
        });
        steps.add(c -> {
            Pet young = manager().get((String) c.get("young"));
            c.expect(young != null && !young.isDead() && young.inChamber, "after a save and load the young is still in its Cryochamber");
            Pet mom = manager().get((String) c.get("Mom"));
            c.expect("doge_1".equals(mom.sheet.look), "after a save and load Mom is still Piebald");
            String sw = System.getProperty("ie.switches", "");
            c.keepDialog(true);
            // SWITCHES=poor: 100 credits, under every price
            if (sw.contains("poor")) Global.getSector().getPlayerFleet().getCargo().getCredits().set(100f);
            if (sw.contains("adopt")) {
                // SWITCHES=adopt: the Adoption tab at Jangala's center
                PetShop npc = (PetShop) Global.getSector().getEconomy().getMarket("jangala").getIndustry(Ids.PET_STORE);
                PetsView view = new PetsView();
                view.tab = PetsView.TAB_ADOPT;
                c.expect(PetsDialogPlugin.show(npc, view), "the Pets panel of Jangala opens on Adoption");
            } else {
                PetShop own = (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
                c.expect(own.getBrood(1) != null && own.getBrood(1).isReady(), "a second ready brood for the New young popup");
                c.expect(PetsDialogPlugin.show(own, new PetsView((String) c.get("Mom"))), "the Pets panel of the player center opens");
            }
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
