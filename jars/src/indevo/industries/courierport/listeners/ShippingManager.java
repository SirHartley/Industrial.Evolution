package indevo.industries.courierport.listeners;

import com.fs.starfarer.api.Global;
import indevo.utils.helper.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import indevo.industries.courierport.ShippingCargoManager;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingContractMemory;
import indevo.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import indevo.utils.timers.IndEvo_newDayListener;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class ShippingManager implements IndEvo_newDayListener {

    //on contract trigger, take the required stuff from the target storage and immediately store it in a magic box
    //spawn a fleet and attach a listener to check if it has reached its target
    //calculate the approximate flight time
    //if the fleet has not arrived after that flicht time * 1.2, despawn the fleet and magically add the cargo

    //permanent
    public static ShippingManager getInstanceOrRegister() {
        for (ShippingManager manager : Global.getSector().getListenerManager().getListeners(ShippingManager.class)) {
            return manager;
        }

        ShippingManager manager = new ShippingManager();
        Global.getSector().getListenerManager().addListener(manager);
        return manager;
    }

    @Override
    public void onNewDay() {
        List<ShippingContract> contractListCopy = new ArrayList<>(ShippingContractMemory.getContractList());

        boolean allowed = false;
        for (MarketAPI m : Misc.getPlayerMarkets(true)){
            if (m.hasIndustry(IndEvo_ids.PORT) && m.getIndustry(IndEvo_ids.PORT).isFunctional()){
                allowed = true;
                break;
            }
        }

        if(!allowed) return;

        for (ShippingContract c : contractListCopy){
            if(!c.isValid() && c.isActive) {
                c.isActive = false;
                notifyFailure(c);
                continue;
            }

            if(c.isActive) c.elapsedDays++;

            if(c.elapsedDays > c.getRecurrentDays()){
                c.elapsedDays = 0;
                performShippingActions(c);
                if(c.getRecurrentDays() == 0 && c.isActive) ShippingContractMemory.removeContract(c);
            }
        }
    }

    private void notifyFailure(ShippingContract contract){
        MessageIntel intel = new MessageIntel("A contract has been marked as %s.", Misc.getTextColor(), new String[]{"inactive"}, Misc.getNegativeHighlightColor());
        intel.addLine(BaseIntelPlugin.BULLET + "%s", Misc.getTextColor(), new String[]{contract.name}, Misc.getHighlightColor());
        intel.addLine(BaseIntelPlugin.BULLET + "Reason: %s", Misc.getTextColor(), new String[]{contract.getInvalidReason()}, Misc.getHighlightColor());
        intel.setIcon(Global.getSettings().getSpriteName("intel", "tradeFleet_valuable"));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, contract.getToMarket());
    }

    private void performShippingActions(ShippingContract contract){
        CargoAPI check = ShippingCargoManager.getTargetCargoFromOrigin(contract, false);
        check.initMothballedShips("player");

        if(!check.isEmpty() || check.getMothballedShips().getNumMembers() > 0){
            Shipment shipment = Shipment.create(contract);
            shipment.init();
        }
    }

    public static void chargePlayer(float amt, Shipment container) {
        MarketAPI m = getClosestPort(container);

        if(m != null){
            MonthlyReport report = SharedData.getData().getCurrentReport();
            MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
            MonthlyReport.FDNode mNode = report.getNode(marketsNode, m.getId());
            MonthlyReport.FDNode indNode = report.getNode(mNode, "industries");
            MonthlyReport.FDNode iNode = report.getNode(indNode, IndEvo_ids.PORT);

            iNode.upkeep += amt;
        } else Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(amt);
    }

    public static MarketAPI getClosestPort(Shipment container) {
        MarketAPI market = IndEvo_IndustryHelper.getClosestMarketWithIndustry(container.contract.getFromMarket(), IndEvo_ids.PORT);

        if (market == null) {
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                if (m.hasIndustry(IndEvo_ids.PORT) && m.isPlayerOwned()) {
                    market = m;
                    break;
                }
            }
        }

        return market;
    }
}
