package indevo.ambience.shuttletraffic;

public record ShuttleLook(boolean glow, float trailAlpha, float trailWidth, int trailPoints, float flicker, float tumbleRate) {

    public static final ShuttleLook CRAFT = new ShuttleLook(true, 1f, 3f, ShuttleTrail.TRAIL_POINTS, 0.2f, 0f);
    public static final ShuttleLook SMUGGLER = new ShuttleLook(false, 0.3f, 3f, ShuttleTrail.TRAIL_POINTS, 0.2f, 0f);
    public static final ShuttleLook HUNT = new ShuttleLook(true, 0.5f, 3f, ShuttleTrail.TRAIL_POINTS, 0.6f, 0f);

    // tumbleRate in degrees per second at ejection, it decays to 0 over the boost
    public static ShuttleLook pod(float tumbleRate) {
        return new ShuttleLook(true, 1f, 1.5f, 8, 0.2f, tumbleRate);
    }
}
