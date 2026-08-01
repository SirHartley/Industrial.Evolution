package skillshot.ability;

import com.fs.starfarer.api.characters.AbilityPlugin;
import skillshot.render.SkillshotRenderer;

/**
 * What the input layer needs from an ability. Implement this on top of whatever ability base class
 * your mod already uses, or just extend {@link BaseSkillshotAbility}.
 * <p>
 * The ability additionally has to carry {@link skillshot.SkillshotSettings#TAG_SKILLSHOT} in
 * abilities.csv - that tag is how the hotkey listener recognises it in the ability bar.
 */
public interface SkillshotAbility extends AbilityPlugin {

    /**
     * A fresh reticule for one targeting session. Called every time targeting starts, so returning
     * a new instance is expected.
     */
    SkillshotRenderer createReticule();

    /**
     * Fire. Called by the input layer once the player commits to a valid aim point, bypassing the
     * normal activation path (which would fire the moment the button is pressed, before aiming).
     */
    void forceActivation();
}
