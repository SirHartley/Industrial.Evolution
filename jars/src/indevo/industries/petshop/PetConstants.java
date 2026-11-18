package indevo.industries.petshop;

public final class PetConstants {
    public static final float UNSETTLED_DAYS = 30f;
    public static final float SHELL_SHOCKED_DAYS = 7f;
    public static final float SLEEPY_AGE_FRACTION = 0.8f;
    public static final float FLAGSHIP_LINE_WEIGHT = 3f;
    public static final float LINE_DAYS_SPREAD = 0.5f;
    public static final float HAPPY_DAYS = 3f;
    public static final int PETTING_CLICKS = 5;
    public static final float REHOME_FEE = 2000f;
    public static final int NAME_MAX_CHARS = 40;

    // bond days for hearts 1 to 5; time aboard gives the first three, wants and shared events the rest
    public static final float[] HEART_DAYS = {14f, 60f, 180f, 300f, 480f};
    public static final int MAX_HEARTS = 5;
    public static final int TIME_HEARTS = 3;
    public static final float SUPPLIES_HEART_RATE = 0.25f;
    public static final int MASCOT_HEARTS = 1;
    public static final float HAMSTER_MASCOT_MULT = 2f;
    public static final int NEST_HEARTS = 4;
    public static final float NEST_MULT = 1.3f;
    public static final float NEST_REBUILD_DAYS = 120f;
    public static final float NEST_UNSETTLED_DAYS = 90f;
    public static final float POD_CHANCE_PER_HEART = 0.1f;

    public static final int MAX_FAVOURITES = 3;
    public static final float FAVOURITE_LINE_WEIGHT = 3f;
    // one want open in the fleet; each favourite waits WANT_COOLDOWN_DAYS after its last, the fleet WANT_GAP_DAYS after any
    public static final float WANT_DAYS = 30f;
    public static final float WANT_COOLDOWN_DAYS = 60f;
    public static final float WANT_GAP_DAYS = 5f;
    public static final float WANT_BOND_DAYS = 30f;
    public static final float EVENT_BOND_DAYS = 3f;
    public static final int SULK_LAPSES = 3;

    private PetConstants() {
    }
}
