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

import java.awt.*;
import java.util.List;
import java.util.*;

public class VPCItemPlugin extends GenericSpecialItemPlugin {

    public enum Tier {
        COMMON(1, 30000, 70f, 1f, "This relatively common chip contains handling and production protocols for %s."),
        UNCOMMON(1, 50000, 25f, 2f, "This uncommon chip contains handling and production protocols for %s."),
        RARE(2, 100000, 5f, 4f, "This rare, top of the line chip contains handling and production protocols for both %s."),
        ULTRA(3, 200000, 1f, 6f, "This exceedingly rare chip contains handling and production protocols for %s.");

        public final int outputs;
        public final int price;
        public final float npcWeight;
        public final float patherInterest;
        public final String desc;

        Tier(int outputs, int price, float npcWeight, float patherInterest, String desc) {
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
    private static final Color SCREEN_COLOR = new Color(28, 48, 56);

    protected SpecialItemData data;

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
        if (!isValid(data)) return;

        float cx = x + w / 2f;
        float cy = y + h / 2f;

        String icon = getChipIcon(data);
        if (icon != null) {
            renderSprite(Global.getSettings().getSprite(icon), cx, cy, alphaMult, glowMult);
            return;
        }

        Tier tier = getTier(data);
        if (tier == Tier.RARE || tier == Tier.ULTRA) {
            renderSprite(Global.getSettings().getSprite("IndEvo", "VPCRareChip"), cx, cy, alphaMult, glowMult);
        }

        // screen area of the chip art, pixel offsets from the 80px icon center
        float blX = cx - 22f, blY = cy - 5f;
        float tlX = cx - 23f, tlY = cy + 19f;
        float trX = cx + 22f, trY = cy + 19f;
        float brX = cx + 22f, brY = cy - 5f;
        renderer.renderBGWithCorners(SCREEN_COLOR, blX, blY, tlX, tlY, trX, trY, brX, brY, alphaMult, glowMult * 0.5f, false);

        List<Commodity> outputs = getOutputs(data);
        int n = outputs.size();
        float size = n == 1 ? 24f : n == 2 ? 20f : 16f;
        float step = n == 2 ? 22f : 14f;
        float startX = cx - step * (n - 1) / 2f;

        for (int i = 0; i < n; i++) {
            SpriteAPI sprite = Global.getSettings().getSprite(Global.getSettings().getCommoditySpec(outputs.get(i).id()).getIconName());
            sprite.setSize(size, size);
            renderSprite(sprite, startX + step * i, cy + 7f, alphaMult, glowMult);
        }

        renderer.renderScanlinesWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY, alphaMult, false);
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
        Tier tier = tierName.isEmpty() ? Tier.COMMON : Tier.valueOf(tierName.toUpperCase());
        SpecialItemData item = roll(tier, random);
        return item != null ? item.getData() : null;
    }

    public static SpecialItemData createItem(Tier tier, List<String> commodityIds) {
        return new SpecialItemData(ItemIds.VPC, tier.name().toLowerCase() + TIER_SEPARATOR + String.join(COMMODITY_SEPARATOR, commodityIds));
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
        return data != null && ItemIds.VPC.equals(data.getId());
    }

    public static boolean isValid(SpecialItemData data) {
        return getTier(data) != null && !getCommodityIds(data).isEmpty();
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

    public static String getIconName(SpecialItemData data) {
        String icon = isValid(data) ? getChipIcon(data) : null;
        return icon != null ? icon : Global.getSettings().getSpecialItemSpec(data.getId()).getIconName();
    }

    private static String getChipIcon(SpecialItemData data) {
        List<Commodity> outputs = getOutputs(data);
        if (outputs.size() == 1) return outputs.get(0).icon().isEmpty() ? null : outputs.get(0).icon();

        VPCCommodityRepo.Package p = VPCCommodityRepo.getPackage(getCommodityIds(data));
        return p != null && !p.icon().isEmpty() ? p.icon() : null;
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

    private static void renderSprite(SpriteAPI sprite, float x, float y, float alphaMult, float glowMult) {
        sprite.setNormalBlend();
        sprite.setAlphaMult(alphaMult);
        sprite.renderAtCenter(x, y);

        if (glowMult > 0) {
            sprite.setAdditiveBlend();
            sprite.setAlphaMult(alphaMult * glowMult * 0.5f);
            sprite.renderAtCenter(x, y);
        }
    }
}
