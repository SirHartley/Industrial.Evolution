package indevo.ambience.shuttletraffic.dogfights;

import indevo.ambience.shuttletraffic.ShuttleHull;

import java.awt.Color;

// one fighter wing as a dogfight craft: hull look, role and what it shoots
public record WingType(ShuttleHull hull, Role role, boolean guns, boolean lasers, boolean torpedoes, Color boltColor) {

    public enum Role {
        STRIKE,
        ESCORT,
        SUPPORT
    }
}
