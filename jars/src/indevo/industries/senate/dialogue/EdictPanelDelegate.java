package indevo.industries.senate.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.dialogue.sidepanel.HelpIcon;
import indevo.ids.Ids;
import indevo.industries.senate.EdictHelper;
import indevo.industries.senate.EdictRepo;
import indevo.industries.senate.EdictRequirement;
import indevo.industries.senate.conditions.EdictAPI;
import indevo.utils.helper.RelicHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;

public class EdictPanelDelegate extends ButtonReportingDialogueDelegate {

    // outer dialog adds 20 x 55, so it never exceeds the vanilla cargo tab (1004 x 850)
    public static final float MAX_WIDTH = 984f;
    public static final float MAX_HEIGHT = 795f;

    private static final float OPAD = 10f;
    private static final float PAD = 3f;
    private static final float GAP = 10f;
    private static final float COLONY_WIDTH = 260f;
    private static final float EDICT_WIDTH = 280f;
    private static final float HEADER_HEIGHT = 50f;
    private static final float FOOTER_HEIGHT = 24f;
    private static final float HEADING_HEIGHT = 20f;
    private static final float ACTION_HEIGHT = 84f;
    private static final float ROW_MIN_HEIGHT = 44f;
    // list content sits 5 px in, rows give that back so their button reaches the column edge
    private static final float ROW_INSET = 5f;
    private static final float ROW_TEXT_PAD = 15f;
    private static final float MARK_SIZE = 12f;
    private static final float MARK_GAP = 8f;
    private static final float BUTTON_WIDTH = 150f;
    private static final float BUTTON_HEIGHT = 25f;
    private static final float TOOLTIP_WIDTH = 360f;

    private static final String RELIC_LORE = "Several senators have districts waiting on machinery that nobody makes anymore. A few crates of relic components should settle their objections to an early repeal.";
    private static final float ICON_SIZE = 28f;

    private static final Color ACTIVE_HEADING = new Color(140, 70, 20);
    private static final Color UNMET_HEADING = new Color(51, 51, 51);
    private static final Color REMOVE_COLOUR = new Color(160, 30, 20);

    private final MarketAPI origin;
    private final boolean senateScope;
    private final List<MarketAPI> colonies;
    private final List<MarketConditionSpecAPI> specs = new ArrayList<>();
    private final Map<String, EdictAPI> previews = new LinkedHashMap<>();

    private MarketAPI selectedColony;
    private String selectedEdict;
    private String message;
    private Color messageColor;

    private CustomPanelAPI basePanel;
    private CustomDialogCallback callback;
    private CustomPanelAPI panel;
    private ScrollPanelAPI colonyScroller;
    private ScrollPanelAPI edictScroller;
    private ScrollPanelAPI detailScroller;
    private boolean resetDetailScroll = false;

    public EdictPanelDelegate(MarketAPI origin) {
        this.origin = origin;

        Industry senate = origin.getIndustry(Ids.SENATE);
        senateScope = senate != null && senate.isFunctional();
        colonies = senateScope ? EdictHelper.getColoniesCoveredBy(origin) : new ArrayList<>(List.of(origin));

        // previews read the target colony from their parameters, the dummy never applies anything
        MarketAPI dummy = Global.getFactory().createMarket("IndEvo_edictPreview", "Edict preview", 3);
        for (MarketConditionSpecAPI spec : EdictRepo.getAll()) {
            dummy.addCondition(spec.getId());
            EdictAPI edict = EdictHelper.asEdict(dummy.getCondition(spec.getId()));
            if (edict == null) continue;

            specs.add(spec);
            previews.put(spec.getId(), edict);
        }

        selectColony(colonies.contains(origin) ? origin : colonies.isEmpty() ? null : colonies.get(0));
    }

    public static float getPanelWidth() {
        return Math.min(MAX_WIDTH, Global.getSettings().getScreenWidth() - 40f);
    }

    public static float getPanelHeight() {
        return Math.min(MAX_HEIGHT, Global.getSettings().getScreenHeight() - 300f);
    }

    public static void show(DialogCreatorUI ui, MarketAPI market) {
        ui.showDialog(getPanelWidth(), getPanelHeight(), new EdictPanelDelegate(market));
    }

    public static void show(InteractionDialogAPI dialog, MarketAPI market) {
        dialog.showCustomDialog(getPanelWidth(), getPanelHeight(), new EdictPanelDelegate(market));
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.basePanel = panel;
        this.callback = callback;
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
        float colonyOffset = colonyScroller != null ? colonyScroller.getYOffset() : 0f;
        float edictOffset = edictScroller != null ? edictScroller.getYOffset() : 0f;
        float detailOffset = detailScroller != null && !resetDetailScroll ? detailScroller.getYOffset() : 0f;
        resetDetailScroll = false;

        if (panel != null) basePanel.removeComponent(panel);

        float width = basePanel.getPosition().getWidth();
        float height = basePanel.getPosition().getHeight();
        panel = Global.getSettings().createCustom(width, height, null);

        addHeader(width);

        TooltipMakerAPI colonyList = null;
        TooltipMakerAPI edictList = null;
        TooltipMakerAPI detail = null;

        float columnHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT;

        if (selectedColony != null && selectedEdict != null) {
            float edictX = COLONY_WIDTH + GAP;
            float detailX = edictX + EDICT_WIDTH + GAP;

            colonyList = addColonyColumn(0f, HEADER_HEIGHT, columnHeight, callback);
            edictList = addEdictColumn(edictX, HEADER_HEIGHT, columnHeight, callback);
            detail = addDetailColumn(detailX, HEADER_HEIGHT, width - detailX, columnHeight);
        } else {
            TooltipMakerAPI empty = panel.createUIElement(width, columnHeight, false);
            empty.addPara("No colony is in range of this Senate.", Misc.getGrayColor(), 0f);
            panel.addUIElement(empty).inTL(0f, HEADER_HEIGHT);
        }

        addFooter(width, height);
        basePanel.addComponent(panel);

        colonyScroller = restoreScroll(colonyList, colonyOffset);
        edictScroller = restoreScroll(edictList, edictOffset);
        detailScroller = restoreScroll(detail, detailOffset);
    }

    private ScrollPanelAPI restoreScroll(TooltipMakerAPI element, float offset) {
        if (element == null) return null;

        ScrollPanelAPI scroller = element.getExternalScroller();
        if (scroller != null) scroller.setYOffset(offset);
        return scroller;
    }

    private void addHeader(float width) {
        Color hl = Misc.getHighlightColor();
        TooltipMakerAPI header = panel.createUIElement(width - HelpIcon.SIZE - PAD, HEADER_HEIGHT, false);
        header.addSectionHeading("Edicts", Alignment.MID, 0f);

        if (senateScope) {
            header.addPara("Senate at %s, %s", OPAD, hl, origin.getName(), EdictHelper.getSystemName(origin));
        } else {
            MarketAPI systemSenate = EdictHelper.getSystemSenate(origin);
            Pair<MarketAPI, Float> compounds = EdictHelper.getCompoundsSenateInRange(origin);

            if (systemSenate != null) {
                header.addPara("Granted by the Senate at %s", OPAD, hl, systemSenate.getName());
            } else if (compounds != null) {
                header.addPara("Granted by the Senate at %s, %s away", OPAD, hl,
                        compounds.one.getName(), Misc.getRoundedValueMaxOneAfterDecimal(compounds.two) + " LY");
            }
        }

        panel.addUIElement(header).inTL(0f, 0f);
        panel.addUIElement(HelpIcon.create(panel, this::addHelp)).inTR(0f, 0f);
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        Color hl = Misc.getHighlightColor();

        tooltip.addPara("A Senate allows one Edict per colony in its star system.", 0f);
        tooltip.addPara("Installing %s extends coverage to %s and halves unrest from early removal.", OPAD, hl,
                EdictHelper.COMPOUNDS_NAME, RANGE_LY_TWELVE + " LY");
        if (!senateScope) tooltip.addPara("Visit the Senate to manage the Edicts of every colony in its range.", Misc.getGrayColor(), OPAD);
    }

    private TooltipMakerAPI addColonyColumn(float x, float y, float height, CustomDialogCallback callback) {
        addColumnHeading("Colonies", x, y, COLONY_WIDTH);

        TooltipMakerAPI list = panel.createUIElement(COLONY_WIDTH, height - HEADING_HEIGHT - PAD, true);
        for (MarketAPI colony : colonies) {
            list.addCustom(createColonyRow(colony, COLONY_WIDTH - ROW_INSET, callback), PAD);
        }

        panel.addUIElement(list).inTL(x, y + HEADING_HEIGHT + PAD);
        return list;
    }

    private CustomPanelAPI createColonyRow(MarketAPI colony, float width, CustomDialogCallback callback) {
        Color hl = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();
        MarketConditionAPI activeCondition = EdictHelper.getActiveEdict(colony);
        EdictAPI active = activeCondition == null ? null : EdictHelper.asEdict(activeCondition);

        CustomPanelAPI row = panel.createCustomPanel(width, ROW_MIN_HEIGHT, new ButtonReportingCustomPanel(this));

        TooltipMakerAPI text = row.createUIElement(width - ROW_TEXT_PAD, ROW_MIN_HEIGHT, false);
        text.addPara("%s  size %s", 0f, new Color[]{Misc.getBasePlayerColor(), hl}, colony.getName(), colony.getSize() + "");
        text.addPara(getLocationText(colony), gray, 1f);

        if (active == null) {
            text.addPara("No Edict", gray, 1f);
        } else {
            String name = EdictRepo.getShortName(activeCondition.getName());
            int min = active.getMinRuntime(colony);

            if (active.getRemainingDays() > 0) text.addPara("%s, day %s of %s", 1f, hl, name, Math.min(active.getDaysPassed(), min) + "", min + "");
            else text.addPara("%s, %s", 1f, new Color[]{hl, Misc.getPositiveHighlightColor()}, name, "can end without penalty");
        }

        float height = Math.max(ROW_MIN_HEIGHT, text.getHeightSoFar() + 16f);
        row.getPosition().setSize(width, height);

        addRowButton(row, width, height, colony == selectedColony, action(callback, () -> selectColony(colony)), null);
        row.addUIElement(text).inTL(0f, 4f);

        if (active != null) {
            CustomPanelAPI bar = row.createCustomPanel(width - ROW_TEXT_PAD, 3f, new RuntimeBarPlugin(getRuntimeFraction(active, colony), getRuntimeColor(active)));
            row.addComponent(bar).inBL(ROW_INSET, 6f);
        }

        return row;
    }

    private TooltipMakerAPI addEdictColumn(float x, float y, float height, CustomDialogCallback callback) {
        addColumnHeading("Edicts for " + selectedColony.getName(), x, y, EDICT_WIDTH);

        MarketConditionAPI activeCondition = EdictHelper.getActiveEdict(selectedColony);
        TooltipMakerAPI list = panel.createUIElement(EDICT_WIDTH, height - HEADING_HEIGHT - PAD, true);

        for (MarketConditionSpecAPI spec : specs) {
            list.addCustom(createEdictRow(spec, activeCondition, EDICT_WIDTH - ROW_INSET, callback), PAD);
        }

        panel.addUIElement(list).inTL(x, y + HEADING_HEIGHT + PAD);
        return list;
    }

    private CustomPanelAPI createEdictRow(MarketConditionSpecAPI spec, MarketConditionAPI activeCondition, float width, CustomDialogCallback callback) {
        Color hl = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();

        boolean isActive = activeCondition != null && activeCondition.getId().equals(spec.getId());
        List<String> unmet = getUnmetShortTexts(previews.get(spec.getId()).getRequirements(selectedColony));

        String status;
        Color statusColor;
        Color nameColor = Misc.getBasePlayerColor();

        if (isActive) {
            EdictAPI active = EdictHelper.asEdict(activeCondition);
            int min = active == null ? 0 : active.getMinRuntime(selectedColony);
            int day = active == null ? 0 : Math.min(active.getDaysPassed(), min);
            status = "Active, day " + day + " of " + min;
            statusColor = hl;
            nameColor = hl;
        } else if (!unmet.isEmpty()) {
            status = "Needs: " + String.join(", ", unmet);
            statusColor = gray;
            nameColor = gray;
        } else if (activeCondition != null) {
            status = "One Edict per colony";
            statusColor = gray;
        } else {
            status = "Available";
            statusColor = Misc.getPositiveHighlightColor();
        }

        CustomPanelAPI row = panel.createCustomPanel(width, ROW_MIN_HEIGHT, new ButtonReportingCustomPanel(this));

        float textX = ICON_SIZE + MARK_GAP;
        TooltipMakerAPI text = row.createUIElement(width - textX - ROW_TEXT_PAD, ROW_MIN_HEIGHT, false);
        text.addPara(EdictRepo.getShortName(spec.getName()), nameColor, 0f);
        text.addPara(status, statusColor, 1f);

        float height = Math.max(ROW_MIN_HEIGHT, text.getHeightSoFar() + 10f);
        row.getPosition().setSize(width, height);

        String shortDesc = previews.get(spec.getId()).getShortDesc();
        addRowButton(row, width, height, spec.getId().equals(selectedEdict), action(callback, () -> selectEdict(spec.getId())),
                tooltip -> tooltip.addPara(shortDesc, 0f));

        TooltipMakerAPI icon = row.createUIElement(ICON_SIZE, ICON_SIZE, false);
        icon.addImage(spec.getIcon(), ICON_SIZE, ICON_SIZE, 0f);
        row.addUIElement(icon).inTL(0f, (height - ICON_SIZE) / 2f);
        row.addUIElement(text).inTL(textX, 5f);

        return row;
    }

    private TooltipMakerAPI addDetailColumn(float x, float y, float width, float height) {
        Color hl = Misc.getHighlightColor();
        Color base = Misc.getBasePlayerColor();

        MarketConditionSpecAPI spec = Global.getSettings().getMarketConditionSpec(selectedEdict);
        MarketConditionAPI activeCondition = EdictHelper.getActiveEdict(selectedColony);
        EdictAPI active = activeCondition == null ? null : EdictHelper.asEdict(activeCondition);
        boolean isActive = active != null && activeCondition.getId().equals(selectedEdict);
        EdictAPI edict = isActive ? active : previews.get(selectedEdict);
        List<EdictRequirement> requirements = edict.getRequirements(selectedColony);
        boolean met = getUnmetShortTexts(requirements).isEmpty();

        String title = spec.getName() + (isActive ? " (active)" : "");
        Color headingText = isActive ? Color.WHITE : met ? base : Misc.getTextColor();
        Color headingBg = isActive ? ACTIVE_HEADING : met ? Misc.getDarkPlayerColor() : UNMET_HEADING;

        TooltipMakerAPI heading = panel.createUIElement(width, HEADING_HEIGHT, false);
        heading.addSectionHeading(title, headingText, headingBg, Alignment.MID, 0f);
        panel.addUIElement(heading).inTL(x, y);

        float detailHeight = height - HEADING_HEIGHT - PAD - ACTION_HEIGHT;
        TooltipMakerAPI detail = panel.createUIElement(width, detailHeight, true);

        if (isActive) {
            int min = active.getMinRuntime(selectedColony);
            detail.addPara("Minimum term: %s / %s", 0f, hl, Math.min(active.getDaysPassed(), min) + "", min + " days");
            CustomPanelAPI bar = panel.createCustomPanel(width - 20f, 5f, new RuntimeBarPlugin(getRuntimeFraction(active, selectedColony), getRuntimeColor(active)));
            detail.addCustom(bar, PAD);
        }

        detail.addPara(spec.getDesc(), Misc.getGrayColor(), isActive ? OPAD : 0f);

        detail.addPara((isActive ? "Current effects on " : "Effects on ") + selectedColony.getName(), base, OPAD);
        edict.addEffectText(detail, selectedColony, PAD);

        if (edict.hasOtherColonyEffects(selectedColony)) {
            detail.addPara("Effects on other colonies", base, OPAD);
            edict.addOtherColonyEffectText(detail, selectedColony, PAD);
        }

        detail.addPara("Requirements", base, OPAD);
        for (EdictRequirement requirement : requirements) {
            detail.addCustom(createRequirementRow(requirement, width - 20f), PAD);
        }

        detail.addPara("Duration", base, OPAD);
        if (isActive) addActiveRuntimeText(detail, active);
        else edict.addRuntimeText(detail, selectedColony, PAD);

        panel.addUIElement(detail).inTL(x, y + HEADING_HEIGHT + PAD);

        addActions(x, y + height - ACTION_HEIGHT, width, ACTION_HEIGHT, activeCondition, active, isActive, met);
        return detail;
    }

    private void addActiveRuntimeText(TooltipMakerAPI detail, EdictAPI active) {
        int unrest = active.getRemovalUnrest();

        if (active.getRemainingDays() > 0 || unrest > 0) {
            detail.addPara("Removing it now causes %s.", PAD, Misc.getNegativeHighlightColor(), unrest + " unrest");
        } else {
            detail.addPara("Can be removed %s.", PAD, Misc.getPositiveHighlightColor(), "without penalty");
        }
    }

    private CustomPanelAPI createRequirementRow(EdictRequirement requirement, float width) {
        CustomPanelAPI row = panel.createCustomPanel(width, 18f, new BaseCustomUIPanelPlugin());

        // labels sit ROW_INSET further in than components, so the gap is MARK_GAP
        float labelX = 2f + MARK_SIZE + MARK_GAP - ROW_INSET;
        TooltipMakerAPI label = row.createUIElement(width - labelX - ROW_TEXT_PAD, 18f, false);
        label.addPara(requirement.text, requirement.met ? Misc.getTextColor() : Misc.getNegativeHighlightColor(), 0f);
        row.getPosition().setSize(width, Math.max(18f, label.getHeightSoFar() + 2f));

        CustomPanelAPI mark = row.createCustomPanel(MARK_SIZE, MARK_SIZE, new RequirementMarkPlugin(requirement.met));
        row.addComponent(mark).inTL(2f, 2f);
        row.addUIElement(label).inTL(labelX, 0f);

        return row;
    }

    private void addActions(float x, float y, float width, float height, MarketConditionAPI activeCondition, EdictAPI active, boolean isActive, boolean met) {
        Color hl = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();

        CustomPanelAPI actionPanel = panel.createCustomPanel(width, height, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI actions = actionPanel.createUIElement(width, height, false);

        if (!isActive) {
            ButtonAPI enact = addActionButton(actions, "Enact", popupAction(this::createEnactConfirm), OPAD);
            enact.setEnabled(met && activeCondition == null);

            if (activeCondition != null) {
                actions.addPara("Remove %s first: one Edict per colony.", PAD, gray, hl, EdictRepo.getShortName(activeCondition.getName()));
            } else if (!met) {
                actions.addPara("Requirements not met.", gray, PAD);
            }
        } else {
            actions.addButton("Remove", popupAction(this::createRemoveConfirm), Misc.getTextColor(), REMOVE_COLOUR, Alignment.MID, CutStyle.TL_BR, BUTTON_WIDTH, BUTTON_HEIGHT, OPAD);

            int unrest = active.getRemovalUnrest();
            if (unrest > 0) actions.addPara("Now: %s", PAD, Misc.getNegativeHighlightColor(), unrest + " unrest");
            else actions.addPara("%s", PAD, Misc.getPositiveHighlightColor(), "No penalty");
        }

        actionPanel.addUIElement(actions).inTL(0f, 0f);
        panel.addComponent(actionPanel).inTL(x, y);
    }

    private EdictConfirmDialog createEnactConfirm() {
        String edictName = EdictRepo.getShortName(Global.getSettings().getMarketConditionSpec(selectedEdict).getName());
        MarketAPI colony = selectedColony;
        EdictAPI edict = previews.get(selectedEdict);

        return new EdictConfirmDialog("Enact", info -> {
            info.addPara("Enact %s on %s?", 0f, Misc.getHighlightColor(), edictName, colony.getName());
            edict.addRuntimeText(info, colony, OPAD);
        }, this::enactSelected);
    }

    private EdictConfirmDialog createRemoveConfirm() {
        EdictAPI active = EdictHelper.getActiveEdictPlugin(selectedColony);
        if (active == null) throw new IllegalStateException("IndEvo: no Edict to remove on " + selectedColony.getName());

        Color hl = Misc.getHighlightColor();
        String edictName = EdictRepo.getShortName(EdictHelper.getActiveEdict(selectedColony).getName());
        MarketAPI colony = selectedColony;
        int unrest = active.getRemovalUnrest();
        int cost = active.getRelicRemovalCost();
        int relicUnrest = active.getRelicRemovalUnrest();

        EdictConfirmDialog confirm = new EdictConfirmDialog("Remove", info -> {
            if (unrest > 0) info.addPara("Remove %s from %s? This causes %s.", 0f, new Color[]{hl, hl, Misc.getNegativeHighlightColor()}, edictName, colony.getName(), unrest + " unrest");
            else info.addPara("Remove %s from %s? There is %s.", 0f, new Color[]{hl, hl, Misc.getPositiveHighlightColor()}, edictName, colony.getName(), "no penalty");

            if (cost <= 0) return;
            if (relicUnrest > 0) info.addPara("With relics: %s.", OPAD, Misc.getNegativeHighlightColor(), relicUnrest + " unrest");
            else info.addPara("With relics: %s.", OPAD, Misc.getPositiveHighlightColor(), "no unrest");
            RelicHelper.addCostLine(info, colony, cost, PAD);
        }, this::removeSelected);

        if (cost <= 0) return confirm;

        return confirm.withRelics(this::removeSelectedWithRelics, RelicHelper.canAfford(colony, cost), tooltip -> {
            tooltip.addPara("Removes the Edict without the early removal unrest.", 0f);
            if (relicUnrest > 0) tooltip.addPara("Removal still causes %s.", PAD, Misc.getNegativeHighlightColor(), relicUnrest + " unrest");
            RelicHelper.addCostLine(tooltip, colony, cost, OPAD);
            tooltip.addPara(RELIC_LORE, Misc.getGrayColor(), OPAD);
        });
    }

    private ButtonAction popupAction(Supplier<EdictConfirmDialog> popup) {
        return new ButtonAction(this) {
            @Override
            public void execute() {
                popup.get().show(() -> recreatePanel(callback));
            }
        };
    }

    private ButtonAPI addActionButton(TooltipMakerAPI tooltip, String text, ButtonAction action, float pad) {
        return tooltip.addButton(text, action, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, BUTTON_WIDTH, BUTTON_HEIGHT, pad);
    }

    private void addFooter(float width, float height) {
        if (message == null) return;

        TooltipMakerAPI footer = panel.createUIElement(width, FOOTER_HEIGHT, false);
        footer.addPara(message, messageColor, 4f);
        panel.addUIElement(footer).inTL(0f, height - FOOTER_HEIGHT);
    }

    private void addColumnHeading(String text, float x, float y, float width) {
        TooltipMakerAPI heading = panel.createUIElement(width, HEADING_HEIGHT, false);
        heading.addSectionHeading(text, Alignment.MID, 0f);
        panel.addUIElement(heading).inTL(x, y);
    }

    private void addRowButton(CustomPanelAPI row, float width, float height, boolean checked, ButtonAction action, Consumer<TooltipMakerAPI> tooltip) {
        float buttonWidth = width + ROW_INSET;
        TooltipMakerAPI background = row.createUIElement(buttonWidth, height, false);
        ButtonAPI button = background.addAreaCheckbox("", action, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), buttonWidth, height, 0f);
        button.setChecked(checked);

        if (tooltip != null) {
            background.addTooltipToPrevious(new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltipMaker, boolean expanded, Object tooltipParam) {
                    tooltip.accept(tooltipMaker);
                }
            }, TooltipMakerAPI.TooltipLocation.RIGHT);
        }

        row.addUIElement(background).inTL(-ROW_INSET * 2f, 0f);
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

    private void selectColony(MarketAPI colony) {
        selectedColony = colony;
        resetDetailScroll = true;
        selectedEdict = null;

        if (colony == null || specs.isEmpty()) return;

        MarketConditionAPI activeCondition = EdictHelper.getActiveEdict(colony);
        if (activeCondition != null && previews.containsKey(activeCondition.getId())) {
            selectedEdict = activeCondition.getId();
            return;
        }

        for (MarketConditionSpecAPI spec : specs) {
            if (getUnmetShortTexts(previews.get(spec.getId()).getRequirements(colony)).isEmpty()) {
                selectedEdict = spec.getId();
                return;
            }
        }

        selectedEdict = specs.get(0).getId();
    }

    private void selectEdict(String id) {
        selectedEdict = id;
        resetDetailScroll = true;
    }

    private void enactSelected() {
        String name = Global.getSettings().getMarketConditionSpec(selectedEdict).getName();
        EdictHelper.enact(selectedColony, selectedEdict);

        message = name + " enacted on " + selectedColony.getName() + ".";
        messageColor = Misc.getHighlightColor();
    }

    private void removeSelected() {
        MarketConditionAPI activeCondition = EdictHelper.getActiveEdict(selectedColony);
        EdictAPI active = activeCondition == null ? null : EdictHelper.asEdict(activeCondition);
        if (active == null) throw new IllegalStateException("IndEvo: no Edict to remove on " + selectedColony.getName());

        int unrest = active.getRemovalUnrest();
        active.removeWithPenalty();

        message = activeCondition.getName() + " removed from " + selectedColony.getName() + (unrest > 0 ? ", " + unrest + " unrest." : ".");
        messageColor = unrest > 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
    }

    private void removeSelectedWithRelics() {
        MarketConditionAPI activeCondition = EdictHelper.getActiveEdict(selectedColony);
        EdictAPI active = activeCondition == null ? null : EdictHelper.asEdict(activeCondition);
        if (active == null) throw new IllegalStateException("IndEvo: no Edict to remove on " + selectedColony.getName());

        int cost = active.getRelicRemovalCost();
        int unrest = active.getRelicRemovalUnrest();
        RelicHelper.take(selectedColony, cost);
        active.removeEarlyWithRelics();

        message = activeCondition.getName() + " removed from " + selectedColony.getName() + " for " + cost + " " + RelicHelper.getName() + (unrest > 0 ? ", " + unrest + " unrest." : ".");
        messageColor = Misc.getHighlightColor();
    }

    private String getLocationText(MarketAPI colony) {
        String system = EdictHelper.getSystemName(colony);

        if (colony.hasIndustry(Ids.SENATE)) return system + ", Senate";
        if (EdictHelper.getSystemSenate(colony) != null) return system + ", same system";

        if (senateScope) {
            float dist = Misc.getDistanceLY(origin.getLocationInHyperspace(), colony.getLocationInHyperspace());
            return system + ", " + Misc.getRoundedValueMaxOneAfterDecimal(dist) + " LY, Compounds range";
        }

        Pair<MarketAPI, Float> compounds = EdictHelper.getCompoundsSenateInRange(colony);
        return compounds == null ? system : system + ", " + Misc.getRoundedValueMaxOneAfterDecimal(compounds.two) + " LY, Compounds range";
    }

    private static List<String> getUnmetShortTexts(List<EdictRequirement> requirements) {
        List<String> unmet = new ArrayList<>();
        for (EdictRequirement requirement : requirements) {
            if (!requirement.met) unmet.add(requirement.shortText);
        }
        return unmet;
    }

    private static float getRuntimeFraction(EdictAPI edict, MarketAPI market) {
        int min = edict.getMinRuntime(market);
        return min <= 0 ? 1f : Math.min(1f, edict.getDaysPassed() / (float) min);
    }

    private static Color getRuntimeColor(EdictAPI edict) {
        return edict.getRemainingDays() > 0 ? Misc.getHighlightColor() : Misc.getPositiveHighlightColor();
    }

    private static class RuntimeBarPlugin extends BaseCustomUIPanelPlugin {
        private final float fraction;
        private final Color color;
        private PositionAPI pos;

        RuntimeBarPlugin(float fraction, Color color) {
            this.fraction = fraction;
            this.color = color;
        }

        @Override
        public void positionChanged(PositionAPI pos) {
            this.pos = pos;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (pos == null) return;

            float x = pos.getX();
            float y = pos.getY();
            float w = pos.getWidth();
            float h = pos.getHeight();

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            drawQuad(x, y, w, h, Color.BLACK, 0.8f * alphaMult);
            drawQuad(x, y, w * fraction, h, color, alphaMult);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        private static void drawQuad(float x, float y, float w, float h, Color c, float alpha) {
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x, y + h);
            GL11.glVertex2f(x + w, y + h);
            GL11.glVertex2f(x + w, y);
            GL11.glEnd();
        }
    }

    private static class RequirementMarkPlugin extends BaseCustomUIPanelPlugin {
        private final boolean met;
        private PositionAPI pos;

        RequirementMarkPlugin(boolean met) {
            this.met = met;
        }

        @Override
        public void positionChanged(PositionAPI pos) {
            this.pos = pos;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null) return;

            Color c = met ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
            float x = pos.getX();
            float top = pos.getY() + pos.getHeight();
            float s = Math.min(pos.getWidth(), pos.getHeight()) / 12f;

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
            GL11.glLineWidth(2f);

            // points in a 12 unit box, y grows downwards
            if (met) {
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex2f(x + 1f * s, top - 6.5f * s);
                GL11.glVertex2f(x + 4.5f * s, top - 10f * s);
                GL11.glVertex2f(x + 11f * s, top - 2f * s);
            } else {
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(x + 2f * s, top - 2f * s);
                GL11.glVertex2f(x + 10f * s, top - 10f * s);
                GL11.glVertex2f(x + 10f * s, top - 2f * s);
                GL11.glVertex2f(x + 2f * s, top - 10f * s);
            }
            GL11.glEnd();

            GL11.glLineWidth(1f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }
}
