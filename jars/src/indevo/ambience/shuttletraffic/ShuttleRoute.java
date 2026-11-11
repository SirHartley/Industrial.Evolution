package indevo.ambience.shuttletraffic;

public class ShuttleRoute {

    public enum Kind {
        SUPPLY(1f, 0f, SpeedClass.CIVIL),
        PIRATE_CARGO(1f, 0f, SpeedClass.CIVIL),
        SMUGGLER(1f, 0f, SpeedClass.CIVIL),
        DIPLOMAT(1f, 0f, SpeedClass.CIVIL),
        HUNT(1f, 0f, SpeedClass.CIVIL),
        MAINTENANCE(1f, 0f, SpeedClass.CIVIL),
        MARKET(0.9f, 0f, SpeedClass.CIVIL),
        MINING(0.8f, 8f, SpeedClass.CIVIL),
        SALVAGE(0.8f, 20f, SpeedClass.CIVIL),
        PICKET(0.8f, 10f, SpeedClass.MILITARY),
        CRUISE(0.7f, 15f, SpeedClass.CIVIL),
        TRAINING(0.7f, 20f, SpeedClass.MILITARY);

        // share of the traffic cap this kind may still spawn into
        public final float capShare;
        // seconds between flights for routes without a destination anchor
        public final float pause;
        // speed class most flights of this kind fly, pirate races and Remnant patrols fly military on cruise routes
        public final SpeedClass speed;

        Kind(float capShare, float pause, SpeedClass speed) {
            this.capShare = capShare;
            this.pause = pause;
            this.speed = speed;
        }
    }

    public final String key;
    public final Kind kind;
    public final ShuttleAnchor a;
    public final ShuttleAnchor b;
    public final float bendFrac;
    public ShuttleTarget target;
    public int max = 1;
    public int active = 0;
    public float timer = 0f;
    public float chancePerDay = 0f;
    public boolean pending = false;
    public boolean seen = true;

    public ShuttleRoute(String key, Kind kind, ShuttleAnchor a, ShuttleAnchor b, float bendFrac) {
        this.key = key;
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.bendFrac = bendFrac;
    }

    public boolean isRare() {
        return chancePerDay > 0f;
    }
}
