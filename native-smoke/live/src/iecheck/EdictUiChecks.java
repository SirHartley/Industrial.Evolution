package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.senate.EdictHelper;
import indevo.industries.senate.EdictRepo;
import indevo.industries.senate.EdictRequirement;
import indevo.industries.senate.conditions.EdictAPI;
import indevo.industries.senate.dialogue.EdictPanelDelegate;
import indevo.industries.senate.dialogue.EdictView;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;

// test only: every Senate Edict panel view from code, audited and captured
public final class EdictUiChecks {
    private static final String LABOUR = "IndEvo_edict_labour_rest";
    private static final String[] INDUSTRIES = {Industries.MINING, Industries.REFINING, Industries.LIGHTINDUSTRY, Industries.FARMING, Industries.FUELPROD,
            Industries.HEAVYINDUSTRY, Industries.ORBITALSTATION, Industries.PATROLHQ, Industries.WAYSTATION, Industries.GROUNDDEFENSES, Industries.AQUACULTURE};

    private EdictUiChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(EdictUiChecks::setup);

        UiViews.view(steps, "edict-preview", c -> {
            MarketAPI colony = c.market("c1");
            open(c, new EdictView(colony.getId(), find(colony, true)));
        });
        UiViews.view(steps, "edict-unmet", c -> {
            MarketAPI colony = c.market("c1");
            String unmet = find(colony, false);
            c.note("unmet edict " + unmet);
            open(c, new EdictView(colony.getId(), unmet));
        });
        UiViews.view(steps, "edict-many-supply", c -> {
            MarketAPI a = c.market("a");
            for (String id : INDUSTRIES) if (!a.hasIndustry(id)) a.addIndustry(id);
            a.reapplyIndustries();
            open(c, new EdictView(a.getId(), LABOUR));
        });
        UiViews.view(steps, "edict-active", c -> {
            MarketAPI colony = c.market("c1");
            String id = find(colony, true);
            EdictHelper.enact(colony, id);
            c.put("active", id);
            MiscIE.getPlayerCargo().addCommodity(ItemIds.RARE_PARTS, 2000f);
            open(c, new EdictView(colony.getId(), null));
        });
        UiViews.view(steps, "edict-bribe-short", c -> {
            relics(c, 0f);
            open(c, new EdictView(c.market("c1").getId(), null));
        });
        UiViews.view(steps, "edict-served", c -> {
            MarketAPI colony = c.market("c1");
            EdictAPI edict = EdictHelper.getActiveEdictPlugin(colony);
            ReflectionUtils.setWithSuper("daysPassed", edict, edict.getMinRuntime(colony) + 5, 3, null);
            open(c, new EdictView(colony.getId(), null));
        });
        UiViews.view(steps, "edict-single", c -> {
            MarketAPI colony = c.market("c2");
            c.expect(EdictPanelDelegate.show(colony, new EdictView()), "single colony panel opens");
        });
        UiViews.view(steps, "edict-worst", c -> {
            c.market("c1").setName("New Maxios Prime Station");
            c.market("c2").setName("Konstantinopel Orbital Habitat Ring");
            UiAudit.acceptLong("Konstantinopel Orbital Habitat Ring");
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(987654321f);
            open(c, new EdictView(c.market("c1").getId(), null));
        });
        UiViews.view(steps, "edict-empty", c -> {
            List<PlanetAPI> far = Colony.planets(1);
            MarketAPI lone = Colony.make(far.get(0), 4);
            lone.addIndustry(Ids.SENATE);
            lone.reapplyIndustries();
            c.put("lone", lone.getId());
            c.note("lone senate at " + lone.getName() + ", covers " + EdictHelper.getColoniesCoveredBy(lone).size());
            c.expect(EdictPanelDelegate.show(lone, new EdictView()), "lone senate panel opens");
        });
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    static void setup(Ctx c) {
        List<PlanetAPI> planets = Colony.planets(5);
        String[] keys = {"b", "a", "c1", "c2", "c3"};
        for (int i = 0; i < planets.size(); i++) c.put(keys[i], Colony.make(planets.get(i), 6 - i % 3).getId());
        MarketAPI senate = c.market("b");
        senate.addIndustry(Ids.SENATE);
        senate.reapplyIndustries();
        Colony.jump(senate.getContainingLocation(), senate.getPrimaryEntity().getLocation().x + 300f, senate.getPrimaryEntity().getLocation().y);
        Global.getSector().getPlayerFleet().getCargo().getCredits().set(2000000f);
        c.note("senate at " + senate.getName() + " covers " + EdictHelper.getColoniesCoveredBy(senate).size() + " colonies");
    }

    // first Edict whose requirements on the colony are all met, or the first with one unmet
    static String find(MarketAPI colony, boolean met) {
        MarketAPI dummy = Global.getFactory().createMarket("iecheck_edictPreview", "Edict preview", 3);
        for (MarketConditionSpecAPI spec : EdictRepo.getAll()) {
            dummy.addCondition(spec.getId());
            EdictAPI edict = EdictHelper.asEdict(dummy.getCondition(spec.getId()));
            if (edict == null) continue;
            boolean all = true;
            for (EdictRequirement r : edict.getRequirements(colony)) all &= r.met;
            if (all == met) return spec.getId();
        }
        return null;
    }

    static void relics(Ctx c, float amount) {
        for (String key : new String[]{"b", "c1"}) {
            MarketAPI m = c.market(key);
            MiscIE.getStorageCargo(m).removeCommodity(ItemIds.RARE_PARTS, MiscIE.getStorageCargo(m).getCommodityQuantity(ItemIds.RARE_PARTS));
        }
        MarketAPI b = c.market("b");
        MiscIE.getPlayerCargo().removeCommodity(ItemIds.RARE_PARTS, MiscIE.getPlayerCargo().getCommodityQuantity(ItemIds.RARE_PARTS));
        if (amount > 0f) MiscIE.getStorageCargo(b).addCommodity(ItemIds.RARE_PARTS, amount);
    }

    static void open(Ctx c, EdictView view) {
        c.expect(EdictPanelDelegate.show(c.market("b"), view), "edict panel opens");
    }
}
