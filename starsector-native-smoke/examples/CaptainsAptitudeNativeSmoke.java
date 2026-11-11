import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.campaign.CampaignEngine;

import aptitude.CaptainMilestones;
import aptitude.ui.CaptainSkillPanel;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Native smoke for Captain's Aptitude: builds the real vanilla Character tab, attaches the mod's buttons
 * and opens both overlays. Runs as the companion mod's plugin, after the mod's own plugin, behind the
 * security gate: all reflection and file access goes through NativeSmokeSupport. Test-only; never packaged.
 */
public final class CaptainsAptitudeNativeSmoke extends BaseModPlugin {
    @Override
    public void onApplicationLoad() throws Exception {
        NativeSmokeSupport.run(CaptainsAptitudeNativeSmoke::probe);
    }

    private static void probe() throws Exception {
        Global.setSector(CampaignEngine.getInstance());
        NativeSmokeSupport.check(Global.getSector().getCharacterData() != null
                && Global.getSector().getPlayerStats() != null, "campaign engine provides player character data");
        Global.getSector().getPlayerStats().setLevel(9);
        Map<String, Object> data = Global.getSector().getPersistentData();
        CaptainMilestones.grantOnce(data, CaptainMilestones.FIRST_VICTORY);
        CaptainMilestones.grantOnce(data, CaptainMilestones.FIRST_CAPITAL);
        CaptainMilestones.grantOnce(data, CaptainMilestones.FIRST_COLONY);

        // same rule as CaptainButtonPatch: a coreui UIPanelAPI with getAptitudeRows and getAptitudeList
        Class<?> tabType = NativeSmokeSupport.findClass(CampaignEngine.class, "com.fs.starfarer.coreui",
                type -> UIPanelAPI.class.isAssignableFrom(type)
                        && NativeSmokeSupport.hasMethod(type, "getAptitudeRows", 0)
                        && NativeSmokeSupport.hasMethod(type, "getAptitudeList", 0));
        NativeSmokeSupport.log("character tab class matched by shape: " + tabType.getName());
        Class<?> coreUi = NativeSmokeSupport.constructorParameters(tabType, 1)[0];
        float tabWidth = ((Number) NativeSmokeSupport.getStatic(coreUi, "CHAR_PANEL_WIDTH")).floatValue();
        float tabHeight = ((Number) NativeSmokeSupport.getStatic(coreUi, "CHAR_PANEL_HEIGHT")).floatValue();
        NativeSmokeSupport.log("vanilla CHAR_PANEL size " + tabWidth + "x" + tabHeight);
        UIPanelAPI tab = (UIPanelAPI) NativeSmokeSupport.construct(tabType, (Object) null);

        float width = NativeSmokeSupport.width(), height = NativeSmokeSupport.height();
        CustomPanelAPI root = Global.getSettings().createCustom(width, height, null);
        root.addComponent(tab).setSize(tabWidth, tabHeight).inMid();
        NativeSmokeSupport.advance(root, 30);
        NativeSmokeSupport.render(root);
        NativeSmokeSupport.screenshot("character-tab-vanilla.png");

        CaptainSkillPanel view = CaptainSkillPanel.attach(tab);
        NativeSmokeSupport.advance(root, 30);
        NativeSmokeSupport.render(root);
        NativeSmokeSupport.screenshot("character-tab.png");

        ButtonAPI skills = button(tab, "Captain Skills");
        ButtonAPI milestones = button(tab, "Milestones (");
        ButtonAPI reassign = button(tab, "Reassign skills...");
        NativeSmokeSupport.check(skills != null && milestones != null, "both mod buttons attach to the Character tab");
        NativeSmokeSupport.check(reassign != null, "vanilla Reassign button found for comparison");
        PositionAPI tp = tab.getPosition();
        describe("tab", tp);
        describe("captain skills", skills.getPosition());
        describe("milestones", milestones.getPosition());
        describe("reassign", reassign.getPosition());
        NativeSmokeSupport.expect(!overlaps(skills.getPosition(), reassign.getPosition())
                && !overlaps(milestones.getPosition(), reassign.getPosition()), "mod buttons do not overlap Reassign");
        NativeSmokeSupport.expect(inside(skills.getPosition(), tp) && inside(milestones.getPosition(), tp),
                "mod buttons lie inside the Character tab");
        NativeSmokeSupport.expect(skills.getPosition().getX() == reassign.getPosition().getX()
                && milestones.getPosition().getX() + milestones.getPosition().getWidth()
                == reassign.getPosition().getX() + reassign.getPosition().getWidth(),
                "mod button row aligns with Reassign's left and right edges");
        NativeSmokeSupport.expect(milestones.getText().equals("Milestones (3)"),
                "Milestones button shows net points: " + milestones.getText());
        NativeSmokeSupport.expect(NativeSmokeSupport.litPixels(NativeSmokeSupport.region(skills)) > 50
                && NativeSmokeSupport.litPixels(NativeSmokeSupport.region(milestones)) > 50, "mod buttons render pixels");

        NativeSmokeSupport.invoke(view, "open", false);
        NativeSmokeSupport.advance(root, 30);
        NativeSmokeSupport.render(root);
        NativeSmokeSupport.screenshot("captain-skills.png");
        NativeSmokeSupport.check(hasHit(view, "captains_aptitude_close", true) && hasHit(view, "learn:", true),
                "Captain Skills overlay lays out Close and an enabled Learn button");
        PositionAPI op = tab.getPosition();
        NativeSmokeSupport.expect(NativeSmokeSupport.litPixels(NativeSmokeSupport.region(op.getX() + 16f,
                op.getY() + op.getHeight() - 34f, 300f, 30f)) > 200, "overlay title bar renders");
        NativeSmokeSupport.expect(!hitsOverlap(view), "overlay buttons never overlap");

        cursor(view, op.getX() + 120f, op.getY() + op.getHeight() - 110f);
        NativeSmokeSupport.render(root);
        NativeSmokeSupport.expect(NativeSmokeSupport.getField(view, "hoverSkill") != null,
                "hovered skill row opens a tooltip");
        NativeSmokeSupport.screenshot("skill-tooltip.png");
        cursor(view, -1f, -1f);

        // click: aim at the first enabled learn hit and let the overlay's advance apply it
        Object learn = firstEnabledLearn(view);
        float[] lb = box(learn);
        String skill = ((String) NativeSmokeSupport.getField(learn, "action")).substring("learn:".length());
        cursor(view, lb[0] + lb[2] / 2f, lb[1] + lb[3] / 2f);
        NativeSmokeSupport.invoke(view, "click");
        NativeSmokeSupport.advance(root, 2);
        NativeSmokeSupport.expect(Global.getSector().getPlayerStats().getSkillLevel(skill) == 1f,
                "clicking Learn learns " + skill);
        NativeSmokeSupport.render(root);
        NativeSmokeSupport.screenshot("after-learn.png");

        // scroll: give the first column overflow, wheel down under the mouse, then clamp at the end
        float[] viewH = (float[]) NativeSmokeSupport.getField(view, "viewH");
        float[] viewX = (float[]) NativeSmokeSupport.getField(view, "viewX");
        float[] viewY = (float[]) NativeSmokeSupport.getField(view, "viewY");
        float[] contentH = (float[]) NativeSmokeSupport.getField(view, "contentH");
        cursor(view, viewX[0] + 20f, viewY[0] + 20f);
        contentH[0] = viewH[0] + 60f;
        NativeSmokeSupport.invoke(view, "scrollAt", -1);
        float[] scroll = (float[]) NativeSmokeSupport.getField(view, "scroll");
        NativeSmokeSupport.expect(scroll[0] == 40f, "wheel down scrolls the hovered column by one step");
        NativeSmokeSupport.invoke(view, "scrollAt", -1);
        NativeSmokeSupport.expect(scroll[0] == 60f, "scroll clamps at the end of the content");
        NativeSmokeSupport.invoke(view, "scrollAt", 1);
        NativeSmokeSupport.expect(scroll[0] == 20f, "wheel up scrolls back");
        NativeSmokeSupport.expect(scroll[1] == 0f, "other columns keep their scroll");

        // real input path: put the os cursor on the next enabled learn button and send a click event
        Object next = firstEnabledLearn(view);
        float[] nb = box(next);
        String nextSkill = ((String) NativeSmokeSupport.getField(next, "action")).substring("learn:".length());
        float px = nb[0] + nb[2] / 2f, py = nb[1] + nb[3] / 2f;
        float scale = Global.getSettings().getScreenScaleMult();
        Mouse.setCursorPosition(Math.round(px * scale), Math.round(py * scale));
        NativeSmokeSupport.log("cursor settings=" + Global.getSettings().getMouseX() + ","
                + Global.getSettings().getMouseY() + " target=" + px + "," + py);
        Object overlayPlugin = NativeSmokeSupport.invoke(NativeSmokeSupport.getField(view, "overlay"), "getPlugin");
        List<InputEventAPI> clickEvents = new ArrayList<>();
        clickEvents.add(NativeSmokeSupport.mouse(px, py, true, 0));
        NativeSmokeSupport.invoke(overlayPlugin, "processInput", clickEvents);
        NativeSmokeSupport.expect(clickEvents.get(0).isConsumed(), "click inside the overlay is consumed");
        NativeSmokeSupport.advance(root, 2);
        NativeSmokeSupport.expect(Global.getSector().getPlayerStats().getSkillLevel(nextSkill) == 1f,
                "a real click event on Learn learns " + nextSkill);

        List<InputEventAPI> wheelEvents = new ArrayList<>();
        NativeSmokeSupport.setField(view, "mouseX", viewX[0] + 20f);
        Mouse.setCursorPosition(Math.round((viewX[0] + 20f) * scale), Math.round((viewY[0] + 20f) * scale));
        contentH[0] = viewH[0] + 60f;
        scroll[0] = 0f;
        wheelEvents.add(NativeSmokeSupport.mouse(viewX[0] + 20f, viewY[0] + 20f, false, -120));
        NativeSmokeSupport.invoke(overlayPlugin, "processInput", wheelEvents);
        NativeSmokeSupport.expect(wheelEvents.get(0).isConsumed() && scroll[0] == 40f,
                "a real wheel-down event scrolls the hovered column");

        // escape through the real input path closes the overlay
        Object plugin = NativeSmokeSupport.invoke(NativeSmokeSupport.getField(view, "overlay"), "getPlugin");
        List<InputEventAPI> events = new ArrayList<>();
        events.add(NativeSmokeSupport.key(Keyboard.KEY_ESCAPE));
        NativeSmokeSupport.invoke(plugin, "processInput", events);
        NativeSmokeSupport.expect(events.get(0).isConsumed(), "Escape is consumed by the overlay");
        NativeSmokeSupport.advance(root, 2);
        NativeSmokeSupport.check(NativeSmokeSupport.getField(view, "overlay") == null, "Escape closes the overlay");

        NativeSmokeSupport.invoke(view, "open", true);
        NativeSmokeSupport.advance(root, 30);
        NativeSmokeSupport.render(root);
        NativeSmokeSupport.screenshot("milestones.png");
        NativeSmokeSupport.expect(hasHit(view, "captains_aptitude_buy_cache", true),
                "Milestones overlay lays out an enabled cache button");
        NativeSmokeSupport.expect(!hitsOverlap(view), "milestone buttons never overlap");
    }

    private static void cursor(CaptainSkillPanel view, float x, float y) throws Exception {
        NativeSmokeSupport.setField(view, "mouseX", x);
        NativeSmokeSupport.setField(view, "mouseY", y);
    }

    private static List<?> hits(CaptainSkillPanel view) throws Exception {
        return (List<?>) NativeSmokeSupport.getField(view, "hits");
    }

    private static Object firstEnabledLearn(CaptainSkillPanel view) throws Exception {
        for (Object hit : hits(view)) {
            if (((String) NativeSmokeSupport.getField(hit, "action")).startsWith("learn:")
                    && (Boolean) NativeSmokeSupport.getField(hit, "enabled")) return hit;
        }
        throw new AssertionError("no enabled Learn button");
    }

    private static boolean hasHit(CaptainSkillPanel view, String actionPrefix, boolean enabled) throws Exception {
        for (Object hit : hits(view)) {
            String action = (String) NativeSmokeSupport.getField(hit, "action");
            if (action.startsWith(actionPrefix) && (Boolean) NativeSmokeSupport.getField(hit, "enabled") == enabled) return true;
        }
        return false;
    }

    private static boolean hitsOverlap(CaptainSkillPanel view) throws Exception {
        List<?> all = hits(view);
        for (int i = 0; i < all.size(); i++) {
            float[] a = box(all.get(i));
            for (int j = i + 1; j < all.size(); j++) {
                float[] b = box(all.get(j));
                if (a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3]) return true;
            }
        }
        return false;
    }

    private static float[] box(Object hit) throws Exception {
        return new float[] { (Float) NativeSmokeSupport.getField(hit, "x"), (Float) NativeSmokeSupport.getField(hit, "y"),
                (Float) NativeSmokeSupport.getField(hit, "w"), (Float) NativeSmokeSupport.getField(hit, "h") };
    }

    private static ButtonAPI button(Object root, String prefix) throws Exception {
        return (ButtonAPI) NativeSmokeSupport.findComponent(root, c -> c instanceof ButtonAPI
                && ((ButtonAPI) c).getText() != null && ((ButtonAPI) c).getText().startsWith(prefix)
                && ((UIComponentAPI) c).getOpacity() > 0f);
    }

    private static void describe(String name, PositionAPI p) {
        NativeSmokeSupport.log(name + " x=" + p.getX() + " y=" + p.getY() + " w=" + p.getWidth() + " h=" + p.getHeight());
    }

    private static boolean overlaps(PositionAPI a, PositionAPI b) {
        return a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
    }

    private static boolean inside(PositionAPI a, PositionAPI outer) {
        return a.getX() >= outer.getX() && a.getY() >= outer.getY()
                && a.getX() + a.getWidth() <= outer.getX() + outer.getWidth()
                && a.getY() + a.getHeight() <= outer.getY() + outer.getHeight();
    }
}
