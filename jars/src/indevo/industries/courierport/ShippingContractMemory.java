package indevo.industries.courierport;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import indevo.utils.ModPlugin;

import java.util.ArrayList;
import java.util.List;

public class ShippingContractMemory {
    private static final String SHIPPING_CONTRACT_LIST_MEMORY = "$IndEvo_ShippingContainerStorage";

    public static List<ShippingContract> getContractList() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(SHIPPING_CONTRACT_LIST_MEMORY))
            return (List<ShippingContract>) mem.get(SHIPPING_CONTRACT_LIST_MEMORY);
        else {
            List<ShippingContract> containerList = new ArrayList<>();
            mem.set(SHIPPING_CONTRACT_LIST_MEMORY, containerList);

            return containerList;
        }
    }

    public static void addOrReplaceContract(ShippingContract contract) {
        if (!contract.isValid()) {
            ModPlugin.log("refused invalid contract " + contract.name);
            return;
        }

        List<ShippingContract> contractList = getContractList();
        for (int i = 0; i < contractList.size(); i++) {
            if (contractList.get(i).getId().equals(contract.getId())) {
                contractList.set(i, contract);
                return;
            }
        }

        contractList.add(contract);
    }

    public static ShippingContract getContract(String id) {
        List<ShippingContract> contractList = getContractList();

        for (ShippingContract contract : contractList) {
            if (contract.getId().equals(id)) return contract;
        }

        return null;
    }

    public static void removeContract(ShippingContract contract) {
        getContractList().remove(contract);
    }
}
