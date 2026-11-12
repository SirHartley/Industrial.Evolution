package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.salvageyards.industry.SalvageYards;
import indevo.industries.salvageyards.memory.YardsProductionData;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static indevo.items.installable.SpecialItemEffectsRepo.SALVAGE_DRONES_SP_PER_FP;

public class SalvageYardsPanel extends BaseCustomDialogDelegate {

    public static final float BOARD_W = 1024f;
    public static final float BOARD_H = 760f;
    static final float BODY_Y = 64f;
    static final float TAB_W = 156f;
    static final float SIDE_W = 304f;
    static final float SIDE_GAP = 20f;

    private final MarketAPI market;
    private final SalvageYardsView view;
    private final SalvageYardsDialogPlugin host;
    private CustomDialogCallback callback;
    private UiRoot root;
    private boolean open = false;
    private boolean forPicker = false;

    public SalvageYardsPanel(MarketAPI market, SalvageYardsView view, SalvageYardsDialogPlugin host) {
        this.market = market;
        this.view = view;
        this.host = host;
    }

    public static float getWidth() {
        return UiFrame.width(BOARD_W);
    }

    public static float getHeight() {
        return UiFrame.height(BOARD_H);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        open = true;
        forPicker = false;
        host.panelShown();
        root = new UiRoot(panel, view.scroll, this::build);
        root.refresh();
    }

    @Override
    public boolean hasCancelButton() {
        return false;
    }

    @Override
    public String getConfirmText() {
        return "Return";
    }

    @Override
    public void customDialogConfirm() {
        closed();
    }

    @Override
    public void customDialogCancel() {
        closed();
    }

    private void closed() {
        open = false;
        if (!forPicker) host.leave();
    }

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    void dismissForPicker() {
        forPicker = true;
        callback.dismissCustomDialog(1);
    }

    void openPicker(Consumer<InteractionDialogAPI> picker) {
        host.openPicker(picker);
    }

    void refresh() {
        root.refresh();
    }

    MarketAPI getMarket() {
        return market;
    }

    SalvageYardsView getView() {
        return view;
    }

    SalvageYards getYards() {
        return (SalvageYards) market.getIndustry(Ids.SCRAPYARD);
    }

    boolean hasRegistry() {
        SalvageYards yards = getYards();
        return yards != null && yards.hasSalvageDrones();
    }

    private List<SalvageYardsView.Tab> getTabs() {
        List<SalvageYardsView.Tab> tabs = new ArrayList<>();
        tabs.add(SalvageYardsView.Tab.ASSEMBLY);
        if (hasRegistry()) tabs.add(SalvageYardsView.Tab.REGISTRY);
        tabs.add(SalvageYardsView.Tab.DMODS);
        tabs.add(SalvageYardsView.Tab.SMODS);
        return tabs;
    }

    private void build(CustomPanelAPI p, float w, float h) {
        if (getYards() == null) {
            UiFrame.title(p, w, "Salvage Yards", market.getName(), null);
            Ui.text(p, 0f, BODY_Y + 6f, w, "The Salvage Yards are gone.", Ui.gray(), Ui.CENTER);
            return;
        }

        List<SalvageYardsView.Tab> tabs = getTabs();
        if (!tabs.contains(view.tab)) view.tab = SalvageYardsView.Tab.ASSEMBLY;

        YardsContract contract = new YardsContract(market, view.order);
        UiFrame.title(p, w, getYards().getCurrentName(), market.getName(), this::addHelp);
        float tabsEnd = addTabs(p, w, tabs, contract);
        addCounters(p, w, tabsEnd, contract);

        switch (view.tab) {
            case ASSEMBLY -> new AssemblyTab(this, contract).build(p, w, h);
            case REGISTRY -> new RegistryTab(this, contract).build(p, w, h);
            case DMODS -> new TransferTab(this, ModTransfer.Kind.DMOD).build(p, w, h);
            case SMODS -> new TransferTab(this, ModTransfer.Kind.SMOD).build(p, w, h);
        }
    }

    private float addTabs(CustomPanelAPI p, float w, List<SalvageYardsView.Tab> tabs, YardsContract contract) {
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) labels[i] = getTabLabel(tabs.get(i), contract);

        UiButtons.tabs(p, 0f, UiFrame.HEADER_Y, w, TAB_W, UiFrame.HEADER_H, labels, tabs.indexOf(view.tab), null, i -> switchTab(tabs.get(i)));
        return tabs.size() * (TAB_W + UiButtons.TAB_GAP);
    }

    private static String getTabLabel(SalvageYardsView.Tab tab, YardsContract contract) {
        return switch (tab) {
            case ASSEMBLY -> {
                int lines = contract.getOrder().getLineCount();
                yield lines > 0 ? "Ship assembly (" + lines + ")" : "Ship assembly";
            }
            case REGISTRY -> "Wreck Registry";
            case DMODS -> "D-mods";
            case SMODS -> "S-mods";
        };
    }

    private void switchTab(SalvageYardsView.Tab tab) {
        if (view.tab != tab) {
            view.tab = tab;
            view.confirm.clear();
        }

        refresh();
    }

    // right to left: credits, Relic Components, Ship Components, salvage units; a slot that would reach the tabs is left out
    private void addCounters(CustomPanelAPI p, float w, float tabsEnd, YardsContract contract) {
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        String relicName = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();
        String partsName = Global.getSettings().getCommoditySpec(ItemIds.PARTS).getName();

        float right = w;
        right = counter(p, right, tabsEnd, null, UiImages.creditsSprite(), Ui.credits(credits), "Credits");
        right = counter(p, right, tabsEnd, null, UiImages.relicSprite(), Ui.num(contract.getRelicsAvailable()), relicName + ", in your fleet and in storage here");
        right = counter(p, right, tabsEnd, null, UiImages.partsSprite(), Ui.num(contract.getPartsAvailable()), partsName + ", in your fleet and in storage here");
        counter(p, right, tabsEnd, "Salvage units", null, Ui.num(contract.getAvailableSalvageUnits()), "Salvage units at these yards");
    }

    private static float counter(CustomPanelAPI p, float right, float min, String label, String sprite, String value, String name) {
        float vw = Ui.width(value, Ui.INS15) + 2f;
        float iconW = sprite == null ? 0f : UiData.COST_ICON + 4f;
        float lw = label == null ? 0f : Ui.width(label, Ui.INS15) + 2f + 6f;
        float left = right - vw - iconW - lw;
        if (left < min + 10f) return right;

        float y = UiFrame.HEADER_Y;
        CustomPanelAPI cell = Ui.panel(p, left, y, right - left, UiFrame.HEADER_H);
        if (label != null) Ui.textIn(cell, 0f, 0f, lw - 6f, UiFrame.HEADER_H, label, Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        if (sprite != null) UiImages.img(cell, lw, 1f, UiData.COST_ICON, UiData.COST_ICON, sprite);
        Ui.textIn(cell, lw + iconW, 0f, vw, UiFrame.HEADER_H, value, Ui.INS15, Ui.hl(), Ui.LEFT, new String[0], new Color[0]);
        Ui.tooltip(p, cell, name);
        return left - UiFrame.SLOT_GAP;
    }

    private void addHelp(TooltipMakerAPI t) {
        Color hl = Misc.getHighlightColor();
        float pad = 10f;

        switch (view.tab) {
            case ASSEMBLY -> {
                t.addPara("The yards build ships from your blueprints and from the Wreck Registry. Delivery to local storage takes %s.", 0f, hl, YardsContract.getDeliveryText());
                t.addPara("The budget is the yards' capacity, plus %s of the value of scrapped ships, less per D-mod. A story point doubles the yard capacity for one contract and is kept until the next one.", pad, hl, "30%");
                t.addPara("The price follows your standing with " + market.getFaction().getDisplayNameWithArticle() + ": %s at best, %s at worst.", pad, hl, "90%", "150%");
                t.addPara("%s reduce the D-mods on the new hulls, %s for each fleet point fills the quota. %s count %s each against the cost, up to half of it.", pad, hl,
                        Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName(), Setting.RARE_PARTS_AMOUNT_PER_FP.intValue() + "",
                        Global.getSettings().getCommoditySpec(ItemIds.PARTS).getName(), Misc.getDGSCredits(YardsProductionData.getPartValue()));
                t.addPara("A running order can be accelerated once every %s with %s.", pad, hl, "60 days", Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName());
                t.addPara("\"Spend enough time taking 'em apart, you get to know how to make one. Bring us the parts we can't just scrounge up, pay us, and we'll build you a beauty!\"", Misc.getGrayColor(), pad);
            }
            case REGISTRY -> {
                t.addPara("The salvage drones keep a Wreck Registry of hulls, weapons and fighter wings lost nearby. Each entry can be built once without a blueprint.", 0f);
                t.addPara("Registry hulls cost %s salvage units per fleet point and come with the weapons, fighter wings and hullmods they had when they were lost, charged at their value.", pad, hl, SALVAGE_DRONES_SP_PER_FP + "");
                t.addPara("Weapons and fighter wings cost no salvage units.", pad);
            }
            case DMODS -> {
                t.addPara("The salvage yards can transfer D-Mods between ships of the same size and manufacturer. The ship that takes them must be restorable.", 0f);
                t.addPara("The fee per D-mod follows the value of the source hull and its D-mods, and your standing: %s at best, %s at worst.", pad, hl, "80%", "150%");
                t.addPara("\"One mans trash is also another mans trash, but I can do more with it than they ever could!\"", Misc.getGrayColor(), pad);
            }
            case SMODS -> {
                t.addPara("The salvage yards can move S-mods between ships of the same size and manufacturer, for relic components and credits. The target ship needs a free S-mod slot and the hullmod installed.", 0f);
                t.addPara("Each S-mod costs %s Relic Components per deployment point of the target ship, plus a fee by your standing.", pad, hl, Math.round(ModTransfer.RELICS_PER_DP) + "");
                t.addPara(ModTransfer.LORE, Misc.getGrayColor(), pad);
            }
        }
    }
}
