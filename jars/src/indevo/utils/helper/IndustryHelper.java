package indevo.utils.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.RoleEntryAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import indevo.utils.memory.SessionTransientMemory;
import indevo.industries.derelicts.scripts.PlanetMovingScript;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;


public class IndustryHelper {
    public static final Logger log = Global.getLogger(IndustryHelper.class);

    public static void applyDeficitToProduction(Industry ind, int index, Pair<String, Integer> deficit, String... commodities) {
        String[] var7 = commodities;
        int var6 = commodities.length;

        for (int var5 = 0; var5 < var6; ++var5) {
            String commodity = var7[var5];
            if (!ind.getSupply(commodity).getQuantity().isUnmodified()) {
                ind.supply(String.valueOf(index), commodity, -(Integer) deficit.two, BaseIndustry.getDeficitText((String) deficit.one));
            }
        }
    }

    public static float smootherstep(float edge0, float edge1, float x) {
        //https://en.wikipedia.org/wiki/Smoothstep
        x = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    private static float clamp(float x, float lowerlimit, float upperlimit) {
        if (x < lowerlimit)
            x = lowerlimit;
        if (x > upperlimit)
            x = upperlimit;
        return x;
    }

    public static void addOrRemoveSubmarket(MarketAPI market, String submarketId, boolean shouldHave) {
        if (market.hasSubmarket(submarketId) && !shouldHave)
            market.removeSubmarket(submarketId);
        else if (!market.hasSubmarket(submarketId) && shouldHave) {
            market.addSubmarket(submarketId);
            market.getSubmarket(submarketId).getCargo();
        }
    }

    public static CargoAPI getIndustrialStorageCargo(MarketAPI market) {
        if (market == null) return null;
        SubmarketAPI submarket = market.getSubmarket(Ids.SHAREDSTORAGE);
        if (submarket == null) return null;
        return submarket.getCargo();
    }

    public static void finalizeAndUpdateFleetMember(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();

        variant = variant.clone();
        variant.setOriginalVariant(null);

        member.setVariant(variant, false, true);
        member.updateStats();
    }

    public static List<ShipHullSpecAPI> getAllLearnableShipHulls() {
        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();
        String key = "$IndEvo_all_blueprints_list_key";

        if (transientMemory.contains(key)) return (List<ShipHullSpecAPI>) transientMemory.get(key);
        List<ShipHullSpecAPI> hullSpecList = new ArrayList<>();

        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (isLearnable(spec)) {
                hullSpecList.add(spec);
            }
        }

        transientMemory.set(key, hullSpecList);
        return hullSpecList;
    }

    public static List<ShipHullSpecAPI> getAllRareShipHulls() {
        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();
        String key = "$IndEvo_rare_blueprints_list_key";

        if (transientMemory.contains(key)) return (List<ShipHullSpecAPI>) transientMemory.get(key);
        List<ShipHullSpecAPI> hullSpecList = new ArrayList<>();

        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (spec.getTags().contains("rare_bp")) {
                hullSpecList.add(spec);
            }
        }

        transientMemory.set(key, hullSpecList);
        return hullSpecList;
    }

    public static boolean isLearnable(ShipHullSpecAPI spec) {
        if (spec.getHullSize() == ShipAPI.HullSize.FIGHTER) return false;
        if (spec.getTags().contains("IndEvo_no_ship_roulette")) return false;

        for (String tag : spec.getTags()) {
            if (tag.endsWith("_bp")) return true;
        }

        return false;
    }

    public static Map<String, Float> addOrIncrement(Map<String, Float> map, String id, float increaseBy) {
        if (!map.containsKey(id)) {
            map.put(id, increaseBy);
        } else {
            map.put(id, map.get(id) + increaseBy);
        }

        return map;
    }

    public static Map<String, Integer> addOrIncrement(Map<String, Integer> map, String id, Integer increaseBy) {
        if (!map.containsKey(id)) {
            map.put(id, increaseBy);
        } else {
            map.put(id, map.get(id) + increaseBy);
        }

        return map;
    }

    //Split a long list into as many parts as the "itemsPerPage" counter needs to be kept
    public static List<List<String>> splitList(List<String> list, int itemsPerPage) {
        int i = 0;
        List<List<String>> splitList = new ArrayList<>();
        while (i < list.size()) {
            int nextInc = Math.min(list.size() - i, itemsPerPage);
            List<String> batch = new ArrayList<>(list.subList(i, i + nextInc));
            splitList.add(batch);
            i = i + nextInc;
        }

        return splitList;
    }

    public static List<List<String>> splitList(Set<String> set, int itemsPerPage) {
        List<String> l = new ArrayList<>(set);
        return splitList(l, itemsPerPage);
    }

    public static Set<SpecialItemData> getVPCItemSet() {
        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();
        String key = "$IndEvo_VPC_List_Key";

        if (transientMemory.contains(key)) return (Set<SpecialItemData>) transientMemory.get(key);

        Set<SpecialItemData> set = new HashSet<>();
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            if (spec.getId().contains("IndEvo_vpc")) set.add(new SpecialItemData(spec.getId(), null));
        }

        transientMemory.set(key, set);
        return set;
    }

    public static ShipVariantAPI stripShipNoCargo(FleetMemberAPI member) {
        ShipVariantAPI shipVar = member.getVariant();

        for (String slot : shipVar.getFittedWeaponSlots()) {

            if (shipVar.getSlot(slot).isBuiltIn()
                    || shipVar.getSlot(slot).isSystemSlot()
                    || shipVar.getSlot(slot).isDecorative()
                    || shipVar.getSlot(slot).isStationModule()) {
                continue;
            }

            WeaponSpecAPI weap = shipVar.getWeaponSpec(slot);
            if (weap.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) {
                continue;
            }

            shipVar.clearSlot(slot);
        }

        //clear all non built ins
        for (int i = 0; i < shipVar.getWings().size(); i++) {
            if (!shipVar.getHullSpec().isBuiltInWing(i)) shipVar.setWingId(i, "");
        }

        member.setVariant(shipVar, true, true);

        return shipVar;
    }

    public static int getDaysOfCurrentMonth() {
        CampaignClockAPI clock = Global.getSector().getClock();
        GregorianCalendar cal = new GregorianCalendar(clock.getCycle(), clock.getMonth(), clock.getDay());
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    public static String getAiCoreIdNotNull(Industry ind) {
        if (ind.getAICoreId() != null) {
            return ind.getAICoreId();
        }
        return "none";
    }

    public static CargoAPI getCargoCopy(CargoAPI source) {
        CargoAPI copy = source.createCopy();

        source.initMothballedShips(Global.getSector().getPlayerFaction().getId());
        copy.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        copy.getMothballedShips().clear();

        for (FleetMemberAPI member : source.getMothballedShips().getMembersListCopy()) {
            copy.getMothballedShips().addFleetMember(IndustryHelper.createFleetMemberClone(member));
        }

        return copy;
    }

    public static FleetMemberAPI createFleetMemberClone(FleetMemberAPI member) {
        return Global.getFactory().createFleetMember(FleetMemberType.SHIP, member.getVariant().clone());
    }

    public static List<String> convertFleetMemberListToIdList(List<FleetMemberAPI> fleetMemberList) {
        List<String> l = new ArrayList<>();

        for (FleetMemberAPI member : fleetMemberList) {
            l.add(member.getHullId());
        }

        return l;
    }

    public static MarketAPI getClosestMarketWithIndustry(MarketAPI toMarket, String id) {
        float shortestDistanceToTarget = Float.MAX_VALUE;

        MarketAPI bestTarget = null;
        for (MarketAPI market : Misc.getFactionMarkets(toMarket.getFaction())) {
            if (!market.hasIndustry(id)) continue;

            float distanceToTargetLY = Misc.getDistanceLY(toMarket.getLocation(), market.getLocationInHyperspace());

            if (distanceToTargetLY < shortestDistanceToTarget) {
                shortestDistanceToTarget = distanceToTargetLY;
                bestTarget = market;
            }
        }

        return bestTarget;
    }

    public static MarketAPI getClosestPlayerMarketWithIndustryFromSet(MarketAPI toMarket, String id, Set<MarketAPI> set) {
        float shortestDistanceToTarget = Float.MAX_VALUE;

        MarketAPI bestTarget = null;
        for (MarketAPI market : set) {
            if (!market.isPlayerOwned() || !market.hasIndustry(id)) continue;

            float distanceToTargetLY = Misc.getDistanceLY(toMarket.getLocation(), market.getLocationInHyperspace());

            if (distanceToTargetLY < shortestDistanceToTarget) {
                shortestDistanceToTarget = distanceToTargetLY;
                bestTarget = market;
            }
        }

        return bestTarget;
    }

    public static Map<String, Float> getMapFromMemory(String key) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        if (!memory.contains(key)) {
            memory.set(key, new HashMap<String, Float>());
        }

        Map<String, Float> prlist = new HashMap<>((Map<String, Float>) memory.get(key));
        return IndustryHelper.sortByLargestValue(prlist);
    }

    public static Map<String, Float> getClampedMap(Map<String, Float> map, Float limit) {
        Map<String, Float> clampedMap = new HashMap<>();
        //normalize to 1
        for (Map.Entry<String, Float> e : map.entrySet()) {
            clampedMap.put(e.getKey(), Math.min(limit, e.getValue()));
        }

        return clampedMap;
    }

    public static void storeMapInMemory(Map<String, Float> map, String key) {
        Global.getSector().getMemoryWithoutUpdate().set(key, map);
    }

    public static float customLog(float base, float logNumber) {
        return (float) (Math.log(logNumber) / Math.log(base));
    }

    //sort hashmap by values - hashmaps don't have order -> return a LinkedHashMap
    public static Map<String, Float> sortByLargestValue(Map<String, Float> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Float>> list =
                new LinkedList<>(hm.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Float>>() {
            public int compare(Map.Entry<String, Float> o1,
                               Map.Entry<String, Float> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        // put data from sorted list into a hashmap
        Map<String, Float> temp = new LinkedHashMap<>();
        for (Map.Entry<String, Float> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }

    //Get faction markets excluding salvageMarket
    public static List<MarketAPI> getFactionMarkets(FactionAPI faction) {
        List<MarketAPI> result = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getFaction() == faction && !market.getId().contains("SalvageMarket")) {
                result.add(market);
            }
        }
        return result;
    }

    public static ShipVariantAPI stripShipToCargoAndReturnVariant(FleetMemberAPI member, CargoAPI cargo) {
        if (cargo == null) return null;

        if(member.getCaptain() != null && member.getCaptain().isAICore()) {
            cargo.addCommodity(member.getCaptain().getAICoreId(), 1);
            member.setCaptain(null);
        }

        ShipVariantAPI shipVar = member.getVariant();

        for (String slot : shipVar.getFittedWeaponSlots()) {

            if (shipVar.getSlot(slot).isBuiltIn()
                    || shipVar.getSlot(slot).isSystemSlot()
                    || shipVar.getSlot(slot).isDecorative()) {
                continue;
            }

            WeaponSpecAPI wep = shipVar.getWeaponSpec(slot);
            if (wep.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) {
                continue;
            }

            cargo.addWeapons(shipVar.getWeaponId(slot), 1);
            shipVar.clearSlot(slot);
        }

        //move all non-inbuilt wings
        for (String wing : shipVar.getNonBuiltInWings()) {
            cargo.addFighters(wing, 1);
        }

        //clear all non built ins
        for (int i = 0; i < shipVar.getWings().size(); i++) {
            if (!shipVar.getHullSpec().isBuiltInWing(i)) shipVar.setWingId(i, "");
        }

        member.setVariant(shipVar, true, true);
        return shipVar;
    }

    public static ShipVariantAPI stripShipToCargoAndReturnVariant(FleetMemberAPI member, MarketAPI market) {
        MarketAPI target = IndustryHelper.getMarketForStorage(market);
        CargoAPI cargo = target != null ? Misc.getStorageCargo(market) : Global.getSector().getPlayerFleet().getCargo();

        return stripShipToCargoAndReturnVariant(member, cargo);
    }

    public static boolean planetHasRings(PlanetAPI planet) {
        boolean hasRings = false;
        List<SectorEntityToken> tokenList = PlanetMovingScript.getEntitiesWithOrbitTarget(planet);
        if (tokenList != null) {

            for (SectorEntityToken t : PlanetMovingScript.getEntitiesWithOrbitTarget(planet)) {
                if (t instanceof RingBandAPI) {
                    hasRings = true;
                    break;
                }
            }
        }

        return hasRings;
    }

    public static boolean planetHasRings(MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        boolean hasRings = false;
        List<SectorEntityToken> tokenList = PlanetMovingScript.getEntitiesWithOrbitTarget(planet);
        if (tokenList != null) {

            for (SectorEntityToken t : PlanetMovingScript.getEntitiesWithOrbitTarget(planet)) {
                if (t instanceof RingBandAPI) {
                    hasRings = true;
                    break;
                }
            }
        }

        return hasRings;
    }

    //excluding salvageMarket
    public static List<MarketAPI> getMarketsInLocation(LocationAPI location, String factionId) {
        List<MarketAPI> result = new ArrayList<>();
        for (MarketAPI curr : IndustryHelper.getMarketsInLocation(location)) {
            if (curr.getFactionId().equals(factionId)) {
                result.add(curr);
            }
        }
        return result;

    }

    //excluding salvageMarket
    public static List<MarketAPI> getMarketsInLocation(LocationAPI location) {
        List<MarketAPI> result = Global.getSector().getEconomy().getMarkets(location);
        List<MarketAPI> remove = new ArrayList<>();

        for (MarketAPI m : result) {
            if (m.isHidden()) remove.add(m);
        }

        result.removeAll(remove);

        return result;
    }

    //get the production point, or the local market if there is none
    public static MarketAPI getMarketForStorage(MarketAPI market) {
        MarketAPI prod = market.getFaction().getProduction().getGatheringPoint();
        MarketAPI targetMarket = prod.hasSubmarket(Submarkets.SUBMARKET_STORAGE) ? prod : market;

        return targetMarket.hasSubmarket(Submarkets.SUBMARKET_STORAGE) ? targetMarket : null;
    }

    /**
     * Stores generated Set as in transient memory and will attempt to retrieve it before generating a new one
     *
     * @param path
     * @return
     */

    //csv stuff
    public static Set<String> getCSVSetFromMemory(String path) {
        String idString = "$" + path;
        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();

        if (transientMemory.contains(idString)) {
            return (Set<String>) transientMemory.getSet(idString);
        } else {
            Set<String> csvSet = new HashSet<>();

            try {

                JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "IndEvo");
                for (int i = 0; i < config.length(); i++) {

                    JSONObject row = config.getJSONObject(i);
                    String id = row.getString("id");

                    csvSet.add(id);
                }
            } catch (IOException | JSONException ex) {
                log.error(ex);
            }

            transientMemory.set(idString, csvSet);
            return csvSet;
        }
    }

    /**
     * Is this boss ship (as specified in CSV) available given our currently loaded mods?
     *
     * @param factionOrModId
     * @return
     */

    public static boolean canLoadShips(String factionOrModId) {
        if (factionOrModId == null) return false;
        return Global.getSector().getFaction(factionOrModId) != null || Global.getSettings().getModManager().isModEnabled(factionOrModId);
    }

    public static Set<String> getPrismBossShips() {
        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();
        String path = "data/config/prism/prism_boss_ships.csv";
        String idString = "$" + path;

        if (transientMemory.contains(idString)) {
            return (Set<String>) transientMemory.getSet(idString);
        } else {

            Set<String> bossShips = new HashSet<>();
            try {
                JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "IndEvo");
                for (int i = 0; i < config.length(); i++) {

                    JSONObject row = config.getJSONObject(i);
                    String hullId = row.getString("id");
                    String factionId = row.optString("faction");

                    if (!canLoadShips(factionId)) continue;
                    bossShips.add(hullId);
                }
            } catch (IOException | JSONException ex) {
                log.error(ex);
            }
            transientMemory.set(idString, bossShips);
            return bossShips;
        }
    }

    public static Set<String> getVayraBossShips() {
        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();
        String path = "data/config/vayraBounties/unique_bounty_data.csv";
        String idString = "$" + path;

        if (transientMemory.contains(idString)) {
            return (Set<String>) transientMemory.getSet(idString);
        } else {
            Set<String> hvbShips = new HashSet<>();
            try {
                JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("bounty_id", path, "IndEvo");
                for (int i = 0; i < config.length(); i++) {

                    JSONObject row = config.getJSONObject(i);
                    String variantId = row.getString("flagshipVariantId");
                    String factionId = row.optString("faction");

                    if (!canLoadShips(factionId)
                            || Global.getSettings().getVariant(variantId) == null
                            || Global.getSettings().getVariant(variantId).getHullSpec() == null
                    ) continue;

                    hvbShips.add(Misc.getHullIdForVariantId(variantId));
                }
            } catch (IOException | JSONException ex) {
                log.error(ex);
            }

            transientMemory.set(idString, hvbShips);
            return hvbShips;
        }
    }

    public static boolean marketHasMilitaryIncludeRelays(MarketAPI market) {
        return marketHasMilitaryIncludeRelays(market, true);
    }

    public static boolean marketHasMilitaryIncludeRelays(MarketAPI market, boolean onlyFunctional) {

        boolean milPresent = false;

        boolean checkPresenceMilArray = market.hasIndustry(Ids.INTARRAY) && (!onlyFunctional || market.getIndustry(Ids.INTARRAY).isFunctional());
        boolean checkPresenceIntArray = market.hasIndustry(Ids.COMARRAY) && (!onlyFunctional || market.getIndustry(Ids.COMARRAY).isFunctional());

        if (marketHasMilitary(market, onlyFunctional) || checkPresenceIntArray || checkPresenceMilArray) {
            milPresent = true;
        }

        return milPresent;
    }

    public static boolean marketHasMilitary(MarketAPI market, boolean onlyFunctional) {

        boolean milPresent = false;
        boolean checkPresenceHC = market.hasIndustry(Industries.HIGHCOMMAND) && (!onlyFunctional || market.getIndustry(Industries.HIGHCOMMAND).isFunctional());
        boolean checkPresenceMB = market.hasIndustry(Industries.MILITARYBASE) && (!onlyFunctional || market.getIndustry(Industries.MILITARYBASE).isFunctional());
        boolean checkPresenceFHQ = market.hasIndustry(Industries.PATROLHQ) && (!onlyFunctional || market.getIndustry(Industries.PATROLHQ).isFunctional());

        if (checkPresenceFHQ || checkPresenceHC || checkPresenceMB) {
            milPresent = true;
        }

        return milPresent;
    }

    public static boolean isOnlyInstanceInSystemExcludeMarket(String id, StarSystemAPI system, MarketAPI excludeMarket, FactionAPI faction) {

        boolean onlyOne = true;

        //check the built or building industries for an entry
        List<MarketAPI> marketsInLocation = IndustryHelper.getMarketsInLocation(system, faction.getId());
        for (MarketAPI m : marketsInLocation) {
            if (m.getId().equals(excludeMarket.getId())) continue;

            if (m.hasIndustry(id)) {
                onlyOne = false;
                break;
            }

            //check the construction queue for an entry
            List<ConstructionQueue.ConstructionQueueItem> queueItem = new ArrayList<>(m.getConstructionQueue().getItems());
            for (ConstructionQueue.ConstructionQueueItem item : queueItem) {
                if (item.id.equals(id)) {
                    onlyOne = false;
                    break;
                }
            }
        }

        return onlyOne;
    }

    public static boolean systemHasIndustry(String id, StarSystemAPI system, FactionAPI faction) {
        boolean present = false;

        List<MarketAPI> marketsInLocation = IndustryHelper.getMarketsInLocation(system, faction.getId());
        for (MarketAPI playerMarket : marketsInLocation) {

            if (playerMarket.hasIndustry(id)) {
                present = true;
                break;
            }
        }
        return present;
    }

    public static boolean systemHasIndustryExcludeNotFunctional(String id, StarSystemAPI system, FactionAPI faction) {
        boolean present = false;

        List<MarketAPI> playerMarketsInSystem = IndustryHelper.getMarketsInLocation(system, faction.getId());
        for (MarketAPI playerMarket : playerMarketsInSystem) {

            if (playerMarket.hasIndustry(id)) {
                if (playerMarket.getIndustry(id).isFunctional()) {
                    present = true;
                    break;
                }
            }
        }
        return present;
    }

    public static boolean systemHasIndustry(String id, StarSystemAPI system, FactionAPI faction, boolean withUnfinished) {
        boolean present = false;

        List<MarketAPI> PlayerMarketsInSystem = IndustryHelper.getMarketsInLocation(system, faction.getId());
        for (MarketAPI PlayerMarket : PlayerMarketsInSystem) {

            if (PlayerMarket.hasIndustry(id)) {
                if (withUnfinished) {
                    present = true;
                    break;
                } else {
                    return !PlayerMarket.getIndustry(id).isBuilding();
                }

            }
        }
        return present;
    }

    public static int getAmountOfIndustryInSystem(String id, StarSystemAPI system, FactionAPI faction) {
        int amount = 0;

        List<MarketAPI> PlayerMarketsInSystem = IndustryHelper.getMarketsInLocation(system, faction.getId());
        for (MarketAPI PlayerMarket : PlayerMarketsInSystem) {
            List<Industry> thisMarketIndustries = new ArrayList<>(PlayerMarket.getIndustries());
            for (Industry i : thisMarketIndustries) {
                if (i.getId().equals(id)) {
                    amount++;
                }
            }

            List<ConstructionQueue.ConstructionQueueItem> queueItem = new ArrayList<>(PlayerMarket.getConstructionQueue().getItems());
            for (ConstructionQueue.ConstructionQueueItem item : queueItem) {
                if (item.id.equals(id)) {
                    amount++;
                    break;
                }
            }
        }

        return amount;
    }

    public static boolean isOnlyInstanceInSystem(String id, StarSystemAPI system, FactionAPI faction) {

        boolean onlyOne = true;

        //check the built or building industries for an entry
        List<MarketAPI> PlayerMarketsInSystem = IndustryHelper.getMarketsInLocation(system, faction.getId());
        for (MarketAPI PlayerMarket : PlayerMarketsInSystem) {
            List<Industry> thisMarketIndustries = new ArrayList<>(PlayerMarket.getIndustries());
            for (Industry i : thisMarketIndustries) {
                if (i.getId().equals(id)) {
                    onlyOne = false;
                    break;
                }
            }
            //check the construction queue for an entry
            List<ConstructionQueue.ConstructionQueueItem> queueItem = new ArrayList<>(PlayerMarket.getConstructionQueue().getItems());
            for (ConstructionQueue.ConstructionQueueItem item : queueItem) {
                if (item.id.equals(id)) {
                    onlyOne = false;
                    break;
                }
            }
        }
        return onlyOne;
    }

    public static Map<String, Set<String>> getShipRoleListMap() {
        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();
        String key = "$IndEvo_RoleListMap_Key";

        if (transientMemory.contains(key)) return (Map<String, Set<String>>) transientMemory.get(key);

        Set<String> combat = new HashSet<>();
        Set<String> carrier = new HashSet<>();
        Set<String> civilian = new HashSet<>();
        Set<String> phase = new HashSet<>();
        Set<String> freighter = new HashSet<>();
        Set<String> tanker = new HashSet<>();
        Set<String> personnel = new HashSet<>();

        for (FactionAPI fact : Global.getSector().getAllFactions()) {
            for (String s : new String[]{"combatSmallForSmallFleet", "combatSmall", "combatMedium", "combatLarge", "combatCapital"})
                combat.addAll(convertRoleSetToHullIDSet(fact.getId(), s));
            for (String s : new String[]{"carrierSmall", "carrierMedium", "carrierLarge"})
                carrier.addAll(convertRoleSetToHullIDSet(fact.getId(), s));
            for (String s : new String[]{"phaseSmall", "phaseMedium", "phaseLarge", "phaseCapital"})
                phase.addAll(convertRoleSetToHullIDSet(fact.getId(), s));
            for (String s : new String[]{"combatFreighterSmall", "combatFreighterMedium", "combatFreighterLarge", "freighterSmall", "freighterMedium", "freighterLarge"})
                freighter.addAll(convertRoleSetToHullIDSet(fact.getId(), s));
            for (String s : new String[]{"tankerSmall", "tankerMedium", "tankerLarge"})
                tanker.addAll(convertRoleSetToHullIDSet(fact.getId(), s));
            for (String s : new String[]{"personnelSmall", "personnelMedium", "personnelLarge", "linerSmall", "linerMedium", "linerLarge"})
                personnel.addAll(convertRoleSetToHullIDSet(fact.getId(), s));
            for (String s : new String[]{"civilianRandom"}) civilian.addAll(convertRoleSetToHullIDSet(fact.getId(), s));
        }

        HashMap<String, Set<String>> allRoleSets = new HashMap<>();
        allRoleSets.put("combat", combat);
        allRoleSets.put("carrier", carrier);
        allRoleSets.put("civilian", civilian);
        allRoleSets.put("phase", phase);
        allRoleSets.put("freighter", freighter);
        allRoleSets.put("tanker", tanker);
        allRoleSets.put("personnel", personnel);

        transientMemory.set(key, allRoleSets);
        return allRoleSets;
    }

    private static Set<String> convertRoleSetToHullIDSet(String factionId, String role) {
        Set<String> shipIdSet = new HashSet<>();

        List<RoleEntryAPI> rl = Global.getSettings().getEntriesForRole(factionId, role);
        for (RoleEntryAPI re : rl) {
            String hid = Misc.getHullIdForVariantId(re.getVariantId());
            if (hid != null) {
                shipIdSet.add(hid);
            }
        }

        return shipIdSet;
    }
}
	





