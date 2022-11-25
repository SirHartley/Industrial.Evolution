package industrial_evolution.splinterFleet.fleetAssignmentAIs;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import industrial_evolution.plugins.IndEvo_modPlugin;
import industrial_evolution.splinterFleet.FleetUtils;
import industrial_evolution.splinterFleet.LocationFollower.PlayerFleetFollower;
import industrial_evolution.splinterFleet.fleetManagement.DetachmentMemory;
import industrial_evolution.splinterFleet.fleetManagement.LoadoutMemory;
import com.fs.starfarer.api.util.Misc;

public class DeliverAssignmentAI extends BaseSplinterFleetAssignmentAIV2 {

    private String targetMarketId;
    public CargoTransferScript cargoTransferScript;
    boolean reset = false;

    public DeliverAssignmentAI(CampaignFleetAPI fleet) {
        super();
        this.fleet = fleet;
        this.targetMarketId = LoadoutMemory.getLoadout(DetachmentMemory.getNumForFleet(fleet)).transportTargetMarket;
        this.cargoTransferScript = new CargoTransferScript(fleet, targetMarketId);
        giveInitialAssignments();
    }

    @Override
    protected void pickNext() {
        if (fleet.getAI() == null) return;
        IndEvo_modPlugin.log("Deliver Detachment AI setting up");

        if(!cargoTransferScript.finished){
            checkOrCorrectMarket();
            MarketAPI market = Global.getSector().getEconomy().getMarket(targetMarketId);

            //if something happens and the assignments get cleared, head to the planet and despawn immediately
            fleet.addFloatingText("Heading to " + market.getName(), fleet.getFaction().getBaseUIColor(), 1f);

            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), ASSIGNMENT_DURATION_FOREVER, "Delivering cargo to " + market.getName());
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), ASSIGNMENT_DURATION_3_DAY, "Unloading Cargo", cargoTransferScript);
        }

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, PlayerFleetFollower.getToken(), ASSIGNMENT_DURATION_FOREVER, "Returning to main force", new Script() {
            @Override
            public void run() {
                FleetUtils.mergeFleetWithPlayerFleet(fleet);
            }
        });
    }

    @Override
    public void setFlags() {
        MemoryAPI splinterFleetMemory = fleet.getMemoryWithoutUpdate();
        splinterFleetMemory.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        splinterFleetMemory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if(fleet != null
                && fleet.getAI() != null
                && fleet.getCurrentAssignment() != null
                && !fleet.getCurrentAssignment().getAssignment().equals(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN)
                && !fleet.getCurrentAssignment().getAssignment().equals(FleetAssignment.ORBIT_PASSIVE)) headToPlayerIfTargettedAssignment();

        //resetFollowAssignment();
        if(!cargoTransferScript.finished) checkOrCorrectMarket();
    }

    public void checkOrCorrectMarket(){
        MarketAPI market = Global.getSector().getEconomy().getMarket(targetMarketId);
        if(market == null) {
            targetMarketId = getAlternateMarket(fleet);
            cargoTransferScript = new CargoTransferScript(fleet, targetMarketId);

            notifyPlayerOfMarketChange();
            giveInitialAssignments();
        }
    }

    public void notifyPlayerOfMarketChange(){
        MarketAPI m = Global.getSector().getEconomy().getMarket(targetMarketId);

        MessageIntel intel = new MessageIntel("A delivery detachment has %s.", Misc.getTextColor(), new String[]{"changed destination"}, Misc.getHighlightColor());
        intel.addLine(BaseIntelPlugin.BULLET + "New destination: %s in %s", Misc.getTextColor(), new String[]{m.getName(), m.getStarSystem().getBaseName()}, m.getFaction().getColor(), m.getFaction().getColor());
        intel.setIcon(Global.getSettings().getSpriteName("intel", "tradeFleet_valuable"));
        intel.setSound(BaseIntelPlugin.getSoundMajorPosting());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, m);
    }

    @Override
    public void reportFleetJumped(final CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        super.reportFleetJumped(fleet, from, to);

        if(fleet.isPlayerFleet() && cargoTransferScript.finished){
            //fleet.addAssignmentAtStart();
        }
    }

    public static String getAlternateMarket(SectorEntityToken toFleet){
        //get the absolute closest market and store yourself

        float dist = Float.MAX_VALUE;
        MarketAPI market = Global.getSector().getEconomy().getMarketsCopy().get(0);

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()){
            float d = Misc.getDistance(toFleet, m.getPrimaryEntity());
            if(d < dist) {
                dist = d;
                market = m;
            }
        }

        return market.getId();
    }

    public static class CargoTransferScript implements Script {
        private CampaignFleetAPI fleet;
        private String marketID;
        public boolean finished = false;

        public CargoTransferScript(CampaignFleetAPI fleet, String marketId) {
            this.fleet = fleet;
            this.marketID = marketId;
        }

        @Override
        public void run() {
            IndEvo_modPlugin.log("running cargo transfer script");

            MarketAPI market = Global.getSector().getEconomy().getMarket(marketID);
            if (market == null) market = Global.getSector().getEconomy().getMarket(getAlternateMarket(fleet));

            float distance = Misc.getDistanceLY(Global.getSector().getPlayerFleet(), market.getPrimaryEntity());
            float requiredFuel = fleet.getLogistics().getFuelCostPerLightYear() * distance * 2f;

            float requiredSuppliesPerDay = fleet.getLogistics().getShipMaintenanceSupplyCost();
            float lyPerDay = Misc.getLYPerDayAtBurn(fleet, fleet.getFleetData().getBurnLevel());
            float days = (float) Math.ceil(distance / lyPerDay);
            float requiredSuppliesHyperspace = requiredSuppliesPerDay * days;

            distance = Misc.getDistance(fleet, Misc.getDistressJumpPoint(market.getStarSystem()));
            days = distance / lyPerDay;
            float requiredSuppliesToExitSystem = requiredSuppliesPerDay * days;

            float requiredSupplies = (requiredSuppliesHyperspace + (requiredSuppliesToExitSystem * 3)) * 1.5f;

            float availableFuel = fleet.getCargo().getFuel();
            float availableSupplies = fleet.getCargo().getSupplies();
            int crew = fleet.getCargo().getCrew();

            float addRemoveFuel = Math.min(availableFuel, requiredFuel);
            float addRemoveSupplies = Math.min(availableSupplies, requiredSupplies);

            fleet.getCargo().removeFuel(addRemoveFuel);
            fleet.getCargo().removeSupplies(addRemoveSupplies);
            fleet.getCargo().removeCrew(crew);

            CargoAPI storage = Misc.getStorageCargo(market);
            storage.addAll(fleet.getCargo());
            fleet.getCargo().clear();

            fleet.getCargo().addFuel(addRemoveFuel);
            fleet.getCargo().addSupplies(addRemoveSupplies);
            fleet.getCargo().addCrew(crew);

            Global.getSector().getCampaignUI().addMessage(fleet.getName() + " delivered cargo to %s in %s and is now returning to main force.", Misc.getTextColor(), market.getName(), market.getContainingLocation().getName(), market.getFaction().getColor(), Misc.getHighlightColor());

            finished = true;
        }
    }
}
