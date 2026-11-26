package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.SectorEntityToken;

// public contract for other mods, never rename the tags: an entity without a market flies like a market of this size, as a destination only
public enum ShuttleTrafficTag {
    HIGH("IndEvo_traffic_high", 6),
    MED("IndEvo_traffic_med", 4),
    LOW("IndEvo_traffic_low", 3);

    public final String tag;
    public final int size;

    ShuttleTrafficTag(String tag, int size) {
        this.tag = tag;
        this.size = size;
    }

    // the highest tier wins when an entity carries several
    public static ShuttleTrafficTag get(SectorEntityToken entity) {
        for (ShuttleTrafficTag tier : values()) {
            if (entity.hasTag(tier.tag)) return tier;
        }
        return null;
    }

    public float getLiveliness() {
        return ShuttleNetwork.getLiveliness(size, 1f, false, false);
    }
}
