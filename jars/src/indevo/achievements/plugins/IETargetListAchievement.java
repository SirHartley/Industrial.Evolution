package indevo.achievements.plugins;

import indevo.achievements.Achievements;
import org.magiclib.achievements.MagicTargetListAchievement;

public class IETargetListAchievement extends MagicTargetListAchievement {

    @Override
    public void onApplicationLoaded(boolean isComplete) {
        super.onApplicationLoaded(isComplete);
        IEAchievement.loadImage(this);
        if (isComplete) return;

        setTargets(Achievements.targets(getSpecId()));
        saveChanges();
    }
}
