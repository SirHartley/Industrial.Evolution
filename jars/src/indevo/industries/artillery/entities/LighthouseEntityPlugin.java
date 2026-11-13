package indevo.industries.artillery.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.terrain.LighthouseBeamTerrain;

public class LighthouseEntityPlugin extends WatchtowerEntityPlugin {

    public static final String ENTITY_ID = "IndEvo_Lighthouse";
    public static final float CHECK_SECONDS = 0.5f;

    protected SectorEntityToken beam;
    protected boolean lit = false;
    protected float sinceCheck = CHECK_SECONDS;

    // unlit it works as a plain watchtower, lit only the beams see
    @Override
    protected void advanceVision(float amount) {
        LighthouseBeamTerrain terrain = getOrCreateBeam();

        sinceCheck += amount;
        if (sinceCheck >= CHECK_SECONDS) {
            sinceCheck = 0f;
            lit = shouldBeLit();
            terrain.setLit(lit);
            if (lit) markFleetsInBeam(terrain);
        }

        if (!lit) super.advanceVision(amount);
    }

    public boolean isLit() {
        return lit;
    }

    public boolean beamSees(SectorEntityToken target) {
        return lit && beam != null && getBeamPlugin().sees(target.getLocation(), target.getRadius(), CHECK_SECONDS);
    }

    private boolean shouldBeLit() {
        if (!isFunctional() || isHacked()) return false;
        if (!entity.getFaction().isHostileTo(Factions.PLAYER)) return false;

        return ArtilleryStationEntityPlugin.needsVisionAgainstPlayer(entity.getContainingLocation());
    }

    private void markFleetsInBeam(LighthouseBeamTerrain terrain) {
        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) {
            if (fleet.isPlayerFleet() || fleet.isStationMode() || !isHostileTo(fleet)) continue;
            if (terrain.sees(fleet.getLocation(), fleet.getRadius(), CHECK_SECONDS)) markSeen(fleet);
        }
    }

    private LighthouseBeamTerrain getOrCreateBeam() {
        LocationAPI loc = entity.getContainingLocation();

        if (beam == null || beam.isExpired() || beam.getContainingLocation() != loc) {
            beam = loc.addTerrain(LighthouseBeamTerrain.TERRAIN_ID, new BaseRingTerrain.RingParams(1f, 0f, entity, "Lighthouse Beam"));
            beam.setCircularOrbit(entity, 0f, 0f, 100f);
        }

        return getBeamPlugin();
    }

    private LighthouseBeamTerrain getBeamPlugin() {
        return (LighthouseBeamTerrain) ((CampaignTerrainAPI) beam).getPlugin();
    }

    @Override
    protected String getSeenText() {
        return "Detected by Lighthouse";
    }

    @Override
    public void printEffect(TooltipMakerAPI text, float pad) {
        if (!lit) {
            super.printEffect(text, pad);
            return;
        }

        text.addPara(BaseIntelPlugin.INDENT + "Transmits target telemetry of fleets caught in its %s",
                pad, Misc.getHighlightColor(), "beams");
    }

    @Override
    public void addHackStatusToTooltip(TooltipMakerAPI text, float pad) {
        if (!lit) {
            super.addHackStatusToTooltip(text, pad);
            return;
        }

        if (beamSees(Global.getSector().getPlayerFleet())) text.addPara("%s your fleet", pad, Misc.getNegativeHighlightColor(), "Tracking");
        else text.addPara("Tracks fleets inside its beams", pad);
    }
}
