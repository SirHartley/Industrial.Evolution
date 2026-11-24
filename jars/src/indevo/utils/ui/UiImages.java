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
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class UiImages {

    public static final float DIM = 0.4f;
    public static final float SHIP_PAD = 4f;
    public static final float STACK_PAD = 6f;
    public static final float STACK_TOP = 4f;
    public static final float STACK_COUNT_Y = 18f;
    public static final float GREY_LIGHT = 0.6f;

    private static final Logger log = Global.getLogger(UiImages.class);
    // one texture per path for the whole game session, never deleted; null after a failed read
    private static final Map<String, Grey> GREYS = new HashMap<>();
    // aptitude:column to sprite path, data files do not change while the game runs
    private static final Map<String, String> OVERLAYS = new HashMap<>();

    public interface Cell<T> {
        void render(CustomPanelAPI p, float x, float y, float w, float h, T item);
    }

    private record Grey(int id, float w, float h, float u, float v) {
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

    // the sprite without colour and darker (luma x GREY_LIGHT, alpha kept), contain fit; the sprite at DIM when the file can not be read
    public static CustomPanelAPI grey(CustomPanelAPI p, float x, float y, float w, float h, String spriteName) {
        if (spriteName != null && !GREYS.containsKey(spriteName)) GREYS.put(spriteName, readGrey(spriteName));
        Grey g = spriteName == null ? null : GREYS.get(spriteName);
        if (g == null) return img(p, x, y, w, h, spriteName, null, DIM);
        return Ui.painted(p, x, y, w, h, null, (pos, a) -> UiDraw.texture(g.id(), g.w(), g.h(), g.u(), g.v(), pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), a));
    }

    // a sprite or its grey copy into a rect from a painter, for pictures that change while the panel stays
    public static void paint(String spriteName, boolean grey, float x, float y, float w, float h, float alpha) {
        if (spriteName == null) return;
        if (grey && !GREYS.containsKey(spriteName)) GREYS.put(spriteName, readGrey(spriteName));
        Grey g = grey ? GREYS.get(spriteName) : null;
        if (g != null) {
            UiDraw.texture(g.id(), g.w(), g.h(), g.u(), g.v(), x, y, w, h, alpha);
            return;
        }

        SpriteAPI sprite = Global.getSettings().getSprite(spriteName);
        UiDraw.sprite(sprite, sprite.getWidth(), sprite.getHeight(), x, y, w, h, Color.WHITE, grey ? alpha * DIM : alpha);
    }

    private static Grey readGrey(String path) {
        BufferedImage image;
        try (InputStream in = Global.getSettings().openStream(path)) {
            image = ImageIO.read(in);
        } catch (IOException e) {
            log.warn("Could not read " + path + " for a grey portrait, drawing it dimmed", e);
            return null;
        }
        if (image == null) {
            log.warn("Could not decode " + path + " for a grey portrait, drawing it dimmed");
            return null;
        }

        int w = image.getWidth();
        int h = image.getHeight();
        int tw = 1;
        int th = 1;
        while (tw < w) tw <<= 1;
        while (th < h) th <<= 1;

        ByteBuffer pixels = BufferUtils.createByteBuffer(tw * th * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                float luma = 0.299f * ((argb >> 16) & 0xff) + 0.587f * ((argb >> 8) & 0xff) + 0.114f * (argb & 0xff);
                byte g = (byte) Math.min(255, Math.round(luma * GREY_LIGHT));
                int i = (y * tw + x) * 4;
                pixels.put(i, g).put(i + 1, g).put(i + 2, g).put(i + 3, (byte) (argb >>> 24));
            }
        }

        return new Grey(UiDraw.upload(pixels, tw, th), w, h, w / (float) tw, h / (float) th);
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

    // vanilla skill cell (coreui Z): black, icon 4 px in, two rings in the aptitude colour at 70%, elite stars at their own size in the middle
    public static CustomPanelAPI skill(CustomPanelAPI p, float x, float y, float size, SkillSpecAPI spec, boolean elite) {
        SpriteAPI icon = Global.getSettings().getSprite(spec.getSpriteName());
        float iw = icon.getWidth();
        float ih = icon.getHeight();
        Color frame = Misc.interpolateColor(spec.getGoverningAptitudeColor(), Global.getSettings().getColor("tooltipTitleAndLightHighlightColor"), 0.35f);
        String overlay = elite ? eliteOverlay(spec.getGoverningAptitudeId(), size) : null;
        SpriteAPI stars = overlay == null || overlay.isEmpty() ? null : Global.getSettings().getSprite(overlay);

        CustomPanelAPI cell = Ui.painted(p, x, y, size, size, null, (pos, a) -> {
            float x0 = UiDraw.left(pos);
            float y0 = UiDraw.bottom(pos);
            UiDraw.quad(x0, y0, size, size, Color.BLACK, a);
            UiDraw.sprite(icon, iw, ih, x0 + 4f, y0 + 4f, size - 8f, size - 8f, Color.WHITE, a);
            UiDraw.ring(x0, y0, x0 + size, y0 + size, 0, frame, a * 0.7f);
            UiDraw.ring(x0, y0, x0 + size, y0 + size, 1, frame, a * 0.7f);
            if (stars == null) return;
            stars.setNormalBlend();
            stars.setColor(Color.WHITE);
            stars.setAlphaMult(a);
            stars.renderAtCenter(Math.round(x0 + size / 2f), Math.round(y0 + size / 2f));
        });

        if (elite) Ui.tooltip(p, cell, spec.getName(), "Elite");
        else Ui.tooltip(p, cell, spec.getName());
        return cell;
    }

    // one star sprite per aptitude and cell size (40, 56, 72 and up), the api has no getter for the path
    private static String eliteOverlay(String aptitudeId, float size) {
        String column = size - 8f >= 64f ? "elite_overlay" : size - 8f >= 48f ? "elite_overlay_medium" : "elite_overlay_small";
        String key = aptitudeId + ":" + column;
        if (OVERLAYS.containsKey(key)) return OVERLAYS.get(key);

        String path = null;
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetData("id", "data/characters/skills/aptitude_data.csv");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (aptitudeId.equals(row.getString("id"))) path = row.optString(column, null);
            }
        } catch (IOException | JSONException e) {
            log.warn("could not read elite overlay for " + aptitudeId, e);
        }

        OVERLAYS.put(key, path);
        return path;
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
        return stack(p, x, y, size, spriteName, count >= 0 ? Ui.num(count) : null, extra, name);
    }

    public static CustomPanelAPI stack(CustomPanelAPI p, float x, float y, float size, String spriteName, String count, int extra, String name) {
        Color border = extra > 0 ? Misc.scaleAlpha(Ui.bright(), 0.85f) : Misc.scaleAlpha(Ui.base(), 0.2f);
        CustomPanelAPI cell = Ui.box(p, x, y, size, size, border, Misc.setAlpha(Color.BLACK, 90));
        img(cell, STACK_PAD, STACK_TOP, size - 2f * STACK_PAD, size - 2f * STACK_PAD, spriteName);
        if (count != null) Ui.text(cell, 0f, size - STACK_COUNT_Y, size - 3f, count, Ui.hl(), Ui.RIGHT);
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
