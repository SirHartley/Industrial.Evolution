package indevo.other;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class SpawnDerelictFleetMembersOnDeathListener implements FleetEventListener {

    //call this from your mod plugin onSaveLoad()
    public static void attachToPlayerFleetIfNotPresent() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (!fleet.hasScriptOfClass(SpawnDerelictFleetMembersOnDeathListener.class))
            fleet.addEventListener(new SpawnDerelictFleetMembersOnDeathListener());
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (reason != CampaignEventListener.FleetDespawnReason.DESTROYED_BY_BATTLE) return;

        LocationAPI loc = fleet.getContainingLocation();
        Vector2f pos = fleet.getLocation();
        SectorEntityToken focus = loc.createToken(pos);

        List<FleetMemberAPI> membersLost = Misc.getSnapshotMembersLost(fleet);
        for (FleetMemberAPI member : membersLost) addDerelict(focus, member);
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }

    protected void addDerelict(SectorEntityToken focus,
                               FleetMemberAPI member) {

        DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(member.getVariant().getHullVariantId(), ShipRecoverySpecial.ShipCondition.WRECKED), false);
        SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(focus.getContainingLocation(), Entities.WRECK, Factions.NEUTRAL, params);
        ship.setDiscoverable(true);

        float orbitRadius = MathUtils.getRandomNumberInRange(5f, 100f);
        float orbitDays = orbitRadius * MathUtils.getRandomNumberInRange(0.7f, 1.3f);
        ship.setCircularOrbit(focus, (float) MathUtils.getRandomNumberInRange(0f, 360f), orbitRadius, orbitDays);
    }
}
