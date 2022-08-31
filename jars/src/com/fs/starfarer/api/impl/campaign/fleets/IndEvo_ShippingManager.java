package com.fs.starfarer.api.impl.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_PrivatePort;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.intel.misc.IndEvo_ShippingFleetIntel;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.plugins.timers.IndEvo_newDayListener;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Deprecated
public class IndEvo_ShippingManager extends BaseRouteFleetManager implements IndEvo_newDayListener {

    public static final String KEY = "$IndEvo_ShippingManager";

    public final List<IndEvo_PrivatePort.ShippingContainer> containerList = new ArrayList<>();

    public static final Integer ROUTE_SRC_LOAD = 1;
    public static final Integer ROUTE_TRAVEL_DST = 2;
    public static final Integer ROUTE_DST_UNLOAD = 3;
    public static final Integer ROUTE_DESPAWN = 4;

    public static final Integer ROUTE_TRAVEL_SRC = 5;

    public static final String SOURCE_ID = "IndEvo_Shipping";
    public static final Logger log = Global.getLogger(IndEvo_ShippingManager.class);

    public IndEvo_ShippingManager() {
        super(0.2f, 0.3f);
    }

    public static IndEvo_ShippingManager getCurrentInstance() {
        return (IndEvo_ShippingManager) Global.getSector().getMemoryWithoutUpdate().get(KEY);
    }

    @Override
    public void onNewDay() {

        //removed to account for beta functionality

        /*removeAllContainersIfNoPort();
        cleanContainers();
        tickDownFraudCounter();

        disruptContainersIfNecessary();

        for (IndEvo_PrivatePort.Shipment container : getContainerList()) {
            //dispatch the container
            if (container.isReadyForDispatch()) {
                performContainerAction(container);
            } else {
                container.notifyDayPassed();
            }
        }*/
    }

    private void tickDownFraudCounter() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        float amt = getFraudTimeoutAmt();
        if (amt > 0) {
            amt--;
        }

        mem.set(IndEvo_ShippingFleetIntel.FRAUD_TIME_KEY, amt);
    }

    public static int getFraudTimeoutAmt() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        return (int) mem.getFloat(IndEvo_ShippingFleetIntel.FRAUD_TIME_KEY);
    }

    public static boolean isFraudTimeout() {
        return getFraudTimeoutAmt() > 0;
    }

    public static float getFraudCounter() {
        return 1 + Global.getSector().getMemoryWithoutUpdate().getFloat(IndEvo_ShippingFleetIntel.FRAUD_INSTANCES_KEY);
    }

    public static void incrementFraudCounter() {
        float current = Global.getSector().getMemoryWithoutUpdate().getFloat(IndEvo_ShippingFleetIntel.FRAUD_INSTANCES_KEY);
        Global.getSector().getMemoryWithoutUpdate().set(IndEvo_ShippingFleetIntel.FRAUD_INSTANCES_KEY, current + 1);
    }

    private void cleanContainers() {
        List<IndEvo_PrivatePort.ShippingContainer> removalList = new ArrayList<>();

        for (IndEvo_PrivatePort.ShippingContainer container : containerList) {
            if (container.isMarkedForRemoval()) removalList.add(container);
        }

        for (IndEvo_PrivatePort.ShippingContainer container : removalList) {
            removeContainer(container);
        }
    }

    private void cycleContainer(IndEvo_PrivatePort.ShippingContainer container) {
        boolean singleShipment = container.isSingleShipment();

        if (singleShipment) {
            container.setMarkedForRemoval(true);
        } else {
            container.setDaysPassed(0);
        }
    }

    private MarketAPI getClosestPort(IndEvo_PrivatePort.ShippingContainer container) {
        MarketAPI market = IndEvo_IndustryHelper.getClosestMarketWithIndustry(container.getOriginMarket(), IndEvo_ids.PORT);

        if (market == null) {
            for (MarketAPI m : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
                if (m.hasIndustry(IndEvo_ids.PORT)) {
                    market = m;
                    break;
                }
            }
        }

        return market;
    }

    private void disruptContainersIfNecessary() {
        int delay = getLowestDisruptionTime();

        if (delay > 0) {
            for (IndEvo_PrivatePort.ShippingContainer c : containerList) {
                c.setDelayByDays(delay);
            }
        }
    }

    private int getLowestDisruptionTime() {
        int minTime = 0;

        for (MarketAPI m : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (m.hasIndustry(IndEvo_ids.PORT) && m.getIndustry(IndEvo_ids.PORT).isDisrupted()) {
                minTime = (int) Math.min(m.getIndustry(IndEvo_ids.PORT).getDisruptedDays(), minTime);
            }
        }

        return minTime;
    }

    private void chargePlayer(float amt, IndEvo_PrivatePort.ShippingContainer container) {
        MonthlyReport report = SharedData.getData().getCurrentReport();
        MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
        MonthlyReport.FDNode mNode = report.getNode(marketsNode, getClosestPort(container).getId());
        MonthlyReport.FDNode indNode = report.getNode(mNode, "industries");
        MonthlyReport.FDNode iNode = report.getNode(indNode, IndEvo_ids.PORT);

        iNode.upkeep += amt;
    }

    private void removeAllContainersIfNoPort() {
        boolean none = true;
        for (MarketAPI m : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (m.hasIndustry(IndEvo_ids.PORT)) none = false;
        }

        if (none) {
            for (IndEvo_PrivatePort.ShippingContainer container : containerList) {
                container.setMarkedForRemoval(true);
            }
        }
    }

    private void performContainerAction(IndEvo_PrivatePort.ShippingContainer container) {
        IndEvo_PrivatePort.ShippingContainer.ShippingStatus status = container.getShippingStatus(false);

        switch (status) {
            case FAILURE_ORIGIN_LOST:
                container.setMarkedForRemoval(true);
                log.info("Shipping not possible as originMarket is gone - removing container.");
                break;
            case FAILURE_ORIGIN_NO_SPACEPORT:
                if (!container.getOriginMarket().hasIndustry(Industries.SPACEPORT)) {
                    container.setDelayByDays(31);
                } else {
                    container.setDelayByDays((int) container.getOriginMarket().getIndustry(Industries.SPACEPORT).getDisruptedDays() + 1);
                }
                log.info("Shipping not possible as " + container.getOriginMarket().getName() + " has no functional spaceport - delaying.");
                break;
            case FAILURE_ORIGIN_SUBMARKET_LOST:
                container.setMarkedForRemoval(true);
                log.info("Shipping not possible as " + container.getOriginMarket().getName() + " no longer has " + container.getOriginSubmarketId() + " - removing container.");
                break;
            case FAILURE_TARGET_LOST:
                container.setMarkedForRemoval(true);
                log.info("Shipping not possible as targetMarket is gone - removing container.");
                break;
            case FAILURE_TARGET_NO_SPACEPORT:
                if (!container.getTargetMarket().hasIndustry(Industries.SPACEPORT)) {
                    container.setDelayByDays(31);
                } else {
                    container.setDelayByDays((int) container.getTargetMarket().getIndustry(Industries.SPACEPORT).getDisruptedDays() + 1);
                }
                log.info("Shipping not possible as " + container.getTargetMarket().getName() + " has no functional spaceport - delaying.");
                break;
            case FAILURE_TARGET_SUBMARKET_LOST:
                container.setMarkedForRemoval(true);
                log.info("Shipping not possible as " + container.getTargetMarket().getName() + " no longer has " + container.getTargetSubmarketId() + " - removing container.");
                break;
            case FAILURE_HOSTILE:
                container.setDelayByDays(31);
                log.info("Shipping not possible as origin or target faction hostile to each other and independent - delaying.");
                break;
            case FAILURE_EMPTY:
                container.setDelayByDays(31);
                log.info("Shipping not possible, origin has no eligible cargo - delaying.");
                break;
            case SUCCESS_PARTIAL:
            case SUCCESS:
                log.info("Shipping possible");
                addRouteFleetForContainer(container);
                cycleContainer(container);
                break;
            case NULL:
            default:
                throw new IllegalArgumentException("Something broke, IndEvo_ShippingManager container responded with ShippingStatus.NULL");
        }
    }

    public void advance(float amount) {
    }

    public void addContainer(IndEvo_PrivatePort.ShippingContainer container) {
        if (!containerList.contains(container)) containerList.add(container);
    }

    public IndEvo_PrivatePort.ShippingContainer getContainer(String id) {
        for (IndEvo_PrivatePort.ShippingContainer c : containerList) {
            if (c.getId().equals(id)) return c;
        }

        return null;
    }

    public void removeContainer(IndEvo_PrivatePort.ShippingContainer container) {
        containerList.remove(container);
    }

    public List<IndEvo_PrivatePort.ShippingContainer> getContainerList() {
        List<IndEvo_PrivatePort.ShippingContainer> l = new ArrayList<>();

        for (IndEvo_PrivatePort.ShippingContainer c : containerList) {
            if (!c.isMarkedForRemoval()) l.add(c);
        }

        return l;
    }

    protected String getRouteSourceId() {
        return SOURCE_ID;
    }

    @Override
    protected int getMaxFleets() {
        return 100;
    }

    protected boolean addRouteFleetForContainer(IndEvo_PrivatePort.ShippingContainer container) {
        MarketAPI from = container.getOriginMarket();
        MarketAPI to = container.getTargetMarket();

        if (from != null && to != null) {

            if (isAstropolisMarketOfTarget(from, to) || isAstropolisOfSameMarket(from, to)) {
                log.info("Direct cargo transfer from " + from.getName() + " to " + to.getName());
                directTransfer(from, to, container);

            } else {
                //removes target cargo from origin market as well
                IndEvo_ShippingFleetAssignmentAI.EconomyRouteData data = createData(container);

                log.info("Added shipping fleet route from " + from.getName() + " to " + to.getName());

                Long seed = new Random().nextLong();
                String id = getRouteSourceId();

                RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(from);
                String factionId = from.getFactionId();

                //set to independent if the factions are hostile
                if (from.getFaction().isHostileTo(to.getFaction())) {
                    if (!from.getFaction().isHostileTo(Factions.INDEPENDENT) &&
                            !to.getFaction().isHostileTo(Factions.INDEPENDENT)) {
                        factionId = Factions.INDEPENDENT;
                    } else {
                        return false;
                    }
                }

                extra.factionId = factionId;

                RouteManager.RouteData route = RouteManager.getInstance().addRoute(id, from, seed, extra, this);
                route.setCustom(data);
                data.intel = new IndEvo_ShippingFleetIntel(container, route); //create the intel

                float orbitDays;

                orbitDays = data.size * (0.3f + (float) Math.random() * 0.5f);
                orbitDays = Math.max(1, orbitDays);
                orbitDays = Math.min(7, orbitDays);

                route.addSegment(new RouteManager.RouteSegment(ROUTE_SRC_LOAD, orbitDays, from.getPrimaryEntity()));
                route.addSegment(new RouteManager.RouteSegment(ROUTE_TRAVEL_DST, from.getPrimaryEntity(), to.getPrimaryEntity()));
                route.addSegment(new RouteManager.RouteSegment(ROUTE_DST_UNLOAD, orbitDays * 0.5f, to.getPrimaryEntity()));
                route.addSegment(new RouteManager.RouteSegment(ROUTE_DESPAWN, 1, to.getPrimaryEntity()));
                setDelay(route);

                //register and run the intel
                data.intel.init();

                //charge the player
                chargePlayer(container.getTotalShippingCost(data.cargo), container);
            }
        }

        return true;
    }

    public boolean isAstropolisMarketOfTarget(MarketAPI target, MarketAPI check) {
        for (SectorEntityToken e : target.getStarSystem().getAllEntities()) {
            if (e.getOrbitFocus() != null
                    && e.getOrbitFocus().equals(target.getPrimaryEntity())
                    && e.getCustomEntityType() != null
                    && e.getCustomEntityType().contains("boggled")
                    && e.getMarket() != null
                    && e.getMarket() == check) {
                return true;
            }
        }
        return false;
    }

    public boolean isAstropolisOfSameMarket(MarketAPI from, MarketAPI to) {
        //get orbit focus of from
        //get all astropolis of focus
        //check if to is one of them

        if (from.getPrimaryEntity() != null
                && from.getPrimaryEntity().getOrbitFocus() != null
                && from.getPrimaryEntity().getOrbitFocus().getMarket() != null) {

            MarketAPI focus = from.getPrimaryEntity().getOrbitFocus().getMarket();

            for (SectorEntityToken e : focus.getStarSystem().getAllEntities()) {
                if (e.getOrbitFocus() != null
                        && e.getOrbitFocus().equals(focus.getPrimaryEntity())
                        && e.getCustomEntityType() != null
                        && e.getCustomEntityType().contains("boggled")
                        && e.getMarket() != null
                        && e.getMarket() == to) {
                    return true;
                }
            }
        }

        return false;
    }

    private void directTransfer(MarketAPI from, MarketAPI to, IndEvo_PrivatePort.ShippingContainer container) {
        CargoAPI actualCargo = container.loadActualCargoFromOrigin();
        String submarketId = container.getTargetSubmarketId();

        log.info("Transferring shipment cargo to " + to.getName() + " " + submarketId);

        if (!to.isInEconomy()) return;

        actualCargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        if (!to.hasSubmarket(submarketId)) submarketId = Submarkets.SUBMARKET_STORAGE;

        SubmarketAPI sub = to.getSubmarket(submarketId);
        CargoAPI cargo = sub.getCargo();

        if (cargo != null) {
            for (CargoStackAPI stack : actualCargo.getStacksCopy()) {
                cargo.addFromStack(stack);
            }

            for (FleetMemberAPI member : actualCargo.getMothballedShips().getMembersListCopy()) {
                cargo.getMothballedShips().addFleetMember(member);
                member.getRepairTracker().setMothballed(false);
            }

            //charge the player
            float amt = container.getTotalShippingCost(actualCargo);
            chargePlayer(amt, container);

            log.info("Transfer complete");

            MessageIntel intel = new MessageIntel("A courier shipment has been directly transferred to an %s.", Misc.getTextColor(), new String[]{"Astropolis"}, Misc.getHighlightColor());
            intel.addLine(BaseIntelPlugin.BULLET + "From %s to %s.", Misc.getTextColor(), new String[]{from.getName(), to.getName()}, from.getFaction().getColor(), to.getFaction().getColor());
            intel.addLine(BaseIntelPlugin.BULLET + "Cargo: " + IndEvo_ShippingFleetAssignmentAI.EconomyRouteData.getCargoList(actualCargo) + ".", Misc.getTextColor(), new String[]{"items", "ships"}, Misc.getHighlightColor());
            intel.addLine(BaseIntelPlugin.BULLET + "Shipment Cost: %s.", Misc.getTextColor(), new String[]{Misc.getDGSCredits(amt)}, Misc.getHighlightColor());
            intel.setIcon(Global.getSettings().getSpriteName("intel", "tradeFleet_valuable"));
            intel.setSound(BaseIntelPlugin.getSoundMajorPosting());
            Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, to);

            return;
        }

        log.info("Transfer failed");
    }

    protected void addRouteFleetIfPossible() {

    }

    protected void setDelay(RouteManager.RouteData route) {
        IndEvo_ShippingFleetAssignmentAI.EconomyRouteData data = (IndEvo_ShippingFleetAssignmentAI.EconomyRouteData) route.getCustom();

        float delay;
        if (data.size <= 4f) {
            delay = 2f;
        } else if (data.size <= 8f) {
            delay = 3f;
        } else {
            delay = 4f;
        }

        data.intel.setDaysToLaunch(delay);
        route.setDelay(delay);
    }

    public static IndEvo_ShippingFleetAssignmentAI.EconomyRouteData createData(IndEvo_PrivatePort.ShippingContainer container) {
        MarketAPI from = container.getOriginMarket();
        MarketAPI to = container.getTargetMarket();

        IndEvo_ShippingFleetAssignmentAI.EconomyRouteData routeData = new IndEvo_ShippingFleetAssignmentAI.EconomyRouteData();
        routeData.from = from;
        routeData.to = to;
        routeData.cargo = container.loadActualCargoFromOrigin(); //also removes cargo from origin point

        float totalValue = 0f;

        for (CargoStackAPI s : routeData.cargo.getStacksCopy()) {
            totalValue += s.getSize();
        }

        totalValue += routeData.cargo.getMothballedShips().getNumMembers();

        routeData.size = (int) Math.ceil(totalValue / 500f);

        return routeData;
    }

    public boolean shouldCancelRouteAfterDelayCheck(RouteManager.RouteData route) {
        return false;
    }

    public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {
        Random random = new Random();
        if (route.getSeed() != null) {
            random = new Random(route.getSeed());
        }

        CampaignFleetAPI fleet = createCourierRouteFleet(route, random);
        if (fleet == null) return null;

        fleet.addScript(new IndEvo_ShippingFleetAssignmentAI(fleet, route));
        return fleet;
    }

    public static String getFleetTypeIdForTier(float tier) {
        String type = FleetTypes.TRADE;
        if (tier <= 3) type = FleetTypes.TRADE_SMALL;
        return type;
    }

    public static CampaignFleetAPI createCourierRouteFleet(RouteManager.RouteData route, Random random) {
        IndEvo_ShippingFleetAssignmentAI.EconomyRouteData data = (IndEvo_ShippingFleetAssignmentAI.EconomyRouteData) route.getCustom();

        MarketAPI from = data.from;
        MarketAPI to = data.to;
        float tier = data.size;

        String factionId = route.getFactionId();

        float fuel = 0f;
        float cargo = 120f;
        float personnel = 0f;
        //float ships = 0f;

        for (CargoStackAPI stack : data.cargo.getStacksCopy()) {
            if (!stack.isCommodityStack()) continue;

            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(stack.getCommodityId());

            if (spec.isMeta()) continue;

            if (spec.hasTag(Commodities.TAG_PERSONNEL)) {
                personnel += stack.getSize();
            } else if (spec.getId().equals(Commodities.FUEL)) {
                fuel += stack.getSize();
            } else {
                cargo += stack.getSize();
            }
        }

        // Buffalo is 5 FP for 300 cargo, or 60 cargo/FP
        int freighter = (int) Math.max(Math.ceil(cargo / 60f) * 2, 5);
        int combat = 5 + freighter * 2;
        if (to.hasCondition(Conditions.PIRATE_ACTIVITY)) {
            combat = Math.min(combat, 30);
        }

        int utility = freighter / 4;
        int total = freighter + combat + utility;

        int tanker = Math.round((total * 0.25f) + (fuel / 60));
        int liner = Math.round(personnel / 60);

        log.info("Creating courier fleet of tier " + tier + " at market [" + from.getName() + "]");

        String type = getFleetTypeIdForTier(tier);
        FleetParamsV3 params = new FleetParamsV3(
                from,
                null, // locInHyper
                factionId,
                route.getQualityOverride(), // qualityOverride
                type,
                combat, // combatPts
                freighter, // freighterPts
                tanker, // tankerPts
                0f, // transportPts
                liner, // linerPts
                utility, // utilityPts
                0f //-0.5f // qualityBonus
        );

        params.timestamp = route.getTimestamp();
        params.onlyApplyFleetSizeToCombatShips = true;
        params.officerLevelBonus = -5;
        params.officerNumberMult = 0.5f;
        params.random = random;

        params.ignoreMarketFleetSizeMult = true;    // only use doctrine size, not source source size
        params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
        fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
        fleet.setNoAutoDespawn(true);
        return fleet;
    }

    public boolean shouldRepeat(RouteManager.RouteData route) {
        return false;
    }

    public void reportAboutToBeDespawnedByRouteManager(RouteManager.RouteData route) {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(IndEvo_ShippingFleetIntel.class)) {
            if (intel instanceof IndEvo_ShippingFleetIntel) {
                IndEvo_ShippingFleetIntel indeInte = (IndEvo_ShippingFleetIntel) intel;
                if ((indeInte.getRoute() == route)) {
                    log.info("Despawning route for intel from " + indeInte.getOrigin().getName() + " to " + indeInte.getTarget().getName());
                }
            }
        }
    }
}