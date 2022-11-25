package industrial_evolution.campaign.rulecmd.salvageYardsRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.ListMap;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IndEvo_HasValidShipPair extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI m = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        Map<String, List<String>> baseHullIdList = new ListMap<>();

        if (m != null) {
            for (FleetMemberAPI member : Misc.getStorageCargo(m).getMothballedShips().getMembersListCopy()) {
                if (addAndCompare(baseHullIdList, member)) return true;
            }
        }

        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (addAndCompare(baseHullIdList, member)) return true;
        }

        return false;
    }

    private boolean addAndCompare(Map<String, List<String>> bl, FleetMemberAPI member) {
        String id = member.getHullSpec().getBaseHullId();

        if (bl.containsKey(id)) {
            //compare the two lists.
            boolean allowed = compareLists(getDModList(member), ((ListMap<String>) bl).getList(id));
            if (allowed) return true;

        } else {
            //add it
            bl.put(id, getDModList(member));
        }

        return false;
    }

    private boolean compareLists(List<String> a, List<String> b) {
        for (String s : a) {
            if (!b.contains(s)) return true;
        }

        for (String s : b) {
            if (!a.contains(s)) return true;
        }

        return false;
    }

    private List<String> getDModList(FleetMemberAPI member) {
        List<String> dmodList = new ArrayList<>();
        for (String s : member.getVariant().getHullMods()) {
            if (Global.getSettings().getHullModSpec(s).getTags().contains(Tags.HULLMOD_DMOD)) dmodList.add(s);
        }

        return dmodList;
    }
}