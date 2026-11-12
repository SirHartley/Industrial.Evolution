package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionProductionAPI.ProductionItemType;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.salvageyards.industry.SalvageYards;
import indevo.industries.salvageyards.intel.YardsCustomProductionIntel;
import indevo.industries.salvageyards.memory.WreckRegistry;
import indevo.industries.salvageyards.memory.YardsProductionData;
import indevo.utils.helper.BillingHelper;
import indevo.utils.helper.MiscIE;

import java.util.*;

import static indevo.items.installable.SpecialItemEffectsRepo.SALVAGE_DRONES_SP_PER_FP;

// prices, budget and limits of the order in the view; the order holds requests, lines hold what the registry allows
public class YardsContract {

    public enum Type {
        HULL, REGISTRY_HULL, WEAPON, WING
    }

    public static final String HAS_SP_SELECTED = "$IndEvo_HasSelectedSP_SY";
    public static final String BONUS_XP_KEY = "IndEvo_SYDoubleCapBonusXP";
    public static final float SCRAP_SHARE = 0.3f;

    public record Line(Type type, String id, int requested, int qty, int limit, float cost, float fitCost, int salvageUnits) {
        public boolean capped() {
            return limit >= 0 && requested > limit;
        }

        public boolean isHull() {
            return type == Type.HULL || type == Type.REGISTRY_HULL;
        }
    }

    private final MarketAPI market;
    private final YardsOrder order;
    private final float costMult;
    private final List<Line> lines = new ArrayList<>();

    public YardsContract(MarketAPI market, YardsOrder order) {
        this.market = market;
        this.order = order;
        costMult = getCostMult(market);

        List<FleetMemberAPI> members = ModTransfer.getMembers(market);
        order.scrapped.removeIf(m -> !members.contains(m));
        buildLines();
        order.parts = Math.max(0, Math.min(order.parts, getMaxParts()));
        order.relics = Math.max(0, Math.min(order.relics, getMaxRelics()));
    }

    public static float getCostMult(MarketAPI market) {
        float costMod = 1.8f - market.getFaction().getRelationship(Global.getSector().getPlayerFaction().getId());
        return Math.max(0.9f, Math.min(1.5f, costMod));
    }

    public MarketAPI getMarket() {
        return market;
    }

    public YardsOrder getOrder() {
        return order;
    }

    public SalvageYards getYards() {
        return (SalvageYards) market.getIndustry(Ids.SCRAPYARD);
    }

    public WreckRegistry getRegistry() {
        SalvageYards yards = getYards();
        return yards != null && yards.hasSalvageDrones() ? yards.getWreckRegistry() : null;
    }

    public float getPriceMult() {
        return costMult;
    }

    public List<Line> getLines() {
        return lines;
    }

    private void buildLines() {
        WreckRegistry registry = getRegistry();

        for (Map.Entry<String, Integer> e : order.hulls.entrySet()) {
            lines.add(new Line(Type.HULL, e.getKey(), e.getValue(), e.getValue(), -1, getLineCost(ProductionItemType.SHIP, e.getKey(), e.getValue()), 0f, 0));
        }

        for (Map.Entry<String, Integer> e : order.registryHulls.entrySet()) {
            int limit = registry == null ? 0 : registry.getHullCount(e.getKey());
            int qty = Math.min(e.getValue(), limit);
            float fitCost = 0f;
            if (registry != null) {
                for (WreckRegistry.WreckFit fit : registry.getOldestFits(e.getKey(), qty)) fitCost += fit.getValue() * costMult;
            }

            lines.add(new Line(Type.REGISTRY_HULL, e.getKey(), e.getValue(), qty, limit, getLineCost(ProductionItemType.SHIP, e.getKey(), qty),
                    Math.round(fitCost), qty * getSalvageUnitCost(e.getKey())));
        }

        for (Map.Entry<String, Integer> e : order.weapons.entrySet()) {
            int limit = registry == null ? 0 : registry.getWeaponCount(e.getKey());
            int qty = Math.min(e.getValue(), limit);
            lines.add(new Line(Type.WEAPON, e.getKey(), e.getValue(), qty, limit, getLineCost(ProductionItemType.WEAPON, e.getKey(), qty), 0f, 0));
        }

        for (Map.Entry<String, Integer> e : order.wings.entrySet()) {
            int limit = registry == null ? 0 : registry.getWingCount(e.getKey());
            int qty = Math.min(e.getValue(), limit);
            lines.add(new Line(Type.WING, e.getKey(), e.getValue(), qty, limit, getLineCost(ProductionItemType.FIGHTER, e.getKey(), qty), 0f, 0));
        }
    }

    // FactionProduction.getTotalCurrentCost rounds base cost times quantity times cost mult per item
    public float getUnitCost(ProductionItemType type, String id) {
        return Global.getSector().getPlayerFaction().getProduction().createSampleItem(type, id, 1).getBaseCost();
    }

    public float getLineCost(ProductionItemType type, String id, int qty) {
        return Math.round(getUnitCost(type, id) * qty * costMult);
    }

    public static int getSalvageUnitCost(String hullId) {
        return Global.getSettings().getHullSpec(hullId).getFleetPoints() * SALVAGE_DRONES_SP_PER_FP;
    }

    public float getBaseCost() {
        float total = 0f;
        for (Line line : lines) total += line.cost();
        return total;
    }

    public float getFitCost() {
        float total = 0f;
        for (Line line : lines) total += line.fitCost();
        return total;
    }

    public float getOrderValue() {
        return getBaseCost() + getFitCost();
    }

    public int getSalvageUnits() {
        int total = 0;
        for (Line line : lines) total += line.salvageUnits();
        return total;
    }

    public int getAvailableSalvageUnits() {
        SalvageYards yards = getYards();
        return yards == null ? 0 : yards.getAvailableSalvagePoints();
    }

    public boolean hasItems() {
        for (Line line : lines) if (line.qty() > 0) return true;
        return false;
    }

    public boolean isStoryPointUsed() {
        return market.getMemoryWithoutUpdate().getBoolean(HAS_SP_SELECTED);
    }

    public static float getScrapValue(FleetMemberAPI member) {
        return member.getBaseValue() * SCRAP_SHARE * Math.max(0f, 1f - ModTransfer.getDModList(member).size() / 10f);
    }

    public float getScrapValue() {
        float total = 0f;
        for (FleetMemberAPI m : order.scrapped) total += getScrapValue(m);
        return total;
    }

    // the story point doubles only the hull capacity term, see the feature doc known issues
    public float getCapacity() {
        float storyPointBonus = isStoryPointUsed() ? 2f : 1f;
        SalvageYards yards = getYards();
        float hullCapacity = yards == null ? 0f : yards.getHullCapacity();
        return 3 + getScrapValue() + hullCapacity * Global.getSettings().getInt("productionCapacityPerSWUnit") * storyPointBonus;
    }

    public float getBudgetLeft() {
        return getCapacity() - getOrderValue();
    }

    public int getPartsAvailable() {
        return (int) (Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.PARTS) + storage(ItemIds.PARTS));
    }

    public int getRelicsAvailable() {
        return (int) (Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS) + storage(ItemIds.RARE_PARTS));
    }

    private float storage(String id) {
        CargoAPI storage = MiscIE.getStorageCargo(market);
        return storage == null ? 0f : storage.getCommodityQuantity(id);
    }

    public int getMaxParts() {
        return Math.max(0, Math.min(Math.round(getBaseCost() / 2f / YardsProductionData.getPartValue()), getPartsAvailable()));
    }

    public int getMaxRelics() {
        return Math.max(0, Math.min(toData().getRequiredParts(), getRelicsAvailable()));
    }

    public float getPartsDiscount() {
        return order.parts * YardsProductionData.getPartValue();
    }

    public float getTotalCredits() {
        return getOrderValue() - getPartsDiscount();
    }

    public int getDModForecast() {
        return toData().getAverageDModAmount();
    }

    public boolean hasHulls() {
        for (Line line : lines) if (line.isHull() && line.qty() > 0) return true;
        return false;
    }

    public String getConfirmReason() {
        if (!hasItems()) return "Nothing is selected.";
        if (getOrderValue() > getCapacity()) return "The order is over the budget.";
        if (!BillingHelper.canAfford(getTotalCredits())) return "You do not have enough credits.";
        if (getAvailableSalvageUnits() < getSalvageUnits()) return "The yards do not have enough salvage units.";
        return null;
    }

    // registry tab: one more of this item fits the registry, the salvage units and the budget
    public boolean canAdd(Type type, String id) {
        WreckRegistry registry = getRegistry();
        if (registry == null) return false;

        switch (type) {
            case REGISTRY_HULL -> {
                int inOrder = order.registryHulls.getOrDefault(id, 0);
                if (inOrder >= registry.getHullCount(id)) return false;
                if (getSalvageUnits() + getSalvageUnitCost(id) > getAvailableSalvageUnits()) return false;

                List<WreckRegistry.WreckFit> fits = registry.getOldestFits(id, inOrder + 1);
                float fit = fits.size() > inOrder ? fits.get(inOrder).getValue() * costMult : 0f;
                return getLineCost(ProductionItemType.SHIP, id, 1) + fit <= getBudgetLeft();
            }
            case WEAPON -> {
                if (order.weapons.getOrDefault(id, 0) >= registry.getWeaponCount(id)) return false;
                return getLineCost(ProductionItemType.WEAPON, id, 1) <= getBudgetLeft();
            }
            case WING -> {
                if (order.wings.getOrDefault(id, 0) >= registry.getWingCount(id)) return false;
                return getLineCost(ProductionItemType.FIGHTER, id, 1) <= getBudgetLeft();
            }
            default -> {
                return false;
            }
        }
    }

    public YardsProductionData toData() {
        YardsProductionData data = new YardsProductionData();
        WreckRegistry registry = getRegistry();
        WreckRegistry.Order registryOrder = new WreckRegistry.Order();

        for (Line line : lines) {
            if (line.qty() <= 0) continue;

            switch (line.type()) {
                case HULL -> data.productionList.put(line.id(), line.qty());
                case REGISTRY_HULL -> {
                    data.productionList.put(line.id(), line.qty());
                    registryOrder.hulls.put(line.id(), line.qty());
                    registryOrder.salvageUnitCost += line.salvageUnits();
                    registryOrder.fits.addAll(registry.getOldestFits(line.id(), line.qty()));
                    registryOrder.fitCost += line.fitCost();
                }
                case WEAPON -> registryOrder.weapons.put(line.id(), line.qty());
                case WING -> registryOrder.wings.put(line.id(), line.qty());
            }
        }

        data.baseCost = getBaseCost();
        data.partsSpent = order.parts;
        data.rarePartsSpent = order.relics;
        data.registryOrder = registryOrder.isEmpty() ? null : registryOrder;
        return data;
    }

    public List<YardsCustomProductionIntel> getRunningOrders() {
        List<YardsCustomProductionIntel> orders = new ArrayList<>();

        for (IntelInfoPlugin plugin : Global.getSector().getIntelManager().getIntel(YardsCustomProductionIntel.class)) {
            YardsCustomProductionIntel intel = (YardsCustomProductionIntel) plugin;
            if (intel.getMarket() == market && intel.isWaiting()) orders.add(intel);
        }

        return orders;
    }

    public void confirm() {
        YardsProductionData data = toData();
        int credits = Math.round(data.getCost());

        BillingHelper.chargeNow(credits);
        removeCommodity(ItemIds.RARE_PARTS, data.rarePartsSpent);
        removeCommodity(ItemIds.PARTS, data.partsSpent);

        CargoAPI storage = MiscIE.getStorageCargo(market);
        FleetDataAPI playerFleet = Global.getSector().getPlayerFleet().getFleetData();
        FleetDataAPI storageFleet = null;
        if (storage != null) {
            storage.initMothballedShips("player");
            storageFleet = storage.getMothballedShips();
        }

        for (FleetMemberAPI member : order.scrapped) {
            playerFleet.removeFleetMember(member);
            if (storageFleet != null) storageFleet.removeFleetMember(member);
        }

        if (data.registryOrder != null) {
            getYards().getWreckRegistry().take(data.registryOrder);
            if (data.registryOrder.salvageUnitCost > 0) getYards().spendSalvagePoints(data.registryOrder.salvageUnitCost);
        }

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        memory.unset(HAS_SP_SELECTED);

        YardsCustomProductionIntel intel = new YardsCustomProductionIntel(market, data);
        Global.getSector().getIntelManager().addIntel(intel);
        intel.init();

        Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Paid " + Misc.getDGSCredits(credits) + " for the production order", Misc.getHighlightColor());
        order.clear();
    }

    // fleet cargo first, then local storage
    private void removeCommodity(String id, int amount) {
        if (amount <= 0) return;

        CargoAPI fleetCargo = Global.getSector().getPlayerFleet().getCargo();
        float inCargo = fleetCargo.getCommodityQuantity(id);
        float fromCargo = Math.min(inCargo, amount);
        fleetCargo.removeCommodity(id, fromCargo);

        float rest = amount - fromCargo;
        if (rest > 0) MiscIE.getStorageCargo(market).removeCommodity(id, rest);
    }

    public static String getDeliveryText() {
        int days = Setting.SY_HULL_DELIVERY_TIME.intValue();
        return days + (days == 1 ? " day" : " days");
    }
}
