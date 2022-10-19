package com.fs.starfarer.api.plugins;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.artilleryStation.station.IndEvo_WatchtowerEyeIndicator;
import com.fs.starfarer.api.artilleryStation.trails.IndEvo_MagicCampaignTrailPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.impl.items.consumables.listeners.IndEvo_LocatorSystemRatingUpdater;
import com.fs.starfarer.api.campaign.impl.items.consumables.listeners.IndEvo_SpooferItemKeypressListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_OrbitalStation;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.listeners.ShippingManager;
import com.fs.starfarer.api.impl.campaign.econ.impl.installableItemPlugins.IndEvo_SpecialItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.researchProjects.IndEvo_DoritoGunFoundChecker;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidSource;
import com.fs.starfarer.api.impl.campaign.terrain.conditions.IndEvo_MineFieldCondition;
import com.fs.starfarer.api.impl.campaign.terrain.listeners.IndEvo_RecentJumpListener;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.plugins.ambassadorPlugins.IndEvo_ambassadorPersonManager;
import com.fs.starfarer.api.plugins.converters.IndEvo_ConverterRepResetScript;
import com.fs.starfarer.api.plugins.derelicts.IndEvo_ArtilleryStationPlacer;
import com.fs.starfarer.api.plugins.derelicts.IndEvo_DerelictStationPlacer;
import com.fs.starfarer.api.plugins.derelicts.IndEvo_RuinsManager;
import com.fs.starfarer.api.plugins.derelicts.IndEvo_SalvageSpecialAssigner;
import com.fs.starfarer.api.plugins.economy.IndEvo_ConsumableItemMarketAdder;
import com.fs.starfarer.api.plugins.economy.IndEvo_PartsManager;
import com.fs.starfarer.api.plugins.notifications.IndEvo_depositMessage;
import com.fs.starfarer.api.plugins.salvage.IndEvo_ConsumableItemDropListener;
import com.fs.starfarer.api.plugins.salvage.IndEvo_SpecialItemDropsListener;
import com.fs.starfarer.api.plugins.timers.IndEvo_TimeTracker;
import com.fs.starfarer.api.plugins.timers.IndEvo_raidTimeout;
import com.fs.starfarer.api.plugins.update.IndEvo_DetachmentAbilityAdder;
import com.fs.starfarer.api.plugins.update.IndEvo_NewGameIndustryPlacer;
import com.fs.starfarer.api.splinterFleet.plugins.SplinterFleetCampignPlugin;
import com.fs.starfarer.api.splinterFleet.plugins.dialogue.DialogueInterceptListener;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.weapons.ai.IndEvo_missileProjectileAI;
import data.scripts.weapons.ai.IndEvo_mortarProjectileAI;
import org.dark.shaders.light.LightData;
import org.dark.shaders.util.ShaderLib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.academyRules.IndEvo_AcademyVariables.ACADEMY_MARKET_ID;

public class IndEvo_modPlugin extends BaseModPlugin {
    public static void log(String Text) {
       if(Global.getSettings().isDevMode()) Global.getLogger(IndEvo_modPlugin.class).info(Text);
    }

    public static int DEALMAKER_INCOME_PERCENT_BONUS = 25;

    @Override
    public void onApplicationLoad() throws Exception {
        boolean hasLazyLib = Global.getSettings().getModManager().isModEnabled("lw_lazylib");
        if (!hasLazyLib) {
            throw new RuntimeException("Industrial Evolution requires LazyLib by LazyWizard" + "\nGet it at http://fractalsoftworks.com/forum/index.php?topic=5444");
        }
        boolean hasMagicLib = Global.getSettings().getModManager().isModEnabled("MagicLib");
        if (!hasMagicLib) {
            throw new RuntimeException("Industrial Evolution requires MagicLib!" + "\nGet it at http://fractalsoftworks.com/forum/index.php?topic=13718");
        }

        boolean hasGraphicsLib = Global.getSettings().getModManager().isModEnabled("shaderLib");
        if (hasGraphicsLib) {
            ShaderLib.init();
            LightData.readLightDataCSV("data/lights/IndEvo_lights.csv");
//            TextureData.readTextureDataCSV("data/lights/IndEvo_texture.csv");
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        //Global.getSector().getPlayerFleet().setFaction("hegemony");

        IndEvo_ArtilleryStationPlacer.placeCoreWorldArtilleries(); // TODO: 02/09/2022 this is just for this update, remove on the next save breaking one
        IndEvo_ArtilleryStationPlacer.placeDerelictArtilleries(); //same here

        ModManagerAPI mm = Global.getSettings().getModManager();
        boolean yunruindustries = mm.isModEnabled("yunruindustries");
        boolean yunruTechmining = mm.isModEnabled("yunrutechmining");

        if (Global.getSettings().getBoolean("IndEvo_CommerceBalanceChanges")) overrideVanillaCommerce();
        if (Global.getSettings().getBoolean("IndEvo_TechMiningBalanceChanges") && !yunruindustries && !yunruTechmining) overrideVanillaTechMining();

        overrideVanillaOrbitalStations();

        createAcademyMarket();

        //updateVersionIfNeeded(newGame);
        setListenersIfNeeded();
        setScriptsIfNeeded();

        IndEvo_PartsManager.applyRessourceCondToAllMarkets();
        IndEvo_SpecialItemEffectsRepo.addItemEffectsToVanillaRepo();

        loadTransientMemory();

        IndEvo_LocatorSystemRatingUpdater.updateAllSystems();
        resetDerelictRep();
    }

    public void overrideVanillaOrbitalStations(){
        for (IndustrySpecAPI spec : Global.getSettings().getAllIndustrySpecs()){
            if (spec.hasTag(Tags.STATION) && spec.getPluginClass().equals("com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation")) {

                log("replacing industry spec " + spec.getId());
                spec.setPluginClass("com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_OrbitalStation");

                String id = spec.getId();

                for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                    if (m.hasIndustry(id) && !(m.getIndustry(id) instanceof IndEvo_OrbitalStation)) {
                        log("replacing orbital station at " + m.getName());

                        Industry ind = m.getIndustry(id);
                        replaceIndustry(ind);
                    }
                }
            }
        }
    }

    //runcode com.fs.starfarer.api.plugins.IndEvo_modPlugin.report()

    public static void report(){
        for (IndustrySpecAPI spec : Global.getSettings().getAllIndustrySpecs()) {
            if (spec.hasTag(Tags.STATION)) {
                String id = spec.getId();

                for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                    if (m.hasIndustry(id)) {
                        log("station is new spec " + (m.getIndustry(id) instanceof IndEvo_OrbitalStation));
                    }
                }
            }
        }
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        super.onNewGameAfterEconomyLoad();

        IndEvo_RuinsManager.forceCleanCoreRuins();
        IndEvo_NewGameIndustryPlacer.run();
        IndEvo_ArtilleryStationPlacer.placeCoreWorldArtilleries();
        IndEvo_ArtilleryStationPlacer.placeDerelictArtilleries();
        createAcademyMarket();

        if (Global.getSettings().getBoolean("Enable_Indevo_Derelicts")) {
            new IndEvo_DerelictStationPlacer().init();
            new IndEvo_SalvageSpecialAssigner().init();
        }

        spawnMineFields();
        IndEvo_RuinsManager.placeRuinedInfrastructure();
        IndEvo_RuinsManager.placeIndustrialRuins();

        IndEvo_ConverterRepResetScript.resetConverterRep();
    }

    protected Map<SectorEntityToken, LocationAPI> mines = new HashMap<SectorEntityToken, LocationAPI>();

    @Override
    public void beforeGameSave() {
        super.beforeGameSave();

        mines.clear();

        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (SectorEntityToken mine : new ArrayList<>(loc.getEntitiesWithTag("IndEvo_Mine"))) {
                //count++;
                AsteroidSource source = Misc.getAsteroidSource(mine);
                if (source == null || !mine.getMemoryWithoutUpdate().isEmpty()) {
                    if (source != null) {
                        source.reportAsteroidPersisted(mine);
                        Misc.clearAsteroidSource(mine);
                    }

                } else {
                    this.mines.put(mine, loc);
                    loc.removeEntity(mine);
                }
            }
        }
    }

    @Override
    public void afterGameSave() {
        restoreRemovedEntities();
    }

    @Override
    public void onGameSaveFailed() {
        restoreRemovedEntities();
    }

    protected void restoreRemovedEntities() {
        if(!Global.getSettings().getBoolean("Enable_IndEvo_minefields")) return;

        for (SectorEntityToken mine : mines.keySet()) {
            ((LocationAPI) mines.get(mine)).addEntity(mine);
        }
        mines.clear();
    }

    public void spawnMineFields() {
        if (Global.getSector().getEconomy().getMarket("culann") == null || !Global.getSettings().getBoolean("Enable_IndEvo_minefields")) return;

        MarketAPI m = Global.getSector().getEconomy().getMarket("culann");
        m.addCondition("IndEvo_mineFieldCondition");

        m = Global.getSector().getEconomy().getMarket("chicomoztoc");
        m.getMemoryWithoutUpdate().set(IndEvo_MineFieldCondition.NO_ADD_BELT_VISUAL, true);
        m.addCondition("IndEvo_mineFieldCondition");

        m = Global.getSector().getEconomy().getMarket("kantas_den");
        m.getMemoryWithoutUpdate().set(IndEvo_MineFieldCondition.NO_ADD_BELT_VISUAL, true);
        m.addCondition("IndEvo_mineFieldCondition");
    }

    @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        if (missile.getProjectileSpecId().equals("IndEvo_mortar_projectile")) return new PluginPick<MissileAIPlugin>(new IndEvo_mortarProjectileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        if (missile.getProjectileSpecId().equals("IndEvo_missile_projectile")) return new PluginPick<MissileAIPlugin>(new IndEvo_missileProjectileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);

        return super.pickMissileAI(missile, launchingShip);
    }

    private void createAcademyMarket() {
        if (Global.getSector().getEconomy().getMarket(ACADEMY_MARKET_ID) == null
                && Global.getSector().getEntityById("station_galatia_academy") != null) {

            log("Creating academy market");

            MarketAPI market = Global.getFactory().createMarket(ACADEMY_MARKET_ID, "Galatia Academy", 3);
            market.setHidden(true);
            market.getMemoryWithoutUpdate().set(ContactIntel.NO_CONTACTS_ON_MARKET, true);

            market.setFactionId(Factions.INDEPENDENT);
            market.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
            market.addCondition(Conditions.POPULATION_3);

            market.addIndustry(Industries.POPULATION);
            market.addIndustry(IndEvo_ids.ACADEMY);

            market.setPrimaryEntity(Global.getSector().getEntityById("station_galatia_academy"));
            Global.getSector().getEconomy().addMarket(market, false);
        }
    }

    private void loadTransientMemory() {
        IndEvo_IndustryHelper.getVPCItemSet();
        IndEvo_IndustryHelper.getVayraBossShips();
        IndEvo_IndustryHelper.getPrismBossShips();

        IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.EMBASSY_LIST);
        IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.ORDER_LIST);
        IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.PRINT_LIST);
        IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.REVERSE_LIST);
        IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.SHIPPING_LIST);
        IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.BUREAU_LIST);
        IndEvo_IndustryHelper.getCSVSetFromMemory(IndEvo_ids.RUIND_LIST);
    }

    private void setListenersIfNeeded() {
        IndEvo_SpecialItemEffectsRepo.initEffectListeners();

        ListenerManagerAPI l = Global.getSector().getListenerManager();

        if (!l.hasListenerOfClass(IndEvo_PartsManager.RessCondApplicator.class))
            l.addListener(new IndEvo_PartsManager.RessCondApplicator(), true);
        if (!l.hasListenerOfClass(IndEvo_RuinsManager.ResolveRuinsToUpgradeListener.class))
            l.addListener(new IndEvo_RuinsManager.ResolveRuinsToUpgradeListener(), true);
        if (!l.hasListenerOfClass(IndEvo_depositMessage.class)) l.addListener(new IndEvo_depositMessage(), true);
        if (!l.hasListenerOfClass(IndEvo_ambassadorPersonManager.class))
            l.addListener(new IndEvo_ambassadorPersonManager(), true);
        if (!l.hasListenerOfClass(IndEvo_raidTimeout.class)) l.addListener(new IndEvo_raidTimeout(), true);
        if (!l.hasListenerOfClass(IndEvo_PartsManager.PartsCargoInterceptor.class))
            l.addListener(new IndEvo_PartsManager.PartsCargoInterceptor(), true);
        if (!l.hasListenerOfClass(IndEvo_DoritoGunFoundChecker.class))
            l.addListener(new IndEvo_DoritoGunFoundChecker(), true);
        if (!l.hasListenerOfClass(IndEvo_ConverterRepResetScript.class))
            l.addListener(new IndEvo_ConverterRepResetScript(), true);

        Global.getSector().addTransientListener(new IndEvo_PartsManager.PartsLootAdder(false));
        Global.getSector().addTransientListener(new IndEvo_ambassadorPersonManager.checkAmbassadorPresence());
        Global.getSector().addTransientListener(new DialogueInterceptListener(false));

        IndEvo_RuinsManager.DerelictRuinsPlacer.register();
        IndEvo_RuinsManager.ResolveRuinsToUpgradeListener.register();
        //ColonyFleetDialogueInterceptListener.register();
        ShippingManager.getInstanceOrRegister();
        IndEvo_DetachmentAbilityAdder.register();
        IndEvo_LocatorSystemRatingUpdater.register();
        IndEvo_ConsumableItemDropListener.register();
        IndEvo_ConsumableItemMarketAdder.register();
        IndEvo_SpecialItemDropsListener.register();
        IndEvo_RecentJumpListener.register();
        IndEvo_SpooferItemKeypressListener.register();
    }

    private void setScriptsIfNeeded() {
        //Scripts:
        Global.getSector().registerPlugin(new SplinterFleetCampignPlugin());

        if (!Global.getSector().hasScript(IndEvo_TimeTracker.class)) {
            Global.getSector().addScript(new IndEvo_TimeTracker());
        }

        IndEvo_MagicCampaignTrailPlugin.register();
        IndEvo_WatchtowerEyeIndicator.register();
        //PlayerFleetFollower.register();
    }

    public void overrideVanillaTechMining() {
        replaceIndustries(Industries.TECHMINING);
    }

    public void overrideVanillaCommerce() {
        ItemEffectsRepo.ITEM_EFFECTS.put(Items.DEALMAKER_HOLOSUITE, new BaseInstallableItemEffect(Items.DEALMAKER_HOLOSUITE) {
            public void apply(Industry industry) {
                industry.getMarket().getIncomeMult().modifyPercent(spec.getId(), DEALMAKER_INCOME_PERCENT_BONUS,
                        Misc.ucFirst(spec.getName().toLowerCase()));
            }

            public void unapply(Industry industry) {
                industry.getMarket().getIncomeMult().unmodifyPercent(spec.getId());
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {
                text.addPara(pre + "Colony income increased by %s.",
                        pad, Misc.getHighlightColor(),
                        "" + (int) DEALMAKER_INCOME_PERCENT_BONUS + "%");
            }
        });

        replaceIndustries(IndEvo_ids.COMMERCE);
    }

    public static void replaceIndustries(String... industryIds) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            for (String id : industryIds){
                if (m.hasIndustry(id)) {

                    Industry ind = m.getIndustry(id);
                    replaceIndustry(ind);
                }
            }
        }
    }

    public static void replaceIndustry(Industry ind){
        MarketAPI m = ind.getMarket();
        String id = ind.getId();

        SpecialItemData special = ind.getSpecialItem();
        String aiCore = ind.getAICoreId();
        boolean improved = ind.isImproved();
        boolean isBuilding = ind.isBuilding();
        float buildProgress = ind.getBuildOrUpgradeProgress();

        m.removeIndustry(id, null, false);
        m.addIndustry(id);

        ind = m.getIndustry(id);

        if (isBuilding) {
            ind.startBuilding();
            ((BaseIndustry) ind).setBuildProgress(buildProgress);
        }

        ind.setSpecialItem(special);
        ind.setAICoreId(aiCore);
        ind.setImproved(improved);
    }

    public void resetDerelictRep(){
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if(f.isShowInIntelTab()) f.setRelationship(IndEvo_ids.DERELICT, -1);
            else f.setRelationship(IndEvo_ids.DERELICT, 1);
        }

        Global.getSector().getPlayerFaction().setRelationship(IndEvo_ids.DERELICT, -1);

        //just to make sure
        Global.getSector().getFaction(Factions.DERELICT).setRelationship("ML_bounty", 1);

        if(Global.getSettings().getModManager().isModEnabled("swp")){
            Global.getSector().getFaction(Factions.DERELICT).setRelationship("famous_bounty", 1);
        }
    }
}
