package iecheck;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.RuleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingContractMemory;
import indevo.industries.courierport.listeners.Shipment;
import indevo.industries.courierport.listeners.ShippingManager;
import indevo.industries.embassy.AmbassadorItemHelper;
import indevo.industries.embassy.industry.Embassy;
import indevo.industries.embassy.listeners.AmbassadorItemTrackerPlugin;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.industries.embassy.listeners.LostAmbassadorRemovalChecker;
import indevo.industries.embassy.rules.IndEvo_addAmbassadorToCargo;
import indevo.industries.embassy.rules.IndEvo_ambassadorRemoval;
import indevo.industries.embassy.rules.IndEvo_moveAmbassadorToClosestEmbassy;
import indevo.industries.embassy.scripts.AmbassadorMurderConsequences;
import indevo.industries.embassy.scripts.HAAmbassadorEventFactor;
import indevo.industries.warehouses.data.WarehouseSubmarketData;
import indevo.industries.warehouses.industry.Warehouses;
import indevo.utils.helper.ReflectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EmbassyChecks {
    private static final String HEG = Factions.HEGEMONY;
    private static final String DIK = Factions.DIKTAT;
    private static final String CHURCH = Factions.LUDDIC_CHURCH;
    private static final String PER = Factions.PERSEAN;
    private static final String TT = Factions.TRITACHYON;
    private static final String WAREHOUSE = "IndEvo_WarehouseStorage_1";

    private static RuleBasedInteractionDialogPluginImpl plugin;

    private EmbassyChecks() {
    }

    static MarketAPI home(String faction, int index) {
        List<MarketAPI> found = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (faction.equals(market.getFactionId()) && market.getSize() >= 5 && !market.isHidden()) found.add(market);
        }
        found.sort(Comparator.comparing(MarketAPI::getId));
        return found.get(index);
    }

    static Map<String, MemoryAPI> memory(MarketAPI market, PersonAPI person) {
        Map<String, MemoryAPI> map = new HashMap<>();
        map.put(MemKeys.LOCAL, person != null ? person.getMemory() : market.getPrimaryEntity().getMemory());
        map.put(MemKeys.MARKET, market.getMemory());
        map.put(MemKeys.GLOBAL, Global.getSector().getMemory());
        map.put(MemKeys.PLAYER, Global.getSector().getCharacterData().getMemory());
        map.put(MemKeys.FACTION, market.getFaction().getMemory());
        return map;
    }

    static String rule(MarketAPI market, PersonAPI person, String option) {
        Map<String, MemoryAPI> map = memory(market, person);
        map.get(MemKeys.LOCAL).set("$option", option, 0f);
        RuleAPI rule = Global.getSector().getRules().getBestMatching(null, "DialogOptionSelected", null, map);
        map.get(MemKeys.LOCAL).unset("$option");
        return rule == null ? "none" : rule.getId();
    }

    static String hireRule(MarketAPI market) {
        AmbassadorPersonManager.createAmbassador(market, true, null);
        return rule(market, AmbassadorPersonManager.getAmbassador(market), "IndEvo_ambassadorHire");
    }

    static List<String> menu(MarketAPI market, PersonAPI person) {
        List<String> ids = new ArrayList<>();
        for (RuleAPI rule : Global.getSector().getRules().getAllMatching(null, "IndEvo_AmbassadorMenu", null, memory(market, person))) {
            ids.add(rule.getId());
        }
        return ids;
    }

    static void open(Ctx c, MarketAPI market) {
        plugin = new RuleBasedInteractionDialogPluginImpl("IECheckOpen");
        c.keepDialog(true);
        c.expect(Global.getSector().getCampaignUI().showInteractionDialog(plugin, market.getPrimaryEntity()), "dialog opens at " + market.getName());
        c.frames(60);
    }

    static void talk(PersonAPI person) {
        Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget().setActivePerson(person);
        plugin.notifyActivePersonChanged();
    }

    static void close(Ctx c) {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        dialog.getInteractionTarget().setActivePerson(null);
        c.keepDialog(false);
        dialog.dismiss();
        c.frames(30);
    }

    static void detach(PersonAPI person, Behaviour.FleetBehaviour behaviour, String target) {
        List<FleetMemberAPI> members = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();
        LoadoutMemory.Loadout loadout = new LoadoutMemory.Loadout();
        loadout.addToMembersList(members.get(members.size() - 1));
        loadout.behaviour = behaviour;
        loadout.transportTargetMarket = target;
        loadout.targetCargo.addSupplies(50f);
        loadout.targetCargo.addFuel(50f);
        loadout.targetCargo.addCrew(100);
        loadout.targetCargo.addSpecial(item(person), 1);
        // TransportAssignmentAI reads the target from the stored loadout
        LoadoutMemory.setLoadout(1, loadout);
        FleetUtils.createAndSpawnFleet(loadout, 1);
    }

    static PersonAPI hire(Ctx c, String key, MarketAPI market) {
        AmbassadorPersonManager.createAmbassador(market, true, null);
        PersonAPI person = AmbassadorPersonManager.getAmbassador(market);
        new IndEvo_addAmbassadorToCargo().execute(null, null, null, memory(market, person));
        c.put(key, person);
        c.put(key + "_home", market.getId());
        return person;
    }

    static PersonAPI person(Ctx c, String key) {
        return (PersonAPI) c.get(key);
    }

    static SpecialItemData item(PersonAPI person) {
        return AmbassadorItemHelper.createItem(person);
    }

    static CargoAPI fleet() {
        return Global.getSector().getPlayerFleet().getCargo();
    }

    static CargoAPI storage(MarketAPI market) {
        return Misc.getStorageCargo(market);
    }

    static CargoAPI warehouse(MarketAPI market) {
        return market.getSubmarket(WAREHOUSE).getCargo();
    }

    static Embassy embassy(MarketAPI market) {
        return (Embassy) market.getIndustry(Ids.EMBASSY);
    }

    static float count(CargoAPI cargo, PersonAPI person) {
        return cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, item(person));
    }

    static void move(CargoAPI from, CargoAPI to, PersonAPI person) {
        from.removeItems(CargoAPI.CargoItemType.SPECIAL, item(person), 1);
        to.addSpecial(item(person), 1);
    }

    static void install(MarketAPI market, PersonAPI person, CargoAPI from) {
        from.removeItems(CargoAPI.CargoItemType.SPECIAL, item(person), 1);
        embassy(market).getInstallableItems().get(0).setCurrentlyInstalledItemData(item(person));
    }

    static AmbassadorItemTrackerPlugin tracker(String faction) {
        for (AmbassadorItemTrackerPlugin tracker : Global.getSector().getListenerManager().getListeners(AmbassadorItemTrackerPlugin.class)) {
            if (tracker.faction.getId().equals(faction)) return tracker;
        }
        return null;
    }

    static boolean lost(String faction) {
        for (LostAmbassadorRemovalChecker checker : Global.getSector().getListenerManager().getListeners(LostAmbassadorRemovalChecker.class)) {
            if (checker.faction.getId().equals(faction)) return true;
        }
        return false;
    }

    static int murders() {
        int count = 0;
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof AmbassadorMurderConsequences) count++;
        }
        return count;
    }

    static boolean known(PersonAPI person) {
        return Global.getSector().getImportantPeople().getPerson(person.getId()) != null;
    }

    static boolean inOffice(MarketAPI market, PersonAPI person) {
        PersonAPI current = AmbassadorPersonManager.getAmbassador(market);
        return current != null && current.getId().equals(person.getId());
    }

    static float rep(String faction) {
        return Global.getSector().getPlayerFaction().getRelationship(faction);
    }

    static void setRep(String faction, float value) {
        Global.getSector().getPlayerFaction().setRelationship(faction, value);
    }

    static Shipment shipment(PersonAPI person) {
        for (Shipment shipment : Global.getSector().getListenerManager().getListeners(Shipment.class)) {
            if (!shipment.done && count(shipment.cargo, person) > 0f) return shipment;
        }
        return null;
    }

    static void clearMurders() {
        Global.getSector().removeScriptsOfClass(AmbassadorMurderConsequences.class);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();

        steps.add(c -> {
            Colony.ships();
            fleet().addSupplies(200f);
            fleet().addFuel(200f);
            List<PlanetAPI> planets = Colony.planets(3);
            for (int i = 0; i < 3; i++) {
                MarketAPI market = Colony.make(planets.get(i), 6);
                market.addIndustry(Ids.EMBASSY);
                market.reapplyIndustries();
                c.put("m" + i, market.getId());
            }
            for (String faction : new String[]{HEG, DIK, CHURCH, PER, TT}) setRep(faction, 0.2f);
            c.note("colonies " + c.market("m0").getName() + ", " + c.market("m1").getName() + ", " + c.market("m2").getName());
            c.expect(Setting.EMBASSY.enabled(), "embassy setting on");
        });

        // 716, the rule row a player sees for each state of the first ambassador, hire and dismissal through the real dialog
        steps.add(c -> {
            MarketAPI first = home(HEG, 0);
            c.expect("IndEvo_ambassadorHire".equals(hireRule(first)), "hire offered with a free embassy");
            open(c, first);
        });
        steps.add(c -> {
            MarketAPI first = home(HEG, 0);
            PersonAPI person = AmbassadorPersonManager.getAmbassador(first);
            c.put("heg", person);
            talk(person);
            plugin.optionSelected(null, "IndEvo_ambassadorHire");
            plugin.optionSelected(null, "IndEvo_ambassadorConfirm");
            close(c);
        });
        steps.add(c -> {
            MarketAPI first = home(HEG, 0);
            PersonAPI person = person(c, "heg");
            c.expect(count(fleet(), person) == 1f, "hire puts the item in fleet cargo");
            c.expect(tracker(HEG) != null, "hire starts the tracker");
            c.expect(!AmbassadorPersonManager.hasAmbassador(first), "hire takes the ambassador off the market");
            String rule = hireRule(home(HEG, 1));
            c.expect("IndEvo_ambassadorHireFail".equals(rule), "second hire refused while the first is in cargo (" + rule + ")");
            rule = hireRule(home(TT, 0));
            c.expect("IndEvo_ambassadorHire".equals(rule), "other faction can still hire (" + rule + ")");
            install(c.market("m0"), person, fleet());
            c.days(1.2f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "heg");
            MarketAPI office = c.market("m0");
            c.expect(inOffice(office, person), "ambassador takes office the next day");
            c.expect(tracker(HEG) == null, "tracker ends once in office");
            String rule = hireRule(home(HEG, 1));
            c.expect("IndEvo_ambassadorAlreadyInstalled".equals(rule), "716 second hire of a faction refused (" + rule + ")");
            c.reload();
        });
        steps.add(c -> {
            PersonAPI person = person(c, "heg");
            MarketAPI office = c.market("m0");
            c.expect(inOffice(office, person), "ambassador in office after reload");
            c.expect(item(person).equals(embassy(office).getSpecialItem()), "installed item survives reload");
            String rule = hireRule(home(HEG, 1));
            c.expect("IndEvo_ambassadorAlreadyInstalled".equals(rule), "716 still refused after reload (" + rule + ")");
            open(c, office);
        });
        // 701 and the dismissal in one dialog
        steps.add(c -> {
            PersonAPI person = person(c, "heg");
            MarketAPI office = c.market("m0");
            HAAmbassadorEventFactor factor = new HAAmbassadorEventFactor();
            talk(person);
            c.expect(menu(office, person).contains("IndEvo_ambassadorMenuSabotage") && !menu(office, person).contains("IndEvo_ambassadorMenuStopSabotage"), "701 menu offers sabotage");
            plugin.optionSelected(null, "IndEvo_ambSabotageRep");
            plugin.optionSelected(null, "IndEvo_ambSabotageRep_confirm");
            c.expect(factor.getIsSabotaging(), "701 sabotage on");
            c.expect(menu(office, person).contains("IndEvo_ambassadorMenuStopSabotage") && !menu(office, person).contains("IndEvo_ambassadorMenuSabotage"), "701 menu offers the way back");
            plugin.optionSelected(null, "IndEvo_ambStopSabotageRep");
            plugin.optionSelected(null, "IndEvo_ambStopSabotageRep_confirm");
            c.expect(!factor.getIsSabotaging(), "701 sabotage off");
            c.expect(menu(office, person).contains("IndEvo_ambassadorMenuSabotage"), "701 menu offers sabotage again");
            plugin.optionSelected(null, "IndEvo_ambassadorRemove");
            plugin.optionSelected(null, "IndEvo_ambassadorRemovalConfirm");
            close(c);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "heg");
            MarketAPI office = c.market("m0");
            c.near(rep(HEG), -0.05f, 0.001f, "dismissal at favorable costs 25 rep");
            c.expect(!AmbassadorPersonManager.hasAmbassador(office), "dismissal clears the office");
            c.expect(embassy(office).getSpecialItem() == null, "dismissal frees the embassy");
            c.expect(inOffice(home(HEG, 0), person), "dismissed ambassador goes home");
            String rule = hireRule(home(HEG, 1));
            c.expect("IndEvo_ambassadorHire".equals(rule), "hire offered again after dismissal (" + rule + ")");
            setRep(HEG, 0.2f);
        });

        // 711
        steps.add(c -> {
            MarketAPI market = c.market("m0");
            market.addIndustry(Ids.WAREHOUSES);
            market.reapplyIndustries();
            SubmarketAPI sub = ((Warehouses) market.getIndustry(Ids.WAREHOUSES)).addSubmarket(new WarehouseSubmarketData(WAREHOUSE, "Warehouse 1", false, true, false));
            c.expect(sub != null, "warehouse submarket added");
            PersonAPI person = hire(c, "dik", home(DIK, 0));
            move(fleet(), sub.getCargo(), person);
            c.days(3f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "dik");
            c.expect(count(warehouse(c.market("m0")), person) == 1f, "711 item stays in the warehouse");
            c.near(rep(DIK), 0.2f, 0.001f, "711 no rep loss for a warehouse");
            c.expect(murders() == 0, "711 no bounty hunter for a warehouse");
            c.expect(tracker(DIK) != null && known(person), "711 ambassador still tracked");
            c.reload();
        });
        steps.add(c -> {
            PersonAPI person = person(c, "dik");
            c.expect(count(warehouse(c.market("m0")), person) == 1f, "711 item in the warehouse after reload");
            move(warehouse(c.market("m0")), home(DIK, 0).getSubmarket(Submarkets.SUBMARKET_OPEN).getCargo(), person);
            c.days(1.5f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "dik");
            c.near(rep(DIK), -0.1f, 0.001f, "selling on an open market costs 30 rep");
            c.expect(murders() == 1, "selling starts the bounty hunter script");
            c.expect(tracker(DIK) == null && !known(person), "selling ends the ambassador");
            c.expect(count(home(DIK, 0).getSubmarket(Submarkets.SUBMARKET_OPEN).getCargo(), person) == 0f, "sold item is deleted");
            clearMurders();
            setRep(DIK, 0.2f);
        });

        // 712
        steps.add(c -> {
            Colony.jump(c.market("m1").getStarSystem(), 4000f, 0f);
            PersonAPI person = hire(c, "church", home(CHURCH, 0));
            detach(person, Behaviour.FleetBehaviour.STAY, null);
            c.expect(DetachmentMemory.isDetachmentActive(1), "detachment spawned");
            c.expect(count(DetachmentMemory.getDetachment(1).getCargo(), person) == 1f, "712 item moved into the detachment");
            c.expect(count(fleet(), person) == 0f, "712 item left the fleet");
            c.days(3f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "church");
            c.near(rep(CHURCH), 0.2f, 0.001f, "712 no rep loss for a detachment");
            c.expect(murders() == 0, "712 no bounty hunter for a detachment");
            c.expect(tracker(CHURCH) != null && known(person), "712 ambassador still tracked");
            c.expect(count(DetachmentMemory.getDetachment(1).getCargo(), person) == 1f, "712 item still on the detachment");
            c.reload();
        });
        steps.add(c -> c.days(2f));
        steps.add(c -> {
            PersonAPI person = person(c, "church");
            c.expect(tracker(CHURCH) != null, "712 ambassador tracked after reload");
            c.near(rep(CHURCH), 0.2f, 0.001f, "712 no rep loss after reload");
            FleetUtils.mergeDetachment(1);
            c.expect(count(fleet(), person) == 1f, "712 item back in the fleet on merge");
            c.days(1.5f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "church");
            c.expect(tracker(CHURCH) != null && known(person), "712 ambassador tracked after merge");
            install(c.market("m2"), person, fleet());
            c.days(1.2f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "church");
            c.expect(inOffice(c.market("m2"), person), "ambassador carried by a merged detachment takes office");
            c.expect(tracker(CHURCH) == null, "tracker ends after a merged detachment");
            IndEvo_ambassadorRemoval.removeAmbassadorWithPenalty(c.market("m2"));
            setRep(CHURCH, 0.2f);
            detach(hire(c, "spaced", home(CHURCH, 0)), Behaviour.FleetBehaviour.STAY, null);
            c.days(1.2f);
        });
        steps.add(c -> {
            FleetUtils.mergeDetachment(1);
            c.days(1.2f);
        });
        steps.add(c -> {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            CustomCampaignEntityAPI pods = Misc.addCargoPods(player.getContainingLocation(), player.getLocation());
            move(fleet(), pods.getCargo(), person(c, "spaced"));
            c.days(1.5f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "spaced");
            c.near(rep(CHURCH), -0.1f, 0.001f, "spacing costs 30 rep");
            c.expect(murders() == 1, "spacing starts the bounty hunter script");
            c.expect(tracker(CHURCH) == null && !known(person), "spacing ends the ambassador");
            clearMurders();
            setRep(CHURCH, 0.2f);
            detach(hire(c, "sunk", home(DIK, 0)), Behaviour.FleetBehaviour.STAY, null);
            c.days(1.2f);
        });
        steps.add(c -> {
            DetachmentMemory.getDetachment(1).despawn(CampaignEventListener.FleetDespawnReason.DESTROYED_BY_BATTLE, null);
            c.days(1.5f);
        });
        steps.add(c -> {
            c.expect(lost(DIK) && tracker(DIK) == null, "destroyed detachment reports the ambassador lost");
            c.near(rep(DIK), 0.2f, 0.001f, "no rep loss for a destroyed detachment");
            MarketAPI to = c.market("m1");
            PersonAPI person = hire(c, "freight", home(CHURCH, 0));
            detach(person, Behaviour.FleetBehaviour.TRANSPORT, to.getId());
            c.until(() -> count(storage(to), person) > 0f, 40f, "transport detachment delivers the item");
        });
        steps.add(c -> {
            PersonAPI person = person(c, "freight");
            c.expect(tracker(CHURCH) != null && known(person), "ambassador tracked after a transport detachment");
            c.near(rep(CHURCH), 0.2f, 0.001f, "no rep loss for a transport detachment");
            install(c.market("m1"), person, storage(c.market("m1")));
            c.days(1.2f);
        });
        steps.add(c -> {
            c.expect(inOffice(c.market("m1"), person(c, "freight")), "ambassador delivered by a detachment takes office");
            IndEvo_ambassadorRemoval.removeAmbassadorWithPenalty(c.market("m1"));
            setRep(CHURCH, 0.2f);
        });

        // 713
        steps.add(c -> {
            MarketAPI from = c.market("m0");
            from.addIndustry(Ids.PORT);
            from.reapplyIndustries();
            PersonAPI person = hire(c, "per", home(PER, 0));
            move(fleet(), storage(from), person);
            ShippingContract contract = new ShippingContract();
            contract.fromMarketId = from.getId();
            contract.toMarketId = c.market("m1").getId();
            ShippingContractMemory.addOrReplaceContract(contract);
            ShippingManager.getInstanceOrRegister();
            c.until(() -> shipment(person) != null, 3f, "713 courier picks up the item");
        });
        steps.add(c -> {
            PersonAPI person = person(c, "per");
            c.expect(count(storage(c.market("m0")), person) == 0f, "713 item left storage");
            c.put("shipped", Global.getSector().getClock().getTimestamp());
            c.reload();
        });
        steps.add(c -> {
            PersonAPI person = person(c, "per");
            MarketAPI to = c.market("m1");
            c.expect(shipment(person) != null, "713 shipment survives reload");
            c.until(() -> count(storage(to), person) > 0f, 40f, "713 courier delivers the item");
        });
        steps.add(c -> {
            PersonAPI person = person(c, "per");
            float days = Global.getSector().getClock().getElapsedDaysSince((Long) c.get("shipped"));
            c.note("courier took " + days + " days");
            c.expect(days >= 1f, "713 shipment spanned a new day");
            c.expect(!lost(PER), "713 not reported lost");
            c.expect(tracker(PER) != null && known(person), "713 ambassador still tracked");
            c.near(rep(PER), 0.2f, 0.001f, "713 no rep loss for a shipment");
            install(c.market("m1"), person, storage(c.market("m1")));
            c.days(1.2f);
        });
        steps.add(c -> {
            c.expect(inOffice(c.market("m1"), person(c, "per")), "713 shipped ambassador takes office");
            c.days(2f);
        });
        steps.add(c -> {
            MarketAPI office = c.market("m1");
            c.expect(inOffice(office, person(c, "per")) && embassy(office).getSpecialItem() != null, "713 shipped ambassador not retrieved");
        });

        // 714, tritachyon above the cap and persean below it through the same month ends
        steps.add(c -> {
            PersonAPI person = hire(c, "tt", home(TT, 0));
            install(c.market("m2"), person, fleet());
            setRep(TT, 0.6f);
            setRep(PER, 0.15f);
            c.days(2f);
        });
        steps.add(c -> {
            c.expect(inOffice(c.market("m2"), person(c, "tt")), "tritachyon ambassador in office");
            Global.getSector().getPlayerFaction().adjustRelationship(TT, -0.05f);
            c.days(1.5f);
        });
        steps.add(c -> {
            c.near(rep(TT), 0.55f, 0.001f, "714 no penalty before the month ends");
            c.days(31f);
        });
        steps.add(c -> {
            c.near(rep(TT), 0.52f, 0.001f, "714 first month end bills half the loss");
            c.note("persean after one month " + rep(PER));
            c.expect(rep(PER) > 0.15f, "embassy gains rep below the cap");
            c.days(31f);
        });
        steps.add(c -> {
            c.near(rep(TT), 0.52f, 0.001f, "714 second month end bills nothing");
            c.note("persean after two months " + rep(PER));
            c.days(31f);
        });
        steps.add(c -> {
            c.near(rep(TT), 0.52f, 0.001f, "714 third month end bills nothing");
            c.note("persean after three months " + rep(PER));
            c.days(31f);
        });
        steps.add(c -> {
            c.near(rep(TT), 0.52f, 0.001f, "714 fourth month end bills nothing");
            c.near(rep(PER), 0.4f, 0.001f, "embassy gain stops at the cap");

            // alpha core halves the penalty multiplier, beta core raises the cap
            embassy(c.market("m2")).setAICoreId(Commodities.ALPHA_CORE);
            c.market("m2").reapplyIndustries();
            embassy(c.market("m1")).setAICoreId(Commodities.BETA_CORE);
            c.market("m1").reapplyIndustries();
            setRep(PER, 0.5f);
            c.days(1.5f);
        });
        steps.add(c -> {
            Global.getSector().getPlayerFaction().adjustRelationship(TT, -0.1f);
            c.days(31f);
        });
        steps.add(c -> {
            c.near(rep(TT), 0.39f, 0.001f, "alpha core halves the month end penalty");
            c.near(rep(PER), 0.55f, 0.001f, "beta core raises the cap to 55");
            embassy(c.market("m1")).setAICoreId(null);
            c.market("m1").reapplyIndustries();
            c.days(1.5f);
        });
        steps.add(c -> {
            c.near(rep(PER), 0.35f, 0.001f, "removing the beta core costs 20 rep");
            c.days(0.1f);
        });

        // hostile, in cargo and in office
        steps.add(c -> {
            PersonAPI person = hire(c, "hostile", home(CHURCH, 0));
            setRep(CHURCH, -0.6f);
            setRep(PER, -0.6f);
            c.days(1.5f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "hostile");
            c.expect(count(fleet(), person) == 0f && tracker(CHURCH) == null, "hostile faction takes the carried ambassador back");
            c.expect(inOffice(home(CHURCH, 0), person), "carried ambassador returns home when hostile");
            MarketAPI office = c.market("m1");
            c.expect(!AmbassadorPersonManager.hasAmbassador(office) && embassy(office).getSpecialItem() == null, "hostile faction clears the office");
            c.expect(inOffice(home(PER, 0), person(c, "per")), "ambassador in office returns home when hostile");
            setRep(CHURCH, 0.2f);
            setRep(PER, 0.2f);
        });

        // delay penalties, tracker day jumps instead of 124 days
        steps.add(c -> {
            hire(c, "late", home(HEG, 0));
            ReflectionUtils.set("daysPassed", tracker(HEG), 61);
            c.days(1.2f);
        });
        steps.add(c -> {
            c.near(rep(HEG), 0.1f, 0.001f, "day 62 costs 10 rep");
            ReflectionUtils.set("daysPassed", tracker(HEG), 92);
            c.days(1.2f);
        });
        steps.add(c -> {
            c.near(rep(HEG), -0.05f, 0.001f, "day 93 costs 15 rep");
            ReflectionUtils.set("daysPassed", tracker(HEG), 123);
            c.days(1.2f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "late");
            c.near(rep(HEG), -0.25f, 0.001f, "day 124 costs 20 rep");
            c.expect(tracker(HEG) == null && count(fleet(), person) == 0f, "day 124 sends the ambassador home");
            c.expect(inOffice(home(HEG, 0), person), "late ambassador back on the home market");
            setRep(HEG, 0.2f);
        });

        // recall through the faction memory key, in cargo and in office
        steps.add(c -> {
            hire(c, "recall", home(DIK, 0));
            Global.getSector().getMemoryWithoutUpdate().set(AmbassadorItemHelper.getFactionMemoryKey(Global.getSector().getFaction(DIK)), false);
            Global.getSector().getMemoryWithoutUpdate().set(AmbassadorItemHelper.getFactionMemoryKey(Global.getSector().getFaction(TT)), false);
            c.days(1.5f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "recall");
            c.expect(tracker(DIK) == null && count(fleet(), person) == 0f, "recall takes the carried ambassador back");
            MarketAPI office = c.market("m2");
            c.expect(!AmbassadorPersonManager.hasAmbassador(office) && embassy(office).getSpecialItem() == null, "recall clears the office");
            Global.getSector().getMemoryWithoutUpdate().unset(AmbassadorItemHelper.getFactionMemoryKey(Global.getSector().getFaction(DIK)));
            Global.getSector().getMemoryWithoutUpdate().unset(AmbassadorItemHelper.getFactionMemoryKey(Global.getSector().getFaction(TT)));
            embassy(office).setAICoreId(null);
            office.reapplyIndustries();
        });

        // item gone from every cargo
        steps.add(c -> {
            PersonAPI person = hire(c, "lost", home(TT, 0));
            fleet().removeItems(CargoAPI.CargoItemType.SPECIAL, item(person), 1);
            c.days(1.5f);
        });
        steps.add(c -> {
            c.expect(lost(TT) && tracker(TT) == null, "vanished item reported lost");
            c.expect(known(person(c, "lost")), "lost ambassador stays in important people until retrieved");
        });

        // 717
        steps.add(c -> {
            MarketAPI existing = home(HEG, 1);
            MarketAPI fresh = home(PER, 0);
            AmbassadorPersonManager.deleteAmbassador(fresh);
            c.expect(AmbassadorPersonManager.hasAmbassador(existing), "npc ambassador present before the setting goes off");

            Colony.setting(Setting.EMBASSY, false);
            Global.getSector().reportPlayerOpenedMarket(existing);
            Global.getSector().reportPlayerOpenedMarket(fresh);
            c.expect(!AmbassadorPersonManager.hasAmbassador(existing), "717 setting off removes npc ambassadors");
            c.expect(!AmbassadorPersonManager.hasAmbassador(fresh), "717 setting off creates no npc ambassadors");

            Colony.setting(Setting.EMBASSY, true);
            Global.getSector().reportPlayerOpenedMarket(fresh);
            c.expect(AmbassadorPersonManager.hasAmbassador(fresh), "npc ambassadors back with the setting on");
        });

        // 718
        steps.add(c -> {
            MarketAPI market = c.market("m1");
            PersonAPI person = hire(c, "early", home(HEG, 1));
            install(market, person, fleet());
            market.removeIndustry(Ids.EMBASSY, MarketAPI.MarketInteractionMode.REMOTE, false);
            c.expect(count(storage(market), person) == 1f, "718 remote removal puts the item in storage");
            c.days(1.5f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "early");
            MarketAPI market = c.market("m1");
            c.expect(!lost(HEG) && tracker(HEG) != null && known(person), "718 ambassador not lost");
            c.expect(count(storage(market), person) == 1f, "718 item still in storage");
            market.addIndustry(Ids.EMBASSY);
            market.reapplyIndustries();
            install(market, person, storage(market));
            market.removeIndustry(Ids.EMBASSY, MarketAPI.MarketInteractionMode.LOCAL, false);
            c.expect(count(fleet(), person) == 1f, "718 local removal puts the item in the fleet");
            market.addIndustry(Ids.EMBASSY);
            market.reapplyIndustries();
            install(market, person, fleet());
            c.days(1.2f);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "early");
            MarketAPI market = c.market("m1");
            c.expect(inOffice(market, person), "718 reinstalled ambassador takes office");
            setRep(HEG, 0.2f);
            market.removeIndustry(Ids.EMBASSY, MarketAPI.MarketInteractionMode.REMOTE, false);
            c.near(rep(HEG), -0.05f, 0.001f, "removing an embassy in office costs the dismissal penalty");
            c.expect(inOffice(home(HEG, 1), person), "removing an embassy in office sends the ambassador home");
            c.expect(count(storage(market), person) == 0f && count(fleet(), person) == 0f, "no item left behind after removal in office");
            market.addIndustry(Ids.EMBASSY);
            market.reapplyIndustries();
            setRep(HEG, 0.2f);
        });

        // relocation
        steps.add(c -> {
            PersonAPI person = hire(c, "mover", home(CHURCH, 0));
            install(c.market("m0"), person, fleet());
            c.market("m0").addCondition(Conditions.LUDDIC_MAJORITY);
            c.market("m0").reapplyConditions();
            setRep(CHURCH, 0.4f);
            c.days(1.2f);
        });
        steps.add(c -> {
            MarketAPI market = c.market("m0");
            c.expect(!embassy(market).isAmbMovementAllowed(), "no relocation in the first months");
            ReflectionUtils.set("monthsPassed", embassy(market), 3);
            c.expect(embassy(market).isAmbMovementAllowed(), "relocation allowed after three months");
            new IndEvo_moveAmbassadorToClosestEmbassy().execute(null, null, null, memory(market, null));
            c.frames(5);
        });
        steps.add(c -> {
            PersonAPI person = person(c, "mover");
            MarketAPI from = c.market("m0");
            MarketAPI to = !AmbassadorPersonManager.hasAmbassador(c.market("m1")) ? c.market("m2") : c.market("m1");
            c.expect(!AmbassadorPersonManager.hasAmbassador(from) && embassy(from).getSpecialItem() == null, "relocation clears the old office");
            c.expect(inOffice(to, person) && item(person).equals(embassy(to).getSpecialItem()), "relocation fills the new office");
            c.put("office", to.getId());
        });

        // 708, every band has a row, also on the band edges and between hundredths
        steps.add(c -> {
            MarketAPI office = c.market("office");
            PersonAPI person = person(c, "mover");
            float[] rels = {-0.3f, -0.25f, -0.2f, -0.1f, -0.05f, 0.1f, 0.105f, 0.25f, 0.2500001f, 0.3f, 0.5f, 0.6f};
            String[] rows = {"1", "2", "2", "3", "3", "3", "4", "4", "5", "5", "5", "6"};
            for (int i = 0; i < rels.length; i++) {
                setRep(CHURCH, rels[i]);
                String rule = rule(office, person, "IndEvo_ambassadorRemove");
                c.expect(("IndEvo_ambassadorRemove" + rows[i]).equals(rule), "708 dismiss row at " + rels[i] + " (" + rule + ")");
            }
        });

        // 704
        steps.add(c -> {
            setRep(CHURCH, 0.55f);
            TooltipMakerAPI tooltip = Global.getSettings().createCustom(400f, 400f, null).createUIElement(400f, 400f, false);
            new HAAmbassadorEventFactor().getMainRowTooltip(null).createTooltip(tooltip, false, null);
            Object last = tooltip.getPrev();
            String text = last instanceof LabelAPI ? ((LabelAPI) last).getText() : String.valueOf(last);
            c.expect(text.contains("[40/40]"), "704 hostile activity tooltip caps at 40 (" + text + ")");
        });

        // 701 sign of the hostile activity factor
        steps.add(c -> {
            setRep(CHURCH, 0.4f);
            HostileActivityEventIntel intel = HostileActivityEventIntel.get();
            c.expect(intel != null, "hostile activity event exists");
            HAAmbassadorEventFactor factor = new HAAmbassadorEventFactor();
            float reduction = HAAmbassadorEventFactor.getReductionAmtForFaction(Global.getSector().getFaction(CHURCH));
            c.note("church reduction " + reduction);
            c.expect(reduction > 0f, "church ambassador lowers hostile activity");
            c.expect(factor.getProgress(null) < 0, "no sabotage lowers progress (" + factor.getProgress(null) + ")");
            Global.getSector().getMemoryWithoutUpdate().set(HAAmbassadorEventFactor.HAA_SABOTAGE_MEMORY_KEY, true);
            c.expect(factor.getProgress(null) > 0, "sabotage raises progress (" + factor.getProgress(null) + ")");
            Global.getSector().getMemoryWithoutUpdate().set(HAAmbassadorEventFactor.HAA_SABOTAGE_MEMORY_KEY, false);
            c.expect(factor.getProgress(null) < 0, "sabotage off lowers progress again");
        });

        // embassy turned off mid game, one ambassador in office and one in cargo
        steps.add(c -> {
            hire(c, "frozen", home(DIK, 0));
            setRep(DIK, 0.2f);
            ReflectionUtils.set("daysPassed", tracker(DIK), 61);
            setRep(CHURCH, 0.1f);
            Colony.setting(Setting.EMBASSY, false);
            c.put("offProgress", new HAAmbassadorEventFactor().getProgress(null));
            c.days(31f);
        });
        steps.add(c -> {
            c.note("setting off: church rep 0.1 -> " + rep(CHURCH) + ", ha progress " + c.get("offProgress"));
            c.near(rep(CHURCH), 0.1f, 0.001f, "no monthly gain with the embassy setting off");
            c.expect((Integer) c.get("offProgress") == 0, "no hostile activity effect with the embassy setting off");
            c.near(rep(DIK), 0.2f, 0.001f, "no delay penalty with the embassy setting off");
            Colony.setting(Setting.EMBASSY, true);
            c.days(1.2f);
        });
        steps.add(c -> c.near(rep(DIK), 0.1f, 0.001f, "delay penalties resume with the setting on"));
        return steps;
    }
}
