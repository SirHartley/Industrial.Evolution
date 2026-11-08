package indevo.industries.orbitalworks.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.orbitalworks.data.OrbitalWorksSlotJob;
import indevo.industries.orbitalworks.scripts.OrbitalWorksSlotManager;
import indevo.utils.plugins.BaseSimpleBaseIndustryOptionProvider;

import java.awt.Color;

public class OrbitalWorksSlotOptionProvider extends BaseSimpleBaseIndustryOptionProvider {

    public static final String NAME = "Relic Lattice Work";
    public static final String LORE = "The shipwrights thread a Domain reinforcement lattice through the hull, making room for another integrated modification. Opening the frame and sealing it again takes three months.";

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(OrbitalWorksSlotOptionProvider.class)) {
            listeners.addListener(new OrbitalWorksSlotOptionProvider(), true);
        }
    }

    @Override
    public boolean isSuitable(Industry ind, boolean allowUnderConstruction) {
        return ind != null
                && Industries.ORBITALWORKS.equals(ind.getId())
                && ind.getMarket() != null
                && ind.getMarket().isPlayerOwned()
                && !(ind.isBuilding() && !ind.isUpgrading());
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return getJob(opt.ind) != null || opt.ind.isFunctional();
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return NAME + "...";
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        OrbitalWorksSlotJob job = getJob(opt.ind);

        if (job == null) ui.showDialog(OrbitalWorksSlotPanelDelegate.getWidth(), OrbitalWorksSlotPanelDelegate.getHeight(), new OrbitalWorksSlotPanelDelegate(opt.ind.getMarket()));
        else ui.showDialog(500f, 110f, new AbortDelegate(job));
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        Color hl = Misc.getHighlightColor();
        float opad = 10f;
        OrbitalWorksSlotJob job = getJob(opt.ind);

        if (job == null) {
            tooltip.addPara("Adds %s S-mod slot over %s. The ship is unavailable during the work, then returned to local storage.", 0f, hl,
                    "one", OrbitalWorksSlotManager.DAYS_REQUIRED + " days");
            tooltip.addPara("Cost: %s Relic Components per deployment point.", opad, hl, OrbitalWorksSlotPanelDelegate.COST_PER_DP + "");
            tooltip.addPara("One ship at a time.", Misc.getGrayColor(), 3f);
            if (!opt.ind.isFunctional()) tooltip.addPara("Orbital Works is not functional.", Misc.getNegativeHighlightColor(), opad);
        } else {
            tooltip.addPara("Working on the %s. %s left.", 0f, hl, job.member.getShipName(), getDaysLeft(job) + " days");
            tooltip.addPara("Aborting returns the ship to local storage without refunding the relic components.", opad);
        }

        tooltip.addPara(LORE, Misc.getGrayColor(), opad);
    }

    @Override
    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
        if (!Industries.ORBITALWORKS.equals(ind.getId())) return;

        OrbitalWorksSlotJob job = getJob(ind);
        if (job == null) return;

        float opad = 10f;
        FleetMemberAPI member = job.member;
        float progress = Math.min(1f, job.daysDone / (float) OrbitalWorksSlotManager.DAYS_REQUIRED);

        tooltip.addPara(NAME, Misc.getHighlightColor(), opad);
        tooltip.addPara("%s, %s", 3f, Misc.getGrayColor(), Misc.getTextColor(), member.getShipName(), member.getHullSpec().getHullNameWithDashClass());
        tooltip.addCustom(createProgressBar(width - opad * 2, progress), 5f);
        tooltip.addPara("%s days left", 3f, Misc.getGrayColor(), Misc.getHighlightColor(), getDaysLeft(job) + "");
        if (!ind.isFunctional()) tooltip.addPara("Paused while Orbital Works is not functional.", Misc.getNegativeHighlightColor(), 3f);
    }

    public static int getDaysLeft(OrbitalWorksSlotJob job) {
        return Math.max(0, OrbitalWorksSlotManager.DAYS_REQUIRED - job.daysDone);
    }

    private static OrbitalWorksSlotJob getJob(Industry ind) {
        MarketAPI market = ind.getMarket();
        return market == null ? null : OrbitalWorksSlotManager.getInstanceOrRegister().getJob(market);
    }

    private static CustomPanelAPI createProgressBar(float width, final float progress) {
        final Color base = Misc.getButtonTextColor();
        final Color dark = Misc.getDarkPlayerColor();

        return Global.getSettings().createCustom(width, 8f, new BaseCustomUIPanelPlugin() {
            private PositionAPI pos;

            @Override
            public void positionChanged(PositionAPI position) {
                pos = position;
            }

            @Override
            public void render(float alphaMult) {
                if (pos == null) return;
                Misc.renderQuad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), dark, alphaMult);
                Misc.renderQuad(pos.getX(), pos.getY(), pos.getWidth() * progress, pos.getHeight(), base, alphaMult);
            }
        });
    }

    private static class AbortDelegate extends BaseCustomDialogDelegate {
        private final OrbitalWorksSlotJob job;

        AbortDelegate(OrbitalWorksSlotJob job) {
            this.job = job;
        }

        @Override
        public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
            Color hl = Misc.getHighlightColor();
            TooltipMakerAPI info = panel.createUIElement(500f, 110f, false);
            info.setParaInsigniaLarge();
            info.addPara("Abort " + NAME + " on the %s? %s left.", 0f, hl, job.member.getShipName(), getDaysLeft(job) + " days");
            info.addPara("The ship returns to local storage. Relic components are not refunded.", Misc.getNegativeHighlightColor(), 10f);
            panel.addUIElement(info).inTL(0f, 0f);
        }

        @Override
        public boolean hasCancelButton() {
            return true;
        }

        @Override
        public String getConfirmText() {
            return "Abort";
        }

        @Override
        public String getCancelText() {
            return "Cancel";
        }

        @Override
        public void customDialogConfirm() {
            OrbitalWorksSlotManager manager = OrbitalWorksSlotManager.getInstanceOrRegister();
            if (manager.getJob(job.market) == job) manager.abort(job);
        }
    }
}
