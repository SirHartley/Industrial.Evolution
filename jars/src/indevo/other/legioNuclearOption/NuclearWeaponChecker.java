package indevo.other.legioNuclearOption;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static indevo.other.legioNuclearOption.NuclearOptionManager.*;

public class NuclearWeaponChecker implements RefitTabListener {

    public static void register() {
        Global.getSector().getListenerManager().addListener(new NuclearWeaponChecker(), true);
        initPersistentDataIfNeeded();
    }

    @Override
    public void reportRefitOpened(CampaignFleetAPI fleet) {

    }

    @Override
    public void reportRefitClosed(CampaignFleetAPI fleet) {
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(NUCLEAR_WAR_MEMORY_KEY)) return;

        List<String> foundWeapons = new ArrayList<>();
        boolean showDialogue = false;

        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            ShipVariantAPI variant = m.getVariant();

            for (String slotId : variant.getFittedWeaponSlots()) {
                WeaponSlotAPI slot = variant.getSlot(slotId);
                if (slot.isBuiltIn() || slot.isDecorative() || slot.isSystemSlot() || slot.isStationModule()) continue;

                String weapondID = variant.getWeaponSpec(slotId).getWeaponId();
                if (NUCLEAR_WEAPONS.contains(weapondID)) foundWeapons.add(weapondID);
            }
        }

        for (String id : foundWeapons) {
            if (!weaponFoundPreviously(id)) {
                setWeaponFound(id);
                showDialogue = true;
            }
        }

        if (showDialogue) WarningDialogue.show();
    }

    public boolean weaponFoundPreviously(String weaponID) {
        return ((List<String>) Global.getSector().getPersistentData().get(WEAPON_LIST_MEMORY_KEY)).contains(weaponID);
    }

    public void setWeaponFound(String weaponID) {
        Map<String, Object> mem = Global.getSector().getPersistentData();
        List<String> data = (List<String>) mem.get(WEAPON_LIST_MEMORY_KEY);
        if (!data.contains(weaponID)) data.add(weaponID);

        mem.put(WEAPON_LIST_MEMORY_KEY, data);
    }

    private static void initPersistentDataIfNeeded() {
        Map<String, Object> mem = Global.getSector().getPersistentData();
        if (!mem.containsKey(WEAPON_LIST_MEMORY_KEY)) mem.put(WEAPON_LIST_MEMORY_KEY, new ArrayList<String>());
    }
}
