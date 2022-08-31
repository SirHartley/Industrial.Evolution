package com.fs.starfarer.api.impl.campaign.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.plugins.converters.IndEvo_ConverterWarManager;
import com.fs.starfarer.api.plugins.converters.IndEvo_TakeoverScript;
import com.fs.starfarer.api.plugins.converters.IndEvo_TakeoverScriptWithNex;
import org.apache.log4j.Logger;

import java.util.*;

import static com.fs.starfarer.api.IndEvo_IndustryHelper.addOrRemoveSubmarket;

public class IndEvo_LogCoreCond extends BaseMarketConditionPlugin implements EconomyTickListener {

    public static final Logger log = Global.getLogger(IndEvo_LogCoreCond.class);
    public static final String COND_MEM_KEY = "$IndEvo_LogCoreCondKey";
    public static final String CONVERTER_WAR_ACTIVE_KEY = "$IndEvo_ActiveConverterWar";
    public static final String COLONY_RESTORATION_PACKAGE = "$IndEvo_ColonyRestObj";
    public static final float MONTHLY_TAKEOVER_CHANCE_MOD = 0.07f;
    public int monthsWithActiveCore = 1;
    protected boolean hasRolledThisMonth = false;
    public boolean hasTakenOver = false;

    public static final Set<String> ALWAYS_CAPTURE_SUBMARKET = new HashSet(Arrays.asList(new String[]{
            "tiandong_retrofit", "ii_ebay"
    }));

    @Override
    public void apply(String id) {
        super.apply(id);
        if (!colonyHasLogCore()) return;

        Global.getSector().getListenerManager().addListener(this, true);

        if (!hasTakenOver && doesCoreInitTakeover()) {
            hasTakenOver = true;
            initTakeover();
        }

        if (hasTakenOver) {
            //since player will never access the ressource panel for a converter world, we'll simplify the economy to just output nothing
            //also solves the demand problem
            //this way, the stability and fleet size depend only on the available ressources
            for (Industry ind : market.getIndustries()) {
                for (MutableCommodityQuantity q : ind.getAllSupply())
                    q.getQuantity().modifyMult(IndEvo_ids.COND_LOG_CORE, 0f);
                for (MutableCommodityQuantity q : ind.getAllDemand())
                    q.getQuantity().modifyMult(IndEvo_ids.COND_LOG_CORE, 0f);
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        Global.getSector().getListenerManager().removeListener(this);
        Industry pop = market.getIndustry(Industries.POPULATION);

        for (MutableCommodityQuantity commodityQuantity : pop.getAllDemand()) {
            commodityQuantity.getQuantity().unmodify(IndEvo_ids.COND_LOG_CORE);
        }

    }

    public static void resetColonyToOriginalState(MarketAPI market) {
        transferMarketToOriginalFaction(market);

        // TODO: 18.10.2021 script to reestablish colony
        // Unrest, disruption ect gets handled here as well

        //scale the damage with the time it took to reconquer the planet
        //some industries might have gotten destroyed, others built depending on the needs of the AI

        getLogCoreCond(market).hasTakenOver = false;
    }

    public static IndEvo_LogCoreCond getLogCoreCond(MarketAPI m) {
        MemoryAPI memory = m.getMemoryWithoutUpdate();
        String ident;

        if (!m.hasCondition(IndEvo_ids.COND_LOG_CORE)) {
            ident = m.addCondition(IndEvo_ids.COND_LOG_CORE);
            memory.set(COND_MEM_KEY, ident);
        } else if (memory.contains(COND_MEM_KEY)) {
            ident = memory.getString(COND_MEM_KEY);
        } else return (IndEvo_LogCoreCond) m.getFirstCondition(IndEvo_ids.COND_LOG_CORE).getPlugin();

        return (IndEvo_LogCoreCond) m.getSpecificCondition(ident).getPlugin();
    }


    private void initTakeover() {
        transferMarketToConverters(market);

        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();

        if (!memory.getBoolean(CONVERTER_WAR_ACTIVE_KEY)) {
            Global.getSector().getListenerManager().addListener(new IndEvo_ConverterWarManager());
            memory.set(CONVERTER_WAR_ACTIVE_KEY, true);
        }
    }

    public static PersonAPI addNewConverterAIAdmin(MarketAPI market) {
        Global.getSector().getCharacterData().removeAdmin(market.getAdmin());

        PersonAPI admin;
        admin = Global.getFactory().createPerson();
        admin.setFaction(IndEvo_ids.CONVERTERS_FACTION_ID);
        admin.setAICoreId("gamma_core");
        admin.setPortraitSprite("graphics/portraits/portrait_IndEvo_ai3.png");

        admin.setRankId(null);
        admin.setPostId(Ranks.POST_ADMINISTRATOR);

        market.setAdmin(admin);
        market.getCommDirectory().addPerson(admin, 0);
        market.addPerson(admin);

        return admin;
    }

    public static void transferMarketToConverters(MarketAPI market) {

        FactionAPI oldOwner = market.getFaction();
        FactionAPI newOwner = Global.getSector().getFaction(IndEvo_ids.CONVERTERS_FACTION_ID);
        String newOwnerId = newOwner.getId();
        String oldOwnerId = oldOwner.getId();

        MemoryAPI mem = market.getMemoryWithoutUpdate();
        ColonyRestorationPackage restorationPackage = new ColonyRestorationPackage();

        restorationPackage.originalFactionId = oldOwnerId;
        restorationPackage.wasPlayerOwned = market.isPlayerOwned();

        //mark invalid target
        restorationPackage.isInvalidMissionTarget = market.isInvalidMissionTarget();
        market.setInvalidMissionTarget(true);

        restorationPackage.administrator = market.getAdmin();

        //for this, remove all comm board people since the market is now under AI control
        //store in variable assigned to market and reestablish when returned to initial faction
        List<PersonAPI> commBoardList = new ArrayList<>();
        for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy()) {
            if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
            PersonAPI person = (PersonAPI) dir.getEntryData();
            commBoardList.add(person);
        }

        restorationPackage.personList = commBoardList;
        market.getCommDirectory().clear();

        market.setFactionId(newOwnerId);

        restorationPackage.freeport = market.isFreePort();
        market.setFreePort(false);

        market.setPlayerOwned(false);
        market.setEconGroup(IndEvo_ids.CONVERTERS_FACTION_ID);

        addNewConverterAIAdmin(market);
        market.addTag(Tags.NO_MARKET_INFO);

        if (Global.getSettings().getModManager().isModEnabled("nexerelin")) {
            IndEvo_TakeoverScriptWithNex.finalizeColonyExchange(market, newOwnerId, oldOwnerId);
        } else IndEvo_TakeoverScript.finalizeColonyExchange(market, newOwnerId);

        mem.set(COLONY_RESTORATION_PACKAGE, restorationPackage);
    }

    public static void transferMarketToOriginalFaction(MarketAPI market) {
        FactionAPI oldOwner = market.getFaction();

        MemoryAPI mem = market.getMemoryWithoutUpdate();
        ColonyRestorationPackage restorationPackage = (ColonyRestorationPackage) mem.get(COLONY_RESTORATION_PACKAGE);

        String newOwnerId = restorationPackage.originalFactionId;
        String oldOwnerId = oldOwner.getId();

        market.setInvalidMissionTarget(restorationPackage.isInvalidMissionTarget);

        //clear the board and repopulate with what it used to be
        market.getCommDirectory().clear();
        for (PersonAPI person : restorationPackage.personList) {
            market.getCommDirectory().addPerson(person);
        }

        market.setFactionId(newOwnerId);
        market.setPlayerOwned(restorationPackage.wasPlayerOwned);

        if (!market.isPlayerOwned()) market.setAdmin(restorationPackage.administrator);
        else market.setAdmin(Global.getSector().getPlayerPerson());

        market.setEconGroup(null);
        market.setFreePort(restorationPackage.freeport);
        market.removeTag(Tags.NO_MARKET_INFO);

        if (Global.getSettings().getModManager().isModEnabled("nexerelin")) {
            IndEvo_TakeoverScriptWithNex.finalizeColonyExchange(market, oldOwnerId, newOwnerId);
        } else IndEvo_TakeoverScript.finalizeColonyExchange(market, oldOwnerId);

        mem.unset(COLONY_RESTORATION_PACKAGE);
    }


    public static void updateSubmarkets(MarketAPI market, String newOwnerId) {
        boolean isPlayer = newOwnerId.equals(Factions.PLAYER) || market.isPlayerOwned();
        boolean haveOpen = false;
        boolean haveMilitary = !isPlayer && (market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND) || market.hasIndustry("tiandong_merchq"));
        boolean haveBlackMarket = false;

        if (!isPlayer || market.hasIndustry("commerce")) haveOpen = true;

        addOrRemoveSubmarket(market, Submarkets.LOCAL_RESOURCES, isPlayer);
        addOrRemoveSubmarket(market, Submarkets.SUBMARKET_OPEN, haveOpen);
        addOrRemoveSubmarket(market, Submarkets.SUBMARKET_BLACK, haveBlackMarket);
        addOrRemoveSubmarket(market, Submarkets.GENERIC_MILITARY, haveMilitary);
    }

    public static List<SectorEntityToken> getCapturableEntitiesAroundPlanet(SectorEntityToken primary) {
        List<SectorEntityToken> results = new ArrayList<>();
        for (SectorEntityToken token : primary.getContainingLocation().getAllEntities()) {
            if (token.getCustomEntitySpec() == null) continue;
            if (token.getMarket() != null) continue;
            if (token.hasTag(Tags.OBJECTIVE)) continue;
            if (token instanceof CampaignFleetAPI) continue;
            if (token.getOrbit() == null || token.getOrbit().getFocus() != primary)
                continue;
            if (token.getFaction() != primary.getFaction())
                continue;
            results.add(token);
        }

        return results;
    }

    private boolean doesCoreInitTakeover() {
        if (hasRolledThisMonth) return false;
        if (market.getName().toLowerCase().equals("davemode")) return true;

        hasRolledThisMonth = true;

        int seed = this.getClass().hashCode() + monthsWithActiveCore;
        Random rand = new Random(seed);

        if (Global.getSettings().isDevMode())
            log.info("rolling to check if takeover at " + IndEvo_StringHelper.getAbsPercentString(MONTHLY_TAKEOVER_CHANCE_MOD * monthsWithActiveCore, false));

        return rand.nextFloat() < (MONTHLY_TAKEOVER_CHANCE_MOD * monthsWithActiveCore);
    }

    @Override
    public boolean showIcon() {
        return false;
    }

    public String getModId() {
        return condition.getId();
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (Global.getSettings().isDevMode()) {
            if (colonyHasLogCore()) monthsWithActiveCore++;
            hasRolledThisMonth = false;
        }

    }

    private boolean colonyHasLogCore() {
        for (Industry ind : market.getIndustries()) {
            if (ind.getSpecialItem() != null && ind.getSpecialItem().getId().equals(IndEvo_Items.LOG_CORE)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void reportEconomyMonthEnd() {
        if (colonyHasLogCore()) monthsWithActiveCore++;
        hasRolledThisMonth = false;
    }

    public static class ColonyRestorationPackage {
        public String originalFactionId;
        public List<PersonAPI> personList;
        public boolean isInvalidMissionTarget;
        public boolean wasPlayerOwned;
        public PersonAPI administrator;
        public boolean freeport;

        public ColonyRestorationPackage() {
        }
    }
}
