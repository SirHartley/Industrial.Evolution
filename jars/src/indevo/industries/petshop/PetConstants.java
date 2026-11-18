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
    // hamsters count twice, for the mascot effect and for talents
    public static final float HAMSTER_MULT = 2f;
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

    public static final int VETERAN_FIGHTS = 10;
    public static final int NAVIGATOR_JUMPS = 50;
    public static final int PAMPERED_WANTS = 10;
    public static final int TREASURE_HUNTER_FINDS = 5;
    public static final int ADMIRAL_DAYS = 365;
    public static final int GLOBETROTTER_MARKETS = 25;
    public static final int STARGAZER_DAYS = 120;

    // lead Scavenger in a salvaged wreck: a find worth SCAVENGER_FIND_VALUE credits x its value, one colony item roll
    public static final float SCAVENGER_FIND_CHANCE = 0.25f;
    public static final int SCAVENGER_FIND_VALUE = 1000;
    public static final String SCAVENGER_RARE_GROUP = "rare_tech_low";

    // one talent switches on per heart, best first, all at MAX_HEARTS; at READ_HEARTS every talent is read
    public static final int READ_HEARTS = 3;
    public static final float TWO_TALENTS_CHANCE = 0.5f;
    // a rare species may bring one signature talent besides the others
    public static final float SIGNATURE_CHANCE = 0.2f;

    // husbandry by pace: fast, medium, slow; the hamster rolls as fast
    public static final float[] SUM_CHANCE = {0.10f, 0.25f, 0.40f};
    public static final float SUM_CHANCE_PER_HEART = 0.03f;
    public static final float[] MUTATION_RATE = {0.08f, 0.16f, 0.24f};
    public static final float IRRADIATED_MUTATION_MULT = 2f;
    public static final float ALPHA_MUTATION_MULT = 1.5f;
    // a new trait from the mutation rate: a mutation or a talent half and half, a talent one time in four a signature
    public static final float NEW_MUTATION_CHANCE = 0.5f;
    public static final float NEW_SIGNATURE_CHANCE = 0.25f;
    public static final float MALFORMED_PER_INBREEDING = 1.5f;
    public static final float MALFORMED_MIN = 0.05f;
    public static final float HAMSTER_MALFORMED = 0.5f;
    public static final int MAX_BROOD = 3;

    private PetConstants() {
    }
}
