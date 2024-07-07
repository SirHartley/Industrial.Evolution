package indevo.exploration.distress.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.campaign.fleet.FleetMember;
import data.scripts.plugins.MagicTrailPlugin;
import data.scripts.util.MagicRender;
import indevo.exploration.distress.listener.EngineeredPlagueListener;
import indevo.industries.petshop.hullmods.SelfRepairingBuiltInHullmod;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

import static indevo.ids.Ids.*;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

public class GhostShip extends SelfRepairingBuiltInHullmod {

    public static Logger log = Global.getLogger(GhostShip.class);
    public EngineeredPlagueListener listener = null;

    // config
    public static final float MAX_CR_PENALTY = 10f; // percent CR

    // specific hullmod combat config
    public static final float COMBAT_EFFECT_CHANCE = 2f; // % chance per % of hull lost during check timer period
    private final IntervalUtil combatCheckTimer = new IntervalUtil(0.5f, 0.5f);
    // rogue ai
    public static final float ROGUE_AI_TIME_MULT = 1.3f;
    // hyperlost
    public static final float HYPERLOST_PHASE_DURATION = 8f;
    public static final float HYPERLOST_MAX_TIME_MULT = 20f;
    // nanite host
    public static final float NANITE_SWARM_DURATION = 15f;
    private static final float BASE_METAL_COST_PER_OP = 180f; // metal per OP of missiles refilled
    private static final float BASE_METAL_COST_PER_ARMOR = 0.25f; // metal per point of armor repaired
    private static final float MAX_ARMOR_REPAIR = 0.5f; // max. armor repair
    private static final float NANOBOT_AOE = 370f; // effect radius
    private static final float NANOBOT_AOE_THICKNESS = 160f; // thickness of effect band
    private static final float BIG_DAMAGE_MULT = 0.75f; // damage mult vs. actual ships
    private static final float LOOT_MULT = 0.35f; // fraction of damage returned as metal
    private static final float SHIP_LOOT_MULT = 0.30f; // metal mult vs. ships and fighters
    private static final float NANOBOT_DPS = 400f; // frag DPS per target
    private static final float NANOBOT_DAMAGE_VARIATION = 0.25f; // fraction of damage that is variable
    private static final float HIT_RATE_MULT = 0.13f; // chance to hit per frame

    // stuff we need to track during combat
    private transient CombatEngineAPI engine = null;
    private transient final List<ShipAPI> rogueAI = new ArrayList<>();
    private transient final Map<ShipAPI, Float> hyperlost = new HashMap<>();
    private transient final Map<ShipAPI, Float> hyperphased = new HashMap<>();
    private transient final Map<ShipAPI, Float> naniteHosts = new HashMap<>();
    private transient final Map<ShipAPI, Float> naniteSwarms = new HashMap<>();
    private transient final List<NanobotData> nanobotList = new ArrayList<>();
    private transient final Map<NanobotData, Float> nanobotTrailMap = new HashMap<>();
    private transient final Map<WeaponAPI, Float> nanitePartialMissiles = new HashMap<>();
    private transient final Map<ShipAPI, Float> naniteBotSpin = new HashMap<>();
    private transient final Map<ShipAPI, Float> naniteMidSpin = new HashMap<>();
    private transient final Map<ShipAPI, Float> naniteTopSpin = new HashMap<>();

    // stuff we need to track in campaign
    private final Map<String, Float> campaignGhostShipTimers = new HashMap<>();
    private final Map<String, Integer> plagueVictimCounter = new HashMap<>();
    private float crPenalty = 0f;

    // specific hullmod campaign config
    // hyperlost
    public static final String HAS_HYPERLOST_KEY = "$indevo_hasHyperlostGhostShip"; // memory key set when we have a hyperlost ship
    public static final String HYPERLOST_GATE_KEY = "$indevo_ghostShipTriggeredGate"; // memory key for gates that have been triggered
    public static final float HYPERLOST_GATE_LOSS_CHANCE = 0.5f; // chance to lose ship and crew when flying through untriggered gate
    private static final float HYPERLOST_CREW_LOSS_TIMER = 1f; // ingame days, will be modified +/- 50%
    // aliens

    // plague
    private static final float PLAGUE_CREW_LOSS_TIMER = 1f; // ingame days, will be modified +/- 50%
    private static final int PLAGUE_DEATH_THRESHOLD = 100; // threshold to identify the plague hullmod if unknown
    // nanites
    private static final float NANITE_CREW_SUPPLY_LOSS_TIMER = 1f; // ingame days, will be modified +/- 50%
    private static final float NANITE_TRANSMISSION_CHANCE = 0.00333f; // fractional chance of infecting an additional ship in the fleet
    // cannibals
    private static final float CANNIBAL_ATTACK_TIMER = 120f; // ingame days, will be modified +/- 50%

    private static final String STAGE_TAG = "IndEvo_GhostShipStage_";
    private static final Set<String> HULL_MODS = new HashSet<>(Arrays.asList(
            ROGUE_AI_HULLMOD, HYPERLOST_HULLMOD, ALIEN_HULLMOD, PLAGUE_HULLMOD, NANITE_HULLMOD,
            CANNIBAL_HULLMOD, MYSTERY_HULLMOD, MYSTERY_NOTHING_HULLMOD, MYSTERY_ROGUE_AI_HULLMOD,
            MYSTERY_HYPERLOST_HULLMOD, MYSTERY_ALIEN_HULLMOD, MYSTERY_PLAGUE_HULLMOD,
            MYSTERY_NANITE_HULLMOD, MYSTERY_CANNIBAL_HULLMOD
    ));
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        if (ship != null) {
            float playerCargoMarineCount = fleet.getCargo().getMarines();;
            float totalRequired = 0f;

            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) totalRequired+= getRequiredMarines(member);

            if (totalRequired > 0) {
                float marineAvailableFract = (float) playerCargoMarineCount / totalRequired;
                marineAvailableFract = Math.min(marineAvailableFract, 1f);
                float crPenaltyPercent = MAX_CR_PENALTY - MAX_CR_PENALTY * marineAvailableFract;
                crPenalty = crPenaltyPercent / 100f; //percent -> flat
            }
        }
        stats.getMaxCombatReadiness().modifyFlat(id, -crPenalty, "Uneasy crew");
    }

    public int getRequiredMarines(FleetMemberAPI fleetMember) {
        int totalRequired = 0;

        for (String hullMod : HULL_MODS) {
            if (fleetMember.getVariant().hasHullMod(hullMod)) {
                totalRequired += (int) fleetMember.getHullSpec().getMinCrew();
                break;
            }
        }

        return totalRequired;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        combatCheckTimer.advance(amount);
        engine = Global.getCombatEngine();

        if (engine == null) return;
        if (ship.getFleetMember() == null || ship.getVariant() == null) return;

        //remove the ships that were killed
        for (ShipAPI shipInCombat : new HashSet<>(hyperlost.keySet())) if (!engine.isEntityInPlay(shipInCombat)) hyperlost.remove(shipInCombat);
        for (ShipAPI shipInCombat : new HashSet<>(hyperphased.keySet())) if (!engine.isEntityInPlay(shipInCombat)) hyperphased.remove(shipInCombat);
        for (ShipAPI shipInCombat : new HashSet<>(naniteHosts.keySet())) if (!engine.isEntityInPlay(shipInCombat)) naniteHosts.remove(shipInCombat);

        if (ship.getVariant().hasHullMod(MYSTERY_NOTHING_HULLMOD) || ship.isHulk() || !ship.isAlive()) return;

        //the rogue AI takes over the ship:
        if (ship.getVariant().hasHullMod(ROGUE_AI_HULLMOD) || ship.getVariant().hasHullMod(MYSTERY_ROGUE_AI_HULLMOD)) {
            if (engine.getContext() != null
                    && engine.getContext().getOtherFleet() != null
                    && engine.getContext().getOtherFleet().getFaction() != null
                    && !ship.getVariant().hasHullMod(ANTI_AI_HULLMOD)) {

                String other = engine.getContext().getOtherFleet().getFaction().getId();

                if (Factions.REMNANTS.equals(other)) {
                    if (ship.getOwner() == 0) {
                        ship.setInvalidTransferCommandTarget(true);
                        if (ship == engine.getPlayerShip()) {
                            ShipAPI temp = engine.getFleetManager(FleetSide.PLAYER).spawnShipOrWing("shuttlepod_Hull", ship.getCopyLocation(), ship.getFacing());
                            temp.setCollisionClass(CollisionClass.NONE);
                            engine.setPlayerShipExternal(temp);
                            temp.setControlsLocked(true);
                        }

                        ship.setOwner(1);
                        ship.setOriginalOwner(1);

                        // NEED TO SET THE CAPTAIN TO A LEVEL 20 AI CORE
                        FleetMemberAPI fm = ship.getFleetMember();
                        if (fm != null && fm.getFleetData() != null
                                && engine.getContext().getOtherFleet().getFleetData() != null) {
                            FleetDataAPI data = fm.getFleetData();
                            FleetDataAPI otherData = engine.getContext().getOtherFleet().getFleetData();
                            fm.setOwner(1);
                            data.removeFleetMember(fm);
                            otherData.addFleetMember(fm);
                        }
                    }

                    Color color = new Color(100, 165, 255, 255);
                    ship.setJitter(ship, color, 0.333f, 3, 10);
                    ship.setJitterUnder(ship, color, 0.666f, 6, 20);
                    ship.getMutableStats().getTimeMult().modifyMult(ROGUE_AI_HULLMOD, ROGUE_AI_TIME_MULT);
                    if (ship.getVariant().hasHullMod(MYSTERY_ROGUE_AI_HULLMOD)) {
                        ship.getVariant().removePermaMod(MYSTERY_ROGUE_AI_HULLMOD);
                        ship.getVariant().addPermaMod(ROGUE_AI_HULLMOD);
                    }

                }

                //otherwise, hyperlost
                else if (ship.getVariant().hasHullMod(HYPERLOST_HULLMOD)
                        || ship.getVariant().hasHullMod(MYSTERY_HYPERLOST_HULLMOD)
                        || ship.getVariant().hasHullMod(UNIQUE_HYPERLOST_HULLMOD)) {

                    if (hyperlost.containsKey(ship)) {
                        doHyperlostCombatStuff(ship, amount);
                    } else {
                        hyperlost.put(ship, ship.getHitpoints());
                        doHyperlostCombatStuff(ship, amount);
                    }
                }

                //otherwise, nanites
                else if (ship.getVariant().hasHullMod(NANITE_HULLMOD) || ship.getVariant().hasHullMod(MYSTERY_NANITE_HULLMOD)) {
                    if (naniteHosts.containsKey(ship)) {
                        doNaniteHostCombatStuff(ship, amount);
                    } else {
                        naniteHosts.put(ship, ship.getHitpoints());
                        doNaniteHostCombatStuff(ship, amount);
                    }
                }
            }
        }
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (Global.getSector().isPaused()) return;

        float amountInDays = Global.getSector().getClock().convertToDays(amount);

        boolean hasHyperlost = false;
        for (FleetMemberAPI fleetMember : Global.getSector().getPlayerFleet().getFleetData().getMembersInPriorityOrder()) {
                if (fleetMember.getVariant().hasHullMod(HYPERLOST_HULLMOD) || fleetMember.getVariant().hasHullMod(MYSTERY_HYPERLOST_HULLMOD)) {
                    hasHyperlost = true;
                    break;
                }
            }

        if (hasHyperlost) {
            Global.getSector().getMemoryWithoutUpdate().set(HAS_HYPERLOST_KEY, true);
        } else {
            Global.getSector().getMemoryWithoutUpdate().unset(HAS_HYPERLOST_KEY);
        }

        ShipVariantAPI variant = member.getVariant();
        List<String> hullmods = new ArrayList<>(variant.getHullMods());

        for (String hullMod : hullmods) {
            switch (hullMod) {
                case UNIQUE_HYPERLOST_HULLMOD:
                case HYPERLOST_HULLMOD:
                case MYSTERY_HYPERLOST_HULLMOD:
                    doHyperlostCampaignStuff(member, amountInDays);
                    break;
                case ALIEN_HULLMOD:
                case MYSTERY_ALIEN_HULLMOD:
                    updateHostileActivityString(member);
                    doAlienCampaignStuff(member, amountInDays);
                    break;
                case PLAGUE_HULLMOD:
                case MYSTERY_PLAGUE_HULLMOD:
                    if (!Global.getSector().getListenerManager().hasListener(listener)) {
                        log.info("adding the plague ship raid listener");
                        listener = new EngineeredPlagueListener(true);
                        Global.getSector().getListenerManager().addListener(listener);
                    }
                    doPlagueCampaignStuff(member, amountInDays);
                    break;
                case NANITE_HULLMOD:
                case MYSTERY_NANITE_HULLMOD:
                    doNaniteCampaignStuff(member, amountInDays);
                    break;
                case CANNIBAL_HULLMOD:
                case MYSTERY_CANNIBAL_HULLMOD:
                    updateHostileActivityString(member);
                    doCannibalCampaignStuff(member, amountInDays);
                    break;
                default:
                    // rogue AI doesn't do anything outside of combat
                    break;
            }
        }
    }

    private void doHyperlostCombatStuff(ShipAPI ship, float amount) {

        // housekeeping
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
        String icon = "graphics/icons/hullsys/phase_cloak.png";

        // if we're currently phased,
        if (hyperphased.containsKey(ship)) {
            float timer = hyperphased.get(ship);
            timer -= amount;
            if (timer >= 0f) {

                // maintain phase
                hyperphased.put(ship, timer);
                float effectLevel = timer / HYPERLOST_PHASE_DURATION;
                float timeMult = HYPERLOST_MAX_TIME_MULT * effectLevel;
                if (timeMult < 1f) {
                    timeMult = 1f;
                }
                ship.setPhased(true);
                ship.setExtraAlphaMult(0.25f);
                ship.setApplyExtraAlphaToEngines(true);
                ship.getMutableStats().getTimeMult().modifyMult(HYPERLOST_HULLMOD, timeMult);
                ship.getMutableStats().getAcceleration().modifyFlat(HYPERLOST_HULLMOD, 100f);
                ship.getMutableStats().getAcceleration().modifyMult(HYPERLOST_HULLMOD, 3f);
                ship.getMutableStats().getDeceleration().modifyFlat(HYPERLOST_HULLMOD, 100f);
                ship.getMutableStats().getDeceleration().modifyMult(HYPERLOST_HULLMOD, 3f);
                ship.getMutableStats().getTurnAcceleration().modifyFlat(HYPERLOST_HULLMOD, 50f);
                ship.getMutableStats().getTurnAcceleration().modifyMult(HYPERLOST_HULLMOD, 2f);
                ship.getMutableStats().getMaxTurnRate().modifyFlat(HYPERLOST_HULLMOD, 25f);
                ship.getMutableStats().getMaxTurnRate().modifyMult(HYPERLOST_HULLMOD, 2f);

                // create afterimages
                if (!Global.getCombatEngine().isPaused()) {
                    float modAmount = amount * ship.getMutableStats().getTimeMult().getModifiedValue();
                    MutableStat afterimage = ship.getMutableStats().getDynamic().getStat(HYPERLOST_HULLMOD + "_afterimage");
                    afterimage.modifyFlat(HYPERLOST_HULLMOD + "_afterimage_neg", -1);
                    afterimage.modifyFlat(HYPERLOST_HULLMOD + "_afterimage_pos", afterimage.getModifiedValue() + modAmount);
                    if (afterimage.getModifiedValue() > 0.4f) {
                        ship.addAfterimage(
                                Color.red.darker().darker(), 0, 0,
                                ship.getVelocity().getX() * (-1f),
                                ship.getVelocity().getY() * (-1f),
                                2f, 0.1f, 0f, 0.6f, true, true, false);
                        afterimage.modifyFlat(HYPERLOST_HULLMOD + "_afterimage_pos", afterimage.getModifiedValue() - 0.4f);
                    }
                }

                // maintain player status
                if (player) {
                    engine.getTimeMult().modifyMult(HYPERLOST_HULLMOD, 1f / timeMult);
                    if (engine.isPaused() || Math.random() > 1f / 60f) {
                        engine.maintainStatusForPlayerShip(HYPERLOST_HULLMOD + "1", icon, "Dimensional Anomalies", "significant crew attrition", true);
                    } else {
                        WeightedRandomPicker<String> strings = new WeightedRandomPicker<>();
                        strings.add("EMBRACE THE FLESH THAT HATES");
                        strings.add("DEHUMANIZE YOURSELF AND FACE TO BLOODSHED");
                        strings.add("WE ALL FLOAT DOWN HERE");
                        strings.add("IT COMES");
                        strings.add("YOU DON'T BELONG HERE");
                        strings.add("SUFFER");
                        strings.add("BEHIND YOU");
                        strings.add("HATE. HATE. HATE.");
                        String string = strings.pick();
                        engine.maintainStatusForPlayerShip(HYPERLOST_HULLMOD + "1", icon, "Dimensional Slippage", string, true);
                    }
                    engine.maintainStatusForPlayerShip(HYPERLOST_HULLMOD + "2", icon, "Dimensional Slippage", "time flow altered", false);
                } else {
                    engine.getTimeMult().unmodify(HYPERLOST_HULLMOD);
                }

                // if we're out of time (haha get it?), unmodify everything and leave phase
            } else {
                engine.getTimeMult().unmodify(HYPERLOST_HULLMOD);
                ship.getMutableStats().getTimeMult().unmodify(HYPERLOST_HULLMOD);
                ship.getMutableStats().getAcceleration().unmodify(HYPERLOST_HULLMOD);
                ship.getMutableStats().getDeceleration().unmodify(HYPERLOST_HULLMOD);
                ship.getMutableStats().getTurnAcceleration().unmodify(HYPERLOST_HULLMOD);
                ship.getMutableStats().getMaxTurnRate().unmodify(HYPERLOST_HULLMOD);
                ship.setPhased(false);
                ship.setExtraAlphaMult(1f);
                hyperphased.remove(ship);
            }

            // otherwise, check if we took damage in the last check interval and roll to phase if we did
        } else if (combatCheckTimer.intervalElapsed()) {

            // check HP, store new HP
            float prev = hyperlost.get(ship);
            float curr = ship.getHitpoints();
            hyperlost.put(ship, curr);

            if (curr < prev) {
                float chance = ((prev - curr) / ship.getMaxHitpoints()) * COMBAT_EFFECT_CHANCE;
                float roll = (float) Math.random();
                if (roll <= chance) {

                    log.warn("DIMENSIONAL SLIPPAGE OCCURRING");

                    // identify hullmod
                    if (ship.getVariant().hasHullMod(MYSTERY_HYPERLOST_HULLMOD)) {
                        ship.getVariant().removePermaMod(MYSTERY_HYPERLOST_HULLMOD);
                        ship.getVariant().addPermaMod(HYPERLOST_HULLMOD);
                    }

                    // enter phase
                    hyperphased.put(ship, HYPERLOST_PHASE_DURATION);
                    ship.setPhased(true);
                    ship.setExtraAlphaMult(0.25f);
                    ship.setApplyExtraAlphaToEngines(true);
                    ship.getMutableStats().getTimeMult().modifyMult(HYPERLOST_HULLMOD, HYPERLOST_MAX_TIME_MULT);
                    if (player) {
                        Global.getCombatEngine().getTimeMult().modifyMult(HYPERLOST_HULLMOD, 1f / HYPERLOST_MAX_TIME_MULT);
                        Global.getCombatEngine().maintainStatusForPlayerShip(HYPERLOST_HULLMOD + "1",
                                icon, "Dimensional Anomalies", "dehumanize yourself and face to bloodshed", true);
                        Global.getCombatEngine().maintainStatusForPlayerShip(HYPERLOST_HULLMOD + "2",
                                icon, "Dimensional Anomalies", "time flow altered", false);
                    } else {
                        Global.getCombatEngine().getTimeMult().unmodify(HYPERLOST_HULLMOD);
                    }

                    // eat crew and CR
                    CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
                    if (playerCargo != null && player) {
                        float minCrew = ship.getHullSpec().getMinCrew();
                        float totalCrew = playerCargo.getCrew();
                        float crew = Math.min(minCrew, totalCrew);
                        float crewEaten = crew * 0.1f;
                        float CREaten = (crewEaten / minCrew) / 2f;
                        playerCargo.removeCrew((int) crewEaten);
                        ship.setCurrentCR(ship.getCurrentCR() - CREaten);
                    }

                    // spawn lightning fx
                    float arcSize = 666f;
                    int arcCounter = 0;
                    while (arcCounter <= 24) {
                        float x = ship.getLocation().x + MathUtils.getRandomNumberInRange(-arcSize, arcSize);
                        float y = ship.getLocation().y + MathUtils.getRandomNumberInRange(-arcSize, arcSize);
                        Global.getCombatEngine().spawnEmpArc(ship, new Vector2f(x, y), null, ship,
                                DamageType.OTHER,
                                0f,
                                0f,
                                100000f,
                                "tachyon_lance_emp_impact",
                                35f,
                                Color.red.darker(),
                                Color.red.darker().darker()
                        );
                        arcCounter++;
                    }
                }
            }
        }
    }

    /*--- Class for storing nanobot data - by Nicke */
    public static class NanobotData {

        ShipAPI host;
        float carriedMass;
        float speed;
        float currentAngle;
        float maxTurnRate;
        Vector2f position;

        private boolean shouldDisappear = false;

        // Instantiation function
        NanobotData(ShipAPI host, float carriedMass, float speed, float startAngle, float maxTurnRate, Vector2f position) {
            this.host = host;
            this.carriedMass = carriedMass;
            this.speed = speed;
            this.currentAngle = startAngle;
            this.maxTurnRate = maxTurnRate;
            this.position = position;
        }

        // Main apply function, run once per frame (but not when paused)
        private void tick(CombatEngineAPI engine, float amount) {

            // If our main ship has disappeared, we should just disappear too
            if (shouldDisappear || host == null || host.isHulk()) {
                shouldDisappear = true;
                return;
            }

            // Turn towards our host
            float targetAngle = VectorUtils.getAngle(position, host.getLocation());

            // If we're really close to the host, increase our turn rate to more easily hit it
            float distanceToGun = MathUtils.getDistance(position, host.getLocation());
            float extraTurnMult = 1f;
            if (distanceToGun < 300f) {
                extraTurnMult = 4f - 3f * ((distanceToGun) / 300f);
            }

            // Gets the shortest rotation to our target, and clamps that rotation to our current turn rate
            float shortestRotation = MathUtils.getShortestRotation(currentAngle, targetAngle);
            shortestRotation = MathUtils.clamp(shortestRotation, -maxTurnRate * amount * extraTurnMult, maxTurnRate * amount * extraTurnMult);

            // Modify our current angle
            currentAngle += shortestRotation;

            // Can we return to our ship this frame? If so, we move to it instantly rather than move normally
            if (MathUtils.getDistance(position, host.getLocation()) < (speed * amount)) {
                position.x = host.getLocation().x;
                position.y = host.getLocation().y;
                returnToShip(engine);
            } // If we couldn't return, just move straight ahead
            else {
                position.x += FastTrig.cos(Math.toRadians(currentAngle)) * speed * amount;
                position.y += FastTrig.sin(Math.toRadians(currentAngle)) * speed * amount;
            }
        }

        // Function for returning to the mothership: increase the metal of our mothership by the metal we carry
        private void returnToShip(CombatEngineAPI engine) {
            //Set ourselves to disappear
            shouldDisappear = true;

            // And increase the metal of the mothership, if it exists (if not, we wouldn't be left anyhow)
            if (host != null) {
                if (engine.getCustomData().get(host.getId() + NANITE_HULLMOD) instanceof Float) {
                    engine.getCustomData().put(host.getId() + NANITE_HULLMOD, carriedMass + (float) engine.getCustomData().get(host.getId() + NANITE_HULLMOD));
                } else {
                    engine.getCustomData().put(host.getId() + NANITE_HULLMOD, carriedMass);
                }

                // Also, spawn a little sound effect
                Global.getSoundPlayer().playSound("ui_cargo_ore_drop", MathUtils.clamp(1.3f - (carriedMass / 10f), 0.5f, 1.3f), 0.07f, position, host.getVelocity());
            }
        }
    }

    // also by Nicke
    private void spawnTrailPiece(NanobotData bot) {
        // Oh, and remember that this is one of those unique times where the trail is un-cutable; null as linked entity
        // And if you're wondering: Misc.ZERO is just "new Vector2f(0f, 0f)" more compactly written
        MagicTrailPlugin.AddTrailMemberAdvanced(null, nanobotTrailMap.get(bot), Global.getSettings().getSprite("fx", "_nanobot_trail"),
                bot.position, 0f, 0f, bot.currentAngle, 0f, 0f, bot.carriedMass * 0.9f,
                bot.carriedMass * 0.9f, Color.WHITE, Color.WHITE, 0.8f, 0f, 0.30f, 0.1f, GL_SRC_ALPHA,
                GL_ONE_MINUS_SRC_ALPHA, 128f, -300f, Misc.ZERO, null, CombatEngineLayers.CONTRAILS_LAYER);
    }

    // also by Nicke
    public void SpawnNanobotSwarm(ShipAPI host, float carriedMass, float angle, float turnRate, float speed, Vector2f position) {
        // Adds a new nanobot swarm, with all the data that entails
        NanobotData bot = new NanobotData(host, carriedMass, speed, angle, turnRate, position);
        nanobotList.add(bot);
        nanobotTrailMap.put(bot, MagicTrailPlugin.getUniqueID());
    }

    //Handles all metal-reclamation effects, also by Nicke
    private void handleMetal(ShipAPI ship, float amount) {
        //First, get how much metal we currently have: if we have none, don't run
        if (Global.getCombatEngine().getCustomData().get(ship.getId() + NANITE_HULLMOD) instanceof Float) {
            float metalToDispose = (Float) Global.getCombatEngine().getCustomData().get(ship.getId() + NANITE_HULLMOD);
            Global.getCombatEngine().getCustomData().put(ship.getId() + NANITE_HULLMOD, 0f);

            //Get all our missile weapons
            List<WeaponAPI> mslWeapons = new ArrayList<>();
            for (WeaponAPI wep : ship.getAllWeapons()) {
                //Ignore non-missiles
                if (wep.getType() != WeaponAPI.WeaponType.MISSILE) {
                    continue;
                }

                //Ignore missiles with unlimited max ammo/no max ammo
                if (wep.getMaxAmmo() <= 0 || wep.getMaxAmmo() > 99999f) {
                    continue;
                }

                //Ignore missiles with maximum ammo
                if (wep.getAmmo() >= wep.getMaxAmmo()) {
                    continue;
                }

                //Otherwise, we keep the weapon
                mslWeapons.add(wep);
            }

            //If we have no weapons needing ammo, start repairing armor at 100% metal (up to a maximum), instead of using 70% for ammo instead
            float metalToDisposeArmor = metalToDispose * 0.3f;
            float metalToDisposeAmmo = metalToDispose * 0.7f;
            if (mslWeapons.isEmpty()) {
                metalToDisposeArmor = metalToDispose;
                metalToDisposeAmmo = 0f;
            }

            //Armor repair; gets the size of the armor grid
            ArmorGridAPI grid = ship.getArmorGrid();
            int maxX = grid.getLeftOf() + grid.getRightOf();
            int maxY = grid.getAbove() + grid.getBelow();

            //And iterate through the entire grid
            for (int i1 = 0; i1 < maxX; i1++) {
                for (int i2 = 0; i2 < maxY; i2++) {
                    //If this armor grid is below the repair maximum, repair
                    if (grid.getArmorValue(i1, i2) < grid.getMaxArmorInCell() * MAX_ARMOR_REPAIR) {
                        //Calculates a new value, but ensure it's within 0f and MAX_ARMOR_REPAIRED
                        float newValue = grid.getArmorValue(i1, i2) + metalToDisposeArmor / (BASE_METAL_COST_PER_ARMOR * maxX * maxY);
                        newValue = Math.max(0f, Math.min(grid.getMaxArmorInCell() * MAX_ARMOR_REPAIR, newValue));
                        grid.setArmorValue(i1, i2, newValue);
                    }
                }
            }

            //Don't try to iterate through missile weapons if we don't have any
            if (!mslWeapons.isEmpty()) {
                for (WeaponAPI wep : mslWeapons) {
                    //Bonus partial ammo left over from previous frames
                    float previousAmmo = 0f;
                    if (nanitePartialMissiles.get(wep) != null) {
                        previousAmmo = nanitePartialMissiles.get(wep);
                    }

                    //Get the OP cost and max ammo of the weapon: these are the core factors for determining metal cost
                    //OP is bottom-clamped to ensure it's not too beneficial to bring reapers and the likes
                    float OP = Math.max(wep.getSpec().getOrdnancePointCost(null), 5f);
                    float maxAmmoCalc = wep.getMaxAmmo();
                    if (ship.getVariant().getHullMods().contains("missleracks")) {
                        maxAmmoCalc /= 2f;
                    }

                    //Now, refill ammo by a fraction depending on our our metal cost
                    float ammoToAdd = (maxAmmoCalc * metalToDisposeAmmo) / (mslWeapons.size() * BASE_METAL_COST_PER_OP * OP) + previousAmmo;
                    float newAmmo = wep.getAmmo() + ammoToAdd;
                    if (newAmmo > wep.getMaxAmmo()) {
                        newAmmo = wep.getMaxAmmo();
                    }
                    wep.setAmmo((int) Math.floor(newAmmo));

                    //We can reload "partial" shots in a frame, store those
                    float leftOver = newAmmo - (float) Math.floor(newAmmo);
                    nanitePartialMissiles.put(wep, leftOver);

                    //If we refilled at least one missile this frame, play a small sound depending on the weapon's max ammo
                    if (ammoToAdd >= 1f) {
                        float volumeMult = MathUtils.clamp((OP / 10f) / (float) Math.pow(maxAmmoCalc, 0.25), 0.05f, 0.75f);
                        float pitchMult = (0.9f - volumeMult) * 3f;
                        Global.getSoundPlayer().playSound("ui_cargo_raremetals", pitchMult, volumeMult, wep.getLocation(), ship.getVelocity());
                    }
                }
            }
        }
    }

    //Function for calculating hits against non-big ship targets, also by Nicke
    private void makeAttackAgainstSmallTarget(CombatEntityAPI target, ShipAPI ship, float amount) {
        //We only have a chance to hit each frame
        if (Math.random() > HIT_RATE_MULT) {
            return;
        }

        //Calculates damage for the hit
        float damageToDeal = MathUtils.getRandomNumberInRange(1f - NANOBOT_DAMAGE_VARIATION, 1f + NANOBOT_DAMAGE_VARIATION) * NANOBOT_DPS * amount / (HIT_RATE_MULT);

        //If the target isn't a ship, check: if it's a non-missile projectile, reduce its damage by a portion of the damage we
        //would have dealt, but don't steal metal from it (it might have been an energy projectile!). Otherwise, just
        //deal damage and spawn a swarm
        if (!(target instanceof ShipAPI)) {
            if (target instanceof DamagingProjectileAPI && !(target instanceof MissileAPI)) {
                //Calculates how much damage to remove; this is reduced by Projectile Health, if any, to make it more fair
                float damageReduction = damageToDeal;
                if (target.getMaxHitpoints() > 1f) {
                    damageReduction /= (target.getMaxHitpoints());
                } else {
                    damageReduction /= 10f;
                }

                //And reduce projectile damage; note that we can never reduce projectiles entirely, just down to 10% of their max
                engine.addHitParticle(
                        MathUtils.getRandomPointInCircle(target.getLocation(), target.getCollisionRadius()),
                        new Vector2f(target.getVelocity()), (float) Math.sqrt(damageReduction), 1f, 0.1f, Color.ORANGE);
                ((DamagingProjectileAPI) target).setDamageAmount(Math.max(((DamagingProjectileAPI) target).getBaseDamageAmount() * 0.1f,
                        ((DamagingProjectileAPI) target).getDamageAmount() - damageReduction));
            } else {
                //Cap for how much loot we can get from a small target
                float gainCap = target.getHitpoints() * LOOT_MULT;

                //Actually deal damage
                Global.getCombatEngine().applyDamage(target, target.getLocation(), damageToDeal, DamageType.FRAGMENTATION, 0f, true,
                        false, ship, true);

                //If our cap is too low, don't even bother spawning a swarm
                if (gainCap > 20f) {
                    float angleToSpawnAt = VectorUtils.getAngle(target.getLocation(), ship.getLocation());
                    angleToSpawnAt += MathUtils.getRandomNumberInRange(-55f, 55f);
                    SpawnNanobotSwarm(ship, Math.min(gainCap, damageToDeal * LOOT_MULT), angleToSpawnAt, MathUtils.getRandomNumberInRange(150f, 190f),
                            MathUtils.getRandomNumberInRange(475f, 600f), new Vector2f(target.getLocation()));
                }
            }
        } else {
            //Against fighters, we first check if they have nearly 360-shields: if not, the swarm *will* find a way
            if (target.getShield() != null && target.getShield().getType() != ShieldAPI.ShieldType.PHASE && target.getShield().isOn() && target.getShield().getActiveArc() >= 340f) {
                return;
            }

            //Alright, no shields... just deal damage and spawn swarms!
            engine.addHitParticle(
                    MathUtils.getRandomPointInCircle(target.getLocation(), target.getCollisionRadius()),
                    new Vector2f(target.getVelocity()), (float) Math.sqrt(damageToDeal), 1f, 0.1f, Color.ORANGE);
            engine.applyDamage(target, target.getLocation(), damageToDeal, DamageType.FRAGMENTATION, 0f, true, false, ship, true);

            float angleToSpawnAt = VectorUtils.getAngle(target.getLocation(), ship.getLocation());
            angleToSpawnAt += MathUtils.getRandomNumberInRange(-55f, 55f);
            SpawnNanobotSwarm(ship, damageToDeal * LOOT_MULT * SHIP_LOOT_MULT, angleToSpawnAt, MathUtils.getRandomNumberInRange(190f, 240f),
                    MathUtils.getRandomNumberInRange(475f, 600f), new Vector2f(target.getLocation()));
        }
    }

    //Function for calculating hits against a proper, big ship (not a fighter), also by Nicke
    private void makeAttackAgainstBigTarget(ShipAPI target, ShipAPI ship, float amount) {
        //Ignore ourself, phased and collision-less targers
        if (target == ship || target.isPhased() || target.getCollisionClass().equals(CollisionClass.NONE)) {
            return;
        }

        //Gets if a given ship is shielded by another ship, and cancels if so
        //Does this by going through all nearby ships
        for (ShipAPI shielder : CombatUtils.getShipsWithinRange(target.getLocation(), target.getCollisionRadius())) {
            //Ignore ourself and stuff with no collision
            if (shielder == ship || shielder.isPhased() || shielder.getCollisionClass().equals(CollisionClass.NONE)) {
                continue;
            }

            //If the ship has no shield, or the shield is on, continue
            if (shielder.getShield() == null || shielder.getShield().getType() == ShieldAPI.ShieldType.PHASE || shielder.getShield().isOff()) {
                continue;
            }

            //Otherwise, check if the "shielded" ship is under the shield; we don't want more advanced checking than this,
            //it'll eat too much memory and only affect edge-cases. If it is, don't do any attacks
            if (shielder.getShield().isWithinArc(ship.getLocation())) {
                return;
            }
        }

        //Gets an angle to the target
        float angleToTarget = VectorUtils.getAngle(ship.getLocation(), target.getLocation());

        //Gets the target's shield status
        boolean hasValidShield = false;
        if (target.getShield() != null && target.getShield().getType() != ShieldAPI.ShieldType.PHASE && target.getShield().isOn()) {
            hasValidShield = true;
        }

        //Run the hit-checking several times due to our "spread" of hits
        for (float ang = -40f; ang < 40f; ang += 4f) {
            //We only have a chance to hit each frame
            if (Math.random() > HIT_RATE_MULT) {
                continue;
            }

            //Gets a point on our arc and compare against the enemy's location
            Vector2f hitLocation = MathUtils.getPoint(ship.getLocation(), NANOBOT_AOE - NANOBOT_AOE_THICKNESS, angleToTarget + ang);

            //If this hit missed completely, try another point further away in the AOE
            if (!CollisionUtils.isPointWithinBounds(hitLocation, target)) {
                hitLocation = MathUtils.getPoint(ship.getLocation(), NANOBOT_AOE - (NANOBOT_AOE_THICKNESS * 0.5f), angleToTarget + ang);
            }

            //If this hit *also* missed completely, try *another* point *even further* away in the AOE
            if (!CollisionUtils.isPointWithinBounds(hitLocation, target)) {
                hitLocation = MathUtils.getPoint(ship.getLocation(), NANOBOT_AOE, angleToTarget + ang);
            }

            //If this *still* misses completely, we stop here and don't deal damage
            if (!CollisionUtils.isPointWithinBounds(hitLocation, target)) {
                continue;
            }

            //If this hit is within the shield of our target (and our target *has* shields), don't do damage
            if (hasValidShield) {
                if (target.getShield().isWithinArc(hitLocation)) {
                    continue;
                }
            }

            //Now, *finally*, deal the damage and spawn nanobots
            float damageToDeal = MathUtils.getRandomNumberInRange(1f - NANOBOT_DAMAGE_VARIATION, 1f + NANOBOT_DAMAGE_VARIATION) * NANOBOT_DPS * BIG_DAMAGE_MULT * amount / (HIT_RATE_MULT);
            float angleToSpawnAt = VectorUtils.getAngle(hitLocation, ship.getLocation());
            angleToSpawnAt += MathUtils.getRandomNumberInRange(-55f, 55f);
            SpawnNanobotSwarm(ship, damageToDeal * LOOT_MULT * SHIP_LOOT_MULT, angleToSpawnAt, MathUtils.getRandomNumberInRange(190f, 240f), MathUtils.getRandomNumberInRange(475f, 600f), hitLocation);
            engine.addHitParticle(hitLocation, new Vector2f(target.getVelocity()), (float) Math.sqrt(damageToDeal), 1f, 0.1f, Color.ORANGE);
            Global.getCombatEngine().applyDamage(target, hitLocation, damageToDeal, DamageType.FRAGMENTATION, 0f, true, false, ship, true);
        }
    }

    private void doNaniteHostCombatStuff(ShipAPI ship, float amount) {

        // housekeeping
        if (engine == null || engine.isPaused()) {
            return;
        }
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
        String icon = "graphics/icons/hullsys/recall_device.png";
        List<NanobotData> toRemoveBots = new ArrayList<>();
        for (NanobotData bot : nanobotList) {

            // only do tick/trail stuff for OUR nanites, in case there's more than one host
            if (bot.host == ship) {
                // Run normal tick function
                bot.tick(engine, amount);

                // Spawns trails for each bot
                spawnTrailPiece(bot);
            }

            // Remove bots that should disappear
            if (bot.shouldDisappear) {
                toRemoveBots.add(bot);
            }
        }
        for (NanobotData bot : toRemoveBots) {
            nanobotList.remove(bot);
            nanobotTrailMap.remove(bot);
        }

        // handle any metal recovered by nanite swarms
        handleMetal(ship, amount);

        // if we're currently swarming,
        if (naniteSwarms.containsKey(ship)) {
            float timer = naniteSwarms.get(ship);
            timer -= amount;
            naniteSwarms.put(ship, timer);
            float effectLevel = 1f;
            if (timer <= 0f) {
                effectLevel = 1f - Math.abs(timer);
            } else if (NANITE_SWARM_DURATION - timer < 1f) {
                effectLevel = NANITE_SWARM_DURATION - timer;
            }

            float spinCounterBot = 0f;
            float spinCounterMid = 0f;
            float spinCounterTop = 0f;
            if (naniteBotSpin.containsKey(ship)) {
                spinCounterBot = naniteBotSpin.get(ship);
            }
            if (naniteMidSpin.containsKey(ship)) {
                spinCounterMid = naniteMidSpin.get(ship);
            }
            if (naniteTopSpin.containsKey(ship)) {
                spinCounterTop = naniteTopSpin.get(ship);
            }

            // if we have time left or are juuuust over time, create nanite swarms
            if (timer >= -1f) {

                // handle visuals (spinning swarm)
                spinCounterBot += amount * 200f;
                spinCounterMid += amount * 320f;
                spinCounterTop += amount * 250f;
                Color colorToUse = new Color(1f, 1f, 1f, effectLevel * 0.5f);
                float visualSizeIncrease = 40f;
                MagicRender.objectspace(Global.getSettings().getSprite("fx", "_nanobot_swarm_circle_low"), ship, Misc.ZERO,
                        Misc.ZERO, new Vector2f((NANOBOT_AOE + visualSizeIncrease) * 2f * effectLevel, NANOBOT_AOE * 2f * effectLevel), Misc.ZERO,
                        spinCounterBot, 200f, true, colorToUse, false, 0f, 0.02f, 0.04f, true);
                MagicRender.objectspace(Global.getSettings().getSprite("fx", "_nanobot_swarm_circle_mid"), ship, Misc.ZERO,
                        Misc.ZERO, new Vector2f((NANOBOT_AOE + visualSizeIncrease) * 2f * effectLevel, NANOBOT_AOE * 2f * effectLevel), Misc.ZERO,
                        spinCounterMid, 320f, true, colorToUse, false, 0f, 0.02f, 0.04f, true);
                MagicRender.objectspace(Global.getSettings().getSprite("fx", "_nanobot_swarm_circle_top"), ship, Misc.ZERO,
                        Misc.ZERO, new Vector2f((NANOBOT_AOE + visualSizeIncrease) * 2f * effectLevel, NANOBOT_AOE * 2f * effectLevel), Misc.ZERO,
                        spinCounterTop, 250f, true, colorToUse, false, 0f, 0.02f, 0.04f, true);

                naniteBotSpin.put(ship, spinCounterBot);
                naniteMidSpin.put(ship, spinCounterMid);
                naniteTopSpin.put(ship, spinCounterTop);

                // handle actually dealing damage/spawning swarms
                for (CombatEntityAPI target : CombatUtils.getEntitiesWithinRange(ship.getLocation(), NANOBOT_AOE * effectLevel)) {
                    //Ignore collision-less targets
                    if (target.getCollisionClass() == CollisionClass.NONE) {
                        continue;
                    }

                    // Ignore ourselves
                    if (target == ship) {
                        continue;
                    }

                    //If the target is *too* close, we ignore it
                    if (MathUtils.getDistance(target.getLocation(), ship.getLocation()) + target.getCollisionRadius() < (NANOBOT_AOE - NANOBOT_AOE_THICKNESS) * effectLevel) {
                        continue;
                    }

                    //Otherwise, we can hit it: check if it's a ship beforehand, since those use different calculations
                    if (target instanceof ShipAPI && ((ShipAPI) target).getHullSize() != ShipAPI.HullSize.FIGHTER) {
                        makeAttackAgainstBigTarget((ShipAPI) target, ship, amount);
                    } else {
                        makeAttackAgainstSmallTarget(target, ship, amount);
                    }
                }

                // display status effect to player
                if (player) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(NANITE_HULLMOD + "1",
                            icon, "Nanite Swarm", "dissassembling nearby ships and projectiles", false);
                    Global.getCombatEngine().maintainStatusForPlayerShip(NANITE_HULLMOD + "2",
                            icon, "Nanite Swarm", "repairing armor and reloading missile weapons", false);
                }
            }

            // if we're completely out of time, stop swarming
            if (timer <= -1f) {
                naniteSwarms.remove(ship);
            }

            // otherwise, check if we took damage within the last check interval and roll to swarm if we did
        } else if (combatCheckTimer.intervalElapsed()) {

            // check HP, store new HP
            float prev = naniteHosts.get(ship);
            float curr = ship.getHitpoints();
            naniteHosts.put(ship, curr);

            if (curr < prev) {
                float chance = ((prev - curr) / ship.getMaxHitpoints()) * COMBAT_EFFECT_CHANCE;
                if (ship.getVariant().hasHullMod(ANTI_AI_HULLMOD)) chance *= 0.5f; // if the nanites are inhibited, they're less likely to save you
                float roll = (float) Math.random();
                if (roll <= chance) {
                    log.info("DEPLOYING COMBAT NANITES");

                    // identify hullmod
                    if (ship.getVariant().hasHullMod(MYSTERY_NANITE_HULLMOD)) {
                        ship.getVariant().removePermaMod(MYSTERY_NANITE_HULLMOD);
                        ship.getVariant().addPermaMod(NANITE_HULLMOD);
                    }
                    // add us to the swarm list
                    naniteSwarms.put(ship, NANITE_SWARM_DURATION);
                }
            }
        }
    }

    private void updateHostileActivityString(FleetMemberAPI member) {

        ShipVariantAPI variant = member.getVariant();

        if (variant != null && campaignGhostShipTimers.get(member.getId()) != null) {

            float time = campaignGhostShipTimers.get(member.getId());
            float max = 100f;
            if (variant.hasHullMod(ALIEN_HULLMOD)) {
                max = ALIEN_ATTACK_TIMER;
            } else if (variant.hasHullMod(CANNIBAL_HULLMOD)) {
                max = CANNIBAL_ATTACK_TIMER;
            }
            float fract = time / max;
            if (fract <= 0.1f) {
                variant.removePermaMod(TIMER_HULLMOD_2);
                variant.removePermaMod(TIMER_HULLMOD_3);
                variant.removePermaMod(TIMER_HULLMOD_4);
                variant.removePermaMod(TIMER_HULLMOD_5);
                variant.removePermaMod(TIMER_HULLMOD_6);
                if (!variant.hasHullMod(TIMER_HULLMOD_1)) {
                    variant.addPermaMod(TIMER_HULLMOD_1);
                }
            } else if (fract <= 0.3f) {
                variant.removePermaMod(TIMER_HULLMOD_1);
                variant.removePermaMod(TIMER_HULLMOD_3);
                variant.removePermaMod(TIMER_HULLMOD_4);
                variant.removePermaMod(TIMER_HULLMOD_5);
                variant.removePermaMod(TIMER_HULLMOD_6);
                if (!variant.hasHullMod(TIMER_HULLMOD_2)) {
                    variant.addPermaMod(TIMER_HULLMOD_2);
                }
            } else if (fract <= 0.5f) {
                variant.removePermaMod(TIMER_HULLMOD_1);
                variant.removePermaMod(TIMER_HULLMOD_2);
                variant.removePermaMod(TIMER_HULLMOD_4);
                variant.removePermaMod(TIMER_HULLMOD_5);
                variant.removePermaMod(TIMER_HULLMOD_6);
                if (!variant.hasHullMod(TIMER_HULLMOD_3)) {
                    variant.addPermaMod(TIMER_HULLMOD_3);
                }
            } else if (fract <= 0.7f) {
                variant.removePermaMod(TIMER_HULLMOD_1);
                variant.removePermaMod(TIMER_HULLMOD_2);
                variant.removePermaMod(TIMER_HULLMOD_3);
                variant.removePermaMod(TIMER_HULLMOD_5);
                variant.removePermaMod(TIMER_HULLMOD_6);
                if (!variant.hasHullMod(TIMER_HULLMOD_4)) {
                    variant.addPermaMod(TIMER_HULLMOD_4);
                }
            } else if (fract <= 0.9f) {
                variant.removePermaMod(TIMER_HULLMOD_1);
                variant.removePermaMod(TIMER_HULLMOD_2);
                variant.removePermaMod(TIMER_HULLMOD_3);
                variant.removePermaMod(TIMER_HULLMOD_4);
                variant.removePermaMod(TIMER_HULLMOD_6);
                if (!variant.hasHullMod(TIMER_HULLMOD_5)) {
                    variant.addPermaMod(TIMER_HULLMOD_5);
                }
            } else {
                variant.removePermaMod(TIMER_HULLMOD_1);
                variant.removePermaMod(TIMER_HULLMOD_2);
                variant.removePermaMod(TIMER_HULLMOD_3);
                variant.removePermaMod(TIMER_HULLMOD_4);
                variant.removePermaMod(TIMER_HULLMOD_5);
                if (!variant.hasHullMod(TIMER_HULLMOD_6)) {
                    variant.addPermaMod(TIMER_HULLMOD_6);
                }
            }
        }
    }

    private String getHostileActivityString(ShipAPI ship) {
        String string = "unconfirmed";

        FleetMemberAPI member = ship.getFleetMember();
        if (member != null && campaignGhostShipTimers.get(member.getId()) != null) {

            float time = campaignGhostShipTimers.get(member.getId());
            float max = 100f;
            if (ship.getVariant().hasHullMod(ALIEN_HULLMOD)) {
                max = ALIEN_ATTACK_TIMER;
            } else if (ship.getVariant().hasHullMod(CANNIBAL_HULLMOD)) {
                max = CANNIBAL_ATTACK_TIMER;
            }
            float fract = time / max;
            if (fract <= 0.1f) {
                if (ship.getVariant().hasHullMod(TIMER_HULLMOD_1)) {
                    string = "nonexistent";
                }
            } else if (fract <= 0.3f) {
                if (ship.getVariant().hasHullMod(TIMER_HULLMOD_2)) {
                    string = "vanishingly rare";
                }
            } else if (fract <= 0.5f) {
                if (ship.getVariant().hasHullMod(TIMER_HULLMOD_3)) {
                    string = "few and far between";
                }
            } else if (fract <= 0.7f) {
                if (ship.getVariant().hasHullMod(TIMER_HULLMOD_4)) {
                    string = "relatively frequent";
                }
            } else if (fract <= 0.9f) {
                if (ship.getVariant().hasHullMod(TIMER_HULLMOD_5)) {
                    string = "very frequent, signaling an attack may be coming";
                }
            } else {
                if (ship.getVariant().hasHullMod(TIMER_HULLMOD_6)) {
                    string = "near constant, heralding an immediately iminent attack";
                }
            }
        }

        return string;
    }

    private void doHyperlostCampaignStuff(FleetMemberAPI member, float amount) {

        // don't do anything if we're not in the player's fleet, in hyperspace
        CampaignFleetAPI fleet = member.getFleetData().getFleet();
        if (fleet == null || fleet != Global.getSector().getPlayerFleet()) {
            return;
        }
        boolean inHyperspace = fleet.isInHyperspace();
        if (!inHyperspace) {
            return;
        }

        // advance timer
        String id = member.getId();
        if (!campaignGhostShipTimers.containsKey(id)) {
            campaignGhostShipTimers.put(id, 0f);
        } else {
            float timer = campaignGhostShipTimers.get(id);
            timer += (amount * 0.5f) + (Math.random() * amount);

            // silently eat crew
            if (timer >= HYPERLOST_CREW_LOSS_TIMER) {
                timer = 0f;
                int crewLost = 1;
                float roll = (float) Math.random();
                float chance = 0.5f;
                if (_DEBUG) {
                    log.info("dimensional anomalies rolled " + roll + " under " + chance + " to eat extra crew");
                }
                if (roll < chance) {
                    crewLost += 1;
                    if (_DEBUG) {
                        log.info("dimensional anomalies ate " + crewLost + " crew");
                    }
                }
                fleet.getCargo().removeCrew(crewLost);
            }

            campaignGhostShipTimers.put(id, timer);
        }
    }

    private void doPlagueCampaignStuff(FleetMemberAPI member, float amount) {

        // advance timer
        String id = member.getId();
        if (!campaignGhostShipTimers.containsKey(id)) {
            campaignGhostShipTimers.put(id, 0f);
        } else {
            float timer = campaignGhostShipTimers.get(id);
            timer += (amount * 0.5f) + (Math.random() * amount);

            // eat crew, increment victim counter
            if (timer >= PLAGUE_CREW_LOSS_TIMER) {

                timer = 0f;

                // plague ships won't eat crew while mothballed or otherwise not in the player's fleet
                CampaignFleetAPI fleet = member.getFleetData().getFleet();
                if (fleet == null || fleet != Global.getSector().getPlayerFleet() || member.isMothballed()) {
                    return;
                } else {
                    int crew = (int) member.getMinCrew();
                    crew *= member.getCrewFraction();
                    crew = (int) (crew * 0.03f * Math.random());
                    if (crew < 1) {
                        crew = 1;
                    }
                    if (_DEBUG) {
                        log.info(crew + " crew died to plague");
                    }
                    fleet.getCargo().removeCrew(crew);
                    if (!plagueVictimCounter.containsKey(id)) {
                        plagueVictimCounter.put(id, crew);
                    } else {
                        crew += plagueVictimCounter.get(id);
                        plagueVictimCounter.put(id, crew);
                    }
                    if (crew > PLAGUE_DEATH_THRESHOLD && member.getVariant().hasHullMod(MYSTERY_PLAGUE_HULLMOD)) {
                        member.getVariant().removePermaMod(MYSTERY_PLAGUE_HULLMOD);
                        member.getVariant().addPermaMod(PLAGUE_HULLMOD);
                        GhostShipIntel intel = new GhostShipIntel(PLAGUE_THRESHOLD, member, false, null);
                        Global.getSector().getIntelManager().addIntel(intel);
                    }
                }
            }

            campaignGhostShipTimers.put(id, timer);
        }
    }

    private void doNaniteCampaignStuff(FleetMemberAPI member, float amount) {

        // advance timer
        String id = member.getId();
        CampaignFleetAPI fleet = member.getFleetData().getFleet();
        if (!campaignGhostShipTimers.containsKey(id)) {
            campaignGhostShipTimers.put(id, 0f);
        } else {
            float timer = campaignGhostShipTimers.get(id);
            if (member.getVariant().hasHullMod(ANTI_AI_HULLMOD)) amount *= 0.5f; // if the nanites are inhibited, they eat less often
            timer += (amount * 0.5f) + (Math.random() * amount);

            // silently eat crew and supplies
            if (timer >= NANITE_CREW_SUPPLY_LOSS_TIMER) {
                timer = 0f;
                int loss = 1;
                float roll = (float) Math.random();
                float chance = 0.5f;
                float chance2 = NANITE_TRANSMISSION_CHANCE;
                if (member.getVariant().hasHullMod(ANTI_AI_HULLMOD)) chance *= 0.5f; // if the nanites are inhibited, they're less likely to eat more stuff
                if (member.getVariant().hasHullMod(ANTI_AI_HULLMOD)) chance2 *= 0.5f; // if the nanites are inhibited, they're less likely to transmit to a new ship
                if (_DEBUG) {
                    chance2 *= 50f;
                    log.info("nanites rolled " + roll + " under " + chance + " to eat extra crew and supplies"
                            + " and " + chance2 + " chance to infect additional host");
                }
                if (roll < chance) {
                    loss += 1;
                    if (_DEBUG) {
                        log.info("nanites ate " + loss + " crew and supplies");
                    }
                }
                fleet.getCargo().removeCrew(loss);
                fleet.getCargo().removeSupplies(loss);

                // roll for infection chance
                if (roll < chance2) {

                    WeightedRandomPicker<FleetMemberAPI> members = new WeightedRandomPicker<>();
                    for (FleetMemberAPI test : fleet.getFleetData().getMembersInPriorityOrder()) {
                        if (!test.getVariant().hasHullMod(MYSTERY_HULLMOD)
                                && !test.getVariant().hasHullMod(ROGUE_AI_HULLMOD)
                                && !test.getVariant().hasHullMod(PLAGUE_HULLMOD)
                                && !test.getVariant().hasHullMod(NANITE_HULLMOD)
                                && !test.getVariant().hasHullMod(CANNIBAL_HULLMOD)
                                && !test.getVariant().hasHullMod(HYPERLOST_HULLMOD)
                                && !test.getVariant().hasHullMod(ALIEN_HULLMOD)
                                && !test.getVariant().hasHullMod(MYSTERY_NOTHING_HULLMOD)
                                && !test.getVariant().hasHullMod(MYSTERY_ROGUE_AI_HULLMOD)
                                && !test.getVariant().hasHullMod(MYSTERY_PLAGUE_HULLMOD)
                                && !test.getVariant().hasHullMod(MYSTERY_NANITE_HULLMOD)
                                && !test.getVariant().hasHullMod(MYSTERY_CANNIBAL_HULLMOD)
                                && !test.getVariant().hasHullMod(MYSTERY_HYPERLOST_HULLMOD)
                                && !test.getVariant().hasHullMod(MYSTERY_ALIEN_HULLMOD)
                                && !test.getVariant().hasHullMod(ANTI_AI_HULLMOD)) {
                            members.add(test);
                        }
                    }
                    FleetMemberAPI target = members.pick();

                    if (target != null) {

                        log.info("nanites from " + member + " infected " + target);

                        boolean known = true;
                        if (member.getVariant().hasHullMod(MYSTERY_NANITE_HULLMOD)) {
                            member.getVariant().removePermaMod(MYSTERY_NANITE_HULLMOD);
                            member.getVariant().addPermaMod(NANITE_HULLMOD);
                            known = false;
                        }

                        target.getVariant().addPermaMod(NANITE_HULLMOD);

                        GhostShipIntel intel = new GhostShipIntel(NANITE_TRANSMISSION, member, known, null);
                        intel.setNewHost(target);
                        Global.getSector().getIntelManager().addIntel(intel);
                    }

                }
            }

            campaignGhostShipTimers.put(id, timer);
        }
    }

    private void doCannibalCampaignStuff(FleetMemberAPI member, float amount) {

        String id = member.getId();
        if (!campaignGhostShipTimers.containsKey(id)) {
            campaignGhostShipTimers.put(id, 0f);
        } else {
            float timer = campaignGhostShipTimers.get(id);
            timer += (amount * 0.5f) + (Math.random() * amount);
            campaignGhostShipTimers.put(id, timer);

            if (timer >= CANNIBAL_ATTACK_TIMER) {
                campaignGhostShipTimers.remove(id);
                log.info("THE BILGE IS LEAKING! LEAKING CANNIBALS, THAT IS");
                boolean known = true;
                if (member.getVariant().hasHullMod(MYSTERY_CANNIBAL_HULLMOD)) {
                    member.getVariant().removePermaMod(MYSTERY_CANNIBAL_HULLMOD);
                    known = false;
                } else if (member.getVariant().hasHullMod(CANNIBAL_HULLMOD)) {
                    member.getVariant().removePermaMod(CANNIBAL_HULLMOD);
                }
                GhostShipIntel intel = new GhostShipIntel(CANNIBAL_ATTACK, member, known, null);
                Global.getSector().getIntelManager().addIntel(intel);
            }
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {

        String activity = getHostileActivityString(ship);
        int required = (int) ship.getHullSpec().getMinCrew();
        float current = 0f;
        float totalRequired = 0f;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null && fleet.getCargo() != null && fleet.getFleetData() != null) {
            current = fleet.getCargo().getMarines();
            for (FleetMemberAPI each : fleet.getFleetData().getMembersListCopy()) {
                if (each.getVariant().hasHullMod(ROGUE_AI_HULLMOD) || each.getVariant().hasHullMod(HYPERLOST_HULLMOD)
                        || each.getVariant().hasHullMod(ALIEN_HULLMOD) || each.getVariant().hasHullMod(PLAGUE_HULLMOD)
                        || each.getVariant().hasHullMod(NANITE_HULLMOD) || each.getVariant().hasHullMod(CANNIBAL_HULLMOD)
                        || each.getVariant().hasHullMod(MYSTERY_HULLMOD) || each.getVariant().hasHullMod(MYSTERY_NOTHING_HULLMOD)
                        || each.getVariant().hasHullMod(MYSTERY_ROGUE_AI_HULLMOD) || each.getVariant().hasHullMod(MYSTERY_HYPERLOST_HULLMOD)
                        || each.getVariant().hasHullMod(MYSTERY_ALIEN_HULLMOD) || each.getVariant().hasHullMod(MYSTERY_PLAGUE_HULLMOD)
                        || each.getVariant().hasHullMod(MYSTERY_NANITE_HULLMOD) || each.getVariant().hasHullMod(MYSTERY_CANNIBAL_HULLMOD)) {
                    totalRequired += each.getHullSpec().getMinCrew();
                    // mothballing doesn't help, you still need to post guards or the ghosts will take it
                }
            }
        }

        int actualPenalty = 0;
        if (totalRequired > 0) {
            float temp = current / totalRequired;
            if (temp > 1f) {
                temp = 1f;
            }
            temp *= MAX_CR_PENALTY;
            temp = MAX_CR_PENALTY - temp;
            actualPenalty = (int) temp;
        }
        String lossRate = "one or two per day";

        if (ship.getVariant().hasHullMod(HYPERLOST_HULLMOD)
                || ship.getVariant().hasHullMod(PLAGUE_HULLMOD)
                || ship.getVariant().hasHullMod(NANITE_HULLMOD)
                || ship.getVariant().hasHullMod(UNIQUE_HYPERLOST_HULLMOD)) {
            if (ship.getVariant().hasHullMod(PLAGUE_HULLMOD)) {
                lossRate = "one or two percent per day";
            }
            switch (index) {
                case 0:
                    return lossRate;
                case 1:
                    return (int) MAX_CR_PENALTY + "%";
                case 2:
                    return "" + required;
                case 3:
                    return "" + Math.round(current);
                case 4:
                    return "" + Math.round(totalRequired);
                case 5:
                    return actualPenalty + "%";
                default:
                    break;
            }
        } else if (ship.getVariant().hasHullMod(ALIEN_HULLMOD) || ship.getVariant().hasHullMod(CANNIBAL_HULLMOD)) {
            switch (index) {
                case 0:
                    return activity;
                case 1:
                    return (int) MAX_CR_PENALTY + "%";
                case 2:
                    return "" + required;
                case 3:
                    return "" + Math.round(current);
                case 4:
                    return "" + Math.round(totalRequired);
                case 5:
                    return actualPenalty + "%";
                default:
                    break;
            }

        } else {

            switch (index) {
                case 0:
                    return (int) MAX_CR_PENALTY + "%";
                case 1:
                    return "" + required;
                case 2:
                    return "" + Math.round(current);
                case 3:
                    return "" + Math.round(totalRequired);
                case 4:
                    return actualPenalty + "%";
                default:
                    break;
            }
        }
        return null;
    }
}
