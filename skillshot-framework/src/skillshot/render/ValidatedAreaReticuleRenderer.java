package skillshot.render;

import skillshot.SkillshotSettings;

/**
 * An {@link AreaReticuleRenderer} whose aim point is gated by a {@link PositionValidator}. While the
 * validator rejects the cursor position the whole reticule turns
 * {@link SkillshotSettings#INVALID_COLOR} and the input listeners refuse to fire.
 */
public class ValidatedAreaReticuleRenderer extends AreaReticuleRenderer {

    protected PositionValidator validator;

    public ValidatedAreaReticuleRenderer(PositionValidator validator) {
        this(SkillshotSettings.DEFAULT_AREA_SIZE, validator);
    }

    public ValidatedAreaReticuleRenderer(float size, PositionValidator validator) {
        super(size);
        this.validator = validator;
    }

    @Override
    public boolean isValidPosition() {
        return validator == null || validator.isValid(cursorPos);
    }
}
