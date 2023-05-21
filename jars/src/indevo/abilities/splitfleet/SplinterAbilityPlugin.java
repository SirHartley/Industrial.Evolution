package indevo.abilities.splitfleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.abilities.splitfleet.dialogue.AbilityPanelDialoguePlugin;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;

public class SplinterAbilityPlugin extends BaseDurationAbility {

    @Override
    protected void activateImpl() {
        Global.getSector().getCampaignUI().showInteractionDialog(new AbilityPanelDialoguePlugin(), Global.getSector().getPlayerFleet());
    }

    @Override
    public boolean isUsable() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        return !playerFleet.isInHyperspaceTransition()
                && !isOnCooldown()
                && disableFrames <= 0;
    }

    @Override
    public boolean showActiveIndicator() {
        return DetachmentMemory.isAnyDetachmentActive();
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {

        tooltip.addTitle("Detachment Control Panel");

        float pad = 10.0F;
        tooltip.addPara("Click to create or manage your fleet detachments.", pad);
        if (DetachmentMemory.isAnyDetachmentActive()) tooltip.addPara("A detachment is currently active.", pad);
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    protected void applyEffect(float v, float v1) {
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

}