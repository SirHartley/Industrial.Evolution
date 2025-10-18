package indevo.industries.relay.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RelayNetwork {
    private List<NetworkEntry> entries = new ArrayList<>();

    public void addEntry(NetworkEntry entry){
        entries.add(entry);
    }

    public boolean containsSystem(StarSystemAPI system){
        EconomyAPI econ = Global.getSector().getEconomy();
        return entries.stream()
                .anyMatch(entry -> {
                    MarketAPI m = econ.getMarket(entry.marketId);
                    return m != null && m.getStarSystem() != null && system.equals(m.getStarSystem());
                });
    }

    public void calculateAndApplyFleetSizeMults(){
        if (entries.isEmpty()) return;

        NetworkEntry largestEntry = findLargestFleetSizeInNetwork();
        applyFleetSizeToNetworkedSystems(largestEntry);
    }

    public void applyFleetSizeToNetworkedSystems(NetworkEntry largest){
        for (NetworkEntry e : entries) {
            e.assignTargets(largest);
            e.applyToMarket();
        }
    }

    public NetworkEntry findLargestFleetSizeInNetwork(){
        return entries.stream()
                .max(Comparator.comparingDouble(e -> e.baseFleetSize))
                .orElse(null);
    }
}
