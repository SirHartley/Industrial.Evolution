package indevo.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.items.specialitemdata.ForgeTemplateData;
import indevo.utils.helper.MiscIE;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.utils.helper.MiscIE.addOrIncrement;

public class ForgeTemplateItemPlugin extends BaseSpecialItemPlugin {

    public static final String DROP_MAP_KEY = "$IndEvo_FT_dropResolveMap";
    protected ShipHullSpecAPI ship; //ship.getHullId()) for ship building

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        ship = Global.getSettings().getHullSpec(stack.getSpecialDataIfSpecial().getData());
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

        String hullId = stack.getSpecialDataIfSpecial().getData();
        if (hullId == null) return;
        ShipHullSpecAPI shipSpec = Global.getSettings().getHullSpec(stack.getSpecialDataIfSpecial().getData());
        if (shipSpec.isDHull() || shipSpec.isDefaultDHull()) hullId = shipSpec.getBaseHullId();

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
        if (ship != null) {
            float base = super.getPrice(market, submarket);
            return (int) ((base + ((ship.getBaseValue() * 0.15f) * getItemPriceMult())) * (1 + (getCharges() * 0.2f)));
        }
        return super.getPrice(market, submarket);
    }

    @Override
    public String getName() {
        if (ship != null) {
            //return ship.getHullName() + " Blueprint";
            return ship.getHullName() + "-class Forge Template";
        }
        return super.getName();
    }

    @Override
    public String getDesignType() {
        if (ship != null) {
            return "Experimental"; //ship.getManufacturer();
        }
        return null;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource);

        float opad = 10f;

        String hullId = stack.getSpecialDataIfSpecial().getData();

        if (Global.getSector().getMemory().getBoolean("$" + Ids.HULLFORGE)) {
            tooltip.addPara("This Forge Template contains ship data. Installing this in a Hull Forge will allow it to construct the ship.", opad);
        } else {
            tooltip.addPara("This Forge Template contains ship data. It does not seem to fit any standard Domain blueprint readers, but there might be a way to construct a ship based off it. Residual data contains hints towards planets lost to automated fleets in the first AI war.", opad);
        }

        int c = getCharges();
        String s = c > 1 ? c + " charges" : c + " charge";
        tooltip.addPara("Current capacity: %s", opad, Misc.getHighlightColor(), new String[]{s});

        List<String> hulls = new ArrayList<>();
        hulls.add(hullId);
        addShipList(tooltip, "Ship hulls:", hulls, 1, opad);
        Description desc = Global.getSettings().getDescription(ship.getDescriptionId(), Description.Type.SHIP);

        String prefix = "";
        if (ship.getDescriptionPrefix() != null) {
            prefix = ship.getDescriptionPrefix() + "\n\n";
        }
        tooltip.addPara(prefix + desc.getText1FirstPara(), opad);

        addCostLabel(tooltip, opad, transferHandler, stackSource);
    }

    private int getCharges() {
        return Integer.parseInt(getId().substring(getId().length() - 1));
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

        return pickShip(tags, random);
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

    public static final String TEMPLATE_QUALITY_LEVEL = "$IndEvo_qualityLevel_";
    public static final int BASE_FT_QUALITY_LEVEL = 0;
    public static final int BASE_DMOD_AMOUNT_PER_APPLICATION = 3;

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

    @Deprecated
    public static void applyPrintDefects(FleetMemberAPI member, String aiCoreId) {
        ShipVariantAPI ship = member.getVariant().clone();
        String ident = "IndEvo_print_";
        String installed = "none";
        String id = getApplicablePrintDefectId(ship, aiCoreId);

        for (String inBuiltMod : ship.getPermaMods()) {
            if (inBuiltMod.contains(ident)) installed = inBuiltMod;
        }

        if (!installed.equals("none")) {
            ship.removePermaMod(installed);
        }

        if (!ship.hasHullMod(Ids.PRINTING_INDICATOR)) ship.addPermaMod(Ids.PRINTING_INDICATOR);
        ship.addPermaMod(id);

        member.setVariant(ship, false, true);
    }

    public static String getApplicablePrintDefectId(ShipVariantAPI ship, String aiCoreId) {
        List<String> levels = new ArrayList<>();
        levels.add("low");
        levels.add("med");
        levels.add("high");

        String ident = "IndEvo_print_";
        int level = 1;

        for (String inBuiltMod : ship.getPermaMods()) {
            if (inBuiltMod.contains(ident)) level = levels.indexOf(inBuiltMod.substring(ident.length()));
        }

        level += Commodities.ALPHA_CORE.equals(aiCoreId) || Commodities.BETA_CORE.equals(aiCoreId) ? 1 : 0;
        level -= Commodities.GAMMA_CORE.equals(aiCoreId) ? 1 : 0;

        level = Math.min(level, 2);
        level = Math.max(level, 0);

        return ident + levels.get(level);
    }

    public static int incrementForgeTemplateQualityLevel(SpecialItemData data, int toAdd) {
        int newLevel = getForgeTemplateQualityLevel(data) + toAdd;
        setForgeTemplateQualityLevel(data, newLevel);

        return newLevel;
    }

    public static int getForgeTemplateQualityLevel(SpecialItemData data) {
        if (data instanceof ForgeTemplateData) {
            ForgeTemplateData ftData = (ForgeTemplateData) data;

            for (String s : ftData.getVariant().getTags()) {
                if (s.contains(TEMPLATE_QUALITY_LEVEL)) {
                    char c = s.charAt(s.length() - 1);
                    return Character.isDigit(c) ? Character.getNumericValue(c) : BASE_FT_QUALITY_LEVEL;
                }
            }
        }

        return BASE_FT_QUALITY_LEVEL;
    }

    public static void setForgeTemplateQualityLevel(SpecialItemData data, int level) {
        if (data instanceof ForgeTemplateData) {
            ForgeTemplateData ftData = (ForgeTemplateData) data;
            ShipVariantAPI var = ftData.getVariant();

            String toRemove = null;
            for (String s : var.getTags()) {
                if (s.contains(TEMPLATE_QUALITY_LEVEL)) {
                    toRemove = s;
                }
            }

            if (toRemove != null) var.removeTag(toRemove);
            var.addTag(TEMPLATE_QUALITY_LEVEL + level);
        }
    }

    public static SpecialItemData incrementForgeTemplateData(SpecialItemData data, int num) {
        if (data == null) return null;

        SpecialItemData newData;

        if (getForgeTemplateCharges(data.getId()) > 1) {
            if (data instanceof ForgeTemplateData) {
                ForgeTemplateData ftData = (ForgeTemplateData) data;
                newData = new ForgeTemplateData(getIncrementedFTId(num, ftData.getId()), ftData.getData(), ftData.getVariant());
            } else {
                newData = new SpecialItemData(getIncrementedFTId(-1, data.getId()), data.getData());
            }

        } else {
            newData = new SpecialItemData(ItemIds.BROKENFORGETEMPLATE, null);
        }

        return newData;
    }

    public static String getIncrementedFTId(Integer increment, String initial) {
        int charges = getForgeTemplateCharges(initial);

        if (charges > 0) {
            return ItemIds.FORGETEMPLATE + "_" + (Math.min(charges + increment, 5));
        } else {
            return ItemIds.BROKENFORGETEMPLATE;
        }
    }

    public static int getForgeTemplateCharges(String id) {
        char c = id.charAt(id.length() - 1);
        return Character.isDigit(c) ? Character.getNumericValue(c) : 0;
    }

    public static FleetMemberAPI createNakedFleetMemberFromForgeTemplate(SpecialItemData forgeTemplateData) {
        FleetMemberAPI ship;
        ForgeTemplateData data;

        if (forgeTemplateData instanceof ForgeTemplateData) {
            data = (ForgeTemplateData) forgeTemplateData;
            ShipVariantAPI variant = data.getVariant();

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
            String var = getValidVariantIdForHullId(forgeTemplateData.getData()); //Global.getSettings().createEmptyVariant(MiscIE.genUID(), Global.getSettings().getHullSpec(forgeTemplateData.getData()));
            ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, var);
        }

        FleetEncounterContext.prepareShipForRecovery(ship, true, true, false, 0.2f, 0f, new Random());
        //stripShipNoCargo(ship);
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

    public static String getForgeTemplateHullID(SpecialItemData templateData) {
        if (templateData instanceof ForgeTemplateData) {
            ForgeTemplateData ftData = (ForgeTemplateData) templateData;
            return ftData.getVariant().getHullSpec().getHullId();
        } else {
            return templateData.getData();
        }
    }

    public static ForgeTemplateData createForgeTemplateData(int charges, String hullId) {
        return new ForgeTemplateData(ItemIds.FORGETEMPLATE + "_" + Math.min(charges, 5), hullId, Global.getSettings().createEmptyVariant(Misc.genUID(), Global.getSettings().getHullSpec(hullId)));
    }

    public static ForgeTemplateData createForgeTemplateData(int charges, String data, ShipVariantAPI variant) {
        return new ForgeTemplateData(ItemIds.FORGETEMPLATE + "_" + Math.min(charges, 5), data, variant);
    }
}