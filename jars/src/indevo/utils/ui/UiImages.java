package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class UiImages {

    public static final float DIM = 0.4f;
    public static final float SHIP_PAD = 4f;

    public interface Cell<T> {
        void render(CustomPanelAPI p, float x, float y, float w, float h, T item);
    }

    private UiImages() {
    }

    public static String creditsSprite() {
        return Global.getSettings().getSpriteName("fleetScreen", "icon_logistics_credits");
    }

    public static String commoditySprite(String id) {
        return Global.getSettings().getCommoditySpec(id).getIconName();
    }

    public static String relicSprite() {
        return commoditySprite(ItemIds.RARE_PARTS);
    }

    public static String partsSprite() {
        return commoditySprite(ItemIds.PARTS);
    }

    // sprite at a fixed box, contain fit, tint null keeps the sprite colours
    public static CustomPanelAPI img(CustomPanelAPI p, float x, float y, float w, float h, String spriteName, Color tint, float alpha) {
        SpriteAPI sprite = spriteName == null ? null : Global.getSettings().getSprite(spriteName);
        float srcW = sprite == null ? 0f : sprite.getWidth();
        float srcH = sprite == null ? 0f : sprite.getHeight();
        Color color = tint != null ? tint : Color.WHITE;

        return Ui.painted(p, x, y, w, h, null, (pos, a) -> UiDraw.sprite(sprite, srcW, srcH, pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), color, a * alpha));
    }

    public static CustomPanelAPI img(CustomPanelAPI p, float x, float y, float w, float h, String spriteName) {
        return img(p, x, y, w, h, spriteName, null, 1f);
    }

    public static CustomPanelAPI tint(CustomPanelAPI p, float x, float y, float w, float h, String spriteName, Color color) {
        return img(p, x, y, w, h, spriteName, color, 1f);
    }

    public static CustomPanelAPI icon(CustomPanelAPI p, float x, float y, float size, String spriteName, String name) {
        CustomPanelAPI icon = img(p, x, y, size, size, spriteName);
        if (name != null) Ui.tooltip(p, icon, name);
        return icon;
    }

    public static CustomPanelAPI portrait(CustomPanelAPI p, float x, float y, float size, PersonAPI person) {
        return icon(p, x, y, size, person.getPortraitSprite(), person.getNameString());
    }

    // hand drawn framed cell with the hull sprite; use shipIcon for the vanilla icon with its stock tooltip
    public static CustomPanelAPI ship(CustomPanelAPI p, float x, float y, float size, FleetMemberAPI member, boolean framed, boolean dim) {
        if (framed) Ui.well(p, x, y, size, size);
        float pad = framed ? SHIP_PAD : 0f;
        CustomPanelAPI sprite = img(p, x + pad, y + pad, size - 2f * pad, size - 2f * pad, member.getHullSpec().getSpriteName(), null, dim ? DIM : 1f);
        Ui.tooltip(p, sprite, member.getShipName(), member.getHullSpec().getHullNameWithDashClass());
        return sprite;
    }

    public static TooltipMakerAPI shipIcon(CustomPanelAPI p, float x, float y, float size, FleetMemberAPI member) {
        TooltipMakerAPI element = p.createUIElement(size + Ui.INSET, size, false);
        element.addShipList(1, 1, size, Ui.base(), List.of(member), 0f);
        p.addUIElement(element).inTL(x - Ui.INSET, y);
        return element;
    }

    public static CustomPanelAPI skill(CustomPanelAPI p, float x, float y, float size, SkillSpecAPI spec, boolean elite) {
        CustomPanelAPI icon = img(p, x, y, size, size, spec.getSpriteName());
        if (elite) Ui.box(p, x - 1f, y - 1f, size + 2f, size + 2f, Ui.hl(), null);
        if (elite) Ui.tooltip(p, icon, spec.getName(), "Elite");
        else Ui.tooltip(p, icon, spec.getName());
        return icon;
    }

    public static CustomPanelAPI commodity(CustomPanelAPI p, float x, float y, float size, String id) {
        return icon(p, x, y, size, commoditySprite(id), Global.getSettings().getCommoditySpec(id).getName());
    }

    public static CustomPanelAPI hullmod(CustomPanelAPI p, float x, float y, float size, String id) {
        return icon(p, x, y, size, Global.getSettings().getHullModSpec(id).getSpriteName(), Global.getSettings().getHullModSpec(id).getDisplayName());
    }

    public static CustomPanelAPI item(CustomPanelAPI p, float x, float y, float size, String id) {
        return icon(p, x, y, size, Global.getSettings().getSpecialItemSpec(id).getIconName(), Global.getSettings().getSpecialItemSpec(id).getName());
    }

    public static CustomPanelAPI submarket(CustomPanelAPI p, float x, float y, float size, SubmarketAPI submarket) {
        return icon(p, x, y, size, submarket.getSpec().getIcon(), submarket.getSpec().getName());
    }

    // vanilla planet render for a planet market, the faction crest for a station
    public static void planet(CustomPanelAPI p, float x, float y, float size, MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null) {
            icon(p, x, y, size, market.getFaction().getCrest(), market.getName());
            return;
        }

        TooltipMakerAPI element = p.createUIElement(size + Ui.INSET, size, false);
        element.showPlanetInfo(planet, size, size, false, 0f);
        p.addUIElement(element).inTL(x - Ui.INSET, y);
        Ui.tooltip(p, element, market.getName());
    }

    // fixed cells, wraps to rows, the last cell turns into +N when items do not fit; returns the used height
    public static <T> float iconrow(CustomPanelAPI p, float x, float y, float w, List<T> items, float cw, float ch, float gap, int rows,
                                    Cell<T> cell, Function<T, String> name) {
        if (items.isEmpty()) return 0f;

        int per = Math.max(1, (int) ((w + gap) / (cw + gap)));
        int cap = per * rows;
        int shown = items.size() <= cap ? items.size() : cap - 1;

        for (int i = 0; i < shown; i++) {
            cell.render(p, x + (i % per) * (cw + gap), y + (i / per) * (ch + gap), cw, ch, items.get(i));
        }

        if (shown < items.size()) {
            List<String> rest = new ArrayList<>();
            for (T item : items.subList(shown, items.size())) rest.add(name.apply(item));
            morecell(p, x + (shown % per) * (cw + gap), y + (shown / per) * (ch + gap), cw, ch, rest);
        }

        int used = Math.min(rows, (Math.min(items.size(), cap) + per - 1) / per);
        return used * (ch + gap) - gap;
    }

    public static CustomPanelAPI morecell(CustomPanelAPI p, float x, float y, float w, float h, List<String> rest) {
        CustomPanelAPI cell = Ui.box(p, x, y, w, h, Misc.scaleAlpha(Ui.base(), 0.3f), Misc.scaleAlpha(Ui.dark(), 0.6f));
        String font = h < 22f ? Ui.VIC10 : Ui.INS15;
        Ui.textIn(cell, 0f, 0f, w, h, "+" + rest.size(), font, Ui.base(), Ui.CENTER, new String[0], new Color[0]);
        Ui.tooltip(p, cell, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> {
            for (int i = 0; i < rest.size(); i++) t.addPara(rest.get(i), i == 0 ? 0f : 3f);
        });
        return cell;
    }
}
