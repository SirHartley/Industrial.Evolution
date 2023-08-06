package indevo.exploration.stations;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageEntityGeneratorOld;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.minefields.conditions.MineFieldCondition;
import indevo.exploration.salvage.utils.IndEvo_SalvageSpecialAssigner;
import indevo.ids.Ids;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Settings;
import org.apache.log4j.Logger;

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.*;

public class DerelictStationPlacer {
    protected Random random;

    public static boolean DEBUG = Global.getSettings().isDevMode();
    public static final Logger log = Global.getLogger(DerelictStationPlacer.class);

    public static float ADDITIONAL_STATION_AMT = 0.3f;
    public static final float LAB_FACTOR = 0.4f;
    public static final float ARSENAL_FACTOR = 0.45f;
    public static final float PET_FACTOR = 0.5f;

    public DerelictStationPlacer() {
    }

    public void init() {
        if (!Settings.ENABLE_DERELICT_STATIONS) return;
        ADDITIONAL_STATION_AMT = Settings.DERELICT_STATION_AMOUNT;
        random = StarSystemGenerator.random;

        float totalStationAmt = 0;

        for (SectorEntityToken token : Global.getSector().getEntitiesWithTag(Tags.SALVAGEABLE)) {
            String id = token.getCustomEntityType();
            if (id.equals(Entities.STATION_MINING) ||
                    id.equals(Entities.STATION_RESEARCH) ||
                    id.equals(Entities.ORBITAL_HABITAT)) totalStationAmt++;
        }

        totalStationAmt *= ADDITIONAL_STATION_AMT;
        float labAmt = Math.round(totalStationAmt * LAB_FACTOR);
        float arsAmt = Math.round(totalStationAmt * ARSENAL_FACTOR);
        float petAmt = Math.round(totalStationAmt * PET_FACTOR);

        List<StarSystemAPI> systemList = new ArrayList<>(Global.getSector().getStarSystems());
        Collections.shuffle(systemList);

        float labChance = labAmt * 3 / systemList.size();
        float arsChance = arsAmt * 3 / systemList.size();
        float petChance = petAmt * 3 / systemList.size();

        log.info("DerelictStationPlacer");
        log.info("totals " + totalStationAmt
                + " labAmt" + labAmt + " labChance " + labChance
                + " petAmt " + petAmt + " petChance " + petChance
                + " arsAmt " + arsAmt  + " arsChance " + arsChance
                + " total systems " + systemList.size());

        for (StarSystemAPI s : systemList) {
            if (s == null) continue;
            if (!Global.getSector().getEconomy().getMarkets(s).isEmpty() || Global.getSector().getEconomy().getStarSystemsWithMarkets().contains(s) || s.getTags().contains(Tags.THEME_CORE_UNPOPULATED) || s.getTags().contains(Tags.THEME_CORE_POPULATED))
                continue;

            boolean hasTag = false;
            boolean hasStations = false;

            //skip any systems that already have stations
            for (SectorEntityToken token : s.getEntitiesWithTag("salvageable")) {
                String id = token.getId();
                if (id.equals(Entities.STATION_MINING) ||
                        id.equals(Entities.STATION_RESEARCH) ||
                        id.equals(Entities.ORBITAL_HABITAT)) {
                    hasStations = true;
                    break;
                }
            }

            if (hasStations) continue;
            BaseThemeGenerator.StarSystemData data = BaseThemeGenerator.computeSystemData(s);

            for (String tag : s.getTags()) {
                if (tag.toLowerCase().contains("theme")) {
                    switch (tag) {
                        case Tags.THEME_MISC:
                            if (labAmt > 0 && addLabStations(data, labChance * 0.5f)) labAmt--;
                            if (arsAmt > 0 && addArsenalStation(data, arsChance)) arsAmt--;
                            if (petAmt > 0 && addDerelictPetCenter(data, petChance * 0.5f)) petAmt--;
                            break;
                        case Tags.THEME_REMNANT:
                            if (labAmt > 0 && addLabStations(data, labChance * 3)) labAmt--;
                            if (arsAmt > 0 && addArsenalStation(data, arsChance)) arsAmt--;
                            break;
                        case Tags.THEME_RUINS:
                            if (arsAmt > 0 && addArsenalStation(data, arsChance * 2)) arsAmt--;
                            if (petAmt > 0 && addDerelictPetCenter(data, petChance * 2)) petAmt--;
                            break;
                        case Tags.THEME_DERELICT:
                            if (labAmt > 0 && addLabStations(data, labChance * 0.5f)) labAmt--;
                            break;
                    }

                    hasTag = true;
                }
            }

            if (!hasTag) {
                if (labAmt > 0 && addLabStations(data, labChance)) labAmt--;
            }

            if (labAmt <= 0 && arsAmt <= 0 && petAmt <= 0) break;
        }
    }

    public boolean addArsenalStation(BaseThemeGenerator.StarSystemData data, float chanceToAddAny) {
        if (random.nextFloat() >= chanceToAddAny) return false;

        String type = Ids.ARSENAL_ENTITY;
        BaseThemeGenerator.EntityLocation loc = pickCommonLocation(random, data.system, 100f, true, null);

        AddedEntity e = addStation(loc, data, type, Factions.NEUTRAL);

        if (Settings.ENABLE_MINEFIELDS && loc.type.equals(LocationType.PLANET_ORBIT)) {
            SectorEntityToken t = e.entity.getOrbitFocus();
            if (t != null) {
                //if it's a moon, add to primary entity instead
                if (t.getOrbitFocus() != null && t.getOrbitFocus() instanceof PlanetAPI && !t.getOrbitFocus().isStar() && !t.getOrbitFocus().isSystemCenter()) {
                    t = t.getOrbitFocus();
                }

                MarketAPI m = t.getMarket();
                if (m != null) {
                    m.addCondition(Ids.COND_MINERING);
                    ((MineFieldCondition) m.getCondition(Ids.COND_MINERING).getPlugin()).addMineField();
                    ModPlugin.log(data.system.getName() + " arsenal station, mine ring added");
                }
            }
        }

        return true;
    }

    public boolean addLabStations(BaseThemeGenerator.StarSystemData data, float chanceToAddAny) {
        if (random.nextFloat() >= chanceToAddAny) return false;

        LinkedHashMap<BaseThemeGenerator.LocationType, Float> weights = new LinkedHashMap<>();
        weights.put(BaseThemeGenerator.LocationType.IN_SMALL_NEBULA, 5f);
        weights.put(BaseThemeGenerator.LocationType.GAS_GIANT_ORBIT, 5f);
        weights.put(BaseThemeGenerator.LocationType.NEAR_STAR, 5f);
        weights.put(LocationType.L_POINT, 5f);
        weights.put(LocationType.IN_RING, 5f);
        WeightedRandomPicker<BaseThemeGenerator.EntityLocation> locs = getLocations(random, data.system, data.alreadyUsed, 100f, weights);
        BaseThemeGenerator.EntityLocation loc = locs.pick();

        if (loc != null) {
            addStation(loc, data, Ids.LAB_ENTITY, Factions.NEUTRAL);
            if (loc.orbit != null && loc.orbit.getFocus() instanceof PlanetAPI) {
                PlanetAPI planet = (PlanetAPI) loc.orbit.getFocus();
                if (!planet.isStar()) {
                    data.alreadyUsed.add(planet);
                }
            }
            return true;
        }

        return false;
    }

    public boolean addDerelictPetCenter(BaseThemeGenerator.StarSystemData data, float chanceToAddAny) {
        if (random.nextFloat() >= chanceToAddAny) return false;

        String type = Ids.ABANDONED_PETSHOP_ENTITY;
        BaseThemeGenerator.EntityLocation loc = pickCommonLocation(random, data.system, 100f, false, null);
        addStation(loc, data, type, Factions.NEUTRAL);

        return true;
    }

    public BaseThemeGenerator.AddedEntity addStation(BaseThemeGenerator.EntityLocation loc, BaseThemeGenerator.StarSystemData data, String customEntityId, String factionId) {
        if (loc == null) return null;

        BaseThemeGenerator.AddedEntity station = addEntity(data.system, loc, customEntityId, factionId);
        if (station != null) {
            data.generated.add(station);
        }


        SectorEntityToken focus = station.entity.getOrbitFocus();

        if (focus instanceof PlanetAPI) {
            PlanetAPI planet = (PlanetAPI) focus;
            data.alreadyUsed.add(planet);

            boolean nearStar = planet.isStar() && station.entity.getOrbit() != null && station.entity.getCircularOrbitRadius() < 5000;

            if (planet.isStar() && !nearStar) {
//				station.entity.setFacing(random.nextFloat() * 360f);
//				convertOrbitNoSpin(station.entity);
            } else {
                convertOrbitPointingDown(station.entity);
            }
        }

//		station.entity.getMemoryWithoutUpdate().set(MemFlags.SALVAGE_DEFENDER_FACTION, Factions.REMNANTS);
//		station.entity.getMemoryWithoutUpdate().set(MemFlags.SALVAGE_DEFENDER_PROB, 1f);

        if (focus != null && focus.getStarSystem() != null)
            log.info("IDENT_INDEVO Adding " + customEntityId + " to " + focus.getStarSystem());
        return station;
    }

    public static BaseThemeGenerator.AddedEntity addEntity(StarSystemAPI system, BaseThemeGenerator.EntityLocation loc, String hullSize, String faction) {
        if (loc != null) {
            SectorEntityToken entity = addSalvageEntity(system, hullSize, faction);
            if (loc.orbit != null) {
                entity.setOrbit(loc.orbit);
                loc.orbit.setEntity(entity);
            } else {
                entity.setOrbit(null);
                entity.getLocation().set(loc.location);
            }
            return new AddedEntity(entity, loc, hullSize);
        }
        return null;
    }

    public static SectorEntityToken addSalvageEntity(LocationAPI location, String id, String faction) {
        return addSalvageEntity(location, id, faction, null);
    }

    public static SectorEntityToken addSalvageEntity(LocationAPI location, String id, String faction, Object pluginParams) {
        SalvageEntityGenDataSpec spec = SalvageEntityGeneratorOld.getSalvageSpec(id);

        CustomCampaignEntityAPI entity = location.addCustomEntity(null, spec.getNameOverride(), id, faction, pluginParams);

        if (spec.getRadiusOverride() > 0) {
            entity.setRadius(spec.getRadiusOverride());
        }

        switch (spec.getType()) {
            case ALWAYS_VISIBLE:
                entity.setSensorProfile(null);
                entity.setDiscoverable(null);
                break;
            case DISCOVERABLE:
                entity.setSensorProfile(1f);
                entity.setDiscoverable(true);
                break;
            case NOT_DISCOVERABLE:
                entity.setSensorProfile(1f);
                entity.setDiscoverable(false);
                break;
        }

        long seed = StarSystemGenerator.random.nextLong();
        entity.getMemoryWithoutUpdate().set(MemFlags.SALVAGE_SEED, seed);

        entity.getDetectedRangeMod().modifyFlat("gen", spec.getDetectionRange());

        return entity;
    }

    public static void addDebugStation() {
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(100000000f);

        StarSystemAPI loc = Global.getSector().getPlayerFleet().getStarSystem();

        CustomCampaignEntityAPI entity = addStationToFirstPlanet(loc, 100f, Ids.ARSENAL_ENTITY);

        WeightedRandomPicker<SalvageSpecialAssigner.SpecialCreator> picker = new WeightedRandomPicker<>(StarSystemGenerator.random);
        Random random = StarSystemGenerator.random;

        //picker.add(new IndEvo_SalvageSpecialAssigner.CreditStashSpecialCreator(random, 80000f, 100000f, PATROL_SMALL, 4, 25) );
        //picker.add(new IndEvo_SalvageSpecialAssigner.ExtraFuelSpecialCreator(random, 200, 1000));
        //picker.add(new IndEvo_SalvageSpecialAssigner.JuicyRumorsSpecialCreator(random, 3, 7));
        //picker.add(new IndEvo_SalvageSpecialAssigner.MarineRecoverySpecialCreator(random, 10, 100));
        //picker.add(new IndEvo_SalvageSpecialAssigner.DroneSurveyDataSpecialCreator(random, 1, 10));
        //picker.add(new IndEvo_SalvageSpecialAssigner.DModRepairSpecialCreator(random, 1, 4));
        //picker.add(new IndEvo_SalvageSpecialAssigner.ShipRouletteSpecialCreator(random, 1, 4));
        //picker.add(new IndEvo_SalvageSpecialAssigner.ChooseBlueprintSpecialCreator(random, ShipAPI.HullSize.CRUISER, 3,4));
        //picker.add(new IndEvo_SalvageSpecialAssigner.VPCSpecialCreator(random, 0.1f, 0.9f));
        picker.add(new IndEvo_SalvageSpecialAssigner.PrintShipSpecialCreator(random, 1, 3));
        //picker.add(new IndEvo_SalvageSpecialAssigner.ItemChoiceSpecialCreator(random, 0.1f, 0.95f));

        SalvageSpecialAssigner.SpecialCreator creator = picker.pick();
        Object specialData = creator.createSpecial(entity, new SalvageSpecialAssigner.SpecialCreationContext());

        if (specialData != null) {
            Misc.setSalvageSpecial(entity, specialData);
        }
    }

    public static CustomCampaignEntityAPI addStationToFirstPlanet(StarSystemAPI system, Float orbitRadius, String customEntityId) {
        CustomCampaignEntityAPI entity = system.addCustomEntity(Misc.genUID(), null, customEntityId, Factions.NEUTRAL);
        long seed = StarSystemGenerator.random.nextLong();
        entity.getMemoryWithoutUpdate().set(MemFlags.SALVAGE_SEED, seed);
        entity.setCircularOrbit(system.getPlanets().get(1), 0f, system.getPlanets().get(1).getRadius() + orbitRadius, 31);

        return entity;
    }

}
