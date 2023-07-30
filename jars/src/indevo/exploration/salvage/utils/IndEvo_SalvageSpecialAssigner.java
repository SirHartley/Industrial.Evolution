package indevo.exploration.salvage.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.DropGroupRow;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SleeperPodsSpecial;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.salvage.specials.*;
import indevo.ids.Ids;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.utils.helper.IndustryHelper;
import org.apache.log4j.Logger;

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.ids.FleetTypes.PATROL_MEDIUM;
import static com.fs.starfarer.api.impl.campaign.ids.FleetTypes.PATROL_SMALL;
import static com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner.*;

public class IndEvo_SalvageSpecialAssigner {

    public static final Logger log = Global.getLogger(IndEvo_SalvageSpecialAssigner.class);
    public static final float EXISTING_SPECIAL_REMOVAL_CHANCE = 0.4f;
    public static final float CACHE_EXISTING_SPECIAL_ADDITION_CHANCE = 0.8f;
    public float specialApplicationChance;

    public final Map<String, Float> specialMap = new HashMap<>();
    public final Map<String, Float> removedSpecialMap = new HashMap<>();

    public void init() {
        Random random = StarSystemGenerator.random;
        log.info("IndEvo Starting salvage special seeding");
        specialApplicationChance = Global.getSettings().getFloat("IndEvo_SpecialApplicationChance");

        for (SectorEntityToken entity : Global.getSector().getEntitiesWithTag(Tags.SALVAGEABLE)) {
            addOrReplaceSpecial(entity, random);
        }

        for (SectorEntityToken entity : Global.getSector().getEntitiesWithTag(Tags.PLANET)) {
            if (TechMining.getTechMiningRuinSizeModifier(entity.getMarket()) > 0) addOrReplaceSpecial(entity, random);
        }

        log.info("Added Totals:");
        for (Map.Entry<String, Float> e : specialMap.entrySet()) {
            log.info(e.getKey() + " - " + Math.round(e.getValue()));
        }
    }

    private boolean isCacheEntity(SectorEntityToken entity) {
        for (String tag : entity.getTags()) {
            if (tag.contains("cache")) return true;
        }
        return false;
    }

    private void addOrReplaceSpecial(SectorEntityToken entity, Random random) {
        boolean canAssignToCache = !isCacheEntity(entity) || random.nextFloat() > CACHE_EXISTING_SPECIAL_ADDITION_CHANCE;

        //if it doesn't have one, and it's not a cache/the cache gets overridden:
        if (Misc.getSalvageSpecial(entity) == null && canAssignToCache && random.nextFloat() < specialApplicationChance)
            assignSpecial(entity);

            //else if chance says the special gets replaced (40% chance), for cache or no cache
        else if (!(Misc.getSalvageSpecial(entity) instanceof ShipRecoverySpecial.ShipRecoverySpecialData) && random.nextFloat() < EXISTING_SPECIAL_REMOVAL_CHANCE) {
            //removeSpecial(entity);
            assignSpecial(entity);
        }
    }

    private void assignSpecial(SectorEntityToken entity) {
        SalvageSpecialAssigner.SpecialCreator creator = pickSpecialFor(entity);
        if (creator == null) return;

        Object specialData = creator.createSpecial(entity, new SalvageSpecialAssigner.SpecialCreationContext());

        if (specialData != null) {
            //log.info("Adding " + specialData.getClass().getName() + " to " + entity.getCustomEntityType());
            IndustryHelper.addOrIncrement(specialMap, specialData.getClass().getName(), 1);

            Misc.setSalvageSpecial(entity, specialData);
        }
    }

    public static SalvageSpecialAssigner.SpecialCreator pickSpecialFor(SectorEntityToken entity) {

        WeightedRandomPicker<SalvageSpecialAssigner.SpecialCreator> picker = new WeightedRandomPicker<>(StarSystemGenerator.random);
        Random random = StarSystemGenerator.random;
        String type = entity.getCustomEntityType();
        Misc.getSalvageSpecial(entity);

        WeightedRandomPicker<String> recoverableShipFactions = getNearbyFactions(random, entity);

        if (entity.getContainingLocation().hasTag(Tags.THEME_REMNANT)) {
            recoverableShipFactions = Misc.createStringPicker(random,
                    Factions.TRITACHYON, 10f, Factions.HEGEMONY, 7f, Factions.INDEPENDENT, 3f);
        }

        WeightedRandomPicker<String> remnantsFaction = Misc.createStringPicker(random, Factions.REMNANTS, 10f);

        WeightedRandomPicker<String> trapFactions = Misc.createStringPicker(random, Factions.PIRATES, 10f);
        if (entity.getContainingLocation().hasTag(Tags.THEME_REMNANT_SUPPRESSED) ||
                entity.getContainingLocation().hasTag(Tags.THEME_REMNANT_RESURGENT)) {
            trapFactions = remnantsFaction;
        }

        WeightedRandomPicker<String> officerFactions = recoverableShipFactions;
        WeightedRandomPicker<String> valuableCargo = getValuableCargo(random);
        WeightedRandomPicker<String> industryCargo = getIndustryCargo(random);


        // ruins on a planet
        if (entity instanceof PlanetAPI) {
            float sizeMult = TechMining.getTechMiningRuinSizeModifier(entity.getMarket());

            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 25f);

            picker.add(new CreditStashSpecialCreator(random, 5000f, 30000f, PATROL_SMALL, 4, 25), 10f);
            picker.add(new ExtraFuelSpecialCreator(random, 200, sizeMult > 0.5f ? 2000 : 1000), 10f);
            picker.add(new JuicyRumorsSpecialCreator(random, 3, sizeMult > 0.5f ? 10 : 7), 7f);
            if (entity.getStarSystem().hasTag(Tags.THEME_DERELICT))
                picker.add(new DroneSurveyDataSpecialCreator(random, 1, 5), 10f);

            picker.add(new DModRepairSpecialCreator(random, 1, sizeMult > 0.5f ? 3 : 1), 5f);
            picker.add(new ShipRouletteSpecialCreator(random, 1, 4), 5f);
            picker.add(new ChooseBlueprintSpecialCreator(random, ShipAPI.HullSize.CRUISER, 3, 4), 5f);
            picker.add(new VPCSpecialCreator(random, 0.1f, 0.9f * sizeMult), 5f);
            if (entity.getStarSystem().hasTag(Tags.THEME_REMNANT) && sizeMult > 0.2f)
                picker.add(new PrintShipSpecialCreator(random, 1, 3), 1f);
        }

        // derelict ship
        if (entity.getCustomPlugin() instanceof DerelictShipEntityPlugin || Entities.WRECK.equals(type)) {
            DerelictShipEntityPlugin plugin = (DerelictShipEntityPlugin) entity.getCustomPlugin();
            ShipRecoverySpecial.PerShipData shipData = plugin.getData().ship;
            ShipVariantAPI variant = shipData.variant;
            if (variant == null && shipData.variantId != null) {
                variant = Global.getSettings().getVariant(shipData.variantId);
            }

            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 40f);
            picker.add(new DroneSurveyDataSpecialCreator(random, 1, 7), 10f);
            picker.add(new JuicyRumorsSpecialCreator(random, 5, 10), 10f);

            //this is now vanilla
            /*if (variant != null){
                float p = variant.getHullSpec().getMaxCrew();
                picker.add(new MarineRecoverySpecialCreator(random, Math.round(p*0.25f), Math.round(p*0.7f)), 10f);
            }*/

            if (entity.getOrbit() != null) {
                picker.add(new CreditStashSpecialCreator(random, 10000f, 40000f, PATROL_SMALL, 4, 25), 10f);
            }
        }

        // debris field
        boolean debris = entity instanceof CampaignTerrainAPI &&
                ((CampaignTerrainAPI) entity).getPlugin() instanceof DebrisFieldTerrainPlugin;
        if (debris) {
            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 30);
            picker.add(new CreditStashSpecialCreator(random, 5000f, 30000f, PATROL_SMALL, 4, 25), 10);
            picker.add(new JuicyRumorsSpecialCreator(random, 2, 8), 7f);
        }

        if (Entities.STATION_MINING_REMNANT.equals(type)) {
            picker.add(new NothingSpecialCreator(), 30f);
            picker.add(new ItemChoiceSpecialCreator(random, 0.2f, 0.95f), 5f);
            picker.add(new VPCSpecialCreator(random, 0.2f, 0.6f), 5f);
            picker.add(new CreditStashSpecialCreator(random, 20000f, 60000f, PATROL_SMALL, 4, 25), 10f);
            picker.add(new DModRepairSpecialCreator(random, 1, 2), 10f);
            picker.add(new ExtraFuelSpecialCreator(random, 200, 1500), 10f);
            picker.add(new JuicyRumorsSpecialCreator(random, 5, 10), 5f);
        }

        if (Entities.STATION_RESEARCH_REMNANT.equals(type)) {
            picker.add(new NothingSpecialCreator(), 25f);
            picker.add(new ItemChoiceSpecialCreator(random, 0.1f, 0.4f), 5f);
            picker.add(new ChooseBlueprintSpecialCreator(random, ShipAPI.HullSize.CAPITAL_SHIP, 3, 4), 15f);
            picker.add(new VPCSpecialCreator(random, 0.6f, 1f), 10f);
            picker.add(new PrintShipSpecialCreator(random, 1, 3), 7f);
            picker.add(new ShipRouletteSpecialCreator(random, 0, 3), 15f);
        }

        if (Entities.ORBITAL_HABITAT_REMNANT.equals(type)) {
            picker.add(new NothingSpecialCreator(), 30f);
            picker.add(new ItemChoiceSpecialCreator(random, 0.2f, 1f), 5f);
            picker.add(new ChooseBlueprintSpecialCreator(random, ShipAPI.HullSize.DESTROYER, 2, 3), 10f);
            picker.add(new CreditStashSpecialCreator(random, 15000f, 70000f, PATROL_MEDIUM, 10, 16), 5f);
            picker.add(new ExtraFuelSpecialCreator(random, 100, 1000), 15f);
            picker.add(new DModRepairSpecialCreator(random, 1, 2), 10f);
            picker.add(new JuicyRumorsSpecialCreator(random, 5, 10), 10f);
        }

        if (Ids.LAB_ENTITY.equals(type)) {
            picker.add(new ItemChoiceSpecialCreator(random, 0.1f, 0.4f), 5f);
            picker.add(new ChooseBlueprintSpecialCreator(random, ShipAPI.HullSize.CAPITAL_SHIP, 3, 4), 10f);
            picker.add(new PrintShipSpecialCreator(random, 0, 2), 20f);
            picker.add(new ShipRouletteSpecialCreator(random, 0, 1), 10f);

        }

        if (Ids.ARSENAL_ENTITY.equals(type)) {
            picker.add(new NothingSpecialCreator(), 20f);
            picker.add(new ItemChoiceSpecialCreator(random, 0.2f, 0.95f), 5f);
            picker.add(new ChooseBlueprintSpecialCreator(random, ShipAPI.HullSize.CRUISER, 2, 4), 10f);
            picker.add(new VPCSpecialCreator(random, 0.3f, 0.7f), 10f);
            picker.add(new DModRepairSpecialCreator(random, 2, 3), 10f);
            picker.add(new ExtraFuelSpecialCreator(random, 300, 1500), 5f);
            picker.add(new SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.MARINES, 100, 500, null), 11f);
            picker.add(new SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.ORGANS, 50, 250, null), 4f);
            picker.add(new SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.OFFICER, STANDARD_PODS_OFFICER_LEVEL, Global.getSettings().getInt("exceptionalSleeperPodsOfficerLevel"), officerFactions), 1f);
            picker.add(new ShipRouletteSpecialCreator(random, 2, 5), 15f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.LARGE, recoverableShipFactions), 2f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.MEDIUM, recoverableShipFactions), 2f);
        }

        if (Ids.ABANDONED_PETSHOP_ENTITY.equals(type)){
            picker.add(new NothingSpecialCreator(), 30f);
            picker.add(new ItemChoiceSpecialCreator(random, 0.2f, 1f), 2f);
            picker.add(new CreditStashSpecialCreator(random, 15000f, 70000f, PATROL_MEDIUM, 10, 16), 5f);
            picker.add(new JuicyRumorsSpecialCreator(random, 5, 10), 10f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.CIVILIAN, recoverableShipFactions), 8f);
            picker.add(new SalvageSpecialAssigner.TransmitterTrapSpecialCreator(random, 0.5f, FleetTypes.PATROL_SMALL, trapFactions, 4, 8), 5f);
        }

        List<String> weapons = Arrays.asList(Entities.WEAPONS_CACHE, Entities.WEAPONS_CACHE_HIGH, Entities.WEAPONS_CACHE_LOW, Entities.WEAPONS_CACHE_REMNANT);
        if (weapons.contains(type)) {
            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 30f);
            picker.add(new SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.MARINES, 10, 100, null), 6f);
            picker.add(new SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.ORGANS, 5, 50, null), 4f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.OFFICER, 1, 15, officerFactions), 1f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.ADMIN, 1, 5, officerFactions), 0.2f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.SMALL, recoverableShipFactions), 10f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.OFFICER, 1, 15, officerFactions), 2f);
            picker.add(new SalvageSpecialAssigner.CargoManifestSpecialCreator(random, valuableCargo, 10, 30), 10f);
            picker.add(new SalvageSpecialAssigner.TransmitterTrapSpecialCreator(random, 0.5f, FleetTypes.PATROL_SMALL, trapFactions, 4, 8), 10f);
        }

        List<String> weaponsSmall = Arrays.asList(Entities.WEAPONS_CACHE_SMALL, Entities.WEAPONS_CACHE_SMALL_HIGH,
                Entities.WEAPONS_CACHE_SMALL_LOW, Entities.WEAPONS_CACHE_SMALL_REMNANT);
        if (weaponsSmall.contains(type)) {
            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 30f);
            picker.add(new SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.MARINES, 5, 60, null), 6f);
            picker.add(new SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.ORGANS, 5, 25, null), 4f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.SMALL, recoverableShipFactions), 10f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.OFFICER, 1, 15, officerFactions), 1f);
            picker.add(new SalvageSpecialAssigner.CargoManifestSpecialCreator(random, valuableCargo, 10, 30), 10f);
            picker.add(new SalvageSpecialAssigner.TransmitterTrapSpecialCreator(random, 0.5f, FleetTypes.PATROL_SMALL, trapFactions, 4, 8), 10f);
        }


        List<String> supplies = Collections.singletonList(Entities.SUPPLY_CACHE);
        if (supplies.contains(type)) {
            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 30f);
            picker.add(new ExtraFuelSpecialCreator(random, 100, 300), 20f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.SMALL, recoverableShipFactions), 10f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.OFFICER, 1, 15, officerFactions), 1f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.ADMIN, 1, 5, officerFactions), 0.2f);
            picker.add(new SalvageSpecialAssigner.CargoManifestSpecialCreator(random, valuableCargo, 10, 30), 10f);
            picker.add(new SalvageSpecialAssigner.TransmitterTrapSpecialCreator(random, 0.5f, FleetTypes.PATROL_SMALL, trapFactions, 4, 8), 10f);
        }

        List<String> suppliesSmall = Collections.singletonList(Entities.SUPPLY_CACHE_SMALL);
        if (suppliesSmall.contains(type)) {
            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 30f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.SMALL, recoverableShipFactions), 10f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.OFFICER, 1, 15, officerFactions), 1f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.ADMIN, 1, 5, officerFactions), 0.2f);
            picker.add(new SalvageSpecialAssigner.CargoManifestSpecialCreator(random, valuableCargo, 10, 30), 10f);
            picker.add(new SalvageSpecialAssigner.TransmitterTrapSpecialCreator(random, 0.5f, FleetTypes.PATROL_SMALL, trapFactions, 4, 8), 10f);
        }


        List<String> equipment = Collections.singletonList(Entities.EQUIPMENT_CACHE);
        if (equipment.contains(type)) {
            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 30f);
            picker.add(new CreditStashSpecialCreator(random, 5000f, 20000f, PATROL_SMALL, 4, 25), 10f);
            picker.add(new JuicyRumorsSpecialCreator(random, 3, 8), 10f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.SMALL, recoverableShipFactions), 10f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.OFFICER, 1, 15, officerFactions), 1f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.ADMIN, 1, 5, officerFactions), 0.2f);
            picker.add(new SalvageSpecialAssigner.CargoManifestSpecialCreator(random, industryCargo, 10, 30), 10f);
            picker.add(new SalvageSpecialAssigner.TransmitterTrapSpecialCreator(random, 0.5f, FleetTypes.PATROL_SMALL, trapFactions, 4, 8), 10f);
        }

        List<String> equipmentSmall = Collections.singletonList(Entities.EQUIPMENT_CACHE_SMALL);
        if (equipmentSmall.contains(type)) {
            picker.add(new SalvageSpecialAssigner.NothingSpecialCreator(), 30f);
            picker.add(new CreditStashSpecialCreator(random, 5000f, 15000f, PATROL_SMALL, 4, 25), 10f);
            picker.add(new JuicyRumorsSpecialCreator(random, 3, 8), 10f);
            picker.add(new SalvageSpecialAssigner.ShipRecoverySpecialCreator(random, 1, 1, false, DerelictShipEntityPlugin.DerelictType.SMALL, recoverableShipFactions), 10f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.OFFICER, 1, 15, officerFactions), 1f);
            picker.add(new SalvageSpecialAssigner.SleeperPodsSpecialCreator(random, SleeperPodsSpecial.SleeperSpecialType.ADMIN, 1, 5, officerFactions), 0.2f);
            picker.add(new SalvageSpecialAssigner.CargoManifestSpecialCreator(random, industryCargo, 10, 30), 10f);
            picker.add(new SalvageSpecialAssigner.TransmitterTrapSpecialCreator(random, 0.5f, FleetTypes.PATROL_SMALL, trapFactions, 4, 8), 10f);
        }

        return picker.pick();
    }

    public static class ItemChoiceSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final float minEfficiency;
        private final float maxEfficiency;

        public ItemChoiceSpecialCreator(Random random, float minExplosionChance, float maxExplosionChance) {
            this.random = random;
            this.minEfficiency = minExplosionChance;
            this.maxEfficiency = maxExplosionChance;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            float explosionChance = minEfficiency + random.nextFloat() * (maxEfficiency - minEfficiency);

            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);

            for (DropGroupRow r : DropGroupRow.getPicker("rare_tech").getItems()) {
                if (r.isNothing()) continue;
                if (r.isSpecialItem() && r.getSpecialItemId() != null && !r.getSpecialItemId().isEmpty())
                    picker.add(r.getSpecialItemId());
            }

            for (DropGroupRow r : DropGroupRow.getPicker("indEvo_tech_event").getItems()) {
                if (r.isNothing()) continue;
                if (r.isSpecialItem() && r.getSpecialItemId() != null && !r.getSpecialItemId().isEmpty())
                    picker.add(r.getSpecialItemId());
            }

            //used to crash because the chosen item was "", no idea what happened, but we'll pick as long as we have to god damn it (or 10 times)
            String item = picker.pick();
            int safeguard = 0;

            while (item == null || item.isEmpty() || safeguard > 10) {
                item = picker.pick();
                safeguard++;
                log.info("Repicking for itemChoiceSpecial: " + safeguard);
            }

            return new ItemChoiceSpecial.ItemChoiceSpecialData(item, explosionChance);
        }
    }

    public static class ShipRouletteSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final int min;
        private final int max;

        public ShipRouletteSpecialCreator(Random random, int min, int max) {
            this.random = random;
            this.min = min;
            this.max = max;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            int level = min + random.nextInt(max - min + 1);
            return new ShipRouletteSpecial.ShipRouletteSpecialData(level);
        }
    }

    public static class PrintShipSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final int min;
        private final int max;

        public PrintShipSpecialCreator(Random random, int min, int max) {
            this.random = random;
            this.min = min;
            this.max = max;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            int level = min + random.nextInt(max - min + 1);
            return new PrintShipSpecial.PrintShipSpecialData(level);
        }
    }

    public static class JuicyRumorsSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final int min;
        private final int max;

        public JuicyRumorsSpecialCreator(Random random, int min, int max) {
            this.random = random;
            this.min = min;
            this.max = max;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            List<FactionAPI> inactiveFactions = AmbassadorPersonManager.getListOfIncativeFactions();

            WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>(random);
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (inactiveFactions.contains(faction)) continue;
                picker.add(faction);
            }

            FactionAPI faction_1 = picker.pick();
            float amt = (min + random.nextInt(max - min + 1)) / 100f;

            return new JuicyRumorsSpecial.JuicyRumorsSpecialData(faction_1.getId(), amt);
        }
    }

    public static class ExtraFuelSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private static final float SUCCESS_CHANCE = 0.7f;
        private final Random random;
        private final int min;
        private final int max;

        public ExtraFuelSpecialCreator(Random random, int min, int max) {
            this.random = random;
            this.min = min;
            this.max = max;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            ExtraFuelSpecial.ExtraFuelSpecialType type = random.nextFloat() > SUCCESS_CHANCE ? ExtraFuelSpecial.ExtraFuelSpecialType.EXPLOSION : ExtraFuelSpecial.ExtraFuelSpecialType.SUCCESS;
            return new ExtraFuelSpecial.ExtraFuelSpecialData(type, min, max);
        }
    }

    public static class DroneSurveyDataSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final int min;
        private final int max;

        public DroneSurveyDataSpecialCreator(Random random, int min, int max) {
            this.random = random;
            this.min = min;
            this.max = max;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            int level = min + random.nextInt(max - min + 1);
            return new DroneSurveyDataSpecial.DroneSurveyDataSpecialData(level);
        }
    }

    public static class DModRepairSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final int min;
        private final int max;

        public DModRepairSpecialCreator(Random random, int min, int max) {
            this.random = random;
            this.min = min;
            this.max = max;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            int level = min + random.nextInt(max - min + 1);
            return new DModRepairSpecial.DModRepairSpecialData(level);
        }
    }

    public static class ChooseBlueprintSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final int minAmt;
        private final int maxAmt;
        final ShipAPI.HullSize maxHullSIze;

        public ChooseBlueprintSpecialCreator(Random random, ShipAPI.HullSize maximumHullSize, int minAmt, int maxAmt) {
            this.random = random;
            this.minAmt = minAmt;
            this.maxAmt = maxAmt;
            this.maxHullSIze = maximumHullSize;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            ShipAPI.HullSize hullSize;
            int amt;
            float chanceMod = 0.2f;

            WeightedRandomPicker<ShipAPI.HullSize> picker = new WeightedRandomPicker<>();
            picker.add(ShipAPI.HullSize.FRIGATE, maxHullSIze.equals(ShipAPI.HullSize.FRIGATE) ? chanceMod : 0.4f * (1 - chanceMod));

            if (!maxHullSIze.equals(ShipAPI.HullSize.FRIGATE)) {
                picker.add(ShipAPI.HullSize.DESTROYER, maxHullSIze.equals(ShipAPI.HullSize.DESTROYER) ? chanceMod : 0.3f * (1 - chanceMod));

                if (!maxHullSIze.equals(ShipAPI.HullSize.DESTROYER)) {
                    picker.add(ShipAPI.HullSize.CRUISER, maxHullSIze.equals(ShipAPI.HullSize.CRUISER) ? chanceMod : 0.3f * (1 - chanceMod));

                    if (!maxHullSIze.equals(ShipAPI.HullSize.CRUISER)) {
                        picker.add(ShipAPI.HullSize.CAPITAL_SHIP, maxHullSIze.equals(ShipAPI.HullSize.CAPITAL_SHIP) ? chanceMod : 0.15f * (1 - chanceMod));
                    }
                }
            }

            hullSize = picker.pick(random);
            amt = minAmt + random.nextInt(maxAmt - minAmt + 1);

            return new ChooseBlueprintSpecial.ChooseBlueprintSpecialData(hullSize, amt);
        }
    }

    public static class VPCSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final float minEfficiency;
        private final float maxEfficiency;

        public VPCSpecialCreator(Random random, float minEfficiency, float maxEfficiency) {
            this.random = random;
            this.minEfficiency = minEfficiency;
            this.maxEfficiency = maxEfficiency;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            float efficiency = minEfficiency + random.nextFloat() * (maxEfficiency - minEfficiency);
            return new ConvertVPCSpecial.ConvertVPCSpecialData(efficiency);
        }
    }

    public static class CreditStashSpecialCreator implements SalvageSpecialAssigner.SpecialCreator {
        private final Random random;
        private final String factionId;
        private final int minPts;
        private final int maxPts;
        private final float minCreditAmount; //from 1 to 1000000
        private final float maxCeditAmount;
        private final String fleetType;

        public CreditStashSpecialCreator(Random random, float minCreditAmount, float maxCreditAmount, String fleetType,
                                         int min, int max) {
            this.random = random;
            this.minCreditAmount = minCreditAmount;
            this.maxCeditAmount = maxCreditAmount;
            this.fleetType = fleetType;
            this.factionId = random.nextBoolean() ? Factions.PIRATES : Factions.INDEPENDENT;
            this.minPts = min;
            this.maxPts = max;
        }

        public Object createSpecial(SectorEntityToken entity, SalvageSpecialAssigner.SpecialCreationContext context) {
            CreditStashSpecial.CreditStashSpecialData data = new CreditStashSpecial.CreditStashSpecialData();

            int creditAmount = Math.round(minCreditAmount + (random.nextFloat() * (maxCeditAmount - minCreditAmount)));
            data.creditAmt = creditAmount;
            data.prob = creditAmount / 100000f;

            data.nearbyFleetFaction = factionId;
            data.useAllFleetsInRange = false;

            if (fleetType != null) {
                int combatPoints = minPts + random.nextInt(maxPts - minPts + 1);
                combatPoints *= 5;

                data.params = new FleetParamsV3(
                        null,
                        entity.getLocationInHyperspace(),
                        factionId,
                        null,
                        fleetType,
                        combatPoints, // combatPts
                        0f, // freighterPts
                        0f, // tankerPts
                        0f, // transportPts
                        0f, // linerPts
                        0f, // utilityPts
                        0f // qualityMod
                );
            }

            return data;
        }
    }
}





