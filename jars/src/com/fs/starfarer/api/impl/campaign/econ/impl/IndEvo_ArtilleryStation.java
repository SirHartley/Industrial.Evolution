package com.fs.starfarer.api.impl.campaign.econ.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.station.IndEvo_ArtilleryStationEntityPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;

public class IndEvo_ArtilleryStation extends BaseIndustry implements FleetEventListener {
    //we'll make 2 versions - one for industry and one for derelict interaction
    //the derelict one should probably extend the normal one

    public static float DEFENSE_BONUS_BASE = 0.5f;
    public static final float IMPROVE_RANGE_BONUS = 2000f;

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
            if(plugin != null) tooltip.addPara("Current range: %s", 10f, Misc.getHighlightColor(), (int) Math.round(plugin.getRange()) + " SU");
        }
    }

    @Override
    protected Object readResolve() {
        super.readResolve();
        return this;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Global.getSector().getEconomy().isSimMode()) return;

        if (stationEntity == null) {
            spawnStation();
        }

        if (stationFleet != null) {
            stationFleet.setAI(null);
            if (stationFleet.getOrbit() == null && stationEntity != null) {
                stationFleet.setCircularOrbit(stationEntity, 0, 0, 100);
            }
        }
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

        if (stationFleet != null ) stationFleet.setName(market.getName() + " " + getCurrentName() + " Station");
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

    public String getType(){
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

    public IndEvo_ArtilleryStationEntityPlugin getArtilleryPlugin(){
        if (stationEntity != null) return (IndEvo_ArtilleryStationEntityPlugin) stationEntity.getCustomPlugin();

        for (SectorEntityToken t : market.getConnectedEntities()){
            if (t.getCustomPlugin() instanceof IndEvo_ArtilleryStationEntityPlugin) return (IndEvo_ArtilleryStationEntityPlugin) t.getCustomPlugin();
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
        if (!Misc.hasOrbitalStation(market)) return "Requires an Orbital Station";
        return "Requires a functional spaceport";
    }

    @Override
    public boolean isAvailableToBuild() {
        //if (!market.hasTag(IndEvo_ids.TAG_ARTILLERY_STATION)) return false;

        boolean canBuild = false;
        for (Industry ind : market.getIndustries()) {
            if (ind == this) continue;
            if (!ind.isFunctional()) continue;
            if (ind.getSpec().hasTag(Industries.TAG_SPACEPORT)) {
                canBuild = true;
                break;
            }
        }

        return canBuild && Misc.hasOrbitalStation(market) && Global.getSettings().getBoolean("Enable_IndEvo_Artillery");
    }

    @Override
    public boolean showWhenUnavailable() {
        return market.hasTag(IndEvo_ids.TAG_ARTILLERY_STATION);
    }
}
