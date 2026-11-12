package iecheck;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import indevo.abilities.skills.patches.MicromanagementRemoteItemPatch;
import indevo.abilities.splitfleet.patches.DetachmentCargoPickerPatch;
import indevo.economy.patches.RelicBuildInCounterPatch;
import indevo.economy.patches.RelicBuildInPatch;
import indevo.exploration.salvage.patches.RelicDerelictRecoveryPatch;
import indevo.industries.artillery.patches.WatchtowerStableLocationPatch;
import indevo.industries.patches.CommerceBalancePatch;
import indevo.industries.patches.DealmakerHolosuitePatch;
import indevo.industries.privateer.patches.PrivateerIndustrySlotPatch;
import indevo.industries.senate.patches.FastBuildPickerRowPatch;
import indevo.industries.senate.patches.FastBuildQueuedIndustryPatch;
import indevo.industries.senate.patches.FastBuildTooltipPatch;
import indevo.industries.senate.patches.FastBuildUpgradeConfirmPatch;
import indevo.industries.worldwonder.patches.CloudPainterShieldPatch;
import patchlib.api.PatchLib;

import java.util.ArrayList;
import java.util.List;

public final class PatchChecks {
    private static final Class<?>[] PATCHES = {
            MicromanagementRemoteItemPatch.class, WatchtowerStableLocationPatch.class,
            CommerceBalancePatch.class, DealmakerHolosuitePatch.class, PrivateerIndustrySlotPatch.class,
            FastBuildPickerRowPatch.class, FastBuildQueuedIndustryPatch.class, FastBuildTooltipPatch.class,
            FastBuildUpgradeConfirmPatch.class, CloudPainterShieldPatch.class, DetachmentCargoPickerPatch.class,
            RelicDerelictRecoveryPatch.class, RelicBuildInPatch.class, RelicBuildInCounterPatch.class};

    private PatchChecks() {
    }

    private static void all(boolean on) {
        for (Class<?> patch : PATCHES) {
            if (on) PatchLib.enablePatch(patch);
            else PatchLib.disablePatch(patch);
        }
    }

    private static int enabled() {
        int count = 0;
        for (Class<?> patch : PATCHES) if (PatchLib.isPatchEnabled(patch)) count++;
        return count;
    }

    private static boolean secondCommerceBuildable(Ctx c) {
        return c.market("b").instantiateIndustry(Industries.COMMERCE).isAvailableToBuild();
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            c.market("a").addIndustry(Industries.COMMERCE);
            c.market("a").reapplyIndustries();
            c.days(1f);
        });
        steps.add(c -> {
            c.expect(enabled() == PATCHES.length, "all IE patches on at the start (" + enabled() + " of " + PATCHES.length + ")");
            c.expect(!secondCommerceBuildable(c), "patch on: second commerce refused");
            all(false);
            c.expect(enabled() == 0, "togglepatch: all IE patches off");
            c.expect(secondCommerceBuildable(c), "patch off: vanilla commerce rule back");
            MarketAPI a = c.market("a");
            a.reapplyIndustries();
            for (Industry industry : a.getIndustries()) Ui.text(tooltip(industry));
            c.days(2f);
        });
        steps.add(c -> {
            c.expect(true, "two days with every IE patch off, no crash");
            all(true);
            c.expect(enabled() == PATCHES.length, "togglepatch: all IE patches back on");
            c.expect(!secondCommerceBuildable(c), "patch on again: second commerce refused");
        });
        return steps;
    }

    private static com.fs.starfarer.api.ui.TooltipMakerAPI tooltip(Industry industry) {
        com.fs.starfarer.api.ui.TooltipMakerAPI tooltip = Ui.tooltip();
        industry.createTooltip(Industry.IndustryTooltipMode.NORMAL, tooltip, false);
        return tooltip;
    }
}
