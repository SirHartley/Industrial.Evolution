package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.loading.VariantSource;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.salvageyards.industry.SalvageYards;
import indevo.industries.salvageyards.memory.WreckRegistry;
import indevo.industries.salvageyards.plugins.SalvageYardsDialogPlugin;
import indevo.industries.salvageyards.plugins.SalvageYardsView;
import indevo.industries.salvageyards.plugins.SalvageYardsView.Tab;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// test only: every Salvage Yards panel view from code, audited and captured
public final class SalvageUiChecks {
    private static final String[] DMODS = {"comp_armor", "degraded_engines", "faulty_grid", "glitched_sensors", "comp_hull"};
    private static final String[] SMODS = {"hardenedshieldemitter", "heavyarmor", "reinforcedhull"};
    private static final String[] REG_HULLS = {"wolf", "lasher", "hammerhead", "falcon", "eagle", "dominator", "condor", "enforcer"};
    private static final String[] REG_WEAPONS = {"heavymg", "lightmg", "heavymauler", "hveldriver", "pdlaser", "ioncannon", "harpoon", "sabot", "annihilator",
            "lightac", "lightdualac", "heavyac", "flak", "dualflak", "mininglaser", "taclaser", "gravitonbeam", "phasebeam", "railgun", "hephag"};
    private static final String[] REG_WINGS = {"talon_wing", "broadsword_wing", "piranha_wing", "warthog_wing", "dagger_wing", "longbow_wing", "gladius_wing",
            "thunder_wing", "wasp_wing", "mining_drone_wing", "xyphos_wing", "claw_wing", "khopesh_wing", "trident_wing", "sparkfighter_wing", "lux_wing"};

    private SalvageUiChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(SalvageUiChecks::setup);

        UiViews.view(steps, "salvage-assembly-empty", c -> open(c, new SalvageYardsView(Tab.ASSEMBLY)));
        UiViews.view(steps, "salvage-dmods-none", c -> open(c, new SalvageYardsView(Tab.DMODS)));
        UiViews.view(steps, "salvage-smods-none", c -> open(c, new SalvageYardsView(Tab.SMODS)));
        UiViews.view(steps, "salvage-dmods", c -> {
            ships(c);
            SalvageYardsView view = new SalvageYardsView(Tab.DMODS);
            view.origin = (String) c.get("dorigin");
            view.target = (String) c.get("dtarget");
            view.mods.add(DMODS[0]);
            view.mods.add(DMODS[1]);
            open(c, view);
        });
        UiViews.view(steps, "salvage-smods", c -> {
            SalvageYardsView view = new SalvageYardsView(Tab.SMODS);
            view.origin = (String) c.get("sorigin");
            view.target = (String) c.get("starget");
            view.mods.add(SMODS[1]);
            open(c, view);
        });
        UiViews.view(steps, "salvage-smods-blocked", c -> {
            SalvageYardsView view = new SalvageYardsView(Tab.SMODS);
            view.origin = (String) c.get("sorigin");
            view.target = (String) c.get("sblocked");
            view.mods.add(SMODS[0]);
            open(c, view);
        });
        UiViews.view(steps, "salvage-assembly-confirm", c -> open(c, order(new SalvageYardsView(Tab.ASSEMBLY))),
                (c, panel) -> c.expect(UiAudit.click(panel.content(), "Confirm"), "assembly order confirmed"));
        UiViews.view(steps, "salvage-assembly", c -> {
            SalvageYardsView view = order(new SalvageYardsView(Tab.ASSEMBLY));
            view.order.parts = 60;
            view.order.relics = 10;
            open(c, view);
        });
        UiViews.view(steps, "salvage-assembly-scrapped", c -> {
            SalvageYardsView view = order(new SalvageYardsView(Tab.ASSEMBLY));
            for (int i = 0; i < 12; i++) {
                FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, i % 2 == 0 ? "wolf_Assault" : "lasher_CS");
                Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
                view.order.scrapped.add(m);
            }
            open(c, view);
        });
        UiViews.view(steps, "salvage-assembly-overbudget", c -> {
            SalvageYardsView view = order(new SalvageYardsView(Tab.ASSEMBLY));
            view.order.hulls.put("onslaught", 5);
            view.order.hulls.put("paragon", 3);
            open(c, view);
        });
        UiViews.view(steps, "salvage-assembly-nocredits", c -> {
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(0f);
            open(c, order(new SalvageYardsView(Tab.ASSEMBLY)));
        });
        UiViews.view(steps, "salvage-assembly-story", c -> {
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(5000000f);
            SalvageYardsView view = order(new SalvageYardsView(Tab.ASSEMBLY));
            view.confirm.arm("story");
            open(c, view);
        });
        UiViews.view(steps, "salvage-assembly-accelerate", c -> open(c, order(new SalvageYardsView(Tab.ASSEMBLY))),
                (c, panel) -> c.expect(UiAudit.clickLabel(panel.content(), "Accelerate"), "accelerate armed"));
        for (int i = 0; i < 4; i++) confirmOrder(steps);
        UiViews.view(steps, "salvage-assembly-many", c -> {
            SalvageYardsView view = order(new SalvageYardsView(Tab.ASSEMBLY));
            for (String id : weapons()) view.order.weapons.put(id, 2);
            for (String id : wings()) view.order.wings.put(id, 1);
            open(c, view);
        });
        UiViews.view(steps, "salvage-registry-empty", c -> {
            yards(c).setSpecialItem(new SpecialItemData(ItemIds.SALVAGE_DRONES, null));
            c.market("a").reapplyIndustries();
            open(c, new SalvageYardsView(Tab.REGISTRY));
        });
        UiViews.view(steps, "salvage-registry-few", c -> {
            registry(c, 2, 3, 0);
            open(c, new SalvageYardsView(Tab.REGISTRY));
        });
        UiViews.view(steps, "salvage-registry", c -> {
            registry(c, REG_HULLS.length, REG_WEAPONS.length, REG_WINGS.length);
            SalvageYardsView view = new SalvageYardsView(Tab.REGISTRY);
            view.registryHull = "eagle";
            view.order.registryHulls.put("eagle", 1);
            open(c, view);
        });
        UiViews.view(steps, "salvage-worst", c -> {
            c.market("a").setName("Nova Maxios Prime Station");
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(987654321f);
            for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (!m.isFlagship()) m.setShipName("ISS Unreasonably Long Ship Name");
            }
            SalvageYardsView view = order(new SalvageYardsView(Tab.ASSEMBLY));
            view.order.hulls.put("onslaught", 5);
            open(c, view);
        });
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    private static void setup(Ctx c) {
        Colony.pair(c, 6);
        MarketAPI a = c.market("a");
        a.addIndustry(Ids.SCRAPYARD);
        a.reapplyIndustries();
        Colony.jump(a.getContainingLocation(), a.getPrimaryEntity().getLocation().x + 300f, a.getPrimaryEntity().getLocation().y);
        SalvageYards yards = yards(c);
        ReflectionUtils.set("availableSalvagePoints", yards, 25000);
        Global.getSector().getPlayerFleet().getCargo().getCredits().set(5000000f);
        MiscIE.getStorageCargo(a).addCommodity(ItemIds.PARTS, 400f);
        MiscIE.getStorageCargo(a).addCommodity(ItemIds.RARE_PARTS, 300f);
        for (String id : new String[]{"wolf", "hammerhead", "falcon", "onslaught", "paragon"}) Global.getSector().getPlayerFaction().addKnownShip(id, false);
        c.note("salvage yards at " + a.getName() + ", salvage units " + yards.getAvailableSalvagePoints());
    }

    // one more order in progress, no capture
    private static void confirmOrder(List<Step> steps) {
        steps.add(c -> {
            open(c, order(new SalvageYardsView(Tab.ASSEMBLY)));
            c.frames(30);
        });
        steps.add(c -> {
            UiAudit.Found found = UiAudit.find();
            c.expect(found != null && UiAudit.click(found.content(), "Confirm"), "extra order confirmed");
            if (found != null) UiAudit.close(found);
            c.frames(20);
        });
        steps.add(c -> {
            UiAudit.dismissDialog();
            c.frames(5);
        });
    }

    private static SalvageYardsView order(SalvageYardsView view) {
        view.order.hulls.put("wolf", 2);
        view.order.hulls.put("hammerhead", 1);
        view.order.weapons.put("heavymg", 4);
        view.order.wings.put("talon_wing", 2);
        view.order.hulls.put("falcon", 1);
        return view;
    }

    // dmod origin and target, smod origin, target and a target without the hullmod; all eagle sized midline cruisers
    private static void ships(Ctx c) {
        FleetMemberAPI dOrigin = member("eagle_Assault", "ISS Scarred Veteran");
        ShipVariantAPI v = variant(dOrigin);
        for (String id : DMODS) v.addPermaMod(id, false);
        dOrigin.setVariant(v, false, true);
        FleetMemberAPI dTarget = member("falcon_Attack", "ISS Clean Slate");
        c.put("dorigin", dOrigin.getId());
        c.put("dtarget", dTarget.getId());

        FleetMemberAPI sOrigin = member("eagle_Assault", "ISS Old Hand");
        v = variant(sOrigin);
        for (String id : SMODS) v.addPermaMod(id, true);
        sOrigin.setVariant(v, false, true);
        FleetMemberAPI sTarget = member("falcon_Attack", "ISS New Blood");
        v = variant(sTarget);
        v.addMod(SMODS[1]);
        sTarget.setVariant(v, false, true);
        FleetMemberAPI sBlocked = member("falcon_Attack", "ISS Bare Frame");
        c.put("sorigin", sOrigin.getId());
        c.put("starget", sTarget.getId());
        c.put("sblocked", sBlocked.getId());
    }

    private static FleetMemberAPI member(String variant, String name) {
        FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        m.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
        return m;
    }

    private static ShipVariantAPI variant(FleetMemberAPI m) {
        ShipVariantAPI v = m.getVariant().clone();
        v.setSource(VariantSource.REFIT);
        return v;
    }

    @SuppressWarnings("unchecked")
    private static void registry(Ctx c, int hulls, int weapons, int wings) {
        WreckRegistry registry = yards(c).getWreckRegistry();
        Map<String, List<WreckRegistry.WreckFit>> hullMap = (Map<String, List<WreckRegistry.WreckFit>>) ReflectionUtils.get("hulls", registry);
        Map<String, Integer> weaponMap = (Map<String, Integer>) ReflectionUtils.get("weapons", registry);
        Map<String, Integer> wingMap = (Map<String, Integer>) ReflectionUtils.get("wings", registry);
        hullMap.clear();
        weaponMap.clear();
        wingMap.clear();
        for (int i = 0; i < hulls; i++) {
            String id = REG_HULLS[i];
            List<WreckRegistry.WreckFit> fits = new ArrayList<>();
            ShipVariantAPI variant = Global.getSettings().getVariant(Global.getSettings().getHullIdToVariantListMap().get(id).get(0));
            for (int n = 0; n <= i % 5; n++) fits.add(new WreckRegistry.WreckFit(id, variant));
            hullMap.put(id, fits);
        }
        List<String> w = weapons();
        List<String> f = wings();
        for (int i = 0; i < Math.min(weapons, w.size()); i++) weaponMap.put(w.get(i), 1 + i % 20);
        for (int i = 0; i < Math.min(wings, f.size()); i++) wingMap.put(f.get(i), 1 + i % 10);
    }

    // only ids this game has, getWeaponSpec throws on a missing one
    private static List<String> weapons() {
        List<String> out = new ArrayList<>();
        for (String id : REG_WEAPONS) {
            try {
                if (Global.getSettings().getWeaponSpec(id) != null) out.add(id);
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    private static List<String> wings() {
        List<String> out = new ArrayList<>();
        for (String id : REG_WINGS) {
            try {
                if (Global.getSettings().getFighterWingSpec(id) != null) out.add(id);
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    private static SalvageYards yards(Ctx c) {
        return (SalvageYards) c.market("a").getIndustry(Ids.SCRAPYARD);
    }

    private static void open(Ctx c, SalvageYardsView view) {
        c.expect(SalvageYardsDialogPlugin.show(c.market("a"), view), "salvage yards panel opens");
    }
}
