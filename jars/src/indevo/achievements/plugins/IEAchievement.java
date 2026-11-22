package indevo.achievements.plugins;

import com.fs.starfarer.api.Global;
import indevo.achievements.Achievements;
import indevo.utils.ModPlugin;
import org.magiclib.achievements.MagicAchievement;

import java.io.IOException;

public class IEAchievement extends MagicAchievement {

    @Override
    public void onApplicationLoaded(boolean isComplete) {
        super.onApplicationLoaded(isComplete);
        loadImage(this);

        int goal = Achievements.goal(getSpecId());
        if (goal > 0 && (getMaxProgress() == null || getMaxProgress() != goal)) setMaxProgress((float) goal);
    }

    // the image column names an icon the game may not have loaded, the intel draws it with addImage
    public static void loadImage(MagicAchievement a) {
        String image = a.getImage();
        if (image == null || image.isEmpty() || !image.startsWith("graphics/")) return;

        try {
            Global.getSettings().loadTexture(image);
        } catch (IOException e) {
            ModPlugin.log("Achievement image missing: " + image);
        }
    }
}
