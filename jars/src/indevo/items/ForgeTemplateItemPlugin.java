package indevo.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.GenericSpecialItemPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.items.memory.ItemPayloadMemory;
import indevo.utils.helper.MiscIE;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.utils.helper.MiscIE.addOrIncrement;

public class ForgeTemplateItemPlugin extends GenericSpecialItemPlugin {

    public static final String DROP_MAP_KEY = "$IndEvo_FT_dropResolveMap";
    public static final int MAX_CHARGES = 5;
    public static final String TEMPLATE_QUALITY_LEVEL = "$IndEvo_qualityLevel_";
    public static final int BASE_FT_QUALITY_LEVEL = 0;
    public static final int BASE_DMOD_AMOUNT_PER_APPLICATION = 3;

    // data is hullId|charges, plus |payloadKey when a deconstructed variant is kept in ItemPayloadMemory
    private static final String DATA_SEPARATOR = "|";

    protected ShipHullSpecAPI ship;

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        SpecialItemData data = stack != null ? stack.getSpecialDataIfSpecial() : null;
        String hullId = getHullId(data);
        ship = hullId != null ? Global.getSettings().getHullSpec(hullId) : null;
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult,
                       float glowMult, SpecialItemPlugin.SpecialItemRendererAPI renderer) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;

        float blX = cx - 30f;
        float blY = cy - 15f;
        float tlX = cx - 20f;
        float tlY = cy + 26f;
        float trX = cx + 23f;
        float trY = cy + 26f;
        float brX = cx + 15f;
        float brY = cy - 18f;

        if (ship == null) return;
        String hullId = ship.isDHull() || ship.isDefaultDHull() ? ship.getBaseHullId() : ship.getHullId();

        float mult = 1f;
        //if (known) mult = 0.5f;

        Color bgColor = new Color(70, 100, 80);
        bgColor = Misc.setAlpha(bgColor, 255);

        //float b = Global.getSector().getCampaignUI().getSharedFader().getBrightness() * 0.25f;
        renderer.renderBGWithCorners(bgColor, blX, blY, tlX, tlY, trX, trY, brX, brY,
                alphaMult * mult, glowMult * 0.5f * mult, false);
        renderer.renderShipWithCorners(hullId, null, blX, blY, tlX, tlY, trX, trY, brX, brY,
                alphaMult * mult, glowMult * 0.5f * mult, true /*set to false for darkened BP*/);

        SpriteAPI overlay = Global.getSettings().getSprite("ui", "bpOverlayShip");
        overlay.setColor(Color.green);
        overlay.setColor(Global.getSector().getPlayerFaction().getBrightUIColor());
        overlay.setAlphaMult(alphaMult);
        overlay.setNormalBlend();
        renderer.renderScanlinesWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY, alphaMult, false);

        /* //render dark background overlay if known - might be usable for degraded chips
        boolean known = Global.getSector().getPlayerFaction().knowsShip(hullId);
        if (known) {
            renderer.renderBGWithCorners(Color.black, blX, blY, tlX, tlY, trX, trY, brX, brY,
                    alphaMult * 0.5f, 0f, false);
        }*/

        overlay.renderWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY);
    }

    @Override
    public int getPrice(MarketAPI market, SubmarketAPI submarket) {
        float base = super.getPrice(market, submarket);
        if (ship == null) return (int) base;

        int charges = getCharges(stack.getSpecialDataIfSpecial());
        return (int) (base + ship.getBaseValue() * 0.1f * getItemPriceMult() * (1 + charges * 0.2f));
    }

    @Override
    public String getName() {
        if (ship != null) return ship.getHullName() + "-class Forge Template";
        return super.getName();
    }

    @Override
    public String getDesignType() {
        return ship != null ? "Experimental" : null;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, false);

        float opad = 10f;
        SpecialItemData data = stack != null ? stack.getSpecialDataIfSpecial() : null;

        if (isPayloadMissing(data)) {
            tooltip.addPara("Invalid item: the ship data for %s is missing. Please report this.", opad, Misc.getNegativeHighlightColor(), data.getData());
            addCostLabel(tooltip, opad, transferHandler, stackSource);
            return;
        }

        if (Global.getSector().getMemory().getBoolean("$" + Ids.HULLFORGE)) {
            tooltip.addPara("This Forge Template contains ship data. Installing this in a Hull Forge will allow it to construct the ship.", opad);
        } else {
            tooltip.addPara("This Forge Template contains ship data. It does not seem to fit any standard Domain blueprint readers, but there might be a way to construct a ship based off it. Residual data contains hints towards planets lost to automated fleets in the first AI war.", opad);
        }

        if (ship != null) {
            int c = getCharges(data);
            String s = c > 1 ? c + " charges" : c + " charge";
            tooltip.addPara("Current capacity: %s", opad, Misc.getHighlightColor(), s);

            List<String> hulls = new ArrayList<>();
            hulls.add(ship.getHullId());
            addShipList(tooltip, "Ship hulls:", hulls, 1, opad);

            Description desc = Global.getSettings().getDescription(ship.getDescriptionId(), Description.Type.SHIP);
            String prefix = ship.getDescriptionPrefix() != null ? ship.getDescriptionPrefix() + "\n\n" : "";
            tooltip.addPara(prefix + desc.getText1FirstPara(), opad);
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource);
    }

    @Override
    public String resolveDropParamsToSpecificItemData(String params, Random random) throws JSONException {
        if (params == null || params.isEmpty()) return null;

        JSONObject json = new JSONObject(params);

        Set<String> tags = new HashSet<>();
        if (json.has("tags")) {
            JSONArray tagsArray = json.getJSONArray("tags");
            for (int i = 0; i < tagsArray.length(); i++) {
                tags.add(tagsArray.getString(i));
            }
        }

        String hullId = pickShip(tags, random);
        return hullId != null ? toData(hullId, json.optInt("charges", 1), null) : null;
    }

    public static String pickShip(Set<String> tags, Random random) {
        float MIN_VALUE = 200000f;

        List<ShipHullSpecAPI> specs = Global.getSettings().getAllShipHullSpecs();
        Set<String> restrictedShipSet = new HashSet<>();

        Map<String, Float> dropMap = MiscIE.getMapFromMemory(DROP_MAP_KEY);

        restrictedShipSet.addAll(MiscIE.getVayraBossShips());
        restrictedShipSet.addAll(MiscIE.getPrismBossShips());

        Set<String> allowedShipsInternal = MiscIE.getCSVSetFromMemory(Ids.PRINT_LIST);
        allowedShipsInternal.remove("ziggurat");

        Iterator<ShipHullSpecAPI> iter = specs.iterator();

        if (tags != null && !tags.isEmpty()) {
            iter = specs.iterator();
            while (iter.hasNext()) {
                ShipHullSpecAPI curr = iter.next();
                for (String tag : tags) {
                    boolean not = tag.startsWith("!");
                    tag = not ? tag.substring(1) : tag;
                    boolean has = curr.hasTag(tag);
                    if (not == has) {
                        iter.remove();
                        break;
                    }
                }
            }
        }

        WeightedRandomPicker<ShipHullSpecAPI> picker = new WeightedRandomPicker<>(random);
        for (ShipHullSpecAPI spec : specs) {

            //check if it's worth enough, and is allowed to drop
            if (spec.getBaseValue() <= MIN_VALUE
                    || restrictedShipSet.contains(spec.getHullId())
                    || !allowedShipsInternal.contains(spec.getHullId())
                    || spec.isDHull()
                    || spec.isDefaultDHull()) continue;

            float dropMod = dropMap.get(spec.getHullId()) != null ? dropMap.get(spec.getHullId()) : 1f;
            picker.add(spec, (1 / dropMod));
        }

        ShipHullSpecAPI pick = picker.pick();
        if (pick == null) {
            return null;
        } else {
            addOrIncrement(dropMap, pick.getHullId(), 4f);
            MiscIE.storeMapInMemory(dropMap, DROP_MAP_KEY);
            return pick.getHullId();
        }
    }

    public static void addPrintDefectDMods(FleetMemberAPI member, int qualityLevel, Random random) {
        ShipVariantAPI variant = member.getVariant();
        Set<String> hullmodSet = new HashSet<>(variant.getHullMods());
        List<String> printDefectSet = new LinkedList<>();
        printDefectSet.add(Ids.DEFECTS_LOW);
        printDefectSet.add(Ids.DEFECTS_MED);
        printDefectSet.add(Ids.DEFECTS_HIGH);

        boolean hasDefects = !Collections.disjoint(printDefectSet, hullmodSet);

        //always gets added
        if (!variant.hasHullMod(Ids.PRINTING_INDICATOR)) variant.addPermaMod(Ids.PRINTING_INDICATOR);

        //d-mods get added if quality is above 0
        if (qualityLevel > 0) {
            int normalizedQualityLevel = Math.min(qualityLevel - 1, 2); //adjust to match the index and clamp it to 2 so a quality level of 4 does not exceed the max index

            if (hasDefects) {
                //gets the defect level, if the current quality level is higher than the current defect, apply - otherwise, leave it.
                String levelId = getPrintDefectId(variant);
                int levelIndex = printDefectSet.indexOf(levelId);

                if (levelIndex < normalizedQualityLevel) {
                    variant.removeMod(levelId);
                    variant.addPermaMod(printDefectSet.get(normalizedQualityLevel));
                }

            } else {
                variant.addPermaMod(printDefectSet.get(normalizedQualityLevel));
            }

            //apply D-mods
            for (int i = 0; i < BASE_DMOD_AMOUNT_PER_APPLICATION; i++) {
                if (random.nextFloat() < (qualityLevel / 4f)) {
                    DModManager.addDMods(member, false, 1, random);
                }
            }

            member.updateStats();
        }
    }

    private static String getPrintDefectId(ShipVariantAPI var) {
        Set<String> hullmodSet = new HashSet<>(var.getHullMods());

        for (String s : hullmodSet) {
            if (s.contains("IndEvo_print")) {
                return s;
            }
        }

        return null;
    }

    public static SpecialItemData createForgeTemplate(String hullId, int charges) {
        return new SpecialItemData(ItemIds.FORGETEMPLATE, toData(hullId, charges, null));
    }

    public static SpecialItemData createForgeTemplate(ShipVariantAPI variant, int charges) {
        String payloadKey = ItemPayloadMemory.add(variant);
        return new SpecialItemData(ItemIds.FORGETEMPLATE, toData(variant.getHullSpec().getHullId(), charges, payloadKey));
    }

    public static boolean isForgeTemplate(SpecialItemData data) {
        return data != null && ItemIds.FORGETEMPLATE.equals(data.getId());
    }

    public static String getHullId(SpecialItemData data) {
        String[] parts = split(data);
        return parts != null ? parts[0] : null;
    }

    public static int getCharges(SpecialItemData data) {
        String[] parts = split(data);
        return parts != null && parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    public static ShipVariantAPI getVariant(SpecialItemData data) {
        return ItemPayloadMemory.get(getPayloadKey(data), ShipVariantAPI.class);
    }

    public static boolean isPayloadMissing(SpecialItemData data) {
        String key = getPayloadKey(data);
        return key != null && !ItemPayloadMemory.contains(key);
    }

    public static SpecialItemData useCharge(SpecialItemData data) {
        int charges = getCharges(data) - 1;

        if (charges < 1) {
            ItemPayloadMemory.remove(getPayloadKey(data));
            return new SpecialItemData(ItemIds.BROKENFORGETEMPLATE, null);
        }

        return new SpecialItemData(ItemIds.FORGETEMPLATE, toData(getHullId(data), charges, getPayloadKey(data)));
    }

    private static String getPayloadKey(SpecialItemData data) {
        String[] parts = split(data);
        return parts != null && parts.length > 2 ? parts[2] : null;
    }

    private static String[] split(SpecialItemData data) {
        if (!isForgeTemplate(data) || data.getData() == null) return null;
        return data.getData().split("\\" + DATA_SEPARATOR);
    }

    private static String toData(String hullId, int charges, String payloadKey) {
        String data = hullId + DATA_SEPARATOR + Math.max(1, Math.min(charges, MAX_CHARGES));
        return payloadKey != null ? data + DATA_SEPARATOR + payloadKey : data;
    }

    public static int getForgeTemplateQualityLevel(SpecialItemData data) {
        ShipVariantAPI variant = getVariant(data);
        if (variant == null) return BASE_FT_QUALITY_LEVEL;

        for (String s : variant.getTags()) {
            if (s.contains(TEMPLATE_QUALITY_LEVEL)) {
                char c = s.charAt(s.length() - 1);
                return Character.isDigit(c) ? Character.getNumericValue(c) : BASE_FT_QUALITY_LEVEL;
            }
        }

        return BASE_FT_QUALITY_LEVEL;
    }

    public static void incrementForgeTemplateQualityLevel(SpecialItemData data, int toAdd) {
        ShipVariantAPI variant = getVariant(data);
        if (variant == null) return;

        int newLevel = getForgeTemplateQualityLevel(data) + toAdd;

        String toRemove = null;
        for (String s : variant.getTags()) {
            if (s.contains(TEMPLATE_QUALITY_LEVEL)) toRemove = s;
        }

        if (toRemove != null) variant.removeTag(toRemove);
        variant.addTag(TEMPLATE_QUALITY_LEVEL + newLevel);
    }

    public static FleetMemberAPI createNakedFleetMemberFromForgeTemplate(SpecialItemData data) {
        if (isPayloadMissing(data)) throw new IllegalStateException("Forge template ship data missing for " + data.getData());

        FleetMemberAPI ship;
        ShipVariantAPI variant = getVariant(data);

        if (variant != null) {
            variant = variant.clone();
            variant.setOriginalVariant(null);
            variant.setHullVariantId(Misc.genUID());

            //remove sun_sl hms for compat
            List<String> hmToRemove = new ArrayList<>();
            for (String s : variant.getHullMods()) if (s.contains("sun_sl")) hmToRemove.add(s);
            for (String s : hmToRemove) {
                variant.removeMod(s);
                variant.removePermaMod(s);
            }

            ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);

        } else {
            ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, getValidVariantIdForHullId(getHullId(data)));
        }

        FleetEncounterContext.prepareShipForRecovery(ship, true, true, false, 0.2f, 0f, new Random());
        return ship;
    }

    public static String getValidVariantIdForHullId(String targetId) {
        Iterator variantIter = Global.getSettings().getAllVariantIds().iterator();

        String withHull = targetId + "_Hull";
        Iterator variantIterPlusHull = Global.getSettings().getAllVariantIds().iterator();

        while (variantIterPlusHull.hasNext()) {
            String id = (String) variantIterPlusHull.next();
            if (withHull.equalsIgnoreCase(id)) {
                return id;
            }
        }

        while (variantIter.hasNext()) {
            String id = (String) variantIter.next();
            if (Global.getSettings().getVariant(id).getHullSpec().getHullId().equalsIgnoreCase(targetId)) {
                return id;
            }
        }

        return null;
    }
}
