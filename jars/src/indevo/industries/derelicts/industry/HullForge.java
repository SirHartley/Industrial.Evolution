package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.items.ForgeTemplateItemPlugin;
import indevo.items.installable.ForgeTemplateInstallableItemPlugin;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.timers.NewDayListener;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.utils.helper.IndustryHelper.addOrIncrement;

public class HullForge extends BaseForgeTemplateUser implements NewDayListener {

    //ship starts at 0, a forge adds the mod level it's at and then applies D-mods accordingly

    /*ship quality override
    quality mod (0 = perfect, 4 = shit)
    quality starts at 0
    the lower the quality mod, the less chance for a D-mod to get applied
    printing defects:
        0 = none
        1 = Light
        2 = med
        3 = heavy
        4 = heavy

        all templates start at 1

        decon:
        gamma reduce by 1 (and reduce upkeep while no ship is deconned)
        beta  increase by 1, add one additional charge to template
        alpha increase by 1, bake in a random logi hullmod (check which ones the player uses, if pool is too small, check if hull is exploration, if no, use random)

        forge.
        gamma reduce by 1 (and reduce upkeep while no ship is built)
        beta increase by 1, Chance to get an empty instead of a degraded template once used up
        alpha increase by 1, bake in two random normal hullmods (Pick from any of the base variants, if pool is too small, pick random)

    * when applying the dmods, use the quality override as chance for a D-mod to get applied
    * 0 = 0%
    * 1 = 25%
    * 2 = 50%
    * 3 = 75%
    * 4 = 100%
    *
    * at base 3 D-Mods per Industry (1 defects and 2 randoms)
    * means: it always gets printing defects except at 0, but the remaining hullmods have the chance applied
    * */

    public static final Logger log = Global.getLogger(HullForge.class);
    private boolean debug = false;
    private final Random random = new Random();

    public static final float GAMMA_CORE_UPKEEP_RED_MULT = 0.7f;
    public static final float EMPTY_FORGE_TEMPLATE_CHANCE = 0.25f;
    public static final float BETA_CORE_COST_RED_MULT = 0.70f;

    public static final int EXTRA_BUILTIN_HULLMOD_COUNT = 2;
    public static final int BASE_QUALITY_LEVEL = 1;
    public static final int ALPHA_CORE_QUALITY_LEVEL = 2;
    public static final int BETA_CORE_QUALITY_LEVEL = 2;
    public static final int GAMMA_CORE_QUALITY_LEVEL = 0;

    private int daysRequired = 999;
    private int daysPassed = 0;
    private ShipHullSpecAPI currentShip = null;
    private int qualityLevel = BASE_QUALITY_LEVEL;

    public void apply() {
        super.apply(true);
        debug = Global.getSettings().isDevMode();

        Global.getSector().getListenerManager().addListener(this, true);
        addSharedSubmarket();
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);
        removeSharedSubmarket();
    }

    private void autoFeed() {
        //autofeed
        if (getSpecialItem() == null) {
            if (!market.hasSubmarket(Ids.SHAREDSTORAGE)) return;
            CargoAPI cargo = market.getSubmarket(Ids.SHAREDSTORAGE).getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                if (stack.getPlugin() instanceof ForgeTemplateItemPlugin) {
                    setSpecialItem(stack.getSpecialDataIfSpecial());
                    cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), 1);
                    Global.getSector().getCampaignUI().addMessage("A Hull Forge has taken a %s from the industrial storage at %s.",
                            Global.getSettings().getColor("standardTextColor"), Global.getSettings().getSpecialItemSpec(stack.getSpecialDataIfSpecial().getId()).getName(), market.getName(), Misc.getHighlightColor(), Misc.getHighlightColor());
                    break;
                }
            }
        }
    }

    @Override
    public void onNewDay() {
        if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) == null
                || !isFunctional())
            return;

        autoFeed();

        if (getSpecialItem() != null
                && getSpecialItem().getId().contains(ItemIds.FORGETEMPLATE + "_")
                && currentShip == null) {

            boolean successful = initConstruction();

            if (successful) {
                String name = currentShip.getNameWithDesignationWithDashClass();
                Global.getSector().getCampaignUI().addMessage("A Hull Forge has begun automated construction for a %s at %s.",
                        Misc.getTextColor(), name, market.getName(), Misc.getHighlightColor(), market.getFaction().getBrightUIColor());
            }

        } else if (currentShip != null) {
            if (daysRequired <= daysPassed || debug) {
                boolean toStorage = !Settings.DERELICT_DELIVER_TO_GATHERING;

                MarketAPI gather = IndustryHelper.getMarketForStorage(market);
                MarketAPI target = toStorage ? market : gather;

                //this prints the ship:
                boolean successful = printShip(target);

                if (successful) {
                    float cost = getConstructionCost(currentShip);
                    addCompletedMessage(cost, target);
                    chargeCosts(cost);

                    //don't reset before the message has been set, as it uses currentDeconShipVar for reporting!
                    resetConstructionVariables();

                } else {
                    Global.getSector().getCampaignUI().addMessage("Could not deliver a ship at %s, as there was %s found.",
                            Misc.getTextColor(), market.getName(), "no eligible storage", Misc.getNegativeHighlightColor(), market.getFaction().getBrightUIColor());
                }

                depositOrShipTemplate();

            } else {
                daysPassed++;
            }
        }
    }

    private void depositOrShipTemplate() {
        //deliver the chip back to home if burnt
        if (getSpecialItem().getId().equals(ItemIds.BROKENFORGETEMPLATE)) {

            //ai core effect
            String add = "";
            String targetIndustryId = Ids.LAB;

            if (getAiCoreIdNotNull().equals(Commodities.BETA_CORE) && random.nextFloat() <= EMPTY_FORGE_TEMPLATE_CHANCE) {
                //set item to emptFT
                setSpecialItem(new SpecialItemData(ItemIds.EMPTYFORGETEMPLATE, null));
                targetIndustryId = Ids.DECONSTRUCTOR;
                add = " It remains in workable condition due to beta core presence.";
            }

            //ship item to target
            if (Settings.HULLFORGE_AUTO_DELIVER_TO_CLOSEST_LAB) {
                MarketAPI target = IndustryHelper.getClosestMarketWithIndustry(market, targetIndustryId);

                if (target != null) {
                    CargoAPI c = IndustryHelper.getIndustrialStorageCargo(target);
                    if (c != null) {
                        c.addSpecial(getSpecialItem(), 1);
                        setSpecialItem(null);
                        throwDeliveryMessage(market, target, add);
                        return;
                    }
                }
            }

            //else deposit in target cargo
            boolean toStorage = !Settings.DERELICT_DELIVER_TO_GATHERING;

            MarketAPI gather = market.getFaction().getProduction().getGatheringPoint();
            MarketAPI target = toStorage ? market : gather;

            CargoAPI cargo = Misc.getStorageCargo(target);
            if (cargo != null) {
                cargo.addSpecial(getSpecialItem(), 1);
                throwDeliveryMessage(market, target, add);
                setSpecialItem(null);
            }

            //deposit template if not on autobuild
        } else if (!Settings.HULLFORGE_AUTO_QUEUE_SHIPS_UNTIL_EMPTY) {
            if (market.hasSubmarket(Submarkets.SUBMARKET_STORAGE)) {
                market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addSpecial(getSpecialItem(), 1);
                setSpecialItem(null);
            }
        }
    }

    private void throwDeliveryMessage(MarketAPI from, MarketAPI to, String add) {
        Global.getSector().getCampaignUI().addMessage("A Forge Template has degraded at %s - it has been deposited into storage at %s." + add,
                Misc.getTextColor(), from.getName(), to.getName(), Misc.getHighlightColor(), market.getFaction().getBrightUIColor());

    }

    private boolean initConstruction() {
        if (Global.getSettings().getHullSpec(getSpecialItem().getData()) != null) {
            currentShip = Global.getSettings().getHullSpec(getSpecialItem().getData());
            daysRequired = getRequiredDaysForHull(currentShip);
            return true;
        }
        return false;
    }

    private void resetConstructionVariables() {
        currentShip = null;
        daysRequired = 999;
        daysPassed = 0;
    }

    public ShipHullSpecAPI getCurrentShip() {
        return currentShip;
    }

    public void setCurrentShip(ShipHullSpecAPI currentShip) {
        this.currentShip = currentShip;
    }

    private int getRequiredDaysForHull(ShipHullSpecAPI ship) {
        Map<ShipAPI.HullSize, Integer> dayMap = new HashMap<>();
        dayMap.put(ShipAPI.HullSize.FRIGATE, Settings.HULLFORGE_DAYS_FRIGATE);
        dayMap.put(ShipAPI.HullSize.DESTROYER, Settings.HULLFORGE_DAYS_DESTROYER);
        dayMap.put(ShipAPI.HullSize.CRUISER, Settings.HULLFORGE_DAYS_CRUISER);
        dayMap.put(ShipAPI.HullSize.CAPITAL_SHIP, Settings.HULLFORGE_DAYS_CAPITAL_SHIP);

        return dayMap.get(ship.getHullSize());
    }

    private boolean printShip(MarketAPI toMarket) {
        if (toMarket != null) {
            SpecialItemData data = getSpecialItem();
            int ftQualityLevel = ForgeTemplateItemPlugin.getForgeTemplateQualityLevel(data) + qualityLevel;

            FleetMemberAPI ship = ForgeTemplateItemPlugin.createNakedFleetMemberFromForgeTemplate(data);
            ForgeTemplateItemPlugin.addPrintDefectDMods(ship, ftQualityLevel, random);

            if (getAiCoreIdNotNull().equals(Commodities.ALPHA_CORE)) {
                if (market.getName().toLowerCase().equals("eurobeat") || Global.getSettings().getBoolean("IndEvo_ToggleMagicRandomSelector"))
                    addRandomBuiltInHullmods(EXTRA_BUILTIN_HULLMOD_COUNT, ship);
                else addBuiltInHullmods(EXTRA_BUILTIN_HULLMOD_COUNT, ship, random);
            }

            toMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().getMothballedShips().addFleetMember(ship);

            //reduce charge count
            setSpecialItem(ForgeTemplateItemPlugin.incrementForgeTemplateData(getSpecialItem(), -1));
        } else {
            return false;
        }

        return true;
    }

    private static boolean isValidModularHullMod(String id) {
        HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);

        int i = 0;
        if (spec.isHiddenEverywhere()) i++;
        if (spec.isHidden()) i++;
        if (spec.getUITags().contains("Logistics")) i++;
        if (spec.getTags().contains("dmod")) i++;
        if (spec.getTags().contains(Tags.HULLMOD_NO_BUILD_IN)) i++;
        if (spec.getTags().isEmpty()) i++;

        return i == 0;
    }

    private static boolean isValidRandomHullMod(String id) {
        HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);

        int i = 0;
        if (spec.isHiddenEverywhere()) i++;
        if (spec.getUITags().contains("Logistics")) i++;
        if (spec.getTags().contains("dmod")) i++;

        return i == 0;
    }

    public static void addRandomBuiltInHullmods(int amount, FleetMemberAPI member) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker();
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (isValidRandomHullMod(spec.getId())) picker.add(spec.getId());
        }

        for (int i = 0; i < amount; i++) {
            String modID = picker.pick();

            if (!member.getVariant().hasHullMod(modID)) {
                member.getVariant().addPermaMod(modID, true);

            } else {
                picker.remove(modID);
                i--;
            }
        }

        compensateInbuilts(member, amount);
        member.updateStats();
    }

    public static void addBuiltInHullmods(int amount, FleetMemberAPI member, Random random) {
        Map<String, Float> weightMap = new HashMap<>();

        //get all variant hull mods
        List<String> varList = Global.getSettings().getHullIdToVariantListMap().getList(member.getHullId());
        for (String variantId : varList) {
            for (String hullModId : Global.getSettings().getVariant(variantId).getHullMods()) {
                if (isValidModularHullMod(hullModId)) addOrIncrement(weightMap, hullModId, 0.8f);
            }
        }

        //check player fleet
        for (FleetMemberAPI fleetMember : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (fleetMember.getHullId().equals(member.getHullId())) {
                for (String hullModId : fleetMember.getVariant().getHullMods()) {
                    if (isValidModularHullMod(hullModId)) addOrIncrement(weightMap, hullModId, 0.5f);
                }
            }
        }

        //check player colony inventory and abandoned stations
        for (String factionID : new String[]{Factions.PLAYER, Factions.NEUTRAL}) {
            for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getFaction(factionID))) {
                if (Misc.getStorage(market) == null) continue;

                Misc.getStorage(market).getCargo().initMothballedShips(Factions.PLAYER);

                for (FleetMemberAPI fleetMember : Misc.getStorage(market).getCargo().getMothballedShips().getMembersListCopy()) {
                    if (fleetMember.getHullId().equals(member.getHullId())) {
                        for (String hullModId : fleetMember.getVariant().getHullMods()) {
                            if (isValidModularHullMod(hullModId)) addOrIncrement(weightMap, hullModId, 0.5f);
                        }
                    }
                }
            }
        }

        List<String> hmList = new ArrayList<>();
        hmList.add(HullMods.AUXILIARY_THRUSTERS);
        hmList.add(HullMods.REINFORCEDHULL);
        hmList.add(HullMods.FLUXBREAKERS);
        hmList.add(HullMods.HARDENED_SUBSYSTEMS);
        hmList.add(HullMods.FLUX_DISTRIBUTOR);
        hmList.add(HullMods.FLUX_COIL);
        hmList.add(HullMods.ECM);

        for (String s : hmList) {
            if (!weightMap.containsKey(s)) weightMap.put(s, 0.3f);
        }

        weightMap.remove(HullMods.DEDICATED_TARGETING_CORE);

        //civ
        if (member.getVariant().hasHullMod(HullMods.CIVGRADE))
            addOrIncrement(weightMap, HullMods.MILITARIZED_SUBSYSTEMS, 5f);
        else weightMap.remove(HullMods.MILITARIZED_SUBSYSTEMS);

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
        }

        compensateInbuilts(member, amount);
        member.updateStats();
    }

    public static final String COMPENSATE_1 = "IndEvo_comp1";
    public static final String COMPENSATE_2 = "IndEvo_comp2";
    public static final String COMPENSATE_3 = "IndEvo_comp3";

    private static void compensateInbuilts(FleetMemberAPI member, int amt) {
        String compensation = "";
        if (amt == 1) compensation = COMPENSATE_1;
        if (amt == 2)
            compensation = member.getVariant().hasTag(HullDeconstructor.HAS_INBUILT_LOG_MOD_TAG) ? COMPENSATE_3 : COMPENSATE_2;

        member.getVariant().addTag(compensation);
    }

    private float getConstructionCost(ShipHullSpecAPI ship) {
        float bonus = getAiCoreIdNotNull().equals(Commodities.BETA_CORE) ? BETA_CORE_COST_RED_MULT : 1f;
        return (ship.getBaseValue() * 0.2f) * bonus;
    }

    private void chargeCosts(float cost) {
        MonthlyReport report = SharedData.getData().getCurrentReport();
        MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
        MonthlyReport.FDNode mNode = report.getNode(marketsNode, market.getId());
        MonthlyReport.FDNode indNode = report.getNode(mNode, "industries");
        MonthlyReport.FDNode iNode = report.getNode(indNode, getId());

        iNode.upkeep += cost;
    }

    @Override
    protected String getDescriptionOverride() {
        if (currTooltipMode == null || currTooltipMode != IndustryTooltipMode.NORMAL) {
            return "Your Engineers are confronted by what seems to resemble a massive nanoforge encased in an equally large installation. its purpose is unknown.";
        } else if (market.getName().toLowerCase().equals("eurobeat") || Global.getSettings().getBoolean("IndEvo_ToggleMagicRandomSelector")) {
            return "The data remaining on the semi functional servers indicate this installation as an experimental domain compound researching automated ship hull creation. No standard Domain Blueprint seems to fit the equipment."
                    + "\n\nThere are frequent mentions of something called a Forge Template, and of other structures working on partner programs.\n\n" +
                    "Upbeat music emanates from the console, but the exact track is unknown. The amount of car crashes on the colony has drastically increased ever since this started.";
        } else
            return "The data remaining on the semi functional servers indicate this installation as an experimental domain compound researching automated ship hull creation. No standard Domain Blueprint seems to fit the equipment."
                    + "\n\nThere are frequent mentions of something called a Forge Template, and of other structures working on partner programs.";

    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        if (mode == IndustryTooltipMode.NORMAL) {
            tooltip.addPara("Install a %s into this structure to construct ships.", 10f, Misc.getHighlightColor(), new String[]{"Forge Template"});
        }
    }

    @Override
    public java.util.List<InstallableIndustryItemPlugin> getInstallableItems() {

        ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<>();
        if (currentShip == null) {
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

            if (currentShip != null) {
                TooltipMakerAPI text = tooltip.beginImageWithText(currentShip.getSpriteName(), 48);

                text.addPara("Constructing: %s. Time remaining: %s. This will cost %s in materials.",
                        opad,
                        Misc.getHighlightColor(),
                        new String[]{currentShip.getNameWithDesignationWithDashClass(),
                                Math.max(daysRequired - daysPassed, 0) + " days",
                                Misc.getDGSCredits(getConstructionCost(currentShip))});

                tooltip.addImageWithText(opad);
            } else {

                tooltip.addPara("No ship is currently being built.",
                        opad);
            }
        }
    }

    private void addCompletedMessage(float cost, MarketAPI target) {
        int c = ForgeTemplateItemPlugin.getForgeTemplateCharges(getSpecialItem().getId());
        String s = c > 1 ? c + " charges" : c + " charge";
        s = c < 1 ? "no charges" : s;

        MessageIntel intel = new MessageIntel("A Hull Forge at %s has finished assembling a %s .",
                Misc.getTextColor(),
                new String[]{market.getName(), currentShip.getNameWithDesignationWithDashClass()},
                market.getFaction().getBrightUIColor(),
                Misc.getHighlightColor());

        intel.addLine(BaseIntelPlugin.BULLET + "It has been delivered to %s",
                Misc.getTextColor(),
                new String[]{target.getName()},
                target.getFaction().getBrightUIColor());

        intel.addLine(BaseIntelPlugin.BULLET + "Material costs: %s",
                Misc.getTextColor(),
                new String[]{Misc.getDGSCredits(cost)},
                Misc.getHighlightColor());

        intel.addLine(BaseIntelPlugin.BULLET + "%s remaining on the Forge Template.",
                Misc.getTextColor(),
                new String[]{s},
                Misc.getHighlightColor());

        intel.setIcon(currentShip.getSpriteName());
        Global.getSector().getCampaignUI().addMessage(intel);
        intel.setSound(BaseIntelPlugin.getSoundMinorMessage());
    }

   /* forge.
        gamma reduce by 1 (and reduce upkeep while no ship is built)
        beta increase by 1, Chance to get an empty instead of a degraded template once used up, reduce cost
        alpha increase by 1, bake in two random normal hullmods */

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
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
        Color highlight = Misc.getHighlightColor();

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
        Color highlight = Misc.getHighlightColor();

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

        switch (IndustryHelper.getAiCoreIdNotNull(this)) {
            case Commodities.GAMMA_CORE:
                name = StringHelper.getString("IndEvo_AICores", "gCoreStatModAssigned");

                if (currentShip == null)
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
        return stack.getSpecialItemSpecIfSpecial().getId().contains(ItemIds.FORGETEMPLATE);
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("Hull Forge: pulls %s from this storage to use.", 10f, Misc.getHighlightColor(), "Forge Templates");
    }
}
