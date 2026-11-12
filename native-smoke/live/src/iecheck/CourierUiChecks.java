package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import indevo.ids.Ids;
import indevo.industries.warehouses.data.WarehouseSubmarketData;
import indevo.industries.warehouses.industry.Warehouses;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingContract.Scope;
import indevo.industries.courierport.ShippingContractMemory;
import indevo.industries.courierport.ShippingCostCalculator;
import indevo.industries.courierport.dialogue.CourierPortDialoguePlugin;
import indevo.industries.courierport.dialogue.CourierView;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.List;

// test only: every Courier Port panel view from code, audited and captured
public final class CourierUiChecks {
    private static final String[] STORAGES = {"IndEvo_RepairStorage", "IndEvo_DeconstStorage", "IndEvo_EngStorage", "IndEvo_SharedStore", "IndEvo_MuseumStorage",
            "IndEvo_WarehouseStorage_1", "IndEvo_WarehouseStorage_2", "IndEvo_WarehouseStorage_3", "IndEvo_WarehouseStorage_4", "IndEvo_WarehouseStorage_5"};

    private CourierUiChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(CourierUiChecks::setup);

        UiViews.view(steps, "courier-list", c -> open(c, CourierView.contract(id(c, "cargo"))));
        UiViews.view(steps, "courier-byload", c -> open(c, CourierView.contract(id(c, "byload"))));
        UiViews.view(steps, "courier-stockpiles", c -> open(c, CourierView.contract(id(c, "stockpiles"))));
        UiViews.view(steps, "courier-invalid", c -> open(c, CourierView.contract(id(c, "invalid"))));
        UiViews.view(steps, "courier-remove-confirm", c -> {
            CourierView view = CourierView.contract(id(c, "cargo"));
            view.confirm.arm("remove");
            open(c, view);
        });
        UiViews.view(steps, "courier-core-beta", c -> {
            core(c, Commodities.BETA_CORE);
            open(c, CourierView.contract(id(c, "cargo")));
        });
        UiViews.view(steps, "courier-core-alpha", c -> {
            core(c, Commodities.ALPHA_CORE);
            open(c, CourierView.contract(id(c, "cargo")));
        });
        UiViews.view(steps, "courier-new-step1", c -> {
            core(c, null);
            open(c, CourierView.newContract(null, null, null, null, null));
        });
        UiViews.view(steps, "courier-new-step2", c -> open(c, CourierView.newContract(mid(c, "a"), null, null, null, null)));
        UiViews.view(steps, "courier-new-step3", c -> open(c, CourierView.newContract(mid(c, "a"), Submarkets.SUBMARKET_STORAGE, null, null, null)));
        UiViews.view(steps, "courier-new-step4", c -> open(c, CourierView.newContract(mid(c, "a"), Submarkets.SUBMARKET_STORAGE, mid(c, "b"), null, null)));
        UiViews.view(steps, "courier-new-ready", c -> open(c, CourierView.newContract(mid(c, "a"), Submarkets.SUBMARKET_STORAGE, mid(c, "b"), Submarkets.SUBMARKET_STORAGE, Scope.EVERYTHING)));
        UiViews.view(steps, "courier-storages-many", c -> {
            MarketAPI a = c.market("a");
            for (String id : STORAGES) if (!id.startsWith("IndEvo_Warehouse") && a.getSubmarket(id) == null) a.addSubmarket(id);
            a.addIndustry(Ids.WAREHOUSES);
            a.reapplyIndustries();
            Warehouses warehouses = (Warehouses) a.getIndustry(Ids.WAREHOUSES);
            for (int i = 1; i <= 5; i++) {
                String id = Ids.WAREHOUSE_SUBMARKET + "_" + i;
                if (a.getSubmarket(id) == null) warehouses.addSubmarket(new WarehouseSubmarketData(id, i == 1 ? "Long Term Storage of Unsorted Relic Crates" : "Depot " + i, true, true, false));
            }
            open(c, CourierView.newContract(mid(c, "a"), null, null, null, null));
        });
        UiViews.view(steps, "courier-storages-expanded", c -> {
            CourierView view = CourierView.newContract(mid(c, "a"), null, null, null, null);
            view.expanded = CourierView.FROM;
            open(c, view);
        });
        UiViews.view(steps, "courier-many", c -> {
            for (int i = 0; i < 54; i++) contract("Route " + (i + 7), mid(c, "a"), Submarkets.SUBMARKET_STORAGE, mid(c, "b"), Submarkets.SUBMARKET_STORAGE, Scope.ALL_SHIPS, 30);
            List<ShippingContract> list = ShippingContractMemory.getContractList();
            CourierView view = CourierView.contract(list.get(30).id);
            view.scroll.set(CourierView.LIST, 900f);
            open(c, view);
        }, (c, panel) -> c.expect(UiAudit.clickRow(panel.content(), ShippingContractMemory.getContractList().get(30).name), "selected row clicked again"));
        UiViews.view(steps, "courier-worst", c -> {
            c.market("a").setName("Nova Maxios Prime Station");
            Global.getSector().getPlayerFleet().getCargo().getCredits().set(987654321f);
            ShippingContract worst = ShippingContractMemory.getContract(id(c, "cargo"));
            worst.name = "Monthly relic convoy to the outer stations";
            worst.targetCargo.addCommodity(Commodities.SUPPLIES, 987654f);
            open(c, CourierView.contract(worst.id));
        });
        UiViews.view(steps, "courier-empty", c -> {
            ShippingContractMemory.getContractList().clear();
            open(c, new CourierView());
        });
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    private static void setup(Ctx c) {
        Colony.pair(c, 6);
        MarketAPI a = c.market("a");
        MarketAPI b = c.market("b");
        a.addIndustry(Ids.PORT);
        a.reapplyIndustries();
        Colony.jump(a.getContainingLocation(), a.getPrimaryEntity().getLocation().x + 300f, a.getPrimaryEntity().getLocation().y);
        MiscIE.getStorageCargo(a).addCommodity(Commodities.METALS, 500f);
        MiscIE.getStorageCargo(a).addCommodity(Commodities.SUPPLIES, 300f);
        MiscIE.getStorageCargo(a).addMothballedShip(com.fs.starfarer.api.fleet.FleetMemberType.SHIP, "eagle_Assault", null);
        ShippingContractMemory.getContractList().clear();

        String sa = Submarkets.SUBMARKET_STORAGE;
        ShippingContract cargo = contract("Metals to the forge", a.getId(), sa, b.getId(), sa, Scope.SPECIFIC_CARGO, 30);
        cargo.targetCargo.addCommodity(Commodities.METALS, 200f);
        cargo.targetCargo.addCommodity(Commodities.SUPPLIES, 50f);
        c.put("cargo", cargo.id);
        c.put("byload", contract("Everything home", b.getId(), sa, a.getId(), sa, Scope.ALL_CARGO, 60).id);
        c.put("stockpiles", contract("Stockpile run", a.getId(), Submarkets.LOCAL_RESOURCES, b.getId(), sa, Scope.ALL_CARGO, 0).id);
        contract("Ships to the yard", a.getId(), sa, b.getId(), sa, Scope.ALL_SHIPS, 90).isActive = false;
        contract("Weapons", b.getId(), sa, a.getId(), sa, Scope.ALL_WEAPONS, 14);
        c.put("invalid", contract("Lost route", a.getId(), sa, "iecheck_gone_market", sa, Scope.EVERYTHING, 30).id);
        c.note("courier port at " + a.getName() + ", contracts " + ShippingContractMemory.getContractList().size());
    }

    private static ShippingContract contract(String name, String from, String fromSub, String to, String toSub, Scope scope, int days) {
        ShippingContract contract = new ShippingContract();
        contract.name = name;
        contract.fromMarketId = from;
        contract.fromSubmarketId = fromSub;
        contract.toMarketId = to;
        contract.toSubmarketId = toSub;
        contract.scope = scope;
        contract.setRecurrentDays(days);
        // addOrReplaceContract refuses invalid contracts, the invalid view needs one stored
        ShippingContractMemory.getContractList().add(contract);
        return contract;
    }

    // the port writes its core into the sector memory key on apply, so install it there
    private static void core(Ctx c, String id) {
        c.market("a").getIndustry(Ids.PORT).setAICoreId(id);
        c.market("a").reapplyIndustries();
        c.note("courier core " + Global.getSector().getMemoryWithoutUpdate().getString(ShippingCostCalculator.AI_CORE_ID_STRING));
    }

    private static void open(Ctx c, CourierView view) {
        c.expect(CourierPortDialoguePlugin.show(c.market("a").getPrimaryEntity(), view), "courier panel opens");
    }

    private static String id(Ctx c, String key) {
        return (String) c.get(key);
    }

    private static String mid(Ctx c, String key) {
        return c.market(key).getId();
    }
}
