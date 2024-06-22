package indevo.industries.changeling.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.listener.MarineLossAmplifcationHullmodEffectListener;
import indevo.industries.petshop.hullmods.SelfRepairingBuiltInHullmod;

public class Hellpods extends SelfRepairingBuiltInHullmod {

    public static final String HULLMOD_ID = "IndEvo_hellpods";
    public static final float RAID_STRENGTH_BONUS_PERCENT = 5f;

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        super.applyEffectsBeforeShipCreation(hullSize, stats, id);
        stats.getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).modifyPercent(id, RAID_STRENGTH_BONUS_PERCENT);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);

        float opad = 10f;
        float spad = 3f;

        tooltip.addPara(
                "Increases Marine Raid Effectiveness by %s",
                opad,
                Misc.getPositiveHighlightColor(),
                Math.round(RAID_STRENGTH_BONUS_PERCENT) + "%");

        tooltip.addPara(
                "Increases Marine losses by %s and marine losses by %s",
                spad,
                Misc.getNegativeHighlightColor(),
                Math.round(MarineLossAmplifcationHullmodEffectListener.MARINE_LOSSES_MULT_PER_SHIP_PERCENT) + "%");
    }
}
