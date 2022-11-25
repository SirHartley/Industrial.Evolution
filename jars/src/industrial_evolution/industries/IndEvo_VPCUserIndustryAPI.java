package industrial_evolution.industries;

import java.util.Map;

public interface IndEvo_VPCUserIndustryAPI {
    void setSupply(Map<String, Integer> commodityIDWithMarketSizeModifier);

    void setDemand(Map<String, Integer> commodityIDWithMarketSizeModifier);

    void vpcUnapply();

    boolean hasVPC();

    Map<String, Integer> getDepositList();
}
