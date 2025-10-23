package indevo.utils.helper;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

public class Settings {
    public static final String GOVERNMENT_LARP_MODE = "IndEvo_GovernmentLarpMode";
    public static final String GOVERNMENT_MAX_SIZE = "IndEvo_GovernmentMaxSize";
    public static final String SHIP_PICKER_ROW_COUNT = "IndEvo_shipPickerRowCount";
    public static final String COMMERCE_BALANCE_CHANGES = "IndEvo_CommerceBalanceChanges";
    public static final String ADMANUF = "IndEvo_AdManuf";
    public static final String ADASSEM = "IndEvo_AdAssem";
    public static final String COMFORGE = "IndEvo_ComForge";
    public static final String COMARRAY = "IndEvo_ComArray";
    public static final String INTARRAY = "IndEvo_IntArray";
    public static final String ADINFRA = "IndEvo_AdInfra";
    public static final String SCRAPYARD = "IndEvo_ScrapYard";
    public static final String SUPCOM = "IndEvo_SupCom";
    public static final String EMBASSY = "IndEvo_embassy";
    public static final String SENATE = "IndEvo_senate";
    public static final String DRYDOCK = "IndEvo_dryDock";
    public static final String ACADEMY = "IndEvo_Academy";
    public static final String REQCENTER = "IndEvo_ReqCenter";
    public static final String ENGHUB = "IndEvo_EngHub";
    public static final String PRIVATEPORT = "IndEvo_PrivatePort";
    public static final String PIRATEHAVEN = "IndEvo_pirateHaven";
    public static final String SWITCHABLEREFINING = "IndEvo_SwitchableRefining";
    public static final String SWITCHABLEMINING = "IndEvo_SwitchableMining";
    public static final String DERELICT_DELIVER_TO_GATHERING = "IndEvo_derelictDeliverToGathering";
    public static final String DERINFRA_BUILD_RED = "IndEvo_DerInfraBuildRed";
    public static final String ENABLE_DERELICTS = "IndEvo_Enable_Derelicts";
    public static final String AUTOMATEDSHIPYARD_NUM = "IndEvo_AutomatedShipyardNum";
    public static final String CRUCIBLE_NUM = "IndEvo_CrucibleNum";
    public static final String SPECIAL_APPLICATION_CHANCE = "IndEvo_SpecialApplicationChance";
    public static final String ENABLE_DERELICT_STATIONS = "IndEvo_enable_derelict_stations";
    public static final String DERELICT_STATION_AMOUNT = "IndEvo_derelictStationAmount";
    public static final String PARTS_DROP_IN_CAMPAIGN = "IndEvo_PartsDropInCampaign";
    public static final String RELIC_COMPONENT_HARD_BATTLE_FP_ADVANTAGE = "IndEvo_relicComponentHardbattleFPAdvantage";
    public static final String RELIC_COMPONENT_FP_DESTROYED_FRACT = "IndEvo_relicComponentFPDestroyedFract";
    public static final String ENABLE_MINEFIELDS = "IndEvo_Enable_minefields";
    public static final String MINEFIELD_HITCHANCE_FRIGATE = "IndEvo_Minefield_HitChance_frigate";
    public static final String MINEFIELD_HITCHANCE_DESTROYER = "IndEvo_Minefield_HitChance_destroyer";
    public static final String MINEFIELD_HITCHANCE_CRUISER = "IndEvo_Minefield_HitChance_cruiser";
    public static final String MINEFIELD_HITCHANCE_CAPITAL = "IndEvo_Minefield_HitChance_capital";
    public static final String MINEFIELD_NOHITUNTILSUM = "IndEvo_Minefield_NoHitUntilSum";
    public static final String MINEFIELD_CIVILIAN_SHIP_IMPACT_MULT = "IndEvo_Minefield_CivilianShipImpactMult";
    public static final String MINEFIELD_PHASE_SHIP_IMPACT_MULT = "IndEvo_Minefield_PhaseShipImpactMult";
    public static final String ENABLE_ARTILLERY = "IndEvo_Enable_Artillery";
    public static final String ARTILLERY_SPAWN_WEIGHT = "IndEvo_Artillery_spawnWeight";
    public static final String ARTILLERY_MIN_DELAY_BETWEEN_SHOTS = "IndEvo_Artillery_minDelayBetweenShots";
    public static final String ARTILLERY_MAX_DELAY_BETWEEN_SHOTS = "IndEvo_Artillery_maxDelayBetweenShots";
    public static final String ARTILLERY_MIN_COOLDOWN = "IndEvo_Artillery_minCooldown";
    public static final String ARTILLERY_MAX_COOLDOWN = "IndEvo_Artillery_maxCooldown";
    public static final String ARTILLERY_COOLDOWN_NPC_MULT = "IndEvo_Artillery_cooldownNPCMult";
    public static final String ARTILLERY_MIN_RANGE = "IndEvo_Artillery_minRange";
    public static final String ARTILLERY_MAX_RANGE = "IndEvo_Artillery_maxRange";
    public static final String ARTILLERY_WATCHTOWER_RANGE = "IndEvo_Artillery_WatchtowerRange";
    public static final String ARTILLERY_DEFENSE_FP = "IndEvo_Artillery_defense_FP2";
    public static final String ARTILLERY_MORTAR_PROJECTILES_PER_SHOT = "IndEvo_Artillery_mortar_projectilesPerShot";
    public static final String ARTILLERY_MORTAR_PROJECTILES_IMPACT_TIME = "IndEvo_Artillery_mortar_projectilesImpactTime";
    public static final String ARTILLERY_MORTAR_EXPLOSION_SIZE = "IndEvo_Artillery_mortar_explosionSize";
    public static final String ARTILLERY_RAILGUN_PROJECTILES_PER_SHOT = "IndEvo_Artillery_railgun_projectilesPerShot";
    public static final String ARTILLERY_RAILGUN_PROJECTILES_IMPACT_TIME = "IndEvo_Artillery_railgun_projectilesImpactTime";
    public static final String ARTILLERY_RAILGUN_SHOT_FUZZ_MULT = "IndEvo_Artillery_railgun_shotFuzzMult";
    public static final String ARTILLERY_MISSILE_PROJECTILES_PER_SHOT = "IndEvo_Artillery_missile_projectilesPerShot";
    public static final String ARTILLERY_MISSILE_PROJECTILES_IMPACT_TIME = "IndEvo_Artillery_missile_projectilesImpactTime";
    public static final String ARTILLERY_MISSILE_MAX_COVERED_AREA = "IndEvo_Artillery_missile_maxCoveredArea";
    public static final String ARTILLERY_MISSILE_FUZZ = "IndEvo_Artillery_missile_fuzz";
    public static final String ARTILLERY_MISSILE_EXPLOSION_RADIUS = "IndEvo_Artillery_missile_explosionRadius";
    public static final String ARTILLERY_MISSILE_DURATION = "IndEvo_Artillery_missile_duration";
    public static final String ARTILLERY_WATCHTOWER_FACTION_RESET_TIME = "IndEvo_Watchtower_faction_reset";
    public static final String ENGHUB_IGNORE_WHITELISTS = "IndEvo_EngHubIgnoreWhitelists";
    public static final String AUTO_SHIP_BP_TO_GATHERING_POINT = "IndEvo_AutoshipBPToGatheringPoint";
    public static final String RARE_PARTS_AMOUNT_PER_FP = "IndEvo_RarePartsAmountPerFP";
    public static final String SY_BASE_DMODS = "IndEvo_SYBaseDMods";
    public static final String SY_PART_VALUE_MULT = "IndEvo_SYPartValueMult";
    public static final String SY_HULL_DELIVERY_TIME = "IndEvo_SYHullDeliveryTime";
    public static final String SY_DMOD_MOVE_BASE_COST_MULT = "IndEvo_SYDModMoveBaseCostMult";
    public static final String RAID_FOR_UNKNOWN_ONLY = "IndEvo_RaidForUnknownOnly";
    public static final String PRIVATEER_DELIVER_TO_GATHERING_POINT = "IndEvo_PrivateerDeliverToGatheringPoint";
    public static final String RG_COOLDOWN_TIME = "IndEvo_RG_cooldownTime";
    public static final String RG_RANGE = "IndEvo_RG_range";
    public static final String RESLAB_AUTO_DELIVER_TO_CLOSEST_DECON = "IndEvo_reslab_autoDeliverToClosestDecon";
    public static final String DECON_IGNORE_WHITELISTS = "IndEvo_DeconIgnoreWhitelists";
    public static final String HULLDECON_AUTO_DELIVER_TO_CLOSEST_FORGE = "IndEvo_hullDecon_autoDeliverToClosestForge";
    public static final String HULLDECON_DAYS_FRIGATE = "IndEvo_hullDecon_days_FRIGATE";
    public static final String HULLDECON_DAYS_DESTROYER = "IndEvo_hullDecon_days_DESTROYER";
    public static final String HULLDECON_DAYS_CRUISER = "IndEvo_hullDecon_days_CRUISER";
    public static final String HULLDECON_DAYS_CAPITAL_SHIP = "IndEvo_hullDecon_days_CAPITAL_SHIP";
    public static final String HULLFORGE_AUTO_QUEUE_SHIPS_UNTIL_EMPTY = "IndEvo_hullForge_autoQueueShipsUntilEmpty";
    public static final String HULLFORGE_AUTO_DELIVER_TO_CLOSEST_LAB = "IndEvo_hullForge_autoDeliverToClosestLab";
    public static final String HULLFORGE_DAYS_FRIGATE = "IndEvo_hullForge_days_FRIGATE";
    public static final String HULLFORGE_DAYS_DESTROYER = "IndEvo_hullForge_days_DESTROYER";
    public static final String HULLFORGE_DAYS_CRUISER = "IndEvo_hullForge_days_CRUISER";
    public static final String HULLFORGE_DAYS_CAPITAL_SHIP = "IndEvo_hullForge_days_CAPITAL_SHIP";
    public static final String ALPHA_CORE_RUNTIME = "IndEvo_alpha_core_runtime";
    public static final String BETA_CORE_RUNTIME = "IndEvo_beta_core_runtime";
    public static final String GAMMA_CORE_RUNTIME = "IndEvo_gamma_core_runtime";
    public static final String RESTO_FEE_FRIGATE = "IndEvo_restoFee_FRIGATE";
    public static final String RESTO_FEE_DESTROYER = "IndEvo_restoFee_DESTROYER";
    public static final String RESTO_FEE_CRUISER = "IndEvo_restoFee_CRUISER";
    public static final String RESTO_FEE_CAPITAL = "IndEvo_restoFee_CAPITAL";
    public static final String DAILY_OFFICER_SPAWN_CHANCE = "IndEvo_dailyOfficerSpawnChance";
    public static final String DAILY_ADMIN_SPAWN_CHANCE = "IndEvo_dailyAdminSpawnChance";
    public static final String PERSONALITY_TRAINING_DAY_COUNT = "IndEvo_personalityTrainingDayCount";
    public static final String PERSONALITY_TRAINING_COST = "IndEvo_personalityTrainingCost";
    public static final String ADMIN_TRAINING_DAY_COUNT = "IndEvo_adminTrainingDayCount";
    public static final String ADMIN_TRAINING_COST = "IndEvo_adminTrainingCost";
    public static final String MONTHLY_AI_STORAGE_COST = "IndEvo_monthlyAIStorageCost";
    public static final String VARIND_DELIVER_TO_PRODUCTION_POINT = "IndEvo_VarInd_deliverToProductionPoint";
    public static final String PETS = "IndEvo_pets";
    public static final String CLOUD_PAINTER_SHIELD_OVERRIDE = "IndEvo_CPShieldOverride";
    public static final String CLOUD_PAINTER_SHIELD_REMOVE = "IndEvo_CPShieldRemove";
    public static final String CORRUPTION = "IndEvo_Corruption";
    public static final String CORRUPTION_CUTOFF = "IndEvo_CorruptionCutoff";
    public static final String DECON_ALLOW_NEX_OMEGA = "IndEvo_DeconAllowNexOmega";
    public static final String ENGHUB_STRICT_WL = "IndEvo_EngHubStrictWhitelist";
    public static final String ARTILLERY_STARS_PER_STATION = "IndEvo_Artillery_starsPerStationInConstellation";
    public static final String COURIER_COST_PER_LY_MULT = "IndEvo_courier_costPerLyMult";
    public static final String COURIER_COST_PER_CARGO_SPACE = "IndEvo_courier_costPerSpace";
    public static final String COURIER_COST_PER_SHIP_DP = "IndEvo_courier_costPerShip";
    public static final String COURIER_BASE_FEE = "IndEvo_courier_baseFee";

    //consumables
    public static final String CONSUMABLE_ADD_TO_MARKETS = "IndEvo_consumables_addToMarkets";
    public static final String CONSUMABLE_DROP_FROM_FLEETS = "IndEvo_consumables_addToFleets";
    public static final String CONSUMABLE_RESEARCH = "IndEvo_consumables_researchProject";

    //meteor
    public static final String METEOR_ENABLED = "IndEvo_meteor_enabled";
    public static final String METEOR_BASE_CHANCE_PER_ECONOMY_TICK = "IndEvo_meteor_baseChancePerEconomyTick";
    public static final String METEOR_MIN_INTENSITY = "IndEvo_meteor_minIntensity";
    public static final String METEOR_MAX_INTENSITY = "IndEvo_meteor_maxIntensity";
    public static final String METEOR_BASE_SHOWER_WIDTH = "IndEvo_meteor_baseShowerWidth";
    public static final String METEOR_INTENSITY_WIDTH_MODIFIER = "IndEvo_meteor_intensityWidthModifier";
    public static final String METEOR_MAX_DENSITY = "IndEvo_meteor_maxDensity";
    public static final String METEOR_MAX_RUNTIME_MULT = "IndEvo_meteor_maxRuntimeMult";
    public static final String METEOR_BASE_RUNTIME = "IndEvo_meteor_baseRuntime";
    public static final String METEOR_LOCATION_TIMEOUT_AFTER_SPAWN_DAYS = "IndEvo_meteor_locationTimeoutAfterSpawnDays";
    public static final String METEOR_GENERAL_TIMEOUT_AFTER_SPAWN_DAYS = "IndEvo_meteor_generalTimeoutAfterSpawnDays";
    public static final String METEOR_GRID_LIMIT = "IndEvo_meteor_gridLimit";
    public static final String METEOR_MAX_DISTANCE_FROM_SUN = "IndEvo_meteor_maxDistanceFromSun";
    public static final String METEOR_MIN_DISTANCE_FROM_SUN = "IndEvo_meteor_minDistanceFromSun";
    public static final String METEOR_MAX_ANGLE = "IndEvo_meteor_maxAngle";
    public static final String METEOR_MIN_ANGLE = "IndEvo_meteor_minAngle";

    //museum
    public static final String MUSEUM_ENABLED = "IndEvo_museum_enabled";
    public static final String MUSEUM_MAX_ADDITIONAL_CREDITS = "IndEvo_museum_maxAdditionalCredits";
    public static final String MUSEUM_MIN_ADDITIONAL_CREDITS = "IndEvo_museum_minAdditionalCredits";
    public static final String MUSEUM_DEFAULT_MAX_PARADES = "IndEvo_museum_defaultMaxParades";
    public static final String MUSEUM_DEFAULT_PARADE_MEMBERS_MAX = "IndEvo_museum_defaultParadeMembersMax";
    public static final String MUSEUM_MIN_SHIPS_FOR_PARADE = "IndEvo_museum_minShipsForParade";
    public static final String MUSEUM_TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION = "IndEvo_museum_topShipPoolAmtForParadeSelection";
    public static final String MUSEUM_PARADE_FLEET_IMMIGRATION_BONUS = "IndEvo_museum_paradeFleetImmigrationBonus";
    public static final String MUSEUM_PARADE_FLEET_STABILITY_BONUS = "IndEvo_museum_paradeFleetStabilityBonus";
    public static final String MUSEUM_IMPROVE_EXTRA_PARADES = "IndEvo_museum_improveExtraParades";
    public static final String MUSEUM_ALPHA_CORE_EXTRA_PARADES = "IndEvo_museum_alphaCoreExtraParades";
    public static final String MUSEUM_BETA_CORE_INCOME_PER_STABILITY = "IndEvo_museum_betaCoreIncomePerStability";
    public static final String MUSEUM_BETA_CORE_INCOME_PER_POINT_IMMIGRATION = "IndEvo_museum_betaCoreIncomePerPointImmigration";
    public static final String MUSEUM_GAMMA_CORE_INCOME_MULT = "IndEvo_museum_gammaCoreIncomeMult";

    public static boolean getBoolean(String s){
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            return LunaSettings.getBoolean("IndEvo", s);
        } else return Global.getSettings().getBoolean(s);
    }

    public static float getFloat(String s){
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            return LunaSettings.getFloat("IndEvo", s);
        } else return Global.getSettings().getFloat(s);
    }

    public static int getInt(String s){
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            return LunaSettings.getInt("IndEvo", s);
        } else return Global.getSettings().getInt(s);
    }
}
