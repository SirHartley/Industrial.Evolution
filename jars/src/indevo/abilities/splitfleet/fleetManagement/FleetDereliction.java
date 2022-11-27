package indevo.abilities.splitfleet.fleetManagement;

import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CargoPodsEntityPlugin;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.CargoPods;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import indevo.abilities.splitfleet.salvageSpecials.OfficerAndShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

public class FleetDereliction {

    protected void addDerelict(SectorEntityToken focus,
                               StarSystemAPI system,
                               FleetMemberAPI member,
                               OfficerAndShipRecoverySpecial.ShipCondition condition,
                               CargoAPI extraCargo,
                               float orbitRadius,
                               float angle) {

        //just to spawn the wreck, we got our own impl for salvage interaction
        DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(member.getVariant().getHullVariantId(), ShipRecoverySpecial.ShipCondition.AVERAGE), true);
        SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, Factions.NEUTRAL, params);
        ship.setDiscoverable(true);

        float orbitDays = orbitRadius * MathUtils.getRandomNumberInRange(5f, 50f);
        ship.setCircularOrbit(focus, (float) MathUtils.getRandomNumberInRange(-10, 10) + angle, orbitRadius, orbitDays);

        OfficerAndShipRecoverySpecial.OfficerAndShipRecoverySpecialData specialData = new OfficerAndShipRecoverySpecial.OfficerAndShipRecoverySpecialData(member.getCaptain(), member, condition);
        if (extraCargo != null && !extraCargo.isEmpty()) BaseSalvageSpecial.addExtraSalvage(ship, extraCargo);
        Misc.setSalvageSpecial(ship, specialData);
    }

    public static void leaveCargoInPods(CampaignFleetAPI fleet, CargoAPI cargo) {
        CustomCampaignEntityAPI pods = Misc.addCargoPods(fleet.getContainingLocation(), fleet.getLocation());
        CargoPodsEntityPlugin plugin = (CargoPodsEntityPlugin) pods.getCustomPlugin();
        plugin.setExtraDays(364 * 5);
        pods.getCargo().addAll(cargo);
        CargoPods.stabilizeOrbit(pods, false);
    }

}
