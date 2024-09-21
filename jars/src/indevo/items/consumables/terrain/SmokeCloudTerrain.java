package indevo.items.consumables.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TerrainAIFlags;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SmokeCloudTerrain extends BaseRingTerrain {

    //has to be spawned and despawned by another entity, use the explosion entity see ecmeExplosion, manage visuals there
    
    public static final float VISIBLITY_MULT = 0.5f;
    public static final float BURN_PENALTY_MULT = 0.3f;

    public boolean hasTooltip() {
        return true;
    }

    @Override
    public String getTerrainName() {
        return "Chaff Cloud";
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    public void applyEffect(SectorEntityToken entity, float days) {
        if (entity instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) entity;
            fleet.getStats().addTemporaryModMult(0.1f, getModId() + "_1",
                    "Inside chaff cloud", VISIBLITY_MULT ,
                    fleet.getStats().getDetectedRangeMod());

            float penalty = getBurnMultForTerrain(fleet);
            //float penalty = getBurnPenalty(fleet);
            fleet.getStats().addTemporaryModMult(0.1f, getModId() + "_2",
                    "Inside chaff cloud", penalty,
                    fleet.getStats().getFleetwideMaxBurnMod());
        }
    }

    public static float getBurnMultForTerrain(CampaignFleetAPI fleet) {
        float mult = Misc.getFleetRadiusTerrainEffectMult(fleet);
        mult = (1f - BURN_PENALTY_MULT * mult);
        mult = Math.round(mult * 100f) / 100f;
        if (mult < 0.1f) mult = 0.1f;
        return mult;
    }

    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 10f;
        float small = 5f;
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        Color fuel = Global.getSettings().getColor("progressBarFuelColor");
        Color bad = Misc.getNegativeHighlightColor();
        Color text = Misc.getTextColor();

        tooltip.addTitle("Chaff Cloud");
        tooltip.addPara(Global.getSettings().getDescription(getTerrainId(), Description.Type.TERRAIN).getText1(), pad);
        float nextPad = pad;
        if (expanded) {
            tooltip.addSectionHeading("Travel", Alignment.MID, pad);
            nextPad = small;
        }
        tooltip.addPara("Reduces the range at which fleets inside can be detected by %s.", nextPad,
                highlight,
                "" + (int) ((1f - VISIBLITY_MULT) * 100) + "%"
        );

        tooltip.addPara("Reduces the travel speed of fleets inside by up to %s. Larger fleets are slowed down more.",
                pad,
                highlight,
                "" + (int) (Math.round(BURN_PENALTY_MULT) * 100f) + "%"
        );

        float penalty = Misc.getBurnMultForTerrain(Global.getSector().getPlayerFleet());
        tooltip.addPara("Your fleet's speed is reduced by %s.", pad,
                highlight,
                "" + (int) Math.round((1f - penalty) * 100) + "%"
                //Strings.X + penaltyStr
        );
    }

    public float getTooltipWidth() {
        return 350f;
    }

    public String getEffectCategory() {
        return "nebula-like";
    }

    public boolean hasAIFlag(Object flag) {
        return flag == TerrainAIFlags.REDUCES_DETECTABILITY ||
                flag == TerrainAIFlags.REDUCES_SPEED_LARGE||
                flag == TerrainAIFlags.TILE_BASED
                ;
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
