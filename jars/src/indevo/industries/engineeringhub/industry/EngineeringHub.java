package indevo.industries.engineeringhub.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.economy.RelicSpeedUpTarget;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.SharedSubmarketUser;
import indevo.industries.engineeringhub.memory.RecoveredHullmods;
import indevo.items.installable.BlueprintInstallableItemPlugin;
import indevo.submarkets.IndustrySubmarkets;
import indevo.utils.helper.MiscIE;
import indevo.utils.timers.NewDayListener;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.utils.helper.MiscIE.*;

public class EngineeringHub extends SharedSubmarketUser implements NewDayListener, RelicSpeedUpTarget {

    public static final Logger log = Global.getLogger(EngineeringHub.class);
    private boolean debug = false;

    public static final int RELICS_PER_DP_FOR_BP_CREATION = 10;

    protected SpecialItemData blueprintItem = null;

    private static final String RESEARCH_LIST_KEY = "$IndEvo_researchProgress";
    public Map<String, Float> researchProgressList = new HashMap<>();
    private ShipVariantAPI currentDeconShipVar = null;
    private int daysRequired = 999;
    private int daysPassed = 0;
    private boolean finishOnOwn = false;
    private transient boolean expanded = false;

    private final float gammaCoreUpkeepRed = 0.2f;
    private final float betaCoreTimeMult = 0.7f;
    private final float alphaCoreTimeMult = 0.85f;
    private final int alphaCoreDModRed = 1;

    public record BlueprintSource(SpecialItemData item, boolean installed, int count) {
    }

    @Override
    public boolean isAvailableToBuild() {
        return Setting.ENGHUB.enabled() && super.isAvailableToBuild();
    }

    @Override
    public boolean showWhenUnavailable() {
        return Setting.ENGHUB.enabled();
    }

    @Override
    public void apply() {
        super.apply(true);
        debug = Global.getSettings().isDevMode();

        if (isFunctional() && market.isPlayerOwned()) {
            Global.getSector().getListenerManager().addListener(this, true);
            refreshRequiredDays();
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        IndustrySubmarkets.ensure(this, Ids.ENGSTORAGE);
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);
    }

    @Override
    public void onNewDay() {
        if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) == null
                || market.getSubmarket(Ids.ENGSTORAGE) == null
                || !isFunctional()) {
            return;
        }

        researchProgressList = getMapFromMemory(RESEARCH_LIST_KEY);

        // Deconstruction state machine
        if (currentDeconShipVar == null) {
            if (initDeconstruction()) {
                String name = currentDeconShipVar.getHullSpec().getNameWithDesignationWithDashClass();
                Global.getSector().getCampaignUI().addMessage(
                        "Reverse engineering has begun for a %s at %s.",
                        Global.getSettings().getColor("standardTextColor"),
                        name, market.getName(), Misc.getHighlightColor(), market.getFaction().getBrightUIColor()
                );
            }
        } else if (daysRequired <= daysPassed || debug) {
            String id = currentDeconShipVar.getHullSpec().getHullId();
            addProgressToList(currentDeconShipVar);

            MessageIntel intel = new MessageIntel(
                    "Reverse engineering of the %s has finished.",
                    Misc.getTextColor(),
                    new String[]{currentDeconShipVar.getHullSpec().getNameWithDesignationWithDashClass()},
                    Misc.getHighlightColor()
            );
            intel.addLine(BaseIntelPlugin.BULLET + "The current progress is: %s",
                    Misc.getHighlightColor(),
                    new String[]{Math.round(getProgress(id) * 100) + "%"});
            intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "revBP"));
            Global.getSector().getCampaignUI().addMessage(intel);
            intel.setSound(BaseIntelPlugin.getSoundMinorMessage());

            resetDeconstructionVariables();
        } else {
            daysPassed++;
        }

        // off by default, a hull then waits at 100% until the player finishes it in the panel
        if (finishOnOwn) {
            for (Map.Entry<String, Float> entry : researchProgressList.entrySet()) {
                if (entry.getValue() < 1f) continue;

                String id = entry.getKey();
                List<BlueprintSource> sources = getBlueprintSources(id);
                if (sources.isEmpty() || !consume(sources.get(0))) continue;

                researchProgressList.remove(id);
                deliver(id);
                break; // one completion per day
            }
        }

        storeMapInMemory(getClampedMap(researchProgressList, 1f), RESEARCH_LIST_KEY);
    }

    /* ----------------------------- Helpers ----------------------------- */

    public boolean isFinishOnOwn() {
        return finishOnOwn;
    }

    public void setFinishOnOwn(boolean finishOnOwn) {
        this.finishOnOwn = finishOnOwn;
    }

    // installed item first, then the shared storage stacks; only ship blueprints, or the retrofit item for Tiandong and Roider hulls
    public List<BlueprintSource> getBlueprintSources(String hullId) {
        List<BlueprintSource> sources = new ArrayList<>();
        ShipHullSpecAPI hull = getHullSpecOrNull(hullId);
        if (hull == null) return sources;

        String itemId = getPaymentItemId(hullId);
        if (paysFor(blueprintItem, itemId, hull)) sources.add(new BlueprintSource(blueprintItem, true, 1));

        CargoAPI cargo = MiscIE.getIndustrialStorageCargo(market);
        if (cargo == null) return sources;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (paysFor(data, itemId, hull)) sources.add(new BlueprintSource(data, false, (int) stack.getSize()));
        }

        return sources;
    }

    public boolean paysFor(SpecialItemData item, String hullId) {
        ShipHullSpecAPI hull = getHullSpecOrNull(hullId);
        return hull != null && paysFor(item, getPaymentItemId(hullId), hull);
    }

    private static boolean paysFor(SpecialItemData item, String itemId, ShipHullSpecAPI hull) {
        if (item == null || item.getData() == null || !itemId.equals(item.getId())) return false;

        ShipHullSpecAPI written = getHullSpecOrNull(item.getData());
        return written != null && hull.getHullSize() == written.getHullSize();
    }

    public static String getPaymentItemId(String hullId) {
        if (isTiandong(hullId)) return "tiandong_retrofit_bp";
        if (isRoider(hullId)) return "roider_retrofit_bp";
        return Items.SHIP_BP;
    }

    public int getRelicStock() {
        CargoAPI cargo = MiscIE.getIndustrialStorageCargo(market);
        return cargo != null ? (int) cargo.getCommodityQuantity(ItemIds.RARE_PARTS) : 0;
    }

    public int getRelicCost(String hullId) {
        return Math.round(getRelicCostForHullId(hullId));
    }

    // completes a hull at 100% at once; source null pays with relic components from the shared storage
    public boolean finish(String hullId, BlueprintSource source) {
        refreshResearchProgressList();
        Float progress = researchProgressList.get(hullId);
        if (progress == null || progress < 1f || getHullSpecOrNull(hullId) == null) return false;

        if (source == null) {
            int cost = getRelicCost(hullId);
            if (getRelicStock() < cost) return false;
            MiscIE.getIndustrialStorageCargo(market).removeCommodity(ItemIds.RARE_PARTS, cost);
        } else if (!paysFor(source.item(), hullId) || !consume(source)) return false;

        researchProgressList.remove(hullId);
        deliver(hullId);
        storeMapInMemory(getClampedMap(researchProgressList, 1f), RESEARCH_LIST_KEY);
        return true;
    }

    private boolean consume(BlueprintSource source) {
        if (source.installed()) {
            if (blueprintItem == null || !blueprintItem.equals(source.item())) return false;
            setSpecialItem(null);
            return true;
        }

        CargoAPI cargo = MiscIE.getIndustrialStorageCargo(market);
        if (cargo == null || cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, source.item()) < 1f) return false;
        cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, source.item(), 1);
        return true;
    }

    public MarketAPI getDeliveryMarket() {
        MarketAPI gather = market.getFaction().getProduction().getGatheringPoint();
        if (Setting.AUTO_SHIP_BP_TO_GATHERING_POINT.enabled() && gather != null && MiscIE.getStorageCargo(gather) != null) return gather;
        return market;
    }

    private void deliver(String hullId) {
        MarketAPI target = getDeliveryMarket();
        deliverReverseEngineered(hullId, isTiandong(hullId), isRoider(hullId), target, MiscIE.getStorageCargo(target));
    }

    public float getRelicCostForHullId(String id){
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
        float dp = spec.getSuppliesToRecover();
        return Math.round(dp * RELICS_PER_DP_FOR_BP_CREATION);
    }

    // getHullSpec throws on an id whose mod is gone
    public static ShipHullSpecAPI getHullSpecOrNull(String id) {
        if (id == null) return null;
        try {
            return Global.getSettings().getHullSpec(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void deliverReverseEngineered(String hullId, boolean dong, boolean roider, MarketAPI target, CargoAPI cargo) {
        ShipHullSpecAPI ship = Global.getSettings().getHullSpec(hullId);

        if (dong || roider) {
            String itemId = dong ? "tiandong_retrofit_bp" : "roider_retrofit_bp";
            cargo.addSpecial(new SpecialItemData(itemId, hullId), 1);
            Global.getSector().getCampaignUI().addMessage(
                    "A retrofit template for a %s has been reverse engineered and delivered to %s",
                    Global.getSettings().getColor("standardTextColor"),
                    ship.getNameWithDesignationWithDashClass(), target.getName(),
                    Misc.getHighlightColor(), target.getFaction().getBrightUIColor()
            );
        } else {
            cargo.addSpecial(new SpecialItemData(Items.SHIP_BP, hullId), 1);
            Global.getSector().getCampaignUI().addMessage(
                    "A blueprint for a %s has been reverse engineered and delivered to %s",
                    Global.getSettings().getColor("standardTextColor"),
                    ship.getNameWithDesignationWithDashClass(), target.getName(),
                    Misc.getHighlightColor(), target.getFaction().getBrightUIColor()
            );
        }
    }

    private static boolean isHullSpecsContainsId(String id) {
        for (ShipHullSpecAPI hs : Global.getSettings().getAllShipHullSpecs()) {
            if (hs.getHullId().equals(id)) return true;
        }

        return false;
    }

    public static boolean isRoider(String id) {
        if (isHullSpecsContainsId(id)) {
            return Global.getSettings().getHullSpec(id).getTags().contains("roider_retrofit");
        }

        return id.equals("roider_retrofit_bp");
    }

    public static boolean isTiandong(String id) {
        if (isHullSpecsContainsId(id)) {
            return Global.getSettings().getHullSpec(id).getManufacturer().equals("Tiandong");
        }

        return id.equals("tiandong_retrofit_bp");
    }

    public ShipVariantAPI getCurrentDeconShipVar() {
        return currentDeconShipVar;
    }

    public void setCurrentDeconShipVar(ShipVariantAPI currentDeconShipVar) {
        this.currentDeconShipVar = currentDeconShipVar;
    }

    public float getProgress(String hullId) {
        String baseHullId = getBaseHullIdForHullId(hullId);
        if (!researchProgressList.containsKey(baseHullId)) return 0f;

        float progress = researchProgressList.get(baseHullId);
        return Math.min(progress, 1f);
    }


    private boolean initDeconstruction() {
        CargoAPI engStorage = market.getSubmarket(Ids.ENGSTORAGE).getCargo();
        engStorage.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        if (engStorage.getMothballedShips().getMembersListCopy().isEmpty()) return false;

        for (FleetMemberAPI ship : engStorage.getMothballedShips().getMembersListCopy()) {
            if (getProgress(getBaseShipHullSpec(ship.getVariant()).getHullId()) >= 1) continue;

            RecoveredHullmods.record(market, ship.getVariant());
            ShipVariantAPI shipVar = MiscIE.stripShipToCargoAndReturnVariant(ship, market);

            engStorage.getMothballedShips().removeFleetMember(ship); //remove ship from storage
            currentDeconShipVar = shipVar;
            refreshRequiredDays();

            log.info("init decon for " + currentDeconShipVar.getFullDesignationWithHullName());
            return true;
        }

        return false;
    }

    private void resetDeconstructionVariables() {
        daysRequired = 999;
        daysPassed = 0;
        currentDeconShipVar = null;
    }

    private void addProgressToList(ShipVariantAPI shipVar) {
        String hullId = getBaseShipHullSpec(shipVar).getHullId();
        float progress = getResearchValue(shipVar);

        if (researchProgressList.containsKey(hullId)) {
            researchProgressList.put(hullId, researchProgressList.get(hullId) + progress);
        } else {
            researchProgressList.put(hullId, progress);
        }
    }

    private String getBaseHullIdForHullId(String id) {
        return getBaseShipHullSpec(Global.getSettings().getHullSpec(id)).getHullId();
    }

    private ShipHullSpecAPI getBaseShipHullSpec(ShipVariantAPI shipVar) {
        return getBaseShipHullSpec(shipVar.getHullSpec());
    }

    private ShipHullSpecAPI getBaseShipHullSpec(ShipHullSpecAPI spec) {
        ShipHullSpecAPI base = spec.getDParentHull();

        if (!spec.isDefaultDHull() && !spec.isRestoreToBase()) {
            base = spec;
        }

        if (spec.isRestoreToBase()) {
            base = spec.getBaseHull();
        }

        return base;
    }

    public void refreshResearchProgressList() {
        researchProgressList = getMapFromMemory(RESEARCH_LIST_KEY);
    }

    public float getResearchValue(ShipVariantAPI shipVar) {

        if (shipVar == null) {
            log.error("EngineeringHub ShipVar == null!");
            return 0f;
        }

        //get research value for specific hull
        Map<ShipAPI.HullSize, Float> valueMap = getBaseHullSizeValueMap();

        float redPerDMod = 0.05f;

        int dModAmount = getNumNonBuiltInDMods(shipVar);

        if (dModAmount > 0 && getAiCoreIdNotNull().equals(Commodities.ALPHA_CORE)) {
            dModAmount -= alphaCoreDModRed;
        }

        float progress = valueMap.get(shipVar.getHullSize());

        //reduce by 5% with additional penalty for more D-Mods
        progress -= ((redPerDMod * dModAmount) * (1f + ((redPerDMod * dModAmount) * dModAmount)));

        progress = Math.round(progress * 100f) / 100f; //round it to avoid 25% actually being 24%

        return Math.max(progress, 0f);
    }

    private int getRequiredDaysForHull(ShipVariantAPI ship) {
        return getBaseDays(ship.getHullSize());
    }

    // 15 days up to cruisers, 21 for capitals, before AI cores
    public int getBaseDays(ShipAPI.HullSize size) {
        return Math.round(getCleanResearchValue(size) * 57) - 5;
    }

    private void refreshRequiredDays() {
        if (currentDeconShipVar != null) {
            int daysReq = getRequiredDaysForHull(currentDeconShipVar);
            switch (getAiCoreIdNotNull()) {
                case Commodities.BETA_CORE:
                    daysReq *= betaCoreTimeMult;
                    break;
                case Commodities.ALPHA_CORE:
                    daysReq *= alphaCoreTimeMult;
                    break;
            }

            daysRequired = daysReq;
        }
    }

    private String getAiCoreIdNotNull() {
        if (getAICoreId() != null) {
            return getAICoreId();
        }
        return "none";
    }

    // what one ship without D-mods adds
    public float getCleanResearchValue(ShipAPI.HullSize size) {
        Float value = getBaseHullSizeValueMap().get(size);
        return value != null ? value : 0f;
    }

    public int getDaysRequired() {
        return daysRequired;
    }

    public int getDaysPassed() {
        return daysPassed;
    }

    public String getBaseHullId(ShipHullSpecAPI spec) {
        return getBaseShipHullSpec(spec).getHullId();
    }

    private Map<ShipAPI.HullSize, Float> getBaseHullSizeValueMap() {
        Map<ShipAPI.HullSize, Float> valueMap = new HashMap<>();
        valueMap.put(ShipAPI.HullSize.FRIGATE, 0.35f);
        valueMap.put(ShipAPI.HullSize.DESTROYER, 0.35f);
        valueMap.put(ShipAPI.HullSize.CRUISER, 0.35f);
        valueMap.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.45f);

        return valueMap;
    }

    public int getNumNonBuiltInDMods(ShipVariantAPI variant) {
        int count = 0;
        for (String id : variant.getHullMods()) {
            if (getMod(id).hasTag(Tags.HULLMOD_DMOD)) {
                if (variant.getHullSpec().getBuiltInMods().contains(id)) continue;
                count++;
            }
        }
        return count;
    }

    public HullModSpecAPI getMod(String id) {
        return Global.getSettings().getHullModSpec(id);
    }

    public SpecialItemData getSpecialItem() {
        return blueprintItem;
    }

    public void setSpecialItem(SpecialItemData special) {
        blueprintItem = special;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        return blueprintItem == null && BlueprintInstallableItemPlugin.isBlueprint(data);
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        if (blueprintItem != null && !forUpgrade) {
            CargoAPI cargo = getCargoForInteractionMode(mode);
            if (cargo != null) {
                cargo.addSpecial(blueprintItem, 1);
            }
        }
    }

    @Override
    protected boolean addNonAICoreInstalledItems(Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (blueprintItem == null) return false;

        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();

        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(blueprintItem.getId());

        TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48);
        BlueprintInstallableItemPlugin.addBlueprintDescription(text, blueprintItem, InstallableIndustryItemPlugin.InstallableItemDescriptionMode.INDUSTRY_TOOLTIP);
        tooltip.addImageWithText(opad);

        return true;
    }

    @Override
    public java.util.List<InstallableIndustryItemPlugin> getInstallableItems() {

        ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<>();
        list.add(new BlueprintInstallableItemPlugin(this));
        return list;
    }

    @Override
    public void initWithParams(java.util.List<String> params) {
        super.initWithParams(params);

        for (String str : params) {
            if (BlueprintInstallableItemPlugin.BLUEPRINT_IDS.contains(str)) {
                blueprintItem = new SpecialItemData(str, null);
                break;
            }
        }
    }

    @Override
    public java.util.List<SpecialItemData> getVisibleInstalledItems() {
        List<SpecialItemData> result = super.getVisibleInstalledItems();

        if (blueprintItem != null) {
            result.add(blueprintItem);
        }

        return result;
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);

        if (mode.equals(Industry.IndustryTooltipMode.NORMAL)) {
            tooltip.addPara("Expand this tooltip for a %s.", 10f, Misc.getHighlightColor(), "progress overview");
        }

        if (expanded) {
            addShipProgressOverview(tooltip, mode, true);
            addCurrentDeconstTooltip(tooltip, mode);
        }
    }

    @Override
    protected void addPostUpkeepSection(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        super.addPostUpkeepSection(tooltip, mode);
        if (!expanded) addCurrentDeconstTooltip(tooltip, mode);
    }

    public void addCurrentDeconstTooltip(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        if (!isBuilding() && isFunctional() && mode.equals(Industry.IndustryTooltipMode.NORMAL)) {
            refreshResearchProgressList();

            FactionAPI marketFaction = market.getFaction(); //always get the player faction, for AI control options
            Color color = marketFaction.getBaseUIColor();
            Color dark = marketFaction.getDarkUIColor();
            float opad = 5.0F;

            tooltip.addSectionHeading("Current Project", color, dark, Alignment.MID, 10f);

            if (currentDeconShipVar != null) {
                TooltipMakerAPI text = tooltip.beginImageWithText(currentDeconShipVar.getHullSpec().getSpriteName(), 48);

                text.addPara("Reverse engineering: %s. Time remaining: %s. This will add %s to the total progress.",
                        opad,
                        Misc.getHighlightColor(),
                        new String[]{currentDeconShipVar.getHullSpec().getNameWithDesignationWithDashClass(),
                                daysRequired - daysPassed + " days",
                                Math.round(getResearchValue(currentDeconShipVar) * 100) + "%"});

                tooltip.addImageWithText(opad);
            } else {

                tooltip.addPara("No ship is currently being reverse engineered.",
                        opad);
            }
        }
    }

    public void addShipProgressOverview(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode, boolean expanded) {
        if (!expanded || !mode.equals(Industry.IndustryTooltipMode.NORMAL)) return;
        refreshResearchProgressList();

        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 5.0F;

        tooltip.addSectionHeading("Research Progress", color, dark, Alignment.MID, 10f);

        tooltip.addPara("At 100%% progress, finish a hull in the hub's menu with a %s or %s.",
                opad, Misc.getHighlightColor(), new String[]{"blueprint of the same hull size", "relic components"});

        tooltip.beginTable(marketFaction, 20f, "Ship Hull", 250f, "Total Progress", 140f);


        for (Map.Entry<String, Float> ship : researchProgressList.entrySet()) {

            String designation = Global.getSettings().getHullSpec(ship.getKey()).getHullNameWithDashClass();
            String progress = Math.round(ship.getValue() * 100) + "%";

            tooltip.addRow(designation, progress);
        }

        tooltip.addTable("You have not reverse engineered any ships yet.", 0, opad);
    }


    public boolean isTooltipExpandable() {
        return true;
    }

    @Override
    public void createTooltip(Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        this.expanded = expanded;
        super.createTooltip(mode, tooltip, expanded);
    }

    //AI core handling

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases the time it takes to Reverse Engineer a hull by %s, and negates the penalty of %s.", 0f, highlight, new String[]{Math.round((1F - (alphaCoreTimeMult)) * 100.0F) + "%", "one D-Mod"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Decreases the time it takes to Reverse Engineer a hull by %s, and negates the penalty of %s.", opad, highlight, new String[]{Math.round((1F - (alphaCoreTimeMult)) * 100.0F) + "%", "one D-Mod"});
        }
    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases the time it takes to Reverse Engineer a hull by %s.", 0f, highlight, new String[]{Math.round((1F - (betaCoreTimeMult)) * 100.0F) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Decreases the time it takes to Reverse Engineer a hull by %s.", opad, highlight, new String[]{Math.round((1F - (betaCoreTimeMult)) * 100.0F) + "%"});
        }
    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Reduces upkeep cost by %s while no ship is being Reverse Engineered.", 0.0F, highlight, new String[]{Math.round((1.0F - gammaCoreUpkeepRed) * 100.0F) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Reduces upkeep cost by %s while no ship is being Reverse Engineered.", opad, highlight, new String[]{Math.round((1.0F - gammaCoreUpkeepRed) * 100.0F) + "%"});
        }
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        if (getAiCoreIdNotNull().equals(Commodities.GAMMA_CORE)) {
            String name = "Gamma Core assigned";
            if (currentDeconShipVar == null) {
                this.getUpkeep().modifyMult("ind_core", gammaCoreUpkeepRed, name);
            } else {
                this.getUpkeep().unmodifyMult("ind_core");
            }
        } else {
            this.getUpkeep().unmodifyMult("ind_core");
        }
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    @Override
    public boolean isLegalOnSharedSubmarket(CargoStackAPI stack) {
        if (stack.isCommodityStack() && ItemIds.RARE_PARTS.equals(stack.getCommodityId())) return true;
        if (!stack.isSpecialStack()) return false;

        return new ArrayList<>(Arrays.asList(Items.SHIP_BP, "roider_retrofit_bp", "tiandong_retrofit_bp")).contains(stack.getSpecialItemSpecIfSpecial().getId());
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("Engineering Hub: uses %s or %s in this storage to overwrite and consume.", 10f, Misc.getHighlightColor(), "blueprints", Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName());
    }

    @Override
    public String getSpeedUpId() {
        return getId();
    }

    @Override
    public String getSpeedUpLabel() {
        return "Accelerate Research";
    }

    @Override
    public String getSpeedUpLore() {
        return "Domain scanners can see through armour plating that your engineers would otherwise have to peel away layer by layer.";
    }

    @Override
    public int getSpeedUpDaysLeft() {
        return currentDeconShipVar != null ? Math.max(daysRequired - daysPassed, 0) : 0;
    }

    @Override
    public int getSpeedUpDaysTotal() {
        return currentDeconShipVar != null ? daysRequired : 0;
    }

    @Override
    public void speedUp(int days) {
        daysPassed += days;
    }
}
