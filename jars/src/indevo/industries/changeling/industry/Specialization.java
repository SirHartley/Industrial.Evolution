package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import indevo.industries.changeling.industry.mining.MiningSpecializations;
import indevo.industries.changeling.industry.refining.RefiningSpecializations;

import java.util.Arrays;
import java.util.List;

public class Specialization implements ChangelingTarget {

    public static final String MOD_SUFFIX = "_IndEvo_specialized";
    public static final String DESC = "Specialized Extraction";

    public static final Specialization MINING = new Specialization("mining", Industries.MINING, "$IndEvo_miningFocus", MiningSpecializations.BASE_ID, MiningSpecializations.LIST);
    public static final Specialization REFINING = new Specialization("refining", Industries.REFINING, "$IndEvo_refiningFocus", RefiningSpecializations.BASE_ID, RefiningSpecializations.LIST);
    public static final List<Specialization> ALL = Arrays.asList(MINING, REFINING);

    public final String tag;
    public final String vanillaId;
    public final String memKey;
    public final String baseId;
    public final List<SubIndustryData> list;

    public Specialization(String tag, String vanillaId, String memKey, String baseId, List<SubIndustryData> list) {
        this.tag = tag;
        this.vanillaId = vanillaId;
        this.memKey = memKey;
        this.baseId = baseId;
        this.list = list;
    }

    public static String getModId(Industry ind) {
        return ind.getId() + MOD_SUFFIX;
    }

    public static void addSupply(Industry ind, String commodityId, int amount) {
        ind.supply(getModId(ind), commodityId, amount, DESC);
    }

    public static void addDemand(Industry ind, String commodityId, int amount) {
        if (ind instanceof BaseIndustry) ((BaseIndustry) ind).demand(getModId(ind), commodityId, amount, DESC);
    }

    // a positive supply() call also writes ind_sb, drop it when nothing else is left on that commodity
    public static void removeDeltas(Industry ind) {
        String key = getModId(ind);

        for (MutableCommodityQuantity q : ind.getAllSupply()) {
            MutableStat stat = q.getQuantity();
            if (!stat.getFlatMods().containsKey(key)) continue;

            stat.unmodifyFlat(key);
            if (stat.getFlatMods().size() == 1 && stat.getFlatMods().containsKey("ind_sb")) stat.unmodifyFlat("ind_sb");
        }

        for (MutableCommodityQuantity q : ind.getAllDemand()) q.getQuantity().unmodifyFlat(key);
    }

    public static Specialization getFor(Industry ind) {
        for (Specialization s : ALL) if (s.appliesTo(ind)) return s;
        return null;
    }

    public boolean appliesTo(Industry ind) {
        return ind.getSpec() != null && ind.getSpec().hasTag(tag);
    }

    public SubIndustryData get(String id) {
        for (SubIndustryData data : list) if (data.id.equals(id)) return data;
        return null;
    }

    public SubIndustryData getData(MarketAPI market) {
        String id = market.getMemoryWithoutUpdate().getString(memKey);
        SubIndustryData data = id == null ? null : get(id);
        return data == null ? get(baseId) : data;
    }

    public boolean isSpecialized(MarketAPI market) {
        return !baseId.equals(getData(market).id);
    }

    @Override
    public List<SubIndustryData> getOptions() {
        return list;
    }

    @Override
    public String getCurrentId(MarketAPI market) {
        return getData(market).id;
    }

    @Override
    public void setCurrent(MarketAPI market, SubIndustryData data) {
        if (get(data.id) == null) throw new IllegalArgumentException("Unknown " + tag + " specialization " + data.id);

        for (Industry ind : market.getIndustries()) if (appliesTo(ind)) removeDeltas(ind);

        if (baseId.equals(data.id)) market.getMemoryWithoutUpdate().unset(memKey);
        else market.getMemoryWithoutUpdate().set(memKey, data.id);

        market.reapplyIndustries();
    }

    public void apply(Industry ind) {
        SubIndustryData data = getData(ind.getMarket());

        removeDeltas(ind);

        if (!baseId.equals(data.id) && ind.isFunctional()) {
            SubIndustry sub = data.newInstance();
            sub.init(ind);
            sub.apply();
        }

        updateOverrides(ind, data);
    }

    private void updateOverrides(Industry ind, SubIndustryData data) {
        if (!(ind instanceof BaseIndustry) || !vanillaId.equals(ind.getSpec().getId())) return;
        BaseIndustry base = (BaseIndustry) ind;

        boolean isBase = baseId.equals(data.id);

        if (base.getNameOverride() == null || isOwnName(base.getNameOverride())) base.setNameOverride(isBase ? null : data.name);

        if (base.getImageOverride() == null || isOwnImage(base.getImageOverride())) {
            String image = isBase ? null : data.imageName;
            SubIndustry.ensureTexture(image);
            base.setImageOverride(image);
        }
    }

    private boolean isOwnName(String name) {
        for (SubIndustryData d : list) if (!baseId.equals(d.id) && d.name.equals(name)) return true;
        return false;
    }

    private boolean isOwnImage(String image) {
        for (SubIndustryData d : list) if (!baseId.equals(d.id) && d.imageName.equals(image)) return true;
        return false;
    }
}
