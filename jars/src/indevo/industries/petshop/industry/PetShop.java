package indevo.industries.petshop.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.industries.petshop.plugins.PetsPanel;
import indevo.submarkets.IndustrySubmarkets;
import indevo.utils.helper.BillingHelper;
import indevo.utils.helper.RelicHelper;
import indevo.utils.helper.StringHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class PetShop extends BaseIndustry implements EconomyTickListener {

    private List<Pet> storedPets = new LinkedList<>();

    private HashMap<String, Integer> petMarketInventory = new HashMap<>();

    // monthly share of the species value, the cryochamber price, never the pet's own value
    public static final float STORAGE_FEE_FRACT = 0.1f;
    public static final float MIN_STORAGE_FEE = 100f;
    public static final float NPC_STORAGE_FEE_MULT = 1.5f;
    public static final float GAMMA_CORE_STORAGE_COST_RED_PERCENT = 0.70f;
    public static final float ALPHA_CORE_RARITY_BONUS = 0.3f;
    public static final String STORAGE_UPKEEP_ID = "IndEvo_petStorage";

    public float petStorageCostMult = 1f;
    private List<PetBrood> broods = new ArrayList<>();
    // this month's adoption offers at an NPC center, not registered until adopted; null until the next look
    private ArrayList<Pet> offers = null;

    public void store(Pet pet) {
        storedPets.add(pet);
    }

    public void removeFromStorage(Pet pet){
        storedPets.remove(pet);
    }

    public List<Pet> getStoredPetsPetsCopy() {
        return new ArrayList<>(storedPets);
    }

    // a save from before habitats has none
    public List<PetBrood> getBroods() {
        if (broods == null) broods = new ArrayList<>();
        return broods;
    }

    public int getHabitatCount() {
        return market.isPlayerOwned() ? PetConstants.PLAYER_HABITATS : PetConstants.NPC_HABITATS;
    }

    public PetBrood getBrood(int habitat) {
        for (PetBrood brood : getBroods()) if (brood.habitat == habitat) return brood;
        return null;
    }

    public PetBrood getBrood(Pet parent) {
        for (PetBrood brood : getBroods()) if (!brood.isReady() && brood.has(parent)) return brood;
        return null;
    }

    // the parents of running broods, living ones only
    public List<Pet> getHabitatPets() {
        List<Pet> list = new ArrayList<>();
        PetStatusManager manager = PetStatusManager.getInstance();
        for (PetBrood brood : getBroods()) {
            if (brood.isReady()) continue;
            for (String id : new String[]{brood.parentA, brood.parentB}) {
                Pet pet = manager.get(id);
                if (pet != null && !pet.isDead()) list.add(pet);
            }
        }
        return list;
    }

    public float getCare() {
        float care = market.isPlayerOwned() ? PetConstants.PLAYER_CARE : PetConstants.NPC_CARE;
        if (Commodities.GAMMA_CORE.equals(aiCoreId)) care += PetConstants.GAMMA_CARE;
        if (isImproved()) care += PetConstants.IMPROVED_CARE;
        return care;
    }

    public float getMutationRate(Pet a, Pet b) {
        return PetGenetics.mutationRate(a.getData(), a.sheet, b.sheet, market.hasCondition(Conditions.IRRADIATED), Commodities.ALPHA_CORE.equals(aiCoreId), PetDataRepo.getTraits());
    }

    public static float getInbreeding(Pet a, Pet b) {
        return getInbreeding(a.id, b.id);
    }

    // pets or planned young
    public static float getInbreeding(String a, String b) {
        return PetGenetics.inbreeding(a, b, PetStatusManager.getInstance()::parentsOf);
    }

    // the center a plan counts with: the one docked at, else the player's own with the most Care; null for none
    public static PetShop planCenter(PetShop docked) {
        if (docked != null) return docked;

        PetShop best = null;
        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            if (market.getIndustry(Ids.PET_STORE) instanceof PetShop shop && shop.isFunctional() && (best == null || shop.getCare() > best.getCare())) best = shop;
        }
        return best;
    }

    // a month per busy habitat, only at npc centers
    public float getFee() {
        return market.isPlayerOwned() ? 0f : PetConstants.HABITAT_FEE;
    }

    // why a pet cannot be a parent here, null when it can
    public String parentBlock(Pet pet) {
        if (pet.isDead()) return "Gone.";
        if (pet.isYoung()) return "Still Young.";
        if (pet.getData().isNoSell()) return "This one does not brood.";
        if (pet.sheet.getGrade(PetGenetics.STERILE) > 0 && pet.sheet.isKnown(PetGenetics.STERILE)) return "Sterile.";
        if (pet.isUnsettled()) return "Unsettled.";
        if (PetsPanel.awayFleet(pet) != null) return "Out with " + PetsPanel.awayFleet(pet).getName() + ".";
        for (PetBrood brood : getBroods()) if (!brood.isReady() && brood.has(pet)) return "In a habitat here.";
        if (pet.storage != null && pet.storage != this) return "In storage at " + pet.storage.getMarket().getName() + ".";
        return null;
    }

    public String pairBlock(Pet a, Pet b) {
        if (a == b) return "Pick two adults.";
        if (!a.typeID.equals(b.typeID)) return "Two adults of one species.";
        return null;
    }

    public void startBrood(Pet a, Pet b, int habitat) {
        if (getBrood(habitat) != null || parentBlock(a) != null || parentBlock(b) != null || pairBlock(a, b) != null) {
            throw new IllegalStateException("IndEvo brood of " + a.id + " and " + b.id + " in a busy habitat or with a blocked parent");
        }

        float days = PetGenetics.broodDays(a.getData(), a.sheet, b.sheet, PetDataRepo.getTraits());
        getBroods().add(new PetBrood(habitat, a.id, b.id, Global.getSector().getClock().getTimestamp(), days));
        a.toHabitat(this);
        b.toHabitat(this);
    }

    public float getDaysLeft(PetBrood brood) {
        return Math.max(0f, brood.days - Global.getSector().getClock().getElapsedDaysSince(brood.start));
    }

    // the parents go to pet storage here, Unsettled; any fee paid stays paid
    public void cancelBrood(PetBrood brood) {
        getBroods().remove(brood);
        releaseParents(brood, PetConstants.UNSETTLED_DAYS);
    }

    private void releaseParents(PetBrood brood, float unsettled) {
        PetStatusManager manager = PetStatusManager.getInstance();
        for (String id : new String[]{brood.parentA, brood.parentB}) {
            Pet pet = manager.get(id);
            if (pet == null || pet.isDead()) continue;

            pet.place(null, this);
            pet.unsettle(unsettled);
        }
    }

    // Care, cores and hearts count at the end; a young of two read parents has its talents read, flaws stay unread
    private void roll(PetBrood brood) {
        PetStatusManager manager = PetStatusManager.getInstance();
        Pet a = manager.get(brood.parentA);
        Pet b = manager.get(brood.parentB);
        if (a == null || b == null) throw new IllegalStateException("IndEvo brood parent missing: " + brood.parentA + ", " + brood.parentB);

        Map<String, PetDataRepo.Trait> traits = PetDataRepo.getTraits();
        Random random = new Random();
        PetData data = a.getData();
        float inbreeding = getInbreeding(a, b);
        float mutationRate = getMutationRate(a, b);
        int hearts = a.getHearts() + b.getHearts();
        boolean read = talentsRead(a) && talentsRead(b);

        brood.young = new ArrayList<>();
        int n = PetGenetics.broodSize(data, a.sheet, b.sheet, traits, random);
        Set<String> taken = livingNames();
        for (int i = 0; i < n; i++) {
            PetSheet sheet = PetGenetics.rollYoung(data, a.sheet, b.sheet, getCare(), hearts, mutationRate, inbreeding, traits, random);
            for (String id : sheet.traits.keySet()) {
                PetDataRepo.Trait trait = traits.get(id);
                if (trait != null && (trait.isMutation() || read && trait.isTalent())) sheet.known.add(id);
            }
            Pet young = new Pet(data.id, PetDataRepo.suggestName(data, taken), sheet, a.id, b.id);
            taken.add(young.name);
            brood.young.add(young);
        }

        releaseParents(brood, 0f);
        if (brood.young.isEmpty()) getBroods().remove(brood);
        manager.reportBroodReady(this, brood, a, b);
    }

    private static boolean talentsRead(Pet pet) {
        for (String id : pet.getTalents()) if (!pet.sheet.isKnown(id)) return false;
        return true;
    }

    // registers the young and puts it on the ship, or into pet storage here; the habitat is free once every young is placed
    public void placeYoung(PetBrood brood, Pet young, FleetMemberAPI ship, boolean toCargo) {
        if (!brood.getYoung().remove(young)) throw new IllegalStateException("IndEvo young " + young.id + " is not in this brood");

        PetStatusManager manager = PetStatusManager.getInstance();
        manager.register(young);
        if (toCargo) manager.toChamber(young);
        else young.place(ship, ship == null ? this : null);
        if (brood.getYoung().isEmpty()) getBroods().remove(brood);
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostDescriptionSection(tooltip, mode);

        String ageNotif = !Commodities.BETA_CORE.equals(aiCoreId) ? "Pets will age while in storage." : "";
        tooltip.addPara("The " + getCurrentName() + " allows %s of pets. " + ageNotif, 10f, Misc.getHighlightColor(), "purchase, storage and management");

        if (!isBuilding() && isFunctional() && mode.equals(IndustryTooltipMode.NORMAL)) {
            float opad = 5.0F;

            tooltip.addSectionHeading("Stored Pets", market.getTextColorForFactionOrPlanet(), market.getDarkColorForFactionOrPlanet(), Alignment.MID, 10f);
            tooltip.beginTable(market.getFaction(), 20f, "Name", 190f, "Age", 90f, "Species", 115f); //390 total

            int i = 0;
            for (Pet pet : storedPets){
                if (i > 10) break;
                String name = pet.name.length() > 20 ? pet.name.substring(0, 20) + "..." : pet.name;
                String species = pet.getData().species.length() > 15 ? pet.getData().species.substring(0, 15) + "..." :  pet.getData().species;

                tooltip.addRow(name, pet.getAgeString(), species);
                i++;
            }

            float cost = getTotalStorageCost() * getStorageMult();

            tooltip.addTable("You do not have any pets stored here.", storedPets.size() - 10, opad);
            tooltip.addPara("Total storage costs per Month: %s", 10f, Misc.getHighlightColor(), Misc.getDGSCredits(cost));
        }
    }

    public float getStorageCost(Pet pet){
        float cost = Math.max(MIN_STORAGE_FEE, pet.getData().value * STORAGE_FEE_FRACT);
        if (!market.isPlayerOwned()) cost *= NPC_STORAGE_FEE_MULT;

        return cost * petStorageCostMult;
    }

    // what the player pays a month for one pet here; player markets pay it as upkeep, so the upkeep mults apply
    public float getMonthlyStorageCost(Pet pet) {
        return getStorageCost(pet) * getStorageMult();
    }

    private float getStorageMult() {
        return market.isPlayerOwned() ? getUpkeep().getMult() : 1f;
    }

    public boolean hasAdoption() {
        return isFunctional() && !market.isPlayerOwned();
    }

    public List<Pet> getOffers() {
        if (offers == null) refillOffers();
        return offers;
    }

    // weighted by rarity, rare pets x1.3 with an alpha core; one pet of each picked species
    private void refillOffers() {
        offers = new ArrayList<>();
        WeightedRandomPicker<PetData> picker = new WeightedRandomPicker<>();
        for (PetData data : PetDataRepo.getAll()) if (!data.isNoSell()) picker.add(data, stockWeight(data, aiCoreId));

        int count = Math.max(PetConstants.MIN_OFFERS, Math.min(PetConstants.MAX_OFFERS, market.getSize()));
        Set<String> taken = livingNames();
        for (int i = 0; i < count && !picker.isEmpty(); i++) {
            Pet pet = offer(picker.pickAndRemove(), taken);
            taken.add(pet.name);
            offers.add(pet);
        }
    }

    private static Set<String> livingNames() {
        Set<String> names = new HashSet<>();
        for (Pet pet : PetStatusManager.getInstance().getPets()) if (!pet.isDead()) names.add(pet.name);
        return names;
    }

    private static Pet offer(PetData data, Set<String> taken) {
        Pet pet = new Pet(data.id, PetDataRepo.suggestName(data, taken));
        float young = PetGenetics.youngDays(data, pet.sheet, PetDataRepo.getTraits());
        float old = Math.max(young, PetConstants.OFFER_MAX_AGE * pet.getLifetime());
        Random random = Misc.random;
        pet.age = random.nextFloat() < PetConstants.OFFER_YOUNG_CHANCE ? 1f + random.nextFloat() * Math.max(0f, young - 1f) : young + random.nextFloat() * (old - young);
        return pet;
    }

    public static float stockWeight(PetData data, String aiCoreId) {
        float bonus = Commodities.ALPHA_CORE.equals(aiCoreId) ? 1f + ALPHA_CORE_RARITY_BONUS : 1f;
        return data.rarity < 0.5f ? data.rarity * bonus : data.rarity;
    }

    public float getOfferPrice(Pet offer) {
        return offer.getData().value;
    }

    // the offers change when the month does
    public static int getDaysToNewOffers() {
        GregorianCalendar cal = Global.getSector().getClock().getCal();
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1;
    }

    public void adopt(Pet offer, FleetMemberAPI ship) {
        if (offers == null || !offers.remove(offer)) throw new IllegalStateException("IndEvo pet " + offer.id + " is not on offer at " + market.getId());

        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(getOfferPrice(offer));
        PetStatusManager.getInstance().register(offer);
        offer.place(ship, ship == null ? this : null);
    }

    public void rehome(Pet pet) {
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(PetConstants.REHOME_FEE);
        PetStatusManager.getInstance().reportPetDied(pet, PetStatusManager.PetDeathCause.REHOMED);
    }

    // relics from this market's storage first, then the fleet's cargo
    public boolean canAffordScan() {
        return RelicHelper.canAfford(market, PetConstants.GENE_SCAN_COST);
    }

    public int getRelics() {
        return RelicHelper.getAvailable(market);
    }

    public void geneScan(Pet pet) {
        RelicHelper.take(market, PetConstants.GENE_SCAN_COST);
        pet.scan();
    }

    public float getTotalStorageCost() {
        float cost = 0f;
        for (Pet pet : storedPets) if (!pet.isDead()) cost += getStorageCost(pet);
        return cost;
    }

    public void reportEconomyTick(int iterIndex) {
        //can't do on month end because monthly report

        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != lastIterInMonth) return;

        if (isFunctional() && !market.isPlayerOwned()) BillingHelper.charge(this, "storage", "Pet storage", getTotalStorageCost());
        if (!market.isPlayerOwned()) BillingHelper.charge(this, "habitats", "Pet habitats", getBroods().size() * getFee());
    }

    @Override
    public void reportEconomyMonthEnd() {
        if (!market.isPlayerOwned()) offers = null;
    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && Setting.PETS.enabled();
    }

    @Override
    public boolean showWhenUnavailable() {
        return super.showWhenUnavailable() && Setting.PETS.enabled();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Setting.PETS.enabled()) IndustrySubmarkets.ensure(this, Ids.PETMARKET);
        else IndustrySubmarkets.remove(market, Ids.PETMARKET);

        for (PetBrood brood : new ArrayList<>(getBroods())) {
            if (!brood.isReady() && getDaysLeft(brood) <= 0f) roll(brood);
        }
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        IndustrySubmarkets.release(this);
    }

    @Override
    public void apply() {
        super.apply(true);

        // npc markets bill storage through the monthly report in reportEconomyTick
        float storageCost = isFunctional() && market.isPlayerOwned() ? getTotalStorageCost() : 0f;

        if (storageCost > 0) getUpkeep().modifyFlat(STORAGE_UPKEEP_ID, storageCost, "Pet storage");
        else getUpkeep().unmodifyFlat(STORAGE_UPKEEP_ID);

        boolean petsEnabled = Setting.PETS.enabled();
        //supply(ItemIds.PET_FOOD, market.getSize() - 2);
        demand(Commodities.FOOD, market.getSize() - 2);

        /*Pair<String, Integer> deficit = getMaxDeficit(Commodities.FOOD);
        applyDeficitToProduction(1, deficit, ItemIds.PET_FOOD);*/

        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListener(this)) manager.addListener(this);

        if (isFunctional() && petsEnabled) {
            //if (!market.hasSubmarket(Ids.PETMARKET)) market.addSubmarket(Ids.PETMARKET);
            if (market.isPlayerOwned()){
                SubmarketPlugin sub = Misc.getLocalResources(market);
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

        if (!isFunctional() || !petsEnabled) supply.clear();
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
    }

    //AI-Core tooltips
    //gamma makes storage cost lower by 70%
    //alpha makes chance for rare pets higher by 50%
    //beta removes tariff and stops pets from ageing in storage

    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Increases the %s to be on sale by %s and the %s of broods here by %s.", 0f, Misc.getPositiveHighlightColor(), new String[]{"chance for rare pets", StringHelper.getAbsPercentString(ALPHA_CORE_RARITY_BONUS, true), "mutation rate", StringHelper.getAbsPercentString(PetConstants.ALPHA_MUTATION_MULT - 1f, false)});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Increases the %s to be on sale by %s and the %s of broods here by %s.", opad, Misc.getPositiveHighlightColor(), new String[]{"chance for rare pets", StringHelper.getAbsPercentString(ALPHA_CORE_RARITY_BONUS, true), "mutation rate", StringHelper.getAbsPercentString(PetConstants.ALPHA_MUTATION_MULT - 1f, false)});
        }
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "%s from the animal market and stops pets from %s", 0f, Misc.getPositiveHighlightColor(), new String[]{"Removes tariffs", "ageing in storage and habitats"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "%s from the animal market and stops pets from %s", opad, Misc.getPositiveHighlightColor(), new String[]{"Removes tariffs", "ageing in storage and habitats"});
        }
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Reduces %s by %s and raises habitat %s by %s.", 0f, Misc.getPositiveHighlightColor(), new String[]{"storage costs", StringHelper.getAbsPercentString(GAMMA_CORE_STORAGE_COST_RED_PERCENT, false), "Care", String.valueOf(Math.round(PetConstants.GAMMA_CARE))});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Reduces %s by %s and raises habitat %s by %s.", opad, Misc.getPositiveHighlightColor(), new String[]{"storage costs", StringHelper.getAbsPercentString(GAMMA_CORE_STORAGE_COST_RED_PERCENT, false), "Care", String.valueOf(Math.round(PetConstants.GAMMA_CARE))});
        }
    }

    @Override
    public boolean canImprove() {
        return true;
    }

    @Override
    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        String care = String.valueOf(Math.round(PetConstants.IMPROVED_CARE));
        if (mode == ImprovementDescriptionMode.INDUSTRY_TOOLTIP) info.addPara("Habitat Care increased by %s.", 0f, Misc.getHighlightColor(), care);
        else info.addPara("Increases habitat Care by %s.", 0f, Misc.getHighlightColor(), care);

        info.addSpacer(10f);
        super.addImproveDesc(info, mode);
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
