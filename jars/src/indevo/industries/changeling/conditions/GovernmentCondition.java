package indevo.industries.changeling.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.changeling.industry.ChangelingTarget;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.industry.population.Governments;
import indevo.industries.changeling.industry.population.OutpostSubIndustry;
import indevo.utils.helper.StringHelper;

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

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle(getName(), market.getTextColorForFactionOrPlanet());
        if (current == null) return;

        float opad = 10f;
        tooltip.addPara(current.getDescription().getText1(), opad);
        tooltip.addPara(current.getDescription().getText2(), opad);
        addLockInfo(tooltip, market);
    }

    // governance section on the population tooltip, the population plugin is never replaced
    public static void addGovernanceSection(TooltipMakerAPI tooltip, Industry pop, Industry.IndustryTooltipMode mode) {
        MarketAPI market = pop.getMarket();
        SubIndustryAPI gov = getGovernment(market);
        if (gov == null && !market.isPlayerOwned()) return;

        float opad = 10f;
        tooltip.addSectionHeading("Governance Type", Alignment.MID, opad);

        if (gov == null || Setting.GOVERNMENT_LARP_MODE.enabled()) {
            SubIndustryData base = Governments.get(Governments.BASE_ID);
            tooltip.addPara(Global.getSettings().getDescription(base.descriptionID, Description.Type.CUSTOM).getText2(), opad);
        } else {
            gov.init(pop);
            tooltip.addPara(gov.getDescription().getText2(), opad);
            gov.addRightAfterDescription(tooltip, mode);
        }

        if (!canChange(market) || !market.isPlayerOwned()) return;

        addLockInfo(tooltip, market);
        tooltip.addPara("%s", opad, Misc.getPositiveHighlightColor(), "Click to change government type.");
    }

    public static void addLockInfo(TooltipMakerAPI tooltip, MarketAPI market) {
        if (!canChange(market)) return;

        float opad = 10f;
        SubIndustryAPI gov = getGovernment(market);
        GovernmentCondition cond = get(market);

        if (gov instanceof OutpostSubIndustry) {
            tooltip.addPara("An Anchorage can always be changed to a different government style.", opad);
            return;
        }

        tooltip.addPara("Changing the government style is only possible until %s and becomes permanent after %s.", opad, Misc.getHighlightColor(),
                "colony size " + Setting.GOVERNMENT_MAX_SIZE.intValue(),
                DAYS_TO_LOCK + " " + StringHelper.getDayOrDays(DAYS_TO_LOCK));

        if (gov != null && cond != null && !cond.locked) {
            int daysRemaining = (int) Math.ceil(DAYS_TO_LOCK - cond.daysPassed);
            tooltip.addPara("Days until permanent: %s", opad, Misc.getHighlightColor(), daysRemaining + " " + StringHelper.getDayOrDays(daysRemaining));
        }
    }
}
