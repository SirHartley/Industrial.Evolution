package indevo.items.consumables.itemPlugins;

import com.fs.starfarer.api.Global;
import indevo.utils.helper.StringHelper;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import indevo.items.consumables.itemAbilities.SingleUseItemAbility;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import indevo.utils.timers.IntervalTracker;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class BaseConsumableItemPlugin extends BaseSpecialItemPlugin {

    //Consumable Items (sold by science nexus, add to normal market?):

    //AM Supercharger		yeets you a good amount into the facing direction, CR loss
    //Decoy         		yeets a decoy into the direction of the cursor, which pings and distracts all fleets. Fleets tracking the player are unaffected.
    //Gravitic Stabilizer		all player fleet ships nearly guaranteed recoverable for next battle
    //Tech Locator		Hyperspace Locator for Ruins (vast or better)/research stations/orbital labs, works like ability in green
    //Field Bubble Disruptor		spike mine, arms 0.5s after being deployed onto the map, then does the standard intercept pulse when an enemy enter the range, lasts for 1 week
    //Emergency Fuel Scoop		Creates X amount of fuel from a nearby star, nebula or gas giant
    //Survey Drones		next survey requires a flat 10 crew and supply

    private transient SpriteAPI shortFrame;
    private transient SpriteAPI frame;
    private transient SpriteAPI mask;

    private boolean reverse = false;
    protected float prev = 0f;

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        shortFrame = Global.getSettings().getSprite("IndEvo", "shortFrame");
        frame = Global.getSettings().getSprite("IndEvo", "broadFrame");
        mask = Global.getSettings().getSprite("IndEvo", "frameBG");
    }

    public boolean isInPlayerCargo(){
        return Global.getSector().getCampaignUI().getCurrentCoreTab() == CoreUITabId.CARGO && Global.getSector().getCampaignUI().getCurrentInteractionDialog() == null;
    }

    protected AbilityPlugin getAbilityPlugin() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (!fleet.hasAbility(spec.getParams())) addAbilityToFleet();

        return Global.getSector().getPlayerFleet().getAbility(spec.getParams());
    }

    public void addAbilityToFleet(){
        String params = spec.getParams();
        AbilitySpecAPI abilitySpecAPI = Global.getSettings().getAbilitySpec(params);
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        if(abilitySpecAPI != null && !playerFleet.hasAbility(params)){
            playerFleet.addAbility(params);
            Global.getSector().getCharacterData().addAbility(params);
        }
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
        if(!isActive() && !isCooldown()) return;

        //when active, do the alcohol thing
        //when on cooldown, do a pulsing red border - border breaks everything, revert to default spinny boi
        final SpriteAPI sprite = this.shortFrame;
        final SpriteAPI mask = this.mask;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float secondFraction = isCooldown() ? IntervalTracker.getInstance().getIntervalFraction2s() : IntervalTracker.getInstance().getIntervalFraction1s();
        float maskAlpha = isCooldown() ? 0.5f : 0.9f;
        Color baseColor = isCooldown() ? Color.RED : Misc.getBasePlayerColor();

        if (sprite != null && mask != null) {

            sprite.setSize(90f,90f);

            //sprite render
            sprite.setColor(baseColor);
            sprite.setNormalBlend();
            sprite.setAlphaMult(alphaMult * 0.8f);
            sprite.renderAtCenter(cx, cy);

            // mask
            GL11.glColorMask(false, false, false, true);
            GL11.glPushMatrix();
            GL11.glTranslatef(cx, cy, 0);
            Misc.renderQuadAlpha(x * 3f, y * 3f, w * 3f, h * 3f, Misc.zeroColor, 0f);
            GL11.glPopMatrix();
            sprite.setBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
            sprite.renderAtCenter(cx, cy);

            mask.setAlphaMult(alphaMult * maskAlpha);
            mask.setAngle(-secondFraction * 90f);
            mask.setBlendFunc(GL11.GL_ZERO, GL11.GL_SRC_ALPHA);
            mask.renderAtCenter(cx, cy);

            GL11.glColorMask(true, true, true, false);
            mask.setBlendFunc(GL11.GL_DST_ALPHA, GL11.GL_ONE_MINUS_DST_ALPHA);
            mask.renderAtCenter(cx, cy);
        }
    }

    public boolean isAbilityAvailable(){
        return getAbilityPlugin().isUsable();
    }
    public boolean isCooldown(){
        return getAbilityPlugin().isOnCooldown();
    }
    public boolean isActive(){
        return getAbilityPlugin().isActiveOrInProgress();
    }

    @Override
    public void performRightClickAction() {
        getAbilityPlugin().activate();

        Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                "Using " + getName());
    }

    @Override
    public boolean hasRightClickAction() {
        return isInPlayerCargo() && isAbilityAvailable();
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, true);

        SingleUseItemAbility p = (SingleUseItemAbility) getAbilityPlugin();

        float pad = 3f;
        float opad = 10f;
        float small = 5f;
        Color h = Misc.getHighlightColor();
        Color r = Misc.getNegativeHighlightColor();
        Color b =  Misc.getPositiveHighlightColor();

        tooltip.addSectionHeading("Item Effect", Alignment.MID, opad);
        p.addTooltip(tooltip, true);

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        if(hasRightClickAction()) tooltip.addPara("Right-click to use", b, opad);
        else if (isActive()) tooltip.addPara("Currently active", h, opad);
        else if (isCooldown()) {
            int cd = (int) Math.ceil(getAbilityPlugin().getCooldownLeft());
            tooltip.addPara("On cooldown for " + cd + " " + StringHelper.getDayOrDays(cd), r, opad);
        } else if (!hasRightClickAction()){
            tooltip.addPara("Can not activate in current situation", h, opad);
        }
    }
}
