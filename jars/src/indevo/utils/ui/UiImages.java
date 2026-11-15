package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
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
    public static final float STACK_PAD = 6f;
    public static final float STACK_TOP = 4f;
    public static final float STACK_COUNT_Y = 18f;

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
        CustomPanelAPI sprite = framed(p, x, y, size, member.getHullSpec().getSpriteName(), framed, dim);
        Ui.tooltip(p, sprite, member.getShipName(), member.getHullSpec().getHullNameWithDashClass());
        return sprite;
    }

    public static CustomPanelAPI hull(CustomPanelAPI p, float x, float y, float size, ShipHullSpecAPI spec, boolean framed, boolean dim) {
        CustomPanelAPI sprite = framed(p, x, y, size, spec.getSpriteName(), framed, dim);
        Ui.tooltip(p, sprite, spec.getHullNameWithDashClass());
        return sprite;
    }

    // one fighter hull sprite, not the vanilla formation picture
    public static CustomPanelAPI wing(CustomPanelAPI p, float x, float y, float size, String wingId, boolean framed, boolean dim) {
        FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wingId);
        CustomPanelAPI sprite = framed(p, x, y, size, spec.getVariant().getHullSpec().getSpriteName(), framed, dim);
        if (spec.getRoleDesc() != null) Ui.tooltip(p, sprite, spec.getWingName(), spec.getRoleDesc());
        else Ui.tooltip(p, sprite, spec.getWingName());
        return sprite;
    }

    private static CustomPanelAPI framed(CustomPanelAPI p, float x, float y, float size, String spriteName, boolean framed, boolean dim) {
        if (framed) Ui.well(p, x, y, size, size);
        float pad = framed ? SHIP_PAD : 0f;
        return img(p, x + pad, y + pad, size - 2f * pad, size - 2f * pad, spriteName, null, dim ? DIM : 1f);
    }

    public static TooltipMakerAPI shipIcon(CustomPanelAPI p, float x, float y, float size, FleetMemberAPI member) {
        return Ui.element(p, x, y, size, size, t -> t.addShipList(1, 1, size, Ui.base(), List.of(member), 0f));
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

    // cargo style cell: count bottom right in highlight (hidden below 0), extra top left in positive colour and a lit frame while above 0
    public static CustomPanelAPI stack(CustomPanelAPI p, float x, float y, float size, String spriteName, int count, int extra, String name) {
        Color border = extra > 0 ? Misc.scaleAlpha(Ui.bright(), 0.85f) : Misc.scaleAlpha(Ui.base(), 0.2f);
        CustomPanelAPI cell = Ui.box(p, x, y, size, size, border, Misc.setAlpha(Color.BLACK, 90));
        img(cell, STACK_PAD, STACK_TOP, size - 2f * STACK_PAD, size - 2f * STACK_PAD, spriteName);
        if (count >= 0) Ui.text(cell, 0f, size - STACK_COUNT_Y, size - 3f, Ui.num(count), Ui.hl(), Ui.RIGHT);
        if (extra > 0) Ui.text(cell, 4f, 2f, size - 8f, "+" + Ui.num(extra), Ui.pos());
        if (name != null) Ui.tooltip(p, cell, name);
        return cell;
    }

    // vanilla planet render for a planet market, the faction crest for a station
    public static void planet(CustomPanelAPI p, float x, float y, float size, MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null) icon(p, x, y, size, market.getFaction().getCrest(), market.getName());
        else render(p, x, y, size, planet, market.getName());
    }

    // vanilla render for a planet or star, else the custom entity sprite or icon when loaded, else the faction crest
    public static void planet(CustomPanelAPI p, float x, float y, float size, SectorEntityToken entity) {
        if (entity instanceof PlanetAPI planet) render(p, x, y, size, planet, entity.getName());
        else icon(p, x, y, size, entitySprite(entity), entity.getName());
    }

    private static void render(CustomPanelAPI p, float x, float y, float size, PlanetAPI planet, String name) {
        TooltipMakerAPI element = Ui.element(p, x, y, size, size, t -> t.showPlanetInfo(planet, size, size, false, 0f));
        Ui.tooltip(p, element, name);
    }

    private static String entitySprite(SectorEntityToken entity) {
        CustomEntitySpecAPI spec = entity.getCustomEntitySpec();
        if (spec != null && loaded(spec.getSpriteName())) return spec.getSpriteName();
        if (spec != null && loaded(spec.getIconName())) return spec.getIconName();
        return entity.getFaction() == null ? null : entity.getFaction().getCrest();
    }

    // getSprite on a path that was never loaded returns a sprite without texture and zero size
    private static boolean loaded(String spriteName) {
        return spriteName != null && Global.getSettings().getSprite(spriteName).getWidth() > 0f;
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
