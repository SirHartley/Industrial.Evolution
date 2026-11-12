package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.engineeringhub.dialogue.EngineeringHubPanelDelegate;
import indevo.industries.engineeringhub.dialogue.EngineeringHubView;
import indevo.industries.engineeringhub.industry.EngineeringHub;
import indevo.industries.engineeringhub.memory.RecoveredHullmods;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.ReflectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// test only: every Engineering Hub panel view from code, audited and captured
public final class HubUiChecks {
    private static final String[] PROGRESS = {"eagle", "falcon", "dominator", "champion", "heron", "aurora", "apogee"};

    private HubUiChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(HubUiChecks::setup);

        UiViews.view(steps, "hub-hullmods-empty", c -> open(c, new EngineeringHubView(true, null, null, null)));
        UiViews.view(steps, "hub-reverse-idle", c -> open(c, new EngineeringHubView(false, null, null, null)));
        UiViews.view(steps, "hub-hullmods", c -> {
            hullmods(c, 8);
            relics(c, 2000f);
            open(c, new EngineeringHubView(true, null, null, null));
        });
        UiViews.view(steps, "hub-hullmods-short", c -> {
            relics(c, 5f);
            open(c, new EngineeringHubView(true, null, null, null));
        });
        UiViews.view(steps, "hub-hullmods-many", c -> {
            relics(c, 2000f);
            hullmods(c, 60);
            open(c, new EngineeringHubView(true, longest(), null, null));
        });
        UiViews.view(steps, "hub-reverse", c -> {
            reverse(c, 6);
            open(c, new EngineeringHubView(false, null, "eagle", null));
        });
        UiViews.view(steps, "hub-reverse-relics", c -> open(c, new EngineeringHubView(false, null, "eagle", EngineeringHubView.RELICS)));
        UiViews.view(steps, "hub-reverse-below", c -> open(c, new EngineeringHubView(false, null, "dominator", null)));
        UiViews.view(steps, "hub-reverse-blueprints-many", c -> {
            CargoAPI shared = MiscIE.getIndustrialStorageCargo(c.market("a"));
            int n = 0;
            for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
                if (n >= 200 || spec.getHullSize() != HullSize.CRUISER || spec.isDHull() || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)) continue;
                shared.addSpecial(new SpecialItemData(Items.SHIP_BP, spec.getHullId()), 1);
                n++;
            }
            c.note("cruiser blueprints " + n);
            open(c, new EngineeringHubView(false, null, "eagle", null));
        });
        UiViews.view(steps, "hub-reverse-dropoff-many", c -> {
            CargoAPI drop = c.market("a").getSubmarket(Ids.ENGSTORAGE).getCargo();
            for (int i = 0; i < 40; i++) drop.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, i % 2 == 0 ? "wolf_Assault" : "lasher_CS"));
            open(c, new EngineeringHubView(false, null, "eagle", null));
        });
        UiViews.view(steps, "hub-reverse-blocked", c -> {
            CargoAPI shared = MiscIE.getIndustrialStorageCargo(c.market("a"));
            shared.removeAll(shared.createCopy());
            relics(c, 1f);
            hub(c).getMarket().getMemoryWithoutUpdate().set(indevo.economy.RelicSpeedUpTarget.MEM_COOLDOWN_PREFIX + hub(c).getSpeedUpId(), true, 60f);
            open(c, new EngineeringHubView(false, null, "eagle", null));
        });
        UiViews.view(steps, "hub-worst", c -> {
            c.market("a").setName("Nova Maxios Prime Station");
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(987654321f);
            relics(c, 987654f);
            open(c, new EngineeringHubView(false, null, "eagle", null));
        });
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    private static void setup(Ctx c) {
        Colony.pair(c, 6);
        MarketAPI a = c.market("a");
        a.addIndustry(Ids.ENGHUB);
        if (a.getSubmarket(Ids.SHAREDSTORAGE) == null) a.addSubmarket(Ids.SHAREDSTORAGE);
        if (a.getSubmarket(Ids.ENGSTORAGE) == null) a.addSubmarket(Ids.ENGSTORAGE);
        a.reapplyIndustries();
        Colony.jump(a.getContainingLocation(), a.getPrimaryEntity().getLocation().x + 300f, a.getPrimaryEntity().getLocation().y);
        c.note("hub at " + a.getName() + ", hub " + (hub(c) != null));
    }

    private static void hullmods(Ctx c, int count) {
        Map<String, String> data = new LinkedHashMap<>();
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (data.size() >= count) break;
            if (spec.isHidden() || spec.isHiddenEverywhere() || spec.hasTag("no_drop") || spec.hasTag("dmod")) continue;
            Global.getSector().getPlayerFaction().removeKnownHullMod(spec.getId());
            if (RecoveredHullmods.isLearnable(spec)) data.put(spec.getId(), data.size() % 2 == 0 ? "eagle" : "onslaught");
        }
        c.market("a").getMemoryWithoutUpdate().set(RecoveredHullmods.MEM_KEY, data);
    }

    // the hullmod with the longest description, for the scrolling detail
    private static String longest() {
        String best = null;
        int len = -1;
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (!RecoveredHullmods.isLearnable(spec)) continue;
            int l = spec.getDescriptionFormat() == null ? 0 : spec.getDescriptionFormat().length();
            if (l > len) {
                len = l;
                best = spec.getId();
            }
        }
        return best;
    }

    private static void reverse(Ctx c, int drop) {
        EngineeringHub hub = hub(c);
        Map<String, Float> progress = new LinkedHashMap<>();
        for (int i = 0; i < PROGRESS.length; i++) progress.put(PROGRESS[i], i < 2 ? 1f : 0.15f * i);
        // the hub reloads its list from this sector memory key (EngineeringHub.RESEARCH_LIST_KEY)
        MiscIE.storeMapInMemory(progress, "$IndEvo_researchProgress");
        hub.setCurrentDeconShipVar(Global.getSettings().getVariant("hammerhead_Balanced").clone());
        ReflectionUtils.set("daysPassed", hub, 3);
        CargoAPI dropCargo = c.market("a").getSubmarket(Ids.ENGSTORAGE).getCargo();
        for (int i = 0; i < drop; i++) dropCargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, i % 2 == 0 ? "sunder_CS" : "falcon_Attack"));
        CargoAPI shared = MiscIE.getIndustrialStorageCargo(c.market("a"));
        for (String id : new String[]{"eagle", "falcon", "champion"}) shared.addSpecial(new SpecialItemData(Items.SHIP_BP, id), 1);
        hub.setSpecialItem(new SpecialItemData(Items.SHIP_BP, "heron"));
        relics(c, 2000f);
    }

    private static void relics(Ctx c, float amount) {
        MarketAPI a = c.market("a");
        for (CargoAPI cargo : new CargoAPI[]{MiscIE.getStorageCargo(a), MiscIE.getIndustrialStorageCargo(a), MiscIE.getPlayerCargo()}) {
            if (cargo != null) cargo.removeCommodity(ItemIds.RARE_PARTS, cargo.getCommodityQuantity(ItemIds.RARE_PARTS));
        }
        MiscIE.getStorageCargo(a).addCommodity(ItemIds.RARE_PARTS, amount);
    }

    private static EngineeringHub hub(Ctx c) {
        return (EngineeringHub) c.market("a").getIndustry(Ids.ENGHUB);
    }

    private static void open(Ctx c, EngineeringHubView view) {
        c.expect(EngineeringHubPanelDelegate.show(c.market("a"), view), "hub panel opens");
    }
}
