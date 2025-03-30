package indevo.other;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.DisposableFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager;
import com.fs.starfarer.api.util.Misc;

public class PIRATE_Event_FleetManager extends DisposableFleetManager {

    public static String CROWN_WAR_OFF = "$crown_ceasefire";
    public static int MAX_PIRATE_FLEETS = 3;
    private static final float MAX_LY_FROM_CROWN = 6f;
    private static final float MAX_LY_FROM_CROWN_DESPAWN = 2f;

    public static boolean PIRATE_Active() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getBoolean(CROWN_WAR_OFF);
    }


    @Override
    protected Object readResolve() {
        super.readResolve();
        return this;
    }

    @Override
    protected String getSpawnId() {
        return "pirate_event_spawn"; // not a faction id, just an identifier for this spawner
    }

//    @Override
//    public void advance(float amount) {
//       if (!PIRATE_Active()) {
//            return;
//        }
//    }

    protected StarSystemAPI pickCurrentSpawnLocation() {
        return getCrowConstellation();
    }

    @Override
    protected int getDesiredNumFleetsForSpawnLocation() {
        StarSystemAPI closestSystem = getCrowConstellation();

        float desiredNumFleets;
        if (closestSystem == null) {
            desiredNumFleets = 0f;
        } else {
            desiredNumFleets = 1f - Math.min(1f, Misc.getDistanceToPlayerLY(closestSystem.getLocation()) / MAX_LY_FROM_CROWN);
        }

        return (int) Math.round(desiredNumFleets * MAX_PIRATE_FLEETS);
    }


    protected StarSystemAPI getCrowConstellation() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) {
            return null;
        }
        if (currSpawnLoc == null) return null;

        StarSystemAPI closest = null;
        float minDistance = 100000f;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.hasTag("CROWN_CONSTELLATION_SYSTEM")) {
                continue;
            }
            float distance = Misc.getDistanceToPlayerLY(system.getLocation());

            if (distance < minDistance) {
                closest = system;
                minDistance = distance;
            }
        }

        if (closest == null && currSpawnLoc != null) {
            float distToPlayerLY = Misc.getDistanceLY(player.getLocationInHyperspace(), currSpawnLoc.getLocation());
            if (distToPlayerLY <= MAX_LY_FROM_CROWN_DESPAWN) {
                closest = currSpawnLoc;
            }
        }

        return closest;
    }


    @Override
    protected float getExpireDaysPerFleet() {
        /* Bigger fleets, slower wind-down */
        return 20f;
    }

    @Override
    protected CampaignFleetAPI spawnFleetImpl() {

        StarSystemAPI system = currSpawnLoc;
        if (system == null) return null;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return null;

        if (!PIRATE_Active()) {
            return null;
        }

//		float distToPlayerLY = Misc.getDistanceLY(player.getLocationInHyperspace(), system.getLocation());
//		if (distToPlayerLY > 1f) return null;


        float timeFactor = (PirateBaseManager.getInstance().getDaysSinceStart() - 180f) / (365f * 2f);
        if (timeFactor < 0) timeFactor = 0;
        if (timeFactor > 1) timeFactor = 1;

        float earlyTimeFactor = (PirateBaseManager.getInstance().getDaysSinceStart() - 60f) / 120f;
        if (earlyTimeFactor < 0) earlyTimeFactor = 0;
        if (earlyTimeFactor > 1) earlyTimeFactor = 1;

        //timeFactor = 1f;

        float r = (float) Math.random();
        //r = (float) Math.sqrt(r);

        //float fp = 15f + 30f * (float) Math.random() + bonus * 15f * r * timeFactor;

        float fp = (10f) * earlyTimeFactor +
                (5f) * (0.5f + 0.5f * (float) Math.random()) +
                50f * (0.5f + 0.5f * r) * timeFactor;

        // larger fleets if more fleets
        float desired = getDesiredNumFleetsForSpawnLocation();
        if (desired > 2) {
            fp += ((desired - 2) * (0.5f + (float) Math.random() * 0.5f)) * 2f * timeFactor;
        }

        if (fp < 10) fp = 10;

        FleetFactory.MercType type;
        if (fp < 25) {
            type = FleetFactory.MercType.SCOUT;
        } else if (fp < 75) {
            type = FleetFactory.MercType.PRIVATEER;
        } else if (fp < 125) {
            type = FleetFactory.MercType.PATROL;
        } else {
            type = FleetFactory.MercType.ARMADA;
        }

        String fleetType = type.fleetType;

        float combat = fp;
        float tanker = 0f;

        if (type == FleetFactory.MercType.PATROL || type == FleetFactory.MercType.ARMADA) {
            tanker = combat * 0.1f;
        }

        combat = Math.round(combat);
        tanker = Math.round(tanker);

        FleetParamsV3 params = new FleetParamsV3(
                null,
                system.getLocation(), // location
                Factions.PIRATES,
                null, // quality override
                fleetType,
                combat, // combatPts
                0f, // freighterPts
                tanker, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod
        );
        params.ignoreMarketFleetSizeMult = true;
        if (timeFactor <= 0) {
            params.maxShipSize = 1;
        }
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_NO_MILITARY_RESPONSE, true);

        setLocationAndOrders(fleet, 1f, 1f);

        return fleet;
    }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

}
