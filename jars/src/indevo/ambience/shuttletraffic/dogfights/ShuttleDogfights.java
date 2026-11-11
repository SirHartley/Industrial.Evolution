package indevo.ambience.shuttletraffic.dogfights;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.flights.EscortFlights;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// npc battles in the player's system the player can see; nothing in a battle changes, the fighters only follow it
public class ShuttleDogfights {

    public static final int MAX_BATTLES = 2;
    private static final int MAX_GROUPS = 4;
    private static final int SHIPS_PER_GROUP = 3;
    private static final int FALLBACK_WINGS = 4;
    private static final float REFUGE_RANGE = 1500f;
    private static final Color LASER_COLOR = new Color(255, 120, 120);
    private static final float BLAST_MIN = 25f;
    private static final float BLAST_MAX = 45f;

    private final ShuttleTraffic traffic;
    private final List<Watch> watches = new ArrayList<>();
    // battles that were watched or failed the round rule at first sight, never picked again
    private final Set<BattleAPI> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, WingType> wingTypes = new HashMap<>();

    private final class Watch implements DogfightHost {
        final BattleAPI battle;
        final ShuttleDogfight fight;
        final Map<CampaignFleetAPI, DogfightFleet> fleets = new IdentityHashMap<>();

        Watch(BattleAPI battle) {
            this.battle = battle;
            this.fight = new ShuttleDogfight(this, traffic.getRandom());
        }

        @Override
        public void particle(Vector2f loc, Vector2f vel, float size, float brightness, float duration, Color color) {
            LocationAPI location = traffic.getLocation();
            if (location != null) location.addHitParticle(new Vector2f(loc), new Vector2f(vel), size, brightness, duration, color);
        }

        @Override
        public Refuge findRefuge(Dogfighter fighter) {
            FactionAPI faction = null;
            for (Map.Entry<CampaignFleetAPI, DogfightFleet> entry : fleets.entrySet()) {
                if (entry.getValue() == fighter.home) faction = entry.getKey().getFaction();
            }

            ShuttleAnchor anchor = traffic.getNetwork().findRefuge(null, faction, fighter.pos, traffic.getLocation());
            if (anchor == null || Misc.getDistance(anchor.getLocation(), fighter.pos) > REFUGE_RANGE) return null;
            return new Refuge(anchor.getLocation(), anchor.getRadius());
        }

        // new fleets join their side; a side that carries no wings flies its factions' known fighters
        void sync() {
            add(battle.getSideOne(), 0);
            add(battle.getSideTwo(), 1);
            refresh(true);

            Vector2f center = battle.computeCenterOfMass();
            float radius = 0f;
            int[] ships = new int[2];
            for (Map.Entry<CampaignFleetAPI, DogfightFleet> entry : fleets.entrySet()) {
                DogfightFleet fleet = entry.getValue();
                if (!fleet.alive) continue;
                radius = Math.max(radius, Misc.getDistance(center, fleet.loc) + fleet.radius);
                ships[fleet.side] += entry.getKey().getNumShips();
            }
            fight.setCenter(center.x, center.y, radius);

            for (int side = 0; side < 2; side++) {
                fight.setWantedGroups(side, hasWings(side) ? Math.max(1, Math.min(MAX_GROUPS, ships[side] / SHIPS_PER_GROUP)) : 0);
            }
            fight.setLaunching(isVisible(battle) && getRoundRate(battle) <= 1f);
        }

        void add(List<CampaignFleetAPI> side, int index) {
            boolean added = false;
            for (CampaignFleetAPI fleet : side) {
                if (fleets.containsKey(fleet)) continue;

                DogfightFleet entry = new DogfightFleet(index, new ArrayList<>());
                for (String wingId : EscortFlights.getFittedWings(fleet)) {
                    WingType type = getWingType(wingId, fleet.getFaction());
                    if (type != null && !entry.wings.contains(type)) entry.wings.add(type);
                }
                copy(fleet, entry);
                fleets.put(fleet, entry);
                fight.addFleet(entry);
                added = true;
            }
            if (!added || hasWings(index)) return;

            Random random = traffic.getRandom();
            for (Map.Entry<CampaignFleetAPI, DogfightFleet> entry : fleets.entrySet()) {
                if (entry.getValue().side != index) continue;

                List<String> known = new ArrayList<>(entry.getKey().getFaction().getKnownFighters());
                Collections.shuffle(known, random);
                List<WingType> wings = entry.getValue().wings;
                for (String wingId : known) {
                    if (wings.size() >= FALLBACK_WINGS) break;
                    WingType type = getWingType(wingId, entry.getKey().getFaction());
                    if (type != null && !wings.contains(type)) wings.add(type);
                }
            }
        }

        boolean hasWings(int side) {
            for (DogfightFleet fleet : fleets.values()) if (fleet.side == side && !fleet.wings.isEmpty()) return true;
            return false;
        }

        // during the battle a fleet counts while it sits in it; after the end while it is in the system
        void refresh(boolean inBattle) {
            LocationAPI location = traffic.getLocation();
            for (Map.Entry<CampaignFleetAPI, DogfightFleet> entry : fleets.entrySet()) {
                CampaignFleetAPI fleet = entry.getKey();
                entry.getValue().alive = fleet.getContainingLocation() == location && (fleet.isStationMode() || fleet.isAlive())
                        && fleet.getFleetData().getNumMembers() > 0 && (!inBattle || fleet.getBattle() == battle);
            }
        }

        void end(int winner) {
            refresh(false);
            fight.end(winner);
        }

        // the side with fleets left and more fleet points
        int guessWinner() {
            float[] points = new float[2];
            boolean[] alive = new boolean[2];
            for (Map.Entry<CampaignFleetAPI, DogfightFleet> entry : fleets.entrySet()) {
                CampaignFleetAPI fleet = entry.getKey();
                if (fleet.getContainingLocation() != traffic.getLocation() || fleet.getFleetData().getNumMembers() <= 0) continue;
                int side = entry.getValue().side;
                alive[side] = true;
                points[side] += fleet.getFleetPoints();
            }
            if (alive[0] != alive[1]) return alive[0] ? 0 : 1;
            if (!alive[0]) return -1;
            return points[0] >= points[1] ? 0 : 1;
        }
    }

    public ShuttleDogfights(ShuttleTraffic traffic) {
        this.traffic = traffic;
    }

    public void clear() {
        watches.clear();
        seen.clear();
    }

    // after every fleet scan
    public void update() {
        List<BattleAPI> battles = traffic.getFleetScan().getBattles();
        seen.retainAll(battles);

        for (Watch watch : watches) {
            if (watch.fight.isEnded()) {
                watch.refresh(false);
                continue;
            }

            BattleAPI battle = watch.battle;
            if (battle.isDone() || battle.isPlayerInvolved() || !battles.contains(battle)) watch.end(watch.guessWinner());
            else watch.sync();
        }

        for (BattleAPI battle : battles) {
            if (watches.size() >= MAX_BATTLES) break;
            if (seen.contains(battle) || battle.isDone() || battle.isPlayerInvolved() || !isVisible(battle)) continue;

            seen.add(battle);
            if (getRoundRate(battle) > 1f) continue;

            Watch watch = new Watch(battle);
            watch.sync();
            if (watch.hasWings(0) || watch.hasWings(1)) watches.add(watch);
        }
    }

    public void advance(float amount) {
        for (Watch watch : watches) {
            for (Map.Entry<CampaignFleetAPI, DogfightFleet> entry : watch.fleets.entrySet()) copy(entry.getKey(), entry.getValue());
            watch.fight.advance(amount);
        }
        watches.removeIf(watch -> watch.fight.isOver());
    }

    // reportBattleOccurred after every autoresolve round: the fleet snapshot is from the round start, so the lost members are this round's
    public void onRound(BattleAPI battle) {
        Watch watch = null;
        for (Watch entry : watches) if (entry.battle == battle && !entry.fight.isEnded()) watch = entry;
        if (watch == null) return;

        Random random = traffic.getRandom();
        LocationAPI location = traffic.getLocation();
        float[] lostPoints = new float[2];
        for (CampaignFleetAPI fleet : battle.getBothSides()) {
            DogfightFleet entry = watch.fleets.get(fleet);
            if (entry == null || fleet.getFleetData().getSnapshot() == null) continue;

            boolean survives = fleet.getFleetData().getNumMembers() > 0;
            for (FleetMemberAPI member : Misc.getSnapshotMembersLost(fleet)) {
                if (member.isFighterWing()) continue;
                lostPoints[entry.side] += member.getFleetPointCost();

                Vector2f point = new Vector2f(entry.loc.x + (random.nextFloat() - 0.5f) * entry.radius, entry.loc.y + (random.nextFloat() - 0.5f) * entry.radius);
                for (int i = 0; i < 3; i++) {
                    float size = BLAST_MIN + random.nextFloat() * (BLAST_MAX - BLAST_MIN);
                    location.addHitParticle(new Vector2f(point), new Vector2f(entry.vel), size, 1f, 0.5f + random.nextFloat() * 0.4f, i == 0 ? Color.WHITE : new Color(255, 170, 90));
                }

                // a destroyed fleet fires its pods through reportFleetDespawned
                if (survives && !Misc.isAutomated(member) && member.getHullSpec().getMinCrew() > 0f) {
                    traffic.getFlights().getPods().launchFromWreck(point, getAwayHeading(watch, entry, point), fleet.getFaction());
                }
            }
        }

        if (lostPoints[0] > lostPoints[1]) watch.fight.loseGroup(0);
        else if (lostPoints[1] > lostPoints[0]) watch.fight.loseGroup(1);
    }

    private float getAwayHeading(Watch watch, DogfightFleet own, Vector2f point) {
        Vector2f sum = new Vector2f();
        int count = 0;
        for (DogfightFleet fleet : watch.fleets.values()) {
            if (fleet.side == own.side || !fleet.alive) continue;
            Vector2f.add(sum, fleet.loc, sum);
            count++;
        }
        if (count == 0) return traffic.getRandom().nextFloat() * 360f;
        return Misc.getAngleInDegrees(new Vector2f(sum.x / count, sum.y / count), point);
    }

    // reportBattleFinished: the primary winner's side flies its loop
    public void onFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        for (Watch watch : watches) {
            if (watch.battle != battle || watch.fight.isEnded()) continue;

            DogfightFleet winner = primaryWinner != null ? watch.fleets.get(primaryWinner) : null;
            watch.end(winner != null ? winner.side : watch.guessWinner());
        }
    }

    public List<ShuttleDogfight> getFights() {
        List<ShuttleDogfight> fights = new ArrayList<>(watches.size());
        for (Watch watch : watches) fights.add(watch.fight);
        return fights;
    }

    public boolean isEmpty() {
        return watches.isEmpty();
    }

    private static void copy(CampaignFleetAPI fleet, DogfightFleet entry) {
        entry.loc.set(fleet.getLocation());
        entry.vel.set(fleet.getVelocity());
        entry.radius = fleet.getRadius();
    }

    private static boolean isVisible(BattleAPI battle) {
        for (CampaignFleetAPI fleet : battle.getBothSides()) {
            if (fleet.getVisibilityLevelToPlayerFleet() != SectorEntityToken.VisibilityLevel.NONE) return true;
        }
        return false;
    }

    // autoresolve rounds per day, as Battle.advance: max / (2 x min) fleet points clamped 1 to 5, x0.25 with a station
    static float getRoundRate(BattleAPI battle) {
        float one = 0f;
        float two = 0f;
        for (CampaignFleetAPI fleet : battle.getSideOne()) one += fleet.getFleetPoints();
        for (CampaignFleetAPI fleet : battle.getSideTwo()) two += fleet.getFleetPoints();
        return getRoundRate(one, two, battle.isStationInvolved());
    }

    public static float getRoundRate(float one, float two, boolean station) {
        float rate = 1f;
        if (one >= 1f && two >= 1f) rate = Math.max(1f, Math.min(5f, Math.max(one, two) / Math.min(one * 2f, two * 2f)));
        return station ? rate * 0.25f : rate;
    }

    private WingType getWingType(String wingId, FactionAPI faction) {
        String key = (ShuttleHullPool.isRemnant(faction) ? "remnant|" : "") + wingId;
        if (wingTypes.containsKey(key)) return wingTypes.get(key);

        WingType type = createWingType(wingId, faction);
        wingTypes.put(key, type);
        return type;
    }

    // energy fighters fire lasers, ballistic or unarmed ones guns, missile only ones only torpedoes
    private static WingType createWingType(String wingId, FactionAPI faction) {
        FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wingId);
        ShuttleHull hull = ShuttleHullPool.getWingHull(wingId, faction);
        if (spec == null || hull == null) return null;

        WingType.Role role = switch (spec.getRole()) {
            case BOMBER -> WingType.Role.STRIKE;
            case SUPPORT -> WingType.Role.SUPPORT;
            default -> WingType.Role.ESCORT;
        };

        boolean guns = false;
        boolean lasers = false;
        boolean torpedoes = false;
        Color bolt = null;
        ShipVariantAPI variant = spec.getVariant();
        for (String slot : variant.getFittedWeaponSlots()) {
            WeaponSpecAPI weapon = variant.getWeaponSpec(slot);
            if (weapon == null) continue;

            switch (weapon.getType()) {
                case ENERGY -> {
                    lasers = true;
                    if (bolt == null) bolt = weapon.getGlowColor();
                }
                case MISSILE -> torpedoes = true;
                case BALLISTIC, HYBRID, COMPOSITE, UNIVERSAL, SYNERGY -> guns = true;
                default -> {
                }
            }
        }
        if (!guns && !lasers && !torpedoes) guns = true;

        return new WingType(hull, role, guns, lasers, torpedoes, bolt != null ? bolt : LASER_COLOR);
    }
}
