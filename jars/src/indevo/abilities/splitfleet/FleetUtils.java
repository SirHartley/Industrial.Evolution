package indevo.abilities.splitfleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import indevo.utils.ModPlugin;
import indevo.abilities.splitfleet.fleetAssignmentAIs.SplinterFleetAssignmentAIV2;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.CombatAndDerelictionScript;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class FleetUtils {
    public static final Logger log = Global.getLogger(FleetUtils.class);

    public static final String DETACHMENT_IDENTIFIER_KEY = "$splinterFleetIdentifier";
    public static final float MIN_MERGE_DISTANCE_IN_MENU = 500f;
    public static final String USE_ABILITY_MEM_KEY = "$SplinterFleet_Use_ability";

    public static void transferCommodity(CargoAPI from, CargoAPI to, String id, float amt) {
        float available = from.getCommodityQuantity(id);
        float toAdd = Math.min(available, amt);

        from.removeCommodity(id, toAdd);
        to.addCommodity(id, toAdd);
    }

    public static void mergeDetachment(int num) {
        if (!DetachmentMemory.isDetachmentActive(num)) return;

        CampaignFleetAPI detachment = DetachmentMemory.getDetachment(num);
        mergeFleetWithPlayerFleet(detachment);
    }

    public static void mergeFleetWithPlayerFleet(CampaignFleetAPI detachment) {
        ModPlugin.log("Merging " + detachment.getName() + " with player fleet");
        Global.getSector().getCampaignUI().addMessage("%s has returned to the main force", Misc.getTextColor(), detachment.getName(), "", Misc.getHighlightColor(), Misc.getTextColor());

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        int num = DetachmentMemory.getNumForFleet(detachment);

        FleetDataAPI playerFleetData = playerFleet.getFleetData();
        FleetDataAPI detachmentFleetData = detachment.getFleetData();

        //add all the members and move officers
        for (FleetMemberAPI m : detachmentFleetData.getMembersListCopy()) {
            //officer handling
            PersonAPI officerPerson = m.getCaptain();
            if (officerPerson != null && !officerPerson.isDefault()) detachmentFleetData.removeOfficer(officerPerson);

            //fleet Member transaction
            detachmentFleetData.removeFleetMember(m);
            playerFleetData.addFleetMember(m);
            if (officerPerson != null && !officerPerson.isDefault() && !officerPerson.isAICore() && !officerPerson.isPlayer()) playerFleetData.addOfficer(officerPerson);
        }

        playerFleet.getCargo().addAll(detachment.getCargo());

        Global.getSector().reportFleetDespawned(detachment, CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION, playerFleet); //also handles removal from detachment memory
        detachment.getContainingLocation().removeEntity(detachment);

        DetachmentMemory.removeDetachment(num);
    }

    public static void createAndSpawnFleet(LoadoutMemory.Loadout loadout, int num) {
        log.info("fleet member ids in player");
        for (FleetMemberAPI f : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            log.info(f.getId());
        }

        //AI mode is false, this leaves fuel and supply use enabled
        CampaignFleetAPI splinterFleet = Global.getFactory().createEmptyFleet(Global.getSector().getPlayerFaction().getId(), "Detachment " + num, false);
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        FleetDataAPI playerFleetData = playerFleet.getFleetData();
        FleetDataAPI splinterFleetData = splinterFleet.getFleetData();

        //get all the ships corresponding to the variants in the player fleet
        List<FleetMemberAPI> fleetMemberlist = new ArrayList<>();

        for (ShipVariantAPI variant : loadout.shipVariantList) {
            for (FleetMemberAPI member : playerFleetData.getMembersListCopy()) {
                if (member.getVariant().getHullVariantId().equals(variant.getHullVariantId()) && !fleetMemberlist.contains(member)) {
                    fleetMemberlist.add(member);
                    break;
                }
            }
        }

        //add all the members and move officers
        for (FleetMemberAPI m : fleetMemberlist) {
            //since there is no way to transfer officers, remove them for transport detachments!
            if (Behaviour.behaviourEquals(loadout.behaviour, Behaviour.FleetBehaviour.TRANSPORT)) {
                if(!m.getCaptain().isAICore() && !Misc.isUnremovable(m.getCaptain())) {
                    m.setCaptain(null);
                }
            }

            //officer handling
            PersonAPI officerPerson = m.getCaptain();
            if (officerPerson != null && !officerPerson.isDefault() && !officerPerson.isPlayer()) playerFleetData.removeOfficer(officerPerson);

            //fleet Member transaction
            playerFleetData.removeFleetMember(m);
            splinterFleetData.addFleetMember(m);
            //if (officerPerson != null && !officerPerson.isDefault() && !officerPerson.isPlayer()) m.setCaptain(officerPerson);
        }

        //add default abilities to fleet
        for (String id : Global.getSettings().getSortedAbilityIds()) {
            AbilitySpecAPI spec = Global.getSettings().getAbilitySpec(id);
            if (spec.isAIDefault()) splinterFleet.addAbility(id);
        }

        //transfer cargo
        transferCargoBetweenCargos(playerFleet.getCargo(), splinterFleet.getCargo(), loadout.targetCargo);

        //set memory stuff
        MemoryAPI splinterFleetMemory = splinterFleet.getMemoryWithoutUpdate();
        splinterFleetMemory.set(FleetUtils.DETACHMENT_IDENTIFIER_KEY, true);
        splinterFleetMemory.set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
        splinterFleetMemory.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        splinterFleetMemory.set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
        splinterFleetMemory.set(MemFlags.MEMORY_KEY_SKIP_TRANSPONDER_STATUS_INFO, true);

        splinterFleet.setNoAutoDespawn(true);
        splinterFleet.setNoFactionInName(true);

        //and spawn it (we do this here as FLEET AI NEEDS SYSTEM TO INITIALIZE)
        Global.getSector().getCurrentLocation().spawnFleet(playerFleet, 20f, 20f, splinterFleet);

        //transportAssignment needs the detachment entry, register before assigning AI
        DetachmentMemory.addDetachment(splinterFleet, num);

        //then set behaviour
        Behaviour.changeBehaviourAndUpdateAI(splinterFleet, loadout.behaviour);
        splinterFleet.addScript(new CombatAndDerelictionScript(splinterFleet));

        log.info("fleet member ids in splinter");
        for (FleetMemberAPI f : splinterFleet.getFleetData().getMembersListCopy()) {
            log.info(f.getId());
        }
    }

    public static boolean isDetachmentFleet(CampaignFleetAPI fleet) {
        return fleet.getMemoryWithoutUpdate().getBoolean(DETACHMENT_IDENTIFIER_KEY);
    }

    public static void transferCargoBetweenCargos(CargoAPI from, CargoAPI to, CargoAPI target) {
        for (CargoStackAPI stack : target.getStacksCopy()) {
            if (stack.isFighterWingStack()) {
                String id = stack.getFighterWingSpecIfWing().getId();

                float available = from.getQuantity(CargoAPI.CargoItemType.FIGHTER_CHIP, id);
                float toAdd = Math.min(available, stack.getSize());

                from.removeItems(CargoAPI.CargoItemType.FIGHTER_CHIP, id, toAdd);
                to.addItems(CargoAPI.CargoItemType.FIGHTER_CHIP, id, toAdd);
            } else if (stack.isWeaponStack()) {
                String id = stack.getWeaponSpecIfWeapon().getWeaponId();

                float available = from.getQuantity(CargoAPI.CargoItemType.WEAPONS, id);
                float toAdd = Math.min(available, stack.getSize());

                from.removeItems(CargoAPI.CargoItemType.WEAPONS, id, toAdd);
                to.addItems(CargoAPI.CargoItemType.WEAPONS, id, toAdd);
            } else if (stack.isSpecialStack()) {
                float available = from.getQuantity(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial());
                float toAdd = Math.min(available, stack.getSize());

                from.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), toAdd);
                to.addItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), toAdd);}
            else transferCommodity(from, to, stack.getCommodityId(), stack.getSize());
        }
    }

    public static CampaignFleetAPI createFakeFleet(LoadoutMemory.Loadout loadout) {
        CampaignFleetAPI fakeFleet = Global.getFactory().createEmptyFleet(Global.getSector().getPlayerFaction().getId(), loadout.id, true);

        for (ShipVariantAPI v : loadout.shipVariantList) {
            fakeFleet.getFleetData().addFleetMember(createFakeFleetMember(v));
        }

        fakeFleet.getCargo().addAll(loadout.targetCargo);

        return fakeFleet;
    }

    public static CampaignFleetAPI createFakeFleet(String name, List<FleetMemberAPI> memberList, CargoAPI cargo) {
        CampaignFleetAPI fakeFleet = Global.getFactory().createEmptyFleet(Global.getSector().getPlayerFaction().getId(), name, true);

        for (FleetMemberAPI m : memberList) {
            fakeFleet.getFleetData().addFleetMember(createFakeFleetMember(m));
        }

        fakeFleet.getCargo().addAll(cargo.createCopy());

        return fakeFleet;
    }

    public static FleetMemberAPI createFakeFleetMember(ShipVariantAPI v) {
        return Global.getFactory().createFleetMember(FleetMemberType.SHIP, v);
    }

    public static List<FleetMemberAPI> convertToFleetMemberList(List<ShipVariantAPI> varSet) {
        List<FleetMemberAPI> ships = new ArrayList<>();
        for (ShipVariantAPI variant : varSet) {
            ships.add(FleetUtils.createFakeFleetMember(variant));
        }

        return ships;
    }

    public static FleetMemberAPI createFakeFleetMember(FleetMemberAPI m) {
        FleetMemberAPI fakeMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, m.getVariant());
        fakeMember.setShipName(m.getShipName());

        PersonAPI actualCaptain = m.getCaptain();
        if (actualCaptain != null) {
            PersonAPI fakeCaptain = Global.getFactory().createPerson();
            fakeCaptain.setName(actualCaptain.getName());
            fakeCaptain.setPortraitSprite(actualCaptain.getPortraitSprite());
            fakeMember.setCaptain(fakeCaptain);
        }

        return fakeMember;
    }

    public static SplinterFleetAssignmentAIV2 getAssignmentAI(CampaignFleetAPI fleet) {
        for (EveryFrameScript s : fleet.getScripts()) {
            if (s instanceof SplinterFleetAssignmentAIV2) {
                return (SplinterFleetAssignmentAIV2) s;
            }
        }

        return null;
    }

    public static void setOrReplaceAssignmentAI(CampaignFleetAPI fleet, SplinterFleetAssignmentAIV2 ai) {
        SplinterFleetAssignmentAIV2 current = getAssignmentAI(fleet);

        if (current != null) {
            current.setDone();
            fleet.removeScript(current);
        }

        fleet.addScript(ai);
    }
}
