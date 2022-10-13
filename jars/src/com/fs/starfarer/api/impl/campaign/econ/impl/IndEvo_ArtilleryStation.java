package com.fs.starfarer.api.impl.campaign.econ.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.station.IndEvo_ArtilleryStationEntityPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.econ.conditions.IndEvo_ArtilleryStationCondition;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids.TAG_ARTILLERY_STATION_FLEET;

public class IndEvo_ArtilleryStation extends BaseIndustry implements FleetEventListener {
    //we'll make 2 versions - one for industry and one for derelict interaction
    //the derelict one should probably extend the normal one

    public static float DEFENSE_BONUS_BASE = 0.5f;
    public static final float IMPROVE_RANGE_BONUS = 4000f;

    protected CampaignFleetAPI stationFleet = null;
    protected SectorEntityToken stationEntity = null;

    @Override
    public void apply() {
        super.apply(false);

        int size = 5;

        applyIncomeAndUpkeep(size);

        demand(Commodities.HEAVY_MACHINERY, size);
        demand(Commodities.METALS, size);

        float bonus = DEFENSE_BONUS_BASE;
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(getModId(), 1f + bonus, getNameForModifier());

        matchCommanderToAICore(aiCoreId);
        IndEvo_ArtilleryStationEntityPlugin plugin = getArtilleryPlugin();

        if (!isFunctional()) {
            if (plugin != null) getArtilleryPlugin().setDisrupted(true);

            supply.clear();
            unapply();
        } else {
            applyCRToStation();
            if (plugin != null) getArtilleryPlugin().setDisrupted(false);
        }
    }

    @Override
    public boolean isFunctional() {
        boolean isSuperFunctional = super.isFunctional();

        boolean hasOrbital = false;

        for (Industry ind : market.getIndustries()) {
            if (ind == this) continue;
            if (!ind.isFunctional()) continue;
            if (ind.getSpec().hasTag(Tags.STATION)) {
                hasOrbital = true;
                break;
            }
        }

        return isSuperFunctional && hasOrbital && hasValidStation();
    }

    private boolean hasValidStation() {
        for (Industry ind : market.getIndustries()) {
            if (ind == this) continue;
            if (!ind.isFunctional()) continue;
            if (ind.getSpec().hasTag(Tags.STATION) && ind.getSpec().getPluginClass().equals("com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_OrbitalStation")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void unapply() {
        super.unapply();

        matchCommanderToAICore(null);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId());
    }

    protected void applyCRToStation() {
        if (stationFleet != null) {
            float cr = getCR();
            for (FleetMemberAPI member : stationFleet.getFleetData().getMembersListCopy()) {
                member.getRepairTracker().setCR(cr);
            }
            FleetInflater inflater = stationFleet.getInflater();
            if (inflater != null) {
                if (stationFleet.isInflated()) {
                    stationFleet.deflate();
                }
                inflater.setQuality(Misc.getShipQuality(market));
                if (inflater instanceof DefaultFleetInflater) {
                    DefaultFleetInflater dfi = (DefaultFleetInflater) inflater;
                    ((DefaultFleetInflaterParams) dfi.getParams()).allWeapons = true;
                }
            }
        }
    }

    protected float getCR() {
        float deficit = getMaxDeficit(Commodities.METALS, Commodities.HEAVY_MACHINERY).two;
        float demand = Math.max(getDemand(Commodities.METALS).getQuantity().getModifiedInt(),
                getDemand(Commodities.HEAVY_MACHINERY).getQuantity().getModifiedInt());

        if (deficit < 0) deficit = 0f;
        if (demand < 1) {
            demand = 1;
            deficit = 0f;
        }

        float q = Misc.getShipQuality(market);
        if (q < 0) q = 0;
        if (q > 1) q = 1;

        float d = (demand - deficit) / demand;
        if (d < 0) d = 0;
        if (d > 1) d = 1;

        float cr = 0.5f + 0.5f * Math.min(d, q);
        if (cr > 1) cr = 1;

        return cr;
    }


    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        //return mode == IndustryTooltipMode.NORMAL && isFunctional();
        return mode != IndustryTooltipMode.NORMAL || isFunctional();
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        //if (mode == IndustryTooltipMode.NORMAL && isFunctional()) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            Color h = Misc.getHighlightColor();
            float opad = 10f;

            float cr = getCR();
            tooltip.addPara("Station combat readiness: %s", opad, h, "" + Math.round(cr * 100f) + "%");
            float bonus = DEFENSE_BONUS_BASE;

            addGroundDefensesImpactSection(tooltip, bonus, Commodities.HEAVY_MACHINERY, Commodities.METALS);

            IndEvo_ArtilleryStationEntityPlugin plugin = getArtilleryPlugin();
            if (plugin != null)
                tooltip.addPara("Current range: %s", 10f, Misc.getHighlightColor(), (int) Math.round(plugin.getRange()) + " SU");
        }
    }

    @Override
    protected Object readResolve() {
        super.readResolve();
        return this;
    }

    private IntervalUtil reminderInterval = new IntervalUtil(60, 60);

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Global.getSector().getEconomy().isSimMode()) return;

        if (reminderInterval != null) reminderInterval.advance(amount); //null check for beta saves

        if (stationEntity == null) {
            spawnStation();
        }

        if (stationFleet != null) {
            stationFleet.setAI(null);
            if (stationFleet.getOrbit() == null && stationEntity != null) {
                stationFleet.setCircularOrbit(stationEntity, 0, 0, 100);
            }
        }

        /*if (reminderInterval != null && reminderInterval.intervalElapsed()){
            if (Misc.hasOrbitalStation(market) && !hasValidStation())
                Global.getSector().getCampaignUI().showMessageDialog(
                        "Detected an Artillery station with an invalid station type on " + market.getName() +
                                ", which will break your game unless you remove. This warning will repeat in 30 seconds.");
        }*/
    }

    @Override
    protected void buildingFinished() {
        super.buildingFinished();

        if (stationEntity != null && stationFleet != null) {
            matchStationAndCommanderToCurrentIndustry();
        } else {
            spawnStation();
        }
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);

        if (!forUpgrade) {
            removeStationEntityAndFleetIfNeeded();
        }
    }

    protected void removeStationEntityAndFleetIfNeeded() {
        IndEvo_modPlugin.log("removing artillery station at " + market.getName());

        if (stationEntity != null) {

            stationEntity.getMemoryWithoutUpdate().unset(MemFlags.STATION_FLEET);
            stationEntity.getMemoryWithoutUpdate().unset(MemFlags.STATION_BASE_FLEET);

            ((IndEvo_ArtilleryStationEntityPlugin) stationEntity.getCustomPlugin()).preRemoveActions();

            stationEntity.getContainingLocation().removeEntity(stationFleet);

            if (stationEntity.getContainingLocation() != null) {
                stationEntity.getContainingLocation().removeEntity(stationEntity);
                market.getConnectedEntities().remove(stationEntity);

                // commented out so that MarketCMD doesn't NPE if you destroy a market through bombardment of a station
                //stationEntity.setMarket(null);

            } else if (stationEntity.hasTag(Tags.USE_STATION_VISUAL)) {
                ((CustomCampaignEntityAPI) stationEntity).setFleetForVisual(null);
                float origRadius = ((CustomCampaignEntityAPI) stationEntity).getCustomEntitySpec().getDefaultRadius();
                ((CustomCampaignEntityAPI) stationEntity).setRadius(origRadius);
            }

            if (stationFleet != null) {
                stationFleet.getMemoryWithoutUpdate().unset(MemFlags.STATION_MARKET);
                stationFleet.removeEventListener(this);
            }

            stationEntity = null;
            stationFleet = null;
        }
    }

    @Override
    public void notifyColonyRenamed() {
        super.notifyColonyRenamed();

        if (stationFleet != null) stationFleet.setName(market.getName() + " " + getCurrentName() + " Station");
        if (stationEntity != null) stationEntity.setName(market.getName() + " " + getCurrentName() + " Station");
    }

    protected void spawnStation() {

        FleetParamsV3 fParams = new FleetParamsV3(null, null,
                market.getFactionId(),
                1f,
                FleetTypes.PATROL_SMALL,
                0,
                0, 0, 0, 0, 0, 0);
        fParams.allWeapons = true;

        removeStationEntityAndFleetIfNeeded();

        stationFleet = FleetFactoryV3.createFleet(fParams);
        stationFleet.setNoFactionInName(true);

        stationFleet.setStationMode(true);
        stationFleet.clearAbilities();
        stationFleet.addAbility(Abilities.TRANSPONDER);
        stationFleet.getAbility(Abilities.TRANSPONDER).activate();
        stationFleet.getDetectedRangeMod().modifyFlat("gen", 10000f);

        stationFleet.setAI(null);
        stationFleet.addEventListener(this);

        stationFleet.addTag(TAG_ARTILLERY_STATION_FLEET);

        ensureStationEntityIsSetOrCreated();

        if (stationEntity instanceof CustomCampaignEntityAPI) {
            if (stationEntity.hasTag(Tags.USE_STATION_VISUAL)) {
                ((CustomCampaignEntityAPI) stationEntity).setFleetForVisual(stationFleet);
                stationEntity.setCustomDescriptionId(getSpec().getId());
            }
        }

        stationFleet.setCircularOrbit(stationEntity, 0, 0, 100);
        stationFleet.getMemoryWithoutUpdate().set(MemFlags.STATION_MARKET, market);
        stationFleet.setHidden(true);

        matchStationAndCommanderToCurrentIndustry();
        notifyColonyRenamed();
    }

    protected void ensureStationEntityIsSetOrCreated() {
        if (stationEntity == null) {
            IndEvo_modPlugin.log("spawning artillery station at " + market.getName());

            stationEntity = IndEvo_ArtilleryStationEntityPlugin.placeAtMarket(market, getType(), true);
        }
    }

    public String getType() {
        return getSpec().getId().substring("IndEvo_Artillery_".length());
    }

    protected void matchStationAndCommanderToCurrentIndustry() {
        stationFleet.getFleetData().clear();

        String fleetName = null;
        String variantId = null;
        float radius = 60f;

        try {
            JSONObject json = new JSONObject(getSpec().getData());
            variantId = json.getString("variant");
            radius = (float) json.getDouble("radius");
            fleetName = json.getString("fleetName");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        if (stationEntity != null) {
            fleetName = stationEntity.getName();
        }

        stationFleet.setName(fleetName);

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
        String name = fleetName;
        member.setShipName(name);

        stationFleet.getFleetData().addFleetMember(member);
        applyCRToStation();

        if (stationEntity instanceof CustomCampaignEntityAPI) {
            ((CustomCampaignEntityAPI) stationEntity).setRadius(radius);
        } else if (stationEntity.hasTag(Tags.USE_STATION_VISUAL)) {
            ((CustomCampaignEntityAPI) stationEntity).setRadius(radius);
        }

        boolean skeletonMode = !isFunctional();

        if (skeletonMode) {
            stationEntity.getMemoryWithoutUpdate().unset(MemFlags.STATION_FLEET);
            stationEntity.getMemoryWithoutUpdate().set(MemFlags.STATION_BASE_FLEET, stationFleet);
            stationEntity.getContainingLocation().removeEntity(stationFleet);

            for (int i = 1; i < member.getStatus().getNumStatuses(); i++) {
                ShipVariantAPI variant = member.getVariant();
                if (i > 0) {
                    String slotId = member.getVariant().getModuleSlots().get(i - 1);
                    variant = variant.getModuleVariant(slotId);
                } else {
                    continue;
                }

                if (!variant.hasHullMod(HullMods.VASTBULK)) {
                    member.getStatus().setDetached(i, true);
                    member.getStatus().setPermaDetached(i, true);
                    member.getStatus().setHullFraction(i, 0f);
                }
            }

        } else {
            stationEntity.getMemoryWithoutUpdate().unset(MemFlags.STATION_BASE_FLEET);
            stationEntity.getMemoryWithoutUpdate().set(MemFlags.STATION_FLEET, stationFleet);
            stationEntity.getContainingLocation().removeEntity(stationFleet);
            stationFleet.setExpired(false);
            stationEntity.getContainingLocation().addEntity(stationFleet);
        }
    }

    protected int getHumanCommanderLevel() {
        return Global.getSettings().getInt("tier1StationOfficerLevel");
    }

    protected void matchCommanderToAICore(String aiCore) {
        if (stationFleet == null) return;

        PersonAPI commander = null;
        if (Commodities.ALPHA_CORE.equals(aiCore)) {

            AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE);
            commander = plugin.createPerson(Commodities.ALPHA_CORE, Factions.REMNANTS, null);
            if (stationFleet.getFlagship() != null) {
                RemnantOfficerGeneratorPlugin.integrateAndAdaptCoreForAIFleet(stationFleet.getFlagship());
            }
        } else {
            if (stationFleet.getFlagship() != null) {
                int level = getHumanCommanderLevel();
                PersonAPI current = stationFleet.getFlagship().getCaptain();
                if (level > 0) {
                    if (current.isAICore() || current.getStats().getLevel() != level) {
                        commander = OfficerManagerEvent.createOfficer(
                                Global.getSector().getFaction(market.getFactionId()), level, true);
                    }
                } else {
                    if (stationFleet.getFlagship() == null || stationFleet.getFlagship().getCaptain() == null ||
                            !stationFleet.getFlagship().getCaptain().isDefault()) {
                        commander = Global.getFactory().createPerson();
                    }
                }
            }

        }

        if (commander != null) {
            if (stationFleet.getFlagship() != null) {
                stationFleet.getFlagship().setCaptain(commander);
                stationFleet.getFlagship().setFlagship(false);
            }
        }
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }

    @Override
    protected void disruptionFinished() {
        super.disruptionFinished();

        IndEvo_ArtilleryStationEntityPlugin p = getArtilleryPlugin();
        if (p != null) p.setDisrupted(false);

        matchStationAndCommanderToCurrentIndustry();
    }

    @Override
    protected void notifyDisrupted() {
        super.notifyDisrupted();

        IndEvo_ArtilleryStationEntityPlugin p = getArtilleryPlugin();
        if (p != null) p.setDisrupted(true);

        matchStationAndCommanderToCurrentIndustry();
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (fleet != stationFleet) return; // shouldn't happen...

        disrupt(this);

        // bug where somehow a station fleet can become empty as a result of combat
        // then its despawn() gets called every frame
        if (stationFleet.getMembersWithFightersCopy().isEmpty()) {
            matchStationAndCommanderToCurrentIndustry();
        }
        stationFleet.setAbortDespawn(true);
    }

    public static void disrupt(Industry station) {
        station.setDisrupted(station.getSpec().getBuildTime() * 0.5f, true);
    }

    @Override
    protected void applyAlphaCoreModifiers() {
    }

    @Override
    protected void applyNoAICoreModifiers() {
    }

    @Override
    protected void applyAlphaCoreSupplyAndDemandModifiers() {
        demandReduction.modifyFlat(getModId(0), DEMAND_REDUCTION, "Alpha core");
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48);
            text.addPara(pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                            "Increases station combat effectiveness.", 0f, highlight,
                    "" + (int) ((1f - UPKEEP_MULT) * 100f) + "%", "" + DEMAND_REDUCTION);
            tooltip.addImageWithText(opad);
            return;
        }

        tooltip.addPara(pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                        "Increases station combat effectiveness.", opad, highlight,
                "" + (int) ((1f - UPKEEP_MULT) * 100f) + "%", "" + DEMAND_REDUCTION);

    }

    public IndEvo_ArtilleryStationEntityPlugin getArtilleryPlugin() {
        if (stationEntity != null) return (IndEvo_ArtilleryStationEntityPlugin) stationEntity.getCustomPlugin();

        for (SectorEntityToken t : market.getConnectedEntities()) {
            if (t.getCustomPlugin() instanceof IndEvo_ArtilleryStationEntityPlugin)
                return (IndEvo_ArtilleryStationEntityPlugin) t.getCustomPlugin();
        }

        return null;
    }

    @Override
    public boolean canImprove() {
        return true;
    }

    protected void applyImproveModifiers() {
        IndEvo_ArtilleryStationEntityPlugin p = getArtilleryPlugin();
        if (p == null) return;

        if (isImproved()) {
            p.resetRange();
            p.addToRange(IMPROVE_RANGE_BONUS);
        } else {
            p.resetRange();
        }
    }

    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();


        if (mode == ImprovementDescriptionMode.INDUSTRY_TOOLTIP) {
            info.addPara("Range increased by %s.", 0f, highlight, "" + (int) IMPROVE_RANGE_BONUS);
        } else {
            info.addPara("Increases range by %s.", 0f, highlight, "" + (int) IMPROVE_RANGE_BONUS);
        }

        info.addSpacer(opad);
        super.addImproveDesc(info, mode);
    }

    @Override
    public MarketCMD.RaidDangerLevel adjustCommodityDangerLevel(String commodityId, MarketCMD.RaidDangerLevel level) {
        return level.next();
    }

    @Override
    public MarketCMD.RaidDangerLevel adjustItemDangerLevel(String itemId, String data, MarketCMD.RaidDangerLevel level) {
        return level.next();
    }

    public String getUnavailableReason() {

        boolean hasOrbital = false;

        for (Industry ind : market.getIndustries()) {
            if (ind == this) continue;
            if (!ind.isFunctional()) continue;
            if (ind.getSpec().hasTag(Tags.STATION)) {
                hasOrbital = true;
                break;
            }
        }

        if (!hasOrbital) return "Requires an orbital station";
        if (!hasValidStation()) return "The current orbital station type is not suitable";

        return "Requires a functional spaceport";
    }

    @Override
    public boolean isAvailableToBuild() {
        boolean canBuild = false;
        boolean hasOrbital = false;

        for (Industry ind : market.getIndustries()) {
            if (ind == this) continue;
            if (!ind.isFunctional()) continue;

            if (!canBuild) if (ind.getSpec().hasTag(Industries.TAG_SPACEPORT)) canBuild = true;
            if (!hasOrbital) if (ind.getSpec().hasTag(Tags.STATION)) hasOrbital = true;
        }

        return market.hasCondition(IndEvo_ArtilleryStationCondition.ID) && canBuild && hasValidStation() && hasOrbital && Global.getSettings().getBoolean("Enable_IndEvo_Artillery");
    }

    @Override
    public boolean showWhenUnavailable() {
        return market.hasCondition(IndEvo_ArtilleryStationCondition.ID);
    }

    public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
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
        // the copy is a shallow copy and its conditions point to the original market
        // so, make it share the suppressed conditions list, too, otherwise
        // e.g. SolarArray will suppress conditions in the original market and the copy will still apply them
        copy.setSuppressedConditions(market.getSuppressedConditions());
        copy.setRetainSuppressedConditionsSetWhenEmpty(true);
        market.setRetainSuppressedConditionsSetWhenEmpty(true);
        MarketAPI orig = market;

        //int numBeforeAdd = Misc.getNumIndustries(market);

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

//		Industry inProgress = Misc.getCurrentlyBeingConstructed(market);
//		if ((mode == IndustryTooltipMode.ADD_INDUSTRY && inProgress != null) ||
//				(mode == IndustryTooltipMode.UPGRADE && inProgress != null)) {
//			//tooltip.addPara("Another project (" + inProgress.getCurrentName() + ") in progress", bad, opad);
//			//tooltip.addPara("Already building: " + inProgress.getCurrentName() + "", bad, opad);
//			tooltip.addPara("Another construction in progress: " + inProgress.getCurrentName() + "", bad, opad);
//		}

        //tooltip.addPara("Type: %s", opad, gray, highlight, type);
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
            Color h2 = highlight;
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

        if (isDisrupted()) {
            int left = (int) getDisruptedDays();
            if (left < 1) left = 1;
            String days = "days";
            if (left == 1) days = "day";

            tooltip.addPara("Operations disrupted! %s " + days + " until return to normal function.",
                    opad, Misc.getNegativeHighlightColor(), highlight, "" + left);
        }

        if (DebugFlags.COLONY_DEBUG || market.isPlayerOwned()) {
            if (mode == IndustryTooltipMode.NORMAL) {
                if (getSpec().getUpgrade() != null && !isBuilding()) {
                    tooltip.addPara("Click to manage or upgrade", Misc.getPositiveHighlightColor(), opad);
                } else {
                    tooltip.addPara("Click to manage", Misc.getPositiveHighlightColor(), opad);
                }
                //tooltip.addPara("Click to manage", market.getFaction().getBrightUIColor(), opad);
            }
        }

        boolean hasOrbital = false;
        for (Industry ind : market.getIndustries()) {
            if (ind == this) continue;
            if (!ind.isFunctional()) continue;
            if (ind.getSpec().hasTag(Tags.STATION)) {
                hasOrbital = true;
                break;
            }
        }

        if (mode == IndustryTooltipMode.QUEUED) {
            tooltip.addPara("Click to remove or adjust position in queue", Misc.getPositiveHighlightColor(), opad);
            tooltip.addPara("Currently queued for construction. Does not have any impact on the colony.", opad);

            int left = (int) (getSpec().getBuildTime());
            if (left < 1) left = 1;
            String days = "days";
            if (left == 1) days = "day";
            tooltip.addPara("Requires %s " + days + " to build.", opad, highlight, "" + left);

            //return;
        } else if (!hasOrbital){
            tooltip.addPara("Missing an orbital station to function!", opad, Misc.getNegativeHighlightColor(), Misc.getTextColor());

        } else if(!hasValidStation()){
            tooltip.addPara("!!! THE CURRENT ORBITAL STATION TYPE IS NOT VALID !!!", opad, Misc.getNegativeHighlightColor(), Misc.getTextColor());
            tooltip.addPara("Replace it with a valid orbital station immediately.", opad, Misc.getHighlightColor(), Misc.getTextColor());

        } else if (!isFunctional() && mode == IndustryTooltipMode.NORMAL) {
            tooltip.addPara("Currently under construction and not producing anything or providing other benefits.", opad);

            int left = (int) (buildTime - buildProgress);
            if (left < 1) left = 1;
            String days = "days";
            if (left == 1) days = "day";
            tooltip.addPara("Requires %s more " + days + " to finish building.", opad, highlight, "" + left);
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
                String daysStr = "days";
                if (days == 1) daysStr = "day";

                LabelAPI label = null;
                if (mode == IndustryTooltipMode.UPGRADE) {
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
            } else if (mode == IndustryTooltipMode.DOWNGRADE) {
                if (getSpec().getUpgrade() != null) {
                    float refundFraction = Global.getSettings().getFloat("industryRefundFraction");

                    //int cost = (int) (getBuildCost() * refundFraction);
                    IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(getSpec().getUpgrade());
                    int cost = (int) (spec.getCost() * refundFraction);
                    String refundStr = Misc.getDGSCredits(cost);

                    tooltip.addPara("%s refunded for downgrade.", opad, highlight, refundStr);
                }
            }


            addPostDescriptionSection(tooltip, mode);

            if (!getIncome().isUnmodified()) {
                int income = getIncome().getModifiedInt();
                tooltip.addPara("Monthly income: %s", opad, highlight, Misc.getDGSCredits(income));
                tooltip.addStatModGrid(250, 65, 10, pad, getIncome(), true, new TooltipMakerAPI.StatModValueGetter() {
                    public String getPercentValue(MutableStat.StatMod mod) {return null;}
                    public String getMultValue(MutableStat.StatMod mod) {return null;}
                    public Color getModColor(MutableStat.StatMod mod) {return null;}
                    public String getFlatValue(MutableStat.StatMod mod) {
                        return Misc.getWithDGS(mod.value) + Strings.C;
                    }
                });
            }

            if (!getUpkeep().isUnmodified()) {
                int upkeep = getUpkeep().getModifiedInt();
                tooltip.addPara("Monthly upkeep: %s", opad, highlight, Misc.getDGSCredits(upkeep));
                tooltip.addStatModGrid(250, 65, 10, pad, getUpkeep(), true, new TooltipMakerAPI.StatModValueGetter() {
                    public String getPercentValue(MutableStat.StatMod mod) {return null;}
                    public String getMultValue(MutableStat.StatMod mod) {return null;}
                    public Color getModColor(MutableStat.StatMod mod) {return null;}
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

            float maxIconsPerRow = 10f;
            if (hasSupply) {
                tooltip.addSectionHeading("Production", color, dark, Alignment.MID, opad);
                tooltip.beginIconGroup();
                tooltip.setIconSpacingMedium();
                float icons = 0;
                for (MutableCommodityQuantity curr : supply.values()) {
                    int qty = curr.getQuantity().getModifiedInt();
                    //if (qty <= 0) continue;

                    int normal = qty;
                    if (normal > 0) {
                        tooltip.addIcons(market.getCommodityData(curr.getCommodityId()), normal, IconRenderMode.NORMAL);
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
                    if (minus > 0 && mode == IndustryTooltipMode.NORMAL) {
                        tooltip.addIcons(market.getCommodityData(curr.getCommodityId()), minus, IconRenderMode.DIM_RED);
                    }
                    icons += normal + Math.max(0, minus);
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
                addImprovedSection(mode, tooltip, expanded);
            }

            tooltip.addPara("*Shown production and demand values are already adjusted based on current market size and local conditions.", gray, opad);
        }

        if (needToAddIndustry) {
            unapply();
            market.getIndustries().remove(this);
        }
        market = orig;
        market.setRetainSuppressedConditionsSetWhenEmpty(null);
        if (!needToAddIndustry) {
            reapply();
        }
    }
}
