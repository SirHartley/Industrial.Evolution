package indevo.industries.courierport.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.listeners.Shipment;

import java.util.Random;

public class CourierFleetCreator {

    public static void spawnFleet(Shipment container) {
        SectorEntityToken token = container.contract.getFromMarket().getPrimaryEntity();
        token.getContainingLocation().spawnFleet(token, 20f, 20f, container.fleet);
    }

    public static CampaignFleetAPI createFleet(ShippingContract contract, CargoAPI cargo) {

        float fuelAmt = 0f;
        float cargoAmt = 5f;
        float personnelAmt = 0f;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (!stack.isCommodityStack()) continue;

            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(stack.getCommodityId());

            if (spec.isMeta()) continue;

            if (spec.hasTag(Commodities.TAG_PERSONNEL)) {
                personnelAmt += stack.getSize();
            } else if (spec.getId().equals(Commodities.FUEL)) {
                fuelAmt += stack.getSize();
            } else {
                cargoAmt += stack.getSize();
            }
        }

        // Buffalo is 5 FP for 300 cargo, or 60 cargo/FP
        int freighter = (int) Math.max(Math.ceil(cargoAmt / 60f) * 2, 5);
        int combat = (int) Math.min(5 + freighter * 2, 30);
        int utility = freighter / 4;
        int total = freighter + combat + utility;

        int tanker = Math.round((total * 0.25f) + (fuelAmt / 60));
        int liner = Math.round(personnelAmt / 60);

        float totalSize = cargo.getSpaceUsed();
        cargo.initMothballedShips("player");
        for (FleetMemberAPI m : cargo.getMothballedShips().getMembersListCopy()) {
            totalSize += m.getDeployCost();
        }

        totalSize /= 500f;
        String type = totalSize <= 3 ? FleetTypes.TRADE_SMALL : FleetTypes.TRADE;

        FleetParamsV3 params = new FleetParamsV3(
                contract.getFromMarket(),
                null, // locInHyper
                contract.getFromMarket().getFactionId(),
                contract.getFromMarket().getShipQualityFactor(), // qualityOverride
                type,
                combat, // combatPts
                freighter, // freighterPts
                tanker, // tankerPts
                0f, // transportPts
                liner, // linerPts
                utility, // utilityPts
                0f //-0.5f // qualityBonus
        );

        params.random = new Random();
        params.ignoreMarketFleetSizeMult = true;    // only use doctrine size, not source source size
        params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setFaction("IndEvo_couriers");
        fleet.setNoFactionInName(true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
        ;
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.setNoAutoDespawn(true);

        fleet.setName("Courier Fleet");

        return fleet;
    }
}
