package indevo.industries.artillery.entities;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.terrain.SearchlightTerrain;

public class SearchlightEntityPlugin extends WatchtowerEntityPlugin {

    public static final String ENTITY_ID = "IndEvo_Searchlight";
    public static final float CHECK_SECONDS = 0.5f;

    protected SectorEntityToken lights;
    protected boolean lit = false;
    protected float sinceCheck = CHECK_SECONDS;

    // unlit it works as a plain watchtower, lit only the lights see
    @Override
    protected void advanceVision(float amount) {
        sinceCheck += amount;
        if (sinceCheck >= CHECK_SECONDS && replacedForPlayerColony()) return;

        SearchlightTerrain terrain = getOrCreateLights();

        if (sinceCheck >= CHECK_SECONDS) {
            sinceCheck = 0f;
            lit = shouldBeLit();
            terrain.setLit(lit);
            if (lit) markFleetsInLights(terrain);
        }

        if (!lit) super.advanceVision(amount);
    }

    public boolean isLit() {
        return lit;
    }

    public float getPlayerRate() {
        return lit && lights != null ? getLightsPlugin().getPlayerRate() : -1f;
    }

    public boolean seesPlayer() {
        return lit && lights != null && getLightsPlugin().seesPlayer();
    }

    private void markFleetsInLights(SearchlightTerrain terrain) {
        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) {
            if (fleet.isPlayerFleet() || fleet.isStationMode() || !isHostileTo(fleet)) continue;
            if (terrain.sees(fleet)) markSeen(fleet);
        }
    }

    private SearchlightTerrain getOrCreateLights() {
        LocationAPI loc = entity.getContainingLocation();

        if (lights == null || lights.isExpired() || lights.getContainingLocation() != loc) {
            lights = loc.addTerrain(SearchlightTerrain.TERRAIN_ID, new BaseRingTerrain.RingParams(1f, 0f, entity, "Searchlight"));
            lights.setCircularOrbit(entity, 0f, 0f, 100f);
        }

        return getLightsPlugin();
    }

    private SearchlightTerrain getLightsPlugin() {
        return (SearchlightTerrain) ((CampaignTerrainAPI) lights).getPlugin();
    }

    @Override
    protected String getSeenText() {
        return "Detected by Searchlight";
    }

    @Override
    public void printEffect(TooltipMakerAPI text, float pad) {
        if (!lit) {
            super.printEffect(text, pad);
            return;
        }

        text.addPara(BaseIntelPlugin.INDENT + "Transmits target telemetry of fleets caught in its %s",
                pad, Misc.getHighlightColor(), "searchlights");
    }

    @Override
    public void addHackStatusToTooltip(TooltipMakerAPI text, float pad) {
        if (!lit) {
            super.addHackStatusToTooltip(text, pad);
            return;
        }

        if (seesPlayer()) text.addPara("%s your fleet", pad, Misc.getNegativeHighlightColor(), "Tracking");
        else text.addPara("Tracks fleets inside its searchlights", pad);
    }
}
