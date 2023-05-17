package indevo.industries.petshop.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.petshop.memory.Pet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PetShop extends BaseIndustry {

    private List<Pet> storedPets = new LinkedList<>();

    public void store(Pet pet){
        pet.unassign();
        storedPets.add(pet);
    }

    public void movePetFromStorage(Pet pet, FleetMemberAPI toMember){
        if (!storedPets.contains(pet)) return;

        storedPets.remove(pet);
        pet.assign(toMember);
    }

    public List<Pet> getStoredPetsPetsCopy(){
        return new ArrayList<>(storedPets);
    }

    @Override
    public void apply() {
        super.apply(true);

        supply(ItemIds.PET_FOOD, market.getSize());

        if (market.isPlayerOwned() && isFunctional()) {
            SubmarketPlugin sub = Misc.getLocalResources(market);
            if (sub instanceof LocalResourcesSubmarketPlugin) {
                LocalResourcesSubmarketPlugin lr = (LocalResourcesSubmarketPlugin) sub;
                float mult = Global.getSettings().getFloat("stockpileMultExcess");

                for (MutableCommodityQuantity q : supply.values()){
                    if(q.getQuantity().getModifiedInt() > 0){
                        lr.getStockpilingBonus(q.getCommodityId()).modifyFlat(getModId(0), market.getSize() * mult);
                    }
                }
            }
        }

        if (!isFunctional()) supply.clear();
    }
}
