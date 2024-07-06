package indevo.dialogue.research.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import indevo.utils.helper.StringHelper;

public class FieldModulation extends BaseHullMod {
    private static final int BURN_LEVEL_BONUS = 1;
    private static final int UPKEEP_INCREASE_PERCENT = 10;
    private static final int SMOD_FUEL_USE_DECREASE_PERCENT = -10;
    public static final float SMOD_PROFILE_MULT = 0.6f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        boolean sMod = isSMod(stats);
        stats.getMaxBurnLevel().modifyFlat(id, BURN_LEVEL_BONUS);
        if (!sMod) {
            stats.getSuppliesPerMonth().modifyPercent(id, UPKEEP_INCREASE_PERCENT);
            stats.getFuelUseMod().modifyPercent(id, UPKEEP_INCREASE_PERCENT);
        } else {
            stats.getFuelUseMod().modifyPercent(id, SMOD_FUEL_USE_DECREASE_PERCENT);
            stats.getSensorProfile().modifyMult(id, SMOD_PROFILE_MULT);
        }
    }

    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "supply and fuel";
        if (index == 1) return SMOD_FUEL_USE_DECREASE_PERCENT + "%";
        if (index == 2) return StringHelper.getAbsPercentString(SMOD_PROFILE_MULT, true);
        return null;
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + BURN_LEVEL_BONUS;
        if (index == 1) return UPKEEP_INCREASE_PERCENT + "%";
        return null;
    }

}
