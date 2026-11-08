package indevo.industries.assembler.industry;

import java.util.Map;

public interface VPCUserIndustryAPI {
    void vpcUnapply();

    boolean hasVPC();

    Map<String, Integer> getDepositList();
}
