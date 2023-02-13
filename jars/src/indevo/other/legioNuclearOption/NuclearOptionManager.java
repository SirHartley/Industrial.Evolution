package indevo.other.legioNuclearOption;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

import java.util.*;

public class NuclearOptionManager {

    protected static final String WEAPON_LIST_MEMORY_KEY = "$tahlan_foundWeapons";
    protected static final String NUCLEAR_WAR_MEMORY_KEY = "$tahlan_isNuclear";
    protected static final List<String> NUCLEAR_WEAPONS = new ArrayList<String>(Arrays.asList("tachyonlance"));
    protected static final Map<String, List<String>> NUCLEAR_WEAPON_FACTION_RESPONSE_LIST = new HashMap<String, List<String>>(){{
        put("pirates", new ArrayList<>(Collections.singleton("tachyonlance")));
    }};

    public static void init(){
        RefitUIOpenChecker.register();
        NuclearWeaponChecker.register();
        NuclearWeaponCombatListener.register();
    }

    public static void addNuclearWeaponsToFactions(){
        for (Map.Entry<String, List<String>> factionEntry : NUCLEAR_WEAPON_FACTION_RESPONSE_LIST.entrySet()){
            FactionAPI faction = Global.getSector().getFaction(factionEntry.getKey());

            for (String s : factionEntry.getValue()){
                faction.addKnownWeapon(s, false);
            }
        }
    }

    public static List<String> getFittedIllegalWeaponIds() {
        List<String> weapons = new ArrayList<>();

        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            ShipVariantAPI variant = m.getVariant();

            for (String slotId : variant.getFittedWeaponSlots()) {
                WeaponSlotAPI slot = variant.getSlot(slotId);

                if (slot.isBuiltIn() || slot.isDecorative() || slot.isSystemSlot() || slot.isStationModule()) continue;

                String weapondID = variant.getWeaponSpec(slotId).getWeaponId();
                if (NUCLEAR_WEAPONS.contains(weapondID)) weapons.add(weapondID);
            }
        }

        return weapons;
    }
}
