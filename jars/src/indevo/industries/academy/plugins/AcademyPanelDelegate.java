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
import indevo.industries.academy.industry.Academy;

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
    private static final float BOX_W = 110f;
    private static final float PORTRAIT = 64f;
    private static final float ICON = 28f;
    private static final float SLOT_H = 86f;
    private static final float ROW_HEADER_H = 20f;
    private static final float ROW_H = ROW_HEADER_H + 84f;
    private static final float BAR_W = 320f;

    private static final Color ABORT_BG = new Color(80, 20, 10, 255);
    private static final Color STORED_BG = new Color(60, 60, 60, 255);

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
    public static float getHeight() {
        return Math.min(850f, Global.getSettings().getScreenHeight() - 100f) - 55f;
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

    private void recreatePanel() {
        if (externalScroller != null) scrollY = externalScroller.getYOffset();
        if (panel != null) basePanel.removeComponent(panel);

        float w = basePanel.getPosition().getWidth();
        float h = basePanel.getPosition().getHeight();
        panel = Global.getSettings().createCustom(w, h, null);

        TooltipMakerAPI main = panel.createUIElement(w, h, false);
        main.addSectionHeading(academy.getCurrentName() + " - " + academy.getMarket().getName(), Alignment.MID, 0f);
        main.addCustom(createTabs(w), PAD);
        addFeesLine(main);

        main.addSectionHeading("In training", Alignment.MID, PAD);
        main.addCustom(createTrainingSlot(w), SPAD);

        main.addSectionHeading(adminTab ? "Administrators" : "Officers", Alignment.MID, PAD);
        float listH = h - main.getHeightSoFar() - SPAD * 2;

        CustomPanelAPI listPanel = panel.createCustomPanel(w, listH, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI list = listPanel.createUIElement(w, listH, true);
        addRows(list, w - 20f);
        listPanel.addUIElement(list).inTL(0f, 0f);
        main.addCustom(listPanel, SPAD);

        panel.addUIElement(main).inTL(0f, 0f);
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
        tabs.addUIElement(officerTab).inTL(0f, 0f);

        int maxAdmins = fleet.getCommander().getStats().getAdminNumber().getModifiedInt();
        String adminLabel = "Administrators " + Global.getSector().getCharacterData().getAdmins().size() + " / " + maxAdmins;
        TooltipMakerAPI adminTabElement = tabs.createUIElement(TAB_W, TAB_H, false);
        adminTabElement.addAreaCheckbox(adminLabel, new ButtonAction(this) {
            @Override
            public void execute() {
                switchTab(true);
            }
        }, base, dark, bright, TAB_W, TAB_H, 0f).setChecked(adminTab);
        tabs.addUIElement(adminTabElement).inTL(TAB_W + PAD, 0f);

        TooltipMakerAPI credits = tabs.createUIElement(300f, TAB_H, false);
        LabelAPI label = credits.addPara("Credits: %s", 0f, Misc.getHighlightColor(), Misc.getDGSCredits(fleet.getCargo().getCredits().get()));
        label.setAlignment(Alignment.RMID);
        tabs.addUIElement(credits).inTR(0f, 5f);

        return tabs;
    }

    private void switchTab(boolean admin) {
        if (adminTab != admin) {
            adminTab = admin;
            externalScroller = null;
            scrollY = 0f;
        }

        recreatePanel();
    }

    private void addFeesLine(TooltipMakerAPI main) {
        Color gray = Misc.getGrayColor();
        Color hl = Misc.getHighlightColor();
        boolean own = academy.getMarket().isPlayerOwned();
        String storageFee = Misc.getDGSCredits(Setting.MONTHLY_AI_STORAGE_COST.intValue());

        if (!adminTab) {
            String days = academy.getOfficerTrainingDays() + "";
            String steps = academy.hasAlphaCore() ? "Alpha core installed: up to two personality steps per officer." : "One personality step per officer, two with an alpha core.";

            if (own) main.addPara("Training is free and takes %s days per personality step. " + steps + " Stored officers draw their full salary.", PAD, gray, hl, days);
            else main.addPara("Training costs %s and takes %s days per personality step. " + steps + " Storage costs %s per officer and month, plus full salary.", PAD, gray, hl,
                    Misc.getDGSCredits(academy.getOfficerTrainingFee(1)), days, storageFee);
        } else {
            String days = academy.getAdminTrainingDays() + "";

            if (own) main.addPara("Training is free and takes %s days per skill. Stored administrators draw %s of their salary.", PAD, gray, hl, days, "10%");
            else main.addPara("Training costs %s and takes %s days per skill. Storage costs %s per administrator and month, plus %s of salary.", PAD, gray, hl,
                    Misc.getDGSCredits(academy.getAdminTrainingFee()), days, storageFee, "10%");
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
            LabelAPI label = empty.addPara(adminTab ? "No administrator in training. Pick one below." : "No officer in training. Pick one below.", gray, 0f);
            label.setAlignment(Alignment.MID);
            slot.addUIElement(empty).inTL(0f, SLOT_H / 2f - 10f);
            return slot;
        }

        TooltipMakerAPI portrait = slot.createUIElement(PORTRAIT, PORTRAIT, false);
        portrait.addImage(person.getPortraitSprite(), PORTRAIT, PORTRAIT, 0f);
        slot.addUIElement(portrait).inTL(PAD, (SLOT_H - PORTRAIT) / 2f);

        float infoX = PAD * 2 + PORTRAIT;
        TooltipMakerAPI info = slot.createUIElement(w - infoX - BUTTON_W - PAD * 2, SLOT_H - PAD, false);
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
        slot.addUIElement(info).inTL(infoX, PAD / 2f);

        String fleetBlocker = adminTab ? Academy.getAdminSlotBlocker() : Academy.getOfficerSlotBlocker();
        TooltipMakerAPI actions = slot.createUIElement(BUTTON_W, BUTTON_H * 2 + 8f, false);
        addButton(actions, "Abort to fleet", ABORT_BG, Color.WHITE, fleetBlocker, "No refund, progress is lost.", 0f, new ButtonAction(this) {
            @Override
            public void execute() {
                if (adminTab) academy.abortAdminTraining(false);
                else academy.abortOfficerTraining(false);
                recreatePanel();
            }
        });
        addButton(actions, "Abort to storage", ABORT_BG, Color.WHITE, null, "No refund, progress is lost.", 8f, new ButtonAction(this) {
            @Override
            public void execute() {
                if (adminTab) academy.abortAdminTraining(true);
                else academy.abortOfficerTraining(true);
                recreatePanel();
            }
        });
        slot.addUIElement(actions).inTR(PAD, (SLOT_H - BUTTON_H * 2 - 8f) / 2f);

        return slot;
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

        for (PersonAPI person : active) list.addCustom(createRow(person, false, rowW), SPAD);
        for (PersonAPI person : stored) list.addCustom(createRow(person, true, rowW), SPAD);

        if (active.isEmpty() && stored.isEmpty()) {
            list.addPara(adminTab ? "You have no administrators, and none are stored here." : "You have no officers, and none are stored here.", Misc.getGrayColor(), PAD);
        }
    }

    private CustomPanelAPI createRow(final PersonAPI person, final boolean stored, float rowW) {
        Color base = Misc.getButtonTextColor();
        Color dark = Misc.getDarkPlayerColor();

        CustomPanelAPI row = panel.createCustomPanel(rowW, ROW_H, new ButtonReportingCustomPanel(this, base, -1));

        String where = stored ? "Stored here" : adminTab ? "On staff" : "In fleet";
        TooltipMakerAPI header = row.createUIElement(rowW, ROW_HEADER_H, false);
        header.addSectionHeading(person.getNameString() + " - " + where, stored ? Misc.getTextColor() : base, stored ? STORED_BG : dark, Alignment.MID, 0f);
        row.addUIElement(header).inTL(0f, 0f);

        float y = ROW_HEADER_H + PAD;
        row.addComponent(createAssignmentBox(person, stored)).inTL(PAD, y);

        TooltipMakerAPI portrait = row.createUIElement(PORTRAIT, PORTRAIT, false);
        portrait.addImage(person.getPortraitSprite(), PORTRAIT, PORTRAIT, 0f);
        row.addUIElement(portrait).inTL(PAD * 2 + BOX_W, y);

        float infoX = PAD * 3 + BOX_W + PORTRAIT;
        float infoW = rowW - infoX - BUTTON_W - PAD * 2;
        TooltipMakerAPI info = row.createUIElement(infoW, PORTRAIT + PAD, false);
        if (adminTab) addAdminInfo(info, person, stored);
        else addOfficerInfo(info, person);
        info.addCustom(createSkillIcons(person, infoW), 4f);
        row.addUIElement(info).inTL(infoX, y - 2f);

        TooltipMakerAPI actions = row.createUIElement(BUTTON_W, BUTTON_H * 2 + 8f, false);
        if (adminTab) addAdminButtons(actions, person, stored);
        else addOfficerButtons(actions, person, stored);
        row.addUIElement(actions).inTR(PAD, y + 4f);

        return row;
    }

    private CustomPanelAPI createAssignmentBox(PersonAPI person, boolean stored) {
        Color base = Misc.getButtonTextColor();
        float iconSize = 40f;

        CustomPanelAPI box = panel.createCustomPanel(BOX_W, PORTRAIT, new ButtonReportingCustomPanel(this, base, -1));
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

        TooltipMakerAPI text = box.createUIElement(BOX_W, 18f, false);
        text.setParaSmallInsignia();

        if (name == null) {
            text.addPara("Unassigned", Misc.getGrayColor(), 0f).setAlignment(Alignment.MID);
            box.addUIElement(text).inTL(0f, PORTRAIT / 2f - 9f);
            return box;
        }

        box.addUIElement(icon).inTL((BOX_W - iconSize) / 2f, 3f);
        text.addPara(name, base, 0f).setAlignment(Alignment.MID);
        box.addUIElement(text).inTL(0f, iconSize + 5f);
        return box;
    }

    private void addOfficerInfo(TooltipMakerAPI info, PersonAPI person) {
        Color hl = Misc.getHighlightColor();
        String personality = person.getPersonalityAPI().getId();
        int used = academy.getTrainedSteps(person);

        info.addPara("Level %s, %s", 0f, new Color[]{hl, getPersonalityColor(personality)}, person.getStats().getLevel() + "", getPersonalityName(personality));
        info.addPara("Training steps used %s of %s, salary %s per month", 3f, Misc.getGrayColor(), hl,
                used + "", Math.max(used, academy.getMaxOfficerSteps()) + "", Misc.getDGSCredits(Misc.getOfficerSalary(person)));
    }

    private void addAdminInfo(TooltipMakerAPI info, PersonAPI person, boolean stored) {
        Color hl = Misc.getHighlightColor();
        float salary = Misc.getAdminSalary(person) * (stored ? 0.1f : 1f);

        info.addPara("Skills %s of 2", 0f, hl, Academy.getAdminSkillCount(person) + "");
        info.addPara("Salary %s per month" + (stored ? " while stored" : ""), 3f, Misc.getGrayColor(), hl, Misc.getDGSCredits(salary));
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
            frame.addUIElement(image).inTL(0f, 0f);
            icons.addComponent(frame).inTL(x, 0f);
            x += ICON + 4f;
        }

        if (x == 0f) {
            TooltipMakerAPI none = icons.createUIElement(w, ICON, false);
            none.addPara(adminTab ? "No administrator skills" : "No skills", Misc.getGrayColor(), 5f);
            icons.addUIElement(none).inTL(0f, 0f);
        }

        return icons;
    }

    private void addOfficerButtons(TooltipMakerAPI actions, final PersonAPI person, final boolean stored) {
        Color base = Misc.getButtonTextColor();
        Color dark = Misc.getDarkPlayerColor();

        String trainBlocker = academy.getOfficerTrainBlocker(person);
        addButton(actions, "Train", dark, base, trainBlocker, getOfficerTrainTooltip(person), 0f, new ButtonAction(this) {
            @Override
            public void execute() {
                if (externalScroller != null) scrollY = externalScroller.getYOffset();
                switchingToPopup = true;
                callback.dismissCustomDialog(1);
                plugin.showTrainingPopup(person);
            }
        });

        if (stored) {
            addButton(actions, "Retrieve", dark, base, Academy.getOfficerSlotBlocker(), "Returns to your fleet.", 8f, new ButtonAction(this) {
                @Override
                public void execute() {
                    academy.returnOfficer(person);
                    recreatePanel();
                }
            });
        } else {
            addButton(actions, "Store", dark, base, null, getStoreTooltip(person), 8f, new ButtonAction(this) {
                @Override
                public void execute() {
                    academy.storeOfficer(person);
                    recreatePanel();
                }
            });
        }
    }

    private void addAdminButtons(TooltipMakerAPI actions, final PersonAPI person, final boolean stored) {
        Color base = Misc.getButtonTextColor();
        Color dark = Misc.getDarkPlayerColor();

        String trainTooltip = "Adds one administrator skill. " + academy.getAdminTrainingDays() + " days"
                + (academy.getAdminTrainingFee() > 0 ? ", " + Misc.getDGSCredits(academy.getAdminTrainingFee()) + "." : ".");
        addButton(actions, "Train", dark, base, academy.getAdminTrainBlocker(person), trainTooltip, 0f, new ButtonAction(this) {
            @Override
            public void execute() {
                academy.setAdminInTraining(person);
                recreatePanel();
            }
        });

        if (stored) {
            addButton(actions, "Retrieve", dark, base, Academy.getAdminSlotBlocker(), "Returns to your staff.", 8f, new ButtonAction(this) {
                @Override
                public void execute() {
                    academy.returnAdmin(person);
                    recreatePanel();
                }
            });
        } else {
            addButton(actions, "Store", dark, base, null, getStoreTooltip(person), 8f, new ButtonAction(this) {
                @Override
                public void execute() {
                    academy.storeAdmin(person);
                    recreatePanel();
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

    private void addButton(TooltipMakerAPI actions, String label, Color bg, Color text, final String blocker, final String tooltip, float pad, ButtonAction action) {
        ButtonAPI button = actions.addButton(label, action, text, bg, Alignment.MID, CutStyle.ALL, BUTTON_W, BUTTON_H, pad);
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
        }, TooltipMakerAPI.TooltipLocation.LEFT);
    }

    public static Color getPersonalityColor(String id) {
        Color color = Academy.COLOURS_BY_PERSONALITY.get(id);
        return color != null ? color : Misc.getTextColor();
    }

    public static String getPersonalityName(String id) {
        return Global.getSettings().getPersonaltySpec(id).getDisplayName();
    }
}
