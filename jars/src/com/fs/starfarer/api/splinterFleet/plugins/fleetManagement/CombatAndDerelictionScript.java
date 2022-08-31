package com.fs.starfarer.api.splinterFleet.plugins.fleetManagement;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CargoPodsEntityPlugin;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.CargoPods;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.splinterFleet.plugins.OrbitFocus;
import com.fs.starfarer.api.splinterFleet.plugins.intel.RecoverableShipIntel;
import com.fs.starfarer.api.splinterFleet.plugins.salvageSpecials.OfficerAndShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages Combat, drops, what happens when the fleet runs out of supplies ect
 */
public class CombatAndDerelictionScript implements EveryFrameScript, FleetEventListener {

    public static final Logger log = Global.getLogger(CombatAndDerelictionScript.class);
    public static final String ORBIT_FOCUS_ORBITING_TOKEN_LIST = "$IndEvo_derOrbitList";
    public static final String SHIP_VARIANT_KEY = "$IndEvo_derTokenVarKey";

    float days = 0;
    float lastDay = 0;

    private CampaignFleetAPI fleet;

    public CombatAndDerelictionScript(CampaignFleetAPI fleet) {
        this.fleet = fleet;

        fleet.addEventListener(this);
    }

    @Override
    public boolean isDone() {
        return !fleet.isAlive();
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if(!Global.getSettings().isDevMode()) return;

        days += Global.getSector().getClock().convertToDays(amount);

        if ((float) Math.floor(days) > lastDay) {
            lastDay = (float) Math.floor(days);

            log.info("Is AI mode: " + fleet.isAIMode());
            log.info("Supply status: " + fleet.getCargo().getCommodityQuantity(Commodities.SUPPLIES));

            for (CargoStackAPI stack : fleet.getCargo().getStacksCopy()) {
                log.info(stack.getDisplayName() + " " + stack.getSize());
            }
        }
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {

        if (reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION) {
            if (((SectorEntityToken) param).isPlayerFleet()) {
                Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has rejoined the Main Force");
            } else if (param instanceof PlanetAPI) {
                Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has reached %s in %s, delivered the cargo and docked.", Misc.getTextColor(), ((PlanetAPI) param).getName(), ((PlanetAPI) param).getContainingLocation().getName(), ((PlanetAPI) param).getFaction().getColor(), Misc.getHighlightColor());
            }

        } else if (reason == CampaignEventListener.FleetDespawnReason.DESTROYED_BY_BATTLE) {
            Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has been destroyed in battle.", Misc.getNegativeHighlightColor());
        }

        DetachmentMemory.removeDetachment(fleet);
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!fleet.getId().equals(this.fleet.getId())) return;

        Global.getSector().getCampaignUI().addMessage("Detachment combat concluded.");

        List<FleetMemberAPI> membersLost = Misc.getSnapshotMembersLost(fleet);

        if (membersLost.size() > 0) {
            //cargo logging

            SectorEntityToken focus = OrbitFocus.getOrbitFocusAtTokenPosition(fleet);
            focus.getMemoryWithoutUpdate().set(ORBIT_FOCUS_ORBITING_TOKEN_LIST, new ArrayList<>());

            CargoAPI fleetCargo = fleet.getCargo();
            fleetCargo.removeEmptyStacks();
            CargoAPI excessCargo = Global.getFactory().createCargo(true);

            //dropping behaviour

            float totalAvailableCargoSpace = 0f;
            for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                totalAvailableCargoSpace += m.getCargoCapacity();
            }

            float overloadedByAmount = fleetCargo.getSpaceUsed() - totalAvailableCargoSpace;

            if (overloadedByAmount > 0) {
                //overloaded means we have to move some stuff to the new detachment

                //get stack% of original cargo
                //use that as weight for picker and as max amt for removal

                List<CargoStackAPI> stacks = fleet.getCargo().getStacksCopy(); // TODO: 16.03.2022 it's possible that we have to use the snapshot cargo here if the fleet adjusts cargo after combat
                Map<CargoStackAPI, Integer> stackMaxUnitsForAnteilList = new HashMap<>();
                WeightedRandomPicker picker = new WeightedRandomPicker();

                for (CargoStackAPI stack : stacks) {
                    float anteil = stack.getCargoSpace() / fleetCargo.getSpaceUsed();
                    float spacePerUnit = stack.getCargoSpacePerUnit();
                    int unitsForAnteil = (int) Math.ceil((stack.getCargoSpace() * anteil) / spacePerUnit);

                    stackMaxUnitsForAnteilList.put(stack, unitsForAnteil);
                    picker.add(stack, anteil);
                }

                int safetyCounter = 0;
                while (overloadedByAmount > 0 || safetyCounter > stacks.size()) {
                    //pick stacks according to their weight and move their max amount to drop pool, then update the overload amt
                    if(picker.isEmpty()) break;

                    CargoStackAPI pickedStack = (CargoStackAPI) picker.pickAndRemove();
                    if(pickedStack == null || pickedStack.isNull()) continue;

                    int maxAmt = stackMaxUnitsForAnteilList.get(pickedStack);
                    float space = pickedStack.getCargoSpacePerUnit() * maxAmt;
                    float unitSpace = pickedStack.getCargoSpacePerUnit();

                    //if stack is samller than overload, drop everything, else, drop the diff
                    int amtToRemove = space <= overloadedByAmount ? maxAmt : (int) Math.ceil((space - overloadedByAmount) / unitSpace);

                    excessCargo.addItems(pickedStack.getType(), pickedStack.getData(), amtToRemove);
                    fleetCargo.removeItems(pickedStack.getType(), pickedStack.getData(), amtToRemove);
                    overloadedByAmount -= amtToRemove * unitSpace;

                    safetyCounter++;
                }

                log.info("cargo to distribute to fleet members:");
                for (CargoStackAPI stack : excessCargo.getStacksCopy())
                    log.info(stack.getDisplayName() + " " + stack.getSize());

                //distribute said cargo to the dead members, I am lazy, so just fill em up until nothing is left

                List<FleetMemberAPI> lostMembers = Misc.getSnapshotMembersLost(fleet);
                if (lostMembers.size() == 1) {
                    addDerelict(focus, lostMembers.get(0), OfficerAndShipRecoverySpecial.ShipCondition.AVERAGE, excessCargo);
                } else for (FleetMemberAPI member : lostMembers) {
                    CargoAPI extraCargo = Global.getFactory().createCargo(true);
                    float remainingCargoCapacity = member.getCargoCapacity();

                    if (!excessCargo.isEmpty()) {
                        safetyCounter = 0;
                        while (remainingCargoCapacity >= 1
                                && !excessCargo.isEmpty()
                                && safetyCounter < excessCargo.getStacksCopy().size()) {

                            for (CargoStackAPI stack : excessCargo.getStacksCopy()) {
                                float space = stack.getCargoSpace();
                                float unitSpace = stack.getCargoSpacePerUnit();

                                int amtToRemove = space <= remainingCargoCapacity ? (int) Math.ceil(stack.getSize()) : (int) Math.ceil((space - remainingCargoCapacity) / unitSpace);
                                extraCargo.addItems(stack.getType(), stack.getData(), amtToRemove);
                                excessCargo.removeItems(stack.getType(), stack.getData(), amtToRemove);
                                remainingCargoCapacity -= amtToRemove * unitSpace;
                            }
                        }
                    }

                    addDerelict(focus, member, OfficerAndShipRecoverySpecial.ShipCondition.AVERAGE, extraCargo);
                }
            }

            Global.getSector().getIntelManager().addIntel(new RecoverableShipIntel(focus));
        }
    }

    protected void addDerelict(SectorEntityToken focus,
                               FleetMemberAPI member,
                               OfficerAndShipRecoverySpecial.ShipCondition condition,
                               CargoAPI extraCargo) {

        //just to spawn the wreck, we got our own impl for salvage interaction
        DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(member.getVariant().getHullVariantId(), ShipRecoverySpecial.ShipCondition.AVERAGE), true);
        SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(focus.getContainingLocation(), Entities.WRECK, Factions.NEUTRAL, params);
        ship.setDiscoverable(true);

        ship.getMemoryWithoutUpdate().set(SHIP_VARIANT_KEY, member.getVariant());
        ((List< SectorEntityToken >) focus.getMemoryWithoutUpdate().get(ORBIT_FOCUS_ORBITING_TOKEN_LIST)).add(ship);

        float orbitRadius = MathUtils.getRandomNumberInRange(5f, 30f);
        float orbitDays = orbitRadius * MathUtils.getRandomNumberInRange(0.7f, 1.3f);
        ship.setCircularOrbit(focus, (float) MathUtils.getRandomNumberInRange(0f, 360f), orbitRadius, orbitDays);

        PersonAPI captain = (member.getCaptain() != null && !member.getCaptain().isDefault() && !member.getCaptain().isAICore()) ? member.getCaptain() : null;
        OfficerAndShipRecoverySpecial.OfficerAndShipRecoverySpecialData specialData = new OfficerAndShipRecoverySpecial.OfficerAndShipRecoverySpecialData(captain, member, condition);
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
