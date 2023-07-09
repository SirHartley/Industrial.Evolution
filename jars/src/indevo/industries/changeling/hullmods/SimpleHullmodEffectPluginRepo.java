package indevo.industries.changeling.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;

import java.util.HashMap;
import java.util.Map;

public class SimpleHullmodEffectPluginRepo {

    public static final String HANDMADE_HULLMOD_ID = "IndEvo_HandMade";

    public static Map<String, SimpleHullmodEffectPlugin> HULLMOD_EFFECTS = new HashMap<String, SimpleHullmodEffectPlugin>() {{
        put("weaponTurnRateBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponTurnRateBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("weaponHealthBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponHealthBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("sightRadiusMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSightRadiusMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldArcBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldArcBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("phaseCloakUpkeepCostBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getPhaseCloakUpkeepCostBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("phaseCloakActivationCostBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getPhaseCloakActivationCostBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("peakCRDuration", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getPeakCRDuration().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("overloadTimeMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getOverloadTimeMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileWeaponRangeBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileWeaponRangeBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileWeaponFluxCostMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileWeaponFluxCostMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileTurnAccelerationBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileTurnAccelerationBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileMaxTurnRateBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileMaxTurnRateBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileMaxSpeedBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileMaxSpeedBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileAmmoBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileAmmoBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileAccelerationBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileAccelerationBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("minCrewMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMinCrewMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("maxCrewMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxCrewMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("hullBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHullBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("hitStrengthBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHitStrengthBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("hangarSpaceMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHangarSpaceMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("fuelUseMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFuelUseMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("fuelMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFuelMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("engineHealthBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEngineHealthBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyWeaponRangeBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyWeaponRangeBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyWeaponFluxCostMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyWeaponFluxCostMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyAmmoBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyAmmoBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("effectiveArmorBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEffectiveArmorBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("cRPerDeploymentPercent", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCRPerDeploymentPercent().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("cRLossPerSecondPercent", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCRLossPerSecondPercent().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("cargoMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCargoMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("beamWeaponTurnRateBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamWeaponTurnRateBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("beamWeaponRangeBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamWeaponRangeBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("ballisticWeaponRangeBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticWeaponRangeBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("ballisticWeaponFluxCostMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticWeaponFluxCostMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("ballisticAmmoBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticAmmoBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("armorBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getArmorBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("zeroFluxSpeedBoost", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getZeroFluxSpeedBoost().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("zeroFluxMinimumFluxLevel", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getZeroFluxMinimumFluxLevel().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("weaponMalfunctionChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponMalfunctionChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("weaponDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("ventRateMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getVentRateMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("turnAcceleration", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getTurnAcceleration().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldUpkeepMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldUpkeepMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldUnfoldRateMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldUnfoldRateMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldTurnRateMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldTurnRateMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldMalfunctionFluxLevel", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldMalfunctionFluxLevel().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldMalfunctionChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldMalfunctionChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldAbsorptionMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldAbsorptionMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("recoilPerShotMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getRecoilPerShotMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("recoilDecayMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getRecoilDecayMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("projectileSpeedMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getProjectileSpeedMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("projectileShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getProjectileShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("projectileDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getProjectileDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileWeaponDamageMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileWeaponDamageMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileRoFMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileRoFMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileGuidance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileGuidance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("maxTurnRate", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxTurnRate().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("maxSpeed", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxSpeed().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("maxRecoilMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxRecoilMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("maxCombatReadiness", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxCombatReadiness().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("maxCombatHullRepairFraction", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxCombatHullRepairFraction().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("maxBurnLevel", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxBurnLevel().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("kineticShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getKineticShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("kineticDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getKineticDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("hullDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHullDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("hullCombatRepairRatePercentPerSecond", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHullCombatRepairRatePercentPerSecond().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("highExplosiveShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHighExplosiveShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("highExplosiveDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHighExplosiveDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("hardFluxDissipationFraction", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHardFluxDissipationFraction().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("fragmentationShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFragmentationShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("fragmentationDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFragmentationDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("fluxDissipation", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFluxDissipation().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("fluxCapacity", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFluxCapacity().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("fighterRefitTimeMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFighterRefitTimeMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("engineMalfunctionChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEngineMalfunctionChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("engineDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEngineDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyWeaponDamageMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyWeaponDamageMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyRoFMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyRoFMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("empDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEmpDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("eccmChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEccmChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("deceleration", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDeceleration().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToTargetWeaponsMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToTargetWeaponsMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToTargetShieldsMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToTargetShieldsMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToTargetEnginesMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToTargetEnginesMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("criticalMalfunctionChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCriticalMalfunctionChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("crewLossMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCrewLossMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("combatWeaponRepairTimeMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCombatWeaponRepairTimeMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("combatEngineRepairTimeMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCombatEngineRepairTimeMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("beamWeaponFluxCostMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamWeaponFluxCostMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("beamWeaponDamageMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamWeaponDamageMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("beamShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("beamDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("baseCRRecoveryRatePercentPerDay", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBaseCRRecoveryRatePercentPerDay().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("ballisticWeaponDamageMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticWeaponDamageMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("ballisticRoFMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticRoFMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("autofireAimAccuracy", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getAutofireAimAccuracy().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("armorDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getArmorDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("acceleration", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getAcceleration().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("weaponRangeThreshold", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponRangeThreshold().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("weaponRangeMultPastThreshold", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponRangeMultPastThreshold().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("timeMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getTimeMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("suppliesToRecover", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSuppliesToRecover().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("suppliesPerMonth", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSuppliesPerMonth().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("shieldSoftFluxConversion", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldSoftFluxConversion().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("sensorStrength", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSensorStrength().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("sensorProfile", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSensorProfile().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("repairRatePercentPerDay", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getRepairRatePercentPerDay().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("recoilPerShotMultSmallWeaponsOnly", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getRecoilPerShotMultSmallWeaponsOnly().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("numFighterBays", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getNumFighterBays().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("missileAmmoRegenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileAmmoRegenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("minArmorFraction", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMinArmorFraction().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("maxArmorDamageReduction", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxArmorDamageReduction().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("kineticArmorDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getKineticArmorDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyWeaponFluxBasedBonusDamageMinLevel", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyWeaponFluxBasedBonusDamageMinLevel().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyWeaponFluxBasedBonusDamageMagnitude", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyWeaponFluxBasedBonusDamageMagnitude().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyProjectileSpeedMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyProjectileSpeedMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("energyAmmoRegenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyAmmoRegenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToMissiles", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToMissiles().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToFrigates", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToFrigates().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToFighters", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToFighters().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToDestroyers", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToDestroyers().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToCruisers", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToCruisers().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("damageToCapital", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToCapital().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("breakProb", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBreakProb().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("ballisticProjectileSpeedMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticProjectileSpeedMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("ballisticAmmoRegenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticAmmoRegenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });
        put("allowZeroFluxAtAnyLevel", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getAllowZeroFluxAtAnyLevel().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }
        });


    }};
}
