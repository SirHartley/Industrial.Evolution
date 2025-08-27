package indevo.utils.update;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.engineeringhub.industry.EngineeringHub;
import indevo.industries.academy.industry.Academy;
import indevo.industries.derelicts.industry.HullDeconstructor;
import indevo.industries.derelicts.industry.HullForge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ModUpdater {

    private static void log(String Text) {
        Global.getLogger(ModUpdater.class).info(Text);
    }

    public static void run() {
        log("Industrial.Evolution performing setup for new version");

        log("Removing Scripts");
        removeScripts();
        log("Removing Listeners");
        removeListeners();

        log("Replacing Submarkets");
        replaceSubMarkets();
        log("Replacing Industries");
        replaceIndustries();
        log("Replacing Conditions");
        replaceConditions();

        log("Running Industry Setup");
        NewGameIndustryPlacer.run();

        log("Cleanup done");
    }

    private static void replaceIndustries() {
        //replace all built industries

        Iterator<MarketAPI> marketAPIIterator = Global.getSector().getEconomy().getMarketsCopy().iterator();
        ArrayList<Industry> industryList = new ArrayList<>();

        while (marketAPIIterator.hasNext()) {
            MarketAPI market = marketAPIIterator.next();
            log("Getting Industries on " + market.getName());
            Iterator<Industry> industryIterator = market.getIndustries().iterator();

            while (industryIterator.hasNext()) {
                Industry ind = industryIterator.next();

                if (ind.getId().contains("IndEvo")) {
                    industryList.add(ind);
                }
            }
        }

        for (Industry ind : industryList) {
            String aiCoreId = null;
            SpecialItemData specItem = null;
            List<PersonAPI> os = new ArrayList<>(); //academy stuff
            List<PersonAPI> as = new ArrayList<>(); //academy stuff
            ShipVariantAPI variant = null; //Deconst/Enghub
            ShipHullSpecAPI currentShip = null; //hullForge
            String id = ind.getId();
            MarketAPI market = ind.getMarket();

            log(market.getName() + ": replacing " + ind.getId());
            //remove stuff
            switch (id) {
                case Ids.ACADEMY:
                    ((Academy) ind).abortOfficerTraining(true);
                    ((Academy) ind).abortAdminTraining(true);
                    os = ((Academy) ind).getOfficerStorage();
                    as = ((Academy) ind).getAdminStorage();
                    break;
                case Ids.ENGHUB:
                    variant = ((EngineeringHub) ind).getCurrentDeconShipVar();
                    break;
                case Ids.DECONSTRUCTOR:
                    variant = ((HullDeconstructor) ind).getCurrentDeconShipVar();
                    break;
                case Ids.HULLFORGE:
                    currentShip = ((HullForge) ind).getCurrentShip();
                    break;
            }

            if (ind.getAICoreId() != null) {
                aiCoreId = ind.getAICoreId();
                ind.setAICoreId(null);
            }

            if (ind.getSpecialItem() != null) {
                specItem = ind.getSpecialItem();
                ind.setSpecialItem(null);
            }

            market.removeIndustry(id, MarketAPI.MarketInteractionMode.LOCAL, false);

            //set stuff
            market.addIndustry(id);

            if (aiCoreId != null) {
                market.getIndustry(id).setAICoreId(aiCoreId);
            }
            if (specItem != null && specItem.getId() != null) {
                market.getIndustry(id).setSpecialItem(specItem);
            }

            switch (id) {
                case Ids.ACADEMY:
                    for (PersonAPI o : os) {
                        ((Academy) ind).storeOfficer(o);
                    }
                    for (PersonAPI a : as) {
                        ((Academy) ind).storeAdmin(a);
                    }
                    break;
                case Ids.ENGHUB:
                    ((EngineeringHub) ind).setCurrentDeconShipVar(variant);
                    break;
                case Ids.DECONSTRUCTOR:
                    ((HullDeconstructor) ind).setCurrentDeconShipVar(variant);
                    break;
                case Ids.HULLFORGE:
                    ((HullForge) ind).setCurrentShip(currentShip);
                    break;
            }
        }
    }

    private static void replaceSubMarkets() {
        Iterator<MarketAPI> marketAPIIterator = Global.getSector().getEconomy().getMarketsCopy().iterator();
        ArrayList<SubmarketAPI> submarketAPIArrayList = new ArrayList<>();

        while (marketAPIIterator.hasNext()) {
            MarketAPI market = marketAPIIterator.next();
            log("Getting submarkets on " + market.getName());
            Iterator<SubmarketAPI> submarketAPIIterator = market.getSubmarketsCopy().iterator();

            while (submarketAPIIterator.hasNext()) {
                SubmarketAPI sm = submarketAPIIterator.next();

                if (sm.getSpecId().contains("IndEvo")) {
                    submarketAPIArrayList.add(sm);
                }
            }
        }

        for (SubmarketAPI sub : submarketAPIArrayList) {
            log(sub.getMarket().getName() + ": replacing " + sub.getSpecId());
            MarketAPI m = sub.getMarket();

            m.addSubmarket(Ids.TEMPSTORE);
            SubmarketAPI tempStore = m.getSubmarket(Ids.TEMPSTORE);

            tempStore.getCargo().addAll(sub.getCargo());
            for (FleetMemberAPI ship : sub.getCargo().getMothballedShips().getMembersListCopy()) {
                tempStore.getCargo().getMothballedShips().addFleetMember(ship);
            }

            m.removeSubmarket(sub.getSpecId());
            m.addSubmarket(sub.getSpecId());
            m.getSubmarket(sub.getSpecId()).getCargo().addAll(tempStore.getCargo());
            for (FleetMemberAPI ship : tempStore.getCargo().getMothballedShips().getMembersListCopy()) {
                m.getSubmarket(sub.getSpecId()).getCargo().getMothballedShips().addFleetMember(ship);
            }
            m.removeSubmarket(tempStore.getSpecId());
        }
    }

    private static void removeScripts() {
        //remove (!) all currently running scripts
        Iterator<EveryFrameScript> everyFrameScriptIterator = Global.getSector().getScripts().iterator();
        ArrayList<EveryFrameScript> scriptArrayList = new ArrayList<>();

        while (everyFrameScriptIterator.hasNext()) {
            EveryFrameScript script = everyFrameScriptIterator.next();

            if (script.getClass().getName().contains("IndEvo")) {
                scriptArrayList.add(script);
            }
        }

        for (EveryFrameScript script : scriptArrayList) {
            log("Removing: " + script.getClass().getName());
            Global.getSector().removeScript(script);
        }
    }

    private static void removeListeners() {
        //remove (!) all currently running listeners
        Iterator<EconomyTickListener> economyTickListenerIterator = Global.getSector().getListenerManager().getListeners(EconomyTickListener.class).iterator();
        ArrayList<EconomyTickListener> economyTickListenerArrayList = new ArrayList<>();

        while (economyTickListenerIterator.hasNext()) {
            EconomyTickListener listener = economyTickListenerIterator.next();

            if (listener.getClass().getName().contains("IndEvo")) {
                economyTickListenerArrayList.add(listener);
            }
        }

        Iterator<CampaignEventListener> campaignEventListenerIterator = Global.getSector().getAllListeners().iterator();
        ArrayList<CampaignEventListener> campaignEventListenerArrayList = new ArrayList<>();

        while (campaignEventListenerIterator.hasNext()) {
            CampaignEventListener listener = campaignEventListenerIterator.next();

            if (listener.getClass().getName().contains("IndEvo")) {
                campaignEventListenerArrayList.add(listener);
            }
        }

        for (CampaignEventListener listener : campaignEventListenerArrayList) {
            log("Removing: " + listener.getClass().getName());
            Global.getSector().getListenerManager().removeListener(listener);
        }

        for (EconomyTickListener listener : economyTickListenerArrayList) {
            log("Removing: " + listener.getClass().getName());
            Global.getSector().getListenerManager().removeListener(listener);
        }
    }

    private static void replaceConditions() {
        //Replace all conditions if Edicts
        Iterator<MarketAPI> marketAPIIterator = Misc.getFactionMarkets(Global.getSector().getPlayerFaction()).iterator();
        ArrayList<MarketConditionAPI> conditionAPIArrayList = new ArrayList<>();

        while (marketAPIIterator.hasNext()) {
            MarketAPI market = marketAPIIterator.next();
            log("Replacing Conditions on " + market.getName());
            Iterator<MarketConditionAPI> conditionAPIIterator = market.getConditions().iterator();

            while (conditionAPIIterator.hasNext()) {
                MarketConditionAPI c = conditionAPIIterator.next();

                if (c.getId().contains("IndEvo")) {
                    conditionAPIArrayList.add(c);
                }
            }

            for (MarketConditionAPI c : conditionAPIArrayList) {
                log("Replacing: " + c.getName());
                market.removeCondition(c.getId());
                market.addCondition(c.getId());
            }
        }
    }
}
