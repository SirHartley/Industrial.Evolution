package indevo.utils.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.MutableValue;

public class BillingHelper {

    public static final String SERVICES_NODE = "IndEvo_node_services";

    public static void charge(Industry ind, String line, String label, float amount) {
        if (amount <= 0) return;
        getLineNode(ind, line, label).upkeep += amount;
    }

    public static void pay(Industry ind, String line, String label, float amount) {
        if (amount <= 0) return;
        getLineNode(ind, line, label).income += amount;
    }

    public static boolean canAfford(float amount) {
        return Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= amount;
    }

    public static void chargeNow(float amount) {
        MutableValue credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
        if (credits.get() < amount) throw new IllegalStateException("charged " + amount + " credits with only " + credits.get() + " available, caller skipped canAfford");
        credits.subtract(amount);
    }

    private static FDNode getLineNode(Industry ind, String line, String label) {
        MarketAPI market = ind.getMarket();
        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode parent = market.isPlayerOwned() ? report.getMarketNode(market) : getServicesMarketNode(report, market);

        // own node per industry and line, never shares a vanilla node, so writes add up any time in the month
        FDNode node = report.getNode(parent, ind.getId() + "_" + line);
        node.name = label;
        node.custom = ind;
        node.mapEntity = market.getPrimaryEntity();
        return node;
    }

    private static FDNode getServicesMarketNode(MonthlyReport report, MarketAPI market) {
        FDNode services = report.getNode(SERVICES_NODE);
        if (services.name == null) {
            services.name = "Services";
            services.custom = SERVICES_NODE;
            services.icon = Global.getSettings().getSpriteName("income_report", "generic_expense");
        }

        FDNode mNode = report.getNode(services, market.getId());
        mNode.name = market.getName();
        mNode.custom = market;
        return mNode;
    }
}
