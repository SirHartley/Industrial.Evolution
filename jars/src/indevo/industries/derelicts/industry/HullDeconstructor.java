package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.items.EmptyForgeTemplateItemPlugin;
import indevo.items.ForgeTemplateItemPlugin;
import indevo.items.installable.ForgeTemplateInstallableItemPlugin;
import indevo.utils.helper.Misc;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.scripts.SubMarketAddOrRemovePlugin;
import indevo.utils.timers.NewDayListener;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.utils.helper.Misc.addOrIncrement;

public class HullDeconstructor extends BaseForgeTemplateUser implements NewDayListener {

    public static Logger log = Global.getLogger(HullDeconstructor.class);
    private boolean debug = false;

    public static final float GAMMA_CORE_UPKEEP_RED_MULT = 0.7f;
    public static final int BASE_QUALITY_LEVEL = 1;
    public static final int ALPHA_CORE_QUALITY_LEVEL = 2;
    public static final int BETA_CORE_QUALITY_LEVEL = 2;
    public static final int GAMMA_CORE_QUALITY_LEVEL = 0;

    public static final String HAS_INBUILT_LOG_MOD_TAG = "IndEvo_hasInbuiltLogMod";

    private ShipVariantAPI currentDeconShipVar = null;
    private int daysRequired = 999;
    private int daysPassed = 0;
    private int qualityLevel = BASE_QUALITY_LEVEL;

    public void apply() {
        super.apply(true);
        debug = Global.getSettings().isDevMode();

        Global.getSector().getListenerManager().addListener(this, true);

        if (isFunctional()) {
            Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.DECSTORAGE, false));
            refreshRequiredDays();
        }

        addSharedSubmarket();
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);

        if (currTooltipMode != IndustryTooltipMode.ADD_INDUSTRY) {
            Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.DECSTORAGE, true));
        }

        removeSharedSubmarket();
    }

    private void autoFeed() {
        //autofeed
        if (getSpecialItem() == null) {
            //auto-feed from storage
            if (!market.hasSubmarket(Ids.SHAREDSTORAGE)) return;

            CargoAPI cargo = market.getSubmarket(Ids.SHAREDSTORAGE).getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                if (stack.getPlugin() instanceof EmptyForgeTemplateItemPlugin && stack.getSpecialDataIfSpecial().getId().equals(ItemIds.EMPTYFORGETEMPLATE)) {
                    setSpecialItem(stack.getSpecialDataIfSpecial());
                    cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), 1);
                    Global.getSector().getCampaignUI().addMessage("A Hull Deconstructor has taken a %s from the industrial storage at %s.",
                            Global.getSettings().getColor("standardTextColor"), Global.getSettings().getSpecialItemSpec(stack.getSpecialDataIfSpecial().getId()).getName(), market.getName(), com.fs.starfarer.api.util.Misc.getHighlightColor(), com.fs.starfarer.api.util.Misc.getHighlightColor());
                    break;
                }
            }
        }
    }

    @Override
    public void onNewDay() {
        if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) == null
                || market.getSubmarket(Ids.DECSTORAGE) == null
                || !isFunctional())
            return;

        autoFeed();

        if (currentDeconShipVar == null
                && getSpecialItem() != null
                && getSpecialItem().getId().equals(ItemIds.EMPTYFORGETEMPLATE)) {

            //initDeconstruction starts deon, returns bool
            boolean successful = initDeconstruction();

            if (successful) {
                String name = currentDeconShipVar.getHullSpec().getNameWithDesignationWithDashClass();
                Global.getSector().getCampaignUI().addMessage("Deconstruction has begun for a %s at %s.",
                        com.fs.starfarer.api.util.Misc.getTextColor(), name, market.getName(), com.fs.starfarer.api.util.Misc.getHighlightColor(), market.getFaction().getBrightUIColor());
            }

        } else if (daysRequired <= daysPassed || (currentDeconShipVar != null && debug)) {

            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, currentDeconShipVar);
            Random random = new Random();

            //add printing defects D-Mod
            ForgeTemplateItemPlugin.addPrintDefectDMods(member, 0, random);
            if (getAiCoreIdNotNull().equals(Commodities.ALPHA_CORE)) addBuiltInLogisticsHullmods(1, member, random);

            //make new item
            String id = currentDeconShipVar.getHullSpec().getHullId();
            SpecialItemData newFT = ForgeTemplateItemPlugin.createForgeTemplateData(getCharges(currentDeconShipVar), id, currentDeconShipVar);
            ForgeTemplateItemPlugin.incrementForgeTemplateQualityLevel(newFT, qualityLevel);

            shipToCargo(newFT, 1);

            //don't reset before the message has been set, as it uses currentDeconShipVar for reporting!
            resetDeconstructionVariables();

        } else if (currentDeconShipVar != null) {
            daysPassed++;
        }
    }

    public static void addBuiltInLogisticsHullmods(int amount, FleetMemberAPI member, Random random) {
        Map<String, Float> weightMap = new HashMap<>();

        //slow boi
        if (member.getStats().getMaxBurnLevel().getModifiedInt() <= 7)
            addOrIncrement(weightMap, HullMods.AUGMENTEDENGINES, 3f);

        //Exploration
        String descriptionText = Global.getSettings().getDescription(member.getHullSpec().getDescriptionId(), Description.Type.SHIP).getText1().toLowerCase()
                + Global.getSettings().getDescription(member.getHullSpec().getDescriptionId(), Description.Type.SHIP).getText2().toLowerCase()
                + Global.getSettings().getDescription(member.getHullSpec().getDescriptionId(), Description.Type.SHIP).getText3().toLowerCase();
        String name = member.getHullSpec().getNameWithDesignationWithDashClass().toLowerCase();
        if (name.contains("explor") || name.contains("survey") || descriptionText.contains("explor") || descriptionText.contains("survey")) {
            addOrIncrement(weightMap, HullMods.SURVEYING_EQUIPMENT, 5f);
            addOrIncrement(weightMap, "hiressensors", 2.5f);
        }

        EnumSet<ShipHullSpecAPI.ShipTypeHints> hints = member.getHullSpec().getHints();

        //combat
        if (hints.isEmpty() || hints.contains(ShipHullSpecAPI.ShipTypeHints.COMBAT) || hints.contains(ShipHullSpecAPI.ShipTypeHints.CARRIER) || hints.contains(ShipHullSpecAPI.ShipTypeHints.PHASE)) {
            addOrIncrement(weightMap, HullMods.EFFICIENCY_OVERHAUL, 1f);
            addOrIncrement(weightMap, HullMods.SOLAR_SHIELDING, 1f);
            addOrIncrement(weightMap, HullMods.INSULATEDENGINE, 0.5f);
        }

        //haulers
        if (hints.contains(ShipHullSpecAPI.ShipTypeHints.FREIGHTER)) {
            addOrIncrement(weightMap, HullMods.EXPANDED_CARGO_HOLDS, 5f);
            addOrIncrement(weightMap, HullMods.INSULATEDENGINE, 1f);
        }

        if (hints.contains(ShipHullSpecAPI.ShipTypeHints.TANKER)) {
            addOrIncrement(weightMap, HullMods.AUXILIARY_FUEL_TANKS, 5f);
        }

        if (hints.contains(ShipHullSpecAPI.ShipTypeHints.LINER) || hints.contains(ShipHullSpecAPI.ShipTypeHints.TRANSPORT)) {
            addOrIncrement(weightMap, HullMods.ADDITIONAL_BERTHING, 5f);
        }

        //civ
        if (member.getVariant().hasHullMod(HullMods.CIVGRADE))
            addOrIncrement(weightMap, HullMods.MILITARIZED_SUBSYSTEMS, 5f);
        else weightMap.remove(HullMods.MILITARIZED_SUBSYSTEMS);

        //general
        addOrIncrement(weightMap, HullMods.EFFICIENCY_OVERHAUL, 1f);

        WeightedRandomPicker<String> picker = new WeightedRandomPicker();
        for (Map.Entry<String, Float> e : weightMap.entrySet()) {
            picker.add(e.getKey(), e.getValue());
        }

        for (int i = 0; i < amount; i++) {
            String modID = picker.pick(random);

            List<String> inBuiltHullMods = new ArrayList<>(member.getVariant().getHullMods());
            inBuiltHullMods.removeAll(member.getVariant().getNonBuiltInHullmods());

            if (!inBuiltHullMods.contains(modID)) {
                member.getVariant().addPermaMod(modID, true);
            } else {
                picker.remove(modID);
                i--;
            }

            if (picker.isEmpty()) break;
        }

        member.getVariant().addTag(HAS_INBUILT_LOG_MOD_TAG);
        member.updateStats();
    }


    public ShipVariantAPI getCurrentDeconShipVar() {
        return currentDeconShipVar;
    }

    public void setCurrentDeconShipVar(ShipVariantAPI currentDeconShipVar) {
        this.currentDeconShipVar = currentDeconShipVar;
    }

    private void shipToCargo(SpecialItemData specialItem, int quantity) {
        if (Settings.getBoolean(Settings.HULLDECON_AUTO_DELIVER_TO_CLOSEST_FORGE)) {
            MarketAPI target = Misc.getClosestMarketWithIndustry(market, Ids.HULLFORGE);

            if (target != null) {
                CargoAPI c = Misc.getIndustrialStorageCargo(target);
                if (c != null) {
                    c.addSpecial(specialItem, quantity);
                    throwDeliveryMessage(market, target);
                    setSpecialItem(null);
                    return;
                }
            }
        }

        boolean toStorage = !Settings.getBoolean(Settings.DERELICT_DELIVER_TO_GATHERING);

        MarketAPI gather = market.getFaction().getProduction().getGatheringPoint();
        MarketAPI target = toStorage ? market : gather;

        CargoAPI cargo = Misc.getStorageCargo(target);
        if (cargo != null) {
            cargo.addSpecial(specialItem, quantity);
            throwDeliveryMessage(market, target);
            setSpecialItem(null);
        }
    }

    private void throwDeliveryMessage(MarketAPI from, MarketAPI to) {
        MessageIntel intel = new MessageIntel("Deconstruction of the %s has finished at %s.",
                com.fs.starfarer.api.util.Misc.getTextColor(),
                new String[]{currentDeconShipVar.getHullSpec().getNameWithDesignationWithDashClass(), from.getName()},
                com.fs.starfarer.api.util.Misc.getHighlightColor(),
                from.getFaction().getColor());

        intel.addLine(BaseIntelPlugin.BULLET + "A Forge Template with %s has been created.",
                com.fs.starfarer.api.util.Misc.getTextColor(),
                new String[]{getCharges(currentDeconShipVar) + " charges"},
                com.fs.starfarer.api.util.Misc.getHighlightColor());

        intel.addLine(BaseIntelPlugin.BULLET + "It has been delivered to %s.",
                com.fs.starfarer.api.util.Misc.getTextColor(),
                new String[]{to.getName()},
                to.getFaction().getBrightUIColor());

        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "revBP"));
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, to);
        intel.setSound(BaseIntelPlugin.getSoundMinorMessage());
    }

    private int getCharges(ShipVariantAPI variant) {
        int bonus = 0;
        if (getAiCoreIdNotNull().equals(Commodities.BETA_CORE)) {
            bonus++;
        }

        return getRequiredDaysForHull(variant) > 35 ? 2 + bonus : 3 + bonus;
    }

    private boolean initDeconstruction() {
        CargoAPI decStorage = market.getSubmarket(Ids.DECSTORAGE).getCargo();
        if (decStorage.getMothballedShips().getMembersListCopy().isEmpty()) return false;

        FleetMemberAPI ship = decStorage.getMothballedShips().getMembersListCopy().get(0);
        ShipVariantAPI shipVar = Misc.stripShipToCargoAndReturnVariant(ship, market);

        decStorage.getMothballedShips().removeFleetMember(ship); //remove ship from storage
        currentDeconShipVar = shipVar;
        refreshRequiredDays();

        return true;
    }

    private void resetDeconstructionVariables() {
        daysRequired = 999;
        daysPassed = 0;
        currentDeconShipVar = null;
    }

    private int getRequiredDaysForHull(ShipVariantAPI ship) {
        Map<ShipAPI.HullSize, Integer> dayMap = new HashMap<>();
        dayMap.put(ShipAPI.HullSize.FRIGATE, Settings.getInt(Settings.HULLDECON_DAYS_FRIGATE));
        dayMap.put(ShipAPI.HullSize.DESTROYER, Settings.getInt(Settings.HULLDECON_DAYS_DESTROYER));
        dayMap.put(ShipAPI.HullSize.CRUISER, Settings.getInt(Settings.HULLDECON_DAYS_CRUISER));
        dayMap.put(ShipAPI.HullSize.CAPITAL_SHIP, Settings.getInt(Settings.HULLDECON_DAYS_CAPITAL_SHIP));

        return dayMap.get(ship.getHullSize());
    }

    private void refreshRequiredDays() {
        if (currentDeconShipVar != null) {
            daysRequired = getRequiredDaysForHull(currentDeconShipVar);
        }
    }

    @Override
    public java.util.List<InstallableIndustryItemPlugin> getInstallableItems() {
        ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<>();

        if (currentDeconShipVar == null) {
            list.add(new ForgeTemplateInstallableItemPlugin(this));
        }
        return list;
    }

    private String getAiCoreIdNotNull() {
        if (getAICoreId() != null) {
            return getAICoreId();
        }
        return "none";
    }

    @Override
    protected String getDescriptionOverride() {
        if (currTooltipMode == null || currTooltipMode != IndustryTooltipMode.NORMAL) {
            return "Your engineers are baffled at the sight of massive arrays looking to be nothing other than modified antimatter blasters, arranged in a ring of titanic proportions. Its purpose is unknown.";
        } else
            return "Judging by the surviving marks and some working terminals, this installation is an experimental domain compound researching ship digitization. No standard Domain Blueprint seems to fit the equipment."
                    + "\n\nThere are frequent mentions of something called a Forge Template, and of other structures working on partner programs.";
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);

        if (mode == IndustryTooltipMode.NORMAL) {
            tooltip.addPara("Put a ship into the %s to deconstruct it, and add construction data to an %s.", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), new String[]{"Deconstruction Storage", "Empty Forge Template"});
        }
    }

    @Override
    protected void addPostUpkeepSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostUpkeepSection(tooltip, mode);
        addCurrentDeconstTooltip(tooltip, mode);
    }

    public void addCurrentDeconstTooltip(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (!isBuilding() && isFunctional() && mode.equals(IndustryTooltipMode.NORMAL)) {

            FactionAPI marketFaction = market.getFaction(); //always get the player faction, for AI control options
            Color color = marketFaction.getBaseUIColor();
            Color dark = marketFaction.getDarkUIColor();
            float opad = 5.0F;

            tooltip.addSectionHeading("Current Project", color, dark, Alignment.MID, 10f);

            if (currentDeconShipVar != null) {
                TooltipMakerAPI text = tooltip.beginImageWithText(currentDeconShipVar.getHullSpec().getSpriteName(), 48);

                text.addPara("Deconstructing: %s. Time remaining: %s. You will gain a Forge Template with %s from this.",
                        opad,
                        com.fs.starfarer.api.util.Misc.getHighlightColor(),
                        new String[]{currentDeconShipVar.getHullSpec().getNameWithDesignationWithDashClass(),
                                Math.max(daysRequired - daysPassed, 0) + " days",
                                getCharges(currentDeconShipVar) + " charges"});

                tooltip.addImageWithText(opad);
            } else {

                tooltip.addPara("No ship is currently being deconstructed.",
                        opad);
            }
        }
    }

    /*decon:
        gamma reduce by 1 (and reduce upkeep while no ship is deconned)
        beta  increase by 1, add one additional charge to template
        alpha increase by 1, bake in a random logistics hullmod */

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String coreHighlights = StringHelper.getString(getId(), "aCoreHighlights");
        String effect = StringHelper.getStringAndSubstituteToken(getId(), "aCoreEffect", "$aCoreHighlights", coreHighlights);

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, coreHighlights);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, coreHighlights);
        }
    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "bCoreAssigned" + suffix);
        String coreHighlights = StringHelper.getString(getId(), "bCoreHighlights");
        String effect = StringHelper.getStringAndSubstituteToken(getId(), "bCoreEffect", "$bCoreHighlights", coreHighlights);

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, coreHighlights);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, coreHighlights);
        }
    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "gCoreAssigned" + suffix);
        String coreHighlights = StringHelper.getString(getId(), "gCoreHighlights");
        String effect = StringHelper.getStringAndSubstituteToken(getId(), "gCoreEffect", "$gCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{StringHelper.getAbsPercentString(GAMMA_CORE_UPKEEP_RED_MULT, true), coreHighlights};

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        qualityLevel = ALPHA_CORE_QUALITY_LEVEL;
    }

    @Override
    protected void applyBetaCoreModifiers() {
        qualityLevel = BETA_CORE_QUALITY_LEVEL;
    }

    @Override
    protected void applyGammaCoreModifiers() {
        qualityLevel = GAMMA_CORE_QUALITY_LEVEL;
    }

    @Override
    protected void applyNoAICoreModifiers() {
        qualityLevel = BASE_QUALITY_LEVEL;
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
        String name;

        switch (Misc.getAiCoreIdNotNull(this)) {
            case Commodities.GAMMA_CORE:
                name = StringHelper.getString("IndEvo_AICores", "gCoreStatModAssigned");

                if (currentDeconShipVar == null)
                    this.getUpkeep().modifyMult("ind_core", GAMMA_CORE_UPKEEP_RED_MULT, name);
                break;
            default:
                getUpkeep().unmodifyMult("ind_core");
        }
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    @Override
    public boolean isLegalOnSharedSubmarket(CargoStackAPI stack) {
        if (!stack.isSpecialStack()) return false;
        return stack.getSpecialItemSpecIfSpecial().getId().equals(ItemIds.EMPTYFORGETEMPLATE);
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("Hull Deconstructor: pulls %s from this storage to use.", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), "Empty Forge Templates");
    }
}
