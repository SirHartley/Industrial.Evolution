package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.economy.RelicSpeedUpTarget;
import indevo.ids.Ids;
import indevo.industries.derelicts.RiftBlockers;
import indevo.industries.derelicts.RiftTargetArea;
import indevo.industries.derelicts.RiftTargetMode;
import indevo.industries.derelicts.RiftTargets;
import indevo.industries.derelicts.scripts.RiftJumpScript;
import indevo.industries.derelicts.utils.IndustrySpecView;
import indevo.utils.ModPlugin;
import indevo.utils.timers.NewDayListener;
import org.apache.log4j.Logger;

import java.util.Random;

import static indevo.industries.derelicts.industry.Ruins.INDUSTRY_ID_MEMORY_KEY;

public class RiftGenerator extends BaseIndustry implements NewDayListener, RelicSpeedUpTarget {

    public static final Logger log = Global.getLogger(RiftGenerator.class);

    public static final int ALPHA_DAY_REDUCTION = 30;
    public static final int BETA_RANGE_INCREASE = 3;
    public static final int GAMMA_TEAR_MULT = 2;

    private int daysPassed = 999;
    private boolean jumping = false;
    private transient IndustrySpecView specView = null;

    // the csv downgrade only keeps it out of the build list, an instance never offers it
    @Override
    public IndustrySpecAPI getSpec() {
        IndustrySpecAPI base = super.getSpec();
        specView = IndustrySpecView.of(specView, base, base.getUpgrade(), null);
        return specView;
    }

    @Override
    public void apply() {
        super.apply(true);

        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListener(this)) listeners.addListener(this, true);
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
    }

    @Override
    public boolean isAvailableToBuild() {
        String id = market.getMemoryWithoutUpdate().getString(INDUSTRY_ID_MEMORY_KEY);
        boolean fromRuins = getId().equals(id) && market.hasIndustry(Ids.RUINS) && !market.getIndustry(Ids.RUINS).isUpgrading();
        return fromRuins && super.isAvailableToBuild();
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    public void onNewDay() {
        if (isFunctional()) daysPassed++;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!market.isPlanetConditionMarketOnly() && !market.hasCondition(Ids.COND_CRYODISABLE)) {
            market.addCondition(Ids.COND_CRYODISABLE);
            ModPlugin.log("Adding cryo_disabler to market " + market.getId());
        }
    }

    public boolean isJumping() {
        return jumping;
    }

    public boolean isReady() {
        return getDaysUntilReady() <= 0 || Global.getSettings().isDevMode();
    }

    public int getDaysUntilReady() {
        return Math.max(getDaysRequired() - daysPassed, 0);
    }

    public RiftBlockers.Reason getBlockReason() {
        if (jumping) return new RiftBlockers.Reason("The Generator is charging for a jump.");

        if (!isReady()) {
            int days = getDaysUntilReady();
            return new RiftBlockers.Reason("The Generator is recharging. Ready in " + days + (days == 1 ? " day." : " days."), days + "");
        }

        RiftBlockers.Reason reason = RiftBlockers.getReason(market);
        if (reason != null) return reason;

        if (!getTargets().hasAnyTarget()) return new RiftBlockers.Reason("No star system in range can take the planet.");
        return null;
    }

    public RiftTargets getTargets() {
        return new RiftTargets(market, getRangeLY());
    }

    public void fire(RiftTargetMode mode, Random random) {
        RiftTargetArea area = getTargets().getArea(mode);
        if (area.isEmpty()) throw new IllegalStateException("fired " + mode + " on " + market.getId() + " without a target, the option should be disabled");
        fire(area.pick(random));
    }

    public void fire(StarSystemAPI target) {
        RiftBlockers.Reason reason = getBlockReason();
        if (reason != null) throw new IllegalStateException("fired the rift generator on " + market.getId() + " while blocked: " + reason.text());
        if (!getTargets().isEligible(target)) throw new IllegalStateException("fired the rift generator on " + market.getId() + " at ineligible " + target);

        log.info("rift jump of " + market.getId() + " to " + target.getId());
        jumping = true;
        daysPassed = 0;
        RecentUnrest.get(market).add(1, "Recent Rift Generator usage");
        Global.getSector().addScript(new RiftJumpScript(market, target, getTearDays()));
    }

    public void onJumpDone() {
        jumping = false;
    }

    public float getRangeLY() {
        int range = Setting.RG_RANGE.intValue();
        return Commodities.BETA_CORE.equals(getAICoreId()) ? range + BETA_RANGE_INCREASE : range;
    }

    public int getDaysRequired() {
        int days = Setting.RG_COOLDOWN_TIME.intValue();
        return Commodities.ALPHA_CORE.equals(getAICoreId()) ? Math.max(days - ALPHA_DAY_REDUCTION, 0) : days;
    }

    public int getTearDays() {
        int days = Math.max(getDaysRequired(), 1);
        return Commodities.GAMMA_CORE.equals(getAICoreId()) ? days * GAMMA_TEAR_MULT : days;
    }

    public int getPreciseCost() {
        return Setting.RG_PRECISE_COST.intValue();
    }

    @Override
    protected String getDescriptionOverride() {
        if (currTooltipMode == null || currTooltipMode != IndustryTooltipMode.NORMAL) {
            return "The claustrophobic tunnels of the underground facility eventually give way to a cavern with a massive center structure atop a spider web of power lines. Its purpose is unknown.";
        } else
            return "There is a suspicious absence of walkways and doors in the interiors of this facility. Data found on the extremely advanced network nodes wired through the machinery indicate it as a prototype large scale displacer unit."
                    + "\n\nThe control center is only reachable by crawling through a service tunnel. It was seemingly added as an afterthought.";
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        if (!isFunctional() || mode != IndustryTooltipMode.NORMAL) return;

        RiftBlockers.Reason reason = getBlockReason();
        if (reason == null) {
            tooltip.addPara("The Generator is %s.", 10f, Misc.getPositiveHighlightColor(), "ready to fire");
        } else if (!isReady()) {
            int days = getDaysUntilReady();
            tooltip.addPara("The Generator is %s, ready in %s" + (days == 1 ? " day." : " days."), 10f, Misc.getHighlightColor(), "recharging", days + "");
        } else if (!jumping) {
            tooltip.addPara("The Generator is %s.", 10f, Misc.getNegativeHighlightColor(), "unable to fire");
            tooltip.addPara(reason.text(), 2f, Misc.getHighlightColor(), reason.highlights());
        }

        tooltip.addPara("Jump range: %s light years", 2f, Misc.getHighlightColor(), Math.round(getRangeLY()) + "");
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostDescriptionSection(tooltip, mode);
        if (jumping) tooltip.addPara("Preparing to move the planet, can not be shut down or accessed.", 10f, Misc.getNegativeHighlightColor());
    }

    @Override
    public boolean canShutDown() {
        return !jumping;
    }

    @Override
    public boolean showShutDown() {
        return false;
    }

    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addCoreDescription(tooltip, mode, "Alpha", "Reduces the Generator recharge time by %s.", ALPHA_DAY_REDUCTION + " days");
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addCoreDescription(tooltip, mode, "Beta", "Increases jump range by %s.", BETA_RANGE_INCREASE + " light years");
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addCoreDescription(tooltip, mode, "Gamma", "Keeps the Artificial Hyperspace Tear open %s as long.", "twice");
    }

    private void addCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode, String level, String text, String... highlights) {
        float opad = 10f;
        boolean list = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP;
        String pre = level + "-level AI core" + (list ? ". " : " currently assigned. ");

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI image = tooltip.beginImageWithText(coreSpec.getIconName(), 48f);
            image.addPara(pre + text, 0f, Misc.getHighlightColor(), highlights);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + text, opad, Misc.getHighlightColor(), highlights);
        }
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    @Override
    public String getSpeedUpId() {
        return getId();
    }

    @Override
    public String getSpeedUpLabel() {
        return "Recharge";
    }

    @Override
    public String getSpeedUpLore() {
        return "The colony grid charges the generator a little at a time. A set of Domain power cells can fill its capacitors in a single discharge.";
    }

    @Override
    public int getSpeedUpDaysLeft() {
        return getDaysUntilReady();
    }

    @Override
    public int getSpeedUpDaysTotal() {
        return getDaysRequired();
    }

    @Override
    public void speedUp(int days) {
        daysPassed += days;
    }

    @Override
    public boolean isSpeedUpInstant() {
        return true;
    }

    @Override
    public boolean canSpeedUp() {
        return isFunctional() && !jumping && getDaysUntilReady() > 1;
    }
}
