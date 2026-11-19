package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.*;

// New young popup: a finished brood opens like a draw, one face down card per young, then each gets its place
public class PetYoungDialog extends BaseCustomDialogDelegate {

    static final float CARD_W = 250f;
    static final float CARD_H = 374f;
    static final float CARD_GAP = 12f;
    static final float CARDS_Y = 46f;
    static final float MIN_W = 330f;
    static final String TITLE = "New young: ";
    static final float OPEN_W = 96f;
    static final float OPEN_GAP = 14f;
    static final float SCREEN_MARGIN = 200f;
    static final float ROW_H = 18f;
    static final float FROM_W = 80f;
    // the board hangs the tag 9 px over the right edge, turned 7 degrees clockwise
    static final float TAG_OVER = 9f;
    static final float TAG_TILT = 7f;
    static final float PLACE_Y = CARD_H - 8f - 17f - 6f - 24f;
    // turn over, then one glow in the card's colour; real seconds
    static final float FLIP = 0.33f;
    static final float GLOW = 0.5f;
    static final float GLOW_W = 14f;
    static final float[] DIAMOND = {1f, 0f, 2f, 1f, 1f, 2f, 0f, 1f};
    // sparks: start offset from the card's top left, as a share of its size, and the push outwards in px
    static final float[][] SPARKS = {{-0.05f, 0.08f, -18f, 0f}, {-0.04f, 0.4f, -14f, 0f}, {-0.06f, 0.8f, -20f, 0f}, {1.03f, 0.16f, 16f, 0f},
            {1.04f, 0.56f, 20f, 0f}, {1.02f, 0.88f, 14f, 0f}, {0.24f, -0.04f, 0f, -16f}, {0.72f, -0.03f, 0f, -12f}, {0.48f, 1.02f, 0f, 14f}};
    // card back as the board card_back: lattice of 20 x 28 diamonds framed 5 px in, a black 80 x 112 diamond in the middle
    static final float BACK_INSET = 5f;
    static final float LATTICE_W = 20f;
    static final float LATTICE_H = 28f;
    static final float MID_W = 80f;
    static final float MID_H = 112f;
    static final int BACK_ROUND = 6;
    static final int FRAME_ROUND = 3;
    static final float BACK_TEX_W = 256f;
    static final float BACK_TEX_H = 512f;
    // board question mark is 21 px high, the font glyph 15
    static final float QMARK_SCALE = 1.4f;
    private static final Map<String, LazyFont.DrawableString> TAG_TEXT = new HashMap<>();
    private static LazyFont.DrawableString qmark;
    // gl texture ids by ui colour set
    private static final Map<Integer, Integer> BACK_TEXTURES = new HashMap<>();

    private final PetShop shop;
    private final PetBrood brood;
    private final String pair;
    private final Map<String, FleetMemberAPI> ships = new HashMap<>();
    private final Set<String> cargo = new HashSet<>();
    private final Map<String, TextFieldAPI> fields = new HashMap<>();
    private final Map<String, Float> openedAt = new HashMap<>();
    private final UiScroll.Offsets scroll = new UiScroll.Offsets();
    private InteractionDialogAPI dialog;
    private UiRoot root;
    private float clock = 0f;
    private final PetReveal reveal = new PetReveal();
    // the young whose gene scan reveal runs: its kind shows with glow and sparks once the last row is read
    private Pet scanning = null;

    enum Tier {
        PLAIN, SUM, MUTATION, MALFORMED
    }

    public PetYoungDialog(PetShop shop, PetBrood brood) {
        this.shop = shop;
        this.brood = brood;
        PetStatusManager manager = PetStatusManager.getInstance();
        Pet a = manager.get(brood.parentA);
        Pet b = manager.get(brood.parentB);
        this.pair = (a != null ? a.name : "?") + " x " + (b != null ? b.name : "?");
    }

    public void show(Runnable onClose) {
        brood.shown = true;
        UiHost.over(dialog -> {
            this.dialog = dialog;
            dialog.showCustomDialog(width(), CARDS_Y + CARD_H, this);
        }, onClose);
    }

    private int count() {
        return brood.getYoung().size();
    }

    private float cardsWidth() {
        return count() * CARD_W + (count() - 1) * CARD_GAP;
    }

    // wide enough for the title next to Open all, within the screen; a longer title is cut with a tooltip
    private float width() {
        float title = Ui.width(TITLE + pair, Ui.INS21) + 6f + OPEN_W + OPEN_GAP;
        float max = Global.getSettings().getScreenWidth() - SCREEN_MARGIN;
        return Math.max(Math.max(cardsWidth(), MIN_W), Math.min((float) Math.ceil(title), max));
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        root = new UiRoot(panel, scroll, this::build);
        root.refresh();
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return new BaseCustomUIPanelPlugin() {
            @Override
            public void advance(float amount) {
                clock += amount;
                if (!reveal.tick(clock)) return;

                if (scanning != null && !reveal.reading(scanning)) {
                    if (Setting.PET_MOTION.enabled() && tier(scanning, null) != Tier.PLAIN) openedAt.put(scanning.id, clock - FLIP);
                    scanning = null;
                }
                refresh();
            }

            @Override
            public void processInput(List<InputEventAPI> events) {
                for (InputEventAPI event : events) if (event.isLMBDownEvent()) reveal.skip();
            }
        };
    }

    private void refresh() {
        keepNames();
        root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        boolean closed = false;
        for (Pet young : brood.getYoung()) if (!brood.opened.contains(young.id)) closed = true;

        // measured as one string like width() and Ui.runs; the two parts measured apart do not add up to it
        float tw = closed ? w - OPEN_W - OPEN_GAP : w;
        String shown = Ui.fit(TITLE + pair, Ui.INS21, tw);
        String shownPair = shown.length() > TITLE.length() ? shown.substring(TITLE.length()) : "";
        LabelAPI title = Ui.runs(p, 0f, 2f, tw, Ui.INS21, Ui.text(), Ui.LEFT, shown, new String[]{shownPair}, new Color[]{Ui.hl()});
        if (!shown.equals(TITLE + pair)) Ui.tooltip(p, title, null, pair);
        if (closed) UiButtons.button(p, w - OPEN_W, 2f, OPEN_W, PetCard.BUTTON_H, "Open all", UiButtons.Kind.STD, CutStyle.TL_BR, this::openAll);

        fields.clear();
        float x0 = Math.round((w - cardsWidth()) / 2f);
        List<Pet> young = brood.getYoung();
        for (int i = 0; i < young.size(); i++) {
            Pet pet = young.get(i);
            float x = x0 + i * (CARD_W + CARD_GAP);
            if (brood.opened.contains(pet.id)) openCard(p, x, CARDS_Y, pet);
            else {
                CustomPanelAPI row = UiButtons.row(p, x, CARDS_Y, CARD_W, CARD_H, false, true, null, () -> open(pet));
                cardBack(row, 0f, 0f);
            }
        }
    }

    private void open(Pet pet) {
        brood.opened.add(pet.id);
        if (Setting.PET_MOTION.enabled()) openedAt.put(pet.id, clock);
        refresh();
    }

    private void openAll() {
        for (Pet pet : brood.getYoung()) {
            if (brood.opened.add(pet.id) && Setting.PET_MOTION.enabled()) openedAt.put(pet.id, clock);
        }
        refresh();
    }

    // closed young tile of the Husbandry tab: a diamond lattice, a diamond in the middle with the question mark
    static void back(CustomPanelAPI p, float x, float y, float w, float h) {
        float scale = h / CARD_H;
        float tile = Math.max(8f, 20f * scale);
        float slope = 28f / 20f;
        float mw = Math.round(80f * scale);
        float mh = Math.round(112f * scale);
        Color lattice = Misc.scaleAlpha(Ui.base(), 0.4f);
        Color edge = Ui.line();

        Ui.painted(p, x, y, w, h, (pos, a) -> {
            float left = pos.getX() + 5f;
            float top = pos.getY() + pos.getHeight() - 5f;
            float iw = pos.getWidth() - 10f;
            float ih = pos.getHeight() - 10f;
            UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), Color.BLACK, a);

            float reach = ih / slope;
            for (float c = -reach - tile; c <= iw + reach + tile; c += tile) {
                float t0 = Math.max(0f, -c);
                float t1 = Math.min(iw - c, reach);
                if (t1 > t0) UiDraw.line(left + c + t0, top - t0 * slope, left + c + t1, top - t1 * slope, 1f, lattice, 1f, 1f, 1f, a, false);

                float u0 = Math.max(0f, c - iw);
                float u1 = Math.min(c, reach);
                if (u1 > u0) UiDraw.line(left + c - u0, top - u0 * slope, left + c - u1, top - u1 * slope, 1f, lattice, 1f, 1f, 1f, a, false);
            }

            UiDraw.outline(pos.getX() + 5f, pos.getY() + 5f, iw, ih, edge, a);
            float cx = Math.round(pos.getX() + pos.getWidth() / 2f);
            float cy = Math.round(pos.getY() + pos.getHeight() / 2f);
            UiDraw.fan(cx - mw / 2f, cy - mh / 2f, mh, 2f, new float[]{mw / mh, 0f, 2f * mw / mh, 1f, mw / mh, 2f, 0f, 1f}, mw / mh, 1f, Color.BLACK, a);
            diamond(cx, cy, mw, mh, Ui.base(), a);
            diamond(cx, cy, mw - 8f, mh - 10f, edge, a);
        }, null);

        float q = Math.max(12f, Math.round(24f * scale));
        UiImages.img(p, x + Math.round((w - q) / 2f), y + Math.round((h - q) / 2f), q, q, PetCard.unknownSprite());
    }

    // back of a closed young card, then "Click to open" under the middle diamond
    private static void cardBack(CustomPanelAPI p, float x, float y) {
        Ui.painted(p, x, y, CARD_W, CARD_H, PetYoungDialog::paintBack, null);

        String text = "Click to open";
        float hw = Ui.width(text, Ui.INS15) + 20f;
        float hx = x + Math.round((CARD_W - hw) / 2f);
        float hy = y + Math.round(CARD_H / 2f + MID_H / 2f + 24f);
        Ui.box(p, hx, hy, hw, 22f, Ui.dark(), Color.BLACK);
        Ui.textIn(p, hx, hy, hw, 22f, text, Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
    }

    private static void paintBack(PositionAPI pos, float a) {
        float x = Math.round(pos.getX());
        float y = Math.round(pos.getY());
        UiDraw.quad(x, y, CARD_W, CARD_H, Color.BLACK, a);
        UiDraw.texture(backTexture(), CARD_W, CARD_H, CARD_W / BACK_TEX_W, CARD_H / BACK_TEX_H, x, y, CARD_W, CARD_H, a);

        LazyFont.DrawableString q = qmark();
        if (q == null) return;

        q.setBaseColor(Misc.scaleAlpha(Misc.scaleColorOnly(Ui.base(), 0.66f), a));
        GL11.glPushMatrix();
        GL11.glTranslatef(x + Math.round(CARD_W / 2f), y + Math.round(CARD_H / 2f), 0f);
        GL11.glScalef(QMARK_SCALE, QMARK_SCALE, 1f);
        q.draw(-Math.round(q.getWidth() / 2f), Math.round(q.getHeight() / 2f));
        GL11.glPopMatrix();
    }

    // the board svg drawn once per ui colour set with java2d anti aliasing (vanilla builds font textures the same way), gl lines can not be smoothed reliably
    private static int backTexture() {
        Color base = Ui.base();
        Color dark = Ui.dark();
        Color plain = color(Tier.PLAIN);
        int key = Objects.hash(base.getRGB(), dark.getRGB(), plain.getRGB());
        Integer cached = BACK_TEXTURES.get(key);
        if (cached != null) return cached;

        int w = (int) CARD_W;
        int h = (int) CARD_H;
        double in = BACK_INSET;
        double iw = w - 2 * in;
        double ih = h - 2 * in;
        double ox = in + (iw % LATTICE_W) / 2;
        double oy = in + (ih % LATTICE_H) / 2;
        double cx = in + iw / 2;
        double cy = in + ih / 2;

        BufferedImage image = new BufferedImage((int) BACK_TEX_W, (int) BACK_TEX_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);

        Shape frame = new RoundRectangle2D.Double(in + 0.5, in + 0.5, iw - 1, ih - 1, 2 * FRAME_ROUND, 2 * FRAME_ROUND);
        Color lattice = Misc.setAlpha(dark, 255);
        for (double tx = ox - LATTICE_W; tx < in + iw; tx += LATTICE_W) {
            for (double ty = oy - LATTICE_H; ty < in + ih; ty += LATTICE_H) {
                g.setClip(frame);
                g.clip(new Rectangle2D.Double(tx, ty, LATTICE_W, LATTICE_H));
                g.setColor(lattice);
                g.draw(diamondShape(tx + LATTICE_W / 2, ty + LATTICE_H / 2, LATTICE_W, LATTICE_H));
                g.setColor(dark);
                g.fill(diamondShape(tx + LATTICE_W / 2, ty + LATTICE_H / 2, 6, 8));
            }
        }
        g.setClip(null);
        g.setColor(dark);
        g.draw(frame);

        Shape mid = diamondShape(cx, cy, MID_W, MID_H);
        g.setColor(Color.BLACK);
        g.fill(mid);
        g.setColor(base);
        g.draw(mid);
        g.setColor(dark);
        g.draw(diamondShape(cx, cy, MID_W - 8, MID_H - 10));

        g.setColor(plain);
        g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 2 * BACK_ROUND, 2 * BACK_ROUND));
        g.dispose();

        int tw = (int) BACK_TEX_W;
        ByteBuffer pixels = BufferUtils.createByteBuffer(tw * (int) BACK_TEX_H * 4);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int argb = image.getRGB(px, py);
                int i = (py * tw + px) * 4;
                pixels.put(i, (byte) (argb >> 16)).put(i + 1, (byte) (argb >> 8)).put(i + 2, (byte) argb).put(i + 3, (byte) (argb >>> 24));
            }
        }
        int id = UiDraw.upload(pixels, tw, (int) BACK_TEX_H);
        BACK_TEXTURES.put(key, id);
        return id;
    }

    private static Shape diamondShape(double cx, double cy, double w, double h) {
        Path2D.Double d = new Path2D.Double();
        d.moveTo(cx, cy - h / 2);
        d.lineTo(cx + w / 2, cy);
        d.lineTo(cx, cy + h / 2);
        d.lineTo(cx - w / 2, cy);
        d.closePath();
        return d;
    }

    private static LazyFont.DrawableString qmark() {
        if (qmark != null) return qmark;

        try {
            LazyFont font = LazyFont.loadFont(Fonts.ORBITRON_24AABOLD);
            qmark = font.createText("?", Color.WHITE, font.getBaseHeight());
        } catch (Exception e) {
            Global.getLogger(PetYoungDialog.class).warn("IndEvo: no font for the young card back", e);
        }
        return qmark;
    }

    private static void diamond(float cx, float cy, float w, float h, Color c, float a) {
        UiDraw.line(cx, cy + h / 2f, cx + w / 2f, cy, 1f, c, 1f, 1f, 1f, a, false);
        UiDraw.line(cx + w / 2f, cy, cx, cy - h / 2f, 1f, c, 1f, 1f, 1f, a, false);
        UiDraw.line(cx, cy - h / 2f, cx - w / 2f, cy, 1f, c, 1f, 1f, 1f, a, false);
        UiDraw.line(cx - w / 2f, cy, cx, cy + h / 2f, 1f, c, 1f, 1f, 1f, a, false);
    }

    // sum and the talent tables only once its talents are read; a mutation and Malformed always show
    static Tier tier(Pet pet, PetReveal reveal) {
        PetSheet sheet = pet.sheet;
        if (sheet.malformed) return Tier.MALFORMED;
        if (sheet.from == null) return Tier.PLAIN;
        if (sheet.from.containsValue(PetSheet.FROM_MUTATION)) return Tier.MUTATION;
        for (Map.Entry<String, String> e : sheet.from.entrySet()) {
            if (PetSheet.FROM_SUM.equals(e.getValue()) && (reveal != null ? reveal.known(pet, e.getKey()) : sheet.isKnown(e.getKey()))) return Tier.SUM;
        }
        return Tier.PLAIN;
    }

    static Color color(Tier tier) {
        return switch (tier) {
            case SUM -> Ui.hl();
            case MUTATION -> Ui.bright();
            case MALFORMED -> Ui.neg();
            default -> Misc.scaleAlpha(Ui.base(), 0.3f);
        };
    }

    static String tag(Tier tier) {
        return switch (tier) {
            case SUM -> "Sum";
            case MUTATION -> "Mutation";
            case MALFORMED -> "Malformed";
            default -> null;
        };
    }

    private void openCard(CustomPanelAPI parent, float x, float y, Pet pet) {
        Tier tier = tier(pet, reveal);
        Color border = color(tier);
        CustomPanelAPI p = Ui.panel(parent, x, y, CARD_W, CARD_H);
        Ui.box(p, 0f, 0f, CARD_W, CARD_H, reveal.on(pet) ? PetReveal.GREEN : border, Color.BLACK);

        fields.put(pet.id, PetCard.nameField(p, 8f, 8f, CARD_W - 16f, pet.name));
        Ui.card(p, 8f, 42f, PetCard.IDENT, PetCard.IDENT);
        UiImages.img(p, 12f, 46f, PetCard.PORTRAIT, PetCard.PORTRAIT, pet.getIcon());
        UiData.grid(p, 88f, 44f, CARD_W - 96f, ROW_H, List.of(
                GridRow.of("Look", pet.getLookName(), Ui.text()),
                GridRow.of("Disposition", Misc.ucFirst(pet.sheet.disposition), Academy.COLOURS_BY_PERSONALITY.get(pet.sheet.disposition))));
        if (pet.sheet.malformed) {
            Ui.text(p, 88f, 84f, CARD_W - 96f, "Malformed", Ui.neg());
            Ui.tooltip(p, Ui.panel(p, 88f, 84f, CARD_W - 96f, ROW_H), "Malformed", "Its parents were kin: it came with a new flaw, or one more level of a flaw.");
        }

        float ty = addTalents(p, 130f, pet);
        float by = addBody(p, ty + PetsPanel.HEAD_GAP, pet);
        PetCard.scanButton(p, 8f, by + 8f, shop, pet, reveal, null, () -> {
            reveal.begin(pet, clock);
            scanning = pet;
            shop.geneScan(pet);
            refresh();
        });
        addPlace(p, pet);

        String tag = tag(tier);
        if (tag != null) {
            float tw = Ui.width(tag, Ui.ORB12) + 18f;
            Ui.painted(parent, x + CARD_W - tw + TAG_OVER, y - 10f, tw, 20f, null, (pos, a) -> paintTag(pos, a, tag, border));
        }

        Float at = openedAt.get(pet.id);
        if (at != null) Ui.painted(parent, x, y, CARD_W, CARD_H, null, (pos, a) -> paintOpening(pos, a, clock - at, tier == Tier.PLAIN ? null : border));
        if (reveal.on(pet)) reveal.border(parent, x, y, CARD_W, CARD_H, 0f);
    }

    // first FLIP: a black cover with the card turning as a narrowing then widening frame; then a glow and sparks in its colour
    private static void paintOpening(PositionAPI pos, float alpha, float t, Color color) {
        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        if (t < FLIP) {
            float f = Math.abs(1f - 2f * t / FLIP);
            float fw = Math.max(2f, Math.round(w * f));
            UiDraw.quad(x - 1f, y - 1f, w + 2f, h + 2f, Color.BLACK, alpha);
            UiDraw.outline(x + Math.round((w - fw) / 2f), y, fw, h, Ui.bright(), alpha);
            return;
        }

        float g = (t - FLIP) / GLOW;
        if (color == null || g >= 1f) return;

        float fade = 1f - g;
        for (int i = 1; i <= 4; i++) {
            float d = i * GLOW_W / 4f * (0.4f + 0.6f * g);
            UiDraw.outline(x - d, y - d, w + 2f * d, h + 2f * d, color, alpha * fade * (1f - i / 5f) * 0.6f);
        }

        float ease = 1f - (1f - g) * (1f - g);
        for (float[] s : SPARKS) {
            float sx = x + s[0] * w + s[2] * ease;
            float sy = y + h - s[1] * h - s[3] * ease;
            UiDraw.fan(sx - 3f, sy - 3f, 6f, 2f, DIAMOND, 1f, 1f, color, alpha * fade);
        }
    }

    // talents read with the parents, else one Unknown row each
    private float addTalents(CustomPanelAPI p, float y, Pet pet) {
        tableHead(p, y, "Grade", "Talents");
        y += Ui.HEADING_H + 4f;

        List<String> talents = pet.getTalents();
        if (talents.isEmpty()) return row(p, y, null, "None", null, false);

        for (String id : talents) {
            if (reveal.lit(pet, id)) lit(p, y);
            if (!reveal.known(pet, id)) {
                y = row(p, y, "?", "Unknown", null, false);
                continue;
            }
            y = row(p, y, String.valueOf(pet.sheet.getGrade(id)), PetDataRepo.getTrait(id).name(), pet.sheet.getFrom(id), true, pet);
        }

        return y;
    }

    // mutations always, flaws once read
    private float addBody(CustomPanelAPI p, float y, Pet pet) {
        tableHead(p, y, "Level", "Mutations, flaws");
        y += Ui.HEADING_H + 4f;

        int rows = 0;
        for (String id : pet.sheet.traits.keySet()) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (trait == null || trait.isTalent() || !reveal.known(pet, id)) continue;
            if (reveal.lit(pet, id)) lit(p, y);
            String level = trait.hasLevels() ? String.valueOf(pet.sheet.getGrade(id)) : null;
            y = row(p, y, level, trait.name(), pet.sheet.getFrom(id), true, pet);
            rows++;
        }

        if (!reveal.flawsRead(pet)) return row(p, y, "?", "Flaws: Unknown", null, false);
        if (rows > 0) return y;
        if (reveal.lit(pet, PetReveal.FLAWS)) lit(p, y);
        return row(p, y, null, "None", null, false);
    }

    // labels can not turn, so the tag is drawn: box, outline and text around its centre
    private static void paintTag(PositionAPI pos, float alpha, String tag, Color c) {
        float w = pos.getWidth();
        float h = pos.getHeight();
        GL11.glPushMatrix();
        GL11.glTranslatef(pos.getCenterX(), pos.getCenterY(), 0f);
        GL11.glRotatef(-TAG_TILT, 0f, 0f, 1f);
        UiDraw.quad(-w / 2f, -h / 2f, w, h, Color.BLACK, alpha);
        UiDraw.outline(-w / 2f, -h / 2f, w, h, c, alpha);
        LazyFont.DrawableString text = tagText(tag);
        if (text != null) {
            text.setBaseColor(Misc.scaleAlpha(c, alpha));
            text.draw(-text.getWidth() / 2f, text.getHeight() / 2f);
        }
        GL11.glPopMatrix();
    }

    private static LazyFont.DrawableString tagText(String tag) {
        LazyFont.DrawableString text = TAG_TEXT.get(tag);
        if (text != null) return text;

        try {
            LazyFont font = LazyFont.loadFont(Ui.ORB12);
            text = font.createText(tag, Color.WHITE, font.getBaseHeight());
        } catch (Exception e) {
            Global.getLogger(PetYoungDialog.class).warn("IndEvo: no font for the young tag", e);
            return null;
        }
        TAG_TEXT.put(tag, text);
        return text;
    }

    private void lit(CustomPanelAPI p, float y) {
        reveal.bar(Ui.panel(p, 8f, y, CARD_W - 16f, ROW_H), CARD_W - 16f, ROW_H);
    }

    // the second label runs up to the measured From label, as the board draws it
    private static void tableHead(CustomPanelAPI p, float y, String first, String second) {
        float w = CARD_W - 16f;
        float from = Ui.width("From", Ui.ORB12);
        Ui.thead(p, 8f, y, w, new String[]{first, second, "From"}, new float[]{4f, 52f, w - FROM_W - 8f},
                new float[]{44f, w - 52f - from - 16f, FROM_W}, new Alignment[]{Ui.CENTER, Ui.LEFT, Ui.RIGHT});
    }

    private static float row(CustomPanelAPI p, float y, String grade, String name, String from, boolean on) {
        return row(p, y, grade, name, from, on, null);
    }

    private static float row(CustomPanelAPI p, float y, String grade, String name, String from, boolean on, Pet pet) {
        Color c = on ? Ui.text() : Ui.gray();
        if (grade != null) Ui.textIn(p, 12f, y, 44f, ROW_H, grade, Ui.INS15, on ? Ui.hl() : c, Ui.CENTER, new String[0], new Color[0]);
        String label = from != null && pet != null ? fromLabel(pet, from) : "";
        // the name takes what the From label leaves
        float nameW = CARD_W - 60f - 16f - (label.isEmpty() ? 0f : Ui.width(label, Ui.INS15) + 8f);
        Ui.textIn(p, 60f, y, nameW, ROW_H, name, Ui.INS15, c, Ui.LEFT, new String[0], new Color[0]);
        if (!label.isEmpty()) {
            Ui.textIn(p, CARD_W - 8f - FROM_W - 8f, y, FROM_W, ROW_H, label, Ui.INS15, fromColor(from), Ui.RIGHT, new String[0], new Color[0]);
        }
        return y + ROW_H;
    }

    private static String fromLabel(Pet pet, String from) {
        PetStatusManager manager = PetStatusManager.getInstance();
        return switch (from) {
            case PetSheet.FROM_A -> parentName(manager.get(pet.parentA));
            case PetSheet.FROM_B -> parentName(manager.get(pet.parentB));
            case PetSheet.FROM_SUM -> "Sum";
            case PetSheet.FROM_MUTATION -> "Mutation";
            case PetSheet.FROM_MALFORMED -> "Malformed";
            default -> "";
        };
    }

    private static String parentName(Pet parent) {
        return parent != null ? Ui.fit(parent.name, Ui.INS15, FROM_W) : "";
    }

    private static Color fromColor(String from) {
        return switch (from) {
            case PetSheet.FROM_SUM -> Ui.hl();
            case PetSheet.FROM_MUTATION -> Ui.bright();
            case PetSheet.FROM_MALFORMED -> Ui.neg();
            default -> Ui.gray();
        };
    }

    // Storage is picked when it opens; Ship... asks for a ship without a pet that no other young here has taken
    private void addPlace(CustomPanelAPI p, Pet pet) {
        FleetMemberAPI ship = ships.get(pet.id);
        boolean inCargo = cargo.contains(pet.id);
        float bw = (float) Math.floor((CARD_W - 16f - 8f) / 3f);
        UiButtons.button(p, 8f, PLACE_Y, bw, PetCard.BUTTON_H, "Storage", ship == null && !inCargo ? UiButtons.Kind.HOT : UiButtons.Kind.STD, CutStyle.TL_BR, () -> {
            ships.remove(pet.id);
            cargo.remove(pet.id);
            refresh();
        });

        List<FleetMemberAPI> free = PetStatusManager.getInstance().getFreeShips(Global.getSector().getPlayerFleet());
        for (Map.Entry<String, FleetMemberAPI> e : ships.entrySet()) if (!e.getKey().equals(pet.id)) free.remove(e.getValue());
        String reason = pet.getLifetime() <= 0f ? "It lives only where it does not age: Pet storage with a Beta core."
                : free.isEmpty() ? "No ship without a pet." : null;

        ButtonAPI shipButton = UiButtons.button(p, 8f + bw + 4f, PLACE_Y, bw, PetCard.BUTTON_H, "Ship...",
                reason != null ? UiButtons.Kind.OFF : ship != null ? UiButtons.Kind.HOT : UiButtons.Kind.STD, CutStyle.TL_BR,
                reason != null ? null : () -> pickShip(pet, free));
        if (reason != null) Ui.tooltip(p, shipButton, "Ship...", reason);

        ButtonAPI cargoButton = UiButtons.button(p, 8f + 2f * (bw + 4f), PLACE_Y, bw, PetCard.BUTTON_H, "Cargo", inCargo ? UiButtons.Kind.HOT : UiButtons.Kind.STD, CutStyle.TL_BR, () -> {
            ships.remove(pet.id);
            cargo.add(pet.id);
            refresh();
        });
        Ui.tooltip(p, cargoButton, "Cargo", "Puts the young into a Cryochamber in your fleet's cargo. It does not age there. Open it from the cargo to give it a ship or the menagerie, or sell it.");

        String where = ship != null ? ship.getShipName() + ", " + PetCard.shipClass(ship) : inCargo ? "Cryochamber, fleet cargo" : "Pet storage, " + shop.getMarket().getName();
        Ui.text(p, 8f, PLACE_Y + 30f, CARD_W - 16f, Ui.fit(where, Ui.INS15, CARD_W - 16f), Ui.gray());
    }

    private void pickShip(Pet pet, List<FleetMemberAPI> free) {
        keepNames();
        PetPlaceDialog.pick(pet, free, ship -> {
            ships.put(pet.id, ship);
            cargo.remove(pet.id);
        }).show(this::refresh);
    }

    // the fields are made again on every rebuild; an empty field keeps the suggested name
    private void keepNames() {
        for (Map.Entry<String, TextFieldAPI> e : fields.entrySet()) {
            String typed = PetCard.trimName(e.getValue().getText());
            if (typed.isEmpty()) continue;
            for (Pet young : brood.getYoung()) if (young.id.equals(e.getKey())) young.rename(typed);
        }
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return "Confirm";
    }

    @Override
    public String getCancelText() {
        return "Cancel";
    }

    // every opened young goes where it was put; a closed card stays in the habitat
    @Override
    public void customDialogConfirm() {
        keepNames();
        for (Pet young : new ArrayList<>(brood.getYoung())) {
            if (brood.opened.contains(young.id)) shop.placeYoung(brood, young, ships.get(young.id), cargo.contains(young.id));
        }
        dialog.dismiss();
    }

    @Override
    public void customDialogCancel() {
        keepNames();
        dialog.dismiss();
    }
}
