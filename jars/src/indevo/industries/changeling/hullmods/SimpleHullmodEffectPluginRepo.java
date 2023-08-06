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

            @Override
            public String getName() {
                return "weapon turn rate";
            }
        });
        put("weaponHealthBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponHealthBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "weapon health";
            }
        });
        put("sightRadiusMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSightRadiusMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "sight radius";
            }
        });
        put("shieldArcBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldArcBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield arc";
            }
        });
        put("phaseCloakUpkeepCostBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getPhaseCloakUpkeepCostBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "phase cloak upkeep cost";
            }
        });
        put("phaseCloakActivationCostBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getPhaseCloakActivationCostBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "phase cloak activation cost";
            }
        });
        put("peakCRDuration", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getPeakCRDuration().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "peak CR duration";
            }
        });
        put("overloadTimeMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getOverloadTimeMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "overload time";
            }
        });
        put("missileWeaponRangeBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileWeaponRangeBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile weapon range";
            }
        });
        put("missileWeaponFluxCostMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileWeaponFluxCostMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile weapon flux cost";
            }
        });
        put("missileTurnAccelerationBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileTurnAccelerationBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile turn acceleration";
            }
        });
        put("missileMaxTurnRateBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileMaxTurnRateBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile max turn rate";
            }
        });
        put("missileMaxSpeedBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileMaxSpeedBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile max speed";
            }
        });
        put("missileAmmoBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileAmmoBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile ammo";
            }
        });
        put("missileAccelerationBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileAccelerationBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile acceleration";
            }
        });
        put("minCrewMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMinCrewMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "min crew";
            }
        });
        put("maxCrewMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxCrewMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "max crew";
            }
        });
        put("hullBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHullBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "hull";
            }
        });
        put("hitStrengthBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHitStrengthBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "hit strength";
            }
        });
        put("fuelUseMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFuelUseMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "fuel use";
            }
        });
        put("fuelMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFuelMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "fuel capacity";
            }
        });
        put("engineHealthBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEngineHealthBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "engine health";
            }
        });
        put("energyWeaponRangeBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyWeaponRangeBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "energy weapon range";
            }
        });
        put("energyWeaponFluxCostMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyWeaponFluxCostMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "energy weapon flux cost";
            }
        });
        put("energyAmmoBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyAmmoBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "energy ammo";
            }
        });
        put("cRPerDeploymentPercent", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCRPerDeploymentPercent().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "CR per deployment percent";
            }
        });
        put("cRLossPerSecondPercent", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCRLossPerSecondPercent().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "CR loss per second percent";
            }
        });
        put("cargoMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCargoMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "cargo capacity";
            }
        });
        put("beamWeaponTurnRateBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamWeaponTurnRateBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "beam weapon turn rate";
            }
        });
        put("beamWeaponRangeBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamWeaponRangeBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "beam weapon range";
            }
        });
        put("ballisticWeaponRangeBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticWeaponRangeBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "ballistic weapon range";
            }
        });
        put("ballisticWeaponFluxCostMod", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticWeaponFluxCostMod().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "ballistic weapon flux cost";
            }
        });
        put("ballisticAmmoBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticAmmoBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "ballistic ammo";
            }
        });
        put("armorBonus", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getArmorBonus().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "armor";
            }
        });
        put("zeroFluxSpeedBoost", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getZeroFluxSpeedBoost().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "zero flux speed boost";
            }
        });
        put("zeroFluxMinimumFluxLevel", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getZeroFluxMinimumFluxLevel().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "zero flux minimum flux level";
            }
        });
        put("weaponMalfunctionChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponMalfunctionChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "weapon malfunction chance";
            }
        });
        put("weaponDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getWeaponDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "weapon damage taken";
            }
        });
        put("ventRateMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getVentRateMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "vent rate";
            }
        });
        put("turnAcceleration", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getTurnAcceleration().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "turn acceleration";
            }
        });
        put("shieldUpkeepMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldUpkeepMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield upkeep";
            }
        });
        put("shieldUnfoldRateMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldUnfoldRateMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield unfold rate";
            }
        });
        put("shieldTurnRateMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldTurnRateMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield turn rate";
            }
        });
        put("shieldMalfunctionFluxLevel", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldMalfunctionFluxLevel().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield malfunction flux level";
            }
        });
        put("shieldMalfunctionChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldMalfunctionChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield malfunction chance";
            }
        });
        put("shieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield damage taken";
            }
        });
        put("shieldAbsorptionMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldAbsorptionMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield absorption";
            }
        });
        put("recoilPerShotMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getRecoilPerShotMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "recoil per shot";
            }
        });
        put("projectileSpeedMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getProjectileSpeedMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "projectile speed";
            }
        });
        put("projectileShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getProjectileShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "projectile shield damage taken";
            }
        });
        put("projectileDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getProjectileDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "projectile damage taken";
            }
        });
        put("missileWeaponDamageMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileWeaponDamageMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile weapon damage";
            }
        });
        put("missileShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile shield damage taken";
            }
        });
        put("missileRoFMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileRoFMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile rate of fire";
            }
        });
        put("missileGuidance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileGuidance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile guidance";
            }
        });
        put("missileDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile damage taken";
            }
        });
        put("maxTurnRate", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxTurnRate().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "max turn rate";
            }
        });
        put("maxSpeed", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxSpeed().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "max speed";
            }
        });
        put("maxRecoilMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxRecoilMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "max recoil";
            }
        });
        put("maxCombatReadiness", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxCombatReadiness().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "max combat readiness";
            }
        });
        put("maxCombatHullRepairFraction", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxCombatHullRepairFraction().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "max combat hull repair fraction";
            }
        });
        put("maxBurnLevel", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxBurnLevel().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "max burn level";
            }
        });
        put("kineticShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getKineticShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "kinetic shield damage taken";
            }
        });
        put("kineticDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getKineticDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "kinetic damage taken";
            }
        });
        put("hullDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHullDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "hull damage taken";
            }
        });
        put("hullCombatRepairRatePercentPerSecond", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHullCombatRepairRatePercentPerSecond().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "hull combat repair rate percent per second";
            }
        });
        put("highExplosiveShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHighExplosiveShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "high explosive shield damage taken";
            }
        });
        put("highExplosiveDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHighExplosiveDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "high explosive damage taken";
            }
        });
        put("hardFluxDissipationFraction", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getHardFluxDissipationFraction().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "hard flux dissipation fraction";
            }
        });
        put("fragmentationShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFragmentationShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "fragmentation shield damage taken";
            }
        });
        put("fragmentationDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFragmentationDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "fragmentation damage taken";
            }
        });
        put("fluxDissipation", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFluxDissipation().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "flux dissipation";
            }
        });
        put("fluxCapacity", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFluxCapacity().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "flux capacity";
            }
        });
        put("fighterRefitTimeMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getFighterRefitTimeMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "fighter refit time";
            }
        });
        put("engineMalfunctionChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEngineMalfunctionChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "engine malfunction chance";
            }
        });
        put("engineDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEngineDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "engine damage taken";
            }
        });
        put("energyWeaponDamageMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyWeaponDamageMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "energy weapon damage";
            }
        });
        put("energyShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "energy shield damage taken";
            }
        });
        put("energyRoFMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyRoFMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "energy rate of fire";
            }
        });
        put("energyDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "energy damage taken";
            }
        });
        put("empDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEmpDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "emp damage taken";
            }
        });
        put("eccmChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEccmChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "eccm chance";
            }
        });
        put("deceleration", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDeceleration().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "deceleration";
            }
        });
        put("damageToTargetWeaponsMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToTargetWeaponsMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to target weapons";
            }
        });
        put("damageToTargetShieldsMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToTargetShieldsMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to target shields";
            }
        });
        put("damageToTargetEnginesMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToTargetEnginesMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to target engines";
            }
        });
        put("criticalMalfunctionChance", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCriticalMalfunctionChance().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "critical malfunction chance";
            }
        });
        put("crewLossMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCrewLossMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "crew loss";
            }
        });
        put("combatWeaponRepairTimeMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCombatWeaponRepairTimeMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "weapon repair time";
            }
        });
        put("combatEngineRepairTimeMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getCombatEngineRepairTimeMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "engine repair time";
            }
        });
        put("beamWeaponFluxCostMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamWeaponFluxCostMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "beam weapon flux cost";
            }
        });
        put("beamWeaponDamageMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamWeaponDamageMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "beam weapon damage";
            }
        });
        put("beamShieldDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamShieldDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "beam shield damage taken";
            }
        });
        put("beamDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBeamDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "beam damage taken";
            }
        });
        put("baseCRRecoveryRatePercentPerDay", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBaseCRRecoveryRatePercentPerDay().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "base CR recovery rate percent per day";
            }
        });
        put("ballisticWeaponDamageMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticWeaponDamageMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "ballistic weapon damage";
            }
        });
        put("ballisticRoFMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticRoFMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "ballistic rate of fire";
            }
        });
        put("autofireAimAccuracy", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getAutofireAimAccuracy().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "autofire aim accuracy";
            }
        });
        put("armorDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getArmorDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "armor damage taken";
            }
        });
        put("acceleration", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getAcceleration().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "acceleration";
            }
        });

        put("timeMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getTimeMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "time mult";
            }
        });
        put("suppliesToRecover", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSuppliesToRecover().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "supplies to recover";
            }
        });
        put("suppliesPerMonth", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSuppliesPerMonth().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "supplies per month";
            }
        });
        put("shieldSoftFluxConversion", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getShieldSoftFluxConversion().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "shield soft flux conversion";
            }
        });
        put("sensorStrength", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSensorStrength().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "sensor strength";
            }
        });
        put("sensorProfile", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getSensorProfile().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "sensor profile";
            }
        });
        put("repairRatePercentPerDay", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getRepairRatePercentPerDay().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "repair rate percent per day";
            }
        });

        put("missileAmmoRegenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMissileAmmoRegenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "missile ammo regen";
            }
        });
        put("minArmorFraction", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMinArmorFraction().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "min armor fraction";
            }
        });
        put("maxArmorDamageReduction", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getMaxArmorDamageReduction().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "max armor damage reduction";
            }
        });
        put("kineticArmorDamageTakenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getKineticArmorDamageTakenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "kinetic armor damage taken";
            }
        });

        put("energyAmmoRegenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getEnergyAmmoRegenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "energy ammo regen";
            }
        });
        put("damageToMissiles", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToMissiles().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to missiles";
            }
        });
        put("damageToFrigates", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToFrigates().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to frigates";
            }
        });
        put("damageToFighters", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToFighters().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to fighters";
            }
        });
        put("damageToDestroyers", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToDestroyers().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to destroyers";
            }
        });
        put("damageToCruisers", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToCruisers().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to cruisers";
            }
        });
        put("damageToCapital", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getDamageToCapital().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "damage to capitals";
            }
        });
        put("ballisticProjectileSpeedMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticProjectileSpeedMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "ballistic projectile speed";
            }
        });
        put("ballisticAmmoRegenMult", new SimpleHullmodEffectPlugin() {
            @Override
            public void apply(MutableShipStatsAPI stats, float amt) {
                stats.getBallisticAmmoRegenMult().modifyMult(HANDMADE_HULLMOD_ID, amt);
            }

            @Override
            public String getName() {
                return "ballistic ammo regen";
            }
        });

    }};
}
