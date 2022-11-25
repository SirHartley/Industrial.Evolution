package industrial_evolution.industries.derelicts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class IndEvo_PrintDefectsMed extends BaseHullMod {

    //Minor: 10% slower repairs, small chance of in-combat malfunctions
    //Med: Block Upkeep reduction hullmods, +5% upkeep, 15% slower repairs, med chance of in-combat malfunctions
    //High: Block Upkeep reduction hullmods, reduce weapon turn rate, 20% slower repairs, +10% upkeep, high chance of in-combat malfunctions

    private static final float MALFUNCTION_PROB = 0.03f;
    public static final float REPAIR_MALUS = -10f;

    public static final float MAINTENANCE_MULT = 1.1f;

    public static final float REPAIR_RATE_BONUS = -10f;
    public static final float CR_RECOVERY_BONUS = -10f;

    public static final float DEGRADE_INCREASE_PERCENT = 20f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {

        //Malfunctions:
        float effect = stats.getDynamic().getValue(Stats.DMOD_EFFECT_MULT);

        stats.getCriticalMalfunctionChance().modifyFlat(id, MALFUNCTION_PROB * effect / 5);
        stats.getWeaponMalfunctionChance().modifyFlat(id, MALFUNCTION_PROB * effect);
        stats.getEngineMalfunctionChance().modifyFlat(id, MALFUNCTION_PROB * effect);

        //repair
        stats.getCombatEngineRepairTimeMult().modifyMult(id, 1f - REPAIR_MALUS * 0.01f);
        stats.getCombatWeaponRepairTimeMult().modifyMult(id, 1f - REPAIR_MALUS * 0.01f);

        //upkeep
        stats.getMinCrewMod().modifyMult(id, MAINTENANCE_MULT);
        stats.getSuppliesPerMonth().modifyMult(id, MAINTENANCE_MULT);
        stats.getFuelUseMod().modifyMult(id, MAINTENANCE_MULT);

        stats.getBaseCRRecoveryRatePercentPerDay().modifyPercent(id, CR_RECOVERY_BONUS);
        stats.getRepairRatePercentPerDay().modifyPercent(id, REPAIR_RATE_BONUS);

        //CR deg after PPT
        stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);

    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + (int) -REPAIR_MALUS + "%";
        if (index == 1) return "" + Math.round((MAINTENANCE_MULT - 1) * 100f) + "%";
        if (index == 2) return "" + (int) DEGRADE_INCREASE_PERCENT + "%";
        if (index == 3) return "" + "malfunctions";
        return null;
    }
}
