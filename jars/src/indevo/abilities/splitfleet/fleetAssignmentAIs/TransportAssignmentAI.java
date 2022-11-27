package indevo.abilities.splitfleet.fleetAssignmentAIs;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import indevo.utils.IndEvo_modPlugin;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

public class TransportAssignmentAI extends BaseSplinterFleetAssignmentAIV2 {

    private String targetMarketId;
    private CargoAndFleetTransferScript onCompletionScript;

    public TransportAssignmentAI(CampaignFleetAPI fleet) {
        super();
        this.fleet = fleet;
        this.targetMarketId = LoadoutMemory.getLoadout(DetachmentMemory.getNumForFleet(fleet)).transportTargetMarket;
        this.onCompletionScript = new CargoAndFleetTransferScript(fleet, targetMarketId);
        giveInitialAssignments();
    }

    @Override
    protected void pickNext() {
        if (fleet.getAI() == null) return;
        IndEvo_modPlugin.log("Transport Detachment AI setting up");

        checkOrCorrectMarket();
        MarketAPI market = Global.getSector().getEconomy().getMarket(targetMarketId);

        //if something happens and the assignments get cleared, head to the planet and despawn immediately
        fleet.addFloatingText("Heading to " + market.getName(), fleet.getFaction().getBaseUIColor(), 1f);

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), ASSIGNMENT_DURATION_FOREVER, "Delivering cargo to " + market.getName());
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), ASSIGNMENT_DURATION_3_DAY, "Unloading Cargo");
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, market.getPrimaryEntity(), ASSIGNMENT_DURATION_3_DAY, "Landing", onCompletionScript);
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

        checkOrCorrectMarket();

        //failsafe to make sure it runs if the fleet somehow dies and it hasn't completed
        //if (fleet.isAlive() && !onCompletionScript.finished) onCompletionScript.run();
    }

    public void checkOrCorrectMarket(){
        MarketAPI market = Global.getSector().getEconomy().getMarket(targetMarketId);
        if(market == null) {
            targetMarketId = getAlternateMarket(fleet);
            onCompletionScript = new CargoAndFleetTransferScript(fleet, targetMarketId);

            notifyPlayerOfMarketChange();
            giveInitialAssignments();
        }
    }

    public void notifyPlayerOfMarketChange(){
        MarketAPI m = Global.getSector().getEconomy().getMarket(targetMarketId);

        MessageIntel intel = new MessageIntel("A transport detachment has %s.", Misc.getTextColor(), new String[]{"changed destination"}, Misc.getHighlightColor());
        intel.addLine(BaseIntelPlugin.BULLET + "New destination: %s in %s", Misc.getTextColor(), new String[]{m.getName(), m.getStarSystem().getBaseName()}, m.getFaction().getColor(), m.getFaction().getColor());
        intel.setIcon(Global.getSettings().getSpriteName("intel", "tradeFleet_valuable"));
        intel.setSound(BaseIntelPlugin.getSoundMajorPosting());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, m);

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

    public static class CargoAndFleetTransferScript implements Script {
        private CampaignFleetAPI fleet;
        private List<FleetMemberAPI> membersList;
        private String marketID;
        public boolean finished = false;

        public CargoAndFleetTransferScript(CampaignFleetAPI fleet, String marketId) {
            this.fleet = fleet;
            this.membersList = fleet.getFleetData().getMembersListCopy();
            this.marketID = marketId;
        }

        @Override
        public void run() {
            IndEvo_modPlugin.log("running transfer script");

            MarketAPI market = Global.getSector().getEconomy().getMarket(marketID);
            if(market == null) market = Global.getSector().getEconomy().getMarket(getAlternateMarket(fleet));

            CargoAPI storage = Misc.getStorageCargo(market);
            storage.addAll(fleet.getCargo());
            storage.initMothballedShips("player");
            for (FleetMemberAPI member : membersList) {
                storage.getMothballedShips().addFleetMember(member);
            }

            if (fleet.isAlive()) fleet.despawn();

            finished = true;
        }
    }
}
