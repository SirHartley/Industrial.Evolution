package com.fs.starfarer.api.campaign.impl.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.impl.items.consumables.singleUseItemPlugins.IndEvo_SpooferConsumableItemPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class IndEvo_SpooferAbilityPlugin extends IndEvo_BaseConsumableAbilityPlugin {

    //change player fleet faction to selected faction
    //if within 100 SU of enemy, disable
    //turn off when transponder turned off

    public static final float DISABLE_RANGE = 100f;
    public String originalFaction = null;
    private final static int CIRCLE_POINTS = 50;

    @Override
    protected void activateImpl() {
        originalFaction = entity.getFaction().getId();
        entity.setFaction(IndEvo_SpooferConsumableItemPlugin.getCurrentFaction());

        entity.setTransponderOn(true);
    }

    @Override
    protected void applyEffect(float amount, float level) {
        if (!entity.isTransponderOn()){
            deactivate();
            return;
        }

        for (CampaignFleetAPI f : Misc.getNearbyFleets(entity, entity.getRadius() + DISABLE_RANGE)){
            boolean valid = !f.isStationMode() && f.getAI() != null && !f.getFaction().getId().equals(entity.getFaction().getId());
            if (valid) {
                deactivate();
                return;
            }
        }
    }

    @Override
    protected void deactivateImpl() {
        entity.setFaction(originalFaction);
    }

    @Override
    protected void cleanupImpl() {

    }

    public static final float MAX_ALPHA = 0.4f;
    public static final float SECONDS_PER_CYCLE = 2f;

    boolean increase = true;
    IntervalUtil interval = new IntervalUtil(SECONDS_PER_CYCLE, SECONDS_PER_CYCLE);
    public float circleAlpha = 0f;

    @Override
    public void advance(float amount) {
        super.advance(amount);
        interval.advance(amount);

        if (interval.intervalElapsed()) increase = !increase;
        float fraction = interval.getElapsed() / interval.getIntervalDuration();
        circleAlpha = increase ? MAX_ALPHA * fraction : MAX_ALPHA * (1 - fraction);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        if(Global.getSector().getCampaignUI().isShowingDialog() || true) return;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glViewport(0,0, Display.getWidth(), Display.getHeight());
        GL11.glOrtho(0.0, Display.getWidth() * 1f,0.0, Display.getHeight() * 1f,-1.0, 1.0);
        GL11.glTranslatef(0.01f, 0.01f, 0f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.5f);

        GL11.glColor4f(entity.getFaction().getColor().getRed(), entity.getFaction().getColor().getGreen(), entity.getFaction().getColor().getBlue(), circleAlpha);
        DrawUtils.drawCircle(entity.getLocation().x, entity.getLocation().y, entity.getRadius() + DISABLE_RANGE, CIRCLE_POINTS, false);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, boolean forItem) {
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        float opad = 10f;

        if(!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }
        String id = isActiveOrInProgress() ? entity.getFaction().getId() : IndEvo_SpooferConsumableItemPlugin.getCurrentFaction();
        FactionAPI faction = Global.getSector().getFaction(id);

        tooltip.addPara("Emits a fake transponder signal. " +
                        "Lasts for %s and gets disabled if another fleet comes too close. " +
                        "Some entities might recognize you by sight or have alternative ways of identification and will ignore the effect.", opad, Misc.getHighlightColor(),
                Math.round(getDurationDays()) + " days");

        tooltip.addPara("Currently set to: %s", opad, faction.getColor(),
                Misc.ucFirst(faction.getDisplayName()));
        tooltip.addPara("[Use arrow keys to change the faction]",Misc.getGrayColor(), 3f);
    }
}
