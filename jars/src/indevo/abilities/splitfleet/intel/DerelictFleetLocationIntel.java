package indevo.abilities.splitfleet.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class DerelictFleetLocationIntel extends FleetLogIntel {

    protected CampaignFleetAPI fleet;

    public DerelictFleetLocationIntel(CampaignFleetAPI fleet) {
        this.fleet = fleet;
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float pad = 3f;
        float opad = 10f;

        bullet(info);
        info.addPara(fleet.getName(), opad);
        unindent(info);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color tc = Misc.getTextColor();
        float pad = 3f;
        float small = 3f;
        float opad = 10f;

        info.addPara("A detachment has gone dormant. It is either out of supplies or fuel.", opad);
        info.addPara("Head there and merge it into your fleet, or resupply it.", opad);

        addBulletPoints(info, ListInfoMode.IN_DESC);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>();
        if (isImportant()) {
            tags.add(Tags.INTEL_IMPORTANT);
        }
        if (isNew()) {
            tags.add(Tags.INTEL_NEW);
        }
        if (map != null) {
            SectorEntityToken loc = getMapLocation(map);
            if (loc != null) {
                float max = Global.getSettings().getFloat("maxRelayRangeInHyperspace");
                float dist = Misc.getDistanceLY(loc.getLocationInHyperspace(), Global.getSector().getPlayerFleet().getLocationInHyperspace());
                if (dist <= max) {
                    tags.add(Tags.INTEL_LOCAL);
                }
            }
        }

        tags.add("Detachments");
        return tags;
    }

    public String getSortString() {
        return "Detachments";
    }

    public String getName() {
        return "Dormant Detachment";
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return fleet;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return !Behaviour.isDormant(fleet);
    }
}
