package indevo.industries.changeling.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.loading.Description;
import indevo.industries.changeling.industry.mining.MiningSpecializations;
import indevo.industries.changeling.industry.refining.RefiningSpecializations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Specialization implements ChangelingTarget {

    public static final String MOD_SUFFIX = "_IndEvo_specialized";
    public static final String DESC = "Specialized Extraction";

    public static final Specialization MINING = new Specialization("mining", Industries.MINING, "$IndEvo_miningFocus", MiningSpecializations.BASE_ID, MiningSpecializations.LIST,
            Arrays.asList(Commodities.ORE, Commodities.RARE_ORE, Commodities.ORGANICS, Commodities.VOLATILES));
    public static final Specialization REFINING = new Specialization("refining", Industries.REFINING, "$IndEvo_refiningFocus", RefiningSpecializations.BASE_ID, RefiningSpecializations.LIST,
            Arrays.asList(Commodities.METALS, Commodities.RARE_METALS, Commodities.ORE, Commodities.RARE_ORE));
    public static final List<Specialization> ALL = Arrays.asList(MINING, REFINING);

    public final String tag;
    public final String vanillaId;
    public final String memKey;
    public final String baseId;
    public final List<SubIndustryData> list;
    public final List<String> commodities;

    public Specialization(String tag, String vanillaId, String memKey, String baseId, List<SubIndustryData> list, List<String> commodities) {
        this.tag = tag;
        this.vanillaId = vanillaId;
        this.memKey = memKey;
        this.baseId = baseId;
        this.list = list;
        this.commodities = commodities;
    }

    public static String getModId(Industry ind) {
        return ind.getId() + MOD_SUFFIX;
    }

    public static void addSupply(Industry ind, String commodityId, float percent) {
        int amount = getDelta(ind.getSupply(commodityId), percent);
        if (amount != 0) ind.supply(getModId(ind), commodityId, amount, DESC);
    }

    public static void addDemand(Industry ind, String commodityId, float percent) {
        int amount = getDelta(ind.getDemand(commodityId), percent);
        if (amount != 0 && ind instanceof BaseIndustry) ((BaseIndustry) ind).demand(getModId(ind), commodityId, amount, DESC);
    }

    // percent of the line the host left, as a flat mod since the vanilla commodity tooltip only lists flat mods
    private static int getDelta(MutableCommodityQuantity line, float percent) {
        float delta = Math.max(0f, line.getQuantity().getModifiedValue()) * percent / 100f;
        return (int) Math.signum(delta) * Math.round(Math.abs(delta));
    }

    // a positive supply() call also writes ind_sb, drop it when nothing else is left on that commodity
    // getAllSupply and getAllDemand skip lines at 0 or below, where a malus on an unproduced commodity sits
    public static void removeDeltas(Industry ind) {
        String key = getModId(ind);
        Specialization spec = getFor(ind);

        List<MutableCommodityQuantity> supply = new ArrayList<>(ind.getAllSupply());
        List<MutableCommodityQuantity> demand = new ArrayList<>(ind.getAllDemand());
        if (spec != null) {
            for (String id : spec.commodities) {
                supply.add(ind.getSupply(id));
                demand.add(ind.getDemand(id));
            }
        }

        for (MutableCommodityQuantity q : supply) {
            MutableStat stat = q.getQuantity();
            if (!stat.getFlatMods().containsKey(key)) continue;

            stat.unmodifyFlat(key);
            if (stat.getFlatMods().size() == 1 && stat.getFlatMods().containsKey("ind_sb")) stat.unmodifyFlat("ind_sb");
        }

        for (MutableCommodityQuantity q : demand) q.getQuantity().unmodifyFlat(key);
    }

    // only the vanilla host takes the variant's name, image and description, modded tiers keep their own
    public static String getDescriptionOverride(Industry ind) {
        Specialization spec = getFor(ind);
        if (spec == null || ind.getMarket() == null || !spec.vanillaId.equals(ind.getSpec().getId())) return null;

        SubIndustryData data = spec.getData(ind.getMarket());
        return spec.baseId.equals(data.id) ? null : Global.getSettings().getDescription(data.descriptionID, Description.Type.CUSTOM).getText1();
    }

    public static boolean isOwnImage(Industry ind) {
        Specialization spec = getFor(ind);
        return spec != null && ind instanceof BaseIndustry base && base.getImageOverride() != null && spec.isOwnImage(base.getImageOverride());
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
    public String getTitle() {
        return "Change specialization";
    }

    @Override
    public String getSubTitle(Industry industry) {
        return industry.getSpec().getName() + ", " + industry.getMarket().getName();
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public String getPermanence(SubIndustryData data) {
        return null;
    }

    @Override
    public List<EffectLine> getEffects(SubIndustryData data) {
        return data.effects;
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
