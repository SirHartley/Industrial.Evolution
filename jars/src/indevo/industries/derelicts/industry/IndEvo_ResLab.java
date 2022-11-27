package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import indevo.utils.helper.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.*;
import indevo.items.IndEvo_EmptyForgeTemplateItemPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import indevo.items.installable.IndEvo_ForgeTemplateInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.ids.IndEvo_Items;
import indevo.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import indevo.utils.timers.IndEvo_newDayListener;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class IndEvo_ResLab extends IndEvo_BaseForgeTemplateUser implements IndEvo_newDayListener {

    boolean debug = false;
    private SpecialItemData current = null;

    private int daysPassed = 0;
    private int daysMax = 999;

    protected final String pickerSeed = "$IndEvo_ruinsPickerSeed";
    public static final String COMMODITY_KEY = "$IndEvo_LabBonusCommodityID";

    protected String bonusCommodityId1 = "none";
    protected String bonusCommodityId2 = "none";
    protected String bonusCommodityId3 = "none";

    private float bonusValue = 2f;

    private boolean isInit = false;

    protected void upgradeFinished(Industry previous) {

        sendBuildOrUpgradeMessage();
    }

    @Override
    public void apply() {
        super.apply();
        debug = Global.getSettings().isDevMode();
        bonusValue = getBonusValue();

        Global.getSector().getListenerManager().addListener(this, true);

        setAllCommodityBonuses();
        addSharedSubmarket();
    }

    @Override
    public void unapply() {
        super.unapply();

        unapplyCommodityBonuses();
        Global.getSector().getListenerManager().removeListener(this);
        removeSharedSubmarket();
    }

    private void autoFeed() {
        //autofeed
        if (getSpecialItem() == null) {
            if (!market.hasSubmarket(IndEvo_ids.SHAREDSTORAGE)) return;
            CargoAPI cargo = market.getSubmarket(IndEvo_ids.SHAREDSTORAGE).getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                if (stack.getPlugin() instanceof IndEvo_EmptyForgeTemplateItemPlugin && stack.getSpecialDataIfSpecial().getId().equals(IndEvo_Items.BROKENFORGETEMPLATE)) {
                    setSpecialItem(stack.getSpecialDataIfSpecial());
                    cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), 1);
                    Global.getSector().getCampaignUI().addMessage("An Ancient Laboratory has taken a %s from the industrial storage at %s.",
                            Global.getSettings().getColor("standardTextColor"), Global.getSettings().getSpecialItemSpec(stack.getSpecialDataIfSpecial().getId()).getName(), market.getName(), Misc.getHighlightColor(), Misc.getHighlightColor());
                    break;
                }
            }
        }
    }

    @Override
    public void onNewDay() {
        if (isFunctional()) {
            autoFeed();

            if (getSpecialItem() != null
                    && getSpecialItem().getId().equals(IndEvo_Items.BROKENFORGETEMPLATE)
                    && current == null) {
                initRepair();

            } else if (current != null) {
                if (daysPassed >= daysMax || debug) {
                    if (repairInstalledFT()) {
                        daysPassed = 0;
                        daysMax = 999;
                        current = null;
                    }
                } else {
                    daysPassed++;
                }
            }
        }
    }

    private void initRepair() {
        current = getSpecialItem();
        setSpecialItem(null);
        daysMax = getAiCoreIdNotNull().equals(Commodities.GAMMA_CORE) ? 10 : 20;

        Global.getSector().getCampaignUI().addMessage("Restoration of a %s has begun at %s.",
                Misc.getTextColor(), "Degraded Forge Template", market.getName(), Misc.getHighlightColor(), market.getFaction().getBrightUIColor());
    }

    private String getAiCoreIdNotNull() {
        if (getAICoreId() != null) {
            return getAICoreId();
        }
        return "none";
    }

    private int getBonusValue() {
        switch (getAiCoreIdNotNull()) {
            case Commodities.BETA_CORE:
                return 3;
            case Commodities.ALPHA_CORE:
                return 1;
            default:
                return 2;
        }
    }

    private void setAllCommodityBonuses() {
        if (!isInit) initBonusIds();

        if (isFunctional()) {
            for (Industry ind : market.getIndustries()) {
                MutableCommodityQuantity m1 = ind.getSupply(bonusCommodityId1);
                MutableCommodityQuantity m2 = ind.getSupply(bonusCommodityId2);
                MutableCommodityQuantity m3 = ind.getSupply(bonusCommodityId2);

                if (ind.getId().equals(IndEvo_ids.ADMANUF) && ind.getSpecialItem() != null) {
                    Pair<String, String> p = IndEvo_Items.getVPCCommodityIds(ind.getSpecialItem().getId());

                    if (p.one.equals(bonusCommodityId1) || p.two.equals(bonusCommodityId1))
                        m1.getQuantity().modifyFlat(getModId(), bonusValue, getNameForModifier());
                    if (p.one.equals(bonusCommodityId2) || p.two.equals(bonusCommodityId2))
                        m2.getQuantity().modifyFlat(getModId(), bonusValue, getNameForModifier());
                    if (p.one.equals(bonusCommodityId3) || p.two.equals(bonusCommodityId3))
                        m3.getQuantity().modifyFlat(getModId(), bonusValue, getNameForModifier());
                }

                if (m1.getQuantity().getModifiedInt() != 0f) {
                    m1.getQuantity().modifyFlat(getModId(), bonusValue, getNameForModifier());
                }

                //if not beta core
                if (!getAiCoreIdNotNull().equals(Commodities.BETA_CORE)) {
                    if (m2.getQuantity().getModifiedInt() != 0f) {
                        m2.getQuantity().modifyFlat(getModId(), bonusValue, getNameForModifier());
                    }

                    //if alpha core
                    if (getAiCoreIdNotNull().equals(Commodities.ALPHA_CORE)) {
                        if (m3.getQuantity().getModifiedInt() != 0f) {
                            m3.getQuantity().modifyFlat(getModId(), bonusValue, getNameForModifier());
                        }
                    }
                }
            }
        }
    }

    private void unapplyCommodityBonuses() {
        for (Industry ind : market.getIndustries()) {
            ind.getSupply(bonusCommodityId1).getQuantity().unmodify(getModId());
            ind.getSupply(bonusCommodityId2).getQuantity().unmodify(getModId());
            ind.getSupply(bonusCommodityId3).getQuantity().unmodify(getModId());
        }
    }

    private void initBonusIds() {
        MemoryAPI mem = market.getMemoryWithoutUpdate();

        if (mem.getBoolean(COMMODITY_KEY)) {
            bonusCommodityId1 = mem.getString(COMMODITY_KEY + "_" + 1);
            bonusCommodityId2 = mem.getString(COMMODITY_KEY + "_" + 2);
            bonusCommodityId3 = mem.getString(COMMODITY_KEY + "_" + 3);

        } else {
            List<String> commodityList = getVanillaCommodities();

            Random random = new Random(getPickerSeed());
            WeightedRandomPicker<String> commodityPicker = new WeightedRandomPicker<>(random);
            updatePickerSeed();

            for (String s : commodityList) {
                if (Global.getSettings().getCommoditySpec(s).getExportValue() > 0) commodityPicker.add(s, 1);
            }

            bonusCommodityId1 = commodityPicker.pickAndRemove();
            bonusCommodityId2 = commodityPicker.pickAndRemove();
            bonusCommodityId3 = commodityPicker.pickAndRemove();

            if (bonusCommodityId1 == null) bonusCommodityId1 = Commodities.SUPPLIES;
            if (bonusCommodityId2 == null) bonusCommodityId2 = Commodities.FUEL;
            if (bonusCommodityId3 == null) bonusCommodityId3 = Commodities.LUXURY_GOODS;

            mem.set(COMMODITY_KEY, true);
            mem.set(COMMODITY_KEY + "_" + 1, bonusCommodityId1);
            mem.set(COMMODITY_KEY + "_" + 2, bonusCommodityId2);
            mem.set(COMMODITY_KEY + "_" + 3, bonusCommodityId3);
        }

        isInit = true;
    }

    private static List<String> getVanillaCommodities() {
        final JSONArray csv;
        try {
            csv = Global.getSettings().loadCSV("data/campaign/commodities.csv");
        } catch (Exception ex) {
            // Not actually an error, just means that mod doesn't override the core CSV
            return Collections.emptyList();
        }

        final List<String> added = new ArrayList<>(csv.length());
        try {
            for (int i = 0; i < csv.length(); i++) {
                final JSONObject row = csv.getJSONObject(i);

                // Skip empty rows
                final String id = row.getString("id");
                if (id.isEmpty()) {
                    continue;
                }

                boolean nonecon = row.getString("tags").contains("nonecon");
                if(!nonecon) added.add(id);
            }
        } catch (Exception ex) {
            Global.getLogger(IndEvo_ResLab.class).error("Failed to parse", ex);
            return Collections.emptyList();
        }

        Collections.sort(added, String.CASE_INSENSITIVE_ORDER);
        return added;
    }

    private boolean repairInstalledFT() {
        if (Global.getSettings().getBoolean("reslab_autoDeliverToClosestDecon")) {
            MarketAPI target = IndEvo_IndustryHelper.getClosestMarketWithIndustry(market, IndEvo_ids.DECONSTRUCTOR);

            if (target != null) {
                CargoAPI c = IndEvo_IndustryHelper.getIndustrialStorageCargo(target);
                if (c != null) {
                    c.addSpecial(new SpecialItemData(IndEvo_Items.EMPTYFORGETEMPLATE, null), 1);
                    throwDeliveryMessage(market, target);
                    return true;
                }
            }
        }

        boolean toStorage = !Global.getSettings().getBoolean("IndEvo_derelictDeliverToGathering");

        MarketAPI gather = IndEvo_IndustryHelper.getMarketForStorage(market);
        MarketAPI target = toStorage ? market : gather;

        if (gather != null) {
            CargoAPI cargo = Misc.getStorageCargo(target);
            cargo.addSpecial(new SpecialItemData(IndEvo_Items.EMPTYFORGETEMPLATE, null), 1);
            throwDeliveryMessage(market, target);
            return true;
        }

        return false;
    }

    private void throwDeliveryMessage(MarketAPI from, MarketAPI to) {
        MessageIntel intel = new MessageIntel("A Forge Template has been %s at %s.", Misc.getTextColor(), new String[]{"repaired", from.getName()}, Misc.getHighlightColor(), from.getFaction().getBrightUIColor());
        intel.addLine("It has been delivered to " + to.getName() + ".", Misc.getTextColor());
        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "FTIcon"));
        intel.setSound(BaseIntelPlugin.getSoundStandardPosting());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, to);
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        if (mode == IndustryTooltipMode.NORMAL) {
            tooltip.addPara("Install a %s here to restore it.", 10f, Misc.getHighlightColor(), new String[]{"Degraded Forge Template"});
        }
    }

    private long getPickerSeed() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();

        if (!memory.contains(pickerSeed)) {
            memory.set(pickerSeed, new Random().nextLong());
        }

        return memory.getLong(pickerSeed);
    }

    private void updatePickerSeed() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        memory.set(pickerSeed, new Random().nextLong());
    }

    @Override
    protected String getDescriptionOverride() {
        if (currTooltipMode == null || currTooltipMode != IndustryTooltipMode.NORMAL) {
            return "Breaching the large bunker doors, your crew finds massive storehouses filled to the brim with almost alien machines. Their purpose is unknown.";
        } else
            return "According to some old logs, this was a facility researching the next generation of blueprints, along with some other, more minor topics. No standard Domain Blueprint seems to fit the equipment."
                    + "\n\nThere are frequent mentions of something called a Forge Template, and of other structures working on partner programs.";
    }

    @Override
    public java.util.List<InstallableIndustryItemPlugin> getInstallableItems() {
        ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<>();
        list.add(new IndEvo_ForgeTemplateInstallableItemPlugin(this));
        return list;
    }

    @Override
    protected void addPostUpkeepSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostUpkeepSection(tooltip, mode);
        addBonusTooltip(tooltip, mode);
        addCurrentProjectTooltip(tooltip, mode);
    }

    public void addCurrentProjectTooltip(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (!isBuilding() && isFunctional() && mode.equals(IndustryTooltipMode.NORMAL)) {

            FactionAPI marketFaction = market.getFaction(); //always get the player faction, for AI control options
            Color color = marketFaction.getBaseUIColor();
            Color dark = marketFaction.getDarkUIColor();
            float opad = 5.0F;

            tooltip.addSectionHeading("Current Project", color, dark, Alignment.MID, 10f);

            if (current != null) {

                SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(current.getId());
                TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48);

                text.addPara("Repairing: %s. Time remaining: %s. You will gain an %s from this.",
                        opad,
                        Misc.getHighlightColor(),
                        new String[]{spec.getName(),
                                daysMax - daysPassed + " days",
                                "empty Forge Template"});

                tooltip.addImageWithText(opad);
            } else {

                tooltip.addPara("No Forge Template is currently being repaired",
                        opad);
            }
        }
    }

    public void addBonusTooltip(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (isFunctional() && mode.equals(IndustryTooltipMode.NORMAL)) {

            FactionAPI marketFaction = market.getFaction(); //always get the player faction, for AI control options
            Color color = marketFaction.getBaseUIColor();
            Color dark = marketFaction.getDarkUIColor();
            float opad = 5.0F;

            tooltip.addSectionHeading("Structure Bonus", color, dark, Alignment.MID, 10f);

            if (bonusCommodityId1 != null && bonusCommodityId2 != null && bonusCommodityId3 != null) {
                ArrayList<CommoditySpecAPI> a = new ArrayList<>();
                a.add(Global.getSettings().getCommoditySpec(bonusCommodityId1));
                if (!getAiCoreIdNotNull().equals(Commodities.BETA_CORE)) {
                    a.add(Global.getSettings().getCommoditySpec(bonusCommodityId2));
                    if (getAiCoreIdNotNull().equals(Commodities.ALPHA_CORE)) {
                        a.add(Global.getSettings().getCommoditySpec(bonusCommodityId3));
                    }
                }

                for (CommoditySpecAPI specAPI : a) {
                    TooltipMakerAPI text1 = tooltip.beginImageWithText(specAPI.getIconName(), 24);

                    text1.addPara("Increases production for %s by %s.",
                            opad,
                            Misc.getHighlightColor(),
                            new String[]{specAPI.getName(),
                                    Math.round(bonusValue) + " units",
                            });
                    tooltip.addImageWithText(5f);
                }
            } else {

                tooltip.addPara("Bonus not set yet, or something is broken.",
                        opad);
            }
        }
    }

       /* alpha unlocks a third bonus, but reduces the increase to from 2 to 1
Beta removes the second bonus, but increases the increase on the first one to 3
Gamma reduces template refit time*/


    //AI-Core tooltips
    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Unlocks a %s, but decreases the bonus to %s.", 0f, Misc.getHighlightColor(), new String[]{"third commodity", "1 unit"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Unlocks a %s, but decreases the bonus to %s.", opad, Misc.getHighlightColor(), new String[]{"third commodity", "1 unit"});
        }
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Limits the bonus commodities to %s, but increases the bonus to %s.", 0f, Misc.getHighlightColor(), new String[]{"one", "3 units"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Limits the bonus commodities to %s, but increases the bonus to %s.", opad, Misc.getHighlightColor(), new String[]{"one", "3 units"});
        }
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Reduces Forge Template repair time by %s.", 0f, Misc.getHighlightColor(), new String[]{"50%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Reduces Forge Template repair time by %s.", opad, Misc.getHighlightColor(), new String[]{"50%"});
        }
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    @Override
    public boolean isLegalOnSharedSubmarket(CargoStackAPI stack) {
        if (!stack.isSpecialStack()) return false;
        return stack.getSpecialItemSpecIfSpecial().getId().equals(IndEvo_Items.BROKENFORGETEMPLATE);
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("Hull Forge: pulls %s from this storage to repair.", 10f, Misc.getHighlightColor(), "Degraded Forge Templates");
    }
}
