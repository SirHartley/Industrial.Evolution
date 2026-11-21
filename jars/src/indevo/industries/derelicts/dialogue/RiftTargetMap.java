package indevo.industries.derelicts.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.MapParams;
import com.fs.starfarer.api.ui.MarkerData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.derelicts.RiftTargetArea;
import indevo.industries.derelicts.RiftTargetMode;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;

public class RiftTargetMap {

    public static final String TITLE = "Target area";
    private static final float OPAD = 10f;
    private static final float MAP_ASPECT = 1.6f;
    private static final float MIN_ZOOM = 3f;

    private RiftTargetMap() {
    }

    public static void addAreaTooltip(TooltipMakerAPI tooltip, MarketAPI market, RiftTargetArea area) {
        tooltip.addPara(getAreaLine(area), 0f, Misc.getHighlightColor(), area.getHighlight());
        float w = tooltip.getWidthSoFar();
        addMap(tooltip, market, area, w, Math.round(w / MAP_ASPECT), OPAD);
    }

    public static void addAreaText(TextPanelAPI text, RiftTargetArea area) {
        text.addPara(getAreaLine(area), Misc.getHighlightColor(), area.getHighlight());
    }

    private static String getAreaLine(RiftTargetArea area) {
        return "Target area: " + area.getDescription() + ".";
    }

    public static void addMap(TooltipMakerAPI tooltip, MarketAPI market, RiftTargetArea area, float w, float h, float pad) {
        MapParams params = new MapParams();
        params.showSystems = new HashSet<>(area.systems);
        params.useFullAlphaForShownSystems = true;
        params.skipCurrLocMarkerRendering = true;
        params.markers = new ArrayList<>();
        params.markers.add(new MarkerData(new Vector2f(area.origin), null, market.getFaction().getBaseUIColor()));
        for (StarSystemAPI system : area.systems) params.markers.add(new MarkerData(new Vector2f(system.getLocation()), null, Misc.getHighlightColor()));

        StarSystemAPI from = market.getStarSystem();
        if (area.mode != RiftTargetMode.RANDOM && from.getHyperspaceAnchor() != null) {
            SectorEntityToken centre = Global.getSector().getHyperspace().createToken(getCentre(area));
            params.arrows.add(new IntelInfoPlugin.ArrowData(from.getHyperspaceAnchor(), centre));
        }

        frame(params, area, Math.min(w, h));

        UIPanelAPI map = tooltip.createSectorMap(Math.round(w), Math.round(h), params, TITLE);
        tooltip.addCustom(map, pad);
    }

    private static Vector2f getCentre(RiftTargetArea area) {
        Vector2f centre = new Vector2f();
        for (StarSystemAPI system : area.systems) Vector2f.add(centre, system.getLocation(), centre);
        centre.scale(1f / area.systems.size());
        return centre;
    }

    // MapParams.positionToShowAllMarkersAndSystems centres on the player fleet, this frames the planet and its area
    private static void frame(MapParams params, RiftTargetArea area, float heightOnScreen) {
        Vector2f min = new Vector2f(area.origin);
        Vector2f max = new Vector2f(area.origin);
        for (StarSystemAPI system : area.systems) {
            Vector2f p = system.getLocation();
            min.set(Math.min(min.x, p.x), Math.min(min.y, p.y));
            max.set(Math.max(max.x, p.x), Math.max(max.y, p.y));
        }

        float factor = MapParams.GRID_SIZE_PIXELS / MapParams.GRID_SIZE_MAP_UNITS;
        float distance = Vector2f.sub(max, min, new Vector2f()).length() * 0.5f * 1.2f * factor;

        params.zoomLevel = Math.max(MIN_ZOOM, (float) Math.ceil(distance / heightOnScreen) + 1f);
        params.centerOn = new Vector2f((min.x + max.x) / 2f, (min.y + max.y) / 2f);
    }
}
