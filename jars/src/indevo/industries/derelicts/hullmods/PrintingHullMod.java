package indevo.industries.derelicts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import indevo.industries.derelicts.industry.HullForge;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class PrintingHullMod extends BaseHullMod {

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        super.applyEffectsBeforeShipCreation(hullSize, stats, id);

        int compensate = 0;

        if (stats.getVariant().getTags().contains(HullForge.COMPENSATE_1)) compensate = 1;
        if (stats.getVariant().getTags().contains(HullForge.COMPENSATE_2)) compensate = 2;
        if (stats.getVariant().getTags().contains(HullForge.COMPENSATE_3)) compensate = 3;

        if (compensate > 0)
            stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).modifyFlat(id, compensate, "Printed Ship S-Mod Compensation");

        //stats.getDynamic().getMod(Stats.MAX_LOGISTICS_HULLMODS_MOD).modifyFlat("HullDeconstructor", 1);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);
        MutableShipStatsAPI stats = ship.getMutableStats();

        int compensate = 0;

        if (stats.getVariant().getTags().contains(HullForge.COMPENSATE_1)) compensate = 1;
        if (stats.getVariant().getTags().contains(HullForge.COMPENSATE_2)) compensate = 2;
        if (stats.getVariant().getTags().contains(HullForge.COMPENSATE_3)) compensate = 3;

        if (compensate > 0) {
            tooltip.addPara("Increases the maximum built in Hullmods by " + compensate, 10f, Misc.getHighlightColor(), compensate + "");
        }
    }
}
