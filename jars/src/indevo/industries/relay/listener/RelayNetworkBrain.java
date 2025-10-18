package indevo.industries.relay.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.relay.industry.MilitaryRelay;
import indevo.industries.relay.plugins.NetworkEntry;
import indevo.industries.relay.plugins.RelayNetwork;
import indevo.utils.timers.NewDayListener;

import java.util.*;
import java.util.stream.Collectors;

public class RelayNetworkBrain extends BaseCampaignEventListener implements NewDayListener {

    public static RelayNetworkBrain getInstanceOrRegister(){
        RelayNetworkBrain brain;
        ListenerManagerAPI manager = Global.getSector().getListenerManager();

        if (manager.hasListenerOfClass(RelayNetworkBrain.class)) brain = manager.getListeners(RelayNetworkBrain.class).get(0);
        else {
            brain = new RelayNetworkBrain();
            //Global.getSector().getListenerManager().addListener(brain, true);
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
        List<RelayNetwork> networks = new ArrayList<>();

        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            List<MarketAPI> marketsWithRelays = Misc.getFactionMarkets(faction).stream()
                    .filter(m -> m.getIndustries().stream()
                            .anyMatch(ind -> ind instanceof MilitaryRelay && ind.isFunctional()))
                    .toList();

            //group markets by system
            Map<StarSystemAPI, List<MarketAPI>> bySystem = marketsWithRelays.stream()
                    .collect(Collectors.groupingBy(MarketAPI::getStarSystem));

            //one intarray network per faction
            Set<StarSystemAPI> systemsWithIntArrays = marketsWithRelays.stream()
                    .filter(m -> m.hasIndustry(Ids.INTARRAY))
                    .map(MarketAPI::getStarSystem)
                    .collect(Collectors.toSet()); //set cause no dupes

            RelayNetwork intArrayNetwork = new RelayNetwork();
            networks.add(intArrayNetwork);

            //networks per system per faction
            for (Map.Entry<StarSystemAPI, List<MarketAPI>> entry : bySystem.entrySet()) {

                StarSystemAPI sys = entry.getKey();
                List<MarketAPI> ms = entry.getValue();

                RelayNetwork network;

                if (systemsWithIntArrays.contains(sys)) {
                    network = intArrayNetwork;
                } else {
                    network = new RelayNetwork();
                    networks.add(network);
                }

                for (MarketAPI m : ms) {
                    network.addEntry(new NetworkEntry(m));
                }
            }
        }

        networks.forEach(RelayNetwork::calculateAndApplyFleetSizeMults);
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
