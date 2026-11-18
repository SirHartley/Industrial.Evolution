package indevo.industries.petshop.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
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
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.patches.PetRefitRowPatch;
import indevo.industries.petshop.plugins.PetCard;
import indevo.utils.ui.Ui;
import patchlib.api.PatchLib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public static final String SCAVENGER = PetDataRepo.TRAIT_PREFIX + "scavenger";
    private static final String FLAW_ID = "_flaw";

    // the pet's line in the game's status list of the piloted ship: line 2 follows the ship, the first state that fits wins
    static final String STATUS_KEY = "IndEvo_petStatus";
    static final String STATUS_FONT = "graphics/fonts/victor14.fnt";
    static final String[] STATUS_LINES = {"startled", "panicking", "hiding", "holding on tight", "worried", "waiting it out", "nervous",
            "holding still", "glad to leave", "celebrating", "cheering you on", "keeping watch"};
    static final int STARTLED = 0;
    // lines before this one show in the warning colour
    static final int STATUS_WARN = 4;

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

        applyFlaws(stats, pet, id + FLAW_ID, source);
    }

    // always on, whatever the hearts; flaws keep their own id so a talent on the same stat does not replace them
    private static void applyFlaws(MutableShipStatsAPI stats, Pet pet, String id, String source) {
        for (Map.Entry<String, Integer> entry : pet.sheet.traits.entrySet()) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(entry.getKey());
            if (trait != null && trait.isFlaw() && !trait.stat().isEmpty()) applyStat(stats, trait.stat(), trait.value(entry.getValue(), 1f), id, source);
        }

        int glutton = pet.sheet.getGrade(PetGenetics.GLUTTON);
        if (glutton > 0) stats.getSuppliesPerMonth().modifyPercent(id, glutton * PetConstants.GLUTTON_SUPPLIES, source);
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
            case "cr_recovery_less" -> {
                stats.getBaseCRRecoveryRatePercentPerDay().modifyMult(id, less, source);
                stats.getRepairRatePercentPerDay().modifyMult(id, less, source);
            }
            case "crew_loss" -> stats.getCrewLossMult().modifyPercent(id, value, source);
            case "peak_time_less" -> stats.getPeakCRDuration().modifyMult(id, less, source);
            case "sensor_profile_more" -> stats.getSensorProfile().modifyPercent(id, value, source);
            case "recovery_less" -> {
                stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyMult(id, less, source);
                stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD).modifyMult(id, 1f + value * 0.01f, source);
            }
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
        person.setPortraitSprite(pet.getIcon());
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

    // per ship state of the status line, in the ship's custom data; timers count battle time, so they hold while paused
    private static final class Status {
        final String title;
        final String icon;
        float hull;
        float hit = 0f;
        int shown = -1;
        float shownFor = 0f;

        Status(Pet pet, float hull) {
            // the game does not shorten titles and shows them in capitals
            this.title = Ui.fit(pet.name.toUpperCase(Locale.ROOT), STATUS_FONT, PetConstants.STATUS_NAME_W);
            this.icon = pet.getIcon();
            this.hull = hull;
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        updateStatus(ship, amount);

        float rate = ship.getMutableStats().getDynamic().getMod(MENDER).computeEffective(0f);
        if (rate <= 0f || !ship.isAlive() || ship.isHulk()) return;

        ship.setHitpoints(Math.min(ship.getMaxHitpoints(), ship.getHitpoints() + ship.getMaxHitpoints() * rate * 0.01f * amount));
    }

    // fighters, shuttle pods and ships with no pet have no pet hullmod, so they never get here
    private static void updateStatus(ShipAPI ship, float amount) {
        if (!ship.isAlive() || ship.isHulk()) return;

        Status s = (Status) ship.getCustomData().get(STATUS_KEY);
        if (s == null) {
            Pet pet = PetStatusManager.getInstance().getPet(ship.getVariant());
            if (pet == null) return;
            s = new Status(pet, ship.getHitpoints());
            ship.setCustomData(STATUS_KEY, s);
        }

        // only hull damage startles, a new hit starts the second again
        if (ship.getHitpoints() < s.hull) s.hit = PetConstants.STATUS_HIT_TIME;
        else s.hit = Math.max(0f, s.hit - amount);
        s.hull = ship.getHitpoints();
        s.shownFor += amount;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship != engine.getPlayerShip()) return;

        // a line holds STATUS_HOLD_TIME unless a higher one comes up; startled ends with its second
        int state = statusState(ship, engine, s);
        boolean held = s.shown != STARTLED && s.shownFor < PetConstants.STATUS_HOLD_TIME;
        if (s.shown < 0 || state < s.shown || (state != s.shown && !held)) {
            s.shown = state;
            s.shownFor = 0f;
        }

        engine.maintainStatusForPlayerShip(STATUS_KEY, s.icon, s.title, STATUS_LINES[s.shown], s.shown < STATUS_WARN);
    }

    private static int statusState(ShipAPI ship, CombatEngineAPI engine, Status s) {
        float hull = ship.getHitpoints() / Math.max(1f, ship.getMaxHitpoints());
        if (s.hit > 0f) return STARTLED;
        if (ship.getFluxTracker().isOverloaded()) return 1;
        if (hull < PetConstants.STATUS_HULL_LOW) return 2;
        if (ship.getEngineController().isFlamedOut()) return 3;
        if (hull < PetConstants.STATUS_HULL_HALF) return 4;
        if (ship.getFluxTracker().isVenting()) return 5;
        if (ship.getFluxTracker().getFluxLevel() > PetConstants.STATUS_FLUX_HIGH) return 6;
        if (ship.isPhased()) return 7;
        if (ship.isRetreating()) return 8;
        if (engine.isEnemyInFullRetreat() || (engine.isCombatOver() && engine.getWinningSideId() == ship.getOwner())) return 9;
        if (ship.areAnyEnemiesInRange()) return 10;
        return 11;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);

        Pet pet = ship != null ? PetStatusManager.getInstance().getPet(ship.getVariant()) : null;
        // the refit row is the only place with a click target under this tooltip
        if (pet != null) PetCard.hover(tooltip, pet, true, !isForModSpec && PatchLib.isPatchEnabled(PetRefitRowPatch.class));
    }
}
