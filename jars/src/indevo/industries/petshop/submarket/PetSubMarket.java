package indevo.industries.petshop.submarket;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.submarkets.DynamicSubmarket;

public class PetSubMarket extends BaseSubmarketPlugin implements DynamicSubmarket {

    public boolean isSetForRemoval = false;

    public void prepareForRemoval() {
        this.isSetForRemoval = true;
    }

    public static final float BASE_AMOUNT_PER_ANIMAL = 3f;

    public void updateCargoPrePlayerInteraction() {
        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true); //This clears commodities for us, uses shouldHaveCommodity/getStockpileLimit
        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            sinceSWUpdate = 0f;

            String aiCoreId = getLocalAICoreId();
            float rarityBonus = Commodities.ALPHA_CORE.equals(aiCoreId) ? 1 + PetShop.ALPHA_CORE_RARITY_BONUS : 1f;

            getCargo().clear();

            WeightedRandomPicker<PetData> picker = new WeightedRandomPicker<>(itemGenRandom);
            for (PetData petData : PetDataRepo.getAll()){
                if (petData.isNoSell()) continue;

                float chance = petData.rarity < 0.5f ? petData.rarity * rarityBonus : petData.rarity;
                picker.add(petData, chance);
            }

            for (int i= 0; i < market.getSize(); i++){
                PetData data = picker.pickAndRemove();

                int amt = (int) Math.floor(data.rarity * BASE_AMOUNT_PER_ANIMAL);
                cargo.addSpecial(new SpecialItemData(ItemIds.PET_CHAMBER, data.id),  amt);
            }
        }

        getCargo().sort();
    }

    @Override
    public float getTariff() {
        return getLocalAICoreId().equals(Commodities.BETA_CORE) ? 0f : 0.2f;
    }

    public boolean isParticipatesInEconomy() {
        return false;
    }

    private String getLocalAICoreId() {
        String id = "none";

        if (market.hasIndustry(Ids.REQCENTER) && market.getIndustry(Ids.REQCENTER).getAICoreId() != null) {
            id = market.getIndustry(Ids.REQCENTER).getAICoreId();
        }

        return id;
    }

    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        this.submarket.setFaction(Global.getSector().getFaction("petStoreColour"));
    }

    public boolean shouldHaveCommodity(CommodityOnMarketAPI com) {
        return com.getId().equals(ItemIds.PET_FOOD);
    }

    public boolean showInFleetScreen() {
        return false;
    }

    public boolean showInCargoScreen() {
        return !isSetForRemoval;
    }

    @Override
    public boolean isOpenMarket() {
        return true;
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        return action == TransferAction.PLAYER_SELL;
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return "You can not sell anything here.";
    }

    @Override
    public boolean isTooltipExpandable() {
        return super.isTooltipExpandable();
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        tooltip.addPara("Purchase all your pets here! No returns accepted.", 10f);
    }
}
