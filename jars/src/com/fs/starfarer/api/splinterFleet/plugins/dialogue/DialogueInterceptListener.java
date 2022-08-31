package com.fs.starfarer.api.splinterFleet.plugins.dialogue;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.Behaviour;

import static com.fs.starfarer.api.splinterFleet.plugins.FleetUtils.DETACHMENT_IDENTIFIER_KEY;
import static com.fs.starfarer.api.splinterFleet.plugins.FleetUtils.log;

public class DialogueInterceptListener extends BaseCampaignEventListener {

    public DialogueInterceptListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        super.reportShownInteractionDialog(dialog);

        if (dialog.getPlugin() instanceof FleetInteractionDialogPluginImpl) {
            if (dialog.getInteractionTarget().getMemoryWithoutUpdate().getBoolean(DETACHMENT_IDENTIFIER_KEY)
                    && !Behaviour.behaviourEquals(Behaviour.getFleetBehaviour((CampaignFleetAPI) dialog.getInteractionTarget(), false), Behaviour.FleetBehaviour.COMBAT)) {
                log.info("intercepting dialogue for detachment");

                FleetEncounterContext context = (FleetEncounterContext) dialog.getPlugin().getContext();
                context.applyAfterBattleEffectsIfThereWasABattle();

                BattleAPI b = context.getBattle();
                if (b.isPlayerInvolved()) {
                    ((FleetInteractionDialogPluginImpl) dialog.getPlugin()).cleanUpBattle();
                }

                dialog.setPlugin(new DetachmentDialoguePlugin());
                dialog.getPlugin().init(dialog);
            }
        }
    }
}
