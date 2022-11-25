package industrial_evolution.plugins.update;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import industrial_evolution.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.tutorial.CampaignTutorialScript;
import industrial_evolution.plugins.timers.IndEvo_newDayListener;

public class IndEvo_DetachmentAbilityAdder implements IndEvo_newDayListener {
    public static boolean isTutorialInProgress() {
        return Global.getSector().getMemoryWithoutUpdate().contains(CampaignTutorialScript.USE_TUTORIAL_RESPAWN);
    }

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(IndEvo_DetachmentAbilityAdder.class))
            manager.addListener(new IndEvo_DetachmentAbilityAdder());
    }

    public static void remove() {
        Global.getSector().getListenerManager().removeListenerOfClass(IndEvo_DetachmentAbilityAdder.class);
    }

    @Override
    public void onNewDay() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (!isTutorialInProgress() && !player.hasAbility(IndEvo_ids.ABILITY_DETACHMENT)) {
            player.addAbility(IndEvo_ids.ABILITY_DETACHMENT);
            Global.getSector().getCharacterData().addAbility(IndEvo_ids.ABILITY_DETACHMENT);
        }

        if (player.hasAbility(IndEvo_ids.ABILITY_DETACHMENT)) remove();
    }
}
