package com.fs.starfarer.api.impl.campaign.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.station.IndEvo_ArtilleryStationEntityPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_ArtilleryStation;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

import static com.fs.starfarer.api.impl.campaign.econ.impl.derelicts.IndEvo_Ruins.INDUSTRY_ID_MEMORY_KEY;

public class IndEvo_ArtilleryStationCondition extends BaseHazardCondition implements FleetEventListener {

    public static final String ID = "IndEvo_ArtilleryStationCondition";
    public static final String TYPE_KEY = "$IndEvo_ArtilleryType";

    protected CampaignFleetAPI stationFleet = null;
    protected SectorEntityToken stationEntity = null;
    protected SectorEntityToken brokenStationEntity = null;

    public boolean isDestroyed = false;
    private String orbitMatched = null;

    @Override
    public void init(MarketAPI market, MarketConditionAPI condition) {
        super.init(market, condition);
        setType();
    }

    @Override
    public void apply(String id) {
        super.apply(id);

        if(brokenStationEntity != null && (orbitMatched == null || !orbitMatched.equals(IndEvo_ArtilleryStationEntityPlugin.getOrbitalStationAtMarket(market).getId()))) matchOrbitalstationOrbit(brokenStationEntity);
        if (!market.isPlanetConditionMarketOnly()) return;

        matchCommanderToAICore(Commodities.BETA_CORE);
        IndEvo_ArtilleryStationEntityPlugin plugin = getArtilleryPlugin();

        if (isDestroyed) {
            if (plugin != null) getArtilleryPlugin().setDisrupted(true);
            unapply(id);
        } else {
            applyCRToStation();
            if (plugin != null) getArtilleryPlugin().setDisrupted(false);
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        matchCommanderToAICore(null);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Global.getSector().getEconomy().isSimMode()) return;

        if (isDestroyed) {
            if (marketHasArtilleryIndustry()) removeBrokenStationEntity();
            else spawnBrokenStationEntityIfNeeded();

            return;
        }

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

    public boolean marketHasArtilleryIndustry() {
        boolean hasArty = false;

        if (!market.isPlanetConditionMarketOnly()) {
            for (Industry i : market.getIndustries()) {
                if (i instanceof IndEvo_ArtilleryStation) {
                    hasArty = true;
                    break;
                }
            }

            for (ConstructionQueue.ConstructionQueueItem i : market.getConstructionQueue().getItems()) {
                if (i.id.toLowerCase().contains("artillery")) {
                    hasArty = true;
                    break;
                }
            }
        }

        return hasArty;
    }

    public void removeBrokenStationEntity() {
        if (brokenStationEntity != null) {
            Misc.fadeAndExpire(brokenStationEntity, 0f);
            brokenStationEntity = null;
        }
    }

    public void spawnBrokenStationEntityIfNeeded() {
        if (brokenStationEntity == null) {
            SectorEntityToken brokenStation = market.getContainingLocation().addCustomEntity(Misc.genUID(), null, "IndEvo_DestroyedArtilleryStation", Factions.DERELICT, null);

            if (stationEntity != null) brokenStation.setOrbit(stationEntity.getOrbit());
            else if(!matchOrbitalstationOrbit(brokenStation)) addRandomOrbit(brokenStation); //matches station orbit, if it can't, adds random orbit

            brokenStationEntity = brokenStation;
        }

        removeStationEntityAndFleetIfNeeded();
    }

    public void addRandomOrbit(SectorEntityToken entity) {
        SectorEntityToken primaryEntity = market.getPrimaryEntity();

        float angle = (float) Math.random() * 360f;
        float radius = primaryEntity.getRadius() + 150f;
        float period = radius / 10f;

        entity.setCircularOrbitWithSpin(primaryEntity,
                angle,
                radius,
                period,
                5f,
                5f);
    }

    public boolean matchOrbitalstationOrbit(SectorEntityToken entity) {
        SectorEntityToken station = IndEvo_ArtilleryStationEntityPlugin.getOrbitalStationAtMarket(market);

        if (station == null) return false;

        SectorEntityToken primaryEntity = market.getPrimaryEntity();

        float angle = station.getCircularOrbitAngle() - 180;
        float radius = station.getCircularOrbitRadius();
        float period = station.getCircularOrbitPeriod();

        entity.setCircularOrbitWithSpin(primaryEntity,
                angle,
                radius,
                period,
                5f,
                5f);

        orbitMatched = station.getId();

        return true;
    }

    public void setType() {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        picker.addAll(Arrays.asList("mortar", "railgun", "missile"));
        market.getMemoryWithoutUpdate().set(TYPE_KEY, picker.pick());
    }

    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if(isDestroyed) tooltip.addPara("It has been rendered %s. Restoration might be possible, but will require a functional orbital station.",
                10f,
                Misc.getTextColor(),
                Misc.getHighlightColor(),
                new String[]{"unusable"});

        else tooltip.addPara("It will defend the planet from any hostile forces until disabled.",
                10f,
                Misc.getTextColor(),
                Misc.getHighlightColor(),
                new String[]{"access to exotic technologies"});

    }

    protected void applyCRToStation() {
        if (stationFleet != null) {
            float cr = 1f;

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

    protected void removeStationEntityAndFleetIfNeeded() {
        if (stationEntity != null) {
            IndEvo_modPlugin.log("removing artillery station at " + market.getName());

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

    protected void spawnStation() {

        FleetParamsV3 fParams = new FleetParamsV3(null, null,
                Factions.DERELICT,
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

        stationFleet.setName(market.getName() + " " + Misc.ucFirst(getType()) + " Station");
        stationEntity.setName(market.getName() + " " + Misc.ucFirst(getType()) + " Station");
    }

    protected void ensureStationEntityIsSetOrCreated() {
        if (stationEntity == null) {
            IndEvo_modPlugin.log("spawning artillery station at " + market.getName());

            stationEntity = IndEvo_ArtilleryStationEntityPlugin.placeAtMarket(market, getType(), true);
        }
    }

    public String getType() {
        return market.getMemoryWithoutUpdate().getString(TYPE_KEY);
    }

    public IndustrySpecAPI getSpec() {
        return Global.getSettings().getIndustrySpec("IndEvo_Artillery_" + getType());
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

        boolean skeletonMode = isDestroyed;

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

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (fleet != stationFleet) return; // shouldn't happen...

        // bug where somehow a station fleet can become empty as a result of combat
        // then its despawn() gets called every frame
        if (stationFleet.getMembersWithFightersCopy().isEmpty()) {
            matchStationAndCommanderToCurrentIndustry();
        }
        stationFleet.setAbortDespawn(true);

        isDestroyed = true;
        spawnBrokenStationEntityIfNeeded();
    }

    public IndEvo_ArtilleryStationEntityPlugin getArtilleryPlugin() {
        if (stationEntity != null) return (IndEvo_ArtilleryStationEntityPlugin) stationEntity.getCustomPlugin();

        for (SectorEntityToken t : market.getConnectedEntities()) {
            if (t.getCustomPlugin() instanceof IndEvo_ArtilleryStationEntityPlugin)
                return (IndEvo_ArtilleryStationEntityPlugin) t.getCustomPlugin();
        }

        return null;
    }
}