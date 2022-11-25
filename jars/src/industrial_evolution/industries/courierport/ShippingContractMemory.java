package industrial_evolution.industries.courierport;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import industrial_evolution.plugins.IndEvo_modPlugin;

import java.util.ArrayList;
import java.util.List;

public class ShippingContractMemory {
    private static final String SHIPPING_CONTRACT_LIST_MEMORY = "$IndEvo_ShippingContainerStorage";

    public static List<ShippingContract> getContractList(){
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(SHIPPING_CONTRACT_LIST_MEMORY)) return (List<ShippingContract>) mem.get(SHIPPING_CONTRACT_LIST_MEMORY);
        else {
            List<ShippingContract> containerList = new ArrayList<>();
            mem.set(SHIPPING_CONTRACT_LIST_MEMORY, containerList);

            return containerList;
        }
    }

    public static void addOrReplaceContract(ShippingContract contract){
        IndEvo_modPlugin.log("adding contract " + contract.name);

        if(contract.isValid()) {
            List<ShippingContract> contractList = getContractList();

            ShippingContract toRemove = null;
            for (ShippingContract c : contractList){
                if (c.getId().equals(contract.getId())) {
                    toRemove = c;
                    IndEvo_modPlugin.log("replacing old one");
                    break;
                }
            }

            if(toRemove != null) contractList.remove(toRemove);
            contractList.add(contract);

            IndEvo_modPlugin.log("success");
        } else IndEvo_modPlugin.log("failed");

        IndEvo_modPlugin.log(getContractList().size() + " contracts on the list");
    }

    public static ShippingContract getContract(String id){
        List<ShippingContract> contractList = getContractList();

        for (ShippingContract contract : contractList){
            if (contract.getId().equals(id)) return contract;
        }

        return null;
    }

    public static void removeContract(ShippingContract contract){
        getContractList().remove(contract);
    }
}
