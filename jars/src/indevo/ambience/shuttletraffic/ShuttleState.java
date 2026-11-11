package indevo.ambience.shuttletraffic;

public enum ShuttleState {
    CRUISE(1f, 1f),
    HOLDING(1f, 1f),
    FLEEING(1.4f, 2f),
    FLEEING_HOLDING(1.4f, 2f),
    ESCAPING(1.4f, 2f),
    STRANDED(0.6f, 1f),
    SCREENING(0.6f, 1f),
    DOWN(1f, 1f);

    public final float pace;
    public final float turnMult;

    ShuttleState(float pace, float turnMult) {
        this.pace = pace;
        this.turnMult = turnMult;
    }

    public boolean isFleeing() {
        return this == FLEEING || this == FLEEING_HOLDING || this == ESCAPING;
    }

    public boolean isHolding() {
        return this == HOLDING || this == FLEEING_HOLDING;
    }

    public boolean isStranded() {
        return this == ESCAPING || this == STRANDED;
    }

    // a station pad is busy, circle until it frees
    public ShuttleState hold() {
        return switch (this) {
            case CRUISE -> HOLDING;
            case FLEEING -> FLEEING_HOLDING;
            default -> throw illegal("hold");
        };
    }

    // a new path to an anchor ends any holding lap
    public ShuttleState redirect() {
        return switch (this) {
            case HOLDING -> CRUISE;
            case FLEEING_HOLDING -> FLEEING;
            case CRUISE, FLEEING -> this;
            default -> throw illegal("redirect");
        };
    }

    public ShuttleState flee(boolean refuge) {
        if (this == DOWN || isStranded()) throw illegal("flee");
        return refuge ? FLEEING : ESCAPING;
    }

    public ShuttleState screen() {
        return switch (this) {
            case CRUISE, HOLDING, SCREENING -> SCREENING;
            default -> throw illegal("screen");
        };
    }

    public ShuttleState land() {
        if (this != CRUISE && this != FLEEING) throw illegal("land");
        return DOWN;
    }

    public ShuttleState strand() {
        if (!isStranded()) throw illegal("strand");
        return STRANDED;
    }

    // screening, stranded and landed craft fly home calmly
    public ShuttleState calm() {
        return switch (this) {
            case SCREENING, STRANDED, DOWN -> CRUISE;
            default -> throw illegal("calm");
        };
    }

    private IllegalStateException illegal(String change) {
        return new IllegalStateException("Shuttle traffic: no " + change + " from " + this);
    }
}
