package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import indevo.ids.Ids;
import indevo.industries.derelicts.industry.Ruins;
import indevo.industries.ruinfra.conditions.DerelictInfrastructureCondition;
import indevo.industries.senate.conditions.Edict_FastBuild;
import indevo.utils.helper.StarReflectionUtils;

import java.util.ArrayList;
import java.util.List;

public final class RuinsChecks {
    private static final String TARGET = DerelictInfrastructureCondition.RUINED_INFRA_UPGRADE_ID_KEY;

    private RuinsChecks() {
    }

    private static String ruinTarget(MarketAPI market) {
        return market.getMemoryWithoutUpdate().getString(Ruins.INDUSTRY_ID_MEMORY_KEY);
    }

    private static boolean offersDowngrade(Industry industry) {
        return industry.canDowngrade() && industry.getSpec().getDowngrade() != null;
    }

    private static float buildTime(Industry industry) {
        return (float) StarReflectionUtils.get((BaseIndustry) industry, "buildTime", float.class, true);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            a.addCondition(Ids.COND_RUINS);
            b.addCondition(Ids.COND_RUINS);
            a.reapplyConditions();
            b.reapplyConditions();
            a.reapplyIndustries();
            b.reapplyIndustries();
            c.expect(a.hasIndustry(Ids.RUINS) && b.hasIndustry(Ids.RUINS), "ruins condition adds the ruins");
            c.expect(ruinTarget(a) != null && ruinTarget(b) != null, "612 ruins added later have targets (" + ruinTarget(a) + ", " + ruinTarget(b) + ")");

            if (ruinTarget(a).equals(ruinTarget(b))) {
                b.getMemoryWithoutUpdate().set(Ruins.INDUSTRY_ID_MEMORY_KEY, ruinTarget(a).equals(Ids.LAB) ? Ids.RIFTGEN : Ids.LAB);
                b.reapplyIndustries();
            }
            a.reapplyIndustries();
            for (int i = 0; i < 3; i++) {
                c.expect(a.getIndustry(Ids.RUINS).getSpec().getUpgrade().equals(ruinTarget(a)), "610 A offers its own upgrade " + ruinTarget(a));
                c.expect(b.getIndustry(Ids.RUINS).getSpec().getUpgrade().equals(ruinTarget(b)), "610 B offers its own upgrade " + ruinTarget(b));
            }
            c.expect(a.getIndustry(Ids.RUINS).canUpgrade() && b.getIndustry(Ids.RUINS).canUpgrade(), "610 both can upgrade");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            a.getMemoryWithoutUpdate().unset(Ruins.INDUSTRY_ID_MEMORY_KEY);
            a.reapplyIndustries();
            c.expect(ruinTarget(a) != null, "612 ruin without a target rolls one on apply (" + ruinTarget(a) + ")");

            String target = ruinTarget(a);
            Industry ruins = a.getIndustry(Ids.RUINS);
            ruins.startUpgrading();
            c.expect(ruins.isUpgrading(), "ruin upgrade starts");
            ruins.finishBuildingOrUpgrading();
            c.expect(a.hasIndustry(target) && !a.hasIndustry(Ids.RUINS), "ruin upgraded to " + target);
            c.expect(!offersDowngrade(a.getIndustry(target)), "upgraded ruin offers no downgrade");
            for (String id : new String[]{Ids.LAB, Ids.RIFTGEN}) {
                c.expect(!offersDowngrade(a.instantiateIndustry(id)), id + " offers no downgrade");
            }
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            b.addIndustry(Ids.RUINFRA);
            b.reapplyIndustries();
            String rolled = b.getMemoryWithoutUpdate().getString(TARGET);
            c.expect(rolled != null, "ruined infrastructure by console gets a target (" + rolled + ")");
            c.expect(b.getIndustry(Ids.RUINFRA).canUpgrade(), "ruined infrastructure can be restored");

            b.getMemoryWithoutUpdate().set(TARGET, "nope");
            b.reapplyIndustries();
            String fixed = b.getMemoryWithoutUpdate().getString(TARGET);
            c.expect(fixed != null && !"nope".equals(fixed) && Global.getSettings().getIndustrySpec(fixed) != null, "target from a removed mod rerolled (" + fixed + ")");

            a.addIndustry(Ids.SENATE);
            a.reapplyIndustries();
            b.getMemoryWithoutUpdate().set(TARGET, Ids.SENATE);
            b.reapplyIndustries();
            Industry restore = b.instantiateIndustry(Ids.CHAMELION);
            String own = b.instantiateIndustry(Ids.SENATE).getUnavailableReason();
            c.expect(!restore.isAvailableToBuild(), "1307 blocked restore is greyed out");
            c.expect(restore.getUnavailableReason().equals(own), "1307 blocked restore shows the target's reason: " + restore.getUnavailableReason());
            c.expect(Ids.SENATE.equals(b.getMemoryWithoutUpdate().getString(TARGET)), "1307 valid but blocked target kept");
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            b.getMemoryWithoutUpdate().set(TARGET, Industries.MINING);
            float full = Global.getSettings().getIndustrySpec(Industries.MINING).getBuildTime() * indevo.config.Setting.DERINFRA_BUILD_RED.value();

            Industry restore = b.getIndustry(Ids.RUINFRA);
            restore.startUpgrading();
            c.near(buildTime(restore), full, 0.01f, "restore starts at its full time");
            b.addCondition(Edict_FastBuild.ID);
            b.reapplyConditions();
            c.near(buildTime(restore), full / 2f, 0.01f, "1507 fast build halves a running restore");
            b.removeCondition(Edict_FastBuild.ID);
            b.reapplyConditions();
            c.near(buildTime(restore), full, 0.01f, "1507 ending fast build puts the full time back");
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            Industry restore = b.getIndustry(Ids.RUINFRA);
            float full = buildTime(restore);
            b.addCondition(Edict_FastBuild.ID);
            b.reapplyConditions();
            restore.startUpgrading();
            b.reapplyConditions();
            c.near(buildTime(restore), full / 2f, 0.01f, "1507 fast build first, then restore: halved");
            b.removeCondition(Edict_FastBuild.ID);
            b.reapplyConditions();
            c.near(buildTime(restore), full, 0.01f, "1507 ending fast build puts the full time back");

            b.addCondition(Edict_FastBuild.ID);
            b.reapplyConditions();
            b.addIndustry(Industries.REFINING);
            Industry refining = b.getIndustry(Industries.REFINING);
            refining.startBuilding();
            b.reapplyConditions();
            float time = Global.getSettings().getIndustrySpec(Industries.REFINING).getBuildTime();
            c.near(buildTime(refining), time / 2f, 0.01f, "1501 fast build halves a new build");
            b.removeCondition(Edict_FastBuild.ID);
            b.reapplyConditions();
            c.near(buildTime(refining), time, 0.01f, "1501 ending fast build puts normal speed back");
        });
        return steps;
    }
}
