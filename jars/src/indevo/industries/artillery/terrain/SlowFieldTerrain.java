package indevo.industries.artillery.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SlowFieldTerrain extends BaseRingTerrain {

    public boolean hasTooltip() {
        return true;
    }

    @Override
    public String getTerrainName() {
        return "Slow Field";
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 10f;
        Color highlight = Misc.getHighlightColor();

        tooltip.addTitle("Slow Field");

        tooltip.addPara(Global.getSettings().getDescription(getTerrainId(), Description.Type.TERRAIN).getText1(), pad);

        tooltip.addPara("Reduces the travel speed of fleets inside by up to %s. Larger fleets are slowed down more.",
                pad,
                highlight,
                "" + (int) ((Misc.BURN_PENALTY_MULT) * 100f) + "%"
        );

        float penalty = Misc.getBurnMultForTerrain(Global.getSector().getPlayerFleet());
        String penaltyStr = Misc.getRoundedValue(1f - penalty);
        tooltip.addPara("Your fleet's speed is reduced by %s.", pad,
                highlight,
                "" + (int) Math.round((1f - penalty) * 100) + "%"
        );
    }

    public void applyEffect(SectorEntityToken entity, float days) {
        if (entity instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) entity;
            float penalty = Misc.getBurnMultForTerrain(fleet);
            fleet.getStats().addTemporaryModMult(0.1f, "IndEvo_slowField",
                    "Inside slow field", penalty,
                    fleet.getStats().getFleetwideMaxBurnMod());
        }
    }

    public float getTooltipWidth() {
        return 350f;
    }

    public String getEffectCategory() {
        return "nebula-like";
    }

    public boolean hasAIFlag(Object flag) {
        return flag == TerrainAIFlags.REDUCES_SPEED_LARGE;
    }

    @Override
    public float getMaxEffectRadius(Vector2f locFrom) {
        return getRingParams().bandWidthInEngine;
    }

    @Override
    public float getMinEffectRadius(Vector2f locFrom) {
        return 0f;
    }

    @Override
    protected float getMaxRadiusForContains() {
        return getRingParams().bandWidthInEngine;
    }

    @Override
    protected float getMinRadiusForContains() {
        return 0f;
    }
}
