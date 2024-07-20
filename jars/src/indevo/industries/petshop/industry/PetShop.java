package indevo.industries.petshop.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.memory.Pet;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.scripts.SubMarketAddOrRemovePlugin;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class PetShop extends BaseIndustry implements EconomyTickListener {

    private List<Pet> storedPets = new LinkedList<>();
    private SectorEntityToken petShopStationEntity = null;

    private HashMap<String, Integer> petMarketInventory = new HashMap<>();

    public static final float PET_STORAGE_AMOUNT = 500f;
    public static final float FLAT_MANAGEMENT_FEE = 2000f;
    public static final float GAMMA_CORE_STORAGE_COST_RED_PERCENT = 0.70f;
    public static final float ALPHA_CORE_RARITY_BONUS = 0.3f;

    public float petStorageCostMult = 1f;

    public void store(Pet pet) {
        storedPets.add(pet);
    }

    public void removeFromStorage(Pet pet){
        storedPets.remove(pet);
    }

    public List<Pet> getStoredPetsPetsCopy() {
        return new ArrayList<>(storedPets);
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostDescriptionSection(tooltip, mode);

        String ageNotif = !Commodities.ALPHA_CORE.equals(aiCoreId) ? "Pets will age while in storage." : "";
        tooltip.addPara("The " + getCurrentName() + " allows %s of pets. " + ageNotif, 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), "purchase, storage and management");

        if (!isBuilding() && isFunctional() && mode.equals(IndustryTooltipMode.NORMAL)) {
            float opad = 5.0F;

            tooltip.addSectionHeading("Stored Pets", market.getTextColorForFactionOrPlanet(), market.getDarkColorForFactionOrPlanet(), Alignment.MID, 10f);
            tooltip.beginTable(market.getFaction(), 20f, "Name", 190f, "Age", 90f, "Species", 115f); //390 total

            int i = 0;
            float cost = 0f;
            for (Pet pet : storedPets){
                if (i > 10) break;
                String name = pet.name.length() > 20 ? pet.name.substring(0, 20) + "..." : pet.name;
                String species = pet.getData().species.length() > 15 ? pet.getData().species.substring(0, 15) + "..." :  pet.getData().species;
                cost += getStorageCost(pet);

                tooltip.addRow(name, pet.getAgeString(), species);
                i++;
            }

            tooltip.addTable("You do not have any pets stored here.", storedPets.size() - 10, opad);
            tooltip.addPara("Total storage costs per Month: %s", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), com.fs.starfarer.api.util.Misc.getDGSCredits(cost));
        }
    }

    public float getStorageCost(Pet pet){
        float cost = pet.getData().foodPerMonth * PET_STORAGE_AMOUNT * petStorageCostMult;
        if (!market.isPlayerOwned()) cost += FLAT_MANAGEMENT_FEE;

        return cost;
    }

    public void reportEconomyTick(int iterIndex) {
        //can't do on month end because monthly report

        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != lastIterInMonth) return;

        if (isFunctional() && !market.isPlayerOwned()) {

            MonthlyReport.FDNode iNode = MiscIE.createMonthlyReportNode(this, market, "Pet Storage", Ids.ACADEMY, Ids.REPAIRDOCKS, Ids.PET_STORE);

            if (storedPets.size() > 0) {
                for (Pet pet : storedPets) {
                    if (!pet.isDead()) iNode.upkeep += pet.getData().foodPerMonth * PET_STORAGE_AMOUNT;
                    if (!pet.isDead() && !market.isPlayerOwned()) iNode.upkeep += FLAT_MANAGEMENT_FEE;
                }
            }
        }
    }

    @Override
    public void reportEconomyMonthEnd() {

    }

    @Deprecated
    public void ensurePetShopCreatedOrAssigned() {
        if (petShopStationEntity == null) {
            for (SectorEntityToken entity : market.getConnectedEntities()) {
                if (entity.hasTag(Ids.PET_SHOP_ENTITY)) {
                    petShopStationEntity = entity;
                    break;
                }
            }
        }

        if (petShopStationEntity == null) {
            petShopStationEntity = market.getContainingLocation().addCustomEntity(
                    null, market.getName() + " " + getCurrentName(), Ids.PET_SHOP_ENTITY, market.getFactionId());
            SectorEntityToken primary = market.getPrimaryEntity();
            float orbitRadius = primary.getRadius() + 70f;
            petShopStationEntity.setCircularOrbitWithSpin(primary, (float) Math.random() * 360f, orbitRadius, orbitRadius / 25f, 5f, 10f);
            market.getConnectedEntities().add(petShopStationEntity);
            petShopStationEntity.setMarket(market);
        }
    }


    @Deprecated
    protected void removeStationEntityAndFleetIfNeeded() {
        if (petShopStationEntity != null) {

            if (petShopStationEntity.getContainingLocation() != null) {
                petShopStationEntity.getContainingLocation().removeEntity(petShopStationEntity);
                market.getConnectedEntities().remove(petShopStationEntity);
            }

            petShopStationEntity = null;
        }
    }

    @Override
    protected void buildingFinished() {
        super.buildingFinished();

        //if (petShopStationEntity == null) ensurePetShopCreatedOrAssigned();

    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && Settings.getBoolean(Settings.PETS);
    }

    @Override
    public boolean showWhenUnavailable() {
        return super.showWhenUnavailable() && Settings.getBoolean(Settings.PETS);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        //if (petShopStationEntity == null && isFunctional() && !isBuilding()) ensurePetShopCreatedOrAssigned();
        if(isFunctional() && !market.hasSubmarket(Ids.PETMARKET)) Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.PETMARKET, false));
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);

        if (!forUpgrade) {
            //removeStationEntityAndFleetIfNeeded();
        }
    }

    @Override
    public void notifyColonyRenamed() {
        super.notifyColonyRenamed();
        //petShopStationEntity.setName(market.getName() + " " + getCurrentName());
    }

    @Override
    public void apply() {
        super.apply(true);

        supply(ItemIds.PET_FOOD, market.getSize() - 2);
        demand(Commodities.FOOD, market.getSize() - 2);

        Pair<String, Integer> deficit = getMaxDeficit(Commodities.FOOD);
        applyDeficitToProduction(1, deficit, ItemIds.PET_FOOD);

        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListener(this)) manager.addListener(this);

        if (isFunctional()) {
            //if (!market.hasSubmarket(Ids.PETMARKET)) market.addSubmarket(Ids.PETMARKET);
            if (market.isPlayerOwned()){
                SubmarketPlugin sub = com.fs.starfarer.api.util.Misc.getLocalResources(market);
                if (sub instanceof LocalResourcesSubmarketPlugin) {
                    LocalResourcesSubmarketPlugin lr = (LocalResourcesSubmarketPlugin) sub;
                    float mult = Global.getSettings().getFloat("stockpileMultExcess");

                    for (MutableCommodityQuantity q : supply.values()) {
                        if (q.getQuantity().getModifiedInt() > 0) {
                            lr.getStockpilingBonus(q.getCommodityId()).modifyFlat(getModId(0), market.getSize() * mult * 0.5f);
                        }
                    }
                }
            }
        }

        if (!isFunctional()) supply.clear();
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.PETMARKET, true));
        Global.getSector().getListenerManager().removeListener(this);
    }

    //AI-Core tooltips
    //gamma makes storage cost lower by 70%
    //alpha makes chance for rare pets higher by 50%
    //beta removes tariff and stops pets from ageing in storage

    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        Color bad = com.fs.starfarer.api.util.Misc.getNegativeHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Increases the %s to be on sale by %s.", 0f, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), new String[]{"chance for rare pets", StringHelper.getAbsPercentString(ALPHA_CORE_RARITY_BONUS, true)});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Increases the %s to be on sale by %s.", opad, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), new String[]{"chance for rare pets", StringHelper.getAbsPercentString(ALPHA_CORE_RARITY_BONUS, true)});
        }
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        Color bad = com.fs.starfarer.api.util.Misc.getNegativeHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "%s from the animal market and stops pets from %s", 0f, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), new String[]{"Removes tariffs", "ageing in storage"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "%s from the animal market and stops pets from %s", opad, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), new String[]{"Removes tariffs", "ageing in storage"});
        }
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        Color bad = com.fs.starfarer.api.util.Misc.getNegativeHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Reduces %s by %s", 0f, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), new String[]{"storage costs", StringHelper.getAbsPercentString(GAMMA_CORE_STORAGE_COST_RED_PERCENT, false)});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Reduces %s by %s", opad, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), new String[]{"storage costs", StringHelper.getAbsPercentString(GAMMA_CORE_STORAGE_COST_RED_PERCENT, false)});
        }
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    @Override
    protected void applyGammaCoreModifiers() {
        super.applyGammaCoreModifiers();
        petStorageCostMult = 1 - GAMMA_CORE_STORAGE_COST_RED_PERCENT;
    }

    @Override
    protected void applyNoAICoreModifiers() {
        super.applyNoAICoreModifiers();
        petStorageCostMult = 1f;
    }
}
