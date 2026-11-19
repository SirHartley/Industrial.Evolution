package iecheck;

import com.fs.starfarer.api.Global;
import indevo.ids.Ids;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.PetPlan;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.industries.petshop.plugins.PetsView;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// pet 3.7: plans saved over a reload, then a held Planner tab over the petstree family with a plan as parent of another plan
public final class PetsPlanChecks {
    private static final Logger log = Global.getLogger(PetsPlanChecks.class);

    private PetsPlanChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        // the petstree family: Gran x Gramps, Opa x Oma (dead), Mom with Dad and Dadtwo, Aunt x Uncle, Kidone, Kidtwo, Kidthree, Cousin, Xavi x Xena, Xylo, Lone
        steps.add(c -> PetsTreeChecks.steps().get(0).run(c));
        steps.add(c -> {
            // Plan 1: Kidone x Cousin for Capacitor 4; Plan 2: Plan 1 x Kidtwo for Capacitor 5 and Plating 1, no Malformed; Plan 3: one open place
            PetPlan p1 = manager().newPlan("doge", (String) c.get("Kidone"), (String) c.get("Cousin"));
            p1.wanted.put("IndEvo_pt_capacitor", 4);
            PetPlan p2 = manager().newPlan("doge", p1.id, (String) c.get("Kidtwo"));
            p2.wanted.put("IndEvo_pt_capacitor", 5);
            p2.wanted.put("IndEvo_pt_plating", 1);
            p2.wanted.put(PetGenetics.MALFORMED, 0);
            PetPlan p3 = manager().newPlan("doge", (String) c.get("Mom"), null);
            p3.wanted.put("IndEvo_pt_far_sight", 2);
            c.note("plans " + p1.name + " " + p1.id + ", " + p2.name + ", " + p3.name);
            // SWITCHES=overlap: two more plans with crossing pairs, so two plan bars share a gap and overlap (5.5 line colours)
            boolean overlap = System.getProperty("ie.switches", "").contains("overlap");
            if (overlap) {
                manager().newPlan("doge", (String) c.get("Kidone"), (String) c.get("Kidthree"));
                manager().newPlan("doge", (String) c.get("Kidtwo"), (String) c.get("Cousin"));
            }
            c.expect(manager().getPlans().size() == (overlap ? 5 : 3), "three plans (five with overlap)");
            c.put("p1", p1.id);
            c.put("p2", p2.id);
            c.reload();
        });
        steps.add(c -> {
            List<PetPlan> plans = manager().getPlans();
            PetPlan p1 = manager().getPlan((String) c.get("p1"));
            PetPlan p2 = manager().getPlan((String) c.get("p2"));
            c.expect(plans.size() == (System.getProperty("ie.switches", "").contains("overlap") ? 5 : 3), "its plans after a save and reload (" + plans.size() + ")");
            c.expect(p1 != null && p1.wanted.get("IndEvo_pt_capacitor") == 4 && c.get("Kidone").equals(p1.parentA), "Plan 1 keeps its parents and wanted row after the reload");
            c.expect(p2 != null && p1 != null && p1.id.equals(p2.parentA) && p2.wanted.size() == 3, "Plan 2 keeps Plan 1 as a parent and three wanted rows");
            String sw = System.getProperty("ie.switches", "");
            PetsView view = new PetsView((String) c.get("Kidone"));
            view.tab = PetsView.TAB_PLAN;
            c.keepDialog(true);
            // SWITCHES=nocenter: the panel from the pet management ability, no center
            PetShop shop = sw.contains("nocenter") ? null : (PetShop) c.market("a").getIndustry(Ids.PET_STORE);
            c.expect(PetsDialogPlugin.show(shop, view), "the Pets panel opens on the Planner tab" + (shop == null ? " without a center" : ""));
            c.frames(60);
        });
        // SWITCHES=savecheck: show every tab with Wide tiles on, close the panel, save and load, then check the save loaded and Wide stays on
        if (System.getProperty("ie.switches", "").contains("savecheck")) {
            int[] tabs = {PetsView.TAB_PETS, PetsView.TAB_HUSBANDRY, PetsView.TAB_TREE};
            for (int tab : tabs) {
                steps.add(c -> c.keepDialog(false));
                steps.add(c -> {
                    c.data().put("IndEvo_petTreeWide", true);
                    PetsView view = new PetsView((String) c.get("Kidone"));
                    view.tab = tab;
                    c.keepDialog(true);
                    c.expect(PetsDialogPlugin.show((PetShop) c.market("a").getIndustry(Ids.PET_STORE), view), "the Pets panel shows tab " + tab);
                    c.frames(60);
                });
            }
            steps.add(c -> {
                c.keepDialog(false);
                c.reload();
            });
            steps.add(c -> {
                c.expect(manager().getPlans().size() == (System.getProperty("ie.switches", "").contains("overlap") ? 5 : 3), "the save made after showing every tab loads with its plans");
                c.expect(Boolean.TRUE.equals(c.data().get("IndEvo_petTreeWide")), "Wide tiles stays on after a save and reload");
                PetsView view = new PetsView((String) c.get("Kidone"));
                view.tab = PetsView.TAB_TREE;
                c.keepDialog(true);
                c.expect(PetsDialogPlugin.show((PetShop) c.market("a").getIndustry(Ids.PET_STORE), view), "the Pets panel opens again on the Family tree tab");
                c.frames(60);
            });
        }
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
