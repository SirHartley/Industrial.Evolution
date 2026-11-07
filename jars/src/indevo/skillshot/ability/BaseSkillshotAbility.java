package indevo.skillshot.ability;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.skillshot.SkillshotFramework;
import indevo.skillshot.input.SkillshotActivationManager;
import indevo.skillshot.util.SkillshotUtils;
import org.lwjgl.util.vector.Vector2f;

public abstract class BaseSkillshotAbility extends BaseDurationAbility implements SkillshotAbility {

    protected abstract void onSkillshotFired(Vector2f worldTarget, float angleFromFleet);

    protected void onConsume() {
    }

    public abstract void addTooltip(TooltipMakerAPI tooltip);

    protected void onActivatedWithoutReticule() {
    }

    @Override
    public boolean isUsable() {
        // no targeting session to block, usable wherever a vanilla ability is, core ui tabs included
        if (!showReticuleOnActivation()) return super.isUsable();

        return super.isUsable() && !isTargetingBlocked();
    }

    public boolean isTargetingBlocked() {
        if (SkillshotActivationManager.getInstanceOrRegister().hasActiveSession()) return true;

        return isUIBlocking();
    }

    protected boolean isUIBlocking() {
        return Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null
                || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;
    }

    @Override
    public boolean isActiveOrInProgress() {
        return super.isActiveOrInProgress() || turnedOn;
    }

    @Override
    public void pressButton() {
        // no reticule wanted, vanilla activation plays its own sound
        if (!showReticuleOnActivation()) {
            super.pressButton();
            return;
        }

        if (!isUsable() || turnedOn) return;
        if (entity == null || !entity.isPlayerFleet()) return;

        playActivationSound();

        SkillshotFramework.log("Starting click targeting for " + getId());
        SkillshotActivationManager.getInstanceOrRegister().queueClickSession(this);
    }

    protected float getActivationVolume() {
        return 1f;
    }

    protected void playActivationSound() {
        String soundId = getOnSoundUI();
        if (soundId == null) return;

        if (PLAY_UI_SOUNDS_IN_WORLD_SOURCES) {
            Global.getSoundPlayer().playSound(soundId, 1f, getActivationVolume(), Global.getSoundPlayer().getListenerPos(), new Vector2f());
        } else {
            Global.getSoundPlayer().playUISound(soundId, 1f, getActivationVolume());
        }
    }

    @Override
    public void forceActivation() {
        activateImpl();
        onConsume();
        setCooldownLeft(getSpec().getDeactivationCooldown());
    }

    @Override
    public void activate() {
        super.activate();
        onConsume();
    }

    @Override
    protected void activateImpl() {
        // the cursor was never an aim point here
        if (!showReticuleOnActivation()) {
            onActivatedWithoutReticule();
            return;
        }

        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        Vector2f target = SkillshotUtils.getCursorWorldPosition();
        onSkillshotFired(target, Misc.getAngleInDegrees(fleet.getLocation(), target));
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        addTooltip(tooltip);
        addBlockedReasonsToTooltip(tooltip);
    }

    protected void addBlockedReasonsToTooltip(TooltipMakerAPI tooltip) {
        if (!showReticuleOnActivation()) return;

        float opad = 10f;

        if (SkillshotActivationManager.getInstanceOrRegister().hasActiveSession()) {
            tooltip.addPara("Conflicting ability in use!", opad, Misc.getNegativeHighlightColor());
        }

        if (isUIBlocking()) {
            tooltip.addPara("Can only be activated from the ability bar.", opad, Misc.getNegativeHighlightColor());
        }
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    protected void applyEffect(float amount, float level) {
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

    @Override
    public boolean showReticuleOnActivation() {
        return true;
    }
}
