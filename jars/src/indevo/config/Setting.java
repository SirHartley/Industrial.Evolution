package indevo.config;

import java.util.HashMap;
import java.util.Map;

public enum Setting {
    COMMERCE_BALANCE_CHANGES("IndEvo_CommerceBalanceChanges", true),
    SHIP_PICKER_ROW_COUNT("IndEvo_shipPickerRowCount", 8, 0, 100),
    BLOCK_INDUSTRIES("IndEvo_block_industries", true),
    BLOCK_CONSUMABLES("IndEvo_block_consumables", true),
    PETS("IndEvo_pets", true),
    ENABLE_DERELICTS("IndEvo_Enable_Derelicts", true),
    AUTOMATEDSHIPYARD_NUM("IndEvo_AutomatedShipyardNum", 1.5, 0, 10),
    CRUCIBLE_NUM("IndEvo_CrucibleNum", 1, 0, 10),
    SPECIAL_APPLICATION_CHANCE("IndEvo_SpecialApplicationChance", 0.9, 0, 1),
    ENABLE_DERELICT_STATIONS("IndEvo_enable_derelict_stations", true, ENABLE_DERELICTS),
    DERELICT_STATION_AMOUNT("IndEvo_derelictStationAmount", 0.2, 0, 2),
    PARTS_DROP_IN_CAMPAIGN("IndEvo_PartsDropInCampaign", true),
    RELIC_COMPONENT_HARD_BATTLE_FP_ADVANTAGE("IndEvo_relicComponentHardbattleFPAdvantage", 1.5, 1, 5),
    RELIC_COMPONENT_FP_DESTROYED_FRACT("IndEvo_relicComponentFPDestroyedFract", 0.5, 0, 1),
    METEOR_ENABLED("IndEvo_meteor_enabled", true),
    METEOR_BASE_CHANCE_PER_ECONOMY_TICK("IndEvo_meteor_baseChancePerEconomyTick", 0.02, 0.001, 0.1),
    METEOR_IMMEDIATE_MODE("IndEvo_meteor_immediateMode", false),
    METEOR_MIN_INTENSITY("IndEvo_meteor_minIntensity", 1.0, 1, 5),
    METEOR_MAX_INTENSITY("IndEvo_meteor_maxIntensity", 3.0, 1, 10),
    METEOR_BASE_SHOWER_WIDTH("IndEvo_meteor_baseShowerWidth", 5000.0, 2500, 10000),
    METEOR_INTENSITY_WIDTH_MODIFIER("IndEvo_meteor_intensityWidthModifier", 1000.0, 100, 5000),
    METEOR_MAX_DENSITY("IndEvo_meteor_maxDensity", 2.5, 1, 5),
    METEOR_MAX_RUNTIME_MULT("IndEvo_meteor_maxRuntimeMult", 2.5, 1, 5),
    METEOR_BASE_RUNTIME("IndEvo_meteor_baseRuntime", 50.0, 10, 150),
    METEOR_LOCATION_TIMEOUT_AFTER_SPAWN_DAYS("IndEvo_meteor_locationTimeoutAfterSpawnDays", 186.0, 1, 500),
    METEOR_GENERAL_TIMEOUT_AFTER_SPAWN_DAYS("IndEvo_meteor_generalTimeoutAfterSpawnDays", 31.0, 1, 100),
    METEOR_GRID_LIMIT("IndEvo_meteor_gridLimit", 26000.0, 1, 100000),
    METEOR_MAX_DISTANCE_FROM_SUN("IndEvo_meteor_maxDistanceFromSun", 10000.0, 100, 26000),
    METEOR_MIN_DISTANCE_FROM_SUN("IndEvo_meteor_minDistanceFromSun", 3000.0, 100, 26000),
    METEOR_MAX_ANGLE("IndEvo_meteor_maxAngle", 80.0, 1, 360),
    METEOR_MIN_ANGLE("IndEvo_meteor_minAngle", 50.0, 1, 360),
    ENABLE_MINEFIELDS("IndEvo_Enable_minefields", true),
    MINEFIELD_DISABLE_DURATION("IndEvo_Minefield_disable_duration", 5.0, 1, 30),
    MINEFIELD_HITCHANCE_FRIGATE("IndEvo_Minefield_HitChance_frigate", 0.02, 0, 1),
    MINEFIELD_HITCHANCE_DESTROYER("IndEvo_Minefield_HitChance_destroyer", 0.04, 0, 1),
    MINEFIELD_HITCHANCE_CRUISER("IndEvo_Minefield_HitChance_cruiser", 0.06, 0, 1),
    MINEFIELD_HITCHANCE_CAPITAL("IndEvo_Minefield_HitChance_capital", 0.12, 0, 1),
    MINEFIELD_NOHITUNTILSUM("IndEvo_Minefield_NoHitUntilSum", 0.5, 0, 5),
    MINEFIELD_CIVILIAN_SHIP_IMPACT_MULT("IndEvo_Minefield_CivilianShipImpactMult", 0.5, 0, 2),
    MINEFIELD_PHASE_SHIP_IMPACT_MULT("IndEvo_Minefield_PhaseShipImpactMult", 0.3, 0, 2),
    ENABLE_ARTILLERY("IndEvo_Enable_Artillery", true),
    ARTILLERY_SPAWN_WEIGHT("IndEvo_Artillery_spawnWeight", 1.0, 0, 100),
    ARTILLERY_STARS_PER_STATION("IndEvo_Artillery_starsPerStationInConstellation", 1.0, 0, 100),
    ARTILLERY_WATCHTOWER_FACTION_RESET_TIME("IndEvo_Watchtower_faction_reset", 31, 1, 365),
    ARTILLERY_MIN_DELAY_BETWEEN_SHOTS("IndEvo_Artillery_minDelayBetweenShots", 2.0, 0, 100),
    ARTILLERY_MAX_DELAY_BETWEEN_SHOTS("IndEvo_Artillery_maxDelayBetweenShots", 3.0, 1, 100),
    ARTILLERY_MIN_COOLDOWN("IndEvo_Artillery_minCooldown", 15.0, 0, 100),
    ARTILLERY_MAX_COOLDOWN("IndEvo_Artillery_maxCooldown", 17.0, 1, 100),
    ARTILLERY_COOLDOWN_NPC_MULT("IndEvo_Artillery_cooldownNPCMult", 2.0, 1, 100),
    ARTILLERY_MIN_RANGE("IndEvo_Artillery_minRange", 500.0, 1, 1000),
    ARTILLERY_MAX_RANGE("IndEvo_Artillery_maxRange", 12000.0, 2, 100000),
    ARTILLERY_WATCHTOWER_RANGE("IndEvo_Artillery_WatchtowerRange", 4000.0, 1, 10000),
    ARTILLERY_DEFENSE_FP("IndEvo_Artillery_defense_FP2", 70.0, 1, 1000),
    ARTILLERY_MORTAR_PROJECTILES_PER_SHOT("IndEvo_Artillery_mortar_projectilesPerShot", 8, 1, 30),
    ARTILLERY_MORTAR_PROJECTILES_IMPACT_TIME("IndEvo_Artillery_mortar_projectilesImpactTime", 5.0, 1, 60),
    ARTILLERY_MORTAR_EXPLOSION_SIZE("IndEvo_Artillery_mortar_explosionSize", 300.0, 1, 1000),
    ARTILLERY_RAILGUN_PROJECTILES_PER_SHOT("IndEvo_Artillery_railgun_projectilesPerShot", 3, 1, 30),
    ARTILLERY_RAILGUN_PROJECTILES_IMPACT_TIME("IndEvo_Artillery_railgun_projectilesImpactTime", 1.5, 0.5, 60),
    ARTILLERY_RAILGUN_SHOT_FUZZ_MULT("IndEvo_Artillery_railgun_shotFuzzMult", 0.4, 0, 2),
    ARTILLERY_MISSILE_PROJECTILES_PER_SHOT("IndEvo_Artillery_missile_projectilesPerShot", 4, 1, 30),
    ARTILLERY_MISSILE_PROJECTILES_IMPACT_TIME("IndEvo_Artillery_missile_projectilesImpactTime", 8.0, 1, 60),
    ARTILLERY_MISSILE_MAX_COVERED_AREA("IndEvo_Artillery_missile_maxCoveredArea", 800.0, 1, 10000),
    ARTILLERY_MISSILE_FUZZ("IndEvo_Artillery_missile_fuzz", 1.0, 1, 10),
    ARTILLERY_MISSILE_EXPLOSION_RADIUS("IndEvo_Artillery_missile_explosionRadius", 200.0, 1, 1000),
    ARTILLERY_MISSILE_DURATION("IndEvo_Artillery_missile_duration", 10.0, 1, 60),
    CONSUMABLE_ADD_TO_MARKETS("IndEvo_consumables_addToMarkets", true, BLOCK_CONSUMABLES),
    CONSUMABLE_DROP_FROM_FLEETS("IndEvo_consumables_addToFleets", true, BLOCK_CONSUMABLES),
    CONSUMABLE_RESEARCH("IndEvo_consumables_researchProject", true, BLOCK_CONSUMABLES),
    ADMANUF("IndEvo_AdManuf", true, BLOCK_INDUSTRIES),
    ADASSEM("IndEvo_AdAssem", true, BLOCK_INDUSTRIES),
    COMFORGE("IndEvo_ComForge", true, BLOCK_INDUSTRIES),
    COMARRAY("IndEvo_ComArray", true, BLOCK_INDUSTRIES),
    INTARRAY("IndEvo_IntArray", true, BLOCK_INDUSTRIES),
    ADINFRA("IndEvo_AdInfra", true, BLOCK_INDUSTRIES),
    SCRAPYARD("IndEvo_ScrapYard", true, BLOCK_INDUSTRIES),
    SUPCOM("IndEvo_SupCom", true, BLOCK_INDUSTRIES),
    EMBASSY("IndEvo_embassy", true, BLOCK_INDUSTRIES),
    SENATE("IndEvo_senate", true, BLOCK_INDUSTRIES),
    DRYDOCK("IndEvo_dryDock", true, BLOCK_INDUSTRIES),
    ACADEMY("IndEvo_Academy", true, BLOCK_INDUSTRIES),
    REQCENTER("IndEvo_ReqCenter", true, BLOCK_INDUSTRIES),
    ENGHUB("IndEvo_EngHub", true, BLOCK_INDUSTRIES),
    PRIVATEPORT("IndEvo_PrivatePort", true, BLOCK_INDUSTRIES),
    PIRATEHAVEN("IndEvo_pirateHaven", true, BLOCK_INDUSTRIES),
    SWITCHABLEREFINING("IndEvo_SwitchableRefining", true, BLOCK_INDUSTRIES),
    SWITCHABLEMINING("IndEvo_SwitchableMining", true, BLOCK_INDUSTRIES),
    MUSEUM_ENABLED("IndEvo_museum_enabled", true, BLOCK_INDUSTRIES),
    GOVERNMENT_LARP_MODE("IndEvo_GovernmentLarpMode", false),
    GOVERNMENT_MAX_SIZE("IndEvo_GovernmentMaxSize", 3, 1, 10),
    ENGHUB_STRICT_WL("IndEvo_EngHubStrictWhitelist", false),
    ENGHUB_IGNORE_WHITELISTS("IndEvo_EngHubIgnoreWhitelists", false),
    AUTO_SHIP_BP_TO_GATHERING_POINT("IndEvo_AutoshipBPToGatheringPoint", false),
    RARE_PARTS_AMOUNT_PER_FP("IndEvo_RarePartsAmountPerFP", 2, 1, 10),
    SY_BASE_DMODS("IndEvo_SYBaseDMods", 5, 1, 10),
    SY_PART_VALUE_MULT("IndEvo_SYPartValueMult", 1.2, 0.1, 5),
    SY_HULL_DELIVERY_TIME("IndEvo_SYHullDeliveryTime", 60, 1, 365),
    SY_DMOD_MOVE_BASE_COST_MULT("IndEvo_SYDModMoveBaseCostMult", 0.8, 0.1, 5),
    COURIER_COST_PER_CARGO_SPACE("IndEvo_courier_costPerSpace", 5, 1, 20),
    COURIER_COST_PER_SHIP_DP("IndEvo_courier_costPerShip", 170, 10, 500),
    COURIER_BASE_FEE("IndEvo_courier_baseFee", 1000, 0, 10000),
    COURIER_COST_PER_LY_MULT("IndEvo_courier_costPerLyMult", 1.0, 0.1, 2),
    RAID_FOR_UNKNOWN_ONLY("IndEvo_RaidForUnknownOnly", false),
    PRIVATEER_DELIVER_TO_GATHERING_POINT("IndEvo_PrivateerDeliverToGatheringPoint", false),
    DERINFRA_BUILD_RED("IndEvo_DerInfraBuildRed", 0.5, 0, 1),
    DERELICT_DELIVER_TO_GATHERING("IndEvo_derelictDeliverToGathering", false),
    RG_COOLDOWN_TIME("IndEvo_RG_cooldownTime", 90, 1, 365),
    RG_RANGE("IndEvo_RG_range", 9, 1, 100),
    RESLAB_AUTO_DELIVER_TO_CLOSEST_DECON("IndEvo_reslab_autoDeliverToClosestDecon", false),
    DECON_IGNORE_WHITELISTS("IndEvo_DeconIgnoreWhitelists", true),
    DECON_ALLOW_NEX_OMEGA("IndEvo_DeconAllowNexOmega", false),
    HULLDECON_AUTO_DELIVER_TO_CLOSEST_FORGE("IndEvo_hullDecon_autoDeliverToClosestForge", false),
    HULLDECON_DAYS_FRIGATE("IndEvo_hullDecon_days_FRIGATE", 10, 1, 100),
    HULLDECON_DAYS_DESTROYER("IndEvo_hullDecon_days_DESTROYER", 20, 1, 100),
    HULLDECON_DAYS_CRUISER("IndEvo_hullDecon_days_CRUISER", 30, 1, 100),
    HULLDECON_DAYS_CAPITAL_SHIP("IndEvo_hullDecon_days_CAPITAL_SHIP", 40, 1, 100),
    HULLFORGE_AUTO_QUEUE_SHIPS_UNTIL_EMPTY("IndEvo_hullForge_autoQueueShipsUntilEmpty", true),
    HULLFORGE_AUTO_DELIVER_TO_CLOSEST_LAB("IndEvo_hullForge_autoDeliverToClosestLab", false),
    HULLFORGE_DAYS_FRIGATE("IndEvo_hullForge_days_FRIGATE", 5, 1, 100),
    HULLFORGE_DAYS_DESTROYER("IndEvo_hullForge_days_DESTROYER", 10, 1, 100),
    HULLFORGE_DAYS_CRUISER("IndEvo_hullForge_days_CRUISER", 15, 1, 100),
    HULLFORGE_DAYS_CAPITAL_SHIP("IndEvo_hullForge_days_CAPITAL_SHIP", 20, 1, 100),
    ALPHA_CORE_RUNTIME("IndEvo_alpha_core_runtime", 558, 31, 3100),
    BETA_CORE_RUNTIME("IndEvo_beta_core_runtime", 279, 31, 3100),
    GAMMA_CORE_RUNTIME("IndEvo_gamma_core_runtime", 93, 31, 3100),
    RESTO_FEE_FRIGATE("IndEvo_restoFee_FRIGATE", 1000.0, 1, 100000),
    RESTO_FEE_DESTROYER("IndEvo_restoFee_DESTROYER", 3000.0, 1, 100000),
    RESTO_FEE_CRUISER("IndEvo_restoFee_CRUISER", 7000.0, 1, 100000),
    RESTO_FEE_CAPITAL("IndEvo_restoFee_CAPITAL", 10000.0, 1, 100000),
    DAILY_OFFICER_SPAWN_CHANCE("IndEvo_dailyOfficerSpawnChance", 0.15, 0, 1),
    DAILY_ADMIN_SPAWN_CHANCE("IndEvo_dailyAdminSpawnChance", 0.1, 0, 1),
    PERSONALITY_TRAINING_DAY_COUNT("IndEvo_personalityTrainingDayCount", 93, 1, 365),
    PERSONALITY_TRAINING_COST("IndEvo_personalityTrainingCost", 30000, 1, 100000),
    ADMIN_TRAINING_DAY_COUNT("IndEvo_adminTrainingDayCount", 124, 1, 365),
    ADMIN_TRAINING_COST("IndEvo_adminTrainingCost", 20000, 1, 100000),
    MONTHLY_AI_STORAGE_COST("IndEvo_monthlyAIStorageCost", 2000, 1, 10000),
    VARIND_DELIVER_TO_PRODUCTION_POINT("IndEvo_VarInd_deliverToProductionPoint", false),
    CLOUD_PAINTER_SHIELD_REMOVE("IndEvo_CPShieldRemove", false),
    CLOUD_PAINTER_SHIELD_OVERRIDE("IndEvo_CPShieldOverride", false),
    MUSEUM_MAX_ADDITIONAL_CREDITS("IndEvo_museum_maxAdditionalCredits", 1950.0, 1, 10000),
    MUSEUM_MIN_ADDITIONAL_CREDITS("IndEvo_museum_minAdditionalCredits", 50.0, 1, 10000),
    MUSEUM_DEFAULT_MAX_PARADES("IndEvo_museum_defaultMaxParades", 1, 1, 10),
    MUSEUM_DEFAULT_PARADE_MEMBERS_MAX("IndEvo_museum_defaultParadeMembersMax", 15, 1, 50),
    MUSEUM_MIN_SHIPS_FOR_PARADE("IndEvo_museum_minShipsForParade", 3, 1, 50),
    MUSEUM_TOP_SHIP_POOL_AMT_FOR_PARADE_SELECTION("IndEvo_museum_topShipPoolAmtForParadeSelection", 20, 1, 50),
    MUSEUM_PARADE_FLEET_IMMIGRATION_BONUS("IndEvo_museum_paradeFleetImmigrationBonus", 5, 1, 30),
    MUSEUM_PARADE_FLEET_STABILITY_BONUS("IndEvo_museum_paradeFleetStabilityBonus", 1, 1, 10),
    MUSEUM_IMPROVE_EXTRA_PARADES("IndEvo_museum_improveExtraParades", 1, 1, 10),
    MUSEUM_ALPHA_CORE_EXTRA_PARADES("IndEvo_museum_alphaCoreExtraParades", 1, 1, 10),
    MUSEUM_BETA_CORE_INCOME_PER_STABILITY("IndEvo_museum_betaCoreIncomePerStability", 20000, 1, 100000),
    MUSEUM_BETA_CORE_INCOME_PER_POINT_IMMIGRATION("IndEvo_museum_betaCoreIncomePerPointImmigration", 3000, 1, 100000),
    MUSEUM_GAMMA_CORE_INCOME_MULT("IndEvo_museum_gammaCoreIncomeMult", 1.3, 0.1, 3),
    MUSEUM_UNIQUE_SHIP_VALUE_MULT("IndEvo_museum_uniqueShipValueMult", 2.0, 1, 5);

    public enum Type {BOOLEAN, INT, DOUBLE}

    private static final Map<String, Setting> BY_ID = new HashMap<>();

    static {
        for (Setting setting : values()) BY_ID.put(setting.id, setting);
    }

    public final String id;
    public final Type type;
    public final Object fallback;
    public final double min;
    public final double max;
    public final Setting block;

    Setting(String id, boolean fallback) {
        this(id, Type.BOOLEAN, fallback, 0, 1, null);
    }

    Setting(String id, boolean fallback, Setting block) {
        this(id, Type.BOOLEAN, fallback, 0, 1, block);
    }

    Setting(String id, int fallback, double min, double max) {
        this(id, Type.INT, fallback, min, max, null);
    }

    Setting(String id, double fallback, double min, double max) {
        this(id, Type.DOUBLE, fallback, min, max, null);
    }

    Setting(String id, Type type, Object fallback, double min, double max, Setting block) {
        this.id = id;
        this.type = type;
        this.fallback = fallback;
        this.min = min;
        this.max = max;
        this.block = block;
    }

    public static Setting byId(String id) {
        Setting setting = BY_ID.get(id);
        if (setting == null) throw new IllegalArgumentException("Unknown IndEvo setting " + id);
        return setting;
    }

    public Object sanitize(Object value) {
        if (type == Type.BOOLEAN) return value instanceof Boolean ? value : fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) return fallback;
        double clamped = Math.max(min, Math.min(max, number.doubleValue()));
        if (type == Type.INT) return (int) Math.round(clamped);
        return clamped;
    }

    public static boolean same(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
        return a != null && a.equals(b);
    }

    public boolean enabled() {
        if (block != null && !block.enabled()) return false;
        return (Boolean) PlayerSettings.get(this);
    }

    public float value() {
        return ((Number) PlayerSettings.get(this)).floatValue();
    }

    public int intValue() {
        return ((Number) PlayerSettings.get(this)).intValue();
    }
}
