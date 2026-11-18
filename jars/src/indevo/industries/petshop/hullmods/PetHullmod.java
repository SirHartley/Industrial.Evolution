package indevo.industries.petshop.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.patches.PetRefitRowPatch;
import indevo.industries.petshop.plugins.PetCard;
import patchlib.api.PatchLib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Use indevo.other.YourMod_PetName_PetHullmod for a ready-made copy and paste solution for custom pet hullmods that won't crash unlike this one.
 */
public class PetHullmod extends SelfRepairingBuiltInHullmod {
    public static final float BASE_DECREASE = 5f;
    public static final float DMOD_CAP = 90f;
    // percent of max hull a second, read each combat frame
    public static final String MENDER = "IndEvo_pet_mender";
    // one system charge per 3 grades of +10% regen, so the talent mult counts for charges too
    public static final float OVERCHARGE_STEP = 30f;
    public static final String CAPTAIN = PetDataRepo.TRAIT_PREFIX + "captain";

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
            case "time_mult" -> stats.getTimeMult().modifyPercent(id, value, source);
            case "deployment" -> stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyMult(id, less, source);
            case "hull_regen" -> stats.getDynamic().getMod(MENDER).modifyFlat(id, value, source);
            case "system" -> {
                stats.getSystemRegenBonus().modifyPercent(id, value, source);
                stats.getSystemUsesBonus().modifyFlat(id, (float) Math.floor(value / OVERCHARGE_STEP), source);
            }
            case "min_crew" -> stats.getMinCrewMod().modifyMult(id, less, source);
            case "explosion" -> {
                stats.getDynamic().getStat(Stats.EXPLOSION_RADIUS_MULT).modifyPercent(id, value, source);
                stats.getDynamic().getStat(Stats.EXPLOSION_DAMAGE_MULT).modifyPercent(id, value, source);
            }
            case "fighter_bays" -> stats.getNumFighterBays().modifyFlat(id, value, source);
            case "smods" -> stats.getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).modifyFlat(id, value, source);
            // the pet commands the ship as a person, Pet.updateCaptain seats it
            case "captain" -> {}
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

    // rebuilds a ship of a fleet now; a talent that went takes its room: the cheapest s-mods it made room for (as vanilla on a skill loss), fighters in lost bays to the cargo; names what went
    // the room before comes from the pet, the member stats may already be rebuilt when this runs
    public static List<String> updateShip(FleetMemberAPI member, Pet pet) {
        member.updateStats();
        ShipVariantAPI variant = member.getVariant();
        List<String> lost = new ArrayList<>();

        MutableStat.StatMod room = member.getStats().getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).getFlatBonus(pet.getData().hullmod);
        int now = room != null ? Math.round(room.value) : 0;
        int remove = Math.min(Misc.getCurrSpecialMods(variant) - sModCap(member), pet.sModRoom - now);
        pet.sModRoom = now;
        List<String> sMods = new ArrayList<>(variant.getSMods());
        sMods.sort(Comparator.comparingInt(id -> Global.getSettings().getHullModSpec(id).getCostFor(variant.getHullSize())));
        for (int i = 0; i < remove && i < sMods.size(); i++) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(sMods.get(i));
            variant.removePermaMod(spec.getId());
            lost.add(spec.getDisplayName());
        }

        CargoAPI cargo = member.getFleetData().getFleet().getCargo();
        List<String> wings = variant.getWings();
        for (int i = (int) member.getStats().getNumFighterBays().getModifiedValue(); i < wings.size(); i++) {
            String wing = variant.getWingId(i);
            if (wing == null || variant.getHullSpec().isBuiltInWing(i)) continue;

            variant.setWingId(i, null);
            cargo.addFighters(wing, 1);
            lost.add(Global.getSettings().getFighterWingSpec(wing).getWingName());
        }

        if (!lost.isEmpty()) member.updateStats();
        return lost;
    }

    // the Captain talent's person; not in the officer list, so it draws no pay and never gains or dies as an officer
    public static PersonAPI createCaptain(Pet pet) {
        PersonAPI person = Global.getFactory().createPerson();
        person.setFaction(Factions.PLAYER);
        person.setName(new FullName(pet.name, "", FullName.Gender.ANY));
        // a person on the default portrait counts as no captain at all (Person.isDefault)
        person.setPortraitSprite(pet.getData().icon);
        person.setPersonality(pet.sheet.disposition);
        person.addTag(Ids.TAG_PET_CAPTAIN);
        return person;
    }

    // the vanilla officer skill pool in one order per pet, so a new grade adds a skill; past the pool they turn elite
    public static void fitCaptain(PersonAPI person, Pet pet, int skills) {
        MutableCharacterStatsAPI stats = person.getStats();
        if (stats.getLevel() == skills) return;

        List<String> pool = new ArrayList<>();
        for (String id : Global.getSettings().getSortedSkillIds()) {
            SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
            if (!spec.isCombatOfficerSkill() || spec.hasTag(Skills.TAG_DEPRECATED) || spec.hasTag(Skills.TAG_NO_AI_CORE) || spec.hasTag(Skills.TAG_PLAYER_ONLY)) continue;
            if (spec.hasTag(Skills.TAG_NPC_ONLY) && !spec.hasTag(Skills.TAG_AI_CORE)) continue;
            pool.add(id);
        }
        Collections.shuffle(pool, new Random(pet.id.hashCode()));

        int elite = skills - pool.size();
        stats.setSkipRefresh(true);
        for (int i = 0; i < pool.size(); i++) stats.setSkillLevel(pool.get(i), i < elite ? 2f : i < skills ? 1f : 0f);
        stats.setLevel(skills);
        stats.setSkipRefresh(false);
        stats.refreshCharacterStatsEffects();
    }

    private static int sModCap(FleetMemberAPI member) {
        return Math.round(member.getStats().getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).computeEffective(Misc.MAX_PERMA_MODS));
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        float rate = ship.getMutableStats().getDynamic().getMod(MENDER).computeEffective(0f);
        if (rate <= 0f || !ship.isAlive() || ship.isHulk()) return;

        ship.setHitpoints(Math.min(ship.getMaxHitpoints(), ship.getHitpoints() + ship.getMaxHitpoints() * rate * 0.01f * amount));
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);

        Pet pet = ship != null ? PetStatusManager.getInstance().getPet(ship.getVariant()) : null;
        // the refit row is the only place with a click target under this tooltip
        if (pet != null) PetCard.hover(tooltip, pet, true, !isForModSpec && PatchLib.isPatchEnabled(PetRefitRowPatch.class));
    }
}
