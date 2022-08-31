package com.fs.starfarer.api.impl.campaign.econ.impl;

import java.util.Map;

public interface IndEvo_VPCUserIndustryAPI {
    void setSupply(Map<String, Integer> commodityIDWithMarketSizeModifier);

    void setDemand(Map<String, Integer> commodityIDWithMarketSizeModifier);

    void vpcUnapply();

    boolean hasVPC();

    Map<String, Integer> getDepositList();
}
