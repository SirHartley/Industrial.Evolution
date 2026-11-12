package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import indevo.abilities.splitfleet.dialogue.DetachmentView;
import indevo.dialogue.research.dialogue.ResearchProjectPanel;
import indevo.industries.academy.industry.Academy;
import indevo.industries.academy.plugins.AcademyView;
import indevo.industries.courierport.dialogue.CourierView;
import indevo.industries.derelicts.dialogue.AncientLabRelicView;
import indevo.industries.engineeringhub.dialogue.EngineeringHubView;
import indevo.industries.salvageyards.plugins.SalvageYardsView;
import indevo.industries.senate.dialogue.EdictView;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// test only: one panel per run left open (c.hold) with its click and hover targets logged, for gamectl
public final class HoldChecks {
    private static final Logger log = Global.getLogger(HoldChecks.class);

    private HoldChecks() {
    }

    public static List<Step> steps(String panel) {
        List<Step> steps = new ArrayList<>();
        switch (panel) {
            case "academy" -> hold(steps, c -> {
                AcademyUiChecks.setup(c);
                Academy academy = Academy.get(c.market("a"));
                academy.setOfficerForTraining(AcademyUiChecks.person(c, "trainee"), academy.getReachablePersonalities(AcademyUiChecks.person(c, "trainee")).get(0));
                AcademyUiChecks.worst(c);
            }, c -> AcademyUiChecks.open(c, new AcademyView(false, AcademyUiChecks.id(c, "selected"), Personalities.STEADY)));
            case "detach" -> hold(steps, DetachmentUiChecks::setup, c -> DetachmentUiChecks.open(c, new DetachmentView()));
            case "courier" -> hold(steps, CourierUiChecks::setup, c -> CourierUiChecks.open(c, CourierView.contract((String) c.get("cargo"))));
            case "salvage" -> hold(steps, c -> {
                SalvageUiChecks.setup(c);
                SalvageUiChecks.ships(c);
            }, c -> SalvageUiChecks.open(c, SalvageUiChecks.order(new SalvageYardsView(SalvageYardsView.Tab.ASSEMBLY))));
            case "edict" -> hold(steps, c -> {
                EdictUiChecks.setup(c);
                EdictUiChecks.relics(c, 1000f);
            }, c -> EdictUiChecks.open(c, new EdictView(c.market("c1").getId(), null)));
            case "lab" -> hold(steps, c -> {
                LabUiChecks.setup(c);
                LabUiChecks.templates(c);
                LabUiChecks.relics(c, 5000f);
            }, c -> LabUiChecks.open(c, new AncientLabRelicView(false, AncientLabRelicView.FIRST, null)));
            case "hub" -> hold(steps, c -> {
                HubUiChecks.setup(c);
                HubUiChecks.reverse(c, 6);
                HubUiChecks.hullmods(c, 8);
            }, c -> HubUiChecks.open(c, new EngineeringHubView(false, null, "eagle", null)));
            case "research" -> {
                steps.add(c -> UiViews.side = new float[]{ResearchProjectPanel.WIDTH, ResearchProjectPanel.HEIGHT});
                hold(steps, ResearchUiChecks::setup, c -> ResearchUiChecks.open(c, new ResearchUiChecks.Host(0f)));
            }
            case "vanilla" -> {
                steps.add(c -> {
                    AcademyUiChecks.setup(c);
                    MarketAPI a = c.market("a");
                    for (String id : new String[]{Industries.SPACEPORT, Industries.MINING, Industries.FARMING, Industries.LIGHTINDUSTRY, Industries.PATROLHQ}) {
                        if (!a.hasIndustry(id)) a.addIndustry(id);
                    }
                    a.reapplyIndustries();
                    c.expect(a.isPlayerOwned(), "player colony for the vanilla screens");
                });
                steps.add(c -> {
                    log.info("LIVE_CHECK hold");
                    c.frames(Integer.MAX_VALUE);
                });
            }
            default -> throw new IllegalArgumentException("Unknown hold panel " + panel);
        }
        return steps;
    }

    private static void hold(List<Step> steps, Step setup, UiViews.Open open) {
        steps.add(setup);
        steps.add(c -> {
            c.keepDialog(true);
            open.open(c);
            c.frames(60);
        });
        steps.add(c -> {
            UiAudit.Found found = UiAudit.find();
            if (found == null && UiViews.side != null) found = UiAudit.findSide(UiViews.side[0], UiViews.side[1]);
            c.expect(found != null, "held panel is open");
            if (found != null) {
                log.info("LIVE_CHECK note: rect held " + UiAudit.rect(found));
                for (String target : UiAudit.targets(found)) log.info("LIVE_CHECK note: TARGET " + target);
            }
            log.info("LIVE_CHECK hold");
            c.frames(Integer.MAX_VALUE);
        });
    }
}
