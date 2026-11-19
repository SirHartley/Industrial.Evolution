package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI.CoreUITradeMode;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.listener.PetStatusManager.PetDeathCause;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// pet 5.3: pet reports go into the open dialog's text, campaign messages only with no dialog; popups hold them until they close
public final class PetsReportChecks {
    private static final Logger log = Global.getLogger(PetsReportChecks.class);

    private PetsReportChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static CampaignFleetAPI fleet() {
        return Global.getSector().getPlayerFleet();
    }

    private static FleetMemberAPI ship(String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName(name);
        fleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static Pet pet(Ctx c, String type, String name, FleetMemberAPI on) {
        Pet pet = new Pet(type, name);
        manager().register(pet);
        pet.place(on, null);
        pet.age = 400f;
        c.put(name, pet.id);
        return pet;
    }

    private static Pet get(Ctx c, String name) {
        return manager().get((String) c.get(name));
    }

    private static void transfer(MarketAPI market, String submarketId, FleetMemberAPI member) {
        SubmarketAPI submarket = market.getSubmarket(submarketId);
        PlayerMarketTransaction tx = new PlayerMarketTransaction(market, submarket, CoreUITradeMode.OPEN);
        tx.getShipsSold().add(new PlayerMarketTransaction.ShipSaleInfo(member, 1000f));
        fleet().getFleetData().removeFleetMember(member);
        submarket.getCargo().initMothballedShips(Factions.PLAYER);
        submarket.getCargo().getMothballedShips().addFleetMember(member);
        Global.getSector().reportPlayerMarketTransaction(tx);
    }

    private static FleetMemberAPI byShip(String name) {
        for (FleetMemberAPI m : fleet().getFleetData().getMembersListCopy()) if (name.equals(m.getShipName())) return m;
        return null;
    }

    private static String dialogText() {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        return dialog == null ? "(no dialog)" : Ui.text(dialog.getTextPanel());
    }

    private static void openMarket(Ctx c, String key) {
        c.keepDialog(true);
        c.expect(Global.getSector().getCampaignUI().showInteractionDialog(c.market(key).getPrimaryEntity()), "the market dialog of " + c.market(key).getName() + " opens");
        c.frames(60);
    }

    private static void closeDialog(Ctx c) {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (dialog != null) dialog.dismiss();
        c.keepDialog(false);
        c.frames(30);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            if (!a.hasIndustry(Ids.PET_STORE)) a.addIndustry(Ids.PET_STORE);
            ((PetShop) a.getIndustry(Ids.PET_STORE)).finishBuildingOrUpgrading();
            fleet().getCargo().addCommodity("food", 500f);

            // only the test ships stay in the fleet
            CampaignFleetAPI parked = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet check parking", true);
            FleetMemberAPI flag = ship("ISS Flag");
            fleet().getFleetData().setFlagship(flag);
            for (FleetMemberAPI m : fleet().getFleetData().getMembersListCopy()) {
                if (m == flag) continue;
                fleet().getFleetData().removeFleetMember(m);
                parked.getFleetData().addFleetMember(m);
            }
            pet(c, "doge", "Rex", ship("ISS Rex"));
            pet(c, "bugdog", "Mover", ship("ISS Mover"));
            ship("ISS Spare");
            fleet().getFleetData().setSyncNeeded();
            fleet().getFleetData().syncIfNeeded();
            c.put("jangala", "jangala");
            openMarket(c, "jangala");
        });
        // 1: a sale moves the pet, the hand over line is a paragraph of the market dialog
        steps.add(c -> {
            MarketAPI b = c.market("jangala");
            String before = dialogText();
            transfer(b, Submarkets.SUBMARKET_OPEN, byShip("ISS Mover"));
            Pet mover = get(c, "Mover");
            String added = dialogText().substring(Math.min(before.length(), dialogText().length()));
            c.note("after the sale: Mover " + (mover.assignedFleetMember == null ? "nowhere" : "on " + mover.assignedFleetMember.getShipName()) + ", dialog adds: " + added.replace('\n', '|'));
            c.expect(added.contains("Mover the Bugdog moved from the ISS Mover to the"), "the hand over line is in the market dialog text");
            c.shot("handover");
        });
        // 1: storing the last pet ship with no free ship left: the forgotten pet warning in three paragraphs
        steps.add(c -> {
            for (FleetMemberAPI m : fleet().getFleetData().getMembersListCopy()) {
                boolean taken = false;
                for (Pet p : manager().getPets()) taken |= !p.isDead() && p.assignedFleetMember == m;
                if (!taken) pet(c, "spacerat", "Filler" + m.getShipName().replace("ISS ", ""), m);
            }
            String before = dialogText();
            transfer(c.market("jangala"), Submarkets.SUBMARKET_STORAGE, byShip("ISS Rex"));
            Pet rex = get(c, "Rex");
            String added = dialogText().substring(Math.min(before.length(), dialogText().length()));
            c.note("after storing: Rex " + (rex.assignedFleetMember == null ? "nowhere" : "on " + rex.assignedFleetMember.getShipName()) + ", dialog adds: " + added.replace('\n', '|'));
            c.expect(added.contains("You forgot some pets on ships you stored at the") && added.contains("They will starve to death unless fed.") && added.contains("Store or sell them at a"),
                    "the forgotten pet warning is three paragraphs in the market dialog");
            c.days(0.3f);
        });
        steps.add(c -> {
            c.shot("forgotten");
            c.put("marketText", dialogText());
            closeDialog(c);
        });
        // no campaign message for what went into the dialog (shot after-market)
        steps.add(c -> c.days(0.3f));
        steps.add(c -> c.shot("after-market"));
        steps.add(c -> {
            // 3 in flight: the panel from the ability, a death while it is open posts as a campaign message after it closes
            c.keepDialog(true);
            c.expect(PetsDialogPlugin.show(null, new PetsView()), "the Pets panel opens in flight");
            c.frames(60);
        });
        steps.add(c -> {
            Pet filler = null;
            for (Pet p : manager().getPets()) if (!p.isDead() && p.name.startsWith("Filler")) filler = p;
            c.put("gone", filler.name);
            manager().reportPetDied(filler, PetDeathCause.EUTHANIZED);
            c.note("put to sleep while the panel is open: " + filler.name);
            c.shot("flight-panel-open");
        });
        steps.add(c -> c.key("Escape"));
        steps.add(c -> {
            c.keepDialog(false);
            c.expect(Global.getSector().getCampaignUI().getCurrentInteractionDialog() == null, "Escape closed the panel");
            c.days(0.3f);
        });
        steps.add(c -> c.shot("flight-death"));
        // 5: time based reports in flight stay campaign messages: a month passes (feeding, the food report), then a life ending warning
        steps.add(c -> c.until(() -> Global.getSector().getClock().getDay() == 1 && Global.getSector().getClock().getElapsedDaysSince((Long) c.data().getOrDefault("iecheck_reportStart", 0L)) > 31f, 70f, "a month ended in flight"));
        steps.add(c -> c.days(0.3f));
        steps.add(c -> c.shot("flight-food"));
        steps.add(c -> {
            Pet old = pet(c, "gravcat", "Oldie", ship("ISS Oldie"));
            old.age = old.getLifetime() - 62f;
            c.days(3f);
        });
        steps.add(c -> {
            c.expect(get(c, "Oldie").lifeWarnings == 1, "the life ending warning came in flight");
            c.days(0.3f);
        });
        steps.add(c -> c.shot("flight-warning"));
        // the first meals came after the month end, so a second month end call shows the food report
        steps.add(c -> {
            manager().reportEconomyMonthEnd();
            c.days(0.3f);
        });
        steps.add(c -> c.shot("flight-food2"));
        steps.add(c -> {
            // hold: Thrinakia's dialog, 3 syringes in the cargo, for the syringe and the Pets panel from the colony
            fleet().getCargo().addSpecial(new SpecialItemData(ItemIds.LONGEVITY_SYRINGE, null), 3);
            // SWITCHES=flighthold: hold in flight instead, for the syringe from the cargo screen
            if (System.getProperty("ie.switches", "").contains("flighthold")) c.frames(30);
            else openMarket(c, "a");
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
