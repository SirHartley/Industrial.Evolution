package indevo.ambience.shuttletraffic;

// trailSeconds: the trail shows where the rear was during that time, so its length is speed * trailSeconds
public record ShuttleLook(boolean glow, float trailAlpha, float trailWidth, float trailSeconds, float flicker, float tumbleRate) {

    public static final float CRAFT_TRAIL_SECONDS = 1.5f;
    public static final float POD_TRAIL_SECONDS = 0.8f;
    public static final float SERVICE_TRAIL_SECONDS = 1f;

    public static final ShuttleLook CRAFT = new ShuttleLook(true, 1f, 3f, CRAFT_TRAIL_SECONDS, 0.2f, 0f);
    public static final ShuttleLook SMUGGLER = new ShuttleLook(false, 0.3f, 3f, CRAFT_TRAIL_SECONDS, 0.2f, 0f);
    // quick hops: shorter trail, steadier glow
    public static final ShuttleLook SERVICE = new ShuttleLook(true, 1f, 3f, SERVICE_TRAIL_SECONDS, 0.05f, 0f);
    public static final ShuttleLook HUNT = new ShuttleLook(true, 0.5f, 3f, CRAFT_TRAIL_SECONDS, 0.6f, 0f);

    // tumbleRate in degrees per second at ejection, it decays to 0 over the boost
    public static ShuttleLook pod(float tumbleRate) {
        return new ShuttleLook(true, 1f, 1.5f, POD_TRAIL_SECONDS, 0.2f, tumbleRate);
    }
}
