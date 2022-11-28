package indevo.industries.assembler.industry;

import java.util.Map;

public interface VPCUserIndustryAPI {
    void setSupply(Map<String, Integer> commodityIDWithMarketSizeModifier);

    void setDemand(Map<String, Integer> commodityIDWithMarketSizeModifier);

    void vpcUnapply();

    boolean hasVPC();

    Map<String, Integer> getDepositList();
}
