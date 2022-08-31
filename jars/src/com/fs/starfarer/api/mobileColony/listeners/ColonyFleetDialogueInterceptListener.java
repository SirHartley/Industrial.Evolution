package com.fs.starfarer.api.mobileColony.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.mobileColony.dialogue.MobileColonyInteractionDialoguePlugin;

import static com.fs.starfarer.api.mobileColony.utility.MobileColonyFactory.MOBILE_COLONY_IDENTIFIER;
import static com.fs.starfarer.api.splinterFleet.plugins.FleetUtils.log;

public class ColonyFleetDialogueInterceptListener extends BaseCampaignEventListener {

    public static void register(){
        Global.getSector().addTransientListener(new ColonyFleetDialogueInterceptListener(false));
    }

    public ColonyFleetDialogueInterceptListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        super.reportShownInteractionDialog(dialog);

        if(false){
            if (dialog.getPlugin() instanceof FleetInteractionDialogPluginImpl) {
                if (dialog.getInteractionTarget().getMemoryWithoutUpdate().getBoolean(MOBILE_COLONY_IDENTIFIER)
                        && ((CampaignFleetAPI) dialog.getInteractionTarget()).getBattle() == null) {
                    log.info("intercepting dialogue for mobile colony");

                    boolean hasStatic = Global.getSettings().getBoolean("enableUIStaticNoise");
                    if(hasStatic) Global.getSettings().setBoolean("enableUIStaticNoise", false);

                    FleetEncounterContext context = (FleetEncounterContext) dialog.getPlugin().getContext();
                    context.applyAfterBattleEffectsIfThereWasABattle();

                    BattleAPI b = context.getBattle();
                    if (b.isPlayerInvolved()) {
                        ((FleetInteractionDialogPluginImpl) dialog.getPlugin()).cleanUpBattle();
                    }

                    dialog.setPlugin(new MobileColonyInteractionDialoguePlugin());
                    dialog.getPlugin().init(dialog);

                    Global.getSettings().setBoolean("enableUIStaticNoise", hasStatic);
                }
            }
        }
    }
}


