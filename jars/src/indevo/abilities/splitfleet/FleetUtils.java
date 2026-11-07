package indevo.abilities.splitfleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.fleetAssignmentAIs.DetachmentAI;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.CombatAndDerelictionScript;
import indevo.abilities.splitfleet.intel.DetachmentIntel;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMarines;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.industries.courierport.ShippingTargetHelper;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class FleetUtils {
    public static final Logger log = Global.getLogger(FleetUtils.class);

    public static final String DETACHMENT_IDENTIFIER_KEY = "$splinterFleetIdentifier";
    public static final float MIN_MERGE_DISTANCE_IN_MENU = 500f;
    public static final String USE_ABILITY_MEM_KEY = "$SplinterFleet_Use_ability";

    //shared side quest lockout with catch.release: their quests skip a fleet that has it
    public static final String SIDE_QUEST_CLAIM_KEY = "$sirhartley_sideQuest";
    public static final String SIDE_QUEST_CLAIM_OWNER = "IndEvo";

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

        //runcode for(int i = 0; i < 5; i++){indevo.abilities.splitfleet.FleetUtils.mergeDetachment(i);}
    }

    public static void mergeFleetWithPlayerFleet(CampaignFleetAPI detachment) {
        ModPlugin.log("Merging " + detachment.getName() + " with player fleet");
        Global.getSector().getCampaignUI().addMessage("%s has returned to the main force", Misc.getTextColor(), detachment.getName(), "", Misc.getHighlightColor(), Misc.getTextColor());

        int num = DetachmentMemory.getNumForFleet(detachment);
        //a merge from MergeScript runs inside the fleet advance, which then reports a NO_MEMBERS despawn
        detachment.getMemoryWithoutUpdate().set(CombatAndDerelictionScript.ENDED_KEY, true);
        moveContentsToPlayerFleet(detachment);
        DetachmentIntel.end(detachment, "Returned to the main force.");

        Global.getSector().reportFleetDespawned(detachment, CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION, Global.getSector().getPlayerFleet());
        detachment.getContainingLocation().removeEntity(detachment);

        DetachmentMemory.removeDetachment(num);
    }

    public static void moveContentsToPlayerFleet(CampaignFleetAPI detachment) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        FleetDataAPI playerFleetData = playerFleet.getFleetData();
        FleetDataAPI detachmentFleetData = detachment.getFleetData();

        //add all the members and move officers
        for (FleetMemberAPI m : detachmentFleetData.getMembersListCopy()) {
            //officer handling
            PersonAPI officerPerson = m.getCaptain();
            if (officerPerson != null && !officerPerson.isDefault()) detachmentFleetData.removeOfficer(officerPerson);

            m.getVariant().removeTag(Tags.TAG_NO_AUTOFIT);

            //fleet Member transaction
            detachmentFleetData.removeFleetMember(m);
            playerFleetData.addFleetMember(m);
            if (officerPerson != null && !officerPerson.isDefault() && !officerPerson.isAICore() && !officerPerson.isPlayer())
                playerFleetData.addOfficer(officerPerson);
        }

        CargoAPI cargo = detachment.getCargo();
        DetachmentMarines.moveToPlayer(detachment, playerFleet.getCargo().getMarines());
        playerFleet.getCargo().addAll(cargo);
        cargo.clear();
        DetachmentMarines.clear(detachment);
    }

    public static void storeInMarket(CampaignFleetAPI detachment, MarketAPI market) {
        SubmarketAPI storageSubmarket = MiscIE.getStorage(market);
        CargoAPI storage = storageSubmarket.getCargo();

        DetachmentMarines.moveToStorage(detachment, storageSubmarket);
        storage.addAll(detachment.getCargo());
        detachment.getCargo().clear();
        DetachmentMarines.clear(detachment);

        storage.initMothballedShips(Factions.PLAYER);
        for (FleetMemberAPI member : detachment.getFleetData().getMembersListCopy()) {
            detachment.getFleetData().removeFleetMember(member);
            member.getVariant().removeTag(Tags.TAG_NO_AUTOFIT);
            storage.getMothballedShips().addFleetMember(member);
        }
    }

    public static MarketAPI getClosestStorageMarket(SectorEntityToken from) {
        MarketAPI best = null;
        float bestDist = Float.MAX_VALUE;

        for (SectorEntityToken entity : ShippingTargetHelper.getValidOriginPlanets()) {
            MarketAPI market = entity.getMarket();
            if (market == null || MiscIE.getStorage(market) == null) continue;

            //markets the player can open storage at come first
            float dist = Misc.getDistanceLY(from.getLocationInHyperspace(), entity.getLocationInHyperspace());
            if (!Misc.playerHasStorageAccess(market)) dist += 100000f;

            if (dist < bestDist) {
                bestDist = dist;
                best = market;
            }
        }

        return best;
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

        List<FleetMemberAPI> fleetMemberlist = getDetachmentMembers(loadout, playerFleet);
        CargoAPI detachmentCargo = getDetachmentCargo(loadout, playerFleet);

        //add all the members and move officers
        for (FleetMemberAPI m : fleetMemberlist) {
            /*Bug: If a fresh-from-custom-production variant is added to a special task group, the variant in the fleet will revert to stock on game load

            To prevent this, the variant source needs to be marked as REFIT *and* its originalVariant needs to be set to null;
            the latter prevents reverting to stock variant on fleet deflation
            */

            m.getVariant().setSource(VariantSource.REFIT);
            m.getVariant().setOriginalVariant(null);
            m.getVariant().addTag(Tags.TAG_NO_AUTOFIT); //required or the game will purge player fits, removed on re-merge

            //since there is no way to transfer officers, remove them for transport detachments!
            if (Behaviour.behaviourEquals(loadout.behaviour, Behaviour.FleetBehaviour.TRANSPORT)) {
                if (!m.getCaptain().isAICore() && !Misc.isUnremovable(m.getCaptain())) {
                    m.setCaptain(null);
                }
            }

            //officer handling
            PersonAPI officerPerson = m.getCaptain();
            if (officerPerson != null && !officerPerson.isDefault() && !officerPerson.isPlayer())
                playerFleetData.removeOfficer(officerPerson);

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
        int playerMarines = playerFleet.getCargo().getMarines();
        DetachmentMarines.move(splinterFleet, 0, playerMarines, (int) Math.min(playerMarines, detachmentCargo.getMarines()));
        transferCargoBetweenCargos(playerFleet.getCargo(), splinterFleet.getCargo(), detachmentCargo);
        PlayerFleetPersonnelTracker.getInstance().update();

        //set memory stuff
        MemoryAPI splinterFleetMemory = splinterFleet.getMemoryWithoutUpdate();
        splinterFleetMemory.set(FleetUtils.DETACHMENT_IDENTIFIER_KEY, true);
        splinterFleetMemory.set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
        splinterFleetMemory.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        splinterFleetMemory.set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
        splinterFleetMemory.set(MemFlags.MEMORY_KEY_SKIP_TRANSPONDER_STATUS_INFO, true);
        splinterFleetMemory.set(SIDE_QUEST_CLAIM_KEY, SIDE_QUEST_CLAIM_OWNER);

        splinterFleet.setNoAutoDespawn(true);
        splinterFleet.setNoFactionInName(true);

        //and spawn it (we do this here as FLEET AI NEEDS SYSTEM TO INITIALIZE)
        Global.getSector().getCurrentLocation().spawnFleet(playerFleet, 20f, 20f, splinterFleet);

        //transportAssignment needs the detachment entry, register before assigning AI
        DetachmentMemory.addDetachment(splinterFleet, num);

        //then set behaviour
        Behaviour.changeBehaviourAndUpdateAI(splinterFleet, loadout.behaviour);
        splinterFleet.addScript(new CombatAndDerelictionScript(splinterFleet));
        DetachmentIntel.create(splinterFleet);

        log.info("fleet member ids in splinter");
        for (FleetMemberAPI f : splinterFleet.getFleetData().getMembersListCopy()) {
            log.info(f.getId());
        }
    }

    public static boolean isDetachmentFleet(CampaignFleetAPI fleet) {
        return fleet.getMemoryWithoutUpdate().getBoolean(DETACHMENT_IDENTIFIER_KEY);
    }

    //despawn only starts a fade, isAlive stays true until it ends
    public static boolean isEnded(CampaignFleetAPI fleet) {
        return !fleet.isAlive() || fleet.getMemoryWithoutUpdate().getBoolean(CombatAndDerelictionScript.ENDED_KEY);
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
                to.addItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), toAdd);
            } else transferCommodity(from, to, stack.getCommodityId(), stack.getSize());
        }
    }

    //the side the loadout lists: the detachment in send mode, the ships the player takes in take mode
    public static CampaignFleetAPI createFakeFleet(LoadoutMemory.Loadout loadout) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        List<FleetMemberAPI> members = loadout.takeMode ? getTakenMembers(loadout, playerFleet) : getFleetMembersForLoadout(loadout, playerFleet);
        return createFakeFleet(loadout.id, members, loadout.targetCargo);
    }

    public static boolean staysWithPlayer(FleetMemberAPI member) {
        return member.isFlagship()
                || member.getHullSpec().getTags().contains(Tags.SHIP_CAN_NOT_SCUTTLE)
                || member.getVariant().getTags().contains(Tags.SHIP_CAN_NOT_SCUTTLE);
    }

    public static List<FleetMemberAPI> getDetachmentMembers(LoadoutMemory.Loadout loadout, CampaignFleetAPI playerFleet) {
        List<FleetMemberAPI> picked = getFleetMembersForLoadout(loadout, playerFleet);
        if (!loadout.takeMode) return picked;

        List<FleetMemberAPI> left = new ArrayList<>();
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (!picked.contains(member) && !staysWithPlayer(member)) left.add(member);
        }

        return left;
    }

    public static List<FleetMemberAPI> getTakenMembers(LoadoutMemory.Loadout loadout, CampaignFleetAPI playerFleet) {
        List<FleetMemberAPI> taken = new ArrayList<>();
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (staysWithPlayer(member)) taken.add(member);
        }

        taken.addAll(getFleetMembersForLoadout(loadout, playerFleet));
        return taken;
    }

    //take mode leaves everything the player does not take, the loadout cargo can be more than the player still has
    public static CargoAPI getDetachmentCargo(LoadoutMemory.Loadout loadout, CampaignFleetAPI playerFleet) {
        if (!loadout.takeMode) return loadout.targetCargo;

        CargoAPI left = playerFleet.getCargo().createCopy();
        left.removeAll(loadout.targetCargo);
        return left;
    }

    public static List<FleetMemberAPI> getFleetMembersForLoadout(LoadoutMemory.Loadout loadout, CampaignFleetAPI sourceFleet) {
        List<FleetMemberAPI> matchingMembers = new ArrayList<>();

        for (ShipVariantAPI variant : loadout.shipVariantList) {
            for (FleetMemberAPI member : sourceFleet.getFleetData().getMembersListCopy()) {
                if (member.getVariant().getHullVariantId().equals(variant.getHullVariantId()) && !matchingMembers.contains(member) && !staysWithPlayer(member)) {
                    matchingMembers.add(member);
                    break;
                }
            }
        }

        return matchingMembers;
    }

    public static CampaignFleetAPI createFakeFleet(String name, List<FleetMemberAPI> memberList, CargoAPI cargo) {
        return createFakeFleet(name, memberList, cargo, false);
    }

    //fake fleet commander has no skills like a live detachment; player side gets the player's, as in vanilla's recovery dialog
    public static CampaignFleetAPI createFakeFleet(String name, List<FleetMemberAPI> memberList, CargoAPI cargo, boolean withPlayerSkills) {
        CampaignFleetAPI fakeFleet = Global.getFactory().createEmptyFleet(Global.getSector().getPlayerFaction().getId(), name, true);
        PersonAPI player = Global.getSector().getPlayerFleet().getCommander();

        for (FleetMemberAPI m : memberList) {
            FleetMemberAPI fake = createFakeFleetMember(m);
            fakeFleet.getFleetData().addFleetMember(fake);
            if (withPlayerSkills) fake.setFleetCommanderForStats(player, null);
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
        fakeMember.getRepairTracker().setMothballed(m.isMothballed());
        fakeMember.getRepairTracker().setCR(m.getRepairTracker().getBaseCR());
        fakeMember.getStatus().setHullFraction(m.getStatus().getHullFraction());

        PersonAPI actualCaptain = m.getCaptain();
        if (actualCaptain != null) {
            PersonAPI fakeCaptain = Global.getFactory().createPerson();
            fakeCaptain.setName(actualCaptain.getName());
            fakeCaptain.setPortraitSprite(actualCaptain.getPortraitSprite());
            fakeMember.setCaptain(fakeCaptain);
        }

        return fakeMember;
    }

    public static DetachmentAI getAssignmentAI(CampaignFleetAPI fleet) {
        for (EveryFrameScript s : fleet.getScripts()) {
            if (s instanceof DetachmentAI ai) return ai;
        }

        return null;
    }
}
