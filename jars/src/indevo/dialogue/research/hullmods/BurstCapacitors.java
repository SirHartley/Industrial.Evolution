package indevo.dialogue.research.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.StringHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BurstCapacitors extends BaseHullMod {
    //reduce burst beam refire depending on the amount of installed beams regardless of size
    //same for flux
    //reduce beam damage depending on amount of installed beams

    public static final float MAX_REFIRE_REDUCTION = 0.5f;
    public static final float MAX_DAMAGE_REDUCTION = 0.5f;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        super.applyEffectsAfterShipCreation(ship, id);

        List<WeaponAPI> beamWeapons = new ArrayList<>();
        for (WeaponAPI w : ship.getAllWeapons()) if (w.isBurstBeam()) beamWeapons.add(w);

        int amt = beamWeapons.size();
        float refireReduction = (float) Math.ceil(MAX_REFIRE_REDUCTION / amt);
        float damageReduction = (float) Math.ceil(MAX_DAMAGE_REDUCTION / amt);

        for (WeaponAPI beamWeapon : beamWeapons){
            beamWeapon.setRefireDelay(beamWeapon.getRefireDelay() * refireReduction);
            beamWeapon.getDamage().getStats().getBeamWeaponDamageMult().modifyMult(id, damageReduction);
            beamWeapon.getDamage().getStats().getBeamWeaponFluxCostMult().modifyMult(id, damageReduction);

            //todo may have to edit some other things too like emp damage
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);
        float opad = 10f;

        tooltip.addPara("Current effect:\n" +
                "Burst beam refire delay: %s\n" +
                "Burst beam flux cost to fire: %s\n" +
                "Burst beam damage: %s", opad); //todo stuff here

        tooltip.addPara("Weapons affected by this:", opad);

        //todo this is incomplete
        tooltip.beginTable(Global.getSector().getPlayerFaction(), 20f, "Ship", 270f, "Days remaining", 120f);

        int i = 0;
        int max = 10;

        List<WeaponAPI> beamWeapons = new ArrayList<>();
        for (WeaponAPI w : ship.getAllWeapons()) if (w.isBurstBeam()) beamWeapons.add(w);

        for (WeaponAPI w : beamWeapons) {
            tooltip.addRow(w.getDisplayName(), "§");
            i++;
            if (i == max) break;
        }

        //add the table to the tooltip
        tooltip.addTable("Add cruisers to storage to have them refit.",  - 10, opad);
    }
}
