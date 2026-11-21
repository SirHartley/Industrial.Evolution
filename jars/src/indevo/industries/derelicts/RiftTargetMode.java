package indevo.industries.derelicts;

public enum RiftTargetMode {
    COLONY(0.9f),
    CORE(0.8f),
    FRINGE(1f),
    RANDOM(1.3f);

    public final float reachMult;

    RiftTargetMode(float reachMult) {
        this.reachMult = reachMult;
    }

    public String getPanelOptionId() {
        return "IndEvo_RiftTarget_" + name() + "_panel";
    }

    public String getInitOptionId() {
        return "IndEvo_RiftTarget_" + name() + "_init";
    }
}
