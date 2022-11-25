package industrial_evolution.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import industrial_evolution.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import industrial_evolution.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.Map;

public class IndEvo_LocatorSystemRatingUpdater extends BaseCampaignEventListener {
    public static final String MEM_LOCATOR_SYSTEM_RATING = "$IndEvo_locatorSystemRating";

    public static Map<String, Integer> salvageEntityMap = new HashMap<String, Integer>(){{
        put(Entities.STATION_MINING, 10);
        put(Entities.STATION_RESEARCH, 20);
        put(Entities.ORBITAL_HABITAT, 10);
        put(Entities.TECHNOLOGY_CACHE, 7);
        put(Entities.SUPPLY_CACHE, 7);
        put(Entities.SUPPLY_CACHE_SMALL, 4);
        put(Entities.EQUIPMENT_CACHE, 7);
        put(Entities.EQUIPMENT_CACHE_SMALL, 4);
        put(Entities.WEAPONS_CACHE, 7);
        put(Entities.WEAPONS_CACHE_LOW, 7);
        put(Entities.WEAPONS_CACHE_HIGH, 7);
        put(Entities.WEAPONS_CACHE_REMNANT, 7);
        put(Entities.WEAPONS_CACHE_SMALL, 3);
        put(Entities.WEAPONS_CACHE_SMALL_LOW, 4);
        put(Entities.WEAPONS_CACHE_SMALL_HIGH, 4);
        put(Entities.WEAPONS_CACHE_SMALL_REMNANT, 4);
        put(Entities.ALPHA_SITE_WEAPONS_CACHE, 20);
        put(Entities.DERELICT_SURVEY_PROBE, 1);
        put(Entities.DERELICT_SURVEY_SHIP, 9);
        put(Entities.DERELICT_MOTHERSHIP, 12);
        put(Entities.DERELICT_CRYOSLEEPER, 15);
        put(Entities.CORONAL_TAP, 20);
        put(IndEvo_ids.ARSENAL_ENTITY, 10);
        put(IndEvo_ids.LAB_ENTITY, 20);
        put(IndEvo_ids.GACHA_STATION, 20);
    }};

    public IndEvo_LocatorSystemRatingUpdater(boolean permaRegister) {
        super(permaRegister);
    }

    public static void register(){
        Global.getSector().addTransientListener(new IndEvo_LocatorSystemRatingUpdater(false));
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        super.reportFleetJumped(fleet, from, to);

        if (!fleet.isPlayerFleet()) return;

        try {
            if(from != null
                    && from.getContainingLocation() != null
                    && !from.getContainingLocation().isHyperspace()){

                LocationAPI loc = from.getContainingLocation();
                updateLoc(loc);
            }
        } catch (NullPointerException e){
            //in vanilla, there are no hyperspace to hyperspace jumps, but the hyperdrive mod has them and crashes this, for no apparent reason.
            //since I can't be bothered to fix someone elses mod, this is good enough
            IndEvo_modPlugin.log("Hyper to Hyper jump - IndEvo_LocatorSystemRatingUpdater null");
        }
    }

    private static void updateLoc(LocationAPI loc){
        int rating = calculateLocationRating(loc);
        addRatingToLocation(rating, loc);
    }

    private static int calculateLocationRating(LocationAPI loc){
        int amt = 0;

        for (SectorEntityToken token : loc.getEntitiesWithTag(Tags.SALVAGEABLE)){
            String id = token.getId();
            if(salvageEntityMap.containsKey(id)) amt += salvageEntityMap.get(id);
            else amt += 1;
        }

        for (SectorEntityToken token : loc.getEntitiesWithTag(Tags.DEBRIS_FIELD)){
            amt += getSpecialAmt(token);
            amt += 2f;
        }

        for (SectorEntityToken token : loc.getEntitiesWithTag(Tags.WRECK)){
            amt += getSpecialAmt(token);
        }

        for (MarketAPI market : Misc.getMarketsInLocation(loc)){
            if(market != null && market.isPlanetConditionMarketOnly() && !market.getMemoryWithoutUpdate().getBoolean("$ruinsExplored")){
                for (MarketConditionAPI condition : market.getConditions()){
                    String id = condition.getId();

                    switch (id){
                        case Conditions.RUINS_SCATTERED : amt += 3; break;
                        case Conditions.RUINS_WIDESPREAD : amt += 5; break;
                        case Conditions.RUINS_EXTENSIVE : amt += 7; break;
                        case Conditions.RUINS_VAST : amt += 10; break;
                        case IndEvo_ids.COND_RUINS: amt += 2; break;
                        case IndEvo_ids.COND_INFRA: amt += 2; break;
                    }
                }
            }

            //Make the core worlds light up like a christmas tree
            if(market != null && !market.isPlanetConditionMarketOnly()) amt += 10 * market.getSize();
        }

        return amt;
    }

    private static int getSpecialAmt(SectorEntityToken token){
        int amt = 0;

        if (Misc.getSalvageSpecial(token) instanceof ShipRecoverySpecial) {
            ShipRecoverySpecial.ShipRecoverySpecialData special = (ShipRecoverySpecial.ShipRecoverySpecialData) Misc.getSalvageSpecial(token);
            for (ShipRecoverySpecial.PerShipData data : special.ships) {
                ShipAPI.HullSize size = data.getVariant().getHullSpec().getHullSize();
                switch (size) {
                    case DEFAULT:
                    case FRIGATE: amt += 1; break;
                    case DESTROYER: amt += 2; break;
                    case CRUISER: amt += 3; break;
                    case CAPITAL_SHIP: amt += 4; break;
                }
            }
        }

        return amt;
    }

    private static void addRatingToLocation(int rating, LocationAPI loc){
        loc.getMemoryWithoutUpdate().set(MEM_LOCATOR_SYSTEM_RATING, rating);
    }

    public static void updateAllSystems(){
        for (LocationAPI loc : Global.getSector().getAllLocations()){
            if (loc.isHyperspace()) continue;

            updateLoc(loc);
        }
    }

    public static int getRating(LocationAPI loc) {
        MemoryAPI mem = loc.getMemoryWithoutUpdate();
        return mem.contains(MEM_LOCATOR_SYSTEM_RATING) ? loc.getMemoryWithoutUpdate().getInt(MEM_LOCATOR_SYSTEM_RATING) : 0;
    }
}
