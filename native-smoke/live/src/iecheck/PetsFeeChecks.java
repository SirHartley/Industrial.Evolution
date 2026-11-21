package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.utils.helper.BillingHelper;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// pet storage fee: a share of the species value with a floor, more on markets the player does not own, the gamma core cut
public final class PetsFeeChecks {
    private static final Logger log = Global.getLogger(PetsFeeChecks.class);

    private PetsFeeChecks() {
    }

    private static PetShop shop(MarketAPI market) {
        if (!market.hasIndustry(Ids.PET_STORE)) market.addIndustry(Ids.PET_STORE);
        PetShop shop = (PetShop) market.getIndustry(Ids.PET_STORE);
        shop.finishBuildingOrUpgrading();
        return shop;
    }

    private static Pet stored(PetShop shop, String species, String name) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetMemberAPI ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        ship.setShipName("ISS " + name + " Home");
        fleet.getFleetData().addFleetMember(ship);
        Pet pet = new Pet(species, name);
        PetStatusManager.getInstance().register(pet);
        pet.place(ship, null);
        pet.place(null, shop);
        return pet;
    }

    private static String tooltip(PetShop shop) {
        TooltipMakerAPI t = Ui.tooltip();
        shop.createTooltip(com.fs.starfarer.api.campaign.econ.Industry.IndustryTooltipMode.NORMAL, t, false);
        return Ui.text(t);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            shop(c.market("a"));
            shop(c.market("b"));
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a"), b = c.market("b");
            PetShop own = shop(a), npc = shop(b);
            b.setPlayerOwned(false);
            b.reapplyIndustries();

            Pet cheap = stored(own, "hampter", "Pip");
            Pet dear = stored(own, "voidsquid", "Inky");
            Pet cheap2 = stored(npc, "hampter", "Pop");
            Pet dear2 = stored(npc, "voidsquid", "Squish");
            a.reapplyIndustries();
            b.reapplyIndustries();

            c.note("values: hampter " + cheap.getData().value + ", voidsquid " + dear.getData().value);
            c.near(own.getStorageCost(cheap), 100f, 0.01f, "player colony, hampter (value 200): fee hits the 100 floor");
            c.near(own.getStorageCost(dear), 750f, 0.01f, "player colony, void squid (value 7500): fee is 10% of the value");
            c.near(npc.getStorageCost(cheap2), 150f, 0.01f, "npc market, hampter: floor x1.5");
            c.near(npc.getStorageCost(dear2), 1125f, 0.01f, "npc market, void squid: 10% x1.5");

            float mult = own.getUpkeep().getMult();
            c.note("player colony upkeep mult " + mult);
            c.near(own.getMonthlyStorageCost(dear), 750f * mult, 0.5f, "player colony monthly fee is the fee times the upkeep mult");
            c.near(npc.getMonthlyStorageCost(dear2), 1125f, 0.01f, "npc market monthly fee has no upkeep mult");

            String ownText = tooltip(own), npcText = tooltip(npc);
            String ownTotal = Misc.getDGSCredits((100f + 750f) * mult), npcTotal = Misc.getDGSCredits(150f + 1125f);
            c.expect(ownText.contains(ownTotal), "player colony Animal Center tooltip total " + ownTotal);
            c.expect(npcText.contains(npcTotal), "npc Animal Center tooltip total " + npcTotal);

            MonthlyReport report = SharedData.getData().getCurrentReport();
            MonthlyReport.FDNode node = report.getNode(report.getNode(report.getNode(BillingHelper.SERVICES_NODE), b.getId()), npc.getId() + "_storage");
            float before = node.upkeep;
            npc.reportEconomyTick((int) Global.getSettings().getFloat("economyIterPerMonth") - 1);
            c.near(node.upkeep - before, 1275f, 0.5f, "npc market monthly report bills 1275 (150 + 1125)");
            node.upkeep = before;

            float ownUpkeepBefore = own.getUpkeep().getModifiedValue();
            own.setAICoreId(Commodities.GAMMA_CORE);
            a.reapplyIndustries();
            c.near(own.getStorageCost(dear), 750f * 0.3f, 0.01f, "gamma core: void squid fee x0.3");
            c.near(own.getStorageCost(cheap), 100f * 0.3f, 0.01f, "gamma core: the floor is cut too (100 x0.3)");
            npc.setAICoreId(Commodities.GAMMA_CORE);
            b.reapplyIndustries();
            c.near(npc.getStorageCost(dear2), 1125f * 0.3f, 0.01f, "gamma core on the npc market: 1125 x0.3");
            c.note("player colony upkeep " + ownUpkeepBefore + " -> " + own.getUpkeep().getModifiedValue() + " with gamma");
            own.setAICoreId(null);
            npc.setAICoreId(null);
            a.reapplyIndustries();
            b.reapplyIndustries();

            boolean atNpc = System.getProperty("ie.switches", "").contains("npc");
            MarketAPI dock = atNpc ? b : a;
            Colony.jump(dock.getContainingLocation(), dock.getPrimaryEntity().getLocation().x + 100f, dock.getPrimaryEntity().getLocation().y);
            c.frames(60);
        });
        steps.add(c -> {
            boolean atNpc = System.getProperty("ie.switches", "").contains("npc");
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog((atNpc ? c.market("b") : c.market("a")).getPrimaryEntity());
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
