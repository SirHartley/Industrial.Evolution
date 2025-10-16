package indevo.industries.relay.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import indevo.ids.Ids;
import indevo.industries.relay.industry.MilitaryRelay;
import indevo.industries.relay.plugins.NetworkEntry;
import indevo.industries.relay.plugins.RelayNetwork;
import indevo.utils.timers.NewDayListener;

import java.util.ArrayList;
import java.util.List;

public class RelayNetworkBrain extends BaseCampaignEventListener implements NewDayListener {

    public static RelayNetworkBrain getInstanceOrRegister(){
        RelayNetworkBrain brain;
        ListenerManagerAPI manager = Global.getSector().getListenerManager();

        if (manager.hasListenerOfClass(RelayNetworkBrain.class)) brain = manager.getListeners(RelayNetworkBrain.class).get(0);
        else {
            brain = new RelayNetworkBrain();
            Global.getSector().getListenerManager().addListener(brain, true);
            Global.getSector().addTransientListener(brain);
        }

        return brain;
    }

    public RelayNetworkBrain() {
        super(false);
    }

    public static void forceUpdate(){
        getInstanceOrRegister().updateNetworks();
    }

    private void updateNetworks(){
        List<StarSystemAPI> systemsWithIntArrays = new ArrayList<>(Global.getSector().getEconomy().getMarketsCopy().stream()
                .filter(m -> m.hasFunctionalIndustry(Ids.INTARRAY))
                .map(MarketAPI::getStarSystem)
                .distinct()
                .toList()
        );

        List<MarketAPI> marketsWithRelays = new ArrayList<>(Global.getSector().getEconomy().getMarketsCopy().stream()
                .filter(m -> m.getIndustries().stream()
                        .anyMatch(ind -> ind.isFunctional() && ind instanceof MilitaryRelay))
                .toList());

        List<RelayNetwork> networks = new ArrayList<>();

        //these are seperate - relay network can be empty
        RelayNetwork intArrayNetwork = new RelayNetwork();
        networks.add(intArrayNetwork);

        OUTER: for (MarketAPI m : marketsWithRelays){
            StarSystemAPI system = m.getStarSystem();
            if (system == null) continue;

            if (systemsWithIntArrays.contains(system)) {
                intArrayNetwork.addEntry(new NetworkEntry(m));

            } else {
                for (RelayNetwork network : networks) if (network.containsSystem(system)) {
                    network.addEntry(new NetworkEntry(m));
                    continue OUTER;
                }

                //there is no existing network in the list
                RelayNetwork newNetwork = new RelayNetwork();
                newNetwork.addEntry(new NetworkEntry(m));
                networks.add(newNetwork);
            }
        }

        for (RelayNetwork network : networks) network.calculateAndApplyFleetSizeMults();
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        super.reportPlayerOpenedMarket(market);
        updateNetworks();
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        super.reportPlayerClosedMarket(market);
        updateNetworks();
    }

    @Override
    public void onNewDay() {
        updateNetworks();
    }
}
