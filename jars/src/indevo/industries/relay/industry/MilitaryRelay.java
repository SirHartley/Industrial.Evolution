package indevo.industries.relay.industry;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.ColonyOtherFactorsListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.items.installable.SpecialItemEffectsRepo;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.scripts.EntityRemovalScript;
import indevo.utils.timers.NewDayListener;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;


public class MilitaryRelay extends MilitaryBase implements NewDayListener {

    private float bestFleetSize = 0f;
    private String bestMarketId = null;
    private String bestAiCoreId = null;

    private float currentMultMod = 0.15f;
    private float currentAICoreBonus = 1f;
    private boolean addedOrRemovedItem = false;

    private static final float MB_FS_MULT = 0.20f;
    private static final float HC_FS_MULT = 0.35f;
    private static final float DEFAULT_BONUS_FS_MULT = 1f;
    private static final float ALPHA_CORE_BONUS_FS_MULT = 1.15f;
    private static final float BETA_CORE_UPKEEP_RED_MULT = 0.75f;
    private static final float PATROL_HQ_UPKEEP_MULT = 2f;

    public static final String STRING_IDENT = "IndEvo_ComArray";

    //Industry effects
    public void apply() {
        Global.getSector().getListenerManager().addListener(this, true);
        onNewDay();
        bestAiCoreId = getBestHighCommandAICoreId();

        if (getId().equals(Ids.INTARRAY)) {
            if (getSpecialItem() == null) createCommRelayStation(Ids.INTARRAY_ENTITY);
            else createCommRelayStation(Ids.PRISTINE_INTARRAY_ENTITY);
        }

        if (!marketHasMilitary()) {
            getUpkeep().modifyMult("ind_patHQ", PATROL_HQ_UPKEEP_MULT, StringHelper.getString(STRING_IDENT, "operateAsHQ"));
            patrolHqApply();
        } else {
            applyIncomeAndUpkeep(3);
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);

        getUpkeep().unmodify("ind_patHQ");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());
        removeCommRelayStation();

        if (!marketHasMilitary()) {
            patrolHqUnapply();
        }
    }

    @Override
    public void setSpecialItem(SpecialItemData special) {
        super.setSpecialItem(special);

        addedOrRemovedItem = true;
    }

    private void patrolHqApply() {
        int size = market.getSize();

        super.apply(false);
        applyIncomeAndUpkeep(3);

        int light = 0;
        int medium = 0;
        int heavy = 0;

        float fsm = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).getMult();

        if (size <= 4 || fsm < 1f) {
            light = 2;
            medium = 0;
        } else if (size <= 6 || fsm < 2f) {
            light = 2;
            medium = 1;
        } else {
            light = 3;
            medium = 1;
        }

        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(getModId(), light);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).modifyFlat(getModId(), medium);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).modifyFlat(getModId(), heavy);

        demand(Commodities.SUPPLIES, size - 1);
        demand(Commodities.FUEL, size - 1);
        demand(Commodities.SHIPS, size - 1);

        supply(Commodities.CREW, size);
        modifyStabilityWithBaseMod();

        float mult = getDeficitMult(Commodities.SUPPLIES);
        String extra = "";
        if (mult != 1) {
            String com = getMaxDeficit(Commodities.SUPPLIES).one;
            extra = " (" + getDeficitText(com).toLowerCase() + ")";
        }
        float bonus = DEFENSE_BONUS_MILITARY;
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(getModId(), 1f + bonus * mult, getNameForModifier() + extra);

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(memory, MemFlags.MARKET_PATROL, getModId(), true, -1);

        if (!isFunctional()) {
            supply.clear();
            unapply();
        }
    }

    private void patrolHqUnapply() {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(memory, MemFlags.MARKET_PATROL, getModId(), false, -1);
        Misc.setFlagWithReason(memory, MemFlags.MARKET_MILITARY, getModId(), false, -1);

        unmodifyStabilityWithBaseMod();

        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).unmodifyFlat(getModId());

        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId());
    }

    @Override
    public boolean isFunctional() {
        return (isFunctionAllowed()) && super.isFunctional();
    }

    @Override
    public void onNewDay() {
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());

        if (isFunctional()) {
            applySystemWideHighestMult();
        }
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (!isFunctional()) return;

        if (reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION) {
            RouteManager.RouteData route = RouteManager.getInstance().getRoute(getRouteSourceId(), fleet);
            if (route != null && route.getCustom() instanceof PatrolFleetData) {
                PatrolFleetData custom = (PatrolFleetData) route.getCustom();
                if (custom.spawnFP > 0) {
                    float fraction = fleet.getFleetPoints() * 1f / custom.spawnFP;
                    returningPatrolValue += fraction;
                }
            }
        }
    }

    private void createCommRelayStation(String id) {
        boolean hasRelay = false;

        //check if we have a relay anywhere already
        for (CustomCampaignEntityAPI entity : market.getContainingLocation().getCustomEntitiesWithTag(Ids.INTARRAY_ENTITY_TAG)) {
            if (entity.hasTag(market.getId())) {
                hasRelay = true;
                break;
            }
        }

        //if not, make one
        if (!hasRelay) {
            float orbitRadius;
            StarSystemAPI system;
            CustomCampaignEntityAPI intArray;

            orbitRadius = market.getPrimaryEntity().getRadius() + 120F; //get planet size (r) and add some to get orbit
            system = market.getStarSystem();

            intArray = system.addCustomEntity(id, null, id, market.getFactionId()); //add the thing orbiting the market
            if (!system.isNebula() && market.getPrimaryEntity() instanceof PlanetAPI) {
                intArray.setCircularOrbitPointingDown(market.getPrimaryEntity(), market.getPrimaryEntity().getCircularOrbitAngle() - 30.0F, orbitRadius, market.getPrimaryEntity().getCircularOrbitPeriod()); //set as circular orbit
            } else {
                intArray.setCircularOrbit(market.getPrimaryEntity(), 0f, orbitRadius, 31f);
            }

            intArray.addTag(market.getId()); //tag it
        }
    }

    private void removeCommRelayStation() {
        CustomCampaignEntityAPI relay = null;

        for (CustomCampaignEntityAPI entity : market.getContainingLocation().getCustomEntitiesWithTag(Ids.INTARRAY_ENTITY_TAG)) {
            if (entity.hasTag(market.getId())) {
                relay = entity;
                break;
            }
        }

        if (relay != null) {
            //check if there is an active removal script and return if there is
            for (EveryFrameScript s : Global.getSector().getTransientScripts()) {
                if (s.getClass().getName().equals("EntityRemovalScript")) {
                    return;
                }
            }

            Global.getSector().addTransientScript(new EntityRemovalScript(relay, market, getId(), addedOrRemovedItem));
            addedOrRemovedItem = false;
        }
    }

    private void applySystemWideHighestMult() {
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());

        currentMultMod = systemHasHC() ? HC_FS_MULT : MB_FS_MULT;
        currentMultMod *= currentAICoreBonus;

        bestFleetSize = calculateNetworkwideHighestFS();

        float value = Math.round((bestFleetSize * currentMultMod) * 100f) / 100f;

        if (!market.getId().equals(bestMarketId) && isFunctionAllowed()) {
            value = reduceValueToNotExceedHighest(value);

            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(getModId(), value, getNameForModifier());
        }
    }

    private float reduceValueToNotExceedHighest(float bonusValue) {
        float highest = bestFleetSize;
        StatBonus fleetSizeMod = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT);

        float flatMod = getFlatModValue(fleetSizeMod);
        float percentMod = getPercentModModValue(fleetSizeMod);
        float mult = getMultModValue(fleetSizeMod);

        float totalValue = ((flatMod + bonusValue) * percentMod) * mult;

        if (totalValue <= highest) {
            return bonusValue;
        } else {
            bonusValue = Math.round(((highest / (mult * percentMod)) - flatMod) * 100f) / 100f;
            return bonusValue;
        }
    }

    private float calculateNetworkwideHighestFS() {
        boolean systemHasIA = IndustryHelper.systemHasIndustryExcludeNotFunctional(Ids.INTARRAY, market.getStarSystem(), market.getFaction());
        FactionAPI faction = market.getFaction();

        if (systemHasIA) {
            //Interstellar Array handling

            //make player system list
            ArrayList<StarSystemAPI> playerSystemList = new ArrayList<>();
            for (MarketAPI market : Misc.getFactionMarkets(faction)) {
                StarSystemAPI system = market.getStarSystem();
                if (!playerSystemList.contains(system)) {
                    playerSystemList.add(system);
                }
            }

            //get highest of all systems with player colony and IA
            Pair<String, Float> networkBest = new Pair<>("", 0f);

            for (StarSystemAPI system : playerSystemList) {
                if (IndustryHelper.systemHasIndustryExcludeNotFunctional(Ids.INTARRAY, system, faction)) {
                    Pair<String, Float> systemBest = getBestPairInSystem(system, faction);
                    networkBest = systemBest.two > networkBest.two ? systemBest : networkBest;
                }
            }

            bestMarketId = networkBest.one;
            return networkBest.two;

        } else {
            //in-System highest only
            Pair<String, Float> systemBest = getBestPairInSystem(market.getStarSystem(), faction);
            bestMarketId = systemBest.one;
            return systemBest.two;
        }
    }

    private Pair<String, Float> getBestPairInSystem(StarSystemAPI system, FactionAPI faction) {

        float systemHighest = 0f;
        String highestMarketId = null;

        List<MarketAPI> PlayerMarketsInSystem = IndustryHelper.getMarketsInLocation(system, faction.getId());
        for (MarketAPI playerMarket : PlayerMarketsInSystem) {
            if (playerMarket.hasIndustry(Ids.COMARRAY) || playerMarket.hasIndustry(Ids.INTARRAY)) {

                float fleetSize = getFleetSizeExcludingArrayBonus(playerMarket);
                if (fleetSize > systemHighest) {
                    systemHighest = fleetSize;
                    highestMarketId = playerMarket.getId();
                }
            }
        }

        return new Pair<>(highestMarketId, systemHighest);
    }

    private float getFleetSizeExcludingArrayBonus(MarketAPI market) {
        StatBonus fleetSizeMod = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT);

        float flatMod = getFlatModValue(fleetSizeMod);
        float percentMod = getPercentModModValue(fleetSizeMod);
        float mult = getMultModValue(fleetSizeMod);
        float cleanedValue;

        cleanedValue = flatMod * percentMod;
        cleanedValue *= mult;

        return cleanedValue;
    }

    private float getFlatModValue(StatBonus fleetSizeMod) {
        float flatMod = 0f;

        if (!fleetSizeMod.getFlatBonuses().isEmpty()) {
            for (MutableStat.StatMod mod : fleetSizeMod.getFlatBonuses().values()) {
                if (!mod.getSource().equals(getModId())) {
                    flatMod += mod.value;
                }
            }
        }

        return flatMod;
    }

    private float getPercentModModValue(StatBonus fleetSizeMod) {
        float percentMod = 1f;

        if (!fleetSizeMod.getPercentBonuses().isEmpty()) {
            for (MutableStat.StatMod mod : fleetSizeMod.getPercentBonuses().values()) {
                if (!mod.getSource().equals(getModId())) {
                    percentMod += mod.value;
                }
            }
        }

        return percentMod;
    }

    private float getMultModValue(StatBonus fleetSizeMod) {
        float mult = 1f;

        if (!fleetSizeMod.getMultBonuses().isEmpty()) {
            for (MutableStat.StatMod mod : fleetSizeMod.getMultBonuses().values()) {
                if (!mod.getSource().equals(getModId())) {
                    mult *= mod.value;
                }
            }
        }

        return mult;
    }

    private boolean isFunctionAllowed() {
        return systemHasHC() || systemHasMB() || transmitterPresent();
    }

    private boolean transmitterPresent() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(SpecialItemEffectsRepo.TRANSMITTER_UNLOCK_KEY);
    }

    private boolean systemHasMB() {
        return IndustryHelper.systemHasIndustry(Industries.MILITARYBASE, market.getStarSystem(), market.getFaction());
    }

    private boolean systemHasHC() {
        return IndustryHelper.systemHasIndustry(Industries.HIGHCOMMAND, market.getStarSystem(), market.getFaction());
    }

    private boolean marketHasMilitary() {
        return IndustryHelper.marketHasMilitary(market, false);
    }

    @Override
    public boolean isAvailableToBuild() {
        boolean setting = Settings.getBoolean(Settings.COMARRAY);
        boolean hasMBOrHC = isFunctionAllowed();
        return super.isAvailableToBuild() && setting && hasMBOrHC;
    }

    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.COMARRAY);
    }

    @Override
    public String getUnavailableReason() {
        if (!isFunctionAllowed()) {
            return StringHelper.getString(STRING_IDENT, "unavailableReason");
        }

        return super.getUnavailableReason();
    }

    @Override
    public float getBuildTime() {
        boolean isIntArray = getId().equals(Ids.INTARRAY);
        return !isIntArray && !marketHasMilitary() ? Global.getSettings().getIndustrySpec(Industries.PATROLHQ).getBuildTime() : super.getBuildTime();
    }

    @Override
    public float getBuildCost() {
        boolean isIntArray = getId().equals(Ids.INTARRAY);
        return !isIntArray && !marketHasMilitary() ? Global.getSettings().getIndustrySpec(Industries.PATROLHQ).getCost() * 1.2f : super.getBuildCost();
    }

    //Tooltip Handling

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        boolean ia = getId().equals(Ids.INTARRAY);

        if (!isBuilding() && isFunctional()) {
            float opad = 5.0F;
            Color highlight = Misc.getHighlightColor();

            if (market.isPlayerOwned() && currTooltipMode == IndustryTooltipMode.NORMAL && bestMarketId != null) {
                if (isFunctional()) {

                    float value = reduceValueToNotExceedHighest(Math.round((bestFleetSize * currentMultMod) * 100f) / 100f);
                    MarketAPI bestMarket = Global.getSector().getEconomy().getMarket(bestMarketId);

                    Map<String, String> toReplace = new HashMap<>();
                    toReplace.put("$fleetSize", "+" + StringHelper.getAbsPercentString(value, false));
                    toReplace.put("$market", bestMarket.getName());
                    toReplace.put("$system", bestMarket.getStarSystem().getName());

                    String currentBonusTooltip = StringHelper.getStringAndSubstituteTokens(STRING_IDENT, ia ? "currentBonusInt" : "currentBonus", toReplace);
                    String hcName = Global.getSettings().getIndustrySpec(Industries.HIGHCOMMAND).getName();

                    if (market.getId().equals(bestMarketId))
                        tooltip.addPara(StringHelper.getString(STRING_IDENT, "isBest"), opad, highlight, StringHelper.getString(STRING_IDENT, "isBestHighlights"));
                    else
                        tooltip.addPara(currentBonusTooltip, opad, highlight, new String[]{toReplace.get("$fleetSize"), toReplace.get("$market"), toReplace.get("$system")});

                    if (!systemHasHC())
                        tooltip.addPara(StringHelper.getString(STRING_IDENT, "canImprove"), 2f, highlight, hcName);

                    tooltip.addPara(StringHelper.getString(STRING_IDENT, "aiCoreNotice"), opad, highlight, new String[]{StringHelper.getString("IndEvo_AICores", "aiCores"), hcName});
                }
            }

            if (ia) {
                tooltip.addPara(StringHelper.getString(STRING_IDENT, "commRelayNotice"), opad, Misc.getPositiveHighlightColor(), Global.getSettings().getCustomEntitySpec(Entities.COMM_RELAY_MAKESHIFT).getNameInText());
            }
        }
    }

    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        return (mode != IndustryTooltipMode.NORMAL || isFunctional()) && !marketHasMilitary();
    }

    protected void addStabilityPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (!marketHasMilitary()) super.addStabilityPostDemandSection(tooltip, hasDemand, mode);
    }

    @Override
    protected void addGroundDefensesImpactSection(TooltipMakerAPI tooltip, float bonus, String... commodities) {
        if (!marketHasMilitary()) super.addGroundDefensesImpactSection(tooltip, bonus, commodities);
    }

//AI core Handling

    private String getBestHighCommandAICoreId() {
        List<MarketAPI> marketsInLocation = IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFactionId());
        Set<String> aiCoreSet = new HashSet<>();

        for (MarketAPI playerMarket : marketsInLocation) {
            if (playerMarket.hasIndustry(Industries.HIGHCOMMAND)) {
                String aiCoreId = playerMarket.getIndustry(Industries.HIGHCOMMAND).getAICoreId();

                if (aiCoreId != null) {
                    aiCoreSet.add(aiCoreId);
                }
            }
        }

        if (aiCoreSet.contains(Commodities.ALPHA_CORE)) return Commodities.ALPHA_CORE;
        if (aiCoreSet.contains(Commodities.BETA_CORE)) return Commodities.BETA_CORE;
        if (aiCoreSet.contains(Commodities.GAMMA_CORE)) return Commodities.GAMMA_CORE;

        return null;
    }

    public void addInstalledItemsSection(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();
        boolean addedSomething = false;

        LabelAPI heading = tooltip.addSectionHeading(Misc.ucFirst(StringHelper.ucFirstIgnore$(StringHelper.getString("IndEvo_items", "items"))), color, dark, Alignment.MID, opad);

        if (bestAiCoreId != null) {
            addAICoreSection(tooltip, bestAiCoreId, AICoreDescriptionMode.INDUSTRY_TOOLTIP);
            addedSomething = true;
        }

        if (aiCoreId != null) {
            tooltip.addPara("%s",
                    opad,
                    Misc.getNegativeHighlightColor(),
                    StringHelper.getString(STRING_IDENT, "aiCoreNoEffect"));

            addedSomething = true;
        }

        addedSomething |= addNonAICoreInstalledItems(mode, tooltip, expanded);

        if (!addedSomething) {
            heading.setText(StringHelper.getString("IndEvo_items", "noItems"));
        }
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String effect = StringHelper.getString(STRING_IDENT, "aCoreEffect");
        String highlightString = StringHelper.getAbsPercentString(ALPHA_CORE_BONUS_FS_MULT, true);

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(bestAiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara("%s", opad, Misc.getNegativeHighlightColor(), StringHelper.getString(STRING_IDENT, "aiCoreNoEffect"));
        }
    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "bCoreAssigned" + suffix);
        String effect = StringHelper.getString(STRING_IDENT, "bCoreEffect");
        String highlightString = StringHelper.getAbsPercentString(BETA_CORE_UPKEEP_RED_MULT, true);

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(bestAiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara("%s", opad, Misc.getNegativeHighlightColor(), StringHelper.getString(STRING_IDENT, "aiCoreNoEffect"));
        }
    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "gCoreAssigned" + suffix);
        String effect = StringHelper.getString(STRING_IDENT, "gCoreEffect");
        String highlightString = "";

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(bestAiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara("%s", opad, Misc.getNegativeHighlightColor(), StringHelper.getString(STRING_IDENT, "aiCoreNoEffect"));
        }
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
        String name = StringHelper.getString("IndEvo_AICores", "bCoreStatModAssigned");
        String id = bestAiCoreId;
        if (id != null && id.equals(Commodities.BETA_CORE))
            getUpkeep().modifyMult("ind_core", BETA_CORE_UPKEEP_RED_MULT, name);
        else getUpkeep().unmodifyMult("ind_core");
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    protected void applyAICoreModifiers() {
        String id = bestAiCoreId;
        if (id != null && id.equals(Commodities.ALPHA_CORE)) applyAlphaCoreModifiers();
        else applyNoAICoreModifiers();
    }

    @Override
    protected void applyNoAICoreModifiers() {
        currentAICoreBonus = DEFAULT_BONUS_FS_MULT;
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        currentAICoreBonus = ALPHA_CORE_BONUS_FS_MULT;
    }

    @Override
    public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        currTooltipMode = mode;
        String cat = "IndEvo_BaseIndustryTooltips";

        float pad = 3f;
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();

        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();


        MarketAPI copy = market.clone();
        MarketAPI orig = market;

        market = copy;
        boolean needToAddIndustry = !market.hasIndustry(getId());
        //addDialogMode = true;
        if (needToAddIndustry) market.getIndustries().add(this);

        if (mode != IndustryTooltipMode.NORMAL) {
            market.clearCommodities();
            for (CommodityOnMarketAPI curr : market.getAllCommodities()) {
                curr.getAvailableStat().setBaseValue(100);
            }
        }

        market.reapplyConditions();
        reapply();

        String type = "";
        if (isIndustry()) type = StringHelper.getString(cat, "t1");
        if (isStructure()) type = StringHelper.getString(cat, "t2");

        tooltip.addTitle(getCurrentName() + type, color);

        String desc = spec.getDesc();
        String override = getDescriptionOverride();
        if (override != null) {
            desc = override;
        }
        desc = Global.getSector().getRules().performTokenReplacement(null, desc, market.getPrimaryEntity(), null);

        tooltip.addPara(desc, opad);

        if (isIndustry() && (mode == IndustryTooltipMode.ADD_INDUSTRY ||
                mode == IndustryTooltipMode.UPGRADE ||
                mode == IndustryTooltipMode.DOWNGRADE)
        ) {
            int num = Misc.getNumIndustries(market);
            int max = Misc.getMaxIndustries(market);

            // during the creation of the tooltip, the market has both the current industry
            // and the upgrade/downgrade. So if this upgrade/downgrade counts as an industry, it'd count double if
            // the current one is also an industry. Thus reduce num by 1 if that's the case.
            if (isIndustry()) {
                if (mode == IndustryTooltipMode.UPGRADE) {
                    for (Industry curr : market.getIndustries()) {
                        if (getSpec().getId().equals(curr.getSpec().getUpgrade())) {
                            if (curr.isIndustry()) {
                                num--;
                            }
                            break;
                        }
                    }
                } else if (mode == IndustryTooltipMode.DOWNGRADE) {
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

                tooltip.addPara(StringHelper.getString(cat, "t3"), bad, opad);
            }
        }

        addRightAfterDescriptionSection(tooltip, mode);

        if (isDisrupted()) {
            int left = (int) getDisruptedDays();
            if (left < 1) left = 1;

            tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t4", "$days", StringHelper.getDayOrDays(left)),
                    opad, Misc.getNegativeHighlightColor(), highlight, "" + left);
        }

        if (DebugFlags.COLONY_DEBUG || market.isPlayerOwned()) {
            if (mode == IndustryTooltipMode.NORMAL) {
                if (getSpec().getUpgrade() != null && !isBuilding()) {
                    tooltip.addPara(StringHelper.getString(cat, "t5"), Misc.getPositiveHighlightColor(), opad);
                } else {
                    tooltip.addPara(StringHelper.getString(cat, "t6"), Misc.getPositiveHighlightColor(), opad);
                }
                //tooltip.addPara("Click to manage", market.getFaction().getBrightUIColor(), opad);
            }
        }

        if (mode == IndustryTooltipMode.QUEUED) {
            tooltip.addPara(StringHelper.getString(cat, "t7"), Misc.getPositiveHighlightColor(), opad);
            tooltip.addPara(StringHelper.getString(cat, "t8"), opad);

            int left = (int) (getSpec().getBuildTime());
            if (left < 1) left = 1;
            tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t9", "$days", StringHelper.getDayOrDays(left)), opad, highlight, "" + left);

            //return;
        } else if (!isFunctional() && mode == IndustryTooltipMode.NORMAL && isBuilding()) {
            tooltip.addPara(StringHelper.getString(cat, "t10"), opad);

            int left = (int) (buildTime - buildProgress);
            if (left < 1) left = 1;
            tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t11", "$days", StringHelper.getDayOrDays(left)), opad, highlight, "" + left);
        } else if (!isFunctional() && !isFunctionAllowed() && mode == IndustryTooltipMode.NORMAL) {
            tooltip.addPara("%s", opad, bad, StringHelper.getString(STRING_IDENT, "notFunctional"));
        }

        if (!isAvailableToBuild() &&
                (mode == IndustryTooltipMode.ADD_INDUSTRY ||
                        mode == IndustryTooltipMode.UPGRADE ||
                        mode == IndustryTooltipMode.DOWNGRADE)) {
            String reason = getUnavailableReason();
            if (reason != null) {
                tooltip.addPara(reason, bad, opad);
            }
        }

        boolean category = getSpec().hasTag(Industries.TAG_PARENT);

        if (!category) {
            int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
            String creditsStr = Misc.getDGSCredits(credits);
            if (mode == IndustryTooltipMode.UPGRADE || mode == IndustryTooltipMode.ADD_INDUSTRY) {
                int cost = (int) getBuildCost();
                String costStr = Misc.getDGSCredits(cost);

                int days = (int) getBuildTime();

                LabelAPI label = null;
                if (mode == IndustryTooltipMode.UPGRADE) {
                    label = tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t12", "$days", StringHelper.getDayOrDays(days)), opad,
                            highlight, costStr, "" + days, creditsStr);
                } else {
                    label = tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t13", "$days", StringHelper.getDayOrDays(days)), opad,
                            highlight, costStr, "" + days, creditsStr);
                }
                label.setHighlight(costStr, "" + days, creditsStr);
                if (credits >= cost) {
                    label.setHighlightColors(highlight, highlight, highlight);
                } else {
                    label.setHighlightColors(bad, highlight, highlight);
                }
            } else if (mode == IndustryTooltipMode.DOWNGRADE) {
                float refundFraction = Global.getSettings().getFloat("industryRefundFraction");
                int cost = (int) (getBuildCost() * refundFraction);
                String refundStr = Misc.getDGSCredits(cost);

                tooltip.addPara(StringHelper.getString(cat, "t14"), opad, highlight, refundStr);
            }


            addPostDescriptionSection(tooltip, mode);

            if (!getIncome().isUnmodified()) {
                int income = getIncome().getModifiedInt();
                tooltip.addPara(StringHelper.getString(cat, "t15"), opad, highlight, Misc.getDGSCredits(income));
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
                tooltip.addPara(StringHelper.getString(cat, "t16"), opad, highlight, Misc.getDGSCredits(upkeep));
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

            hasSupply = hasSupply && !marketHasMilitary();
            hasDemand = hasDemand && !marketHasMilitary();

            float maxIconsPerRow = 10f;
            if (hasSupply) {
                tooltip.addSectionHeading(StringHelper.getString(cat, "t17"), color, dark, Alignment.MID, opad);
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
                        } else if (mod.desc != null && mod.desc.contains(StringHelper.getString("shortage"))) {
                            minus += (int) Math.abs(mod.value);
                        }
                    }
                    minus = Math.min(minus, plus);
                    if (minus > 0 && mode == IndustryTooltipMode.NORMAL) {
                        tooltip.addIcons(market.getCommodityData(curr.getCommodityId()), minus, IconRenderMode.DIM_RED);
                    }
                    icons += curr.getQuantity().getModifiedInt() + Math.max(0, minus);
                }
                int rows = (int) Math.ceil(icons / maxIconsPerRow);
                rows = 3;
                tooltip.addIconGroup(32, rows, opad);


            }

            addPostSupplySection(tooltip, hasSupply, mode);

            if (hasDemand || hasPostDemandSection(hasDemand, mode)) {
                tooltip.addSectionHeading(StringHelper.getString(cat, "t18"), color, dark, Alignment.MID, opad);
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

                    if (mode != IndustryTooltipMode.NORMAL) {
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

            tooltip.addPara(StringHelper.getString(cat, "t19"), gray, opad);
        }

        if (needToAddIndustry) {
            unapply();
            market.getIndustries().remove(this);
        }
        market = orig;
        if (!needToAddIndustry) {
            reapply();
        }
    }

    @Override
    public boolean canInstallAICores() {
        return false;
    }

    @Override
    public boolean canImprove() {
        return false;
    }

    public static Pair<MarketAPI, Float> getNearestMilBaseWithItem(Vector2f locInHyper) {
        MarketAPI nearest = null;
        float minDist = Float.MAX_VALUE;

        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND)) {
                Industry ind = market.hasIndustry(Industries.MILITARYBASE) ? market.getIndustry(Industries.MILITARYBASE) : market.getIndustry(Industries.HIGHCOMMAND);

                if (ind.isFunctional() && ind.getSpecialItem() != null) {
                    float dist = Misc.getDistanceLY(locInHyper, ind.getMarket().getLocationInHyperspace());
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = market;
                    }
                }
            }
        }

        if (nearest == null) return null;

        return new Pair<>(nearest, minDist);
    }

    public static class RelayFactor implements ColonyOtherFactorsListener {
        public boolean isActiveFactorFor(SectorEntityToken entity) {
            return getNearestMilBaseWithItem(entity.getLocationInHyperspace()) != null;
        }

        public void printOtherFactors(TooltipMakerAPI text, SectorEntityToken entity) {
            Pair<MarketAPI, Float> p = getNearestMilBaseWithItem(entity.getLocationInHyperspace());

            if (p != null) {
                Color h = Misc.getHighlightColor();
                float opad = 10f;

                String dStr = "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two);
                String lights = "light-years";
                if (dStr.equals("1")) lights = "light-year";

                text.addPara("The nearest Military Base or High Command with a Relay Hypertransmitter is located in the " +
                                p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away, " +
                                "allowing you to build %s in this star system.",
                        opad, h,
                        "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                        "relays without any military presence");
            }
        }
    }
}
