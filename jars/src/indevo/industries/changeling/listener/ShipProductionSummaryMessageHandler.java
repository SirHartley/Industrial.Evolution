package indevo.industries.changeling.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.script.DelayedCampaignNotificationScript;

import java.util.HashMap;
import java.util.Map;

public class ShipProductionSummaryMessageHandler implements EconomyTickListener {

    private Map<MarketAPI, FleetMemberAPI> productionMap = new HashMap<>();

    public static ShipProductionSummaryMessageHandler getInstanceOrRegister() {
        for (ShipProductionSummaryMessageHandler manager : Global.getSector().getListenerManager().getListeners(ShipProductionSummaryMessageHandler.class)) {
            return manager;
        }

        ShipProductionSummaryMessageHandler manager = new ShipProductionSummaryMessageHandler();
        Global.getSector().getListenerManager().addListener(manager);
        return manager;
    }

    public void add(MarketAPI market, FleetMemberAPI member) {
        productionMap.put(market, member);
    }

    @Override
    public void reportEconomyMonthEnd() {
        // Make a copy of the map so we can clear the original while still displaying the info in the message.
        // The original is cleared by the time the message is displayed, and we don't want to wait to clear it
        // until the message is shown because new production could be added in the meantime.
        final Map<MarketAPI, FleetMemberAPI> productionMapShallowCopy = new HashMap<>(productionMap);

        DelayedCampaignNotificationScript message = new DelayedCampaignNotificationScript(2) {
            @Override
            public void showMessage() {
                // Wisp: if there's nothing to report, don't report it
                if (productionMapShallowCopy.isEmpty()) return;

                MessageIntel intel = new MessageIntel("Your Monastic Orders provided tithes.",
                        Misc.getTextColor());

                for (Map.Entry<MarketAPI, FleetMemberAPI> e : productionMapShallowCopy.entrySet()) {
                    String hull = e.getValue().getHullSpec().getHullName();
                    String market = e.getKey().getName();

                    intel.addLine(BaseIntelPlugin.BULLET + market + ": %s", Misc.getTextColor(), new String[]{hull}, Misc.getHighlightColor());
                }

                intel.addLine("They were delivered to their respective %s.", Misc.getTextColor(), new String[]{"local storage"}, Misc.getHighlightColor());
                intel.setIcon(Global.getSettings().getSpriteName("intel", "repairs_finished"));
                intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.NOTHING, null);
            }
        };

        message.register();
        productionMap.clear();
    }

    @Override
    public void reportEconomyTick(int iterIndex) {

    }
}
