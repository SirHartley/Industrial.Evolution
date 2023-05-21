package indevo.other.legioNuclearOption;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

import static indevo.other.legioNuclearOption.NuclearOptionManager.*;

public class NuclearWeaponCombatListener implements FleetEventListener {

    public static void register() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        boolean register = true;
        for (FleetEventListener l : fleet.getEventListeners()) {
            if (l instanceof NuclearWeaponChecker) {
                register = false;
                break;
            }
        }

        if (register) fleet.addEventListener(new NuclearWeaponCombatListener());
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {

    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(NUCLEAR_WAR_MEMORY_KEY)) return;

        for (FleetMemberAPI m : fleet.getFleetData().getSnapshot()) {
            if (checkForIllegalWeapons(m)) {

                WarBreakoutDialogue.show();
                Global.getSector().getMemoryWithoutUpdate().set(NUCLEAR_WAR_MEMORY_KEY, true);
                addNuclearWeaponsToFactions();
                return;
            }
        }
    }

    public boolean checkForIllegalWeapons(FleetMemberAPI m) {
        ShipVariantAPI variant = m.getVariant();

        for (String slotId : variant.getFittedWeaponSlots()) {
            WeaponSlotAPI slot = variant.getSlot(slotId);
            if (slot.isBuiltIn() || slot.isDecorative() || slot.isSystemSlot() || slot.isStationModule()) continue;

            String weapondID = variant.getWeaponSpec(slotId).getWeaponId();

            if (NUCLEAR_WEAPONS.contains(weapondID)) return true;
        }

        return false;
    }
}
