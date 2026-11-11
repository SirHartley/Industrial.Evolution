package indevo.ambience.shuttletraffic;

// how one faction flies: a row of shuttle_factions.csv
public record ShuttleProfile(String id, boolean pirate, boolean dark, float laneDiscipline, float jitter, float speedSpread, float weave, float formation,
                             boolean queue, float nerve, float lanes, float cruises, float pickets, float convoys, float speed, float flicker) {

    public static final ShuttleProfile DEFAULT = new ShuttleProfile("default", false, false, 1f, 1f, 0.15f, 0f, 1f, true, 1f, 1f, 1f, 1f, 1f, 1f, 1f);
}
