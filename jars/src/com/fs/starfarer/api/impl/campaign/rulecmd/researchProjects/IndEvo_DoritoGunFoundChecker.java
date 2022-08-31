package com.fs.starfarer.api.impl.campaign.rulecmd.researchProjects;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

public class IndEvo_DoritoGunFoundChecker implements ShowLootListener, EconomyTickListener {

    private static final String HAS_FOUND_GUN_KEY = "$IndEvo_HasFoundDoritoGun";

    public static boolean isGunFound() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(HAS_FOUND_GUN_KEY);
    }

    private void setGunFound() {
        Global.getSector().getMemoryWithoutUpdate().set(HAS_FOUND_GUN_KEY, true);

        IndEvo_GalatiaNewProjectsIntel intel = new IndEvo_GalatiaNewProjectsIntel();
        Global.getSector().getIntelManager().addIntel(intel);
    }

    @Override
    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
        if (isGunFound()) return;

        for (CargoAPI.CargoItemQuantity<String> qt : loot.getWeapons()) {
            if (Global.getSettings().getWeaponSpec(qt.getItem()).getTags().contains(Tags.OMEGA)) {
                setGunFound();
                break;
            }
        }
    }

    @Override
    public void reportEconomyTick(int iterIndex) {

    }

    @Override
    public void reportEconomyMonthEnd() {
        if(isGunFound()) return;

        for (CargoAPI.CargoItemQuantity<String> qt : Global.getSector().getPlayerFleet().getCargo().getWeapons()) {
            if (Global.getSettings().getWeaponSpec(qt.getItem()).getTags().contains(Tags.OMEGA)) {
                setGunFound();
                break;
            }
        }

        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()){
            ShipVariantAPI var = m.getVariant();
            for(String s : var.getFittedWeaponSlots()){
                if (var.getWeaponSpec(s).getTags().contains(Tags.OMEGA)){
                    setGunFound();
                    break;
                }
            }
        }
    }
}
