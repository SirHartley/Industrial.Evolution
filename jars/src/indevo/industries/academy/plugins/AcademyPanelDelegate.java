package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.dialogue.sidepanel.HelpIcon;
import indevo.economy.RelicSpeedUpTarget;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.industries.academy.industry.Academy;
import indevo.items.installable.SpecialItemEffectsRepo;

import java.awt.*;
import java.util.List;
import java.util.*;

public class AcademyPanelDelegate extends ButtonReportingDialogueDelegate {

    private static final float PAD = 10f;
    private static final float SPAD = 5f;
    private static final float TAB_W = 200f;
    private static final float TAB_H = 28f;
    private static final float BUTTON_W = 150f;
    private static final float BUTTON_H = 26f;
    private static final float PORTRAIT = 64f;
    private static final float BOX = PORTRAIT;
    private static final float BOX_LABEL_H = 14f;
    private static final float ICON = 28f;
    private static final float SLOT_H = 86f;
    private static final float BAR_W = 320f;
    private static final float SCROLL_W = 20f;
    private static final float EMPTY_H = PAD + 20f;
    private static final int COLUMNS = 4;
    private static final float CARD_GAP = 8f;
    private static final float CARD_PAD = 8f;
    private static final float BOX_GAP = 6f;
    private static final float LINE_PAD = 2f;
    static final float CARD_HEADER_H = 18f;
    private static final float CARD_SKILLS_Y = CARD_HEADER_H + CARD_PAD + BOX + 3f + BOX_LABEL_H + 1f;
    private static final float CARD_BUTTONS_Y = CARD_SKILLS_Y + ICON + 8f;
    private static final float CARD_H = CARD_BUTTONS_Y + BUTTON_H + CARD_PAD;

    private static final Color ABORT_BG = new Color(80, 20, 10, 255);
    static final Color STORED_BG = new Color(60, 60, 60, 255);
    private static final Color TRAIN_BG = new Color(50, 130, 0, 255);

    // tooltip elements indent their first item by 5 px
    public static final float TOOLTIP_INSET = 5f;
    // section heading labels are 10 px wider than the width passed
    public static final float HEADING_EXTRA = 10f;

    private final Academy academy;
    private final AcademyDialogPlugin plugin;

    private CustomPanelAPI basePanel;
    private CustomPanelAPI panel;
    private CustomDialogCallback callback;
    private ScrollPanelAPI externalScroller;
    private float scrollY = 0f;
    private boolean adminTab = false;
    private boolean open = false;
    private boolean switchingToPopup = false;

    public AcademyPanelDelegate(Academy academy, AcademyDialogPlugin plugin) {
        this.academy = academy;
        this.plugin = plugin;
    }

    public static float getWidth() {
        return Math.min(1104f, Global.getSettings().getScreenWidth() - 100f) - 20f;
    }

    // custom dialog adds 55 px for its frame and the button row
    public static float getMaxHeight() {
        return Math.min(850f, Global.getSettings().getScreenHeight() - 100f) - 55f;
    }

    // sized for the larger tab so switching tabs keeps the dialog
    public float getHeight() {
        float top = CARD_HEADER_H + PAD + TAB_H + PAD + CARD_HEADER_H + SPAD + SLOT_H + PAD;
        return Math.min(getMaxHeight(), top + Math.max(getListHeight(false), getListHeight(true)) + PAD);
    }

    private float getListHeight(boolean admin) {
        int active = admin ? academy.getStaffAdmins().size() : academy.getFleetOfficers().size();
        int stored = admin ? academy.getAdminStorage().size() : academy.getOfficerStorage().size();
        if (active == 0 && stored == 0) return EMPTY_H;

        return getGroupHeight(active) + getGroupHeight(stored) + (active > 0 && stored > 0 ? PAD : 0f);
    }

    private static float getGroupHeight(int count) {
        if (count == 0) return 0f;

        int rows = (count + COLUMNS - 1) / COLUMNS;
        return CARD_HEADER_H + SPAD + rows * CARD_H + (rows - 1) * CARD_GAP;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.basePanel = panel;
        this.callback = callback;
        this.panel = null;
        this.externalScroller = null;
        this.open = true;
        this.switchingToPopup = false;
        recreatePanel();
    }

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    @Override
    public String getConfirmText() {
        return "Return";
    }

    @Override
    public void customDialogConfirm() {
        open = false;
        plugin.leave();
    }

    @Override
    public void customDialogCancel() {
        open = false;
        if (switchingToPopup) switchingToPopup = false;
        else plugin.leave();
    }

    // content grew past the dialog, reopen it at the new height
    private void update() {
        if (getHeight() <= basePanel.getPosition().getHeight() + 1f) {
            recreatePanel();
            return;
        }

        if (externalScroller != null) scrollY = externalScroller.getYOffset();
        switchingToPopup = true;
        callback.dismissCustomDialog(1);
        plugin.showPanel();
    }

    private void recreatePanel() {
        if (externalScroller != null) scrollY = externalScroller.getYOffset();
        if (panel != null) basePanel.removeComponent(panel);

        float w = basePanel.getPosition().getWidth();
        float h = basePanel.getPosition().getHeight();
        float cw = w - SCROLL_W;
        panel = Global.getSettings().createCustom(w, h, null);

        float y = 0f;
        addHeading(panel, academy.getCurrentName() + " - " + academy.getMarket().getName(), Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                0f, y, cw - HelpIcon.SIZE - SPAD);
        panel.addUIElement(HelpIcon.create(panel, this::addHelp)).inTL(cw - HelpIcon.SIZE - TOOLTIP_INSET, y - 1f);
        y += CARD_HEADER_H + PAD;

        panel.addComponent(createTabs(cw)).inTL(0f, y);
        y += TAB_H + PAD;

        addHeading(panel, "In training", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), 0f, y, cw);
        y += CARD_HEADER_H + SPAD;

        panel.addComponent(createTrainingSlot(cw)).inTL(0f, y);
        y += SLOT_H + PAD;

        float listH = h - y;
        CustomPanelAPI listPanel = panel.createCustomPanel(w, listH, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI list = listPanel.createUIElement(w, listH, true);
        addRows(list, cw);
        listPanel.addUIElement(list).inTL(-TOOLTIP_INSET, 0f);
        panel.addComponent(listPanel).inTL(0f, y);

        basePanel.addComponent(panel);

        ScrollPanelAPI scroller = list.getExternalScroller();
        if (scroller != null) scroller.setYOffset(scrollY);
        externalScroller = scroller;
    }

    private CustomPanelAPI createTabs(float w) {
        Color base = Misc.getButtonTextColor();
        Color dark = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        CustomPanelAPI tabs = panel.createCustomPanel(w, TAB_H, new ButtonReportingCustomPanel(this));

        String officerLabel = "Officers " + Misc.getNumNonMercOfficers(fleet) + " / " + Misc.getMaxOfficers(fleet);
        TooltipMakerAPI officerTab = tabs.createUIElement(TAB_W, TAB_H, false);
        officerTab.addAreaCheckbox(officerLabel, new ButtonAction(this) {
            @Override
            public void execute() {
                switchTab(false);
            }
        }, base, dark, bright, TAB_W, TAB_H, 0f).setChecked(!adminTab);
        tabs.addUIElement(officerTab).inTL(-TOOLTIP_INSET, 0f);

        int maxAdmins = fleet.getCommander().getStats().getAdminNumber().getModifiedInt();
        String adminLabel = "Administrators " + Global.getSector().getCharacterData().getAdmins().size() + " / " + maxAdmins;
        TooltipMakerAPI adminTabElement = tabs.createUIElement(TAB_W, TAB_H, false);
        adminTabElement.addAreaCheckbox(adminLabel, new ButtonAction(this) {
            @Override
            public void execute() {
                switchTab(true);
            }
        }, base, dark, bright, TAB_W, TAB_H, 0f).setChecked(adminTab);
        tabs.addUIElement(adminTabElement).inTL(TAB_W + PAD - TOOLTIP_INSET, 0f);

        TooltipMakerAPI credits = tabs.createUIElement(300f, TAB_H, false);
        LabelAPI label = credits.addPara("Credits: %s", 0f, Misc.getHighlightColor(), Misc.getDGSCredits(fleet.getCargo().getCredits().get()));
        label.setAlignment(Alignment.RMID);
        tabs.addUIElement(credits).inTR(TOOLTIP_INSET, 6f);

        return tabs;
    }

    private void switchTab(boolean admin) {
        if (adminTab != admin) {
            adminTab = admin;
            externalScroller = null;
            scrollY = 0f;
        }

        update();
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        Color hl = Misc.getHighlightColor();
        boolean own = academy.getMarket().isPlayerOwned();
        String storageFee = Misc.getDGSCredits(Setting.MONTHLY_AI_STORAGE_COST.intValue());

        if (!adminTab) {
            String days = academy.getOfficerTrainingDays() + " days";

            if (own) tooltip.addPara("Training is free and takes %s per personality step.", 0f, hl, days);
            else tooltip.addPara("Training costs %s and takes %s per personality step.", 0f, hl, Misc.getDGSCredits(academy.getOfficerTrainingFee(1)), days);
            String steps = Academy.getStepCountText(academy.getMaxOfficerSteps());
            if (academy.hasAlphaCore()) tooltip.addPara("Up to %s per officer.", PAD, hl, steps);
            else tooltip.addPara("%s per officer, %s with an alpha core.", PAD, hl, Misc.ucFirst(steps), Academy.getStepCountText(academy.getMaxOfficerSteps(true)));

            if (academy.hasHolobench()) tooltip.addPara("Holobench: officers leave training %s. Stored officers gain %s experience per month.", PAD, hl, "mentored", Misc.getWithDGS(SpecialItemEffectsRepo.HOLOBENCH_MONTHLY_XP));

            if (own) tooltip.addPara("Stored officers draw their full salary.", PAD);
            else tooltip.addPara("Storage costs %s per month for each officer, plus full salary.", PAD, hl, storageFee);
        } else {
            String days = academy.getAdminTrainingDays() + " days";

            if (own) tooltip.addPara("Training is free and takes %s per skill.", 0f, hl, days);
            else tooltip.addPara("Training costs %s and takes %s per skill.", 0f, hl, Misc.getDGSCredits(academy.getAdminTrainingFee()), days);

            if (own) tooltip.addPara("Stored administrators draw %s of their salary.", PAD, hl, "10%");
            else tooltip.addPara("Storage costs %s per month for each administrator, plus %s of salary.", PAD, hl, storageFee, "10%");
        }
    }

    private CustomPanelAPI createTrainingSlot(float w) {
        Color base = Misc.getButtonTextColor();
        Color hl = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();

        CustomPanelAPI slot = panel.createCustomPanel(w, SLOT_H, new ButtonReportingCustomPanel(this, base, -1));
        PersonAPI person = adminTab ? academy.getAdminInTraining() : academy.getOfficerInTraining();

        if (person == null) {
            TooltipMakerAPI empty = slot.createUIElement(w, 20f, false);
            LabelAPI label = empty.addPara(adminTab ? "No administrator in training." : "No officer in training.", gray, 0f);
            label.setAlignment(Alignment.MID);
            slot.addUIElement(empty).inTL(-TOOLTIP_INSET, SLOT_H / 2f - 10f);
            return slot;
        }

        TooltipMakerAPI portrait = slot.createUIElement(PORTRAIT, PORTRAIT, false);
        portrait.addImage(person.getPortraitSprite(), PORTRAIT, PORTRAIT, 0f);
        slot.addUIElement(portrait).inTL(PAD - TOOLTIP_INSET, (SLOT_H - PORTRAIT) / 2f);

        float infoX = PAD * 2 + PORTRAIT;
        TooltipMakerAPI info = slot.createUIElement(w - infoX - BUTTON_W * 2 - PAD * 3, SLOT_H - PAD, false);
        int total;
        int left;

        if (!adminTab) {
            String from = person.getPersonalityAPI().getId();
            String to = academy.getOfficerTrainingTarget();
            int steps = academy.getCurrentOfficerTrainingSteps();
            info.addPara("%s, level %s", 0f, new Color[]{base, hl}, person.getNameString(), person.getStats().getLevel() + "");
            info.addPara("%s to %s, %s", 3f, new Color[]{getPersonalityColor(from), getPersonalityColor(to), hl},
                    getPersonalityName(from), getPersonalityName(to), steps + (steps == 1 ? " step" : " steps"));
            total = academy.getCurrentOfficerTrainingDays();
            left = academy.getRemainingOfficerTrainingDays();
        } else {
            info.addPara("%s, skills %s of 2", 0f, new Color[]{base, hl}, person.getNameString(), Academy.getAdminSkillCount(person) + "");
            info.addPara("Learning one new administrator skill", gray, 3f);
            total = academy.getAdminTrainingDays();
            left = academy.getRemainingAdminTrainingDays();
        }

        info.addCustom(createProgressBar(Math.max(0f, 1f - left / (float) Math.max(1, total))), 5f);
        String paused = getPausedReason();
        if (paused != null) info.addPara("%s of %s days left. Paused: %s", 3f, new Color[]{hl, hl, Misc.getNegativeHighlightColor()}, Math.max(0, left) + "", total + "", paused);
        else info.addPara("%s of %s days left", 3f, gray, hl, Math.max(0, left) + "", total + "");
        slot.addUIElement(info).inTL(infoX - TOOLTIP_INSET, PAD / 2f);

        String fleetBlocker = adminTab ? Academy.getAdminSlotBlocker() : Academy.getOfficerSlotBlocker();
        TooltipMakerAPI actions = slot.createUIElement(BUTTON_W, BUTTON_H * 2 + 8f, false);
        addButton(actions, "Abort to fleet", ABORT_BG, Color.WHITE, fleetBlocker, "Cancelling forfeits the fee and training progress.", 0f, new ButtonAction(this) {
            @Override
            public void execute() {
                if (adminTab) academy.abortAdminTraining(false);
                else academy.abortOfficerTraining(false);
                update();
            }
        });
        addButton(actions, "Abort to storage", ABORT_BG, Color.WHITE, null, "Cancelling forfeits the fee and training progress.", 8f, new ButtonAction(this) {
            @Override
            public void execute() {
                if (adminTab) academy.abortAdminTraining(true);
                else academy.abortOfficerTraining(true);
                update();
            }
        });
        slot.addUIElement(actions).inTR(PAD + TOOLTIP_INSET, (SLOT_H - BUTTON_H * 2 - 8f) / 2f);

        final RelicSpeedUpTarget speedUp = academy.getTrainingSpeedUp(adminTab);
        TooltipMakerAPI relic = slot.createUIElement(BUTTON_W, BUTTON_H, false);
        ButtonAPI quick = relic.addButton(speedUp.getSpeedUpLabel(), new ButtonAction(this) {
            @Override
            public void execute() {
                openPopup(() -> plugin.showRelicDialog(speedUp));
            }
        }, base, Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, BUTTON_W, BUTTON_H, 0f);
        quick.setEnabled(RelicSpeedUpDialog.isAvailable(speedUp));
        relic.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                RelicSpeedUpDialog.addTooltip(tooltip, speedUp);
            }
        }, TooltipMakerAPI.TooltipLocation.LEFT);
        slot.addUIElement(relic).inTR(PAD * 2 + BUTTON_W + TOOLTIP_INSET, (SLOT_H - BUTTON_H * 2 - 8f) / 2f);

        return slot;
    }

    private void openPopup(Runnable show) {
        if (externalScroller != null) scrollY = externalScroller.getYOffset();
        switchingToPopup = true;
        callback.dismissCustomDialog(1);
        show.run();
    }

    private String getPausedReason() {
        if (!academy.isFunctional()) return "the Academy is not functional";
        if (academy.getMarket().getFaction().isHostileTo(Global.getSector().getPlayerFaction())) return "the market is hostile to you";
        return null;
    }

    private CustomPanelAPI createProgressBar(final float progress) {
        final Color base = Misc.getButtonTextColor();
        final Color dark = Misc.getDarkPlayerColor();

        return panel.createCustomPanel(BAR_W, 8f, new BaseCustomUIPanelPlugin() {
            private PositionAPI pos;

            @Override
            public void positionChanged(PositionAPI position) {
                pos = position;
            }

            @Override
            public void render(float alphaMult) {
                if (pos == null) return;
                Misc.renderQuad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), dark, alphaMult);
                Misc.renderQuad(pos.getX(), pos.getY(), pos.getWidth() * progress, pos.getHeight(), base, alphaMult);
            }
        });
    }

    private void addRows(TooltipMakerAPI list, float rowW) {
        List<PersonAPI> active = adminTab ? academy.getStaffAdmins() : academy.getFleetOfficers();
        List<PersonAPI> stored = new ArrayList<>(adminTab ? academy.getAdminStorage() : academy.getOfficerStorage());

        Comparator<PersonAPI> order = adminTab
                ? Comparator.comparingInt((PersonAPI p) -> Academy.getAdminSkillCount(p)).reversed()
                : Comparator.comparingInt((PersonAPI p) -> p.getStats().getLevel()).reversed();
        active.sort(order);
        stored.sort(order);

        if (active.isEmpty() && stored.isEmpty()) {
            list.addPara(adminTab ? "You have no administrators, and none are stored here." : "You have no officers, and none are stored here.", Misc.getGrayColor(), PAD);
            return;
        }

        addGroup(list, adminTab ? "On staff" : "In fleet", active, false, rowW);
        addGroup(list, "Stored here", stored, true, rowW);
    }

    private void addGroup(TooltipMakerAPI list, String label, List<PersonAPI> people, boolean stored, float rowW) {
        if (people.isEmpty()) return;

        CustomPanelAPI heading = panel.createCustomPanel(rowW, CARD_HEADER_H, null);
        addHeading(heading, label, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), 0f, 0f, rowW);
        list.addCustom(heading, list.getHeightSoFar() > 0f ? PAD : 0f);

        float cardW = (rowW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        for (int i = 0; i < people.size(); i += COLUMNS) {
            CustomPanelAPI row = panel.createCustomPanel(rowW, CARD_H, null);
            for (int column = 0; column < COLUMNS && i + column < people.size(); column++) {
                row.addComponent(createCard(people.get(i + column), stored, cardW)).inTL(column * (cardW + CARD_GAP), 0f);
            }
            list.addCustom(row, i == 0 ? SPAD : CARD_GAP);
        }
    }

    private CustomPanelAPI createCard(final PersonAPI person, final boolean stored, float cardW) {
        Color base = Misc.getButtonTextColor();

        CustomPanelAPI card = panel.createCustomPanel(cardW, CARD_H, new ButtonReportingCustomPanel(this, base, -1));

        addHeading(card, person.getNameString(), stored ? Misc.getTextColor() : base, stored ? STORED_BG : Misc.getDarkPlayerColor(), 0f, 0f, cardW);

        float y = CARD_HEADER_H + CARD_PAD;
        addAssignment(card, person, stored, CARD_PAD, y);

        float portraitX = CARD_PAD + BOX + BOX_GAP;
        TooltipMakerAPI portrait = card.createUIElement(PORTRAIT, PORTRAIT, false);
        portrait.addImage(person.getPortraitSprite(), PORTRAIT, PORTRAIT, 0f);
        card.addUIElement(portrait).inTL(portraitX - TOOLTIP_INSET, y);

        float infoX = portraitX + PORTRAIT + CARD_PAD;
        float infoW = cardW - infoX - CARD_PAD;
        TooltipMakerAPI info = card.createUIElement(infoW, PORTRAIT + 4f, false);
        if (adminTab) addAdminInfo(info, person, stored);
        else addOfficerInfo(info, person);
        card.addUIElement(info).inTL(infoX - TOOLTIP_INSET, y - 2f);

        card.addComponent(createSkillIcons(person, cardW - CARD_PAD * 2)).inTL(CARD_PAD, CARD_SKILLS_Y);

        float buttonW = (cardW - CARD_PAD * 3) / 2f;
        if (adminTab) addAdminButtons(card, person, stored, buttonW);
        else addOfficerButtons(card, person, stored, buttonW);

        return card;
    }

    private void addAssignment(CustomPanelAPI row, PersonAPI person, boolean stored, float x, float y) {
        Color base = Misc.getButtonTextColor();
        float iconSize = BOX - 8f;

        CustomPanelAPI box = row.createCustomPanel(BOX, BOX, new ButtonReportingCustomPanel(this, base, -1));
        TooltipMakerAPI icon = box.createUIElement(iconSize, iconSize, false);
        String name = null;

        if (!stored && !adminTab) {
            FleetMemberAPI ship = Academy.getCommandedShip(person);
            if (ship != null) {
                icon.addShipList(1, 1, iconSize, base, Collections.singletonList(ship), 0f);
                name = ship.getShipName();
            }
        } else if (!stored) {
            MarketAPI governed = Academy.getGovernedMarket(person);
            if (governed != null) {
                if (governed.getPlanetEntity() != null) icon.showPlanetInfo(governed.getPlanetEntity(), iconSize, iconSize, false, 0f);
                else icon.addImage(governed.getFaction().getCrest(), iconSize, iconSize, 0f);
                name = governed.getName();
            }
        }

        row.addComponent(box).inTL(x, y);
        float labelW = BOX + PAD;

        if (name == null) {
            TooltipMakerAPI text = row.createUIElement(labelW, BOX_LABEL_H, false);
            text.setParaFont(Fonts.VICTOR_10);
            text.addPara("Unassigned", Misc.getGrayColor(), 0f).setAlignment(Alignment.MID);
            row.addUIElement(text).inTL(x - PAD / 2f - TOOLTIP_INSET, y + (BOX - BOX_LABEL_H) / 2f);
            return;
        }

        box.addUIElement(icon).inTL((BOX - iconSize) / 2f - TOOLTIP_INSET, (BOX - iconSize) / 2f);

        TooltipMakerAPI label = row.createUIElement(labelW, BOX_LABEL_H, false);
        label.setParaFont(Fonts.VICTOR_10);
        label.addPara(fitToWidth(name, labelW - SPAD, Fonts.VICTOR_10), base, 0f).setAlignment(Alignment.MID);
        row.addUIElement(label).inTL(x - PAD / 2f - TOOLTIP_INSET, y + BOX + 3f);
    }

    private static String fitToWidth(String text, float width, String font) {
        if (Global.getSettings().computeStringWidth(text, font) <= width) return text;

        String cut = text;
        while (cut.length() > 1 && Global.getSettings().computeStringWidth(cut + "...", font) > width) {
            cut = cut.substring(0, cut.length() - 1);
        }

        return cut.trim() + "...";
    }

    private void addOfficerInfo(TooltipMakerAPI info, PersonAPI person) {
        Color hl = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();
        String personality = person.getPersonalityAPI().getId();
        int used = academy.getTrainedSteps(person);

        info.addPara("Level %s", 0f, hl, person.getStats().getLevel() + "").setAlignment(Alignment.RMID);
        info.addPara(getPersonalityName(personality), getPersonalityColor(personality), LINE_PAD).setAlignment(Alignment.RMID);
        info.addPara("Steps %s of %s", LINE_PAD, gray, hl, used + "", Math.max(used, academy.getMaxOfficerSteps()) + "").setAlignment(Alignment.RMID);
        info.addPara("Salary %s", LINE_PAD, gray, hl, Misc.getDGSCredits(Misc.getOfficerSalary(person))).setAlignment(Alignment.RMID);
    }

    private void addAdminInfo(TooltipMakerAPI info, PersonAPI person, boolean stored) {
        Color hl = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();
        float salary = Misc.getAdminSalary(person) * (stored ? 0.1f : 1f);

        info.addPara("Skills %s of 2", 0f, hl, Academy.getAdminSkillCount(person) + "").setAlignment(Alignment.RMID);
        info.addPara("Salary %s", LINE_PAD, gray, hl, Misc.getDGSCredits(salary)).setAlignment(Alignment.RMID);
        if (stored) info.addPara("while stored", gray, LINE_PAD).setAlignment(Alignment.RMID);
    }

    private CustomPanelAPI createSkillIcons(PersonAPI person, float w) {
        CustomPanelAPI icons = panel.createCustomPanel(w, ICON, null);
        float x = 0f;

        for (String skillId : Global.getSettings().getSortedSkillIds()) {
            int level = (int) person.getStats().getSkillLevel(skillId);
            if (level <= 0) continue;

            final SkillSpecAPI spec = Global.getSettings().getSkillSpec(skillId);
            if (adminTab ? !spec.isAdminSkill() : !spec.isCombatOfficerSkill()) continue;
            if (x + ICON > w) break;

            final boolean elite = level > 1 && !adminTab;
            CustomPanelAPI frame = icons.createCustomPanel(ICON, ICON, new ButtonReportingCustomPanel(this, elite ? Misc.getHighlightColor() : null, -1));
            TooltipMakerAPI image = frame.createUIElement(ICON, ICON, false);
            image.addImage(spec.getSpriteName(), ICON, ICON, 0f);
            image.addTooltipToPrevious(new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 250f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara(spec.getName() + (elite ? " (elite)" : ""), elite ? Misc.getHighlightColor() : Misc.getTextColor(), 0f);
                }
            }, TooltipMakerAPI.TooltipLocation.ABOVE);
            frame.addUIElement(image).inTL(-TOOLTIP_INSET, 0f);
            icons.addComponent(frame).inTL(x, 0f);
            x += ICON + 4f;
        }

        if (x == 0f) {
            TooltipMakerAPI none = icons.createUIElement(w, ICON, false);
            none.addPara(adminTab ? "No administrator skills" : "No skills", Misc.getGrayColor(), 5f);
            icons.addUIElement(none).inTL(-TOOLTIP_INSET, 0f);
        }

        return icons;
    }

    private void addOfficerButtons(CustomPanelAPI card, final PersonAPI person, final boolean stored, float buttonW) {
        Color base = Misc.getButtonTextColor();
        Color dark = Misc.getDarkPlayerColor();

        String trainBlocker = academy.getOfficerTrainBlocker(person);
        addCardButton(card, 0, buttonW, "Train", TRAIN_BG, base, trainBlocker, getOfficerTrainTooltip(person), new ButtonAction(this) {
            @Override
            public void execute() {
                openPopup(() -> plugin.showTrainingPopup(person));
            }
        });

        if (stored) {
            addCardButton(card, 1, buttonW, "Retrieve", dark, base, Academy.getOfficerSlotBlocker(), null, new ButtonAction(this) {
                @Override
                public void execute() {
                    academy.returnOfficer(person);
                    update();
                }
            });
        } else {
            addCardButton(card, 1, buttonW, "Store", dark, base, null, getStoreTooltip(person), new ButtonAction(this) {
                @Override
                public void execute() {
                    academy.storeOfficer(person);
                    update();
                }
            });
        }
    }

    private void addAdminButtons(CustomPanelAPI card, final PersonAPI person, final boolean stored, float buttonW) {
        Color base = Misc.getButtonTextColor();
        Color dark = Misc.getDarkPlayerColor();

        String trainTooltip = "Adds one administrator skill. " + academy.getAdminTrainingDays() + " days"
                + (academy.getAdminTrainingFee() > 0 ? ", " + Misc.getDGSCredits(academy.getAdminTrainingFee()) + "." : ".");
        addCardButton(card, 0, buttonW, "Train", TRAIN_BG, base, academy.getAdminTrainBlocker(person), trainTooltip, new ButtonAction(this) {
            @Override
            public void execute() {
                openPopup(() -> plugin.showAdminTraining(person));
            }
        });

        if (stored) {
            addCardButton(card, 1, buttonW, "Retrieve", dark, base, Academy.getAdminSlotBlocker(), null, new ButtonAction(this) {
                @Override
                public void execute() {
                    academy.returnAdmin(person);
                    update();
                }
            });
        } else {
            addCardButton(card, 1, buttonW, "Store", dark, base, null, getStoreTooltip(person), new ButtonAction(this) {
                @Override
                public void execute() {
                    academy.storeAdmin(person);
                    update();
                }
            });
        }
    }

    private String getOfficerTrainTooltip(PersonAPI person) {
        String current = person.getPersonalityAPI().getId();
        Map<Integer, List<String>> namesBySteps = new TreeMap<>();

        for (String id : academy.getReachablePersonalities(person)) {
            int steps = Academy.getPersonalitySteps(current, id);
            if (!namesBySteps.containsKey(steps)) namesBySteps.put(steps, new ArrayList<String>());
            namesBySteps.get(steps).add(getPersonalityName(id));
        }

        StringBuilder text = new StringBuilder();
        for (Map.Entry<Integer, List<String>> entry : namesBySteps.entrySet()) {
            int steps = entry.getKey();
            float fee = academy.getOfficerTrainingFee(steps);

            if (text.length() > 0) text.append("\n");
            text.append(Misc.getJoined("or", entry.getValue())).append(": ")
                    .append(steps).append(steps == 1 ? " step, " : " steps, ")
                    .append(academy.getOfficerTrainingDays(steps)).append(" days")
                    .append(fee > 0 ? ", " + Misc.getDGSCredits(fee) + "." : ".");
        }

        return text.toString();
    }

    private String getStoreTooltip(PersonAPI person) {
        boolean own = academy.getMarket().isPlayerOwned();
        String fee = Misc.getDGSCredits(Setting.MONTHLY_AI_STORAGE_COST.intValue());
        String text;

        if (!adminTab) text = own ? "Stored officers draw their full salary." : "Storage costs " + fee + " per month plus full salary.";
        else text = own ? "Stored administrators draw 10% of their salary." : "Storage costs " + fee + " per month plus 10% of salary.";

        MarketAPI governed = adminTab ? Academy.getGovernedMarket(person) : null;
        if (governed != null) text += "\nYou take over as governor of " + governed.getName() + ".";

        return text;
    }

    private void addCardButton(CustomPanelAPI card, int column, float buttonW, String label, Color bg, Color text, String blocker, String tooltip, ButtonAction action) {
        TooltipMakerAPI element = card.createUIElement(buttonW, BUTTON_H, false);
        addButton(element, label, bg, text, blocker, tooltip, buttonW, 0f, TooltipMakerAPI.TooltipLocation.ABOVE, action);
        card.addUIElement(element).inTL(CARD_PAD + column * (buttonW + CARD_PAD) - TOOLTIP_INSET, CARD_BUTTONS_Y);
    }

    private void addButton(TooltipMakerAPI actions, String label, Color bg, Color text, String blocker, String tooltip, float pad, ButtonAction action) {
        addButton(actions, label, bg, text, blocker, tooltip, BUTTON_W, pad, TooltipMakerAPI.TooltipLocation.LEFT, action);
    }

    private void addButton(TooltipMakerAPI actions, String label, Color bg, Color text, final String blocker, final String tooltip, float width, float pad,
                           TooltipMakerAPI.TooltipLocation location, ButtonAction action) {
        ButtonAPI button = actions.addButton(label, action, text, bg, Alignment.MID, CutStyle.ALL, width, BUTTON_H, pad);
        button.setEnabled(blocker == null);

        final String tooltipText = blocker != null ? blocker : tooltip;
        if (tooltipText == null || tooltipText.isEmpty()) return;

        actions.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(tooltipText, blocker != null ? Misc.getNegativeHighlightColor() : Misc.getTextColor(), 0f);
            }
        }, location);
    }

    // raw COLOURS_BY_PERSONALITY blue and red are too dark on the panel background
    public static Color getPersonalityColor(String id) {
        Color color = Academy.COLOURS_BY_PERSONALITY.get(id);
        return color != null ? Misc.interpolateColor(color, Color.WHITE, 0.4f) : Misc.getTextColor();
    }

    static void addHeading(CustomPanelAPI parent, String text, Color textColor, Color bgColor, float x, float y, float width) {
        TooltipMakerAPI element = parent.createUIElement(width, CARD_HEADER_H, false);
        element.addSectionHeading(text, textColor, bgColor, Alignment.MID, width - HEADING_EXTRA, 0f);
        parent.addUIElement(element).inTL(x, y);
    }

    public static String getPersonalityName(String id) {
        return Global.getSettings().getPersonaltySpec(id).getDisplayName();
    }
}
