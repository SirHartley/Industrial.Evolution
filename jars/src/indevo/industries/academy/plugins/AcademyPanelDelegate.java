package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.ItemIds;
import indevo.industries.academy.industry.Academy;
import indevo.items.installable.SpecialItemEffectsRepo;
import indevo.utils.helper.BillingHelper;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;
import java.util.*;

public class AcademyPanelDelegate extends BaseCustomDialogDelegate {

    public static final float BOARD_W = 1104f;
    public static final float BOARD_H = 780f;
    static final float DETAIL_W = 295f;
    static final float COLUMN_GAP = 10f;
    static final float BODY_Y = 64f;
    static final float TAB_W = 210f;
    static final float ROW_H = 50f;
    static final float ROW_GAP = 4f;
    static final float GROUP_GAP = 10f;
    static final float EMPTY_H = 24f;
    static final float PORTRAIT = 46f;
    static final float SKILL = 28f;
    static final float SKILL_SMALL = 20f;
    static final float PIP_W = 14f;
    static final float PIP_H = 5f;
    static final float PIP_GAP = 2f;
    static final float SALARY_W = 70f;
    static final float ASSIGN_X = 498f;
    static final float NAME_W = 162f;
    static final float BAR_X = 316f;

    private final Academy academy;
    private final AcademyView view;
    private final Runnable onLeave;
    private CustomDialogCallback callback;
    private UiRoot root;
    private boolean open = false;

    record Skill(SkillSpecAPI spec, boolean elite) {
    }

    public AcademyPanelDelegate(Academy academy, AcademyView view, Runnable onLeave) {
        this.academy = academy;
        this.view = view;
        this.onLeave = onLeave;
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
        open = false;
        onLeave.run();
    }

    @Override
    public void customDialogCancel() {
        open = false;
        onLeave.run();
    }

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    void refresh() {
        root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        PersonAPI selected = resolveSelection();
        float rx = w - DETAIL_W;

        addHeader(p, w);
        addList(p, rx - COLUMN_GAP, h - BODY_Y);
        Ui.vrule(p, rx - 6f, BODY_Y, h - BODY_Y);
        new AcademyDetail(academy, view, this).build(p, rx, BODY_Y, DETAIL_W, h - BODY_Y, selected);
    }

    private PersonAPI resolveSelection() {
        PersonAPI trainee = trainee();
        List<PersonAPI> all = new ArrayList<>();
        if (trainee != null) all.add(trainee);
        all.addAll(active());
        all.addAll(stored());

        PersonAPI selected = null;
        for (PersonAPI person : all) {
            if (person.getId().equals(view.selected)) selected = person;
        }

        if (selected == null && !all.isEmpty()) selected = all.get(0);
        view.selected = selected != null ? selected.getId() : null;

        if (selected != null && !view.admins && selected != trainee) {
            List<String> reachable = academy.getReachablePersonalities(selected);
            if (!reachable.contains(view.rung)) view.rung = nearestRung(selected, reachable);
        } else view.rung = null;

        return selected;
    }

    private String nearestRung(PersonAPI person, List<String> reachable) {
        String current = person.getPersonalityAPI().getId();
        String nearest = null;
        int best = Integer.MAX_VALUE;

        for (String id : reachable) {
            int steps = Academy.getPersonalitySteps(current, id);
            int score = steps + (BillingHelper.canAfford(academy.getOfficerTrainingFee(steps)) ? 0 : 100);
            if (score < best) {
                nearest = id;
                best = score;
            }
        }

        return nearest;
    }

    PersonAPI trainee() {
        return view.admins ? academy.getAdminInTraining() : academy.getOfficerInTraining();
    }

    List<PersonAPI> active() {
        List<PersonAPI> people = new ArrayList<>(view.admins ? academy.getStaffAdmins() : academy.getFleetOfficers());
        people.sort(order());
        return people;
    }

    List<PersonAPI> stored() {
        List<PersonAPI> people = new ArrayList<>(view.admins ? academy.getAdminStorage() : academy.getOfficerStorage());
        people.sort(order());
        return people;
    }

    private Comparator<PersonAPI> order() {
        return view.admins
                ? Comparator.comparingInt((PersonAPI p) -> Academy.getAdminSkillCount(p)).reversed()
                : Comparator.comparingInt((PersonAPI p) -> p.getStats().getLevel()).reversed();
    }

    void select(PersonAPI person) {
        if (!person.getId().equals(view.selected)) {
            view.selected = person.getId();
            view.rung = null;
            view.scroll.reset(AcademyView.DETAIL);
        }

        refresh();
    }

    private void switchTab(boolean admins) {
        if (view.admins != admins) {
            view.admins = admins;
            view.selected = null;
            view.rung = null;
            view.scroll.reset(AcademyView.LIST);
            view.scroll.reset(AcademyView.DETAIL);
        }

        refresh();
    }

    private void addHeader(CustomPanelAPI p, float w) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        UiFrame.title(p, w, academy.getCurrentName(), academy.getMarket().getName(), this::addHelp);

        String officers = "Officers " + Misc.getNumNonMercOfficers(fleet) + " / " + Misc.getMaxOfficers(fleet);
        String admins = "Administrators " + Global.getSector().getCharacterData().getAdmins().size()
                + " / " + fleet.getCommander().getStats().getAdminNumber().getModifiedInt();
        UiButtons.tabs(p, 0f, UiFrame.HEADER_Y, w, TAB_W, UiFrame.HEADER_H, new String[]{officers, admins}, view.admins ? 1 : 0, null, i -> switchTab(i == 1));

        float right = UiFrame.credits(p, w, UiFrame.HEADER_Y);
        if (view.admins) return;

        float tabsEnd = 2f * TAB_W + UiButtons.TAB_GAP + 10f;
        if (academy.hasHolobench()) {
            String name = Global.getSettings().getSpecialItemSpec(ItemIds.HOLOBENCH).getName();
            if (right - slotWidth(name) > tabsEnd) {
                right = UiFrame.slot(p, right, UiFrame.HEADER_Y, Global.getSettings().getSpecialItemSpec(ItemIds.HOLOBENCH).getIconName(), name, name,
                        "Officers leave training mentored and get one more personality step.",
                        "Stored officers gain " + Misc.getWithDGS(SpecialItemEffectsRepo.HOLOBENCH_MONTHLY_XP) + " experience per month.");
            }
        }

        if (academy.hasAlphaCore()) {
            String name = Global.getSettings().getCommoditySpec(Commodities.ALPHA_CORE).getName();
            if (right - slotWidth(name) > tabsEnd) {
                UiFrame.slot(p, right, UiFrame.HEADER_Y, UiImages.commoditySprite(Commodities.ALPHA_CORE), name, name,
                        "Officers get one more personality step.");
            }
        }
    }

    private static float slotWidth(String text) {
        return Ui.width(text, Ui.INS15) + 2f + 4f + UiFrame.SLOT_ICON;
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        Color hl = Misc.getHighlightColor();
        float pad = 10f;
        boolean own = academy.getMarket().isPlayerOwned();
        String storageFee = Misc.getDGSCredits(Setting.MONTHLY_AI_STORAGE_COST.intValue());

        if (!view.admins) {
            String days = academy.getOfficerTrainingDays() + " days";

            if (own) tooltip.addPara("Training is free and takes %s per personality step.", 0f, hl, days);
            else tooltip.addPara("Training costs %s and takes %s per personality step.", 0f, hl, Misc.getDGSCredits(academy.getOfficerTrainingFee(1)), days);
            String steps = Academy.getStepCountText(academy.getMaxOfficerSteps());
            if (academy.hasAlphaCore()) tooltip.addPara("Up to %s per officer.", pad, hl, steps);
            else tooltip.addPara("%s per officer, %s with an alpha core.", pad, hl, Misc.ucFirst(steps), Academy.getStepCountText(academy.getMaxOfficerSteps(true)));

            if (academy.hasHolobench()) tooltip.addPara(Global.getSettings().getSpecialItemSpec(ItemIds.HOLOBENCH).getName() + ": officers leave training %s. Stored officers gain %s experience per month.", pad, hl, "mentored", Misc.getWithDGS(SpecialItemEffectsRepo.HOLOBENCH_MONTHLY_XP));

            if (own) tooltip.addPara("Stored officers draw their full salary.", pad);
            else tooltip.addPara("Storage costs %s per month for each officer, plus full salary.", pad, hl, storageFee);
            tooltip.addPara("Aborting a training loses the fee and the progress.", pad);
        } else {
            String days = academy.getAdminTrainingDays() + " days";

            if (own) tooltip.addPara("Training is free and takes %s per skill.", 0f, hl, days);
            else tooltip.addPara("Training costs %s and takes %s per skill.", 0f, hl, Misc.getDGSCredits(academy.getAdminTrainingFee()), days);

            if (own) tooltip.addPara("Stored administrators draw %s of their salary.", pad, hl, "10%");
            else tooltip.addPara("Storage costs %s per month for each administrator, plus %s of salary.", pad, hl, storageFee, "10%");
            tooltip.addPara("Aborting a training loses the fee and the progress.", pad);
        }
    }

    private void addList(CustomPanelAPI p, float w, float h) {
        UiScroll.Column list = UiScroll.list(p, 0f, BODY_Y, w, h, view.scroll, AcademyView.LIST);
        float rw = list.width();
        PersonAPI trainee = trainee();

        heading(list, rw, "In training", null, 0f);
        if (trainee != null) addStrip(list.block(ROW_H, ROW_GAP), rw, trainee);
        else empty(list, rw, view.admins ? "No administrator in training." : "No officer in training.");

        heading(list, rw, view.admins ? "On staff" : "In fleet", null, GROUP_GAP);
        List<PersonAPI> active = active();
        for (PersonAPI person : active) addRow(list, rw, person, false);
        if (active.isEmpty()) empty(list, rw, view.admins ? "No administrators on staff." : "No officers in your fleet.");

        boolean own = academy.getMarket().isPlayerOwned();
        String note = own ? null : Misc.getDGSCredits(Setting.MONTHLY_AI_STORAGE_COST.intValue()) + " storage fee per month each";
        heading(list, rw, "Stored here", note, GROUP_GAP);
        List<PersonAPI> stored = stored();
        for (PersonAPI person : stored) addRow(list, rw, person, true);
        if (stored.isEmpty()) empty(list, rw, view.admins ? "No administrators stored here." : "No officers stored here.");

        list.end();
    }

    private static void heading(UiScroll.Column list, float w, String title, String note, float gap) {
        CustomPanelAPI block = list.heading(gap);
        if (note != null) Ui.heading(block, 0f, 0f, w, title, note);
        else Ui.heading(block, 0f, 0f, w, title, Ui.LEFT);
    }

    private static void empty(UiScroll.Column list, float w, String text) {
        CustomPanelAPI block = list.block(EMPTY_H, ROW_GAP);
        Ui.text(block, 8f, 2f, w - 16f, text, Ui.gray());
    }

    private void addRow(UiScroll.Column list, float w, PersonAPI person, boolean stored) {
        boolean selected = person.getId().equals(view.selected);
        CustomPanelAPI row = UiButtons.row(list.block(ROW_H, ROW_GAP), 0f, 0f, w, ROW_H, selected, true, null, () -> select(person));

        UiImages.portrait(row, 3f, 2f, PORTRAIT, person);
        if (view.admins) addAdminRow(row, w, person, stored, selected);
        else addOfficerRow(row, w, person, stored, selected);
    }

    private void addOfficerRow(CustomPanelAPI row, float w, PersonAPI person, boolean stored, boolean selected) {
        String personality = person.getPersonalityAPI().getId();
        Ui.text(row, 58f, 7f, NAME_W, person.getNameString(), selected ? Ui.bright() : Ui.text());
        Ui.text(row, 58f, 26f, NAME_W, "Level " + person.getStats().getLevel(), Ui.gray());
        Ui.text(row, 226f, 7f, 80f, getPersonalityName(personality), getPersonalityColor(personality));
        pips(row, 226f, 31f, personality);
        addSkillIcons(row, 312f, person);

        float textX = ASSIGN_X + PORTRAIT + 8f;
        float textW = w - SALARY_W - 12f - textX;
        if (stored) {
            String[] lines = getStoredLines(person);
            Ui.text(row, ASSIGN_X, 7f, textW + textX - ASSIGN_X, lines[0], Ui.gray());
            Ui.text(row, ASSIGN_X, 26f, textW + textX - ASSIGN_X, lines[1], lines[1].startsWith("+") ? Ui.pos() : Ui.gray());
        } else {
            FleetMemberAPI ship = Academy.getCommandedShip(person);
            if (ship != null) {
                UiImages.ship(row, ASSIGN_X, 2f, PORTRAIT, ship, true, false);
                Ui.nameLines(row, textX, 7f, 19f, textW, ship.getShipName(), Ui.text(), ship.getHullSpec().getHullNameWithDashClass(), textW);
            } else {
                Ui.dashbox(row, ASSIGN_X, 2f, PORTRAIT, PORTRAIT);
                Ui.text(row, textX, 16f, textW, "Unassigned", Ui.gray());
            }
        }

        addSalary(row, w, Misc.getOfficerSalary(person));
    }

    private String[] getStoredLines(PersonAPI person) {
        if (!academy.hasHolobench()) return new String[]{"In storage", "draws full salary"};
        if (Misc.isMercenary(person)) return new String[]{"Mercenary", "No reserve drills"};

        OfficerLevelupPlugin levels = (OfficerLevelupPlugin) Global.getSettings().getPlugin("officerLevelUp");
        if (person.getStats().getLevel() >= levels.getMaxLevel(person)) return new String[]{"Max level", "No reserve drills"};
        return new String[]{"Reserve drills", "+" + Misc.getWithDGS(SpecialItemEffectsRepo.HOLOBENCH_MONTHLY_XP) + " XP per month"};
    }

    private void addAdminRow(CustomPanelAPI row, float w, PersonAPI person, boolean stored, boolean selected) {
        Ui.text(row, 58f, 7f, 164f, person.getNameString(), selected ? Ui.bright() : Ui.text());
        Ui.text(row, 58f, 26f, 164f, Academy.getAdminSkillCount(person) + " of 2 skills", Ui.gray());

        List<Skill> skills = getSkills(person, true);
        UiImages.iconrow(row, 230f, 10f, ASSIGN_X - 8f - 230f, skills, SKILL, SKILL, 4f, 1, AcademyPanelDelegate::skillCell, AcademyPanelDelegate::skillName);
        for (int i = skills.size(); i < 2; i++) Ui.dashbox(row, 230f + i * (SKILL + 4f), 10f, SKILL, SKILL);

        float textX = ASSIGN_X + PORTRAIT + 8f;
        float textW = w - SALARY_W - 12f - textX;
        MarketAPI governed = stored ? null : Academy.getGovernedMarket(person);
        if (stored) {
            Ui.text(row, ASSIGN_X, 7f, textW + textX - ASSIGN_X, "In storage", Ui.gray());
            Ui.text(row, ASSIGN_X, 26f, textW + textX - ASSIGN_X, "draws 10% of salary", Ui.gray());
        } else if (governed != null) {
            Ui.well(row, ASSIGN_X, 2f, PORTRAIT, PORTRAIT);
            UiImages.planet(row, ASSIGN_X + 5f, 7f, PORTRAIT - 10f, governed);
            Ui.nameLines(row, textX, 7f, 19f, textW, governed.getName(), Ui.text(), getMarketLine(governed, textW), textW);
        } else {
            Ui.dashbox(row, ASSIGN_X, 2f, PORTRAIT, PORTRAIT);
            Ui.text(row, textX, 16f, textW, "Unassigned", Ui.gray());
        }

        addSalary(row, w, Misc.getAdminSalary(person) * (stored ? 0.1f : 1f));
    }

    // size and system, the system alone when both do not fit
    static String getMarketLine(MarketAPI market, float w) {
        String line = getMarketLine(market);
        if (Ui.width(line, Ui.INS15) + 2f <= w || market.getStarSystem() == null) return line;
        return market.getStarSystem().getBaseName();
    }

    static String getMarketLine(MarketAPI market) {
        String size = "Size " + market.getSize();
        return market.getStarSystem() != null ? size + " · " + market.getStarSystem().getBaseName() : size;
    }

    private static void addSalary(CustomPanelAPI row, float w, float salary) {
        Ui.text(row, w - SALARY_W - 10f, 7f, SALARY_W, Misc.getDGSCredits(salary), Ui.hl(), Ui.RIGHT);
        Ui.text(row, w - SALARY_W - 10f, 26f, SALARY_W, "per month", Ui.gray(), Ui.RIGHT);
    }

    private void addSkillIcons(CustomPanelAPI row, float x, PersonAPI person) {
        List<Skill> skills = getSkills(person, false);
        if (skills.isEmpty()) {
            Ui.text(row, x, 16f, ASSIGN_X - 8f - x, "No skills", Ui.gray());
            return;
        }

        if (skills.size() <= 6) {
            for (int i = 0; i < skills.size(); i++) UiImages.skill(row, x + i * (SKILL + 3f), 10f, SKILL, skills.get(i).spec(), skills.get(i).elite());
            return;
        }

        UiImages.iconrow(row, x, 3f, ASSIGN_X - 6f - x, skills, SKILL_SMALL, SKILL_SMALL, 4f, 2, AcademyPanelDelegate::skillCell, AcademyPanelDelegate::skillName);
    }

    private static void skillCell(CustomPanelAPI p, float x, float y, float w, float h, Skill skill) {
        UiImages.skill(p, x, y, w, skill.spec(), skill.elite());
    }

    private static String skillName(Skill skill) {
        return skill.elite() ? skill.spec().getName() + " (elite)" : skill.spec().getName();
    }

    private void addStrip(CustomPanelAPI block, float w, PersonAPI person) {
        boolean selected = person.getId().equals(view.selected);
        CustomPanelAPI row = UiButtons.row(block, 0f, 0f, w, ROW_H, selected, true, null, () -> select(person));

        UiImages.portrait(row, 3f, 2f, PORTRAIT, person);
        Ui.text(row, 58f, 7f, NAME_W, person.getNameString(), selected ? Ui.bright() : Ui.text());

        int total;
        int left;
        if (!view.admins) {
            String from = person.getPersonalityAPI().getId();
            String to = academy.getOfficerTrainingTarget();
            Ui.text(row, 58f, 26f, NAME_W, "Level " + person.getStats().getLevel(), Ui.gray());
            Ui.text(row, 226f, 7f, 84f, getPersonalityName(from), getPersonalityColor(from));
            Ui.runs(row, 226f, 26f, 84f, Ui.INS15, Ui.gray(), Ui.LEFT, "to " + getPersonalityName(to), new String[]{getPersonalityName(to)}, new Color[]{getPersonalityColor(to)});
            total = academy.getCurrentOfficerTrainingDays();
            left = Math.max(0, academy.getRemainingOfficerTrainingDays());
        } else {
            Ui.text(row, 58f, 26f, NAME_W, Academy.getAdminSkillCount(person) + " of 2 skills", Ui.gray());
            Ui.text(row, 226f, 7f, 84f, "Learns one", Ui.gray());
            Ui.text(row, 226f, 26f, 84f, "random skill", Ui.gray());
            total = academy.getAdminTrainingDays();
            left = Math.max(0, academy.getRemainingAdminTrainingDays());
        }

        String paused = getPausedReason();
        float barW = 200f;
        float rightX = w - 150f;
        UiData.bar(row, BAR_X, 8f, barW, 20f, progress(total, left), paused != null ? Ui.barColor("progressBarWarningColor") : Ui.barBlue(), getDayLabel(total, left));
        if (paused != null) Ui.text(row, BAR_X, 31f, rightX - 8f - BAR_X, "Paused: " + paused, Ui.neg());
        else Ui.text(row, BAR_X, 31f, rightX - 8f - BAR_X, "Done on " + getDoneDate(left), Ui.gray());

        Ui.text(row, rightX, 7f, 140f, left + " " + (left == 1 ? "day" : "days") + " left", Ui.hl(), Ui.RIGHT);
        String steps = view.admins ? "one skill" : getStepsWord(academy.getCurrentOfficerTrainingSteps());
        Ui.text(row, rightX, 26f, 140f, steps, Ui.gray(), Ui.RIGHT);
    }

    static float progress(int total, int left) {
        return total <= 0 ? 1f : Math.max(0f, Math.min(1f, (total - left) / (float) total));
    }

    static String getDayLabel(int total, int left) {
        return "Day " + Math.max(0, total - left) + " of " + total;
    }

    static String getStepsWord(int steps) {
        String[] words = {"no steps", "one step", "two steps", "three steps", "four steps"};
        return steps >= 0 && steps < words.length ? words[steps] : steps + " steps";
    }

    static String getDoneDate(int days) {
        long ms = Global.getSector().getClock().getTimestamp() + (long) (days * 8.64E7);
        return Global.getSector().getClock().createClock(ms).getDateString();
    }

    String getPausedReason() {
        if (!academy.isFunctional()) return "the Academy is not functional";
        if (academy.getMarket().getFaction().isHostileTo(Global.getSector().getPlayerFaction())) return "the market is hostile to you";
        return null;
    }

    static void pips(CustomPanelAPI p, float x, float y, String personality) {
        List<String> ladder = new ArrayList<>(Academy.COLOURS_BY_PERSONALITY.keySet());
        int index = ladder.indexOf(personality);
        UiData.pips(p, x, y, ladder.size(), index, index + 1, PIP_W, PIP_H, PIP_GAP, getPersonalityColor(personality));
    }

    static List<Skill> getSkills(PersonAPI person, boolean admin) {
        List<Skill> skills = new ArrayList<>();
        for (String id : Global.getSettings().getSortedSkillIds()) {
            int level = (int) person.getStats().getSkillLevel(id);
            if (level <= 0) continue;

            SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
            if (admin ? !spec.isAdminSkill() : !spec.isCombatOfficerSkill()) continue;
            skills.add(new Skill(spec, !admin && level > 1));
        }

        return skills;
    }

    // raw COLOURS_BY_PERSONALITY blue and red are too dark on the panel background
    public static Color getPersonalityColor(String id) {
        Color color = Academy.COLOURS_BY_PERSONALITY.get(id);
        return color != null ? Misc.interpolateColor(color, Color.WHITE, 0.4f) : Misc.getTextColor();
    }

    public static String getPersonalityName(String id) {
        return Global.getSettings().getPersonaltySpec(id).getDisplayName();
    }
}
