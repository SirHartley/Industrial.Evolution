package iecheck;

import assortment_of_things.frontiers.FrontiersUtils;
import assortment_of_things.frontiers.SettlementData;
import assortment_of_things.frontiers.SettlementManager;
import assortment_of_things.frontiers.data.SettlementFacilitySlot;
import assortment_of_things.frontiers.intel.SettlementIntel;
import assortment_of_things.frontiers.interactions.SettlementInteraction;
import assortment_of_things.frontiers.submarkets.SettlementStoragePlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.ratsettlement.RatSettlements;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// RAT settlement facilities (PR #213): founds a settlement from code the way RAT's CreateSettlementInteraction does, builds IE facilities, holds in the settlement dialog
// SWITCHES: default builds Annex, Gantry, Quarters, Courier, Habitat; phaseb builds (Requisition, Restoration, Fabricator, Exhibition, Consulate); irradiated; owned (planet owned by a faction)
public final class RatSettleChecks {
    private static final Logger log = Global.getLogger(RatSettleChecks.class);
    static final String[] A = {"IndEvo_rat_annex", "IndEvo_rat_gantry", "IndEvo_rat_quarters", "IndEvo_rat_courier", "IndEvo_rat_habitat"};
    static final String[] B = {"IndEvo_rat_requisition", "IndEvo_rat_restoration", "IndEvo_rat_fabricator", "IndEvo_rat_exhibition", "IndEvo_rat_consulate"};

    private RatSettleChecks() {
    }

    static String sw() {
        return System.getProperty("ie.switches", "");
    }

    static SettlementData data() {
        return FrontiersUtils.INSTANCE.getFrontiersData().getActiveSettlement();
    }

    // RAT's own steps from CreateSettlementInteraction, without the site picker and cost
    static SettlementData found(PlanetAPI planet) {
        FrontiersUtils.INSTANCE.setFrontiersActive();
        SectorEntityToken entity = planet.getStarSystem().addCustomEntity("rat_frontiers_settlement_" + Misc.genUID(), planet.getName() + " Settlement", "rat_frontiers_settlement", Factions.PLAYER);
        entity.setCircularOrbitPointingDown(planet, 1f, 1f, 360f);
        entity.getMemoryWithoutUpdate().set("$abandonedStation", true);
        MarketAPI market = Global.getFactory().createMarket("rat_station_commander_market", entity.getName(), 3);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.setPrimaryEntity(entity);
        market.setFactionId(Factions.PLAYER);
        market.addIndustry(Industries.SPACEPORT);
        market.addSubmarket("rat_settlement_storage");
        ((SettlementStoragePlugin) market.getSubmarket("rat_settlement_storage").getPlugin()).setPlayerPaidToUnlock(true);
        market.setPlanetConditionMarketOnly(false);
        entity.setMarket(market);
        entity.getMemoryWithoutUpdate().unset("$tradeMode");
        market.getStability().modifyFlat("rat_base", 10f);
        market.getAccessibilityMod().modifyFlat("rat_base", 0.40f);

        SettlementData data = new SettlementData(planet, entity);
        FrontiersUtils.INSTANCE.getFrontiersData().setActiveSettlement(data);
        for (int i = 0; i < 6; i++) {
            SettlementFacilitySlot slot = new SettlementFacilitySlot(data);
            if (i == 0) {
                slot.installNewFacility("landing_pad");
                if (slot.isBuilding()) slot.finishConstruction();
            }
            data.getFacilitySlots().add(slot);
        }
        SettlementManager manager = new SettlementManager(data);
        Global.getSector().addScript(manager);
        Global.getSector().getListenerManager().addListener(manager);
        data.setMananger(manager);
        SettlementIntel intel = new SettlementIntel(data);
        data.setIntel(intel);
        Global.getSector().getIntelManager().addIntel(intel, true);
        return data;
    }

    static boolean build(SettlementData data, String id) {
        for (SettlementFacilitySlot slot : data.getFacilitySlots()) {
            if (!slot.getFacilityID().isEmpty()) continue;
            slot.installNewFacility(id);
            if (slot.isBuilding()) slot.finishConstruction();
            return slot.isFunctional();
        }
        return false;
    }

    static List<String> built(SettlementData data) {
        List<String> list = new ArrayList<>();
        for (SettlementFacilitySlot slot : data.getFacilitySlots()) if (slot.isFunctional()) list.add(slot.getFacilityID());
        return list;
    }

    static void open(Ctx c) {
        SettlementData data = data();
        SettlementInteraction plugin = new SettlementInteraction(data);
        plugin.setPreviousPlugin(new RuleBasedInteractionDialogPluginImpl());
        c.keepDialog(true);
        c.expect(Global.getSector().getCampaignUI().showInteractionDialog(plugin, data.getSettlementEntity()), "the settlement dialog opens");
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            PlanetAPI planet = Colony.planets(3).get(2);
            if (sw().contains("irradiated")) planet.getMarket().addCondition("irradiated");
            if (sw().contains("owned")) planet.getMarket().setFactionId(Factions.HEGEMONY);
            c.note("settlement planet " + planet.getName() + " in " + planet.getStarSystem().getName() + ", conditions " + planet.getMarket().getConditions().size() + ", owner " + planet.getMarket().getFactionId());
            SettlementData data = found(planet);
            c.expect(RatSettlements.isEnabled() && RatSettlements.getSettlementMarket() != null, "RAT is on and the settlement market is found");
            String[] ids = sw().contains("phaseb") ? B : A;
            for (String id : ids) c.expect(build(data, id), "built " + id);
            c.note("functional " + built(data));
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(2000000f);
            Colony.jump(planet.getContainingLocation(), planet.getLocation().x + 300f, planet.getLocation().y);
            c.reload();
        });
        steps.add(c -> {
            c.expect(data() != null && built(data()).size() == 6, "after a save and load the settlement keeps its six facilities (" + (data() == null ? "none" : built(data())) + ")");
            open(c);
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
