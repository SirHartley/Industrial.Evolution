package indevo.exploration.fog;

import indevo.config.Setting;

// no engine imports, Setting loads this class in the headless settings checks
public enum FogMode implements Setting.Choice {
    OFF("Off", 2, 0f),
    HIGH("High Fidelity", 1, 0f),
    DEFAULT("Default Fidelity", 2, 0f),
    LOW("Low Fidelity", 4, 1f / 30f);

    public final String label;
    // fog draws at the gl viewport size / divisor and is stretched back up, it has no detail under about 40 units
    public final int divisor;
    // the solver steps once this much frame time has summed up, movers and patches still update every frame
    public final float minStep;

    FogMode(String label, int divisor, float minStep) {
        this.label = label;
        this.divisor = divisor;
        this.minStep = minStep;
    }

    public static FogMode current() {
        return Setting.FOG_WEATHER.choice();
    }

    @Override
    public String label() {
        return label;
    }
}
