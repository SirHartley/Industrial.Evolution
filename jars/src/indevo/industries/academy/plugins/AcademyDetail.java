package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.economy.RelicSpeedUpTarget;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.industries.academy.industry.Academy;
import indevo.utils.helper.BillingHelper;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static indevo.utils.helper.StringHelper.getDayOrDays;

// detail column of the Academy panel: officer, administrator or trainee
// information scrolls, the action block from pin() on stays at the bottom
class AcademyDetail {

    static final float PORTRAIT = 96f;
    static final float GRID_X = 106f;
    static final float GRID_ROW = 19f;
    static final float LINE = 24f;
    static final float RUNG_H = 28f;
    static final float RUNG_STEP = 30f;
    static final float GAP = 6f;
    static final float SECTION_GAP = 10f;
    static final float TRAIN_H = 28f;
    static final float STORE_H = 26f;
    static final float BAR_H = 20f;

    private interface Draw {
        void draw(CustomPanelAPI b);
    }

    private record Block(float h, float gap, boolean heading, Draw draw) {
    }

    private final Academy academy;
    private final AcademyView view;
    private final AcademyPanelDelegate panel;
    private final List<Block> info = new ArrayList<>();
    private final List<Block> actions = new ArrayList<>();
    private List<Block> target = info;
    private float w;

    AcademyDetail(Academy academy, AcademyView view, AcademyPanelDelegate panel) {
        this.academy = academy;
        this.view = view;
        this.panel = panel;
    }

    void build(CustomPanelAPI p, float x, float y, float width, float h, PersonAPI person) {
        w = width - UiScroll.GUTTER;

        if (person == null) {
            heading(view.admins ? "Administrators" : "Officers", 0f);
            text(view.admins ? "No administrator to show." : "No officer to show.", Ui.gray(), GAP);
        } else if (person == panel.trainee()) addTrainee(person);
        else if (view.admins) addAdmin(person, academy.getAdminStorage().contains(person));
        else addOfficer(person, academy.getOfficerStorage().contains(person));

        float actionsH = 0f;
        for (int i = 0; i < actions.size(); i++) actionsH += actions.get(i).h() + (i == 0 ? 0f : actions.get(i).gap());
        float infoH = actions.isEmpty() ? h : h - actionsH - SECTION_GAP;

        UiScroll.Column column = UiScroll.list(p, x, y, width, infoH, view.scroll, AcademyView.DETAIL);
        for (Block b : info) b.draw().draw(b.heading() ? column.heading(b.gap()) : column.block(b.h(), b.gap()));
        column.end();

        if (actions.isEmpty()) return;
        CustomPanelAPI pinned = Ui.panel(p, x, y + h - actionsH, w, actionsH);
        float by = 0f;
        for (int i = 0; i < actions.size(); i++) {
            Block b = actions.get(i);
            if (i > 0) by += b.gap();
            b.draw().draw(Ui.panel(pinned, 0f, by, w, b.h()));
            by += b.h();
        }
    }

    // everything added after this call goes into the action block at the bottom
    private void pin() {
        target = actions;
    }

    private void add(float h, float gap, Draw draw) {
        target.add(new Block(h, gap, false, draw));
    }

    private void addOfficer(PersonAPI person, boolean stored) {
        addOfficerInfo(person, stored ? "Stored here" : null);
        addSkills(person, false);

        String current = person.getPersonalityAPI().getId();
        heading("Personality training", SECTION_GAP);
        addLadder(person, current);

        String blocker = academy.getOfficerTrainBlocker(person);
        float fee = 0f;
        if (view.rung != null) {
            int steps = Academy.getPersonalitySteps(current, view.rung);
            int days = academy.getOfficerTrainingDays(steps);
            fee = academy.getOfficerTrainingFee(steps);

            String description = Global.getSettings().getPersonaltySpec(view.rung).getDescription();
            if (description != null && !description.isEmpty()) text(description, Ui.gray(), GAP);
            grid(GAP, GridRow.of("Duration", days + " " + getDayOrDays(days)),
                    GridRow.of(stored ? "Done on" : "Leaves the fleet until", AcademyPanelDelegate.getDoneDate(days)));
            if (blocker == null && !BillingHelper.canAfford(fee)) blocker = "Not enough credits.";
        }

        pin();
        if (view.rung != null) addFee(fee);
        String rung = view.rung;
        guarded("Train", UiButtons.Kind.GREEN, TRAIN_H, blocker, SECTION_GAP, null, () -> {
            academy.setOfficerForTraining(person, rung);
            view.rung = null;
            panel.refresh();
        });

        if (stored) guarded("Retrieve", UiButtons.Kind.STD, STORE_H, Academy.getOfficerSlotBlocker(), GAP, null, () -> {
            academy.returnOfficer(person);
            panel.refresh();
        });
        else {
            // store is never blocked, no reason line reserved below it
            add(STORE_H, GAP, b -> {
                ButtonAPI store = UiButtons.button(b, 0f, 0f, w, STORE_H, "Store", UiButtons.Kind.STD, () -> {
                    academy.storeOfficer(person);
                    panel.refresh();
                });
                Ui.tooltip(b, store, null, getStoreTooltip(null));
            });
        }
    }

    private void addOfficerInfo(PersonAPI person, String assignment) {
        String personality = person.getPersonalityAPI().getId();
        int used = academy.getTrainedSteps(person);

        heading(person.getNameString(), 0f);
        FleetMemberAPI ship = assignment == null ? Academy.getCommandedShip(person) : null;
        float shipW = w - GRID_X - 24f;
        float infoH = ship != null && Ui.width(ship.getShipName(), Ui.INS15) + 2f > shipW ? Math.max(PORTRAIT, 2f + 4f * GRID_ROW + 2f * Ui.wrapHeight("Ag", Ui.INS15, shipW) + 2f) : PORTRAIT;
        add(infoH, GAP, b -> {
            UiImages.portrait(b, 0f, 0f, PORTRAIT, person);
            UiData.grid(b, GRID_X, 2f, w - GRID_X, GRID_ROW, List.of(
                    GridRow.of("Level", person.getStats().getLevel() + ""),
                    GridRow.of("Personality", AcademyPanelDelegate.getPersonalityName(personality), AcademyPanelDelegate.getPersonalityColor(personality)),
                    GridRow.of("Salary", Misc.getDGSCredits(Misc.getOfficerSalary(person))),
                    GridRow.of("Steps used", used + " of " + Math.max(used, academy.getMaxOfficerSteps()))));

            float lineY = 2f + 4f * GRID_ROW;
            if (ship != null) {
                UiImages.ship(b, GRID_X, lineY, 18f, ship, false, false);
                Ui.wrap(b, GRID_X + 24f, lineY, shipW, Ui.lines(ship.getShipName(), Ui.INS15, shipW, 2), Ui.gray());
            } else Ui.text(b, GRID_X, lineY, w - GRID_X, assignment != null ? assignment : "Unassigned", Ui.gray());
        });
    }

    private void addSkills(PersonAPI person, boolean admin) {
        List<AcademyPanelDelegate.Skill> skills = AcademyPanelDelegate.getSkills(person, admin);
        heading("Skills", SECTION_GAP);

        add(Math.max(1, skills.size()) * LINE, GAP, b -> {
            if (skills.isEmpty()) Ui.text(b, 0f, 3f, w, admin ? "No administrator skills." : "No skills.", Ui.gray());

            for (int i = 0; i < skills.size(); i++) {
                AcademyPanelDelegate.Skill skill = skills.get(i);
                float y = i * LINE;
                UiImages.skill(b, 2f, y + 1f, 22f, skill.spec(), skill.elite());
                Ui.text(b, 32f, y + 3f, w - 32f - (skill.elite() ? 48f : 0f), skill.spec().getName());
                if (skill.elite()) Ui.text(b, w - 44f, y + 3f, 44f, "Elite", Ui.hl(), Ui.RIGHT);
            }
        });
    }

    private void addLadder(PersonAPI person, String current) {
        List<String> ladder = new ArrayList<>(Academy.COLOURS_BY_PERSONALITY.keySet());
        List<String> reachable = academy.getReachablePersonalities(person);
        boolean own = academy.getMarket().isPlayerOwned();

        add(ladder.size() * RUNG_STEP - (RUNG_STEP - RUNG_H), GAP, b -> {
            for (int i = 0; i < ladder.size(); i++) {
                String id = ladder.get(i);
                float y = i * RUNG_STEP;
                boolean isCurrent = id.equals(current);
                boolean isReachable = reachable.contains(id);
                boolean selected = id.equals(view.rung);
                boolean live = isCurrent || isReachable;
                int steps = Academy.getPersonalitySteps(current, id);

                Runnable pick = isReachable ? () -> {
                    view.rung = id;
                    panel.refresh();
                } : isCurrent ? panel::refresh : null;
                CustomPanelAPI row = UiButtons.row(b, 0f, y, w, RUNG_H, selected, live, AcademyPanelDelegate.getPersonalityColor(id), pick);
                Ui.textIn(row, 14f, 0f, 96f, RUNG_H, AcademyPanelDelegate.getPersonalityName(id), Ui.INS15,
                        live ? AcademyPanelDelegate.getPersonalityColor(id) : Ui.gray(), Ui.LEFT, new String[0], new Color[0]);

                String right;
                Color color;
                String[] parts = new String[0];
                Color[] colors = new Color[0];
                if (isCurrent) {
                    right = "Current";
                    color = Ui.hl();
                } else if (isReachable) {
                    int days = academy.getOfficerTrainingDays(steps);
                    float fee = academy.getOfficerTrainingFee(steps);
                    String feeText = Misc.getDGSCredits(fee);
                    right = own ? days + " days" : days + " days · " + feeText;
                    color = selected ? Ui.hl() : Ui.text();
                    if (!own && !BillingHelper.canAfford(fee)) {
                        parts = new String[]{feeText};
                        colors = new Color[]{Ui.neg()};
                    }
                } else {
                    boolean needsAlpha = !academy.hasAlphaCore() && steps <= academy.getMaxOfficerSteps(true) - academy.getTrainedSteps(person)
                            && steps > academy.getOfficerStepsLeft(person);
                    right = needsAlpha ? "Needs an alpha core" : "Out of reach";
                    color = Ui.gray();
                }

                Ui.textIn(row, 116f, 0f, w - 126f, RUNG_H, right, Ui.INS15, color, Ui.RIGHT, parts, colors);
                if (isCurrent) Ui.box(b, 0f, y, w, RUNG_H, Ui.hl(), null);
            }
        });
    }

    private void addAdmin(PersonAPI person, boolean stored) {
        MarketAPI governed = stored ? null : Academy.getGovernedMarket(person);
        addAdminInfo(person, stored, governed, null);
        if (Academy.getAdminSkillCount(person) > 0) addSkills(person, true);

        String blocker = academy.getAdminTrainBlocker(person);
        int days = academy.getAdminTrainingDays();
        heading("Administrator training", SECTION_GAP);
        if (blocker == null) grid(GAP, GridRow.of("Gains", "One random skill"), GridRow.of("Duration", days + " " + getDayOrDays(days)),
                GridRow.of("Away until", AcademyPanelDelegate.getDoneDate(days)));
        else grid(GAP, GridRow.of("Gains", "One random skill"), GridRow.of("Duration", days + " " + getDayOrDays(days)));

        pin();
        addFee(academy.getAdminTrainingFee());
        guarded("Train", UiButtons.Kind.GREEN, TRAIN_H, blocker, SECTION_GAP, null, () -> {
            academy.setAdminInTraining(person);
            panel.refresh();
        });

        if (stored) guarded("Retrieve", UiButtons.Kind.STD, STORE_H, Academy.getAdminSlotBlocker(), GAP, null, () -> {
            academy.returnAdmin(person);
            panel.refresh();
        });
        else {
            guarded("Store", UiButtons.Kind.STD, STORE_H, null, GAP, getStoreTooltip(governed), () -> {
                academy.storeAdmin(person);
                panel.refresh();
            });
            if (governed != null) text("Storing them makes you governor of " + governed.getName() + ".", Ui.hl(), 0f);
        }
    }

    private void addAdminInfo(PersonAPI person, boolean stored, MarketAPI governed, String note) {
        float salary = Misc.getAdminSalary(person) * (stored ? 0.1f : 1f);

        heading(person.getNameString(), 0f);
        add(PORTRAIT, GAP, b -> {
            UiImages.portrait(b, 0f, 0f, PORTRAIT, person);
            UiData.grid(b, GRID_X, 2f, w - GRID_X, GRID_ROW, List.of(
                    GridRow.of("Skills", Academy.getAdminSkillCount(person) + " of 2"),
                    GridRow.of("Salary", Misc.getDGSCredits(salary)),
                    GridRow.of("Governs", governed != null ? governed.getName() : "No colony")));

            float lineY = 2f + 3f * GRID_ROW + 4f;
            if (stored) {
                Ui.text(b, GRID_X, lineY, w - GRID_X, "Stored here", Ui.gray());
                Ui.text(b, GRID_X, lineY + GRID_ROW, w - GRID_X, "draws 10% of salary", Ui.gray());
            } else if (governed != null) {
                UiImages.planet(b, GRID_X, lineY, 30f, governed);
                Ui.text(b, GRID_X + 36f, lineY + 7f, w - GRID_X - 36f, AcademyPanelDelegate.getMarketLine(governed, w - GRID_X - 36f), Ui.gray());
            } else if (note != null) Ui.text(b, GRID_X, lineY, w - GRID_X, note, Ui.gray());
        });
    }

    private void addTrainee(PersonAPI person) {
        int total;
        int left;
        float fee;

        if (!view.admins) {
            addOfficerInfo(person, "In training");
            String from = person.getPersonalityAPI().getId();
            String to = academy.getOfficerTrainingTarget();
            String line = AcademyPanelDelegate.getPersonalityName(from) + " to " + AcademyPanelDelegate.getPersonalityName(to);

            heading("In training", SECTION_GAP);
            add(LINE - 2f, GAP, b -> Ui.textIn(b, 0f, 0f, w, LINE - 2f, line, Ui.INS15, Ui.gray(), Ui.CENTER,
                    new String[]{AcademyPanelDelegate.getPersonalityName(from), AcademyPanelDelegate.getPersonalityName(to)},
                    new Color[]{AcademyPanelDelegate.getPersonalityColor(from), AcademyPanelDelegate.getPersonalityColor(to)}));
            total = academy.getCurrentOfficerTrainingDays();
            left = Math.max(0, academy.getRemainingOfficerTrainingDays());
            fee = academy.getOfficerTrainingFee(academy.getCurrentOfficerTrainingSteps());
        } else {
            addAdminInfo(person, false, null, "In training");
            heading("In training", SECTION_GAP);
            add(LINE - 2f, GAP, b -> Ui.textIn(b, 0f, 0f, w, LINE - 2f, "Learns one random skill", Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]));
            total = academy.getAdminTrainingDays();
            left = Math.max(0, academy.getRemainingAdminTrainingDays());
            fee = academy.getAdminTrainingFee();
        }

        String paused = panel.getPausedReason();
        add(BAR_H, 2f, b -> UiData.bar(b, 0f, 0f, w, BAR_H, AcademyPanelDelegate.progress(total, left), paused != null ? Ui.barColor("progressBarWarningColor") : Ui.barBlue(),
                AcademyPanelDelegate.getDayLabel(total, left)));
        if (paused != null) text("Paused: " + paused, Ui.neg(), 4f);

        boolean own = academy.getMarket().isPlayerOwned();
        List<GridRow> rows = new ArrayList<>();
        rows.add(GridRow.of("Days left", left + ""));
        rows.add(GridRow.of("Done on", AcademyPanelDelegate.getDoneDate(left)));
        rows.add(own ? GridRow.of("Fee paid", "Free", Ui.pos()) : GridRow.of("Fee paid", Misc.getDGSCredits(fee)));
        if (!view.admins) rows.add(GridRow.of("Mentored on completion", academy.hasHolobench() || Misc.isMentored(person) ? "Yes" : "No"));
        grid(8f, rows.toArray(new GridRow[0]));

        pin();
        addAccelerate();
        addAbort();
    }

    private void addAccelerate() {
        RelicSpeedUpTarget target = academy.getTrainingSpeedUp(view.admins);
        int cost = target.getSpeedUpCost();
        MarketAPI market = academy.getMarket();

        heading(target.getSpeedUpLabel(), 0f);
        text("Halves the days left. Usable once every " + Math.round(RelicSpeedUpTarget.COOLDOWN_DAYS) + " days.", Ui.gray(), GAP);
        add(UiData.COST_ROW_H, 4f, b -> UiData.relicRow(b, 0f, 0f, w, RelicHelper.getName(), cost, market));
        int after = target.getSpeedUpDaysAfter();
        grid(4f, GridRow.of("Days left after", after + ""));

        String reason;
        float cooldown = target.getSpeedUpCooldownLeft();
        if (cooldown > 0f) {
            int days = (int) Math.ceil(cooldown);
            reason = "Unavailable for another " + days + " " + getDayOrDays(days) + ".";
        } else if (!target.canSpeedUp()) reason = "Too few days left.";
        else if (!RelicHelper.canAfford(market, cost)) reason = "Not enough " + RelicHelper.getName() + ".";
        else reason = null;

        boolean blocked = reason != null;
        add(UiButtons.guardHeight(w, TRAIN_H, reason), GAP, row -> {
            ButtonAPI button = UiButtons.button(row, 0f, 0f, w, TRAIN_H, target.getSpeedUpLabel(), blocked ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.ALL,
                    UiImages.relicSprite(), null, Ui.ORB12, blocked ? null : () -> {
                        RelicSpeedUpDialog.apply(target);
                        panel.refresh();
                    });
            if (blocked) Ui.wrap(row, 0f, TRAIN_H + UiButtons.REASON_GAP, w, reason, Ui.neg());
            Ui.tooltip(row, button, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.LEFT, t -> RelicSpeedUpDialog.addTooltip(t, target));
        });
    }

    private void addAbort() {
        String blocker = view.admins ? Academy.getAdminSlotBlocker() : Academy.getOfficerSlotBlocker();
        heading("Abort", SECTION_GAP);
        text("The fee and the progress are lost.", Ui.gray(), GAP);

        float half = Math.round((w - 7f) / 2f);
        add(UiButtons.guardHeight(w, STORE_H, blocker), GAP, row -> {
            UiButtons.button(row, 0f, 0f, half, STORE_H, "Abort to fleet", blocker != null ? UiButtons.Kind.OFF : UiButtons.Kind.RED, CutStyle.TL_BR,
                    blocker != null ? null : () -> abort(false));
            UiButtons.button(row, w - half, 0f, half, STORE_H, "Abort to storage", UiButtons.Kind.RED, CutStyle.TL_BR, () -> abort(true));
            if (blocker != null) Ui.wrap(row, 0f, STORE_H + UiButtons.REASON_GAP, w, blocker, Ui.neg());
        });
    }

    private void abort(boolean toStorage) {
        if (view.admins) academy.abortAdminTraining(toStorage);
        else academy.abortOfficerTraining(toStorage);
        panel.refresh();
    }

    private void addFee(float fee) {
        add(UiData.COST_ROW_H, 4f, b -> {
            if (academy.getMarket().isPlayerOwned()) {
                UiData.value(b, 0f, 0f, w, UiData.COST_ROW_H, "Fee", "Free own Academy", Ui.gray(), new String[]{"Free"}, new Color[]{Ui.pos()});
            } else UiData.creditsRow(b, 0f, 0f, w, "Fee", fee);
        });
    }

    private String getStoreTooltip(MarketAPI governed) {
        boolean own = academy.getMarket().isPlayerOwned();
        String fee = Misc.getDGSCredits(Setting.MONTHLY_AI_STORAGE_COST.intValue());
        String text;

        if (!view.admins) text = own ? "Stored officers draw their full salary." : "Storage costs " + fee + " per month plus full salary.";
        else text = own ? "Stored administrators draw 10% of their salary." : "Storage costs " + fee + " per month plus 10% of salary.";

        if (governed != null) text += " You take over as governor of " + governed.getName() + ".";
        return text;
    }

    private void heading(String title, float gap) {
        target.add(new Block(Ui.HEADING_H, gap, true, b -> Ui.heading(b, 0f, 0f, w, title)));
    }

    private void text(String text, Color color, float gap) {
        add(Ui.wrapHeight(text, Ui.INS15, w), gap, b -> Ui.wrap(b, 0f, 0f, w, text, color));
    }

    private void grid(float gap, GridRow... rows) {
        add(rows.length * GRID_ROW, gap, b -> UiData.grid(b, 0f, 0f, w, GRID_ROW, List.of(rows)));
    }

    private void guarded(String label, UiButtons.Kind kind, float h, String reason, float gap, String tooltip, Runnable action) {
        add(UiButtons.guardHeight(w, h, reason), gap, b -> {
            ButtonAPI button = UiButtons.guarded(b, 0f, 0f, w, h, label, kind, reason, action);
            if (tooltip != null) Ui.tooltip(b, button, null, tooltip);
        });
    }
}
