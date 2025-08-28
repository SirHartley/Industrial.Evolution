package indevo.industries.engineeringhub.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.SharedSubmarketUser;
import indevo.items.installable.BlueprintInstallableItemPlugin;
import indevo.submarkets.script.SubMarketAddOrRemovePlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.timers.NewDayListener;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.utils.helper.MiscIE.*;

public class EngineeringHub extends SharedSubmarketUser implements NewDayListener {

    public static final Logger log = Global.getLogger(EngineeringHub.class);
    private boolean debug = false;

    protected SpecialItemData blueprintItem = null;

    private static final String RESEARCH_LIST_KEY = "$IndEvo_researchProgress";
    public Map<String, Float> researchProgressList = new HashMap<>();
    private ShipVariantAPI currentDeconShipVar = null;
    private int daysRequired = 999;
    private int daysPassed = 0;

    private final float gammaCoreUpkeepRed = 0.2f;
    private final float betaCoreTimeMult = 0.7f;
    private final float alphaCoreTimeMult = 0.85f;
    private final int alphaCoreDModRed = 1;

    public void apply() {
        super.apply(true);
        debug = Global.getSettings().isDevMode();
        applyblueprintItemEffects();

        if (isFunctional() && market.isPlayerOwned()) {
            Global.getSector().getListenerManager().addListener(this, true);
            refreshRequiredDays();
        }

        removeDummyRelicComponentFromCargo();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (isFunctional() && !market.hasSubmarket(Ids.ENGSTORAGE)) {
            Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.ENGSTORAGE, false));
            addSharedSubmarket();
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);
        if (blueprintItem != null) {
            BlueprintInstallableItemPlugin.BlueprintEffect effect = BlueprintInstallableItemPlugin.BLUEPRINT_EFFECTS.get(blueprintItem.getId());
            if (effect != null) {
                effect.unapply(this);
            }
        }

        removeSharedSubmarket();
        Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.ENGSTORAGE, true));
    }

    @Override
    public void onNewDay() {

        if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) == null
                || market.getSubmarket(Ids.ENGSTORAGE) == null
                || !isFunctional())
            return;

        researchProgressList = getMapFromMemory(RESEARCH_LIST_KEY);

        if (currentDeconShipVar == null) {
            boolean successful = initDeconstruction();

            if (successful) {
                String name = currentDeconShipVar.getHullSpec().getNameWithDesignationWithDashClass();
                Global.getSector().getCampaignUI().addMessage("Reverse engineering has begun for a %s at %s.",
                        Global.getSettings().getColor("standardTextColor"), name, market.getName(), Misc.getHighlightColor(), market.getFaction().getBrightUIColor());

            }

        } else if (daysRequired <= daysPassed || debug) {
            String id = currentDeconShipVar.getHullSpec().getHullId();

            addProgressToList(currentDeconShipVar);

            MessageIntel intel = new MessageIntel("Reverse engineering of the %s has finished.",
                    Misc.getTextColor(),
                    new String[]{currentDeconShipVar.getHullSpec().getNameWithDesignationWithDashClass()},
                    Misc.getHighlightColor());

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

        boolean toStorage = !Settings.getBoolean(Settings.AUTO_SHIP_BP_TO_GATHERING_POINT);

        for (Map.Entry<String, Float> entry : researchProgressList.entrySet()) {
            String id;

            if (entry.getValue() >= 1f) {
                id = entry.getKey();

                boolean dong = isTiandong(id);
                boolean roider = isRoider(id);

                //hull size check
                ShipAPI.HullSize shipSize = Global.getSettings().getHullSpec(id).getHullSize();

                //hasValidItemForHullSize
                SpecialItemData toRemove = null;
                if (dong) {
                    //dong
                    //check specItem
                    if (getSpecialItem() != null && isTiandong(getSpecialItem().getId())) {
                        if (gethullSize(getSpecialItem()) == shipSize) {
                            toRemove = getSpecialItem();
                            setSpecialItem(null);
                        }
                    }

                    //check Cargo
                    if (toRemove == null) {
                        if (market.hasSubmarket(Ids.SHAREDSTORAGE)) {
                            CargoAPI cargo = market.getSubmarket(Ids.SHAREDSTORAGE).getCargo();
                            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                                SpecialItemData spec = stack.getSpecialDataIfSpecial();
                                if (spec != null
                                        && isTiandong(spec.getId())
                                        && gethullSize(spec) == shipSize) {
                                    toRemove = spec;
                                    cargo.removeItems(stack.getType(), stack.getData(), 1);
                                    break;
                                }
                            }
                        }
                    }

                } else if (roider) {
                    //roider
                    //check specItem
                    if (getSpecialItem() != null && isRoider(getSpecialItem().getId())) {
                        if (gethullSize(getSpecialItem()) == shipSize) {
                            toRemove = getSpecialItem();
                            setSpecialItem(null);
                        }
                    }

                    //check Cargo
                    if (toRemove == null) {
                        if (market.hasSubmarket(Ids.SHAREDSTORAGE)) {
                            CargoAPI cargo = market.getSubmarket(Ids.SHAREDSTORAGE).getCargo();
                            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                                SpecialItemData spec = stack.getSpecialDataIfSpecial();
                                if (spec != null
                                        && isRoider(spec.getId())
                                        && gethullSize(spec) == shipSize) {
                                    toRemove = spec;
                                    cargo.removeItems(stack.getType(), stack.getData(), 1);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    //check specItem
                    if (getSpecialItem() != null) {
                        if (gethullSize(getSpecialItem()) == shipSize) {
                            toRemove = getSpecialItem();
                            setSpecialItem(null);
                        }
                    }

                    //check Cargo
                    if (toRemove == null) {
                        if (market.hasSubmarket(Ids.SHAREDSTORAGE)) {
                            CargoAPI cargo = market.getSubmarket(Ids.SHAREDSTORAGE).getCargo();
                            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                                SpecialItemData spec = stack.getSpecialDataIfSpecial();
                                if (spec != null
                                        && gethullSize(spec) == shipSize) {
                                    toRemove = spec;
                                    cargo.removeItems(stack.getType(), stack.getData(), 1);
                                    break;
                                }
                            }
                        }
                    }
                }

                if (toRemove != null) {
                    researchProgressList.remove(id);

                    MarketAPI gather = market.getFaction().getProduction().getGatheringPoint();
                    MarketAPI target = toStorage ? market : gather;
                    CargoAPI cargo = MiscIE.getStorageCargo(target);

                    if (dong || roider) {
                        //special handling for tiandong/Roider refit templates
                        //add the template to storage

                        if (dong) {
                            SpecialItemData data = new SpecialItemData("tiandong_retrofit_bp", id);
                            cargo.addSpecial(data, 1);
                        } else if (roider) {
                            SpecialItemData data = new SpecialItemData("roider_retrofit_bp", id);
                            cargo.addSpecial(data, 1);
                        }

                        //throw a small message
                        ShipHullSpecAPI ship = Global.getSettings().getHullSpec(id);
                        Global.getSector().getCampaignUI().addMessage("A retrofit template for a %s has been reverse engineered and delivered to %s",
                                Global.getSettings().getColor("standardTextColor"), ship.getNameWithDesignationWithDashClass(), target.getName(), Misc.getHighlightColor(), target.getFaction().getBrightUIColor());
                    } else {

                        //add the blueprint to storage
                        SpecialItemData data = new SpecialItemData(Items.SHIP_BP, id);
                        cargo.addSpecial(data, 1);

                        //throw a small message
                        ShipHullSpecAPI ship = Global.getSettings().getHullSpec(id);
                        Global.getSector().getCampaignUI().addMessage("A blueprint for a %s has been reverse engineered and delivered to %s",
                                Global.getSettings().getColor("standardTextColor"), ship.getNameWithDesignationWithDashClass(), target.getName(), Misc.getHighlightColor(), target.getFaction().getBrightUIColor());
                    }
                    break;
                }

            }
        }

        storeMapInMemory(getClampedMap(researchProgressList, 1f), RESEARCH_LIST_KEY);
    }

    public void addDummyRelicComponentToCargo(){
        if (getSpecialItem() != null && ItemIds.RELIC_SPECIAL_ITEM.equals(getSpecialItem().getId())) return;

        CargoAPI storage = Misc.getStorageCargo(market);
        if (storage != null && storage.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.RELIC_SPECIAL_ITEM, null)) < 1){
            storage.addSpecial(new SpecialItemData(ItemIds.RELIC_SPECIAL_ITEM, null), 1);
        }
    }

    public void removeDummyRelicComponentFromCargo(){
        CargoAPI storage = Misc.getStorageCargo(market);
        if (storage != null) storage.removeItems(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.RELIC_SPECIAL_ITEM, null), 99);

        //items are dumped into fleet cargo once uninstalled
        Global.getSector().getPlayerFleet().getCargo().removeItems(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.RELIC_SPECIAL_ITEM, null), 99);
    }

    public ShipAPI.HullSize gethullSize(SpecialItemData data) {
        if (data.getData() == null) return ShipAPI.HullSize.DEFAULT;

        return Global.getSettings().getHullSpec(data.getData()).getHullSize();
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
        int baseMod = Math.round(getBaseHullSizeValueMap().get(ship.getHullSize()) * 57); //what?
        baseMod -= 5; //reduce by 5 to get the day count I want, fuck you (future me - what??)

        return baseMod;
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

    private Map<ShipAPI.HullSize, Float> getBaseHullSizeValueMap() {
        Map<ShipAPI.HullSize, Float> valueMap = new HashMap<>();
        valueMap.put(ShipAPI.HullSize.FRIGATE, 0.20f);
        valueMap.put(ShipAPI.HullSize.DESTROYER, 0.25f);
        valueMap.put(ShipAPI.HullSize.CRUISER, 0.35f);
        valueMap.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.40f);

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

    protected void applyblueprintItemEffects() {
        if (blueprintItem != null) {
            BlueprintInstallableItemPlugin.BlueprintEffect effect = BlueprintInstallableItemPlugin.BLUEPRINT_EFFECTS.get(blueprintItem.getId());
            if (effect != null) {
                effect.apply(this, blueprintItem);
            }
        }
    }

    public void setblueprintItem(SpecialItemData blueprintItem) {
        if (blueprintItem == null && this.blueprintItem != null) {
            BlueprintInstallableItemPlugin.BlueprintEffect effect = BlueprintInstallableItemPlugin.BLUEPRINT_EFFECTS.get(this.blueprintItem.getId());
            if (effect != null) {
                effect.unapply(this);
            }
        }
        this.blueprintItem = blueprintItem;
    }

    public SpecialItemData getSpecialItem() {
        return blueprintItem;
    }

    public void setSpecialItem(SpecialItemData special) {
        blueprintItem = special;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        return blueprintItem == null &&
                data != null &&
                BlueprintInstallableItemPlugin.BLUEPRINT_EFFECTS.containsKey(data.getId());
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
        BlueprintInstallableItemPlugin.BlueprintEffect effect = BlueprintInstallableItemPlugin.BLUEPRINT_EFFECTS.get(blueprintItem.getId());
        effect.addItemDescription(text, blueprintItem, InstallableIndustryItemPlugin.InstallableItemDescriptionMode.INDUSTRY_TOOLTIP);
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
            if (BlueprintInstallableItemPlugin.BLUEPRINT_EFFECTS.containsKey(str)) {
                setblueprintItem(new SpecialItemData(str, null));
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
    }

    @Override
    protected void addPostUpkeepSection(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        super.addPostUpkeepSection(tooltip, mode);
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

        tooltip.addPara("At 100%% progress, install a %s you want to override with the new ship data.",
                opad, Misc.getHighlightColor(), new String[]{"blueprint of the same hull size"});

        tooltip.beginTable(marketFaction, 20f, "Ship Hull", 250f, "Total Progress", 140f);


        for (Map.Entry<String, Float> ship : researchProgressList.entrySet()) {

            String designation = Global.getSettings().getHullSpec(ship.getKey()).getNameWithDesignationWithDashClass();
            String progress = Math.round(ship.getValue() * 100) + "%";

            tooltip.addRow(designation, progress);
        }

        tooltip.addTable("You have not reverse engineered any ships yet.", 0, opad);
    }


    public boolean isTooltipExpandable() {
        return true;
    }

    public void createTooltip(Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        refreshResearchProgressList();
        currTooltipMode = mode;

        float pad = 3f;
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();
        Color grid = faction.getGridUIColor();
        Color bright = faction.getBrightUIColor();

        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();


        MarketAPI copy = market.clone();
        MarketAPI orig = market;

        //int numBeforeAdd = MiscIE.getNumIndustries(market);

        market = copy;
        boolean needToAddIndustry = !market.hasIndustry(getId());
        //addDialogMode = true;
        if (needToAddIndustry) market.getIndustries().add(this);

        if (mode != Industry.IndustryTooltipMode.NORMAL) {
            market.clearCommodities();
            for (CommodityOnMarketAPI curr : market.getAllCommodities()) {
                curr.getAvailableStat().setBaseValue(100);
            }
        }

//		if (addDialogMode) {
//			market.reapplyConditions();
//			apply();
//		}
        market.reapplyConditions();
        reapply();

        String type = "";
        if (isIndustry()) type = " - Industry";
        if (isStructure()) type = " - Structure";

        tooltip.addTitle(getCurrentName() + type, color);

        String desc = spec.getDesc();
        String override = getDescriptionOverride();
        if (override != null) {
            desc = override;
        }
        desc = Global.getSector().getRules().performTokenReplacement(null, desc, market.getPrimaryEntity(), null);

        tooltip.addPara(desc, opad);

//		Industry inProgress = MiscIE.getCurrentlyBeingConstructed(market);
//		if ((mode == IndustryTooltipMode.ADD_INDUSTRY && inProgress != null) ||
//				(mode == IndustryTooltipMode.UPGRADE && inProgress != null)) {
//			//tooltip.addPara("Another project (" + inProgress.getCurrentName() + ") in progress", bad, opad);
//			//tooltip.addPara("Already building: " + inProgress.getCurrentName() + "", bad, opad);
//			tooltip.addPara("Another construction in progress: " + inProgress.getCurrentName() + "", bad, opad);
//		}

        //tooltip.addPara("Type: %s", opad, gray, highlight, hullSize);
        if (isIndustry() && (mode == Industry.IndustryTooltipMode.ADD_INDUSTRY ||
                mode == Industry.IndustryTooltipMode.UPGRADE ||
                mode == Industry.IndustryTooltipMode.DOWNGRADE)
        ) {

            int num = Misc.getNumIndustries(market);
            int max = Misc.getMaxIndustries(market);


            // during the creation of the tooltip, the market has both the current industry
            // and the upgrade/downgrade. So if this upgrade/downgrade counts as an industry, it'd count double if
            // the current one is also an industry. Thus reduce num by 1 if that's the case.
            if (isIndustry()) {
                if (mode == Industry.IndustryTooltipMode.UPGRADE) {
                    for (Industry curr : market.getIndustries()) {
                        if (getSpec().getId().equals(curr.getSpec().getUpgrade())) {
                            if (curr.isIndustry()) {
                                num--;
                            }
                            break;
                        }
                    }
                } else if (mode == Industry.IndustryTooltipMode.DOWNGRADE) {
                    for (Industry curr : market.getIndustries()) {
                        if (getSpec().getId().equals(curr.getSpec().getDowngrade())) {
                            if (curr.isIndustry()) {
                                num--;
                            }
                            break;
                        }
                    }
                }
            }

            Color c = gray;
            c = Misc.getTextColor();
            Color h1 = highlight;
            if (num > max) {// || (num >= max && mode == IndustryTooltipMode.ADD_INDUSTRY)) {
                //c = bad;
                h1 = bad;
                num--;

                tooltip.addPara("Maximum number of industries reached", bad, opad);
            }
            //tooltip.addPara("Maximum of %s industries on a colony of this size. Currently: %s.",
//			LabelAPI label = tooltip.addPara("Maximum industries for a colony of this size: %s. Industries: %s. ",
//					opad, c, h1, "" + max, "" + num);
//			label.setHighlightColors(h2, h1);
        }

        addRightAfterDescriptionSection(tooltip, mode);

        if (mode.equals(Industry.IndustryTooltipMode.ADD_INDUSTRY) || !expanded) { //only display if either in add Industry or not expanded

            if (isDisrupted()) {
                int left = (int) getDisruptedDays();
                if (left < 1) left = 1;
                String days = "days";
                if (left == 1) days = "day";

                tooltip.addPara("Operations disrupted! %s " + days + " until return to normal function.",
                        opad, Misc.getNegativeHighlightColor(), highlight, "" + left);
            }

            if (DebugFlags.COLONY_DEBUG || market.isPlayerOwned()) {
                if (mode == Industry.IndustryTooltipMode.NORMAL) {
                    if (getSpec().getUpgrade() != null && !isBuilding()) {
                        tooltip.addPara("Click to manage or upgrade", Misc.getPositiveHighlightColor(), opad);
                    } else {
                        tooltip.addPara("Click to manage", Misc.getPositiveHighlightColor(), opad);
                    }
                    //tooltip.addPara("Click to manage", market.getFaction().getBrightUIColor(), opad);
                }
            }

            if (mode == Industry.IndustryTooltipMode.QUEUED) {
                tooltip.addPara("Click to remove or adjust position in queue", Misc.getPositiveHighlightColor(), opad);
                tooltip.addPara("Currently queued for construction. Does not have any impact on the colony.", opad);

                int left = (int) (getSpec().getBuildTime());
                if (left < 1) left = 1;
                String days = "days";
                if (left == 1) days = "day";
                tooltip.addPara("Requires %s " + days + " to build.", opad, highlight, "" + left);

                //return;
            } else if (!isFunctional() && mode == Industry.IndustryTooltipMode.NORMAL) {
                tooltip.addPara("Currently under construction and not producing anything or providing other benefits.", opad);

                int left = (int) (buildTime - buildProgress);
                if (left < 1) left = 1;
                String days = "days";
                if (left == 1) days = "day";
                tooltip.addPara("Requires %s more " + days + " to finish building.", opad, highlight, "" + left);
            }


            if (!isAvailableToBuild() &&
                    (mode == Industry.IndustryTooltipMode.ADD_INDUSTRY ||
                            mode == Industry.IndustryTooltipMode.UPGRADE ||
                            mode == Industry.IndustryTooltipMode.DOWNGRADE)) {
                String reason = getUnavailableReason();
                if (reason != null) {
                    tooltip.addPara(reason, bad, opad);
                }
            }

            boolean category = getSpec().hasTag(Industries.TAG_PARENT);

            if (!category) {
                int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
                String creditsStr = Misc.getDGSCredits(credits);
                if (mode == Industry.IndustryTooltipMode.UPGRADE || mode == Industry.IndustryTooltipMode.ADD_INDUSTRY) {
                    int cost = (int) getBuildCost();
                    String costStr = Misc.getDGSCredits(cost);

                    int days = (int) getBuildTime();
                    String daysStr = "days";
                    if (days == 1) daysStr = "day";

                    LabelAPI label = null;
                    if (mode == Industry.IndustryTooltipMode.UPGRADE) {
                        label = tooltip.addPara("%s and %s " + daysStr + " to upgrade. You have %s.", opad,
                                highlight, costStr, "" + days, creditsStr);
                    } else {
                        label = tooltip.addPara("%s and %s " + daysStr + " to build. You have %s.", opad,
                                highlight, costStr, "" + days, creditsStr);
                    }
                    label.setHighlight(costStr, "" + days, creditsStr);
                    if (credits >= cost) {
                        label.setHighlightColors(highlight, highlight, highlight);
                    } else {
                        label.setHighlightColors(bad, highlight, highlight);
                    }
                } else if (mode == Industry.IndustryTooltipMode.DOWNGRADE) {
                    float refundFraction = Global.getSettings().getFloat("industryRefundFraction");
                    int cost = (int) (getBuildCost() * refundFraction);
                    String refundStr = Misc.getDGSCredits(cost);

                    tooltip.addPara("%s refunded for downgrade.", opad, highlight, refundStr);
                }

                addPostDescriptionSection(tooltip, mode);

                if (!getIncome().isUnmodified()) {
                    int income = getIncome().getModifiedInt();
                    tooltip.addPara("Monthly income: %s", opad, highlight, Misc.getDGSCredits(income));
                    tooltip.addStatModGrid(250, 65, 10, pad, getIncome(), true, new TooltipMakerAPI.StatModValueGetter() {
                        public String getPercentValue(MutableStat.StatMod mod) {
                            return null;
                        }

                        public String getMultValue(MutableStat.StatMod mod) {
                            return null;
                        }

                        public Color getModColor(MutableStat.StatMod mod) {
                            return null;
                        }

                        public String getFlatValue(MutableStat.StatMod mod) {
                            return Misc.getWithDGS(mod.value) + Strings.C;
                        }
                    });
                }

                if (!getUpkeep().isUnmodified()) {
                    int upkeep = getUpkeep().getModifiedInt();
                    tooltip.addPara("Monthly upkeep: %s", opad, highlight, Misc.getDGSCredits(upkeep));
                    tooltip.addStatModGrid(250, 65, 10, pad, getUpkeep(), true, new TooltipMakerAPI.StatModValueGetter() {
                        public String getPercentValue(MutableStat.StatMod mod) {
                            return null;
                        }

                        public String getMultValue(MutableStat.StatMod mod) {
                            return null;
                        }

                        public Color getModColor(MutableStat.StatMod mod) {
                            return null;
                        }

                        public String getFlatValue(MutableStat.StatMod mod) {
                            return Misc.getWithDGS(mod.value) + Strings.C;
                        }
                    });
                }

                addPostUpkeepSection(tooltip, mode);

                addCurrentDeconstTooltip(tooltip, mode);

                boolean hasSupply = false;
                for (MutableCommodityQuantity curr : supply.values()) {
                    int qty = curr.getQuantity().getModifiedInt();
                    if (qty <= 0) continue;
                    hasSupply = true;
                    break;
                }
                boolean hasDemand = false;
                for (MutableCommodityQuantity curr : demand.values()) {
                    int qty = curr.getQuantity().getModifiedInt();
                    if (qty <= 0) continue;
                    hasDemand = true;
                    break;
                }

                float maxIconsPerRow = 10f;
                if (hasSupply) {
                    tooltip.addSectionHeading("Production", color, dark, Alignment.MID, opad);
                    tooltip.beginIconGroup();
                    tooltip.setIconSpacingMedium();
                    float icons = 0;
                    for (MutableCommodityQuantity curr : supply.values()) {
                        //if (qty <= 0) continue;

                        if (curr.getQuantity().getModifiedInt() > 0) {
                            tooltip.addIcons(market.getCommodityData(curr.getCommodityId()), curr.getQuantity().getModifiedInt(), IconRenderMode.NORMAL);
                        }

                        int plus = 0;
                        int minus = 0;
                        for (MutableStat.StatMod mod : curr.getQuantity().getFlatMods().values()) {
                            if (mod.value > 0) {
                                plus += (int) mod.value;
                            } else if (mod.desc != null && mod.desc.contains("shortage")) {
                                minus += (int) Math.abs(mod.value);
                            }
                        }
                        minus = Math.min(minus, plus);
                        if (minus > 0 && mode == Industry.IndustryTooltipMode.NORMAL) {
                            tooltip.addIcons(market.getCommodityData(curr.getCommodityId()), minus, IconRenderMode.DIM_RED);
                        }
                        icons += curr.getQuantity().getModifiedInt() + Math.max(0, minus);
                    }
                    int rows = (int) Math.ceil(icons / maxIconsPerRow);
                    rows = 3;
                    tooltip.addIconGroup(32, rows, opad);


                }
                //			else if (!isFunctional() && mode == IndustryTooltipMode.NORMAL) {
                //				tooltip.addPara("Currently under construction and not producing anything or providing other benefits.", opad);
                //			}

                addPostSupplySection(tooltip, hasSupply, mode);

                if (hasDemand || hasPostDemandSection(hasDemand, mode)) {
                    tooltip.addSectionHeading("Demand & effects", color, dark, Alignment.MID, opad);
                }
                if (hasDemand) {
                    tooltip.beginIconGroup();
                    tooltip.setIconSpacingMedium();
                    float icons = 0;
                    for (MutableCommodityQuantity curr : demand.values()) {
                        int qty = curr.getQuantity().getModifiedInt();
                        if (qty <= 0) continue;

                        CommodityOnMarketAPI com = orig.getCommodityData(curr.getCommodityId());
                        int available = com.getAvailable();

                        int normal = Math.min(available, qty);
                        int red = Math.max(0, qty - available);

                        if (mode != Industry.IndustryTooltipMode.NORMAL) {
                            normal = qty;
                            red = 0;
                        }
                        if (normal > 0) {
                            tooltip.addIcons(com, normal, IconRenderMode.NORMAL);
                        }
                        if (red > 0) {
                            tooltip.addIcons(com, red, IconRenderMode.DIM_RED);
                        }
                        icons += normal + Math.max(0, red);
                    }
                    int rows = (int) Math.ceil(icons / maxIconsPerRow);
                    rows = 3;
                    rows = 1;
                    tooltip.addIconGroup(32, rows, opad);
                }

                addPostDemandSection(tooltip, hasDemand, mode);


                if (!needToAddIndustry) {
                    //addAICoreSection(tooltip, AICoreDescriptionMode.TOOLTIP);
                    addInstalledItemsSection(mode, tooltip, expanded);
                }

                tooltip.addPara("*Shown production and demand values are already adjusted based on current market size and local conditions.", gray, opad);
            }
        }

        addShipProgressOverview(tooltip, mode, expanded);
        if (expanded) addCurrentDeconstTooltip(tooltip, mode);

        if (needToAddIndustry) {
            unapply();
            market.getIndustries().remove(this);
        }
        market = orig;
        if (!needToAddIndustry) {
            reapply();
        }
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
}

