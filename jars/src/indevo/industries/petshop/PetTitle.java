package indevo.industries.petshop;

// earned once from what a pet lived through; Pet.titles saves the constant name, so never rename one
public enum PetTitle {
    VETERAN("Veteran", "Fought in %s battles.", PetConstants.VETERAN_FIGHTS),
    NAVIGATOR("Navigator", "Made %s jumps with the fleet.", PetConstants.NAVIGATOR_JUMPS),
    OLD_TIMER("Old Timer", "Lived past the natural lifespan of its species.", 0),
    SURVIVOR("Survivor", "Escaped a lost ship in an escape pod.", 0),
    BELOVED("Beloved", "Reached all five hearts.", 0),
    PAMPERED("Pampered", "Had %s wants answered.", PetConstants.PAMPERED_WANTS);

    public final String label;
    public final String reason;
    public final int count;

    PetTitle(String label, String reason, int count) {
        this.label = label;
        this.reason = reason;
        this.count = count;
    }

    public String getReason() {
        return count > 0 ? String.format(reason, count) : reason;
    }
}
