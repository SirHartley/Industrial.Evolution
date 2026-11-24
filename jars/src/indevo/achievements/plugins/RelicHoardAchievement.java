package indevo.achievements.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import indevo.achievements.Achievements;
import indevo.ids.ItemIds;

// no callback reports a cargo change, magiclib runs this every 1 to 2 seconds
public class RelicHoardAchievement extends IEAchievement {

    @Override
    public void advanceAfterInterval(float amount) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        float relics = fleet.getCargo().getCommodityQuantity(ItemIds.RARE_PARTS);
        int goal = Achievements.goal(getSpecId());

        if (relics >= goal) {
            completeAchievement();
            saveChanges();
        } else if (Achievements.progress(this) != relics) setProgress(relics);
    }
}
