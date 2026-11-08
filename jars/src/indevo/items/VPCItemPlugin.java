package indevo.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.GenericSpecialItemPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.ItemIds;
import indevo.items.memory.VPCCommodityRepo;
import indevo.items.memory.VPCCommodityRepo.Commodity;
import indevo.items.memory.VPCCommodityRepo.Pool;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;

public class VPCItemPlugin extends GenericSpecialItemPlugin {

    public enum Tier {
        COMMON(ItemIds.VPC, 1, 30000, 70f, 1f, "This relatively common chip contains handling and production protocols for %s."),
        UNCOMMON(ItemIds.VPC, 1, 50000, 25f, 2f, "This uncommon chip contains handling and production protocols for %s."),
        RARE(ItemIds.VPC_RARE, 2, 100000, 5f, 4f, "This rare, top of the line chip contains handling and production protocols for both %s."),
        ULTRA(ItemIds.VPC_ULTRA, 3, 200000, 1f, 6f, "This exceedingly rare chip contains handling and production protocols for %s.");

        public final String itemId;
        public final int outputs;
        public final int price;
        public final float npcWeight;
        public final float patherInterest;
        public final String desc;

        Tier(String itemId, int outputs, int price, float npcWeight, float patherInterest, String desc) {
            this.itemId = itemId;
            this.outputs = outputs;
            this.price = price;
            this.npcWeight = npcWeight;
            this.patherInterest = patherInterest;
            this.desc = desc;
        }

        public String getName() {
            return this == ULTRA ? "Ultra rare" : Misc.ucFirst(name().toLowerCase());
        }

        public boolean canRoll(Commodity commodity) {
            return switch (this) {
                case COMMON -> commodity.pool() == Pool.COMMON;
                case UNCOMMON -> commodity.pool() == Pool.UNCOMMON;
                default -> true;
            };
        }

        public static Tier getLowest(String itemId) {
            for (Tier tier : values()) {
                if (tier.itemId.equals(itemId)) return tier;
            }

            return null;
        }

        public static Tier pickForNPC(Random random) {
            WeightedRandomPicker<Tier> picker = new WeightedRandomPicker<>(random);
            for (Tier tier : values()) picker.add(tier, tier.npcWeight);
            return picker.pick();
        }
    }

    // data is tier|commodityId,commodityId, e.g. rare|supplies,fuel
    private static final String TIER_SEPARATOR = "|";
    private static final String COMMODITY_SEPARATOR = ",";
    private static final float[] DEPOSIT_MULT = {1f, 0.8f, 0.65f};
    // per output count; screen corners in icon px from the top left: tl, tr, br, bl
    private static final Chip[] CHIPS = {
            new Chip("VPCOverlay1", 24f, 18f, 24f, 58f, 19f, 64f, 46f, 24f, 52f),
            new Chip("VPCOverlay2", 18f, 12f, 23.5f, 63f, 23.5f, 66f, 45.5f, 18f, 52.5f),
            new Chip("VPCOverlay3", 15f, 10.5f, 25.5f, 64f, 21f, 70f, 45f, 16f, 53f)
    };
    private static final float ICON_GAP = 0.04f;
    private static final float OVERLAY_ALPHA = 0.4f;
    private static final float GLOW_ALPHA = 0.9f;
    private static final float GLOW_SIZE = 1.9f;
    private static final int CONTENT_MIN_ALPHA = 40;
    private static final float[] FULL_BOUNDS = {0f, 0f, 1f, 1f};
    private static final Map<String, float[]> CONTENT_BOUNDS = new HashMap<>();
    // render and apply read the outputs every frame, warn once per id
    private static final Set<String> WARNED_IDS = new HashSet<>();

    protected SpecialItemData data;

    private static class Chip {
        private final String overlay;
        private final float iconSize;
        private final Vector2f tl, tr, br, bl;
        private final Vector2f u, v;

        private Chip(String overlay, float iconSize, float... corners) {
            this.overlay = overlay;
            this.iconSize = iconSize;
            tl = toCenter(corners[0], corners[1]);
            tr = toCenter(corners[2], corners[3]);
            br = toCenter(corners[4], corners[5]);
            bl = toCenter(corners[6], corners[7]);
            // icons follow the screen tilt: average direction of the opposite edges
            u = average(Vector2f.sub(tr, tl, null), Vector2f.sub(br, bl, null));
            v = average(Vector2f.sub(bl, tl, null), Vector2f.sub(br, tr, null));
        }

        private Vector2f getSlot(int i, int n) {
            float s = n == 1 ? 0.5f : 0.5f + (i - (n - 1) / 2f) * (1f - 2f * ICON_GAP) / n;
            Vector2f top = lerp(tl, tr, s);
            Vector2f bottom = lerp(bl, br, s);
            return lerp(top, bottom, 0.5f);
        }

        private void renderInBox(SpriteAPI sprite, Vector2f c, float w, float h) {
            float uw = w / 2f, vh = h / 2f;
            sprite.renderWithCorners(
                    c.x - u.x * uw + v.x * vh, c.y - u.y * uw + v.y * vh,
                    c.x - u.x * uw - v.x * vh, c.y - u.y * uw - v.y * vh,
                    c.x + u.x * uw - v.x * vh, c.y + u.y * uw - v.y * vh,
                    c.x + u.x * uw + v.x * vh, c.y + u.y * uw + v.y * vh);
        }

        private static Vector2f toCenter(float px, float py) {
            return new Vector2f(px - 40f, 40f - py);
        }

        private static Vector2f lerp(Vector2f a, Vector2f b, float t) {
            return new Vector2f(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
        }

        private static Vector2f average(Vector2f a, Vector2f b) {
            a.normalise();
            b.normalise();
            Vector2f sum = Vector2f.add(a, b, null);
            sum.normalise();
            return sum;
        }
    }

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        data = stack != null ? stack.getSpecialDataIfSpecial() : null;
    }

    @Override
    public String getName() {
        return isValid(data) ? getDisplayName(data) : super.getName();
    }

    @Override
    public int getPrice(MarketAPI market, SubmarketAPI submarket) {
        return isValid(data) ? getTier(data).price : super.getPrice(market, submarket);
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;

        // the engine draws the chip (spec icon) and its hover highlight before this
        if (!isValid(data)) return;

        List<Commodity> outputs = getOutputs(data);
        Chip chip = CHIPS[getTier(data).outputs - 1];

        Color bright = Global.getSettings().getBrightPlayerColor();
        SpriteAPI overlay = Global.getSettings().getSprite("IndEvo", chip.overlay);
        overlay.setNormalBlend();
        overlay.setColor(bright);
        overlay.setAlphaMult(alphaMult * Math.min(1f, OVERLAY_ALPHA * (1f + 0.5f * glowMult)));
        overlay.renderAtCenter(cx, cy);

        int n = outputs.size();
        for (int i = 0; i < n; i++) {
            Vector2f c = chip.getSlot(i, n);
            c.set(cx + c.x, cy + c.y);

            SpriteAPI glow = Global.getSettings().getSprite("IndEvo", "VPCGlow");
            glow.setAdditiveBlend();
            glow.setColor(bright);
            glow.setAlphaMult(alphaMult * GLOW_ALPHA * (1f + 0.6f * glowMult));
            chip.renderInBox(glow, c, chip.iconSize * GLOW_SIZE, chip.iconSize * GLOW_SIZE);

            String path = Global.getSettings().getCommoditySpec(outputs.get(i).id()).getIconName();
            SpriteAPI icon = Global.getSettings().getSprite(path);
            float[] b = getContentBounds(path);
            icon.setTexX(b[0] * icon.getTextureWidth());
            icon.setTexY(b[1] * icon.getTextureHeight());
            icon.setTexWidth(b[2] * icon.getTextureWidth());
            icon.setTexHeight(b[3] * icon.getTextureHeight());
            icon.setNormalBlend();
            icon.setAlphaMult(alphaMult);

            float iw = b[2] * icon.getWidth();
            float ih = b[3] * icon.getHeight();
            float scale = chip.iconSize / Math.max(iw, ih);
            chip.renderInBox(icon, c, iw * scale, ih * scale);
        }
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        float opad = 10f;

        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle(getName());
        else tooltip.addSpacer(-opad);

        tooltip.setBulletWidth(86f);
        tooltip.setBulletColor(Misc.getGrayColor());
        tooltip.setBulletedListMode("Installed in:");
        addInstalledInSection(tooltip, opad);
        tooltip.setBulletedListMode(null);

        if (Global.CODEX_TOOLTIP_MODE) tooltip.setParaSmallInsignia();
        tooltip.addPara(spec.getDesc(), opad);

        ItemEffectsRepo.ITEM_EFFECTS.get(getId()).addItemDescription(null, tooltip, data, InstallableItemDescriptionMode.CARGO_TOOLTIP);

        String flavor = getFlavor(data);
        if (!flavor.isEmpty()) tooltip.addPara(flavor, Misc.getGrayColor(), opad);

        addCostLabel(tooltip, opad, transferHandler, stackSource);
    }

    @Override
    public String resolveDropParamsToSpecificItemData(String params, Random random) throws JSONException {
        String tierName = params == null || params.isEmpty() ? "" : new JSONObject(params).optString("tier", "");
        Tier tier = tierName.isEmpty() ? Tier.getLowest(getId()) : Tier.valueOf(tierName.toUpperCase());
        if (!tier.itemId.equals(getId())) {
            VPCCommodityRepo.log.warn("VPC drop tier " + tierName + " does not belong to " + getId() + ", using its lowest tier");
            tier = Tier.getLowest(getId());
        }

        SpecialItemData item = roll(tier, random);
        return item != null ? item.getData() : null;
    }

    public static SpecialItemData createItem(Tier tier, List<String> commodityIds) {
        return new SpecialItemData(tier.itemId, tier.name().toLowerCase() + TIER_SEPARATOR + String.join(COMMODITY_SEPARATOR, commodityIds));
    }

    public static SpecialItemData roll(Tier tier, Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        for (Commodity c : VPCCommodityRepo.getAll()) {
            if (tier.canRoll(c)) picker.add(c.id());
        }

        List<String> picked = new ArrayList<>();
        while (picked.size() < tier.outputs && !picker.isEmpty()) picked.add(picker.pickAndRemove());

        return picked.isEmpty() ? null : createItem(tier, picked);
    }

    public static SpecialItemData rollForNPC(Random random) {
        return roll(Tier.pickForNPC(random), random);
    }

    public static boolean isVPC(SpecialItemData data) {
        return data != null && Tier.getLowest(data.getId()) != null;
    }

    public static boolean isValid(SpecialItemData data) {
        Tier tier = getTier(data);
        if (tier == null || !tier.itemId.equals(data.getId())) return false;

        for (String id : getCommodityIds(data)) {
            if (VPCCommodityRepo.get(id) != null) return true;
        }

        return false;
    }

    public static Tier getTier(SpecialItemData data) {
        if (!isVPC(data) || data.getData() == null || !data.getData().contains(TIER_SEPARATOR)) return null;

        String name = data.getData().substring(0, data.getData().indexOf(TIER_SEPARATOR)).toUpperCase();
        for (Tier tier : Tier.values()) {
            if (tier.name().equals(name)) return tier;
        }

        return null;
    }

    public static List<String> getCommodityIds(SpecialItemData data) {
        List<String> ids = new ArrayList<>();
        if (!isVPC(data) || data.getData() == null || !data.getData().contains(TIER_SEPARATOR)) return ids;

        String list = data.getData().substring(data.getData().indexOf(TIER_SEPARATOR) + 1);
        for (String id : list.split(COMMODITY_SEPARATOR)) {
            if (!id.isBlank()) ids.add(id.trim());
        }

        return ids;
    }

    public static List<Commodity> getOutputs(SpecialItemData data) {
        if (!isValid(data)) throw new IllegalStateException("VPC without valid production data: " + (data == null ? null : data.getData()));

        List<Commodity> outputs = new ArrayList<>();
        for (String id : getCommodityIds(data)) {
            Commodity c = VPCCommodityRepo.get(id);
            if (c != null) outputs.add(c);
            else if (WARNED_IDS.add(id)) VPCCommodityRepo.log.warn("VPC commodity " + id + " is not in the VPC table, leaving it out");
        }

        return outputs;
    }

    public static Map<String, Integer> getSupply(SpecialItemData data) {
        List<Commodity> outputs = getOutputs(data);
        int n = outputs.size();

        Map<String, Integer> supply = new LinkedHashMap<>();
        for (Commodity c : outputs) supply.put(c.id(), n > 1 ? Math.min(c.supply(), -n) : c.supply());

        return supply;
    }

    public static Map<String, Integer> getDemand(SpecialItemData data) {
        Map<String, Integer> demand = new LinkedHashMap<>();
        for (Commodity c : getOutputs(data)) {
            for (Map.Entry<String, Integer> e : c.demand().entrySet()) demand.merge(e.getKey(), e.getValue(), Math::max);
        }

        return demand;
    }

    public static float getDepositMult(SpecialItemData data) {
        int n = getOutputs(data).size();
        return DEPOSIT_MULT[Math.max(0, Math.min(n, DEPOSIT_MULT.length) - 1)];
    }

    public static String getDisplayName(SpecialItemData data) {
        if (!isValid(data)) return "VPC";

        List<Commodity> outputs = getOutputs(data);
        if (outputs.size() == 1) return "VPC " + getName(outputs.get(0));

        VPCCommodityRepo.Package p = VPCCommodityRepo.getPackage(getCommodityIds(data));
        if (p != null) return "VPC " + p.name();

        return outputs.size() == 2 ? "VPC Package" : "VPC Suite";
    }

    public static String getOutputString(SpecialItemData data) {
        List<String> names = new ArrayList<>();
        for (Commodity c : getOutputs(data)) names.add(Global.getSettings().getCommoditySpec(c.id()).getName().toLowerCase());
        return Misc.getAndJoined(names);
    }

    // x and y from the bottom left, as fractions of the image, texture rows are stored bottom up
    private static float[] getContentBounds(String path) {
        return CONTENT_BOUNDS.computeIfAbsent(path, VPCItemPlugin::readContentBounds);
    }

    private static float[] readContentBounds(String path) {
        BufferedImage image;
        try (InputStream in = Global.getSettings().openStream(path)) {
            image = ImageIO.read(in);
        } catch (IOException e) {
            VPCCommodityRepo.log.warn("Could not read VPC icon " + path + ", drawing it uncropped", e);
            return FULL_BOUNDS;
        }

        if (image == null) {
            VPCCommodityRepo.log.warn("Could not decode VPC icon " + path + ", drawing it uncropped");
            return FULL_BOUNDS;
        }

        int w = image.getWidth();
        int h = image.getHeight();
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((image.getRGB(x, y) >>> 24) <= CONTENT_MIN_ALPHA) continue;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
            }
        }

        if (x1 < 0) return FULL_BOUNDS;
        return new float[]{x0 / (float) w, (h - 1 - y1) / (float) h, (x1 - x0 + 1) / (float) w, (y1 - y0 + 1) / (float) h};
    }

    private static String getFlavor(SpecialItemData data) {
        if (!isValid(data)) return "";

        List<Commodity> outputs = getOutputs(data);
        if (outputs.size() == 1) return outputs.get(0).desc();

        VPCCommodityRepo.Package p = VPCCommodityRepo.getPackage(getCommodityIds(data));
        return p != null ? p.desc() : "";
    }

    private static String getName(Commodity c) {
        return c.name().isEmpty() ? Global.getSettings().getCommoditySpec(c.id()).getName() : c.name();
    }
}
