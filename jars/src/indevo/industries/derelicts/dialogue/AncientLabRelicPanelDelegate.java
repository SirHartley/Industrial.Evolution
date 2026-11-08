package indevo.industries.derelicts.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.hullmods.Automated;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.dialogue.sidepanel.HelpIcon;
import indevo.ids.Ids;
import indevo.items.ForgeTemplateItemPlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.RelicHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AncientLabRelicPanelDelegate extends ButtonReportingDialogueDelegate {

    private enum Tab {TEMPLATES, SHIPS}

    public static final int RELICS_PER_TEMPLATE_FP = 2;
    public static final int RELICS_PER_SHIP_DP = 10;

    public static final String REFINE_LORE = "Centuries of copying have left gaps in the template. Domain reference cores give the laboratory an intact pattern to work from.";
    public static final String INTEGRATE_LORE = "The automated hulls still expect commands from a Domain control network. Your technicians use relic components to build an interface their systems will recognise.";

    // outer dialog adds 20 x 55, so it stays inside the vanilla cargo tab (1004 x 850)
    private static final float MAX_WIDTH = 860f;
    private static final float MAX_HEIGHT = 560f;

    private static final float OPAD = 10f;
    private static final float PAD = 3f;
    private static final float GAP = 10f;
    private static final float HEADER_HEIGHT = 50f;
    private static final float TAB_HEIGHT = 28f;
    private static final float TAB_WIDTH = 200f;
    private static final float FOOTER_HEIGHT = 24f;
    private static final float HEADING_HEIGHT = 20f;
    private static final float ACTION_HEIGHT = 60f;
    private static final float ROW_HEIGHT = 48f;
    private static final float ICON_SIZE = 40f;
    private static final float BUTTON_WIDTH = 150f;
    private static final float BUTTON_HEIGHT = 25f;

    private final MarketAPI market;

    private Tab tab = Tab.TEMPLATES;
    private Object selected;
    private String message;
    private Color messageColor;

    private CustomPanelAPI basePanel;
    private CustomPanelAPI panel;
    private ScrollPanelAPI listScroller;

    public AncientLabRelicPanelDelegate(MarketAPI market) {
        this.market = market;
    }

    public static void show(DialogCreatorUI ui, MarketAPI market) {
        float width = Math.min(MAX_WIDTH, Global.getSettings().getScreenWidth() - 40f);
        float height = Math.min(MAX_HEIGHT, Global.getSettings().getScreenHeight() - 300f);
        ui.showDialog(width, height, new AncientLabRelicPanelDelegate(market));
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        basePanel = panel;
        recreatePanel(callback);
    }

    @Override
    public boolean hasCancelButton() {
        return false;
    }

    @Override
    public String getConfirmText() {
        return "Return";
    }

    private void recreatePanel(CustomDialogCallback callback) {
        float listOffset = listScroller != null ? listScroller.getYOffset() : 0f;
        if (panel != null) basePanel.removeComponent(panel);

        float width = basePanel.getPosition().getWidth();
        float height = basePanel.getPosition().getHeight();
        panel = Global.getSettings().createCustom(width, height, null);

        List<Entry> entries = tab == Tab.TEMPLATES ? getTemplateEntries() : getShipEntries();
        Entry current = null;
        for (Entry entry : entries) {
            if (entry.key.equals(selected)) current = entry;
        }
        if (current == null) selected = null;

        addHeader(width);
        addTabs(width, callback);

        float y = HEADER_HEIGHT + TAB_HEIGHT + GAP;
        float columnHeight = height - y - FOOTER_HEIGHT;
        float listWidth = (width - GAP) * 0.55f;
        float detailX = listWidth + GAP;

        TooltipMakerAPI list = addList(entries, 0f, y, listWidth, columnHeight, callback);
        addDetail(current, detailX, y, width - detailX, columnHeight, callback);
        addFooter(width, height);

        basePanel.addComponent(panel);

        listScroller = list.getExternalScroller();
        if (listScroller != null) listScroller.setYOffset(listOffset);
    }

    private void addHeader(float width) {
        TooltipMakerAPI header = panel.createUIElement(width, HEADER_HEIGHT, false);
        header.addSectionHeading("Relic services", Alignment.MID, 0f);
        header.addPara("%s available: %s", OPAD, Misc.getHighlightColor(), RelicHelper.getName(), RelicHelper.getAvailable(market) + "");
        panel.addUIElement(header).inTL(0f, 0f);
    }

    private void addTabs(float width, CustomDialogCallback callback) {
        CustomPanelAPI tabs = panel.createCustomPanel(width, TAB_HEIGHT, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI row = tabs.createUIElement(width, TAB_HEIGHT, false);

        ButtonAPI templates = addTabButton(row, "Templates", Tab.TEMPLATES, callback);
        ButtonAPI ships = addTabButton(row, "Ships", Tab.SHIPS, callback);
        templates.getPosition().inTL(0f, 0f);
        ships.getPosition().rightOfTop(templates, PAD);

        tabs.addUIElement(row).inTL(0f, 0f);
        panel.addComponent(tabs).inTL(0f, HEADER_HEIGHT);
    }

    private ButtonAPI addTabButton(TooltipMakerAPI row, String text, Tab target, CustomDialogCallback callback) {
        ButtonAPI button = row.addAreaCheckbox(text, action(callback, () -> selectTab(target)), Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor(), TAB_WIDTH, TAB_HEIGHT - 4f, 0f);
        button.setChecked(tab == target);
        return button;
    }

    private TooltipMakerAPI addList(List<Entry> entries, float x, float y, float width, float height, CustomDialogCallback callback) {
        TooltipMakerAPI heading = panel.createUIElement(width, HEADING_HEIGHT, false);
        heading.addSectionHeading(tab == Tab.TEMPLATES ? "Forge Templates" : "Automated ships", Alignment.MID, 0f);
        panel.addUIElement(heading).inTL(x, y);

        TooltipMakerAPI list = panel.createUIElement(width, height - HEADING_HEIGHT - PAD, true);
        if (entries.isEmpty()) {
            String empty = tab == Tab.TEMPLATES
                    ? "No Forge Templates in local or industrial storage."
                    : "No automated ships in your fleet or local storage.";
            list.addPara(empty, Misc.getGrayColor(), OPAD);
        }

        for (Entry entry : entries) {
            list.addCustom(createRow(entry, width - 15f, callback), PAD);
        }

        panel.addUIElement(list).inTL(x, y + HEADING_HEIGHT + PAD);
        return list;
    }

    private CustomPanelAPI createRow(Entry entry, float width, CustomDialogCallback callback) {
        CustomPanelAPI row = panel.createCustomPanel(width, ROW_HEIGHT, new ButtonReportingCustomPanel(this));

        TooltipMakerAPI background = row.createUIElement(width, ROW_HEIGHT, false);
        ButtonAPI button = background.addAreaCheckbox("", action(callback, () -> select(entry.key)), Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor(), width, ROW_HEIGHT, 0f);
        button.setChecked(entry.key.equals(selected));
        row.addUIElement(background).inTL(-OPAD, 0f);

        TooltipMakerAPI icon = row.createUIElement(ICON_SIZE, ICON_SIZE, false);
        List<FleetMemberAPI> members = new ArrayList<>();
        members.add(entry.display);
        icon.addShipList(1, 1, ICON_SIZE, Misc.getBasePlayerColor(), members, 0f);
        row.addUIElement(icon).inTL(-OPAD + 4f, (ROW_HEIGHT - ICON_SIZE) / 2f);

        float textX = ICON_SIZE + 12f;
        TooltipMakerAPI text = row.createUIElement(width - textX - 6f, ROW_HEIGHT, false);
        text.addPara(entry.name, Misc.getBasePlayerColor(), 0f);
        text.addPara(entry.sub, Misc.getGrayColor(), 1f);
        row.addUIElement(text).inTL(-OPAD + textX, 6f);

        return row;
    }

    private void addDetail(Entry entry, float x, float y, float width, float height, CustomDialogCallback callback) {
        TooltipMakerAPI heading = panel.createUIElement(width - HelpIcon.SIZE - PAD, HEADING_HEIGHT, false);
        heading.addSectionHeading(entry != null ? entry.name : "Details", Alignment.MID, 0f);
        panel.addUIElement(heading).inTL(x, y);
        panel.addUIElement(HelpIcon.create(panel, this::addHelp)).inTL(x + width - HelpIcon.SIZE, y);

        TooltipMakerAPI detail = panel.createUIElement(width, height - HEADING_HEIGHT - PAD - ACTION_HEIGHT, false);

        if (entry == null) {
            String pick = tab == Tab.TEMPLATES ? "Pick a Forge Template to refine." : "Pick a ship to integrate.";
            detail.addPara(pick, Misc.getGrayColor(), 0f);
            panel.addUIElement(detail).inTL(x, y + HEADING_HEIGHT + PAD);
            return;
        }

        if (tab == Tab.TEMPLATES) addTemplateDetail(detail, entry);
        else addShipDetail(detail, entry);

        boolean canPay = RelicHelper.canAfford(market, entry.cost);
        RelicHelper.addCostLine(detail, market, entry.cost, OPAD);

        panel.addUIElement(detail).inTL(x, y + HEADING_HEIGHT + PAD);

        CustomPanelAPI actionPanel = panel.createCustomPanel(width, ACTION_HEIGHT, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI actions = actionPanel.createUIElement(width, ACTION_HEIGHT, false);
        ButtonAPI confirm = actions.addButton("Confirm", action(callback, () -> apply(entry)), Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.TL_BR, BUTTON_WIDTH, BUTTON_HEIGHT, OPAD);
        confirm.setEnabled(canPay);
        addConfirmTooltip(actions, entry);
        if (!canPay) actions.addPara("Not enough %s.", PAD, Misc.getNegativeHighlightColor(), RelicHelper.getName());

        actionPanel.addUIElement(actions).inTL(0f, 0f);
        panel.addComponent(actionPanel).inTL(x, y + height - ACTION_HEIGHT);
    }

    private void addTemplateDetail(TooltipMakerAPI detail, Entry entry) {
        Color hl = Misc.getHighlightColor();
        int level = ForgeTemplateItemPlugin.getForgeTemplateQualityLevel(entry.stack.getSpecialDataIfSpecial());

        detail.addPara("Quality level: %s -> %s", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getPositiveHighlightColor()},
                level + "", (level - 1) + "");
        detail.addPara("Charges: %s", PAD, hl, ForgeTemplateItemPlugin.getCharges(entry.stack.getSpecialDataIfSpecial()) + "");
        if (entry.stack.getSize() > 1) {
            detail.addPara("Refines all %s templates in this stack.", PAD, hl, (int) entry.stack.getSize() + "");
        }
    }

    private void addShipDetail(TooltipMakerAPI detail, Entry entry) {
        Color hl = Misc.getHighlightColor();
        Color pos = Misc.getPositiveHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();

        if (entry.member.getVariant().hasHullMod(HullMods.AUTOMATED)) {
            detail.addPara("Max CR: %s -> %s", 0f, new Color[]{neg, pos},
                    "-" + Math.round(Automated.MAX_CR_PENALTY * 100f) + "% automated ship penalty", "no penalty");
        }
        detail.addPara("Automated Ships skill: %s -> %s", PAD, new Color[]{hl, pos}, "counts toward its limit", "no longer counts");
        detail.addPara("Permanent.", PAD);
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        Color hl = Misc.getHighlightColor();

        if (tab == Tab.TEMPLATES) {
            tooltip.addPara("Lower quality levels mean fewer defects and D-mods. The minimum is %s.", 0f, hl, "0");
            tooltip.addPara("Cost: %s %s per fleet point and quality level.", PAD, hl, RELICS_PER_TEMPLATE_FP + "", RelicHelper.getName());
        } else {
            tooltip.addPara("Permanently removes the automated ship CR penalty and exempts the ship from the Automated Ships skill limit.", 0f);
            tooltip.addPara("Cost: %s %s per deployment point.", PAD, hl, RELICS_PER_SHIP_DP + "", RelicHelper.getName());
        }
    }

    private void addConfirmTooltip(TooltipMakerAPI actions, Entry entry) {
        final boolean templates = tab == Tab.TEMPLATES;

        actions.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                String effect = templates
                        ? "Lowers the quality level of this Forge Template by %s."
                        : "Permanently removes the automated ship penalty.";
                tooltip.addPara(effect, 0f, Misc.getHighlightColor(), "1");
                RelicHelper.addCostLine(tooltip, market, entry.cost, PAD);
                tooltip.addPara(templates ? REFINE_LORE : INTEGRATE_LORE, Misc.getGrayColor(), OPAD);
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
    }

    private void addFooter(float width, float height) {
        if (message == null) return;

        TooltipMakerAPI footer = panel.createUIElement(width, FOOTER_HEIGHT, false);
        footer.addPara(message, messageColor, 4f);
        panel.addUIElement(footer).inTL(0f, height - FOOTER_HEIGHT);
    }

    private ButtonAction action(CustomDialogCallback callback, Runnable runnable) {
        return new ButtonAction(this) {
            @Override
            public void execute() {
                runnable.run();
                recreatePanel(callback);
            }
        };
    }

    private void selectTab(Tab target) {
        if (tab == target) return;
        tab = target;
        selected = null;
        message = null;
        listScroller = null;
    }

    private void select(Object key) {
        selected = key;
        message = null;
    }

    private void apply(Entry entry) {
        if (tab == Tab.TEMPLATES) {
            SpecialItemData data = entry.stack.getSpecialDataIfSpecial();
            RelicHelper.take(market, entry.cost);
            ForgeTemplateItemPlugin.incrementForgeTemplateQualityLevel(data, -1);
            message = entry.name + " refined to quality level " + ForgeTemplateItemPlugin.getForgeTemplateQualityLevel(data) + ", used " + entry.cost + " " + RelicHelper.getName() + ".";
        } else {
            RelicHelper.take(market, entry.cost);
            integrate(entry.member);
            selected = null;
            message = entry.name + " integrated, the automated ship penalty no longer applies. Used " + entry.cost + " " + RelicHelper.getName() + ".";
        }

        messageColor = Misc.getPositiveHighlightColor();
    }

    private static void integrate(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant().clone();
        variant.setOriginalVariant(null);
        variant.setHullVariantId(Misc.genUID());
        variant.addTag(Tags.TAG_AUTOMATED_NO_PENALTY);
        member.setVariant(variant, false, true);
        member.updateStats();
    }

    private List<Entry> getTemplateEntries() {
        List<Entry> entries = new ArrayList<>();
        addTemplateEntries(entries, MiscIE.getIndustrialStorageCargo(market), market.getSubmarket(Ids.SHAREDSTORAGE));
        addTemplateEntries(entries, Misc.getStorageCargo(market), market.getSubmarket(Submarkets.SUBMARKET_STORAGE));
        return entries;
    }

    private void addTemplateEntries(List<Entry> entries, CargoAPI cargo, SubmarketAPI submarket) {
        if (cargo == null || submarket == null) return;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!ForgeTemplateItemPlugin.isForgeTemplate(data)) continue;

            ShipVariantAPI variant = ForgeTemplateItemPlugin.getVariant(data);
            if (variant == null) continue;

            int level = ForgeTemplateItemPlugin.getForgeTemplateQualityLevel(data);
            if (level <= 0) continue;

            int fp = variant.getHullSpec().getFleetPoints();
            Entry entry = new Entry(submarket.getSpecId() + "|" + data.getData());
            entry.stack = stack;
            entry.name = stack.getDisplayName();
            entry.sub = "Quality level " + level + ", " + fp + " FP, " + submarket.getNameOneLine();
            entry.cost = RELICS_PER_TEMPLATE_FP * fp * (int) stack.getSize();
            entry.display = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant.clone());
            entries.add(entry);
        }
    }

    private List<Entry> getShipEntries() {
        List<Entry> entries = new ArrayList<>();
        addShipEntries(entries, Global.getSector().getPlayerFleet().getFleetData(), "Fleet");

        SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (storage != null) addShipEntries(entries, storage.getCargo().getMothballedShips(), storage.getNameOneLine());
        return entries;
    }

    private void addShipEntries(List<Entry> entries, FleetDataAPI fleetData, String location) {
        if (fleetData == null) return;

        for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
            if (!Misc.isAutomated(member) || Automated.isAutomatedNoPenalty(member)) continue;

            int dp = Math.round(member.getHullSpec().getSuppliesToRecover());
            Entry entry = new Entry(member.getId());
            entry.member = member;
            entry.name = member.getShipName() + ", " + member.getHullSpec().getHullNameWithDashClass();
            entry.sub = location + ", " + dp + " DP";
            entry.cost = RELICS_PER_SHIP_DP * dp;
            entry.display = member;
            entries.add(entry);
        }
    }

    private static class Entry {
        private final Object key;
        private String name;
        private String sub;
        private int cost;
        private FleetMemberAPI display;
        private CargoStackAPI stack;
        private FleetMemberAPI member;

        Entry(Object key) {
            this.key = key;
        }
    }
}
