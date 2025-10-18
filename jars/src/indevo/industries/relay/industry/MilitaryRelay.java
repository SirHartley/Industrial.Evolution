package indevo.industries.relay.industry;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.ColonyOtherFactorsListener;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.industries.relay.condition.RelayConditionPlugin;
import indevo.industries.relay.listener.RelayNetworkBrain;
import indevo.industries.relay.plugins.NetworkEntry;
import indevo.items.consumables.scripts.DelayedActionScriptRunWhilePaused;
import indevo.items.installable.SpecialItemEffectsRepo;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;
import indevo.utils.scripts.EntityRemovalScript;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;


public class MilitaryRelay extends MilitaryBase {

    public static class RelayItemRemovalButtonListener extends SingleIndustrySimpifiedOptionProvider {

        public static void register() {
            ListenerManagerAPI listeners = Global.getSector().getListenerManager();
            if (!listeners.hasListenerOfClass(RelayItemRemovalButtonListener.class)) {
                listeners.addListener(new RelayItemRemovalButtonListener(), true);
            }
        }

        @Override
        public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
            return super.isSuitable(ind, allowUnderConstruction) && ind.getSpecialItem() != null && ind.getMarket().isPlayerOwned();
        }

        @Override
        public void onClick(IndustryOptionProvider.IndustryOptionData opt, DialogCreatorUI ui) {
            Misc.getStorageCargo(opt.ind.getMarket()).addSpecial(opt.ind.getSpecialItem(), 1);
            opt.ind.setSpecialItem(null);
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
            tooltip.addPara("Removes the %s installed in this industry.", 0f, Misc.getHighlightColor(), "Relay Hypertransmitter");
        }

        @Override
        public String getOptionLabel(Industry ind) {
            return "Remove Hypertransmitter";
        }

        @Override
        public String getTargetIndustryId() {
            return Ids.COMARRAY;
        }
    }

    private boolean addedOrRemovedItem = false;

    private static final float PATROL_HQ_UPKEEP_MULT = 2f;
    public static final float BASE_FLEET_SIZE_TRANSFER_FRACT = 0.3f;
    public static final float IMPROVE_ADDITIONAL_FLEET_SIZE_TRANSFER_FRACT = 0.15f;
    public static final Map<String, Float> AI_CORE_FACTION_MAP = new HashMap<>() {{
        put(Commodities.ALPHA_CORE, 0.3f);
        put(Commodities.BETA_CORE, 0.2f);
        put(Commodities.GAMMA_CORE, 0.1f);
    }};

    public static final String STRING_IDENT = "IndEvo_ComArray";

    //Industry effects
    public void apply() {
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

        getUpkeep().unmodify("ind_patHQ");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());
        removeCommRelayStation();

        if (!marketHasMilitary()) {
            patrolHqUnapply();
        }
    }

    @Override
    public void finishBuildingOrUpgrading() {
        super.finishBuildingOrUpgrading();
        RelayNetworkBrain.forceUpdate();
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);

        if (!forUpgrade) Global.getSector().addScript(new DelayedActionScriptRunWhilePaused(0.01f) {
            @Override
            public void doAction() {
                RelayNetworkBrain.forceUpdate();
            }
        });
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

    private boolean isFunctionAllowed() {
        return systemHasHC() || systemHasMB() || transmitterPresent();
    }

    private boolean transmitterPresent() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(SpecialItemEffectsRepo.TRANSMITTER_UNLOCK_KEY);
    }

    private boolean systemHasMB() {
        return MiscIE.systemHasIndustry(Industries.MILITARYBASE, market.getStarSystem(), market.getFaction());
    }

    private boolean systemHasHC() {
        return MiscIE.systemHasIndustry(Industries.HIGHCOMMAND, market.getStarSystem(), market.getFaction());
    }

    private boolean marketHasMilitary() {
        return MiscIE.marketHasMilitary(market, false);
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
        boolean isIntArray = getId().equals(Ids.INTARRAY);
        float opad = 10.0F;
        float spad = 5f;

        if (!isBuilding() && isFunctional()) {
            NetworkEntry entry = RelayConditionPlugin.getRelayConditionPlugin(market).getEntry();
            if (entry == null) return;

            MarketAPI m = Global.getSector().getEconomy().getMarket(entry.sourceMarketId);
            if (m == null) return;

            float bestFleetSize = entry.baseFleetSize * entry.targetMult;
            float localFleetSize = entry.baseFleetSize;

            float transferFraction = getFleetSizeTransferFraction();
            float transferrableFleetSizeFromBest = bestFleetSize * transferFraction;

            float targetFleetSizeForPlanet = Math.min(bestFleetSize, localFleetSize + transferrableFleetSizeFromBest);
            float multForTransfer = targetFleetSizeForPlanet / localFleetSize;

            float actualFleetSizeIncrease = targetFleetSizeForPlanet - localFleetSize;

            if (currTooltipMode == IndustryTooltipMode.NORMAL) {
                tooltip.addSectionHeading("Relay data", Alignment.MID, opad);

                if (isFunctional()) {
                    if (m != market && multForTransfer > 1) {
                        tooltip.addPara("Increases local fleet size based on the largest fleet size within the relay network.", opad);

                        tooltip.addPara(BaseIntelPlugin.BULLET + "Local fleet size: %s", spad, Misc.getTextColor(), Misc.getHighlightColor(), StringHelper.getAbsPercentString(localFleetSize, false));

                        Color[] highlightColors1 = {Misc.getHighlightColor(), market.getFaction().getColor()};
                        tooltip.addPara(BaseIntelPlugin.BULLET + "Top fleet size in network: %s (%s)", spad, highlightColors1,
                                StringHelper.getAbsPercentString(bestFleetSize, false), m.getName());

                        Color[] highlightColors2 = {Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getTextColor()};
                        tooltip.addPara(BaseIntelPlugin.BULLET + "Transfer rate: up to %s of %s (%s)", spad, highlightColors2,
                                StringHelper.getAbsPercentString(getFleetSizeTransferFraction(), false),
                                StringHelper.getAbsPercentString(bestFleetSize, false),
                                StringHelper.getAbsPercentString(transferrableFleetSizeFromBest, false));

                        Color[] highlightColors3 = {Misc.getPositiveHighlightColor(), Misc.getHighlightColor()};
                        tooltip.addPara(BaseIntelPlugin.BULLET + "Transfer: %s (New local fleet size: %s)", spad, highlightColors3,
                                "+" + StringHelper.getAbsPercentString(actualFleetSizeIncrease, false),
                                StringHelper.getAbsPercentString(targetFleetSizeForPlanet, false));

                        if (targetFleetSizeForPlanet >= bestFleetSize)
                            tooltip.addPara(BaseIntelPlugin.BULLET + "Modified local fleet size can not exceed top network size.", Misc.getGrayColor(), 3f);

                    } else {
                        tooltip.addPara(market.getName() + " has the %s and gains no additional benefit from the " + getCurrentName() + ".", opad, Misc.getHighlightColor(), "largest fleet size in the network");
                    }
                } else tooltip.addPara("The relay is not functional or not connected to a network.", Misc.getGrayColor(), opad);
            }
        }

        if (isIntArray) {
            tooltip.addPara(StringHelper.getString(STRING_IDENT, "commRelayNotice"), opad, Misc.getPositiveHighlightColor(), Global.getSettings().getCustomEntitySpec(Entities.COMM_RELAY_MAKESHIFT).getNameInText());
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

    //story points

    @Override
    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        if (mode == ImprovementDescriptionMode.INDUSTRY_TOOLTIP) {
            info.addPara("Fleet size transfer rate increased by +%s.", 0f, highlight, StringHelper.getAbsPercentString(IMPROVE_ADDITIONAL_FLEET_SIZE_TRANSFER_FRACT, false));
        } else {
            info.addPara("Increases the maximum fleet size transfer rate by +%s.", 0f, highlight, StringHelper.getAbsPercentString(IMPROVE_ADDITIONAL_FLEET_SIZE_TRANSFER_FRACT, false));
        }

        info.addSpacer(opad);

        super.addImproveDesc(info, mode);
    }

    //AI core Handling

    public float getFleetSizeTransferFraction() {
        float increase = aiCoreId != null ? AI_CORE_FACTION_MAP.get(aiCoreId) : 0f;
        return increase + BASE_FLEET_SIZE_TRANSFER_FRACT;
    }

    @Override
    public void setAICoreId(String aiCoreId) {
        super.setAICoreId(aiCoreId);
        RelayNetworkBrain.forceUpdate();
    }

    public void addAnyCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode, String coreId) {
        if (coreId == null) return;

        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();

        String coreName = Misc.ucFirst(Global.getSettings().getCommoditySpec(coreId).getName().split(" ")[0]);
        float increase = AI_CORE_FACTION_MAP.get(coreId);
        increase += BASE_FLEET_SIZE_TRANSFER_FRACT;

        String pre = coreName + "-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = coreName + "-level AI core. ";
        }
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(coreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Increases the maximum fleet size transfer rate to %s", 0.0F, highlight, StringHelper.getAbsPercentString(increase, false));
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Increases the maximum fleet size transfer rate to %s", opad, highlight, StringHelper.getAbsPercentString(increase, false));
        }
    }

    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addAnyCoreDescription(tooltip, mode, Commodities.ALPHA_CORE);
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addAnyCoreDescription(tooltip, mode, Commodities.BETA_CORE);
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addAnyCoreDescription(tooltip, mode, Commodities.GAMMA_CORE);
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        //patrol HQ has a custom AI core effect...
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
