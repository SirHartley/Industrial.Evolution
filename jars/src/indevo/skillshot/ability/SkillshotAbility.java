package indevo.skillshot.ability;

import com.fs.starfarer.api.characters.AbilityPlugin;
import indevo.skillshot.render.SkillshotRenderer;

public interface SkillshotAbility extends AbilityPlugin {

    SkillshotRenderer createReticule();

    void forceActivation();

    boolean showReticuleOnActivation();
}
