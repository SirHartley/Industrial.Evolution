package indevo.skillshot;

public enum GuideLineStyle {

    SOLID,
    DASHED,
    DOTTED;

    public float getSegmentPx() {
        switch (this) {
            case DASHED:
                return SkillshotSettings.GUIDE_LINE_DASH_PX;
            case DOTTED:
                return SkillshotSettings.GUIDE_LINE_DOT_PX;
            default:
                return 0f;
        }
    }

    public float getGapPx() {
        switch (this) {
            case DASHED:
                return SkillshotSettings.GUIDE_LINE_DASH_GAP_PX;
            case DOTTED:
                return SkillshotSettings.GUIDE_LINE_DOT_GAP_PX;
            default:
                return 0f;
        }
    }
}
