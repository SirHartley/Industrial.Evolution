package indevo.WIP.mobilecolony.utility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.WIP.mobilecolony.memory.ColonyMemory;
import indevo.WIP.mobilecolony.memory.ColonyMemoryEntry;
import indevo.WIP.mobilecolony.scripts.ColonyFleetAssignmentAI;

public class MobileColonyFactory {

    public static final String MOBILE_COLONY_IDENTIFIER = "$IndEvo_MobileColony";
    public static final String MOBILE_COLONY_NUM = "$IndEvo_MobileColonyNum";

    private static final String SYSTEM_BASE_NAME = "IndEvo_MobileColonySystem_";
    private static final String MARKET_BASE_ID = "IndEvo_MobileColonyMarket_";

    /**
     * Does not actually spawn the fleet - that has to be done manually afterwards.
     * @return
     */
    public static ColonyMemoryEntry create(){
        int next = ColonyMemory.getNext();

        StarSystemAPI system = createHiddenStarSystem(SYSTEM_BASE_NAME + next);
        MarketAPI market = createMobileMarket(MARKET_BASE_ID + next, system);
        CampaignFleetAPI fleet = createFleet(next);

        ColonyMemoryEntry entry = new ColonyMemoryEntry(market,system,  fleet);
        ColonyMemory.add(next, entry);

        entry.joinFleetAndMarket();

        return entry;
    }


    public static CampaignFleetAPI createFleet(int i){
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet("player", "Mobile Colony", true);

        for (String s : Global.getSettings().getSortedAbilityIds()) {
            AbilitySpecAPI spec = Global.getSettings().getAbilitySpec(s);
            if (spec.isAIDefault()) {
                fleet.addAbility(s);
            }
        }

        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        memory.set(MOBILE_COLONY_IDENTIFIER, true);
        memory.set(MOBILE_COLONY_NUM, i);
        memory.set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
        memory.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        memory.set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
        memory.set(MemFlags.MEMORY_KEY_SKIP_TRANSPONDER_STATUS_INFO, true);

        fleet.setNoAutoDespawn(true);
        fleet.setNoFactionInName(true);

        fleet.addScript(new ColonyFleetAssignmentAI(fleet));

        return fleet;
    }

    public static MarketAPI createMobileMarket(String id, StarSystemAPI system){
        SectorEntityToken entity = system.addCustomEntity(null, "", Entities.MAKESHIFT_STATION, Factions.PLAYER);
        SectorEntityToken center = system.initNonStarCenter();
        entity.setLocation(center.getLocation().x, center.getLocation().y);

        String name = "Andvaranaut";
        MarketAPI market = Global.getFactory().createMarket(Misc.genUID(), name, 3);
        market.setSize(3);
        market.setName(name);
        market.setHidden(true);
        market.setFactionId(Factions.PLAYER);
        market.setPlayerOwned(false);

        market.setInvalidMissionTarget(true);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        market.addCondition(Conditions.POPULATION_3);
        market.addIndustry(Industries.POPULATION);
        market.addIndustry(Industries.SPACEPORT);

        market.addSubmarket(Submarkets.LOCAL_RESOURCES);
        market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());

        market.setPrimaryEntity(entity);
        market.setEconGroup(market.getId());

        MemoryAPI mem = market.getMemoryWithoutUpdate();
        //mem.set(MemFlags.MARKET_DO_NOT_INIT_COMM_LISTINGS, true);
        mem.set(DecivTracker.NO_DECIV_KEY, true);
        mem.set(MemFlags.HIDDEN_BASE_MEM_FLAG, true);
        mem.set(ContactIntel.NO_CONTACTS_ON_MARKET, true);
        market.addTag(Tags.MARKET_NO_OFFICER_SPAWN);

        market.reapplyIndustries();

        Global.getSector().getEconomy().addMarket(market, false);

        return market;
    }

    public static StarSystemAPI createHiddenStarSystem(String name){
        StarSystemAPI system = Global.getSector().createStarSystem(name);
        system.setBaseName(name);
        system.addTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER);
        system.addTag(Tags.DO_NOT_RESPAWN_PLAYER_IN);
        system.addTag(Tags.NOT_RANDOM_MISSION_TARGET);
        system.addTag(Tags.THEME_HIDDEN);
        system.setDoNotShowIntelFromThisLocationOnMap(true);

        return system;
    }
}
