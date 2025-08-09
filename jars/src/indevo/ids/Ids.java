package indevo.ids;

import com.fs.starfarer.api.Global;

public class Ids {
    //factions
    public static final String CONVERTERS_FACTION_ID = "IndEvo_converters";
    public static final String DERELICT_FACTION_ID = "IndEvo_derelict";

    //memory Keys
    public static final String EMBASSY_LIST = Global.getSettings().getString("IndEvo_embassyList");
    public static final String REVERSE_LIST = Global.getSettings().getString("IndEvo_reverseList");
    public static final String PRINT_LIST = Global.getSettings().getString("IndEvo_printList");
    public static final String SHIPPING_LIST = Global.getSettings().getString("IndEvo_shipingList");
    public static final String ORDER_LIST = Global.getSettings().getString("IndEvo_custOrderList");
    public static final String BUREAU_LIST = Global.getSettings().getString("IndEvo_buerauList");
    public static final String RUIND_LIST = Global.getSettings().getString("IndEvo_ruindList");
    public static final String CLOUD_LIST = Global.getSettings().getString("IndEvo_cloudList");
    public static final String LAB_LIST = Global.getSettings().getString("IndEvo_labList");
    public static final String TREE_LIST = Global.getSettings().getString("IndEvo_treeList");
    public static final String RURAL_LIST = Global.getSettings().getString("IndEvo_ruralList");
    public static final String ARCOLOGIES_LIST = Global.getSettings().getString("IndEvo_arcologiesList");
    public static final String ARCOLOGIES_COND_LIST = Global.getSettings().getString("IndEvo_arcologiesCondList");

    //tags
    public static final String TAG_NEVER_REMOVE_RUINS = "IndEvo_RuinsAlways";
    public static final String TAG_ARTILLERY_STATION = "IndEvo_Artillery";
    public static final String TAG_ENTITY_HAS_ARTILLERY_STATION = "IndEvo_Entity_has_artillery";
    public static final String TAG_ARTILLERY_STATION_FLEET = "IndEvo_ArtilleryFleet";
    public static final String TAG_WATCHTOWER = "IndEvo_watchtower";
    public static final String TAG_SYSTEM_HAS_ARTILLERY = "IndEvo_SystemHasArtillery";
    public static final String MEM_SYSTEM_DISABLE_WATCHTOWERS = "$IndEvo_SystemDisableWatchtowers";
    public static final String TAG_YEETOPULT = "IndEvo_yeetopult";

    //Submarkets
    public static final String REPSTORAGE = "IndEvo_RepairStorage";
    public static final String TEMPSTORE = "IndEvo_tempStorage";
    public static final String REQMARKET = "IndEvo_ReqCenterMarket";
    public static final String DECSTORAGE = "IndEvo_DeconstStorage";
    public static final String ENGSTORAGE = "IndEvo_EngStorage";
    public static final String SHAREDSTORAGE = "IndEvo_SharedStore";
    public static final String PETMARKET = "IndEvo_PetSubMarket";
    public static final String MUSEUM_SUBMARKET = "IndEvo_MuseumStorage";

    //Industries
    public static final String ADMANUF = "IndEvo_AdManuf";
    public static final String ADASSEM = "IndEvo_AdAssem";
    public static final String COMFORGE = "IndEvo_ComForge";
    public static final String COMARRAY = "IndEvo_ComArray";
    public static final String ADINFRA = "IndEvo_AdInfra";
    public static final String SCRAPYARD = "IndEvo_ScrapYard";
    public static final String SUPCOM = "IndEvo_SupCom";
    public static final String EMBASSY = "IndEvo_embassy";
    public static final String PIRATEHAVEN = "IndEvo_pirateHaven";
    public static final String SENATE = "IndEvo_senate";
    public static final String REPAIRDOCKS = "IndEvo_dryDock";
    public static final String ACADEMY = "IndEvo_Academy";
    public static final String INTARRAY = "IndEvo_IntArray";
    public static final String REQCENTER = "IndEvo_ReqCenter";
    public static final String ENGHUB = "IndEvo_EngHub";
    public static final String HULLFORGE = "IndEvo_HullForge";
    public static final String DECONSTRUCTOR = "IndEvo_HullDecon";
    public static final String LAB = "IndEvo_ResLab";
    public static final String BEACON = "IndEvo_Lighthouse";
    public static final String RIFTGEN = "IndEvo_RiftGen";
    public static final String RUINS = "IndEvo_Ruins";
    public static final String RUINFRA = "IndEvo_RuinedInfra";
    public static final String PORT = "IndEvo_PrivatePort";
    public static final String CHAMELION = "IndEvo_ChamelionIndustry";
    public static final String CHURCH = "IndEvo_Megachurch";
    public static final String ARTILLERY_MORTAR = "IndEvo_Artillery_mortar";
    public static final String ARTILLERY_MISSILE = "IndEvo_Artillery_missile";
    public static final String ARTILLERY_RAILGUN = "IndEvo_Artillery_railgun";
    public static final String SWITCHABLE_MINING = "IndEvo_SwitchableMining";
    public static final String SWITCHABLE_REFINING = "IndEvo_SwitchableRefining";
    public static final String SWITCHABLE_POPULATION = "IndEvo_SwitchablePopulation";
    public static final String PET_STORE = "IndEvo_PetStore";
    public static final String PLANETARY_SHIELD_ALT = "IndEvo_InvisiblePlanetaryShield";
    public static final String MUSEUM = "IndEvo_Museum";

    public static final String PIRATEHAVEN_SECONDARY = "IndEvo_pirateHavenSecondary";
    public static final String COMMERCE = "commerce";

    //conditions
    public static final String COND_RUINS = "IndEvo_RuinsCondition";
    public static final String COND_PIRATES = "IndEvo_pirate_subpop";
    public static final String COND_CRYODISABLE = "IndEvo_CryoRevivalDisabler";
    public static final String COND_RESSOURCES = "IndEvo_ressCond";
    public static final String COND_INFRA = "IndEvo_RuinedInfra";
    public static final String COND_LOG_CORE = "IndEvo_LogCoreCond";
    public static final String COND_MINERING = "IndEvo_mineFieldCondition";
    public static final String COND_WORLD_WONDER = "IndEvo_WorldWonderCondition";
    public static final String COND_PLAGUE = "IndEvo_engineered_plague";
    public static final String COND_PARADE = "IndEvo_Parade";

    //Hullmods
    public static final String DEFECTS_LOW = "IndEvo_print_low";
    public static final String DEFECTS_MED = "IndEvo_print_med";
    public static final String DEFECTS_HIGH = "IndEvo_print_high";
    public static final String PRINTING_INDICATOR = "IndEvo_auto";
    public static final String DRIVE_PROTOCOLS = "IndEvo_augfield";
    public static final String MYSTERY_HULLMOD = "vayra_ghost_ship_mystery";

    public static final String MYSTERY_NOTHING_HULLMOD = "vayra_ghost_ship_mystery_nothing";

    public static final String MYSTERY_ROGUE_AI_HULLMOD = "indEvo_ghost_ship_mystery_rogue_ai";
    public static final String MYSTERY_HYPERLOST_HULLMOD = "indEvo_ghost_ship_mystery_hyperlost";
    public static final String MYSTERY_ALIEN_HULLMOD = "indEvo_ghost_ship_mystery_alien";
    public static final String MYSTERY_PLAGUE_HULLMOD = "indEvo_ghost_ship_mystery_plague";
    public static final String MYSTERY_NANITE_HULLMOD = "indEvo_ghost_ship_mystery_nanite";
    public static final String MYSTERY_CANNIBAL_HULLMOD = "indEvo_ghost_ship_mystery_cannibal";
    public static final String ROGUE_AI_HULLMOD = "indEvo_ghost_ship_rogue_ai";
    public static final String HYPERLOST_HULLMOD = "indEvo_ghost_ship_hyperlost";
    public static final String ALIEN_HULLMOD = "indEvo_ghost_ship_alien";
    public static final String PLAGUE_HULLMOD = "indEvo_ghost_ship_plague";
    public static final String NANITE_HULLMOD = "indEvo_ghost_ship_nanite";
    public static final String CANNIBAL_HULLMOD = "indEvo_ghost_ship_cannibal";
    public static final String UNIQUE_HYPERLOST_HULLMOD = "indEvo_ghost_ship_unique_hyperlost";
    public static final String ANTI_AI_HULLMOD = "indEvo_anti_robot";

    //Entities
    public static final String INTARRAY_ENTITY_TAG = "IndEvo_intArray";
    public static final String INTARRAY_ENTITY = "IndEvo_int_array";
    public static final String PRISTINE_INTARRAY_ENTITY = "IndEvo_pristine_int_array";
    public static final String ARSENAL_ENTITY = "IndEvo_arsenalStation";
    public static final String LAB_ENTITY = "IndEvo_orbitalLaboratory";
    public static final String GACHA_STATION = "IndEvo_GachaStation";
    public static final String PET_SHOP_ENTITY = "IndEvo_PetShop";
    public static final String ABANDONED_PETSHOP_ENTITY = "IndEvo_abandonedPetCenter";

    //skills
    public static final String SKILL_FLEET_LOGISTICS = "indevo_FleetLogistics";
    public static final String SKILL_INDUSTRIAL_PLANNING = "indevo_IndustrialPlanning";
    public static final String SKILL_PLANETARY_OPERATIONS = "indevo_PlanetaryOperations";
    public static final String MICROMANAGEMENT = "indevo_Micromanagement";

    //abilities
    public static final String ABILITY_DETACHMENT = "splinter_fleet";
    public static final String ABILITY_NANITES = "IndEvo_ability_nanites";
    public static final String ABILITY_SUPERCHARGER = "IndEvo_ability_supercharger";
    public static final String ABILITY_STABILIZER = "IndEvo_ability_stabilizer";
    public static final String ABILITY_LOCATOR = "IndEvo_ability_locator";
    public static final String ABILITY_SPIKE = "IndEvo_ability_spike";
    public static final String ABILITY_SCOOP = "IndEvo_ability_scoop";
    public static final String ABILITY_DRONES = "IndEvo_ability_drones";
    public static final String ABILITY_DECOY = "IndEvo_ability_decoy";
    public static final String ABILITY_SPOOFER = "IndEvo_ability_spoofer";
    public static final String ABILITY_RELAY = "IndEvo_ability_relay";
    public static final String ABILITY_MINE_INTERDICT = "IndEvo_ability_mine_interdict";
    public static final String ABILITY_MINE_DECOY = "IndEvo_ability_mine_decoy";
    public static final String ABILITY_MISSILE_EXPLOSIVE = "IndEvo_ability_missile_explosive";
    public static final String ABILITY_MISSILE_CONCUSSIVE = "IndEvo_ability_missile_concussive";
    public static final String ABILITY_MISSILE_SMOKE = "IndEvo_ability_missile_smoke";
    public static final String ABILITY_MISSILE_INTERCEPT = "IndEvo_ability_missile_intercept";
    public static final String ABILITY_MISSILE_REMOTE = "IndEvo_ability_artillery_target";
    public static final String ABILITY_BEACON = "IndEvo_ability_beacon";
    public static final String ABILITY_CATAPULT = "IndEvo_ability_catapult";

    //research Projects
    public static final String PROJ_SPYGLASS = "indevo_proj_revival";
    public static final String PROJ_SNOWBLIND = "indevo_proj_snowblind";
    public static final String PROJ_SPITFIRE = "indevo_proj_spitfire";
    public static final String PROJ_EUREKA = "indevo_proj_eureka";
    public static final String PROJ_PARALLAX = "indevo_proj_parallax";
    public static final String PROJ_KEYHOLE = "indevo_proj_keyhole";
    public static final String PROJ_TRANSISTOR = "indevo_proj_transistor";
    public static final String PROJ_NAVI = "indevo_proj_navi";
    public static final String PROJ_PROSPECTOR = "indevo_proj_prospector";
    public static final String PROJ_SONIC = "indevo_proj_sanic";
    public static final String PROJ_PULSE = "indevo_proj_pulse";
    public static final String PROJ_MORGANA = "indevo_proj_morgana";
    public static final String PROJ_MJOLNIR = "indevo_proj_mjolnir";

    //id
    //IndEvo_cryoartillery
    //IndEvo_degrader
    //IndEvo_causalitygun
    //IndEvo_pulsedcarbine
    //IndEvo_riftgun
}
