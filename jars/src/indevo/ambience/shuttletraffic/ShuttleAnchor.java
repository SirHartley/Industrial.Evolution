package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lwjgl.util.vector.Vector2f;

public class ShuttleAnchor {

    public enum Type {
        PLANET,
        STATION,
        FLEET
    }

    public enum Role {
        MARKET,
        DECIV,
        REMNANT,
        TARGET,
        FLEET
    }

    public static final float PLANET_PAD_DEPTH = 0.6f;
    public static final float STATION_PAD_DEPTH = 0.15f;
    public static final float STATION_DOCK_LENGTH = 60f;
    public static final float HOLD_RADIUS = 120f;
    public static final float FLEET_END_LENGTH = 30f;

    public final SectorEntityToken entity;
    public final Type type;
    public final Role role;
    public MarketAPI market;
    public FactionAPI faction;
    public boolean closed = false;
    // an IE meteor swarm is near, set every scan
    public boolean swarmed = false;
    public boolean seen = true;
    public float padBusyUntil = 0f;
    public boolean threatened = false;
    public boolean overrun = false;
    public float lastThreatDay = -100f;
    // traffic multiplier from port, commerce, stability and raids, set in the network rebuild
    public float density = 1f;
    // how busy the market feels: size, port, military and station on top of density, set in the network rebuild
    public float liveliness = 1f;
    // the player is close enough to see its local flights, set every scan
    public boolean near = false;
    // world velocity from the last two scans: planets and stations move on orbits, and a fleet parked at one moves with it
    public final Vector2f velocity = new Vector2f();
    private final Vector2f lastLoc = new Vector2f();
    private boolean lastKnown = false;
    // body cruises and training flights fly out to and lap, picked once and kept while it stays in range
    public SectorEntityToken sight;

    public ShuttleAnchor(SectorEntityToken entity, Type type, Role role, MarketAPI market, FactionAPI faction) {
        this.entity = entity;
        this.type = type;
        this.role = role;
        this.market = market;
        this.faction = faction;
    }

    // craft fly to and from a fleet like a station without a pad
    public static ShuttleAnchor forFleet(CampaignFleetAPI fleet) {
        return new ShuttleAnchor(fleet, Type.FLEET, Role.FLEET, null, fleet.getFaction());
    }

    public void updateMotion(float seconds) {
        Vector2f loc = entity.getLocation();
        if (lastKnown && seconds > 0f) velocity.set((loc.x - lastLoc.x) / seconds, (loc.y - lastLoc.y) / seconds);
        lastLoc.set(loc);
        lastKnown = true;
    }

    public Shuttle.End getEnd() {
        return switch (type) {
            case PLANET -> Shuttle.End.PLANET;
            case STATION -> Shuttle.End.STATION;
            case FLEET -> Shuttle.End.FLEET;
        };
    }

    public Vector2f getLocation() {
        return entity.getLocation();
    }

    public float getRadius() {
        return entity.getRadius();
    }

    public boolean hasPad() {
        return type == Type.STATION;
    }

    public boolean isPadBusy(float time) {
        return hasPad() && padBusyUntil > time;
    }

    public float getPadDepth() {
        return switch (type) {
            case PLANET -> PLANET_PAD_DEPTH;
            case STATION -> STATION_PAD_DEPTH;
            case FLEET -> 0f;
        };
    }

    public float getLandingLength() {
        return switch (type) {
            case PLANET -> getRadius() * 1.2f;
            case STATION -> STATION_DOCK_LENGTH;
            case FLEET -> FLEET_END_LENGTH;
        };
    }

    public boolean isUsable(LocationAPI location) {
        return seen && !closed && !swarmed && entity.isAlive() && entity.getContainingLocation() == location;
    }

    public boolean isSafe(float days) {
        return !threatened && days - lastThreatDay >= 1f;
    }
}
