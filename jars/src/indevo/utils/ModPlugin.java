package indevo.utils;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidSource;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.weapons.ai.IndEvo_missileProjectileAI;
import data.scripts.weapons.ai.IndEvo_mortarProjectileAI;
import indevo.WIP.mobilecolony.plugins.MobileColonyCampaignPlugin;
import indevo.abilities.skills.scripts.AdminGovernTimeTracker;
import indevo.abilities.skills.scripts.MicromanagementSkillEffectScript;
import indevo.abilities.splitfleet.SplinterFleetCampignPlugin;
import indevo.abilities.splitfleet.listeners.DetachmentAbilityAdder;
import indevo.dialogue.research.*;
import indevo.dialogue.research.AutopulseUseChecker;
import indevo.dialogue.research.DoritoGunFoundChecker;
import indevo.dialogue.research.HyperspaceTopoProgressChecker;
import indevo.dialogue.research.listeners.CamouflageRefitListener;
import indevo.dialogue.research.listeners.HasUniqueShipChecker;
import indevo.economy.listeners.ResourceConditionApplicator;
import indevo.exploration.crucible.CrucibleSpawner;
import indevo.exploration.gacha.GachaStationCampaignPlugin;
import indevo.exploration.gacha.GachaStationPlacer;
import indevo.exploration.minefields.conditions.MineFieldCondition;
import indevo.exploration.minefields.listeners.InterdictionPulseAbilityListener;
import indevo.exploration.minefields.listeners.RecentJumpListener;
import indevo.exploration.salvage.utils.IndEvo_SalvageSpecialAssigner;
import indevo.exploration.stations.DerelictStationPlacer;
import indevo.ids.Ids;
import indevo.industries.TradeCenter;
import indevo.industries.artillery.listener.WatchtowerFactionResetListener;
import indevo.industries.artillery.plugins.ArtilleryCampaignPlugin;
import indevo.industries.artillery.scripts.ArtilleryStationReplacer;
import indevo.industries.artillery.scripts.EyeIndicatorScript;
import indevo.industries.artillery.utils.ArtilleryStationPlacer;
import indevo.industries.assembler.listeners.DepositMessage;
import indevo.industries.changeling.industry.population.RuralPolitySubIndustry;
import indevo.industries.changeling.listener.MarineLossAmplifcationHullmodEffectListener;
import indevo.industries.changeling.plugins.ChangelingMiningOptionProvider;
import indevo.industries.changeling.plugins.ChangelingPopulationOptionProvider;
import indevo.industries.changeling.plugins.ChangelingRefiningOptionProvider;
import indevo.industries.changeling.plugins.CorporateGovernanceCampaignPlugin;
import indevo.industries.courierport.listeners.ShippingManager;
import indevo.industries.derelicts.listeners.AncientLabCommoditySwitchOptionProvider;
import indevo.industries.derelicts.utils.RuinsManager;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.industries.embassy.scripts.HostileActivityEventSubRegisterScript;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.plugins.PetCenterOptionProvider;
import indevo.industries.petshop.plugins.PetShopCampaignPlugin;
import indevo.industries.ruinfra.listener.RuinfraOnDecivListener;
import indevo.industries.ruinfra.utils.DerelictInfrastructurePlacer;
import indevo.industries.worldwonder.plugins.WorldWonderAltImageOptionProvider;
import indevo.industries.worldwonder.plugins.WorldWonderTexChangeOptionProvider;
import indevo.items.consumables.listeners.ConsumableItemDropListener;
import indevo.items.consumables.listeners.ConsumableItemMarketAdder;
import indevo.items.consumables.listeners.LocatorSystemRatingUpdater;
import indevo.items.consumables.listeners.SpooferItemKeypressListener;
import indevo.items.installable.SpecialItemEffectsRepo;
import indevo.items.listeners.ShipComponentLootManager;
import indevo.items.listeners.SpecialItemDropsListener;
import indevo.other.PlayerHyperspaceTripTracker;
import indevo.dialogue.research.scripts.RefitUIOpenChecker;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.timers.RaidTimeout;
import indevo.utils.timers.TimeTracker;
import indevo.utils.trails.MagicCampaignTrailPlugin;
import indevo.utils.update.NewGameIndustryPlacer;
import org.dark.shaders.light.LightData;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;

import java.io.IOException;
import java.util.*;

import static indevo.industries.academy.rules.IndEvo_AcademyVariables.ACADEMY_MARKET_ID;

public class ModPlugin extends BaseModPlugin {
    public static void log(String Text) {
        if (Global.getSettings().isDevMode()) Global.getLogger(ModPlugin.class).info(Text);
    }

    public static int DEALMAKER_INCOME_PERCENT_BONUS = 25;

    @Override
    public void onApplicationLoad() throws Exception {
        boolean hasLazyLib = Global.getSettings().getModManager().isModEnabled("lw_lazylib");
        if (!hasLazyLib) {
            throw new RuntimeException("Industrial Evolution requires LazyLib by LazyWizard" + "\nDownload at http://fractalsoftworks.com/forum/index.php?topic=5444");
        }

        boolean hasMagicLib = Global.getSettings().getModManager().isModEnabled("MagicLib");
        if (!hasMagicLib) {
            throw new RuntimeException("Industrial Evolution requires MagicLib!" + "\nDownload at http://fractalsoftworks.com/forum/index.php?topic=13718");
        }

        boolean hasLunaLib = Global.getSettings().getModManager().isModEnabled("lunalib");
        if (!hasLunaLib) {
            throw new RuntimeException("Industrial Evolution requires LunaLib!" + "\nDownload at https://fractalsoftworks.com/forum/index.php?topic=25658");
        }

        boolean hasGraphicsLib = Global.getSettings().getModManager().isModEnabled("shaderLib");
        if (hasGraphicsLib) {
            ShaderLib.init();
            LightData.readLightDataCSV("data/lights/IndEvo_lights.csv");
            TextureData.readTextureDataCSV((String)"data/lights/IndEvo_textures.csv");
        }

    }

    @Override
    public void onGameLoad(boolean newGame) {
        boolean devmode = Global.getSettings().isDevMode();
        boolean devActions = true; //Todo SET TO FALSE FOR RELEASE

        if (devmode && devActions && newGame) {
            PersonAPI admin = OfficerManagerEvent.createAdmin(Global.getSector().getPlayerFaction(), 0, new Random());
            admin.getStats().setSkillLevel("indevo_Micromanagement", 1);
            Global.getSector().getCharacterData().addAdmin(admin);
            CrucibleSpawner.spawn();



            //y.setLocation(Global.getSector().getPlayerFleet().getLocation().x, Global.getSector().getPlayerFleet().getLocation().y);

            /*
            t.addScript(new CampaignAttackScript(t, CampaignAttackScript.TYPE_RAILGUN));

            SubspaceSystem.gen();

            for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()){
                Pet.cycleToCustomVariant(m);
                m.getVariant().addPermaMod("IndEvo_handBuilt");
            }

            for (PetData d : PetDataRepo.getAll()){
                Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(ItemIds.PET_CHAMBER, d.id), 1);
            }*/
        }

        //core
        createAcademyMarket();
        setListenersIfNeeded();
        setScriptsIfNeeded();

        ResourceConditionApplicator.applyResourceConditionToAllMarkets();
        SpecialItemEffectsRepo.addItemEffectsToVanillaRepo();

        loadTransientMemory();

        //balance and spec changes
        addTypePrefaceToIndustrySpecs();
        if (Settings.getBoolean(Settings.COMMERCE_BALANCE_CHANGES)) overrideVanillaCommerce();

        LocatorSystemRatingUpdater.updateAllSystems();
        resetDerelictRep();

        if (newGame) ArtilleryStationReplacer.register();

        //pets
        if (Settings.getBoolean(Settings.PETS)){
            if (ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(Ids.PROJ_NAVI).getProgress().redeemed) PetDataRepo.get("fairy").tags.remove(PetData.TAG_NO_SELL);

            for(PetData data : PetDataRepo.getAll()) {
                try {
                    Global.getSettings().loadTexture(data.icon);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            addLordFoogRep();
        }
    }

    public void addLordFoogRep(){
        String id = "foogAristo";
        String marketId = "athulf";
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);

        if (market == null) return;

        ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
        if (ip.getPerson(id) != null) return;

        PersonAPI person = market.getFaction().createRandomPerson(FullName.Gender.FEMALE);
        person.setId(id);
        person.setRankId("aristocrat");
        person.setPostId("Knight of Foog");
        person.setPortraitSprite("graphics/portraits/indevo_persean_aristocrat.png");
        person.setFaction(Factions.PERSEAN);

        market.getCommDirectory().addPerson(person);
        market.addPerson(person);
        ip.addPerson(person);
        ip.getData(person).getLocation().setMarket(market);
        ip.checkOutPerson(person, "permanent_staff");
    }

    public void addTypePrefaceToIndustrySpecs(){
        for (IndustrySpecAPI spec : Global.getSettings().getAllIndustrySpecs()){
            String preface = "Type: ";
            if (spec.getDesc().startsWith(preface)) continue;

            List<String> tagList = new ArrayList<>();
            if (spec.getTags().contains("industrial")) tagList.add("industrial");
            if (spec.getTags().contains("rural")) tagList.add("rural");
            if (spec.getTags().contains("military") || spec.getTags().contains("patrol") || spec.getTags().contains("command") || spec.getNewPluginInstance(Global.getFactory().createMarket(com.fs.starfarer.api.util.Misc.genUID(), "", 1)) instanceof MilitaryBase) tagList.add("military");

            if (tagList.isEmpty()) continue;

            StringBuilder type = new StringBuilder();

            for (String tag : tagList){
                if (type.length() > 0 && tagList.get(tagList.size()-1).equals(tag)) type.append(", ");
                type.append(com.fs.starfarer.api.util.Misc.ucFirst(tag));
            }

            type.insert(0, preface).append("\n\n");
            spec.setDesc(type + spec.getDesc());
        }
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        super.onNewGameAfterEconomyLoad();

        RuinsManager.forceCleanCoreRuins();
        NewGameIndustryPlacer.run();
        ArtilleryStationPlacer.placeDerelictArtilleries();
        ArtilleryStationPlacer.placeCoreWorldArtilleries();
        GachaStationPlacer.place();
        createAcademyMarket();

        if (Settings.getBoolean(Settings.ENABLE_DERELICTS)) {
            new DerelictStationPlacer().init();
            new IndEvo_SalvageSpecialAssigner().init();
        }

        spawnMineFields();
        DerelictInfrastructurePlacer.placeRuinedInfrastructure();
        RuinsManager.placeIndustrialRuins();

        //ConverterRepRestetter.resetConverterRep();
    }

    protected Map<SectorEntityToken, LocationAPI> mines = new HashMap<SectorEntityToken, LocationAPI>();

    @Override
    public void beforeGameSave() {
        super.beforeGameSave();

        mines.clear();

        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (SectorEntityToken mine : new ArrayList<>(loc.getEntitiesWithTag("IndEvo_Mine"))) {
                //count++;
                AsteroidSource source = com.fs.starfarer.api.util.Misc.getAsteroidSource(mine);
                if (source == null || !mine.getMemoryWithoutUpdate().isEmpty()) {
                    if (source != null) {
                        source.reportAsteroidPersisted(mine);
                        com.fs.starfarer.api.util.Misc.clearAsteroidSource(mine);
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
        if (!Settings.getBoolean(Settings.ENABLE_MINEFIELDS)) return;

        for (SectorEntityToken mine : mines.keySet()) {
            ((LocationAPI) mines.get(mine)).addEntity(mine);
        }
        mines.clear();
    }

    public void spawnMineFields() {
        if (Global.getSector().getEconomy().getMarket("culann") == null || !Settings.getBoolean(Settings.ENABLE_MINEFIELDS))
            return;

        MarketAPI m = Global.getSector().getEconomy().getMarket("culann");
        m.addCondition("IndEvo_mineFieldCondition");

        m = Global.getSector().getEconomy().getMarket("chicomoztoc");
        m.getMemoryWithoutUpdate().set(MineFieldCondition.NO_ADD_BELT_VISUAL, true);
        m.addCondition("IndEvo_mineFieldCondition");

        m = Global.getSector().getEconomy().getMarket("kantas_den");
        m.getMemoryWithoutUpdate().set(MineFieldCondition.NO_ADD_BELT_VISUAL, true);
        m.addCondition("IndEvo_mineFieldCondition");
    }

    @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        if (missile.getProjectileSpecId().equals("IndEvo_mortar_projectile"))
            return new PluginPick<MissileAIPlugin>(new IndEvo_mortarProjectileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        if (missile.getProjectileSpecId().equals("IndEvo_missile_projectile"))
            return new PluginPick<MissileAIPlugin>(new IndEvo_missileProjectileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);

        return super.pickMissileAI(missile, launchingShip);
    }

    private void createAcademyMarket() {
        if (Global.getSector().getEconomy().getMarket(ACADEMY_MARKET_ID) == null
                && Global.getSector().getEntityById("station_galatia_academy") != null) {

            log("Creating academy market");

            MarketAPI market = Global.getFactory().createMarket(ACADEMY_MARKET_ID, "Galatia Academy", 3);
            market.setHidden(true);
            market.getMemoryWithoutUpdate().set(ContactIntel.NO_CONTACTS_ON_MARKET, true);
            market.getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);

            market.setFactionId(Factions.INDEPENDENT);
            market.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
            market.addCondition(Conditions.POPULATION_3);

            market.addIndustry(Industries.POPULATION);
            market.addIndustry(Ids.ACADEMY);

            market.setPrimaryEntity(Global.getSector().getEntityById("station_galatia_academy"));
            Global.getSector().getEconomy().addMarket(market, false);
        }
    }

    private void loadTransientMemory() {
        MiscIE.getVPCItemSet();
        MiscIE.getVayraBossShips();
        MiscIE.getPrismBossShips();

        MiscIE.getCSVSetFromMemory(Ids.EMBASSY_LIST);
        MiscIE.getCSVSetFromMemory(Ids.ORDER_LIST);
        MiscIE.getCSVSetFromMemory(Ids.PRINT_LIST);
        MiscIE.getCSVSetFromMemory(Ids.REVERSE_LIST);
        MiscIE.getCSVSetFromMemory(Ids.SHIPPING_LIST);
        MiscIE.getCSVSetFromMemory(Ids.BUREAU_LIST);
        MiscIE.getCSVSetFromMemory(Ids.RUIND_LIST);
        MiscIE.getCSVSetFromMemory(Ids.CLOUD_LIST);
        MiscIE.getCSVSetFromMemory(Ids.LAB_LIST);
    }

    private void setListenersIfNeeded() {
        SpecialItemEffectsRepo.initEffectListeners();

        ListenerManagerAPI l = Global.getSector().getListenerManager();

        if (!l.hasListenerOfClass(ResourceConditionApplicator.class))
            l.addListener(new ResourceConditionApplicator(), true);
        if (!l.hasListenerOfClass(RuinsManager.ResolveRuinsToUpgradeListener.class))
            l.addListener(new RuinsManager.ResolveRuinsToUpgradeListener(), true);
        if (!l.hasListenerOfClass(DepositMessage.class)) l.addListener(new DepositMessage(), true);
        if (!l.hasListenerOfClass(AmbassadorPersonManager.class))
            l.addListener(new AmbassadorPersonManager(), true);
        if (!l.hasListenerOfClass(RaidTimeout.class)) l.addListener(new RaidTimeout(), true);
        if (!l.hasListenerOfClass(ShipComponentLootManager.PartsCargoInterceptor.class))
            l.addListener(new ShipComponentLootManager.PartsCargoInterceptor(), true);
        if (!l.hasListenerOfClass(DoritoGunFoundChecker.class))
            l.addListener(new DoritoGunFoundChecker(), true);

        Global.getSector().addTransientListener(new ShipComponentLootManager.PartsLootAdder(false));
        Global.getSector().addTransientListener(new AmbassadorPersonManager.checkAmbassadorPresence());
        //Global.getSector().addTransientListener(new DialogueInterceptListener(false));

        RuinsManager.DerelictRuinsPlacer.register();
        RuinsManager.ResolveRuinsToUpgradeListener.register();
        ShippingManager.getInstanceOrRegister();
        DetachmentAbilityAdder.register();
        LocatorSystemRatingUpdater.register();
        ConsumableItemDropListener.register();
        ConsumableItemMarketAdder.register();
        SpecialItemDropsListener.register();
        RecentJumpListener.register();
        SpooferItemKeypressListener.register();
        InterdictionPulseAbilityListener.register();
        WorldWonderAltImageOptionProvider.register();
        WorldWonderTexChangeOptionProvider.register();
        ChangelingPopulationOptionProvider.register();
        ChangelingMiningOptionProvider.register();
        ChangelingRefiningOptionProvider.register();
        AncientLabCommoditySwitchOptionProvider.register();
        PetCenterOptionProvider.register();
        RuralPolitySubIndustry.RuralPolityTooltipAdder.register();
        RuralPolitySubIndustry.RuralPolityImageChanger.register();
        RuinfraOnDecivListener.register();
        MarineLossAmplifcationHullmodEffectListener.register();
        PlayerHyperspaceTripTracker.register();
        WatchtowerFactionResetListener.register();
        HyperspaceTopoProgressChecker.register();
        MicromanagementSkillEffectScript.register(); //it's not broke, it's kotlin.
        AdminGovernTimeTracker.getInstanceOrRegister();
        SlowShipInFleetChecker.register();
        AutopulseUseChecker.register();
        RefitUIOpenChecker.register();
        CamouflageRefitListener.register();
        HasUniqueShipChecker.register();
        //DistressCallManager.getInstanceOrRegister();
        //HullmodTimeTracker.getInstanceOrRegister();
    }

    private void setScriptsIfNeeded() {
        //Scripts:
        Global.getSector().registerPlugin(new SplinterFleetCampignPlugin());
        Global.getSector().registerPlugin(new GachaStationCampaignPlugin());
        Global.getSector().registerPlugin(new ArtilleryCampaignPlugin());
        Global.getSector().registerPlugin(new MobileColonyCampaignPlugin());
        Global.getSector().registerPlugin(new PetShopCampaignPlugin());
        Global.getSector().registerPlugin(new CorporateGovernanceCampaignPlugin());

        if (!Global.getSector().hasScript(TimeTracker.class)) {
            Global.getSector().addScript(new TimeTracker());
        }

        MagicCampaignTrailPlugin.register();
        EyeIndicatorScript.register();
        HostileActivityEventSubRegisterScript.register();

        //PlayerFleetFollower.register();
    }

    public void overrideVanillaCommerce() {
        ItemEffectsRepo.ITEM_EFFECTS.put(Items.DEALMAKER_HOLOSUITE, new BaseInstallableItemEffect(Items.DEALMAKER_HOLOSUITE) {
            public void apply(Industry industry) {
                industry.getMarket().getIncomeMult().modifyPercent(spec.getId(), DEALMAKER_INCOME_PERCENT_BONUS,
                        com.fs.starfarer.api.util.Misc.ucFirst(spec.getName().toLowerCase()));
            }

            public void unapply(Industry industry) {
                industry.getMarket().getIncomeMult().unmodifyPercent(spec.getId());
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {
                text.addPara(pre + "Colony income increased by %s.",
                        pad, com.fs.starfarer.api.util.Misc.getHighlightColor(),
                        "" + (int) DEALMAKER_INCOME_PERCENT_BONUS + "%");
            }
        });

        replaceIndustries(TradeCenter.class, Ids.COMMERCE);
    }

    public static void replaceIndustries(Class<?> targetClass, String... industryIds) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            for (String id : industryIds) {
                if (m.hasIndustry(id)) {

                    Industry ind = m.getIndustry(id);
                    if (!ind.getClass().isInstance(targetClass)) replaceIndustry(ind);
                }
            }
        }
    }

    public static void replaceIndustry(Industry ind) {

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

    public void resetDerelictRep() {
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isShowInIntelTab()) f.setRelationship(Ids.DERELICT_FACTION_ID, -1);
            else f.setRelationship(Ids.DERELICT_FACTION_ID, 1);
        }

        Global.getSector().getPlayerFaction().setRelationship(Ids.DERELICT_FACTION_ID, -1);

        //just to make sure
        Global.getSector().getFaction(Factions.DERELICT).setRelationship("ML_bounty", 1);

        if (Global.getSettings().getModManager().isModEnabled("swp")) {
            Global.getSector().getFaction(Factions.DERELICT).setRelationship("famous_bounty", 1);
        }
    }
}
