package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import java.util.Random;

public class ShuttleRoute {

    public enum Kind {
        SUPPLY(ShuttleBudget.Group.LOCAL, 1, 0f, SpeedClass.CIVIL),
        HOP(ShuttleBudget.Group.LOCAL, 1, 0f, SpeedClass.CIVIL),
        PIRATE_CARGO(ShuttleBudget.Group.LANES, 1, 0f, SpeedClass.CIVIL),
        MARKET(ShuttleBudget.Group.LANES, 1, 0f, SpeedClass.CIVIL),
        COURIER(ShuttleBudget.Group.LANES, 1, 0f, SpeedClass.MILITARY),
        PILGRIM(ShuttleBudget.Group.LANES, 1, 0f, SpeedClass.CIVIL),
        SMUGGLER(ShuttleBudget.Group.RARE, 1, 0f, SpeedClass.CIVIL),
        DARK(ShuttleBudget.Group.RARE, 1, 0f, SpeedClass.CIVIL),
        DIPLOMAT(ShuttleBudget.Group.RARE, 1, 0f, SpeedClass.CIVIL),
        HUNT(ShuttleBudget.Group.RARE, 1, 0f, SpeedClass.CIVIL),
        MAINTENANCE(ShuttleBudget.Group.RARE, 1, 0f, SpeedClass.CIVIL),
        MINING(ShuttleBudget.Group.LOCAL, 1, 8f, SpeedClass.CIVIL),
        SALVAGE(ShuttleBudget.Group.LOCAL, 1, 20f, SpeedClass.CIVIL),
        PICKET(ShuttleBudget.Group.LOCAL, 2, 10f, SpeedClass.MILITARY),
        CRUISE(ShuttleBudget.Group.LOCAL, 2, 15f, SpeedClass.CIVIL),
        TRAINING(ShuttleBudget.Group.LOCAL, 3, 20f, SpeedClass.MILITARY),
        PARADE(ShuttleBudget.Group.LOCAL, 3, 20f, SpeedClass.MILITARY),
        TOW(ShuttleBudget.Group.LOCAL, 3, 30f, SpeedClass.CIVIL),
        FLEET_SERVICE(ShuttleBudget.Group.FLEET, 1, 0f, SpeedClass.SERVICE),
        TRADE(ShuttleBudget.Group.FLEET, 1, 0f, SpeedClass.SERVICE),
        SURVEY(ShuttleBudget.Group.FLEET, 1, 3f, SpeedClass.SERVICE);

        public final ShuttleBudget.Group group;
        // typical craft per launch, formations and races count each craft against the cap
        public final int craftPerFlight;
        // seconds between flights for routes without a destination anchor
        public final float pause;
        // speed class most flights of this kind fly, pirate races and Remnant patrols fly military on cruise routes
        public final SpeedClass speed;

        Kind(ShuttleBudget.Group group, int craftPerFlight, float pause, SpeedClass speed) {
            this.group = group;
            this.craftPerFlight = craftPerFlight;
            this.pause = pause;
            this.speed = speed;
        }
    }

    // lane bend as a share of the chord, either side
    public static final float LANE_BEND = 0.25f;

    public final String key;
    public final Kind kind;
    public final ShuttleAnchor a;
    public final ShuttleAnchor b;
    public final float bendFrac;
    public ShuttleTarget target;
    // fleet tied routes: the fleet the craft fly to and from, b is its fleet anchor
    public CampaignFleetAPI fleet;
    // flights the route asks for, set in the network rebuild
    public int demand = 1;
    // flights in the air the budget allows
    public int max = 1;
    public int active = 0;
    public float timer = 0f;
    public float chancePerDay = 0f;
    public boolean pending = false;
    public boolean seen = true;
    // market lanes: day of the next convoy, negative until the first roll
    public float nextConvoyDay = -1f;

    public ShuttleRoute(String key, Kind kind, ShuttleAnchor a, ShuttleAnchor b, float bendFrac) {
        this.key = key;
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.bendFrac = bendFrac;
    }

    public static float rollBend(Random random) {
        return (random.nextFloat() - 0.5f) * 2f * LANE_BEND;
    }

    public boolean isRare() {
        return chancePerDay > 0f;
    }
}
