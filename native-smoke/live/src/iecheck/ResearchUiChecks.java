package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.research.ResearchProjectTemplateRepo;
import indevo.dialogue.research.dialogue.ResearchProject;
import indevo.dialogue.research.dialogue.ResearchProjectDialoguePlugin;
import indevo.dialogue.research.dialogue.ResearchProjectPanel;
import indevo.dialogue.research.dialogue.ResearchView;
import indevo.ids.Ids;
import indevo.utils.helper.ReflectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

// test only: the Research Tracts side panel at the Galatia Academy from code, audited and captured as the whole screen
public final class ResearchUiChecks {

    private ResearchUiChecks() {
    }

    // empty dialog that runs the rule command the way IndEvo_JobMenuInit does, with the wanted view
    static final class Host implements InteractionDialogPlugin {
        private final Map<String, MemoryAPI> memory = new HashMap<>();
        private final String[] info;
        private final float scroll;

        Host(float scroll, String... info) {
            this.info = info;
            this.scroll = scroll;
        }

        @Override
        public void init(InteractionDialogAPI dialog) {
            memory.put(MemKeys.LOCAL, dialog.getInteractionTarget().getMemoryWithoutUpdate());
            memory.put(MemKeys.ENTITY, dialog.getInteractionTarget().getMemoryWithoutUpdate());
            ResearchProjectDialoguePlugin plugin = new ResearchProjectDialoguePlugin();
            ResearchView view = (ResearchView) ReflectionUtils.get("view", plugin);
            for (String id : info) view.info.add(id);
            if (scroll > 0f) view.scroll.set(ResearchView.CARDS, scroll);
            plugin.execute(null, dialog, Misc.tokenize(""), memory);
        }

        @Override
        public void optionSelected(String text, Object data) {
        }

        @Override
        public void optionMousedOver(String text, Object data) {
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public void backFromEngagement(EngagementResultAPI result) {
        }

        @Override
        public Object getContext() {
            return null;
        }

        @Override
        public Map<String, MemoryAPI> getMemoryMap() {
            return memory;
        }
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> UiViews.side = new float[]{ResearchProjectPanel.WIDTH, ResearchProjectPanel.HEIGHT});
        steps.add(ResearchUiChecks::setup);

        UiViews.view(steps, "research-cards", c -> open(c, new Host(0f)));
        UiViews.view(steps, "research-info", c -> open(c, new Host(0f, Ids.PROJ_SPYGLASS, Ids.PROJ_MJOLNIR)));
        UiViews.view(steps, "research-blocked", c -> {
            ResearchProject reforge = ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(Ids.PROJ_REFORGE);
            reforge.getProgress().points = reforge.getRequiredPoints();
            open(c, new Host(0f, Ids.PROJ_REFORGE));
        });
        UiViews.view(steps, "research-items", c -> open(c, new Host(0f, Ids.PROJ_KEYHOLE, Ids.PROJ_TRANSISTOR)));
        UiViews.view(steps, "research-scrolled", c -> open(c, new Host(100000f, Ids.PROJ_PULSE)),
                (c, panel) -> c.note("stock clicked " + UiAudit.click(panel.content(), "Stock") + " " + UiAudit.buttonTexts(panel.content())));
        UiViews.view(steps, "research-empty", c -> {
            for (ResearchProject p : ResearchProjectTemplateRepo.RESEARCH_PROJECTS.values()) p.getProgress().redeemed = true;
            open(c, new Host(0f));
        });
        steps.add(c -> {
            UiViews.side = null;
            c.keepDialog(false);
        });
        return steps;
    }

    private static void setup(Ctx c) {
        float i = 0f;
        for (ResearchProject p : ResearchProjectTemplateRepo.RESEARCH_PROJECTS.values()) {
            p.getProgress().redeemed = false;
            p.getProgress().points = p.getRequiredPoints() * Math.min(0.95f, 0.12f * i++);
        }
        ResearchProject ready = ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(Ids.PROJ_PULSE);
        ready.getProgress().points = ready.getRequiredPoints();
        ResearchProject repeat = ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(Ids.PROJ_MJOLNIR);
        repeat.getProgress().overflow = true;
        MarketAPI galatia = Global.getSector().getEconomy().getMarket(Ids.ACADEMY_MARKET_ID);
        Colony.jump(galatia.getContainingLocation(), galatia.getPrimaryEntity().getLocation().x + 200f, galatia.getPrimaryEntity().getLocation().y);
        c.note("research at " + galatia.getName());
    }

    private static void open(Ctx c, Host host) {
        SectorEntityToken station = Global.getSector().getEconomy().getMarket(Ids.ACADEMY_MARKET_ID).getPrimaryEntity();
        c.expect(Global.getSector().getCampaignUI().showInteractionDialog(host, station), "research dialog opens");
    }
}
