package indevo.industries.orbitalworks.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.ids.ItemIds;
import indevo.industries.orbitalworks.hullmods.RelicLattice;
import indevo.industries.orbitalworks.scripts.OrbitalWorksSlotManager;
import indevo.utils.helper.MiscIE;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class OrbitalWorksSlotPanelDelegate extends ButtonReportingDialogueDelegate {

    public static final int COST_PER_DP = 20;

    private static final float OPAD = 10f;
    private static final float PAD = 3f;
    private static final float HEADER_H = 64f;
    private static final float COLUMN_H = 20f;
    private static final float ROW_H = 28f;
    private static final float FOOTER_H = 60f;
    private static final float BUTTON_W = 150f;
    private static final float BUTTON_H = 25f;
    private static final String[] COLUMNS = {"Ship", "Hull", "DP", "S-mods", "After", "Cost", "Time"};
    private static final float[] COLUMN_W = {0.27f, 0.23f, 0.08f, 0.1f, 0.1f, 0.1f, 0.12f};

    private final MarketAPI market;
    private final boolean local;
    private final List<FleetMemberAPI> ships = new ArrayList<>();

    private FleetMemberAPI selected;
    private CustomPanelAPI basePanel;
    private CustomPanelAPI panel;
    private ScrollPanelAPI scroller;

    public OrbitalWorksSlotPanelDelegate(MarketAPI market) {
        this.market = market;

        MarketAPI current = MiscIE.getCurrentInteractionTargetMarket();
        local = current != null && current.getId().equals(market.getId());

        if (local) {
            for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (isPickable(member) && !member.isFlagship()) ships.add(member);
            }
        }

        CargoAPI storage = MiscIE.getStorageCargo(market);
        if (storage != null && storage.getMothballedShips() != null) {
            for (FleetMemberAPI member : storage.getMothballedShips().getMembersListCopy()) {
                if (isPickable(member)) ships.add(member);
            }
        }
    }

    public static float getWidth() {
        return Math.min(800f, Global.getSettings().getScreenWidth() - 100f);
    }

    public static float getHeight() {
        return Math.min(520f, Global.getSettings().getScreenHeight() - 200f);
    }

    public static int getCost(FleetMemberAPI member) {
        return COST_PER_DP * Math.max(1, Math.round(member.getHullSpec().getSuppliesToRecover()));
    }

    public static int getAvailableRelics(MarketAPI market) {
        CargoAPI storage = MiscIE.getStorageCargo(market);
        float inStorage = storage != null ? storage.getCommodityQuantity(ItemIds.RARE_PARTS) : 0f;
        return Math.round(inStorage + MiscIE.getPlayerCargo().getCommodityQuantity(ItemIds.RARE_PARTS));
    }

    private static boolean isPickable(FleetMemberAPI member) {
        return !member.isFighterWing()
                && !member.isStation()
                && !member.getVariant().hasHullMod(RelicLattice.ID);
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
        float scrollY = scroller != null ? scroller.getYOffset() : 0f;
        if (panel != null) basePanel.removeComponent(panel);

        float w = basePanel.getPosition().getWidth();
        float h = basePanel.getPosition().getHeight();
        panel = Global.getSettings().createCustom(w, h, null);

        TooltipMakerAPI header = panel.createUIElement(w, HEADER_H, false);
        header.addSectionHeading(OrbitalWorksSlotOptionProvider.NAME + " - " + market.getName(), Alignment.MID, 0f);
        header.addPara("The ship stays at the colony for %s. When the work is done it is placed in local storage.", OPAD, Misc.getGrayColor(), Misc.getHighlightColor(),
                OrbitalWorksSlotManager.DAYS_REQUIRED + " days");
        if (!local) header.addPara("Only ships in local storage are listed. Dock here to pick ships from your fleet.", Misc.getGrayColor(), PAD);
        panel.addUIElement(header).inTL(0f, 0f);

        float listW = w - 20f;
        panel.addComponent(createColumnHeadings(listW)).inTL(0f, HEADER_H);

        float listH = h - HEADER_H - COLUMN_H - FOOTER_H - PAD;
        TooltipMakerAPI list = panel.createUIElement(w, listH, true);
        for (FleetMemberAPI member : ships) list.addCustom(createRow(member, listW, callback), 2f);
        if (ships.isEmpty()) list.addPara("No ships here can take the work.", Misc.getGrayColor(), OPAD);
        panel.addUIElement(list).inTL(0f, HEADER_H + COLUMN_H + PAD);

        addFooter(w, h, callback);
        basePanel.addComponent(panel);

        scroller = list.getExternalScroller();
        if (scroller != null) scroller.setYOffset(scrollY);
    }

    private CustomPanelAPI createColumnHeadings(float w) {
        CustomPanelAPI row = panel.createCustomPanel(w, COLUMN_H, null);
        float x = 6f;

        for (int i = 0; i < COLUMNS.length; i++) {
            float colW = w * COLUMN_W[i];
            TooltipMakerAPI cell = row.createUIElement(colW, COLUMN_H, false);
            cell.addPara(COLUMNS[i], Misc.getGrayColor(), 0f);
            row.addUIElement(cell).inTL(x, 2f);
            x += colW;
        }

        return row;
    }

    private CustomPanelAPI createRow(final FleetMemberAPI member, float w, CustomDialogCallback callback) {
        Color text = Misc.getTextColor();
        Color pos = Misc.getPositiveHighlightColor();
        int cost = getCost(member);
        int used = member.getVariant().getSMods().size();
        int cap = Misc.getMaxPermanentMods(member, Global.getSector().getPlayerStats());
        boolean inStorage = !Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().contains(member);

        CustomPanelAPI row = panel.createCustomPanel(w, ROW_H, new ButtonReportingCustomPanel(this));

        TooltipMakerAPI background = row.createUIElement(w, ROW_H, false);
        ButtonAPI button = background.addAreaCheckbox("", new ButtonAction(this) {
            @Override
            public void execute() {
                selected = member == selected ? null : member;
                recreatePanel(callback);
            }
        }, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), w, ROW_H, 0f);
        button.setChecked(member == selected);
        row.addUIElement(background).inTL(-OPAD, 0f);

        String[] values = {
                member.getShipName() + (inStorage ? " (storage)" : ""),
                member.getHullSpec().getHullNameWithDashClass(),
                Math.round(member.getHullSpec().getSuppliesToRecover()) + "",
                used + " / " + cap,
                used + " / " + (cap + RelicLattice.EXTRA_SMODS),
                cost + "",
                OrbitalWorksSlotManager.DAYS_REQUIRED + " days"
        };
        Color[] colors = {text, text, text, text, pos, cost <= getAvailableRelics(market) ? text : Misc.getNegativeHighlightColor(), text};

        float x = 6f;
        for (int i = 0; i < values.length; i++) {
            float colW = w * COLUMN_W[i];
            TooltipMakerAPI cell = row.createUIElement(colW - 4f, ROW_H, false);
            cell.addPara(values[i], colors[i], 0f);
            row.addUIElement(cell).inTL(x, 6f);
            x += colW;
        }

        return row;
    }

    private void addFooter(float w, float h, final CustomDialogCallback callback) {
        TooltipMakerAPI footer = panel.createUIElement(w, FOOTER_H, false);
        int available = getAvailableRelics(market);
        String relicName = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();
        boolean canAfford = false;

        if (selected == null) {
            footer.addPara("Pick a ship. Cost is %s Relic Components per deployment point.", 0f, Misc.getGrayColor(), Misc.getHighlightColor(), COST_PER_DP + "");
        } else {
            int cost = getCost(selected);
            canAfford = cost <= available;
            footer.addPara("Cost: %s (Available: " + available + ")", 0f, canAfford ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), cost + " " + relicName);
        }

        ButtonAPI confirm = footer.addButton("Confirm", new ButtonAction(this) {
            @Override
            public void execute() {
                if (selected == null || getCost(selected) > getAvailableRelics(market)) return;
                if (OrbitalWorksSlotManager.getInstanceOrRegister().getJob(market) != null) return;
                startWork(selected);
                callback.dismissCustomDialog(1);
            }
        }, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, BUTTON_W, BUTTON_H, OPAD);
        confirm.setEnabled(selected != null && canAfford);

        CustomPanelAPI holder = panel.createCustomPanel(w, FOOTER_H, new ButtonReportingCustomPanel(this));
        holder.addUIElement(footer).inTL(0f, 0f);
        panel.addComponent(holder).inTL(0f, h - FOOTER_H);
    }

    private void startWork(FleetMemberAPI member) {
        payRelics(getCost(member));

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet.getFleetData().getMembersListCopy().contains(member)) {
            PersonAPI captain = member.getCaptain();
            if (captain != null && !captain.isDefault() && !captain.isPlayer()) member.setCaptain(null);
            fleet.getFleetData().removeFleetMember(member);
        } else {
            MiscIE.getStorageCargo(market).getMothballedShips().removeFleetMember(member);
        }

        OrbitalWorksSlotManager.getInstanceOrRegister().start(market, member);
    }

    private void payRelics(float amount) {
        CargoAPI storage = MiscIE.getStorageCargo(market);
        float remaining = amount;

        if (storage != null) {
            float take = Math.min(remaining, storage.getCommodityQuantity(ItemIds.RARE_PARTS));
            storage.removeCommodity(ItemIds.RARE_PARTS, take);
            remaining -= take;
        }

        if (remaining > 0) MiscIE.getPlayerCargo().removeCommodity(ItemIds.RARE_PARTS, remaining);
    }
}
