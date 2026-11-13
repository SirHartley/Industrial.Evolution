package indevo.industries.artillery.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.impl.campaign.terrain.RingSystemTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.artillery.conditions.ArtilleryStationCondition;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.LighthouseEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEntityPlugin;
import indevo.industries.artillery.scripts.ArtilleryStationScript;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.items.consumables.listeners.LocatorSystemRatingUpdater;
import indevo.utils.ModPlugin;

import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.Random;

import static indevo.industries.artillery.scripts.ArtilleryStationScript.SCRIPT_KEY;
import static indevo.industries.artillery.scripts.ArtilleryStationScript.TYPE_KEY;

public class ArtilleryStationPlacer {

    public enum VisionType {WATCHTOWER, LIGHTHOUSE, DRONES}

    public static final int MIN_LIGHTHOUSES_PER_SYSTEM = 4;
    public static final float LIGHTHOUSE_MIN_ORBIT = 1500f;
    public static final float LIGHTHOUSE_MAX_ORBIT = 16000f;
    public static final int MIN_DRONE_NESTS = 3;
    public static final int MAX_DRONE_NESTS = 6;
    public static final float EXTENT_PER_DRONE_NEST = 4000f;

    public static void placeCoreWorldArtilleries() {

        if (Global.getSector().getEconomy().getMarket("culann") == null
                || !Setting.ENABLE_ARTILLERY.enabled()
                || Global.getSector().getMemoryWithoutUpdate().contains("$IndEvo_placedArtilleries")) return;

        MarketAPI m = Global.getSector().getEconomy().getMarket("eochu_bres");
        addArtilleryToPlanet(m.getPrimaryEntity(), true);
        m.addIndustry(Ids.ARTILLERY_RAILGUN);
        m.getIndustry(Ids.ARTILLERY_RAILGUN).setAICoreId(Commodities.ALPHA_CORE);

        m = Global.getSector().getEconomy().getMarket("chicomoztoc");
        addArtilleryToPlanet(m.getPrimaryEntity(), true);
        m.addIndustry(Ids.ARTILLERY_MORTAR);

        m = Global.getSector().getEconomy().getMarket("kazeron");
        addArtilleryToPlanet(m.getPrimaryEntity(), true);
        m.addIndustry(Ids.ARTILLERY_MISSILE);
        m.getIndustry(Ids.ARTILLERY_MISSILE).setAICoreId(Commodities.GAMMA_CORE);

        m = Global.getSector().getEconomy().getMarket("sindria");
        addArtilleryToPlanet(m.getPrimaryEntity(), true);
        m.addIndustry(Ids.ARTILLERY_MISSILE);

        Global.getSector().getMemoryWithoutUpdate().set("$IndEvo_placedArtilleries", true);
    }

    public static void placeDerelictArtilleries() {
        if (!Setting.ENABLE_ARTILLERY.enabled() || Global.getSector().getMemoryWithoutUpdate().contains("$IndEvo_placedDerelictArtilleries"))
            return;

        int currentCount = 0;
        Random r = new Random();

        OUTER:
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == null
                    || s.getPlanets().isEmpty()
                    || !Misc.getMarketsInLocation(s).isEmpty()
                    || s.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                    || s.hasTag(Tags.THEME_HIDDEN)
                    || s.hasTag(Tags.THEME_SPECIAL)
                    || s.hasTag(Tags.SYSTEM_ABYSSAL)) continue;

            Constellation c = s.getConstellation();

            if (c != null){
                int starsPerStation = Setting.ARTILLERY_STARS_PER_STATION.intValue();
                int constellationStationCount = 0;

                float sysAmt = c.getSystems().size();
                int stnAmt = (int) Math.ceil(sysAmt / starsPerStation);

                for (StarSystemAPI sys : c.getSystems()) if (sys.hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY)) constellationStationCount++;
                if (constellationStationCount >= stnAmt) continue;
            }

            float baseMod = 0f;
            if (s.getTags().contains(Tags.THEME_REMNANT_RESURGENT)) baseMod += 0.2f;
            if (s.getTags().contains(Tags.THEME_DERELICT_CRYOSLEEPER)) baseMod += 0.2f;
            if (s.getTags().contains(Tags.THEME_RUINS)) baseMod += 0.02f;

            boolean hasRemnantStation = false;
            for (CampaignFleetAPI fleet : s.getFleets()) {
                if (fleet.isStationMode() && fleet.getFaction().getId().equals(Factions.REMNANTS))
                    hasRemnantStation = true;
            }

            float rating = LocatorSystemRatingUpdater.getRating(s);
            baseMod += rating * 0.002f;

            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null || p.hasTag(Tags.NOT_RANDOM_MISSION_TARGET)) continue;

                float planetMod = baseMod;
                planetMod += TechMining.getTechMiningRuinSizeModifier(p.getMarket()) * 0.1f;
                planetMod *= Setting.ARTILLERY_SPAWN_WEIGHT.value();

                if (r.nextFloat() < planetMod) {
                    if (hasRemnantStation) p.getMarket().getMemoryWithoutUpdate().set(TYPE_KEY, ArtilleryStationEntityPlugin.TYPE_MISSILE);

                    //addTestArtilleryToPlanet(p);
                    addArtilleryToPlanet(p, false);

                    ModPlugin.log("Placed artillery at " + p.getName() + " system: " + s.getName());
                    currentCount++;

                    continue OUTER;
                }
            }
        }

        ModPlugin.log("Placed " + currentCount + " Artillery Stations");
        Global.getSector().getMemoryWithoutUpdate().set("$IndEvo_placedDerelictArtilleries", true);
    }

    public static void addArtilleryToPlanet(SectorEntityToken planet, boolean isDestroyed) {
        if (!planet.hasScriptOfClass(ArtilleryStationScript.class) && !planet.hasTag(Ids.TAG_ENTITY_HAS_ARTILLERY_STATION)) {

            planet.addTag(Ids.TAG_ENTITY_HAS_ARTILLERY_STATION);

            ArtilleryStationScript script = new ArtilleryStationScript(planet.getMarket());
            script.setDestroyed(isDestroyed);
            planet.addScript(script);
            planet.getMemoryWithoutUpdate().set(SCRIPT_KEY, script);
            planet.getMarket().addTag(Ids.TAG_ARTILLERY_STATION);
            planet.addTag(Tags.NOT_RANDOM_MISSION_TARGET);
            planet.getContainingLocation().addTag(Ids.TAG_SYSTEM_HAS_ARTILLERY);

            StarSystemAPI starSystem = planet.getStarSystem();

            String faction = planet.getMarket() != null && (planet.getMarket().isPlanetConditionMarketOnly() || Factions.NEUTRAL.equals(planet.getMarket().getFactionId())) ? Ids.DERELICT_FACTION_ID : planet.getMarket().getFactionId();
            placeVision(starSystem, faction);

            if (!planet.getMarket().hasCondition(ArtilleryStationCondition.ID))
                planet.getMarket().addCondition(ArtilleryStationCondition.ID);
        }
    }

    public static class ArtilleryStationInteractionConfigGen implements FleetInteractionDialogPluginImpl.FIDConfigGen {
        public FleetInteractionDialogPluginImpl.FIDConfig createConfig() {
            FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();

            config.alwaysAttackVsAttack = true;
            config.leaveAlwaysAvailable = true;
            config.showFleetAttitude = false;
            config.showTransponderStatus = false;
            config.showEngageText = false;

            config.delegate = new FleetInteractionDialogPluginImpl.BaseFIDDelegate() {
                public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
                    new RemnantSeededFleetManager.RemnantFleetInteractionConfigGen().createConfig().delegate.
                            postPlayerSalvageGeneration(dialog, context, salvage);
                }
                public void notifyLeave(InteractionDialogAPI dialog) {
                }
                public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                    bcc.aiRetreatAllowed = false;
                    bcc.objectivesAllowed = false;
                }
            };
            return config;
        }
    }

    public static void placeVision(StarSystemAPI system, String factionId) {
        MemoryAPI mem = system.getMemoryWithoutUpdate();
        if (mem.contains(Ids.MEM_SYSTEM_ARTILLERY_VISION) || !system.getEntitiesWithTag(Ids.TAG_WATCHTOWER).isEmpty()) return;

        Random random = new Random();
        VisionType type = Ids.DERELICT_FACTION_ID.equals(factionId) ? pickVisionType(random) : VisionType.WATCHTOWER;
        mem.set(Ids.MEM_SYSTEM_ARTILLERY_VISION, type.name());

        switch (type) {
            case WATCHTOWER -> placeTowers(system, factionId, WatchtowerEntityPlugin.ENTITY_ID, random);
            case LIGHTHOUSE -> placeLighthouses(system, factionId, random);
            case DRONES -> placeDroneNet(system, factionId, random);
        }
    }

    private static VisionType pickVisionType(Random random) {
        WeightedRandomPicker<VisionType> picker = new WeightedRandomPicker<>(random);
        picker.add(VisionType.WATCHTOWER, Setting.ARTILLERY_VISION_WEIGHT_WATCHTOWER.value());
        picker.add(VisionType.LIGHTHOUSE, Setting.ARTILLERY_VISION_WEIGHT_LIGHTHOUSE.value());
        picker.add(VisionType.DRONES, Setting.ARTILLERY_VISION_WEIGHT_DRONES.value());

        VisionType type = picker.pick();
        return type != null ? type : VisionType.WATCHTOWER;
    }

    public static void placeLighthouses(StarSystemAPI system, String factionId, Random random) {
        int placed = placeTowers(system, factionId, LighthouseEntityPlugin.ENTITY_ID, random);
        int extra = MIN_LIGHTHOUSES_PER_SYSTEM - placed;
        if (extra <= 0) return;

        SectorEntityToken center = system.getCenter();
        float minRadius = getMinCenterOrbit(system);
        float extent = getCenterOrbitExtent(system, minRadius);

        for (int i = 0; i < extra; i++) {
            float radius = getSpreadRadius(minRadius, extent, i, extra, random);
            float orbitDays = radius / (20f + random.nextFloat() * 5f);

            SectorEntityToken t = addTower(system, factionId, LighthouseEntityPlugin.ENTITY_ID, null);
            t.setOrbit(Global.getFactory().createCircularOrbitWithSpin(center,
                    random.nextFloat() * 360f, radius, orbitDays, random.nextFloat() * 10f + 1f));
        }
    }

    public static void placeDroneNet(StarSystemAPI system, String factionId, Random random) {
        float minRadius = getMinCenterOrbit(system);
        float extent = getCenterOrbitExtent(system, minRadius);
        int count = Math.max(MIN_DRONE_NESTS, Math.min(MAX_DRONE_NESTS, Math.round(extent / EXTENT_PER_DRONE_NEST)));

        DroneNetScript net = DroneNetScript.getInstanceOrRegister(system, factionId);

        for (int i = 0; i < count; i++) {
            float radius = getSpreadRadius(minRadius, extent, i, count, random);
            float orbitDays = radius / (20f + random.nextFloat() * 5f);
            net.addNest(random.nextFloat() * 360f, radius, orbitDays, random);
        }
    }

    private static float getMinCenterOrbit(StarSystemAPI system) {
        float minRadius = LIGHTHOUSE_MIN_ORBIT;
        if (system.getStar() != null) minRadius += system.getStar().getRadius() * 2f;
        return minRadius;
    }

    private static float getCenterOrbitExtent(StarSystemAPI system, float minRadius) {
        return Math.max(minRadius * 2f, Math.min(LIGHTHOUSE_MAX_ORBIT, getSystemExtent(system)));
    }

    private static float getSpreadRadius(float minRadius, float extent, int index, int count, Random random) {
        return minRadius + (extent - minRadius) * (index + random.nextFloat()) / count;
    }

    private static float getSystemExtent(StarSystemAPI system) {
        Vector2f center = system.getCenter().getLocation();
        float extent = 0f;

        for (PlanetAPI planet : system.getPlanets()) extent = Math.max(extent, Misc.getDistance(center, planet.getLocation()));
        for (SectorEntityToken jumpPoint : system.getJumpPoints()) extent = Math.max(extent, Misc.getDistance(center, jumpPoint.getLocation()));

        return extent;
    }

    private static SectorEntityToken addTower(StarSystemAPI system, String factionId, String entityType, String name) {
        SectorEntityToken t = system.addCustomEntity(Misc.genUID(), name, entityType, factionId, null);
        if (Misc.getMarketsInLocation(system).isEmpty())
            MiscellaneousThemeGenerator.makeDiscoverable(t, 200f, 2000f);

        return t;
    }

    public static int placeTowers(StarSystemAPI system, String factionId, String entityType, Random random) {
        float minGap = 100f;
        FactionAPI faction = Global.getSector().getFaction(factionId);
        boolean watchtower = WatchtowerEntityPlugin.ENTITY_ID.equals(entityType);
        int placed = 0;

        //jump points
        for (SectorEntityToken t : system.getJumpPoints()) {
            WatchtowerEntityPlugin.spawn(t, faction, entityType);
            placed++;
        }

        //gates
        for (SectorEntityToken t : system.getEntitiesWithTag(Tags.GATE)){
            WatchtowerEntityPlugin.spawn(t, faction, entityType);
            placed++;
        }

        //gas giants
        for (PlanetAPI planet : system.getPlanets()) {
            if (planet.isStar()) continue;
            if (!planet.isGasGiant()) continue;

            float ow = BaseThemeGenerator.getOrbitalRadius(planet);
            List<BaseThemeGenerator.OrbitGap> gaps = BaseThemeGenerator.findGaps(planet, 100f, 100f + ow + minGap, minGap);
            BaseThemeGenerator.EntityLocation loc = createLocationAtRandomGap(random, planet, gaps, BaseThemeGenerator.LocationType.GAS_GIANT_ORBIT);
            if (loc != null) {
                addTower(system, factionId, entityType, null).setOrbit(loc.orbit);
                placed++;
            }
        }

        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            if (terrain.hasTag(Tags.ACCRETION_DISK)) continue;
            //if (terrain.getOrbit() == null || (terrain.getOrbit().getFocus() != null && !terrain.getOrbit().getFocus().isStar())) continue; //we only do debris fields around the sun
            if (terrain.getOrbitFocus() != null && (!terrain.getOrbitFocus().isStar() || !terrain.getOrbitFocus().isSystemCenter()))
                continue;

            CampaignTerrainPlugin plugin = terrain.getPlugin();

            if (plugin instanceof RingSystemTerrainPlugin) {
                RingSystemTerrainPlugin ring = (RingSystemTerrainPlugin) plugin;
                float start = ring.params.middleRadius - ring.params.bandWidthInEngine / 2f;
                List<BaseThemeGenerator.OrbitGap> gaps = BaseThemeGenerator.findGaps(terrain,
                        start - 100f, start + ring.params.bandWidthInEngine + 100f, minGap);

                BaseThemeGenerator.EntityLocation loc = createLocationAtRandomGap(random, terrain, gaps, BaseThemeGenerator.LocationType.IN_RING);
                if (loc != null) {
                    String name = watchtower ? faction.getDisplayName() + " Watchtower" : null;
                    addTower(system, factionId, entityType, name).setOrbit(loc.orbit);
                    placed++;
                    break;
                }
            }
        }

        return placed;
    }

    private static BaseThemeGenerator.EntityLocation createLocationAtRandomGap(Random random, SectorEntityToken center, List<BaseThemeGenerator.OrbitGap> gaps, BaseThemeGenerator.LocationType type) {
        if (gaps.isEmpty()) return null;
        if (random == null) random = StarSystemGenerator.random;
        WeightedRandomPicker<BaseThemeGenerator.OrbitGap> picker = new WeightedRandomPicker<BaseThemeGenerator.OrbitGap>(random);
        picker.addAll(gaps);
        BaseThemeGenerator.OrbitGap gap = picker.pick();
        return createLocationAtGap(random, center, gap, type);
    }

    private static BaseThemeGenerator.EntityLocation createLocationAtGap(Random random, SectorEntityToken center, BaseThemeGenerator.OrbitGap gap, BaseThemeGenerator.LocationType type) {
        if (gap != null) {
            if (random == null) random = StarSystemGenerator.random;
            BaseThemeGenerator.EntityLocation loc = new BaseThemeGenerator.EntityLocation();
            loc.type = type;
            float orbitRadius = gap.start + (gap.end - gap.start) * (0.25f + 0.5f * random.nextFloat());
            float orbitDays = orbitRadius / (20f + random.nextFloat() * 5f);

            loc.orbit = Global.getFactory().createCircularOrbitWithSpin(center,
                    random.nextFloat() * 360f, orbitRadius, orbitDays, random.nextFloat() * 10f + 1f);
            return loc;
        }
        return null;
    }
}
