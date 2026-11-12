package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetAssignmentAIs.DetachmentAI;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.industries.academy.plugins.AcademyPanelDelegate;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// one slot of the detachment panel: heading fixed on top, the rest in a scroll column
class DetachmentColumn {

    static final float LIST_Y = 26f;
    static final float ROW_H = 24f;
    static final float SHIP = 48f;
    static final float SHIP_GAP = 4f;
    static final float SHIP_TEXT_W = 90f;
    static final float TAKE_TEXT_W = 260f;
    static final float NOTE_H = 18f;
    static final float ICON_W = 24f;
    static final float ICON_H = 16f;
    static final float LABEL_X = 32f;
    static final float BAR_X = 118f;
    static final float BAR_W = 190f;
    static final float BAR_H = 20f;
    static final float BAR_ROW = 24f;
    static final float VALUE_X = 316f;
    static final float MODE_H = 28f;
    static final float MODE_ROW = 32f;
    static final float GAP = 6f;
    static final float ORDER_H = 26f;
    static final float CHECK_W = 110f;
    static final float CHECK_ROW = 20f;
    static final float MARK_X = 22f;
    static final float LOAD_H = 22f;
    static final float LOAD_MIN_W = 82f;
    static final float LOAD_MAX_W = 84f;
    static final float LOAD_W = LOAD_MIN_W + LOAD_MAX_W + 4f;
    static final float ACTION_H = 30f;
    static final float OFFICER_ROW = 36f;
    static final float PORTRAIT = 32f;
    static final float SALARY_W = 150f;
    static final float LAY_COURSE_W = 110f;
    static final float MOVE_CARGO_W = 130f;
    static final float SET_TARGET_W = 130f;
    static final float PICK_W = 100f;
    static final float SEND_W = 64f;
    static final float SUPPLY_DAYS_FULL = 90f;

    private final DetachmentPanel panel;
    private final int slot;
    private final String notice;

    private final CampaignFleetAPI player;
    private final LoadoutMemory.Loadout loadout;
    private final CampaignFleetAPI fleet;
    private final boolean live;
    private final boolean met;
    private final boolean dormant;
    private final boolean stranded;
    private final boolean take;
    private final List<FleetMemberAPI> members;
    private final CargoAPI cargo;
    private final FleetStatus status;
    private final MarketAPI target;

    private UiScroll.Column column;
    private float w;
    private boolean canDeploy = false;

    private record Ship(FleetMemberAPI member, ShipVariantAPI missing) {
        String name() {
            if (member != null) return member.getShipName() + ", " + member.getHullSpec().getHullNameWithDashClass();
            return missing.getHullSpec().getHullNameWithDashClass() + ", not in your fleet";
        }
    }

    private record Check(boolean ok, String text, String[] parts, Color[] colors, Color color) {
        static Check of(boolean ok, String text) {
            return new Check(ok, text, new String[0], new Color[0], Ui.text());
        }
    }

    DetachmentColumn(DetachmentPanel panel, int slot, String notice) {
        this.panel = panel;
        this.slot = slot;
        this.notice = notice;

        player = Global.getSector().getPlayerFleet();
        loadout = LoadoutMemory.getLoadout(slot);
        fleet = DetachmentMemory.getDetachment(slot);
        live = fleet != null;
        met = live && panel.getView().met == slot;
        dormant = Behaviour.isDormant(fleet);
        stranded = Behaviour.isStranded(fleet);
        take = !live && loadout.takeMode;

        members = live ? fleet.getFleetData().getMembersListCopy() : FleetUtils.getDetachmentMembers(loadout, player);
        cargo = live ? fleet.getCargo() : FleetUtils.getDetachmentCargo(loadout, player);
        status = live ? FleetStatus.getFleetStatus(fleet) : FleetStatus.getFleetStatus(loadout.id, cargo, members);
        target = loadout.transportTargetMarket != null ? Global.getSector().getEconomy().getMarket(loadout.transportTargetMarket) : null;
    }

    void build(CustomPanelAPI p, float x, float y, float colW, float h) {
        // the action button stays pinned at the bottom, the column above scrolls
        w = colW - UiScroll.GUTTER;
        float actionH = live ? UiButtons.guardHeight(w, ACTION_H, getActionReason()) : ACTION_H;
        column = UiScroll.list(p, x, y + LIST_Y, colW, h - LIST_Y - actionH - 10f, panel.getView().scroll, DetachmentView.listId(slot));
        addHeading(p, x, y);

        addTopRow();
        addShips();
        boolean note = addNote();
        addLogistics(note ? GAP : 10f);
        addModes();
        addTarget();
        addOrders();

        if (live) addOfficers();
        else addDeployment();

        column.end();

        CustomPanelAPI action = Ui.panel(p, x, y + h - actionH, w, actionH);
        if (live) addAction(action);
        else UiButtons.button(action, 0f, 0f, w, ACTION_H, "Deploy", canDeploy ? UiButtons.Kind.GREEN : UiButtons.Kind.OFF, canDeploy ? () -> panel.deploy(slot) : null);
    }

    private void addHeading(CustomPanelAPI p, float x, float y) {
        String title = "Detachment " + slot;
        Ui.heading(p, x, y, w, title, (String) null);

        float nx = x + 8f + Math.min(Ui.width(title, Ui.ORB12) + 2f, w - 16f) + 16f;
        float nw = x + w - 8f - nx;
        if (nw < 20f) return;

        if (!live) {
            Ui.textIn(p, nx, y, nw, Ui.HEADING_H, "Not deployed", Ui.ORB12, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
            return;
        }

        FleetBehaviour effective = Behaviour.getFleetBehaviour(fleet, false);
        String override = FleetStatus.getStateName(effective);
        String state;
        Color color;
        if (override != null) {
            state = Misc.ucFirst(override);
            color = effective == FleetBehaviour.DORMANT || effective == FleetBehaviour.STRANDED ? Ui.neg() : Ui.hl();
        } else {
            FleetBehaviour base = Behaviour.getFleetBehaviour(fleet, true);
            state = getBaseStateName(base);
            Color mode = Behaviour.getColourForBehaviour(base);
            color = mode != null ? mode : Ui.base();
        }

        String location = getLocationName(fleet.getContainingLocation());
        if (met) location += ", in contact";
        Ui.textIn(p, nx, y, nw, Ui.HEADING_H, state + ", " + location, Ui.ORB12, Ui.base(), Ui.RIGHT, new String[]{state}, new Color[]{color});
    }

    private static String getBaseStateName(FleetBehaviour base) {
        if (base == null) return "Active";
        return switch (base) {
            case STAY -> "Holding";
            case FOLLOW -> "Following";
            case HIDE -> "Hiding";
            case ESCORT -> "Escorting";
            case INTERCEPT -> "Intercepting";
            case TRANSPORT -> "Transporting";
            default -> "Active";
        };
    }

    private static String getLocationName(LocationAPI location) {
        if (location == null) return "Unknown location";
        if (location instanceof StarSystemAPI system) return system.getBaseName();
        return location.getName();
    }

    private void addTopRow() {
        CustomPanelAPI b = column.block(ROW_H, 0f);

        if (live) {
            float bw = met ? MOVE_CARGO_W : LAY_COURSE_W;
            boolean targeted = player.getInteractionTarget() != null && fleet.getId().equals(player.getInteractionTarget().getId());

            ButtonAPI button;
            if (met) button = UiButtons.button(b, w - bw, 0f, bw, ROW_H, "Move cargo...", UiButtons.Kind.STD, () -> panel.showCargoExchange(slot));
            else button = UiButtons.button(b, w - bw, 0f, bw, ROW_H, "Lay course", UiButtons.Kind.STD, targeted ? null : () -> panel.layCourse(slot));
            Ui.tooltip(b, button, null, met ? "Move cargo between your fleet and the detachment." : "Set a course to the detachment.");

            String deployed = getDeployedText();
            String distance = met ? null : getDistanceText(fleet);
            String text = met ? (deployed.isEmpty() ? "In contact." : deployed) : (deployed.isEmpty() ? "" : deployed + " ") + distance + " from your fleet.";
            Ui.runs(b, 0f, 4f, w - bw - 8f, Ui.INS15, Ui.text(), Ui.LEFT, text, new String[]{distance}, new Color[]{Ui.hl()});
            return;
        }

        ButtonAPI send = UiButtons.area(b, 0f, 0f, SEND_W, ROW_H, "Send", !take, true, null, Ui.CENTER, () -> panel.setTakeMode(slot, false));
        Ui.tooltip(b, send, null, "Select ships and cargo for the detachment.");
        ButtonAPI keep = UiButtons.area(b, SEND_W + 2f, 0f, SEND_W, ROW_H, "Take", take, true, null, Ui.CENTER, () -> panel.setTakeMode(slot, true));
        Ui.tooltip(b, keep, null, "Select ships and cargo to keep; the rest form the detachment. Your flagship stays with you.");

        float hintX = 2f * SEND_W + 12f;
        Ui.text(b, hintX, 4f, w - 2f * PICK_W - 6f - 8f - hintX, take ? "Picked ships stay" : "Picked ships leave", Ui.gray());

        UiButtons.button(b, w - 2f * PICK_W - 6f, 0f, PICK_W, ROW_H, "Ships...", UiButtons.Kind.STD, CutStyle.TL_BR, () -> panel.showShipPicker(slot));
        UiButtons.button(b, w - PICK_W, 0f, PICK_W, ROW_H, "Cargo...", UiButtons.Kind.STD, CutStyle.TL_BR, members.isEmpty() ? null : () -> panel.showCargoPicker(slot));
    }

    private String getDeployedText() {
        Object timestamp = fleet.getMemoryWithoutUpdate().get(FleetUtils.SPAWN_TIMESTAMP_KEY);
        if (!(timestamp instanceof Number n)) return "";

        int days = (int) Global.getSector().getClock().getElapsedDaysSince(n.longValue());
        if (days <= 0) return "Deployed today.";
        return "Deployed " + days + (days == 1 ? " day" : " days") + " ago.";
    }

    private String getDistanceText(SectorEntityToken from) {
        if (from.getContainingLocation() == player.getContainingLocation()) return Ui.num(Math.round(Misc.getDistance(from, player))) + " units";
        return Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(from, player)) + " light years";
    }

    private void addShips() {
        CustomPanelAPI b = column.block(SHIP, 8f);
        float textW = take ? TAKE_TEXT_W : SHIP_TEXT_W;
        float shipsW = w - textW - 8f;

        List<Ship> ships = new ArrayList<>();
        int count;
        if (live) {
            for (FleetMemberAPI m : members) ships.add(new Ship(m, null));
            count = members.size();
        } else if (take) {
            for (FleetMemberAPI m : FleetUtils.getTakenMembers(loadout, player)) ships.add(new Ship(m, null));
            count = members.size();
        } else {
            ships = getLoadoutShips();
            count = members.size();
        }

        if (ships.isEmpty()) Ui.text(b, 0f, 15f, shipsW, "No ships picked.", Ui.gray());
        else UiImages.iconrow(b, 0f, 0f, shipsW, ships, SHIP, SHIP, SHIP_GAP, 1, DetachmentColumn::shipCell, Ship::name);

        if (take) {
            String stay = Ui.num(count);
            Ui.runs(b, w - textW, 6f, textW, Ui.INS15, Ui.text(), Ui.RIGHT, "You take these ships, " + stay + " stay behind.", new String[]{stay}, new Color[]{Ui.hl()});
        } else Ui.text(b, w - textW, 6f, textW, Ui.num(count) + (count == 1 ? " ship" : " ships"), Ui.text(), Ui.RIGHT);

        if (!members.isEmpty()) {
            String burn = Ui.num(status.burn);
            Ui.runs(b, w - textW, 25f, textW, Ui.INS15, Ui.gray(), Ui.RIGHT, "Burn " + burn, new String[]{burn}, new Color[]{Ui.hl()});
        }
    }

    // loadout order, ships no longer in the fleet stay in the row dimmed
    private List<Ship> getLoadoutShips() {
        List<Ship> ships = new ArrayList<>();
        List<FleetMemberAPI> used = new ArrayList<>();

        for (ShipVariantAPI variant : loadout.shipVariantList) {
            FleetMemberAPI match = null;
            for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
                if (m.getVariant().getHullVariantId().equals(variant.getHullVariantId()) && !used.contains(m) && !FleetUtils.staysWithPlayer(m)) {
                    match = m;
                    break;
                }
            }

            if (match != null) used.add(match);
            ships.add(new Ship(match, match == null ? variant : null));
        }

        return ships;
    }

    private static void shipCell(CustomPanelAPI p, float x, float y, float w, float h, Ship ship) {
        if (ship.member() != null) UiImages.shipIcon(p, x, y, w, ship.member());
        else UiImages.hull(p, x, y, w, ship.missing().getHullSpec(), true, true);
    }

    private boolean addNote() {
        String text = null;
        Color color = Ui.hl();

        if (notice != null) {
            text = notice;
            color = Ui.neg();
        } else if (!live && !take && !loadout.shipVariantList.isEmpty()) {
            List<Ship> missing = new ArrayList<>();
            for (Ship ship : getLoadoutShips()) if (ship.member() == null) missing.add(ship);

            if (missing.size() == loadout.shipVariantList.size()) {
                text = "None of the ships specified in the loadout are present in the main fleet.";
                color = Ui.neg();
            } else if (missing.size() == 1) {
                text = missing.get(0).missing().getHullSpec().getHullNameWithDashClass() + " is not in your fleet. Unavailable ships will be left behind.";
            } else if (!missing.isEmpty()) {
                text = missing.size() + " ships are not in your fleet. Unavailable ships will be left behind.";
            }
        }

        if (text == null) return false;
        Ui.text(column.block(NOTE_H, GAP), 0f, 0f, w, text, color);
        return true;
    }

    private void addLogistics(float gap) {
        CustomPanelAPI b = column.block(3f * BAR_ROW + BAR_H, gap);

        int free = status.totalCargoCap - status.currentCargo;
        if (free >= 0) logRow(b, 0f, "icon_logistics_cargo", "Cargo", status.currentCargo, status.totalCargoCap, "progressBarCargoColor",
                Ui.num(free) + " free", new String[0], new Color[0]);
        else logRow(b, 0f, "icon_logistics_cargo", "Cargo", status.currentCargo, status.totalCargoCap, "progressBarCargoColor",
                Ui.num(-free) + " over capacity", new String[]{Ui.num(-free)}, new Color[]{Ui.neg()});

        if (status.requiredFuelPerLY > 0) {
            String range = Ui.num(status.currentFuel / status.requiredFuelPerLY);
            logRow(b, BAR_ROW, "icon_logistics_fuel", "Fuel", status.currentFuel, status.totalFuelCap, "progressBarFuelColor",
                    "Range " + range + " light years", new String[]{range}, new Color[]{Ui.hl()});
        } else logRow(b, BAR_ROW, "icon_logistics_fuel", "Fuel", status.currentFuel, status.totalFuelCap, "progressBarFuelColor",
                "No fuel use", new String[0], new Color[0]);

        String needed = Ui.num(status.requiredCrew);
        logRow(b, 2f * BAR_ROW, "icon_logistics_personnel", "Crew", status.currentCrewAndMarines, status.totalCrewCap, "progressBarCrewColor",
                needed + " needed", new String[]{needed}, new Color[]{status.currentCrew < status.requiredCrew ? Ui.neg() : Ui.hl()});

        addSupplies(b, 3f * BAR_ROW);
    }

    private void logRow(CustomPanelAPI b, float y, String icon, String label, int amount, int capacity, String colorKey, String right, String[] parts, Color[] colors) {
        float fraction = capacity > 0 ? amount / (float) capacity : 0f;
        Color fill = Global.getSettings().getColor(amount > capacity ? "progressBarOverflowColor" : colorKey);
        String barLabel = Ui.num(amount) + " / " + Ui.num(capacity);
        logRow(b, y, icon, label, fraction, fill, barLabel, right, parts, colors);
    }

    private CustomPanelAPI logRow(CustomPanelAPI b, float y, String icon, String label, float fraction, Color fill, String barLabel, String right, String[] parts, Color[] colors) {
        UiImages.img(b, 0f, y + (BAR_H - ICON_H) / 2f, ICON_W, ICON_H, Global.getSettings().getSpriteName("fleetScreen", icon));
        Ui.textIn(b, LABEL_X, y, BAR_X - LABEL_X - 6f, BAR_H, label, Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        CustomPanelAPI bar = UiData.bar(b, BAR_X, y, BAR_W, BAR_H, fraction, fill, barLabel);
        Ui.textIn(b, VALUE_X, y, w - VALUE_X, BAR_H, right, Ui.INS15, Ui.gray(), Ui.RIGHT, parts, colors);
        return bar;
    }

    private void addSupplies(CustomPanelAPI b, float y) {
        float perDay = status.suppliesPerDay;
        int need = status.suppliesToRecover + status.requiredSuppliesPerMonth;
        boolean shortOnSupplies = status.currentSupplies < need;

        int days = perDay > 0f ? (int) Math.max(0f, (status.currentSupplies - status.suppliesToRecover) / perDay) : 0;
        float fraction = perDay > 0f ? Math.min(1f, days / SUPPLY_DAYS_FULL) : status.currentSupplies > 0 ? 1f : 0f;
        Color fill = shortOnSupplies ? Global.getSettings().getColor("progressBarDangerColor") : Ui.barBlue();
        String label = perDay > 0f ? Ui.num(days) + (days == 1 ? " day" : " days") : "No active ships";

        String amount = Ui.num(status.currentSupplies);
        String right = amount + " at " + Misc.getRoundedValueMaxOneAfterDecimal(perDay) + " per day";
        CustomPanelAPI bar = logRow(b, y, "icon_logistics_supplies", "Supplies", fraction, fill, label, right,
                new String[]{amount}, new Color[]{shortOnSupplies ? Ui.neg() : Ui.hl()});

        if (status.suppliesToRecover > 1) Ui.tooltip(b, bar, null, "Estimated operating time: " + status.operatingTimeString,
                "Supplies needed to finish repairs: " + Ui.num(status.suppliesToRecover) + " units");
        else Ui.tooltip(b, bar, null, "Estimated operating time: " + status.operatingTimeString);
    }

    private void addModes() {
        heading("Mode", 10f);

        FleetBehaviour current = live ? Behaviour.getFleetBehaviour(fleet, true) : loadout.behaviour;
        int count = Behaviour.behaviourIndexMap.size();
        int rows = (count + 2) / 3;
        float bw = Math.round((w - 12f) / 3f);

        CustomPanelAPI b = column.block((rows - 1) * MODE_ROW + MODE_H, GAP);
        for (int i = 1; i <= count; i++) {
            FleetBehaviour mode = Behaviour.getBehaviourForIndex(i);
            boolean enabled = !dormant && !stranded && (!live || Behaviour.canSwitchLiveTo(mode));
            int index = i - 1;
            float x = index % 3 == 2 ? w - bw : (index % 3) * (bw + 6f);

            UiButtons.area(b, x, (index / 3) * MODE_ROW, bw, MODE_H, Misc.ucFirst(mode.toString().toLowerCase()), Behaviour.behaviourEquals(mode, current), enabled,
                    Behaviour.getColourForBehaviour(mode), Ui.CENTER, () -> panel.setMode(slot, mode));
        }

        String description = Behaviour.behaviourTooltipMap.get(Behaviour.getIndexForBehaviour(current));
        float descH = Ui.LINE_H;
        for (String text : Behaviour.behaviourTooltipMap.values()) descH = Math.max(descH, Ui.wrapHeight(text, Ui.INS15, w));

        CustomPanelAPI d = column.block(descH, 8f);
        if (description != null) Ui.wrap(d, 0f, 0f, w, description, Ui.gray());
    }

    private void addTarget() {
        CustomPanelAPI b = column.block(ROW_H, 2f);
        FleetBehaviour base = live ? Behaviour.getFleetBehaviour(fleet, true) : loadout.behaviour;
        boolean transport = Behaviour.behaviourEquals(base, FleetBehaviour.TRANSPORT);

        if (!live) {
            if (!transport) return;

            ButtonAPI button = UiButtons.button(b, w - SET_TARGET_W, 0f, SET_TARGET_W, ROW_H, "Set target...", UiButtons.Kind.STD, () -> panel.showTransportTargetPicker(slot));
            Ui.tooltip(b, button, null, "Pick the planet to store the ships and cargo at.");

            float tw = w - SET_TARGET_W - 8f;
            if (target != null) addTargetLine(b, tw, player);
            else Ui.text(b, 0f, 4f, tw, "No target planet set.", Ui.gray());
        } else if (transport && target != null) addTargetLine(b, w, fleet);
        else Ui.text(b, 0f, 4f, w, "Transport can not be set after deployment.", Ui.gray());
    }

    private void addTargetLine(CustomPanelAPI b, float tw, SectorEntityToken from) {
        UiImages.planet(b, 0f, 0f, ROW_H, target);

        String system = target.getStarSystem() != null ? " · " + target.getStarSystem().getBaseName() : "";
        String distance = Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(from, target.getPrimaryEntity())) + " light years";
        String rest = system + " · " + distance;
        Ui.runs(b, LABEL_X, 4f, tw - LABEL_X, Ui.INS15, Ui.text(), Ui.LEFT, target.getName() + rest, new String[]{rest}, new Color[]{Ui.gray()});
    }

    private void addOrders() {
        heading("Orders", 8f);

        boolean returning = Behaviour.isReturning(fleet);
        boolean free = live && !dormant && !stranded && !returning;
        boolean inSystem = live && fleet.getContainingLocation() instanceof StarSystemAPI && !fleet.isInHyperspace();
        float bw = Math.round((w - 12f) / 3f);

        CustomPanelAPI b = column.block(ORDER_H, GAP);
        boolean goTo = free && inSystem;
        order(b, 0f, bw, "Go to...", UiButtons.Kind.STD, CutStyle.TL_BR, goTo ? () -> panel.showGoToPicker(slot) : null,
                "Move to a location in this system and hold position.");

        if (live && Behaviour.isAttacking(fleet)) order(b, bw + 6f, bw, "Stop attack", UiButtons.Kind.HOT, CutStyle.TL_BR,
                () -> panel.stopOrder(slot, FleetBehaviour.ATTACK_TARGET), "Resume the current mode.");
        else {
            boolean attack = free && !DetachmentPanel.getAttackTargets(fleet).isEmpty();
            order(b, bw + 6f, bw, "Attack...", UiButtons.Kind.STD, CutStyle.TL_BR, attack ? () -> panel.showAttackPicker(slot) : null,
                    "Attack a visible hostile fleet in this system, then resume the current mode.");
        }

        if (live && Behaviour.isOnTradeRun(fleet)) order(b, w - bw, bw, "Stop run", UiButtons.Kind.HOT, CutStyle.TL_BR,
                () -> panel.stopOrder(slot, FleetBehaviour.TRADE_RUN), "Resume the current mode.");
        else order(b, w - bw, bw, "Trade run", UiButtons.Kind.STD, CutStyle.TL_BR, free ? () -> panel.startTradeRun(slot) : null,
                "Meet the main force to exchange cargo, then resume the current mode.");

        addToggles(bw);
    }

    private void order(CustomPanelAPI b, float x, float bw, String label, UiButtons.Kind kind, CutStyle cut, Runnable action, String tooltip) {
        ButtonAPI button = UiButtons.button(b, x, 0f, bw, ORDER_H, label, action == null ? UiButtons.Kind.OFF : kind, cut, action);
        Ui.tooltip(b, button, null, tooltip);
    }

    private void addToggles(float bw) {
        CustomPanelAPI b = column.block(CHECK_ROW, 8f);
        boolean awake = !dormant && !stranded;

        ButtonAPI recall = UiButtons.check(b, 0f, 0f, CHECK_W, "Auto-recall", loadout.autoRecall, awake, () -> panel.toggleAutoRecall(slot));
        Ui.tooltip(b, recall, null, "Return to you when supplies drop below " + (int) DetachmentAI.AUTO_RECALL_DAYS + " days.");

        String why = getJoinFightsBlocker();
        float jx = bw + 6f;
        ButtonAPI join = UiButtons.check(b, jx, 0f, CHECK_W, "Join fights", why == null && loadout.joinFights, why == null, () -> panel.toggleJoinFights(slot));
        Ui.tooltip(b, join, null, "Join nearby battles involving the main force. Unavailable while hiding, transporting or travelling on orders.");

        float rx = jx + CHECK_W + 6f;
        if (why != null && !why.isEmpty()) Ui.textIn(b, rx, 0f, w - rx, CHECK_ROW, why, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
    }

    // null while allowed, empty when it is off without a short reason
    private String getJoinFightsBlocker() {
        if (dormant || stranded) return "";
        if (live && (Behaviour.isReturning(fleet) || Behaviour.isAttacking(fleet) || Behaviour.isOnTradeRun(fleet))) return "not while travelling on orders";

        FleetBehaviour base = live ? Behaviour.getFleetBehaviour(fleet, true) : loadout.behaviour;
        if (Behaviour.behaviourEquals(base, FleetBehaviour.HIDE)) return "not while hiding";
        if (Behaviour.behaviourEquals(base, FleetBehaviour.TRANSPORT)) return "not while transporting";
        return null;
    }

    private void addDeployment() {
        heading("Deployment", 10f);

        boolean ready = !members.isEmpty();
        boolean shipsOk = ready && members.size() < player.getFleetData().getNumMembers();
        boolean cargoPicked = !loadout.targetCargo.isEmpty();
        boolean over = status.isOverCapacity();
        boolean cargoOk = cargoPicked && !cargo.isEmpty() && !over;
        int need = status.suppliesToRecover + status.requiredSuppliesPerMonth;
        boolean suppliesOk = status.currentSupplies >= need;

        String cargoText;
        if (!cargoPicked) cargoText = take ? "Pick the cargo you take with you" : "Pick cargo for the detachment";
        else if (over) cargoText = "Cargo, fuel and crew within capacity: over " + Misc.getAndJoined(status.getOverCapacityList());
        else cargoText = "Cargo, fuel and crew within capacity";

        String have = Ui.num(status.currentSupplies);
        String needed = Ui.num(need);
        List<Check> checks = new ArrayList<>();
        checks.add(Check.of(shipsOk, take ? "At least one ship stays behind as the detachment" : "At least one ship, and one stays with you"));
        checks.add(Check.of(cargoOk, cargoText));
        checks.add(new Check(suppliesOk, "Supplies for repairs and one month: " + have + " of " + needed, new String[]{have, needed},
                new Color[]{suppliesOk ? Ui.hl() : Ui.neg(), Ui.hl()}, Ui.text()));

        boolean transport = Behaviour.behaviourEquals(loadout.behaviour, FleetBehaviour.TRANSPORT);
        boolean fuelOk;
        if (transport && target == null) {
            fuelOk = false;
            checks.add(Check.of(false, "Target planet for Transport"));
        } else if (transport) {
            float required = status.requiredFuelPerLY * Misc.getDistanceLY(player, target.getPrimaryEntity());
            fuelOk = status.currentFuel > required;
            String fuel = Ui.num((float) Math.floor(required) + 1f);
            checks.add(new Check(fuelOk, "Fuel to reach " + target.getName() + ": " + fuel + " needed", new String[]{fuel},
                    new Color[]{fuelOk ? Ui.hl() : Ui.neg()}, Ui.text()));
        } else {
            fuelOk = true;
            boolean any = status.currentFuel >= 1;
            checks.add(any ? Check.of(true, "Fuel to leave this location")
                    : new Check(false, "Requires fuel to leave this location.", new String[0], new Color[0], Ui.hl()));
        }

        // the load buttons sit beside the supplies line, or on their own row when the line needs the width
        float inlineW = w - MARK_X - LOAD_W - 8f;
        boolean inline = Ui.width(checks.get(2).text(), Ui.INS15) + 2f <= inlineW;
        CustomPanelAPI b = column.block(checks.size() * CHECK_ROW + (inline ? 0f : LOAD_H + 4f), GAP);
        for (int i = 0; i < checks.size(); i++) {
            Check check = checks.get(i);
            float y = i * CHECK_ROW;
            float tw = i == 2 && inline ? inlineW : w - MARK_X;
            UiData.mark(b, 2f, y + 3f, check.ok());
            Ui.runs(b, MARK_X, y, tw, Ui.INS15, check.color(), Ui.LEFT, check.text(), check.parts(), check.colors());
        }

        float loadY = inline ? 2f * CHECK_ROW - 2f : checks.size() * CHECK_ROW + 2f;
        ButtonAPI min = UiButtons.button(b, w - LOAD_W, loadY, LOAD_MIN_W, LOAD_H, "Load min", UiButtons.Kind.STD, CutStyle.TL_BR,
                ready ? () -> panel.load(slot, DetachmentCargoLoad.Mode.MIN) : null);
        Ui.tooltip(b, min, null, "Sets the detachment's supplies to repairs plus one month and its crew to the minimum, moving out marines that take the room. Its fuel tanks are filled, up to half of all fuel. Anything your ships can not hold goes to the detachment if it has room.");
        ButtonAPI max = UiButtons.button(b, w - LOAD_MAX_W, loadY, LOAD_MAX_W, LOAD_H, "Load max", UiButtons.Kind.STD, CutStyle.TL_BR,
                ready ? () -> panel.load(slot, DetachmentCargoLoad.Mode.MAX) : null);
        Ui.tooltip(b, max, null, "Fills the detachment's supplies and crew up to its capacity. You keep repairs plus one month of supplies and your minimum crew. Its fuel tanks are filled, up to half of all fuel. Anything your ships can not hold goes to the detachment if it has room.");

        canDeploy = shipsOk && cargoOk && suppliesOk && fuelOk;
    }

    private void addOfficers() {
        heading("Officers", 10f);

        List<FleetMemberAPI> led = new ArrayList<>();
        for (FleetMemberAPI m : members) {
            PersonAPI officer = m.getCaptain();
            if (officer != null && !officer.isDefault() && !officer.isAICore() && !officer.isPlayer()) led.add(m);
        }

        CustomPanelAPI b = column.block(Math.max(1, led.size()) * OFFICER_ROW, GAP);
        if (led.isEmpty()) Ui.text(b, 0f, 2f, w, "No officers.", Ui.gray());

        float tw = w - 40f - SALARY_W - 8f;
        for (int i = 0; i < led.size(); i++) {
            FleetMemberAPI m = led.get(i);
            PersonAPI officer = m.getCaptain();
            String personality = officer.getPersonalityAPI().getId();
            String personalityName = AcademyPanelDelegate.getPersonalityName(personality);
            float y = i * OFFICER_ROW;

            UiImages.portrait(b, 0f, y, PORTRAIT, officer);
            Ui.text(b, 40f, y, tw, officer.getNameString());
            Ui.runs(b, 40f, y + 17f, tw, Ui.INS15, Ui.gray(), Ui.LEFT, m.getHullSpec().getHullName() + " · " + personalityName,
                    new String[]{personalityName}, new Color[]{AcademyPanelDelegate.getPersonalityColor(personality)});

            String salary = Misc.getDGSCredits(Misc.getOfficerSalary(officer));
            Ui.runs(b, w - SALARY_W, y + 8f, SALARY_W, Ui.INS15, Ui.gray(), Ui.RIGHT, salary + " per month", new String[]{salary}, new Color[]{Ui.hl()});
        }
    }

    private String getActionReason() {
        if (!met && dormant) return "Out of supplies. Meet it and move supplies over to wake it.";
        if (!met && stranded) return "Out of fuel. Meet it and move fuel over to get it moving.";
        return null;
    }

    private void addAction(CustomPanelAPI b) {
        String reason = getActionReason();
        ButtonAPI button;
        String tooltip;
        if (met) {
            button = UiButtons.guarded(b, 0f, 0f, w, ACTION_H, "Merge", UiButtons.Kind.RED, null, () -> panel.merge(slot));
            tooltip = "Merge with the main force.";
        } else if (Behaviour.isReturning(fleet) && reason == null) {
            button = UiButtons.guarded(b, 0f, 0f, w, ACTION_H, "Cancel", UiButtons.Kind.AMBER, null, () -> panel.cancelRecall(slot));
            tooltip = "Resume the current mode.";
        } else {
            button = UiButtons.guarded(b, 0f, 0f, w, ACTION_H, "Recall", UiButtons.Kind.RED, reason, () -> panel.recall(slot));
            tooltip = "Return to you and merge into the main force.";
        }

        Ui.tooltip(b, button, null, tooltip);
    }

    private void heading(String title, float gap) {
        Ui.heading(column.block(Ui.HEADING_H, gap), 0f, 0f, w, title);
    }
}
