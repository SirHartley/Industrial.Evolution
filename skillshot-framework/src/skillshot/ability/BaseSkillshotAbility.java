package skillshot.ability;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import skillshot.SkillshotFramework;
import skillshot.input.OnClickSkillshotListener;
import skillshot.input.SkillshotActivationManager;
import skillshot.util.DelayedActionScriptRunWhilePaused;
import skillshot.util.SkillshotUtils;

/**
 * Base class for a skillshot ability. Subclasses implement two things:
 * {@link SkillshotAbility#createReticule()} and
 * {@link #onSkillshotFired(Vector2f, float)}.
 * <p>
 * Wiring, in abilities.csv: put this class' subclass in the "plugin" column and
 * {@link skillshot.SkillshotSettings#TAG_SKILLSHOT} in the "tags" column. The "deactivationCooldown"
 * column becomes the ability's rearm time - the framework applies it on fire.
 * <p>
 * Nothing here assumes the ability is a consumable. If yours is, override {@link #onConsume()} to
 * take the item out of the cargo.
 */
public abstract class BaseSkillshotAbility extends BaseDurationAbility implements SkillshotAbility {

    /**
     * The payload. Called once the player has committed to an aim point the reticule accepted.
     *
     * @param worldTarget    cursor position in campaign world coordinates
     * @param angleFromFleet degrees from the player fleet to that position
     */
    protected abstract void onSkillshotFired(Vector2f worldTarget, float angleFromFleet);

    /**
     * Called right after a successful shot. Default does nothing - override to consume an item, spend
     * a charge, or whatever else should happen once per use.
     */
    protected void onConsume() {
    }

    /** Ability-specific tooltip body. The framework appends its own "can't fire right now" lines. */
    public abstract void addTooltip(TooltipMakerAPI tooltip);

    @Override
    public boolean isUsable() {
        return super.isUsable() && !isTargetingBlocked();
    }

    /**
     * True while a skillshot cannot start: another ability is already aiming, or the player is in a
     * dialog or a core UI tab (where the reticule would render over the wrong thing and the click
     * would never reach us).
     */
    public boolean isTargetingBlocked() {
        if (SkillshotActivationManager.getInstanceOrRegister().hasActiveListener()) return true;

        return Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null
                || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;
    }

    @Override
    public boolean isActiveOrInProgress() {
        return super.isActiveOrInProgress() || turnedOn;
    }

    /**
     * Entry point for the click path. The hotkey path never gets here - OnKeyPressSkillshotListener
     * consumes the keypress before the UI turns it into a button press, so it can hold the reticule
     * open until the key comes back up.
     */
    @Override
    public void pressButton() {
        if (!isUsable() || turnedOn) return;
        if (entity == null || !entity.isPlayerFleet()) return;

        playActivationSound();

        final SkillshotAbility self = this;
        SkillshotFramework.log("Starting click targeting for " + getId());

        //the activation has to be delayed by one frame: the UI takes priority over input listeners
        //with priority 0, so registering the listener now would make it eat this very click
        Global.getSector().getScripts().add(new DelayedActionScriptRunWhilePaused(0f) {
            @Override
            public void doAction() {
                if (SkillshotActivationManager.getInstanceOrRegister().hasActiveListener()) return;
                new OnClickSkillshotListener(self).activate();
            }
        });
    }

    protected void playActivationSound() {
        String soundId = getOnSoundUI();
        if (soundId == null) return;

        if (PLAY_UI_SOUNDS_IN_WORLD_SOURCES) {
            Global.getSoundPlayer().playSound(soundId, 1f, 1f, Global.getSoundPlayer().getListenerPos(), new Vector2f());
        } else {
            Global.getSoundPlayer().playUISound(soundId, 1f, 1f);
        }
    }

    /**
     * Fires without going through {@link #activate()} - the normal path would trigger on button
     * press, which is exactly what we spent the targeting session avoiding.
     */
    @Override
    public void forceActivation() {
        activateImpl();
        onConsume();
    }

    @Override
    public void activate() {
        super.activate();
        onConsume();
    }

    @Override
    protected void activateImpl() {
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
        float opad = 10f;

        if (SkillshotActivationManager.getInstanceOrRegister().hasActiveListener()) {
            tooltip.addPara("Conflicting ability in use!", opad, Misc.getNegativeHighlightColor());
        }

        boolean uiBlocking = Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null
                || Global.getSector().getCampaignUI().getCurrentCoreTab() != null;

        if (uiBlocking) {
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
}
