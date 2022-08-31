package com.fs.starfarer.api.impl.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.misc.IndEvo_ShippingFleetIntel;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import org.apache.log4j.Logger;

@Deprecated
public class IndEvo_ShippingFleetAssignmentAI extends RouteFleetAssignmentAI {

    public static final Logger log = Global.getLogger(IndEvo_ShippingFleetAssignmentAI.class);

    public static class EconomyRouteData {
        public CargoAPI cargo;
        public float size;
        public MarketAPI from;
        public MarketAPI to;
        public IndEvo_ShippingFleetIntel intel;
        boolean done = false;
        boolean fleetCargoSet = false;

        public static String getCargoList(CargoAPI cargo) {
            cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
            cargo.removeEmptyStacks();
            int stackTotal = 0;
            int shipTotal = cargo.getMothballedShips().getNumMembers();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                stackTotal += stack.getSize();
            }

            String stacks = stackTotal > 0 ? stackTotal + " items" : "";
            String ships = shipTotal > 0 ? shipTotal + " ships" : "";
            String and = shipTotal > 0 && stackTotal > 0 ? " and " : "";

            return ships + and + stacks;
        }
    }

    public IndEvo_ShippingFleetAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
        super(fleet, route);
        fleet.setName("Courier Fleet");
    }

    public static String getCargoList(RouteManager.RouteData route, RouteManager.RouteSegment segment) {
        int id = segment.getId();
        IndEvo_ShippingFleetAssignmentAI.EconomyRouteData data = (IndEvo_ShippingFleetAssignmentAI.EconomyRouteData) route.getCustom();

        switch (id) {
            case 1:
                return IndEvo_ShippingFleetAssignmentAI.EconomyRouteData.getCargoList(data.cargo);
            case 2:
            case 3:
                return route.getActiveFleet() != null ? IndEvo_ShippingFleetAssignmentAI.EconomyRouteData.getCargoList(route.getActiveFleet().getCargo()) : "null on active fleet";
            default:
                return "Nothing";

        }
    }

    protected String getCargoList(RouteManager.RouteSegment segment) {
        return getCargoList(route, segment);
    }

    protected void updateCargo(RouteManager.RouteSegment segment) {
        //adds cargo to the fleet so it drops on death

        //int index = route.getSegments().indexOf(segment);

        // 0: loading from
        // 1: moving to
        // 2: unloading to
        // 4: moving from
        IndEvo_ShippingFleetAssignmentAI.EconomyRouteData data = getData();

        int id = segment.getId();
        if (route.isExpired() || id == IndEvo_ShippingManager.ROUTE_SRC_LOAD) {
            syncMothballedShips(true, false);
        } else if (id == IndEvo_ShippingManager.ROUTE_DESPAWN) {
            syncMothballedShips(true, false);
        } else if (!data.fleetCargoSet) {
            CargoAPI cargo = fleet.getCargo();
            cargo.clear();

            //adds the ships it transports as mothballed ships to the fleet
            syncMothballedShips(false, false);

            if (!data.cargo.isEmpty()) {
                for (CargoStackAPI stack : data.cargo.getStacksCopy()) {
                    cargo.addFromStack(stack);
                }
            }

            data.fleetCargoSet = true;
        } else {
            syncMothballedShips(false, true);
        }
    }

    public void syncMothballedShips(boolean clear, boolean fromFleetCargo) {

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isMothballed()) {
                fleet.getFleetData().removeFleetMember(member);
            }
        }

        if (clear || route.getActiveFleet() == null) return;

        IndEvo_ShippingFleetAssignmentAI.EconomyRouteData data = getData();
        CargoAPI cargo = fromFleetCargo ? route.getActiveFleet().getCargo() : data.cargo;

        for (FleetMemberAPI member : cargo.getMothballedShips().getMembersListCopy()) {
            fleet.getFleetData().addFleetMember(member);
            member.getRepairTracker().setMothballed(true);
        }

        fleet.getFleetData().sort();
    }

    @Override
    protected String getStartingActionText(RouteManager.RouteSegment segment) {
        String list = getCargoList(segment);
        if (list.isEmpty()) {
            return "preparing for a voyage to " + getData().to.getName();
        }
        return "loading " + list + " at " + getData().from.getName();
    }

    @Override
    protected String getEndingActionText(RouteManager.RouteSegment segment) {
        String list = getCargoList(segment);
        if (list.isEmpty()) {
            return "orbiting " + getData().to.getName();
        }
        return "unloading " + list + " at " + getData().from.getName();
    }

    @Override
    protected String getTravelActionText(RouteManager.RouteSegment segment) {
        String list = getCargoList(segment);

        //int index = route.getSegments().indexOf(segment);
        int id = segment.getId();
        if (id == IndEvo_ShippingManager.ROUTE_TRAVEL_DST) {
            if (list.isEmpty()) {
                return "traveling to " + getData().to.getName();
            }
            return "delivering " + list + " to " + getData().to.getName();
        } else if (id == IndEvo_ShippingManager.ROUTE_TRAVEL_SRC) {
            if (list.isEmpty()) {
                return "returning to " + getData().from.getName();
            }
            return "returning to " + getData().from.getName() + " with " + list;
        }
        return super.getTravelActionText(segment);
    }

    @Override
    protected String getInSystemActionText(RouteManager.RouteSegment segment) {
        String list = getCargoList(segment);
        //int index = route.getSegments().indexOf(segment);
        int id = segment.getId();

        if (id == IndEvo_ShippingManager.ROUTE_DST_UNLOAD) {
            if (list.isEmpty()) {
                return "orbiting " + getData().to.getName();
            }
            return "unloading " + list + " at " + getData().to.getName();
        }

        return super.getInSystemActionText(segment);
    }

    @Override
    protected void addEndingAssignment(RouteManager.RouteSegment current, boolean justSpawned) {
        super.addEndingAssignment(current, justSpawned);
        updateCargo(current);
    }

    @Override
    protected void addLocalAssignment(RouteManager.RouteSegment current, boolean justSpawned) {
        super.addLocalAssignment(current, justSpawned);
        updateCargo(current);
    }

    @Override
    protected void addStartingAssignment(RouteManager.RouteSegment current, boolean justSpawned) {
        super.addStartingAssignment(current, justSpawned);
        updateCargo(current);
    }

    @Override
    protected void addTravelAssignment(RouteManager.RouteSegment current, boolean justSpawned) {
        super.addTravelAssignment(current, justSpawned);
        updateCargo(current);
    }


    protected IndEvo_ShippingFleetAssignmentAI.EconomyRouteData getData() {
        return (EconomyRouteData) route.getCustom();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (route.getCurrentSegmentId() == 4 && !getData().done) {
            log.info("Overriding despawn");
            getData().done = true;
            new IndEvo_ShippingFleetIntel.Deliver(getData().intel, getData().to).run();

            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
                    getData().to.getPrimaryEntity(),
                    31,
                    "Returning to " + getData().to.getName());
        }
    }
}