package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiButtons;
import indevo.utils.ui.UiData;
import indevo.utils.ui.UiFrame;
import indevo.utils.ui.UiHost;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

// test only: kit headings and buttons beside vanilla built ones in one dialog, sizes logged
public final class StyleChecks {
    private static final Logger log = Global.getLogger(StyleChecks.class);
    private static final float W = 1100f;
    private static final float H = 770f;

    private StyleChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        UiViews.view(steps, "style-kit-vs-vanilla", c -> c.expect(UiHost.show(Global.getSector().getPlayerFleet(),
                dialog -> dialog.showCustomDialog(W, H, new Board(dialog))), "style dialog opens"));
        UiViews.view(steps, "style-bars", c -> c.expect(UiHost.show(Global.getSector().getPlayerFleet(),
                dialog -> dialog.showCustomDialog(BarBoard.W, BarBoard.H, new BarBoard(dialog))), "bar dialog opens"));
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    static final class Board extends BaseCustomDialogDelegate {
        private final InteractionDialogAPI dialog;
        private final UiButtons.Confirm confirm = new UiButtons.Confirm();

        Board(InteractionDialogAPI dialog) {
            this.dialog = dialog;
            confirm.arm("pair");
        }

        @Override
        public void createCustomDialog(CustomPanelAPI p, CustomDialogCallback callback) {
            float half = 530f;
            float vx = half + 30f;
            Ui.text(p, 0f, 0f, half, "KIT", Ui.ORB12, Ui.hl(), Ui.LEFT);
            Ui.text(p, vx, 0f, half, "VANILLA (built the way vanilla intel and tooltips build them)", Ui.ORB12, Ui.hl(), Ui.LEFT);

            // headings
            LabelAPI kitMid = Ui.heading(p, 0f, 24f, half, "Ui.heading centred");
            Ui.heading(p, 0f, 48f, half, "Ui.heading left", Ui.LEFT);
            Ui.thead(p, 0f, 72f, half, new String[]{"Ui.thead", "Value"}, new float[]{8f, 300f}, new float[]{200f, 200f}, new Alignment[]{Ui.LEFT, Ui.RIGHT});
            float tabsEnd = UiButtons.tabs(p, 0f, 98f, half, 150f, UiFrame.HEADER_H, new String[]{"Kit tab on", "Kit tab off"}, 0, null, i -> {
            });

            LabelAPI[] vanilla = new LabelAPI[2];
            TooltipMakerAPI vh = Ui.element(p, vx, 24f, half, 60f, t -> {
                vanilla[0] = t.addSectionHeading("addSectionHeading MID", Alignment.MID, 0f);
                vanilla[1] = t.addSectionHeading("addSectionHeading LMID", Alignment.LMID, 6f);
            });
            log.info("LIVE_CHECK note: STYLE kit centred heading label " + size(kitMid) + ", kit left bar " + Ui.HEADING_H + " high, kit tabs " + UiFrame.HEADER_H
                    + " high, rule at " + tabsEnd + "; vanilla addSectionHeading MID " + size(vanilla[0]) + ", LMID " + size(vanilla[1]) + ", element height " + vh.getHeightSoFar());

            // kit buttons, one per kind
            float y = 140f;
            UiButtons.Kind[] kinds = UiButtons.Kind.values();
            for (int i = 0; i < kinds.length; i++) {
                float bx = (i % 2) * 265f;
                float by = y + (i / 2) * 34f;
                ButtonAPI b = UiButtons.button(p, bx, by, 255f, 28f, "Kit " + kinds[i], kinds[i], () -> {
                });
                if (i == 0) log.info("LIVE_CHECK note: STYLE kit button " + b.getPosition().getWidth() + " x " + b.getPosition().getHeight());
            }
            y += ((kinds.length + 1) / 2) * 34f + 6f;
            UiButtons.guarded(p, 0f, y, 255f, 28f, "Kit guarded", UiButtons.Kind.STD, "Red reason line of a blocked button.", () -> {
            });
            UiButtons.confirm(p, 265f, y, 255f, 28f, "Kit confirm", UiButtons.Kind.STD, confirm, "pair", () -> {
            }, () -> {
            });
            y += 64f;
            UiButtons.area(p, 0f, y, 255f, 30f, "Kit area off", false, true, null, Ui.CENTER, () -> {
            });
            UiButtons.area(p, 265f, y, 255f, 30f, "Kit area on", true, true, null, Ui.CENTER, () -> {
            });
            y += 36f;
            UiButtons.row(p, 0f, y, 520f, 40f, true, true, Ui.AMBER, () -> {
            });
            y += 46f;
            UiButtons.check(p, 0f, y, 200f, "Kit checkbox", true, true, () -> {
            });
            UiButtons.stepper(p, 265f, y, 3, true, () -> {
            }, () -> {
            });
            UiButtons.button(p, 0f, y + 30f, 255f, 28f, "Kit icon + sub", UiButtons.Kind.STD, CutStyle.ALL, Global.getSettings().getCommoditySpec("IndEvo_rare_parts").getIconName(),
                    "120", Ui.ORB12, () -> {
                    });

            // strips built the way the panels build them, text placed by Ui.textIn
            float sy = 500f;
            Ui.heading(p, 0f, sy, half, "Ui.heading note", "grey note");
            Ui.heading(p, 0f, sy + 24f, half, "Edict gold strip", Ui.CENTER, Ui.text(), Misc.scaleAlpha(Ui.AMBER, 0.6f));
            Ui.textIn(p, half - 8f - 60f, sy + 24f, 60f, Ui.HEADING_H, "Active", Ui.ORB12, Ui.text(), Ui.RIGHT, new String[0], new Color[0]);
            Ui.box(p, 1f, sy + 48f, half - 2f, 20f, null, Ui.dark());
            Ui.textIn(p, 10f, sy + 48f, 200f, 20f, "Research card head", Ui.ORB12, Ui.bright(), Ui.LEFT, new String[0], new Color[0]);
            Ui.textIn(p, half - 110f, sy + 48f, 100f, 20f, "Ready", Ui.ORB12, Ui.text(), Ui.RIGHT, new String[0], new Color[0]);
            Ui.heading(p, 0f, sy + 74f, half, "Detachment strip", (String) null);
            Ui.textIn(p, 200f, sy + 74f, half - 208f, Ui.HEADING_H, "Following, Corvus", Ui.ORB12, Ui.base(), Ui.RIGHT, new String[]{"Following"}, new Color[]{Ui.hl()});
            UiFrame.title(Ui.panel(p, 0f, sy + 98f, half, Ui.HEADING_H), half, "Title strip", "sub text", null);
            Ui.element(p, vx, sy, half, 120f, t -> {
                t.addSectionHeading("addSectionHeading LMID", Alignment.LMID, 0f);
                t.addSectionHeading("addSectionHeading MID", Alignment.MID, 6f);
            });

            // green and amber fills at 100, 75 and 60 %, the kit uses UiButtons.FILL; red at full and a vanilla button for reference
            float ry = 636f;
            Ui.text(p, 0f, ry, W, "Button fills: GREEN and AMBER at 100, 75 and 60 % (kit " + Math.round(UiButtons.FILL * 100f) + " %), RED at full, vanilla dark fill at the end",
                    Ui.ORB12, Ui.hl(), Ui.LEFT);
            float[] levels = {1f, 0.75f, 0.6f};
            for (int i = 0; i < levels.length; i++) {
                float by = ry + 22f + i * 34f;
                Color g = Misc.setAlpha(Misc.scaleColorOnly(Ui.GREEN, levels[i]), 255);
                Color a = Misc.setAlpha(Misc.scaleColorOnly(Ui.AMBER, levels[i]), 255);
                Ui.text(p, 0f, by + 6f, 56f, Math.round(levels[i] * 100f) + " %", Ui.ORB12, Ui.text(), Ui.LEFT);
                fillButton(p, 60f, by, 120f, "Green", g, true);
                fillButton(p, 186f, by, 120f, "Green off", g, false);
                fillButton(p, 312f, by, 96f, "Confirm", g, true);
                fillButton(p, 414f, by, 96f, "Cancel", Ui.dark(), true);
                fillButton(p, 530f, by, 120f, "Amber", a, true);
                fillButton(p, 656f, by, 120f, "Amber off", a, false);
                fillButton(p, 800f, by, 120f, "Red", Ui.RED, true);
                fillButton(p, 926f, by, 120f, "Vanilla", Misc.getDarkPlayerColor(), true);
            }

            // vanilla built buttons, same calls as api/impl intel (base and dark faction colours, ALL cut by default)
            Ui.element(p, vx, 140f, half, 400f, t -> {
                ButtonAPI a = t.addButton("Vanilla intel button 20", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), 255f, 20f, 0f);
                t.addButton("Vanilla 28 ALL", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, 255f, 28f, 6f);
                t.addButton("Vanilla 28 TL_BR", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 255f, 28f, 6f);
                t.addButton("Vanilla 28 BOTTOM", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.BOTTOM, 255f, 28f, 6f);
                ButtonAPI off = t.addButton("Vanilla disabled", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, 255f, 28f, 6f);
                off.setEnabled(false);
                t.addButton("Vanilla story colours", null, Misc.getStoryOptionColor(), Misc.getStoryDarkColor(), Alignment.MID, CutStyle.ALL, 255f, 28f, 6f);
                t.addButton("Vanilla default overload", null, 255f, 28f, 6f);
                t.addAreaCheckbox("Vanilla area off", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), 255f, 30f, 6f);
                ButtonAPI on = t.addAreaCheckbox("Vanilla area on", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), 255f, 30f, 6f);
                on.setChecked(true);
                ButtonAPI box = t.addCheckbox(200f, 20f, "Vanilla checkbox", null, ButtonAPI.UICheckboxSize.SMALL, 6f);
                box.setChecked(true);
                log.info("LIVE_CHECK note: STYLE vanilla intel button " + a.getPosition().getWidth() + " x " + a.getPosition().getHeight());
            });
        }

        // a kit like button with a given fill, light text on coloured fills, base text on the vanilla fill
        private static void fillButton(CustomPanelAPI p, float x, float y, float w, String label, Color bg, boolean enabled) {
            Color fg = bg.equals(Ui.dark()) || bg.equals(Misc.getDarkPlayerColor()) ? Ui.base() : Ui.text();
            Ui.element(p, x, y, w, 28f, t -> {
                ButtonAPI b = t.addButton(label, null, fg, bg, Alignment.MID, CutStyle.ALL, w, 28f, 0f);
                if (!enabled) b.setEnabled(false);
            });
        }

        private static String size(LabelAPI label) {
            return label == null ? "none" : label.getPosition().getWidth() + " x " + label.getPosition().getHeight() + " (line " + label.computeTextHeight("Ag") + ")";
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
            dialog.dismiss();
        }

        @Override
        public void customDialogCancel() {
            dialog.dismiss();
        }
    }

    // kit bars beside the vanilla bar widget com.fs.starfarer.campaign.ui.N (obfuscated, test only) at the same size and fraction
    static final class BarBoard extends BaseCustomDialogDelegate {
        static final float W = 1100f;
        static final float H = 330f;
        static final float BW = 110f;
        static final float BH = 20f;
        private static final String[] KINDS = {"standard", "ready", "paused", "overflow", "cargo", "fuel", "crew", "danger", "thin row"};
        private static final float[] LEVELS = {0f, 0.35f, 0.7f, 1f};
        private final InteractionDialogAPI dialog;

        BarBoard(InteractionDialogAPI dialog) {
            this.dialog = dialog;
        }

        private static Color color(String kind) {
            return switch (kind) {
                case "ready" -> Ui.GREEN;
                case "paused" -> Ui.barColor("progressBarWarningColor");
                case "overflow" -> Ui.barColor("progressBarOverflowColor");
                case "cargo" -> Ui.barColor("progressBarCargoColor");
                case "fuel" -> Ui.barColor("progressBarFuelColor");
                case "crew" -> Ui.barColor("progressBarCrewColor");
                case "danger" -> Ui.barColor("progressBarDangerColor");
                default -> Ui.barBlue();
            };
        }

        @Override
        public void createCustomDialog(CustomPanelAPI p, CustomDialogCallback callback) {
            Ui.text(p, 0f, 0f, W, "Each pair: kit UiData.bar (left), vanilla bar widget (right), at 0, 35, 70 and 100 %; thin row bar is kit only", Ui.ORB12, Ui.hl(), Ui.LEFT);
            for (int r = 0; r < KINDS.length; r++) {
                float y = 26f + r * 32f;
                Ui.text(p, 0f, y + 2f, 80f, KINDS[r], Ui.ORB12, Ui.text(), Ui.LEFT);
                for (int i = 0; i < LEVELS.length; i++) {
                    float x = 90f + i * (2f * BW + 24f);
                    if (r == KINDS.length - 1) {
                        UiData.thinbar(p, x, y + 8f, BW, LEVELS[i], Ui.barBlue());
                        continue;
                    }
                    UiData.bar(p, x, y, BW, BH, LEVELS[i], color(KINDS[r]), null);
                    com.fs.starfarer.campaign.ui.N vanilla = new com.fs.starfarer.campaign.ui.N("", 0f, 100f);
                    vanilla.setShowNoText(true);
                    vanilla.setBarColor(color(KINDS[r]));
                    vanilla.setProgress(LEVELS[i] * 100f);
                    p.addComponent((UIComponentAPI) (Object) vanilla).setSize(BW, BH).inTL(x + BW + 8f, y);
                }
            }
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
            dialog.dismiss();
        }

        @Override
        public void customDialogCancel() {
            dialog.dismiss();
        }
    }
}
