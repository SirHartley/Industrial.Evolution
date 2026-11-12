package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.derelicts.dialogue.AncientLabRelicPanelDelegate;
import indevo.industries.derelicts.dialogue.AncientLabRelicView;
import indevo.items.ForgeTemplateItemPlugin;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.List;

// test only: every Ancient Laboratory relic panel view from code, audited and captured
public final class LabUiChecks {
    private static final String[] TEMPLATES = {"eagle", "dominator", "falcon", "hammerhead", "onslaught", "wolf"};
    private static final String[] AUTOMATED = {"glimmer_Assault", "lumen_Standard", "fulgent_Assault", "scintilla_Strike", "brilliant_Standard"};

    private LabUiChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(LabUiChecks::setup);

        UiViews.view(steps, "lab-empty", c -> open(c, new AncientLabRelicView(false, AncientLabRelicView.FIRST, null)));
        UiViews.view(steps, "lab-ships-empty", c -> open(c, new AncientLabRelicView(true, AncientLabRelicView.FIRST, null)));
        UiViews.view(steps, "lab-templates", c -> {
            templates(c);
            relics(c, 5000f);
            open(c, new AncientLabRelicView(false, AncientLabRelicView.FIRST, null));
        });
        UiViews.view(steps, "lab-template-level0", c -> open(c, new AncientLabRelicView(false, (String) c.get("level1"), null)));
        UiViews.view(steps, "lab-templates-short", c -> {
            relics(c, 3f);
            open(c, new AncientLabRelicView(false, AncientLabRelicView.FIRST, null));
        });
        UiViews.view(steps, "lab-none-picked", c -> {
            relics(c, 5000f);
            open(c, new AncientLabRelicView(false, null, null));
        });
        UiViews.view(steps, "lab-ships", c -> {
            for (int i = 0; i < AUTOMATED.length; i++) ship(c, AUTOMATED[i], i >= 3);
            open(c, new AncientLabRelicView(true, AncientLabRelicView.FIRST, null));
        });
        UiViews.view(steps, "lab-result", c -> open(c, new AncientLabRelicView(true, AncientLabRelicView.FIRST,
                "ISS Glimmering Hope can now run without a penalty as an automated ship.")));
        UiViews.view(steps, "lab-ships-many", c -> {
            for (int i = 0; i < 35; i++) ship(c, AUTOMATED[i % AUTOMATED.length], i % 2 == 0);
            open(c, new AncientLabRelicView(true, AncientLabRelicView.FIRST, null));
        });
        UiViews.view(steps, "lab-worst", c -> {
            c.market("a").setName("Nova Maxios Prime Station");
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(987654321f);
            for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (Misc.isAutomated(m)) m.setShipName("ISS Unreasonably Long Ship Name");
            }
            open(c, new AncientLabRelicView(true, AncientLabRelicView.FIRST, "A result line long enough to need the whole strip and then some more words to wrap."));
        });
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    static void setup(Ctx c) {
        Colony.pair(c, 6);
        MarketAPI a = c.market("a");
        a.addIndustry(Ids.LAB);
        a.addSubmarket(Ids.SHAREDSTORAGE);
        a.reapplyIndustries();
        Colony.jump(a.getContainingLocation(), a.getPrimaryEntity().getLocation().x + 300f, a.getPrimaryEntity().getLocation().y);
        c.note("lab at " + a.getName() + ", industry " + (a.getIndustry(Ids.LAB) != null));
    }

    static void templates(Ctx c) {
        MarketAPI a = c.market("a");
        CargoAPI shared = MiscIE.getIndustrialStorageCargo(a);
        CargoAPI storage = Misc.getStorageCargo(a);
        for (int i = 0; i < TEMPLATES.length; i++) {
            SpecialItemData data = ForgeTemplateItemPlugin.createForgeTemplate(variant(TEMPLATES[i]), 3 + i % 3);
            ForgeTemplateItemPlugin.incrementForgeTemplateQualityLevel(data, 1 + i % 3);
            (i % 2 == 0 ? shared : storage).addSpecial(data, 1 + i % 2);
            if (i == 0) c.put("level1", Ids.SHAREDSTORAGE + "|" + data.getData());
        }
    }

    private static com.fs.starfarer.api.combat.ShipVariantAPI variant(String hullId) {
        return Global.getSettings().getVariant(Global.getSettings().getHullIdToVariantListMap().get(hullId).get(0)).clone();
    }

    private static void ship(Ctx c, String variant, boolean storage) {
        FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        if (storage) Misc.getStorageCargo(c.market("a")).getMothballedShips().addFleetMember(m);
        else Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
    }

    static void relics(Ctx c, float amount) {
        MarketAPI a = c.market("a");
        for (CargoAPI cargo : new CargoAPI[]{Misc.getStorageCargo(a), MiscIE.getIndustrialStorageCargo(a), MiscIE.getPlayerCargo()}) {
            if (cargo != null) cargo.removeCommodity(ItemIds.RARE_PARTS, cargo.getCommodityQuantity(ItemIds.RARE_PARTS));
        }
        Misc.getStorageCargo(a).addCommodity(ItemIds.RARE_PARTS, amount);
    }

    static void open(Ctx c, AncientLabRelicView view) {
        c.expect(AncientLabRelicPanelDelegate.show(c.market("a"), view), "lab panel opens");
    }
}
