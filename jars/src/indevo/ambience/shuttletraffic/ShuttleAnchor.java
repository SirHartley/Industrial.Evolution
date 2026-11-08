package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lwjgl.util.vector.Vector2f;

public class ShuttleAnchor {

    public enum Type {
        PLANET,
        STATION
    }

    public static final float PLANET_PAD_DEPTH = 0.6f;
    public static final float STATION_PAD_DEPTH = 0.15f;
    public static final float STATION_DOCK_LENGTH = 60f;
    public static final float HOLD_RADIUS = 50f;

    public final SectorEntityToken entity;
    public final Type type;
    public MarketAPI market;
    public FactionAPI faction;
    public boolean closed = false;
    public boolean seen = true;
    public float padBusyUntil = 0f;

    public ShuttleAnchor(SectorEntityToken entity, Type type, MarketAPI market, FactionAPI faction) {
        this.entity = entity;
        this.type = type;
        this.market = market;
        this.faction = faction;
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
        return type == Type.PLANET ? PLANET_PAD_DEPTH : STATION_PAD_DEPTH;
    }

    public float getLandingLength() {
        return type == Type.PLANET ? getRadius() * 1.2f : STATION_DOCK_LENGTH;
    }

    public boolean isUsable(LocationAPI location) {
        return seen && !closed && entity.isAlive() && entity.getContainingLocation() == location;
    }
}
