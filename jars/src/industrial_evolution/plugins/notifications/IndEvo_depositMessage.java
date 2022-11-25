package industrial_evolution.plugins.notifications;

import com.fs.starfarer.api.Global;
import industrial_evolution.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import industrial_evolution.industries.IndEvo_VPCUserIndustryAPI;
import industrial_evolution.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IndEvo_depositMessage implements EconomyTickListener {

    public void reportEconomyTick(int iterIndex) {
    }

    public void reportEconomyMonthEnd() {
        if (!getTotalAmountList().isEmpty()) {
            makeMessage();
        }
    }

    private ArrayList<Industry> getVarIndustries() {
        ArrayList<Industry> list = new ArrayList<>();

        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (!market.isPlayerOwned()) continue;

            for (Industry ind : market.getIndustries()) {
                if (ind instanceof IndEvo_VPCUserIndustryAPI
                        && ind.isFunctional()
                        && ((IndEvo_VPCUserIndustryAPI) ind).hasVPC()) list.add(ind);
            }
        }

        return list;
    }

    private HashMap<String, Integer> getTotalAmountList() {
        HashMap<String, Integer> totalsMap = new HashMap<>();

        for (Industry ind : getVarIndustries()) {
            IndEvo_VPCUserIndustryAPI vpcInd = (IndEvo_VPCUserIndustryAPI) ind;

            Map<String, Integer> depList = vpcInd.getDepositList();

            for (Map.Entry<String, Integer> entry : depList.entrySet()) {
                if (totalsMap.containsKey(entry.getKey())) {
                    totalsMap.put(entry.getKey(), totalsMap.get(entry.getKey()) + entry.getValue());
                } else {
                    totalsMap.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return totalsMap;
    }

    private void makeMessage() {
        boolean varIndGath = Global.getSettings().getBoolean("VarInd_deliverToProductionPoint");
        String targetLocation = varIndGath ? Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getName() : IndEvo_StringHelper.getString("theLocalStorage");

        MessageIntel intel = new MessageIntel(
                IndEvo_StringHelper.getStringAndSubstituteToken("IndEvo_VarInd", "deliveryNotice", "$targetLocation", targetLocation),
                Misc.getTextColor(),
                new String[]{(targetLocation)},
                Global.getSector().getPlayerFaction().getBrightUIColor());

        for (Map.Entry<String, Integer> entry : getTotalAmountList().entrySet()) {
            String name = IndEvo_Items.getCommodityNameString(entry.getKey());
            int amount = entry.getValue();

            intel.addLine(BaseIntelPlugin.BULLET + name + ": %s",
                    Misc.getTextColor(),
                    new String[]{(amount + IndEvo_StringHelper.getString("unitsWithFrontSpace"))},
                    Misc.getHighlightColor());
        }

        intel.setIcon(Global.getSettings().getSpriteName("intel", "production_report"));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel);
    }
}
