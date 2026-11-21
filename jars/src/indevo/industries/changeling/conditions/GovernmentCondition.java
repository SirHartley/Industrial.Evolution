package indevo.industries.changeling.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.changeling.dialogue.EffectLines;
import indevo.industries.changeling.industry.ChangelingTarget;
import indevo.industries.changeling.industry.EffectLine;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.industry.population.Governments;
import indevo.industries.changeling.industry.population.OutpostSubIndustry;
import indevo.utils.helper.StringHelper;

import java.util.Collections;
import java.util.List;

public class GovernmentCondition extends BaseMarketConditionPlugin {

    public static final String ID = "IndEvo_government";
    public static final int DAYS_TO_LOCK = 7;

    private SubIndustryAPI current = null;
    private float daysPassed = 0f;
    private boolean locked = false;
    private String appliedName = null;
    private String appliedImage = null;

    public static final ChangelingTarget TARGET = new ChangelingTarget() {
        @Override
        public List<SubIndustryData> getOptions() {
            return Governments.LIST;
        }

        @Override
        public String getCurrentId(MarketAPI market) {
            SubIndustryAPI gov = getGovernment(market);
            return gov == null ? Governments.BASE_ID : gov.getId();
        }

        @Override
        public void setCurrent(MarketAPI market, SubIndustryData data) {
            set(market, data);
        }

        @Override
        public String getTitle() {
            return "Change government";
        }

        @Override
        public String getSubTitle(Industry industry) {
            return industry.getMarket().getName();
        }

        @Override
        public String getHelp() {
            return "A government can be changed until colony size " + Setting.GOVERNMENT_MAX_SIZE.intValue() + ". It becomes permanent " + DAYS_TO_LOCK
                    + " " + StringHelper.getDayOrDays(DAYS_TO_LOCK) + " after it is set. An Anchorage can always be changed.";
        }

        // the base government removes the condition, so it never locks
        @Override
        public String getPermanence(SubIndustryData data) {
            if (Governments.BASE_ID.equals(data.id)) return null;
            return "outpost".equals(data.id) ? "never" : "after " + DAYS_TO_LOCK + " " + StringHelper.getDayOrDays(DAYS_TO_LOCK);
        }

        @Override
        public List<EffectLine> getEffects(SubIndustryData data) {
            return Setting.GOVERNMENT_LARP_MODE.enabled() ? Collections.emptyList() : data.effects;
        }
    };

    public static GovernmentCondition get(MarketAPI market) {
        if (market == null || !market.hasCondition(ID)) return null;
        MarketConditionAPI cond = market.getCondition(ID);
        return cond != null && cond.getPlugin() instanceof GovernmentCondition ? (GovernmentCondition) cond.getPlugin() : null;
    }

    public static SubIndustryAPI getGovernment(MarketAPI market) {
        GovernmentCondition cond = get(market);
        return cond == null ? null : cond.current;
    }

    public static boolean isLocked(MarketAPI market) {
        GovernmentCondition cond = get(market);
        return cond != null && cond.locked;
    }

    public static boolean canChange(MarketAPI market) {
        return Global.getSettings().isDevMode() || (market.getSize() <= Setting.GOVERNMENT_MAX_SIZE.intValue() && !isLocked(market));
    }

    public static Industry getPopulation(MarketAPI market) {
        return market == null ? null : market.getIndustry(Industries.POPULATION);
    }

    public static void set(MarketAPI market, SubIndustryData data) {
        if (Governments.get(data.id) == null) throw new IllegalArgumentException("Unknown government " + data.id);

        Industry pop = getPopulation(market);
        GovernmentCondition cond = get(market);

        if (cond != null && cond.current != null && pop != null) {
            SubIndustryAPI old = cond.current;
            old.init(pop);
            old.unapply();
            for (Industry ind : market.getIndustries()) old.unapplyFromOther(ind);
            cond.clearOverrides(pop);
        }

        if (Governments.BASE_ID.equals(data.id)) {
            if (cond != null) market.removeCondition(ID);
        } else {
            if (cond == null) {
                market.addCondition(ID);
                cond = get(market);
                if (cond == null) throw new IllegalStateException("Government condition missing after add on " + market.getName());
            }

            cond.current = data.newInstance();
        }

        market.reapplyIndustries();
    }

    // null keeps the population's own value, variants return Float.MAX_VALUE for no change
    public static Float getPatherInterest(Industry ind) {
        if (Setting.GOVERNMENT_LARP_MODE.enabled()) return null;

        MarketAPI market = ind.getMarket();
        if (market == null || ind != getPopulation(market)) return null;

        SubIndustryAPI gov = getGovernment(market);
        if (gov == null) return null;

        gov.init(ind);
        float interest = gov.getPatherInterest(ind);
        return interest > 100000000f ? null : interest;
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    public boolean showIcon() {
        return false;
    }

    public SubIndustryAPI getCurrent() {
        return current;
    }

    public void updateOverrides(Industry pop) {
        if (current == null || !(pop instanceof BaseIndustry)) return;
        if (!Industries.POPULATION.equals(pop.getSpec().getId())) return;

        BaseIndustry ind = (BaseIndustry) pop;

        String name = current.getName();
        if (ind.getNameOverride() == null || ind.getNameOverride().equals(appliedName)) {
            ind.setNameOverride(name);
            appliedName = name;
        }

        String image = current.getImageName(market);
        if (ind.getImageOverride() == null || ind.getImageOverride().equals(appliedImage)) {
            SubIndustry.ensureTexture(image);
            ind.setImageOverride(image);
            appliedImage = image;
        }
    }

    public void clearOverrides(Industry pop) {
        if (!(pop instanceof BaseIndustry)) return;
        BaseIndustry ind = (BaseIndustry) pop;

        if (appliedName != null && appliedName.equals(ind.getNameOverride())) ind.setNameOverride(null);
        if (appliedImage != null && appliedImage.equals(ind.getImageOverride())) ind.setImageOverride(null);

        appliedName = null;
        appliedImage = null;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (current == null) return;

        Industry pop = getPopulation(market);
        if (pop == null) return;

        current.init(pop);
        current.advance(amount);

        if (locked || current instanceof OutpostSubIndustry) return;

        daysPassed += Global.getSector().getClock().convertToDays(amount);
        if (daysPassed < DAYS_TO_LOCK) return;

        locked = true;

        MessageIntel intel = new MessageIntel("%s established on " + market.getName(), Misc.getBasePlayerColor(), new String[]{current.getName()});
        intel.addLine(BaseIntelPlugin.BULLET + "Now permanent",
                Misc.getTextColor(),
                new String[]{current.getName()},
                Misc.getHighlightColor());

        intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
        intel.setSound(BaseIntelPlugin.getSoundMajorPosting());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
    }

    @Override
    public String getName() {
        return current == null ? super.getName() : current.getName();
    }

    // the population plugin is never replaced, ChangelingTooltipPatch puts the government's text into its tooltip
    // the sop capital reports id population but keeps its own name, image and description
    public static String getDescriptionOverride(Industry ind) {
        if (ind.getSpec() == null || !Industries.POPULATION.equals(ind.getSpec().getId())) return null;
        SubIndustryAPI gov = getGovernment(ind.getMarket());
        return gov == null ? null : gov.getDescription().getText1();
    }

    // hover keeps the vanilla size line, the lore shows once expanded or where the tooltip can not expand
    public static String getDescriptionOverride(Industry ind, boolean expanded) {
        String lore = getDescriptionOverride(ind);
        if (lore == null || expanded || !ind.isTooltipExpandable()) return lore;

        MarketAPI market = ind.getMarket();
        MarketConditionSpecAPI size = Global.getSettings().getMarketConditionSpec("population_" + market.getSize());
        return size == null ? lore : size.getDesc().replace("$marketName", market.getName()).replace("$MarketName", market.getName());
    }

    // read by ChangelingTooltipPatch for isTooltipExpandable of the governed population
    public static boolean isExpandable(Industry ind) {
        return !Setting.GOVERNMENT_LARP_MODE.enabled() && ind.getMarket() != null && Industries.POPULATION.equals(ind.getId()) && getGovernment(ind.getMarket()) != null;
    }

    public static boolean isOwnImage(Industry ind) {
        GovernmentCondition cond = get(ind.getMarket());
        return cond != null && cond.appliedImage != null && ind instanceof BaseIndustry base && cond.appliedImage.equals(base.getImageOverride());
    }

    // government section on the population tooltip: ongoing effects always, the effect lines once expanded
    public static void addGovernanceSection(TooltipMakerAPI tooltip, Industry pop, float width, boolean expanded) {
        MarketAPI market = pop.getMarket();
        GovernmentCondition cond = get(market);
        if (cond == null || cond.current == null || Setting.GOVERNMENT_LARP_MODE.enabled()) return;

        SubIndustryAPI gov = cond.current;
        gov.init(pop);

        boolean full = expanded || !pop.isTooltipExpandable();
        String warning = gov.getWarning();
        int days = cond.locked || gov instanceof OutpostSubIndustry ? 0 : (int) Math.ceil(DAYS_TO_LOCK - cond.daysPassed);
        if (!full && warning == null && days <= 0 && !gov.hasOngoing()) return;

        float opad = 10f;
        float w = width - 10f;
        tooltip.addSectionHeading("Government", Alignment.MID, opad);

        SubIndustryData data = Governments.get(gov.getId());
        if (full && data != null) EffectLines.add(tooltip, w, data.effects, opad);
        if (warning != null) tooltip.addPara(warning, Misc.getNegativeHighlightColor(), opad);
        if (days > 0) tooltip.addPara("Permanent in %s", opad, Misc.getHighlightColor(), days + " " + StringHelper.getDayOrDays(days));
        gov.addOngoing(tooltip, w, full);
    }
}
