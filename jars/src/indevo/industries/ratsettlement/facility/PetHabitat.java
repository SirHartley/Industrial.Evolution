package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.item.PetItemPlugin;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;

import java.util.ArrayList;

// pet storage and one habitat through an embedded animal center at player rules; pet code finds it through RatSettlements.getHabitat
public class PetHabitat extends SettlementFacility {

    private static final String OPEN = "open";

    private HabitatPetShop shop = null;

    @Override
    protected Setting parentSetting() {
        return Setting.PETS;
    }

    @Override
    public void apply() {
        shop = new HabitatPetShop(getSettlement().getPrimaryPlanet());
        shop.init(Ids.PET_STORE, getSettlementMarket());
    }

    // running broods end early, parents, young and stored pets go to settlement storage in cryochambers
    @Override
    public void unapply() {
        if (shop == null) return;

        PetStatusManager manager = PetStatusManager.getInstance();
        for (PetBrood brood : new ArrayList<>(shop.getBroods())) {
            if (!brood.isReady()) {
                shop.cancelBrood(brood);
                continue;
            }

            for (Pet young : brood.getYoung()) {
                manager.register(young);
                toChamber(young);
            }
        }
        shop.getBroods().clear();

        for (Pet pet : shop.getStoredPetsPetsCopy()) if (!pet.isDead()) toChamber(pet);
        shop = null;
    }

    private void toChamber(Pet pet) {
        pet.toChamber();
        getStorageCargo().addSpecial(new SpecialItemData(ItemIds.PET_CHAMBER, PetItemPlugin.chamberData(pet)), 1f);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (isFunctional() && shop != null) shop.advanceBroods();
    }

    // storage is billed on the last tick, the monthly report is already made at month end
    @Override
    public void reportEconomyTick(int iterIndex) {
        int last = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != last || shop == null) return;

        charge("storage", "Pet storage", shop.getTotalStorageCost());
    }

    @Override
    protected boolean hasOptions() {
        return true;
    }

    @Override
    protected void addOptions(InteractionDialogAPI dialog) {
        addOption(dialog, "Visit the Pet Habitat", OPEN);
    }

    @Override
    protected void onOption(InteractionDialogAPI dialog, String action) {
        if (shop == null) throw new IllegalStateException("Pet Habitat used before construction finished");
        openPanel(dialog, onReturn -> new PetsDialogPlugin(shop, new PetsView(), onReturn));
    }

    public HabitatPetShop getShop() {
        return shop;
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Pet storage and %s habitat for raising young, at the Care of your own Animal Center. No sales or adoption.",
                0f, Misc.getHighlightColor(), String.valueOf(HabitatPetShop.HABITATS));
        tooltip.addPara("An irradiated planet raises the mutation rate %s.", 10f, Misc.getHighlightColor(),
                "x" + Math.round(PetConstants.IRRADIATED_MUTATION_MULT));

        if (shop == null) return;
        int stored = 0;
        for (Pet pet : shop.getStoredPetsPetsCopy()) if (!pet.isDead()) stored++;
        tooltip.addPara("Pets in storage: %s, storage costs per month: %s", 10f, Misc.getHighlightColor(),
                String.valueOf(stored), Misc.getDGSCredits(shop.getTotalStorageCost()));
    }
}
