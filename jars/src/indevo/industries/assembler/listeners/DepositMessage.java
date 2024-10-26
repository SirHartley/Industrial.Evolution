package indevo.industries.assembler.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.assembler.industry.VPCUserIndustryAPI;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DepositMessage implements EconomyTickListener {

    public static void register(){
        Global.getSector().getListenerManager().addListener(new DepositMessage(), true);
    }

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
                if (ind instanceof VPCUserIndustryAPI
                        && ind.isFunctional()
                        && ((VPCUserIndustryAPI) ind).hasVPC()) list.add(ind);
            }
        }

        return list;
    }

    private HashMap<String, Integer> getTotalAmountList() {
        HashMap<String, Integer> totalsMap = new HashMap<>();

        for (Industry ind : getVarIndustries()) {
            VPCUserIndustryAPI vpcInd = (VPCUserIndustryAPI) ind;

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
        boolean varIndGath = Settings.getBoolean(Settings.VARIND_DELIVER_TO_PRODUCTION_POINT);
        String targetLocation = varIndGath ? Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getName() : StringHelper.getString("theLocalStorage");

        MessageIntel intel = new MessageIntel(
                StringHelper.getStringAndSubstituteToken("IndEvo_VarInd", "deliveryNotice", "$targetLocation", targetLocation),
                Misc.getTextColor(),
                new String[]{(targetLocation)},
                Global.getSector().getPlayerFaction().getBrightUIColor());

        for (Map.Entry<String, Integer> entry : getTotalAmountList().entrySet()) {
            String name = ItemIds.getCommodityNameString(entry.getKey());
            int amount = entry.getValue();

            intel.addLine(BaseIntelPlugin.BULLET + name + ": %s",
                    Misc.getTextColor(),
                    new String[]{(amount + StringHelper.getString("unitsWithFrontSpace"))},
                    Misc.getHighlightColor());
        }

        intel.setIcon(Global.getSettings().getSpriteName("intel", "production_report"));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel);
    }
}
