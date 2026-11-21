package indevo.industries.derelicts.dialogue;

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

import java.awt.Color;
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
        Color hl = Misc.getHighlightColor();
        int count = area.systems.size();

        if (area.mode == RiftTargetMode.RANDOM) tooltip.addPara("Target area: anywhere within %s light years.", 0f, hl, Math.round(area.reachLY) + "");
        else tooltip.addPara("Target area: around the %s.", 0f, hl, area.anchor.getNameWithLowercaseType());

        if (count == 1) tooltip.addPara("The %s is the only star system in reach.", 2f, hl, area.systems.get(0).getNameWithLowercaseType());
        else tooltip.addPara("%s star systems in reach. The exact one is only known after the jump.", 2f, hl, count + "");

        float w = tooltip.getWidthSoFar();
        addMap(tooltip, market, area, w, Math.round(w / MAP_ASPECT), OPAD);
    }

    public static void addAreaText(TextPanelAPI text, float width, MarketAPI market, RiftTargetArea area) {
        Color hl = Misc.getHighlightColor();
        String count = area.systems.size() + "";
        String systems = area.systems.size() == 1 ? " star system in reach." : " star systems in reach.";

        if (area.mode == RiftTargetMode.RANDOM) text.addPara("Target area: anywhere within %s light years, %s" + systems, hl, Math.round(area.reachLY) + "", count);
        else text.addPara("Target area: around the %s, %s" + systems, hl, area.anchor.getNameWithLowercaseType(), count);

        TooltipMakerAPI info = text.beginTooltip();
        addMap(info, market, area, width, Math.round(width / MAP_ASPECT), 0f);
        text.addTooltip();
    }

    public static void addMap(TooltipMakerAPI tooltip, MarketAPI market, RiftTargetArea area, float w, float h, float pad) {
        MapParams params = new MapParams();
        params.showSystems = new HashSet<>(area.systems);
        params.useFullAlphaForShownSystems = true;
        params.skipCurrLocMarkerRendering = true;
        params.markers = new ArrayList<>();
        params.markers.add(new MarkerData(new Vector2f(area.origin), null, market.getFaction().getBaseUIColor()));

        StarSystemAPI from = market.getStarSystem();
        if (area.anchor != null && from.getHyperspaceAnchor() != null && area.anchor.getHyperspaceAnchor() != null)
            params.arrows.add(new IntelInfoPlugin.ArrowData(from.getHyperspaceAnchor(), area.anchor.getHyperspaceAnchor()));

        frame(params, area, Math.min(w, h));

        UIPanelAPI map = tooltip.createSectorMap(Math.round(w), Math.round(h), params, TITLE);
        tooltip.addCustom(map, pad);
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
