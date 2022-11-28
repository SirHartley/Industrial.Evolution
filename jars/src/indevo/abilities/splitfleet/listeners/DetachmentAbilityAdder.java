package indevo.abilities.splitfleet.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import indevo.ids.Ids;
import com.fs.starfarer.api.impl.campaign.tutorial.CampaignTutorialScript;
import indevo.utils.timers.NewDayListener;

public class DetachmentAbilityAdder implements NewDayListener {
    public static boolean isTutorialInProgress() {
        return Global.getSector().getMemoryWithoutUpdate().contains(CampaignTutorialScript.USE_TUTORIAL_RESPAWN);
    }

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(DetachmentAbilityAdder.class))
            manager.addListener(new DetachmentAbilityAdder());
    }

    public static void remove() {
        Global.getSector().getListenerManager().removeListenerOfClass(DetachmentAbilityAdder.class);
    }

    @Override
    public void onNewDay() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (!isTutorialInProgress() && !player.hasAbility(Ids.ABILITY_DETACHMENT)) {
            player.addAbility(Ids.ABILITY_DETACHMENT);
            Global.getSector().getCharacterData().addAbility(Ids.ABILITY_DETACHMENT);
        }

        if (player.hasAbility(Ids.ABILITY_DETACHMENT)) remove();
    }
}
