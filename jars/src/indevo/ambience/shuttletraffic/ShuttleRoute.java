package indevo.ambience.shuttletraffic;

public class ShuttleRoute {

    public enum Kind {
        SUPPLY(1f),
        MARKET(0.9f);

        // share of the traffic cap this kind may still spawn into
        public final float capShare;

        Kind(float capShare) {
            this.capShare = capShare;
        }
    }

    public final String key;
    public final Kind kind;
    public final ShuttleAnchor a;
    public final ShuttleAnchor b;
    public final float bendFrac;
    public int max = 1;
    public int active = 0;
    public float timer = 0f;
    public boolean seen = true;

    public ShuttleRoute(String key, Kind kind, ShuttleAnchor a, ShuttleAnchor b, float bendFrac) {
        this.key = key;
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.bendFrac = bendFrac;
    }
}
