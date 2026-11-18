package indevo.industries.petshop.hullmods;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.patches.PetRefitRowPatch;
import indevo.industries.petshop.plugins.PetCard;
import patchlib.api.PatchLib;

/**
 * Use indevo.other.YourMod_PetName_PetHullmod for a ready-made copy and paste solution for custom pet hullmods that won't crash unlike this one.
 */
public class PetHullmod extends SelfRepairingBuiltInHullmod {
    public static final float BASE_DECREASE = 5f;
    public static final float DMOD_CAP = 90f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        PetStatusManager manager = PetStatusManager.getInstance();
        Pet pet = manager.getPet(stats.getVariant());
        if (pet == null) return;

        String source = pet.name + " the " + pet.getData().species;
        if (pet.getMascotMult() > 0f) stats.getCRLossPerSecondPercent().modifyPercent(id, -BASE_DECREASE * pet.getMascotMult(), source);

        CampaignFleetAPI fleet = pet.isAboard() ? pet.assignedFleetMember.getFleetData().getFleet() : null;
        for (String talent : pet.getActiveTalents()) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(talent);
            if (trait.isFleet() && (fleet == null || manager.getLead(fleet, talent) != pet)) continue;

            float value = pet.getTalentValue(talent);
            if (value > 0f) applyStat(stats, trait.stat(), value, id, source);
        }
    }

    // one case per stat key of pet_traits.csv, value in percent or points; false for a key it does not know
    public static boolean applyStat(MutableShipStatsAPI stats, String stat, float value, String id, String source) {
        float less = 1f - value * 0.01f;

        switch (stat) {
            case "flux_capacity" -> stats.getFluxCapacity().modifyPercent(id, value, source);
            case "flux_dissipation" -> stats.getFluxDissipation().modifyPercent(id, value, source);
            case "max_speed" -> stats.getMaxSpeed().modifyPercent(id, value, source);
            case "maneuverability" -> {
                stats.getAcceleration().modifyPercent(id, value, source);
                stats.getDeceleration().modifyPercent(id, value, source);
                stats.getTurnAcceleration().modifyPercent(id, value, source);
                stats.getMaxTurnRate().modifyPercent(id, value, source);
            }
            case "armor" -> stats.getArmorBonus().modifyPercent(id, value, source);
            case "hull" -> stats.getHullBonus().modifyPercent(id, value, source);
            case "shield_damage" -> stats.getShieldDamageTakenMult().modifyMult(id, less, source);
            case "weapon_range" -> {
                stats.getBallisticWeaponRangeBonus().modifyPercent(id, value, source);
                stats.getEnergyWeaponRangeBonus().modifyPercent(id, value, source);
            }
            case "rate_of_fire" -> {
                stats.getBallisticRoFMult().modifyPercent(id, value, source);
                stats.getEnergyRoFMult().modifyPercent(id, value, source);
                stats.getMissileRoFMult().modifyPercent(id, value, source);
            }
            case "ballistic_damage" -> stats.getBallisticWeaponDamageMult().modifyPercent(id, value, source);
            case "energy_damage" -> stats.getEnergyWeaponDamageMult().modifyPercent(id, value, source);
            case "missile" -> {
                stats.getMissileWeaponDamageMult().modifyPercent(id, value, source);
                stats.getMissileAmmoBonus().modifyPercent(id, value, source);
            }
            case "fighter_refit" -> stats.getFighterRefitTimeMult().modifyMult(id, less, source);
            case "peak_time" -> stats.getPeakCRDuration().modifyPercent(id, value, source);
            case "cr_recovery" -> {
                stats.getBaseCRRecoveryRatePercentPerDay().modifyPercent(id, value, source);
                stats.getRepairRatePercentPerDay().modifyPercent(id, value, source);
            }
            case "max_cr" -> stats.getMaxCombatReadiness().modifyFlat(id, value * 0.01f, source);
            case "supplies" -> stats.getSuppliesPerMonth().modifyMult(id, less, source);
            case "sensor_profile" -> stats.getSensorProfile().modifyMult(id, less, source);
            case "recovery" -> {
                stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, value * 0.01f, source);
                stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD).modifyMult(id, 1f - Math.min(DMOD_CAP, value) * 0.01f, source);
            }
            // the fleet sums these over its ships (Misc.getFleetwideTotalMod, RepairGantry), so only the lead pet's ship applies one
            case "fleet_burn" -> stats.getDynamic().getMod(Stats.FLEET_BURN_BONUS).modifyFlat(id, value, source);
            case "salvage" -> stats.getDynamic().getMod(Stats.SALVAGE_VALUE_MULT_MOD).modifyFlat(id, value * 0.01f, source);
            case "ground_support" -> stats.getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).modifyFlat(id, value, source);
            default -> {
                return false;
            }
        }

        return true;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);

        Pet pet = ship != null ? PetStatusManager.getInstance().getPet(ship.getVariant()) : null;
        // the refit row is the only place with a click target under this tooltip
        if (pet != null) PetCard.hover(tooltip, pet, true, !isForModSpec && PatchLib.isPatchEnabled(PetRefitRowPatch.class));
    }
}
