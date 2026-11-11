import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.fleet.FleetMember;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatFleetManager;
import com.fs.starfarer.combat.CombatState;
import com.fs.starfarer.combat.entities.Ship;
import fleetformations.DeploymentPicker;
import fleetformations.FormationCombatPlugin;
import fleetformations.FormationLayout.Role;
import fleetformations.FormationStorage;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FleetFormationsNativeSmoke extends BaseModPlugin {
    private static final int TILE = 58;
    private static float width, height;
    private static File output;

    @Override
    public void onApplicationLoad() {
        try {
            probe();
            System.out.println("NATIVE_SMOKE COMPLETE");
            System.out.flush();
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.err.flush();
            System.exit(1);
        }
    }

    private static void probe() throws Exception {
        output = new File(System.getProperty("native.smoke.output"));
        width = Global.getSettings().getScreenWidth();
        height = Global.getSettings().getScreenHeight();
        Global.setSector(CampaignEngine.getInstance());
        CombatEngine engine = CombatEngine.getInstance();
        CombatFleetManager player = new CombatFleetManager(0);
        engine.setFleetManagers(Arrays.asList(player, new CombatFleetManager(1)));
        FleetMember frigate = member("tempest_Attack", "smoke-tempest");
        FleetMember capital = member("onslaught_Standard", "smoke-onslaught");
        player.getReserves().add(frigate);
        player.getReserves().add(capital);
        Global.getSector().getPersistentData().put(FormationStorage.DEPLOYMENTS_KEY,
                FormationStorage.encodeDeployments(List.of(
                        new FormationStorage.SavedDeployment("Missing", List.of(
                                new FormationStorage.SavedShip("gone", "Gone", "Frigate", "unknown_hull",
                                        ShipAPI.HullSize.FRIGATE, Role.COMBAT, 8))),
                        new FormationStorage.SavedDeployment("Strike", List.of(
                                new FormationStorage.SavedShip(frigate.getId(), frigate.getShipName(),
                                        frigate.getHullSpec().getHullName(), 8))),
                        new FormationStorage.SavedDeployment("Heavy", List.of(
                                new FormationStorage.SavedShip(frigate.getId(), frigate.getShipName(),
                                        frigate.getHullSpec().getHullName(), 8),
                                new FormationStorage.SavedShip(capital.getId(), capital.getShipName(),
                                        capital.getHullSpec().getHullName(), 40))))));

        CustomPanelAPI root = Global.getSettings().createCustom(width, height, null);
        Object reference = deploymentDialog(root);
        invoke(reference, "show", 0.3f, 0.75f);
        for (int i = 0; i < 90; i++) root.advance(1f / 60f);
        beginFrame();
        root.render(1f);
        screenshot("native-reference.png");
        Object inner = invoke(reference, "getInnerPanel");
        byte[][] expected = {nativePixels(findButton(inner, frigate)), nativePixels(findButton(inner, capital))};

        engine.setStateToReturnTo("Campaign State");
        engine.setContext(new BattleCreationContext(null, FleetGoal.ATTACK, null, FleetGoal.ATTACK));
        engine.setPlayerShip((Ship) engine.createFXDrone(Global.getSettings().createEmptyVariant(
                "shuttlepod_xfer", Global.getSettings().getHullSpec("shuttlepod"))));
        check(engine.getPlayerShip().isShuttlePod(), "Fixture uses a real command shuttle");
        System.out.println("NATIVE_SMOKE gate campaign=" + engine.isInCampaign()
                + " simulation=" + engine.isSimulation() + " mission=" + engine.isMission()
                + " over=" + engine.isCombatOver() + " shuttle=" + engine.getPlayerShip().isShuttlePod());
        CombatState state = new CombatState();
        invoke(engine, "setCombatUI", state);
        engine.init();
        invoke(state, "resetSomeUIThings");
        invoke(state, "setPlayerShip", engine.getPlayerShip());
        engine.preCombat();
        engine.setViewport(-width / 2f, -height / 2f, width, height);
        check(invoke(engine, "getCombatMap") != null && engine.getMapWidth() > 0 && engine.getMapHeight() > 0,
                "Native combat map exists and has nonzero dimensions");
        check(engine.getViewport() != null && engine.getViewport().getVisibleWidth() == width
                        && engine.getViewport().getVisibleHeight() == height,
                "Native combat viewport is initialized");
        UIPanelAPI widgets = (UIPanelAPI) getField(state, "widgetPanel");
        UIPanelAPI warroom = newWarroom(state, widgets);
        setField(state, "warroom", warroom);
        widgets.addComponent(warroom).inBL(0f, 0f);
        invoke(warroom, "centerOn", 0f, 0f);
        state.showWarroom();
        if (!Mouse.isCreated()) Mouse.create();
        for (int i = 0; i < 90; i++) widgets.advance(1f / 60f);
        check(state.isShowingCommandUI() && state.getCommandUIOpacity() > 0f,
                "The real warroom is open and visible");

        List<?> mounted = (List<?>) invoke(widgets, "getChildrenCopy");
        state.showDeployDialog();
        check(state.isShowingCommandUI(), "The patched hook leaves the real warroom open");
        check(!state.isShowingDialog() && !state.isShowingDeploymentDialog(), "No hidden vanilla modal remains");
        check(getField(state, "fdd") == null, "Patched showDeployDialog clears the vanilla deployment dialog");
        check(((List<?>) invoke(widgets, "getChildrenCopy")).size() == mounted.size(),
                "The vanilla deployment dialog never attaches to the widget panel");
        check(engine.isPaused(), "Patched picker pauses combat");
        Field pending = DeploymentPicker.class.getDeclaredField("pendingUI");
        pending.setAccessible(true);
        Object prepared = pending.get(null);
        check(prepared != null, "Real patched method prepares the replacement UI");
        @SuppressWarnings("unchecked")
        Map<String, UIComponentAPI> icons = (Map<String, UIComponentAPI>) invoke(prepared, "icons");
        check(invoke(prepared, "parent") == widgets, "Picker is prepared for the actual combat widget panel");
        check(icons.size() == 2, "Both reserve members have native buttons");

        beginFrame();
        int index = 0;
        for (FleetMember member : List.of(frigate, capital)) {
            UIComponentAPI button = icons.get(member.getId());
            check(button != null && invoke(button, "getParent") == null, "Native button is detached from its old parent");
            check(button.getPosition().getWidth() == TILE && button.getPosition().getHeight() == TILE,
                    "Native deployment button remains 58 by 58");
            float x = 280 + index * 125, y = height - 260;
            button.getPosition().setLocation(x, y);
            check(button.getPosition().getX() == x && button.getPosition().getY() == y,
                    "Requested screen coordinates survive native layout recomputation");
            byte[] before = region(x + 12, y + 10, 34, 34);
            button.render(1f);
            check(!Arrays.equals(before, region(x + 12, y + 10, 34, 34)), "Ship artwork draws inside the tile, away from DP text");
            check(Arrays.equals(expected[index], region(x, y, TILE, TILE)), "Captured icon pixels match vanilla reference exactly");
            System.out.println("NATIVE_SMOKE native button " + member.getId() + " matches vanilla pixels at " + x + "," + y);
            index++;
        }
        CustomPanelAPI oldOwner = Global.getSettings().createCustom(TILE, TILE, null);
        TooltipMakerAPI oldTooltip = oldOwner.createUIElement(TILE, TILE, false);
        oldTooltip.addShipList(1, 1, TILE - 12, java.awt.Color.CYAN,
                Collections.<FleetMemberAPI>singletonList(frigate), 0f);
        UIComponentAPI oldList = oldTooltip.getPrev();
        oldList.getPosition().setLocation(70, height - 260);
        oldList.render(1f);
        System.out.println("NATIVE_SMOKE parented grid requested=70," + (height - 260)
                + " actual=" + oldList.getPosition().getX() + "," + oldList.getPosition().getY());
        screenshot("icon-comparison.png");

        FormationCombatPlugin plugin = new FormationCombatPlugin();
        plugin.init(engine);
        player.setMaxStrength(100);
        plugin.advance(1f / 60f, List.of());
        check(pending.get(null) == null, "Picker consumes the prepared native UI");
        List<?> children = (List<?>) invoke(widgets, "getChildrenCopy");
        check(children.contains(warroom) && !children.isEmpty(), "The real warroom remains attached");
        Object top = children.get(children.size() - 1);
        check(top instanceof CustomPanelAPI && ((CustomPanelAPI) top).getPlugin() instanceof DeploymentPicker,
                "The picker's native panel is the topmost widget");
        CustomPanelAPI overlay = (CustomPanelAPI) top;
        DeploymentPicker picker = (DeploymentPicker) overlay.getPlugin();
        check(invoke(overlay, "getParent") == widgets && children.indexOf(warroom) < children.indexOf(overlay),
                "The native overlay is attached above the warroom");
        check(overlay.getPosition().getWidth() == width && overlay.getPosition().getHeight() == height,
                "The native overlay covers the screen");
        check(state.isShowingCommandUI(), "The warroom remains open while the picker is mounted");
        @SuppressWarnings("unchecked")
        List<FormationStorage.SavedDeployment> deployments =
                (List<FormationStorage.SavedDeployment>) getField(picker, "deployments");
        check(deployments.size() == 3 && deployments.get(0).name.equals("Missing")
                        && deployments.get(1).name.equals("Strike")
                        && deployments.get(2).name.equals("Heavy"),
                "Per-save deployment list migrates to global common data");

        widgets.removeComponent(overlay);
        beginFrame();
        byte[] blank = region(0, 0, (int) width, (int) height);
        invoke(warroom, "renderBelowCombatWidgets");
        byte[] mapPixels = region(0, 0, (int) width, (int) height);
        check(changedPixels(blank, mapPixels) > 1000, "The real warroom map renders visible pixels");
        invoke(warroom, "renderAboveCombatWidgets");
        widgets.render(1f);
        byte[] background = region(0, 0, (int) width, (int) height);
        screenshot("warroom.png");
        widgets.addComponent(overlay).inBL(0f, 0f);
        widgets.bringComponentToTop(overlay);
        children = (List<?>) invoke(widgets, "getChildrenCopy");
        check(children.get(children.size() - 1) == overlay, "Picker is topmost when the composite is rendered");
        float oldX = icons.get(frigate.getId()).getPosition().getX();
        icons.get(frigate.getId()).getPosition().setLocation(-1f, -1f);
        beginFrame();
        invoke(warroom, "renderBelowCombatWidgets");
        invoke(warroom, "renderAboveCombatWidgets");
        widgets.render(1f);
        byte[] composite = region(0, 0, (int) width, (int) height);
        check(changedPixels(background, composite) > 1000,
                "Attached native panel renders the picker over the warroom");
        check(oldX != icons.get(frigate.getId()).getPosition().getX()
                        && icons.get(frigate.getId()).getPosition().getX() > 0f,
                "The widget-panel render callback positioned the real ship icon on screen");
        @SuppressWarnings("unchecked")
        List<float[]> tiles = (List<float[]>) getField(picker, "tileHits");
        check(tiles.size() == 2, "Both reserve ship tiles were laid out");
        check(tiles.get(1)[0] >= tiles.get(0)[0] + tiles.get(0)[2] + 4f,
                "Adjacent ship tiles have visible horizontal padding");
        screenshot("picker.png");
        @SuppressWarnings("unchecked")
        List<float[]> savedRows = (List<float[]>) getField(picker, "savedHits");
        check(!savedRows.isEmpty(), "Saved deployment list rows are laid out");
        float[] savedRow = savedRows.get(0);
        Mouse.setCursorPosition((int) (savedRow[0] + savedRow[2] / 2f),
                (int) (savedRow[1] + savedRow[3] / 2f));
        beginFrame();
        invoke(warroom, "renderBelowCombatWidgets");
        invoke(warroom, "renderAboveCombatWidgets");
        widgets.render(1f);
        screenshot("saved-tooltip.png");
        Mouse.setCursorPosition(1, 1);
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "Native picker leaves no OpenGL error");
        System.out.println("NATIVE_SMOKE warroom visible, picker topmost, attached render, and native pixel parity passed");

        @SuppressWarnings("unchecked")
        Set<String> selected = (Set<String>) getField(picker, "selected");
        check(selected.size() == 1 && selected.contains(frigate.getId()), "The first reserve is selected by default");
        selected.clear();
        invoke(picker, "loadDeployment");
        check(selected.size() == 1 && selected.contains(frigate.getId()),
                "A missing deployment ship is replaced by a same-size same-role reserve ship");
        InputEventAPI deselect = key(Keyboard.KEY_SPACE);
        plugin.processInputPreCoreControls(1f / 60f, List.of(deselect));
        check(deselect.isConsumed() && selected.isEmpty() && state.isShowingCommandUI(),
                "Selection input is consumed while the real warroom remains open");
        InputEventAPI select = key(Keyboard.KEY_SPACE);
        plugin.processInputPreCoreControls(1f / 60f, List.of(select));
        check(select.isConsumed() && selected.size() == 1 && selected.contains(frigate.getId())
                        && state.isShowingCommandUI(),
                "Reselecting the ship leaves the real warroom open");
        invoke(picker, "saveDeployment");
        check(deployments.size() == 4 && deployments.get(0).name.equals("Deployment 1")
                        && deployments.get(0).ships.size() == 1
                        && Global.getSettings().fileExistsInCommon(FormationStorage.DEPLOYMENTS_FILE),
                "Save adds the current selection as a new global deployment");
        invoke(picker, "deleteDeployment");
        check(deployments.size() == 3 && deployments.get(0).name.equals("Missing"),
                "Delete removes the selected global deployment");
        InputEventAPI clear = key(Keyboard.KEY_SPACE);
        plugin.processInputPreCoreControls(1f / 60f, List.of(clear));
        plugin.processInputPreCoreControls(1f / 60f, List.of(key(Keyboard.KEY_RIGHT), key(Keyboard.KEY_SPACE)));
        check(selected.size() == 1 && selected.contains(capital.getId()), "Fixture selects the capital alone");
        invoke(picker, "addDeployment");
        check(selected.size() == 2 && selected.contains(frigate.getId()) && selected.contains(capital.getId()),
                "Plus adds saved ships without replacing the current selection");
        invoke(picker, "loadDeployment");
        check(selected.size() == 1 && selected.contains(frigate.getId()),
                "Load replaces the current selection with the saved ships");
        InputEventAPI deploy = key(Keyboard.KEY_RETURN);
        plugin.processInputPreCoreControls(1f / 60f, List.of(deploy));
        check(deploy.isConsumed(), "Deploy input is consumed");
        check(!state.isShowingCommandUI(), "Deploy closes the real warroom");
        check(invoke(overlay, "getParent") == null
                        && !((List<?>) invoke(widgets, "getChildrenCopy")).contains(overlay),
                "Deploy removes the native picker overlay");
        ShipAPI deployed = player.getShipFor(frigate);
        check(deployed != null && deployed.getFacing() == 90f, "Real fleet manager spawns the selected ship facing 90 degrees");
        check(player.getDeployedCopy().size() == 1 && player.getDeployedCopy().contains(frigate),
                "Only the selected reserve ship is deployed");
        boolean visibleAtDeploy = engine.getShips().contains(deployed);
        engine.executeAdds();
        deployed.getLocation().set(100, 0);
        plugin.advance(1f / 60f, List.of());
        check(engine.getShips().contains(deployed), "The real combat engine adds the deployed ship");
        boolean editing = (Boolean) invoke(plugin, "isEditing");
        System.out.println("NATIVE_SMOKE deploy warroomClosed=" + !state.isShowingCommandUI()
                + " overlayRemoved=" + !((List<?>) invoke(widgets, "getChildrenCopy")).contains(overlay)
                + " spawned=" + deployed.getFleetMemberId() + " facing=" + deployed.getFacing()
                + " visibleAtDeploy=" + visibleAtDeploy + " editing=" + editing);
        check(editing, "The deferred deployment opens the formation editor after the engine flushes queued ships");
        beginFrame();
        plugin.renderInUICoords(engine.getViewport());
        screenshot("formation-editor.png");
        List<?> formations = (List<?>) getField(plugin, "formations");
        Set<String> editorSelection = (Set<String>) getField(plugin, "selection");
        check(formations.isEmpty() && editorSelection.isEmpty(), "Formation editor starts with no saved presets or selection");
        check(!(Boolean) getField(plugin, "formationsOpen"), "Formation saver starts closed behind its button");
        beginFrame();
        plugin.renderInUICoords(engine.getViewport());
        screenshot("formation-submenu-closed.png");
        float[] formationsButton = controlsFormationsBounds();
        int toggleX = (int) (formationsButton[0] + formationsButton[2] / 2f);
        int toggleY = (int) (formationsButton[1] + formationsButton[3] / 2f);
        InputEventAPI toggleDown = mouse("down", toggleX, toggleY);
        InputEventAPI toggleUp = mouse("up", toggleX, toggleY);
        plugin.processInputPreCoreControls(1f / 60f, List.of(toggleDown, toggleUp));
        check((Boolean) getField(plugin, "formationsOpen") && editorSelection.isEmpty(),
                "Formations button opens the submenu without selecting a world ship");
        beginFrame();
        plugin.renderInUICoords(engine.getViewport());
        screenshot("formation-panel.png");
        Object presetsPanel = getField(plugin, "presetsPanel");
        check((float) getField(presetsPanel, "x") + (float) getField(presetsPanel, "w") < formationsButton[0],
                "Formation submenu opens directly to the left of the controls panel");
        int saveX = (int) ((float) getField(presetsPanel, "saveX") + (float) getField(presetsPanel, "buttonW") / 2f);
        int saveY = (int) ((float) getField(presetsPanel, "saveY") + 14f);
        int loadX = (int) ((float) getField(presetsPanel, "loadX") + (float) getField(presetsPanel, "buttonW") / 2f);
        int deleteY = (int) ((float) getField(presetsPanel, "deleteY") + 14f);
        InputEventAPI saveDown = mouse("down", saveX, saveY), saveUp = mouse("up", saveX, saveY);
        plugin.processInputPreCoreControls(1f / 60f, List.of(saveDown, saveUp));
        check(saveDown.isConsumed() && saveUp.isConsumed() && editorSelection.isEmpty(),
                "Clicking the formation panel never selects a world ship");
        check(formations.size() == 1
                        && ((FormationStorage.SavedFormation) formations.get(0)).name.equals("Formation 1")
                        && Global.getSettings().fileExistsInCommon(FormationStorage.FORMATIONS_FILE),
                "Formation Save persists a named preset in global common data");
        beginFrame();
        plugin.renderInUICoords(engine.getViewport());
        screenshot("formation-saved.png");
        Mouse.setCursorPosition((int) ((float) getField(presetsPanel, "listX")
                        + (float) getField(presetsPanel, "listW") - 20f),
                (int) ((float) getField(presetsPanel, "listTop") - 12f));
        beginFrame();
        plugin.renderInUICoords(engine.getViewport());
        screenshot("formation-tooltip.png");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "Formation tooltip leaves no OpenGL error");
        Mouse.setCursorPosition(1, 1);
        deployed.getLocation().set(0, 0);
        InputEventAPI loadDown = mouse("down", loadX, saveY), loadUp = mouse("up", loadX, saveY);
        plugin.processInputPreCoreControls(1f / 60f, List.of(loadDown, loadUp));
        check(deployed.getLocation().x != 0 || deployed.getLocation().y != 0,
                "Formation Load restores the saved frigate position");
        InputEventAPI deleteDown = mouse("down", saveX, deleteY), deleteUp = mouse("up", saveX, deleteY);
        plugin.processInputPreCoreControls(1f / 60f, List.of(deleteDown, deleteUp));
        check(formations.isEmpty() && FormationStorage.decodeFormations(
                        Global.getSettings().readJSONFromCommon(
                                FormationStorage.FORMATIONS_FILE, false).toString()).isEmpty(),
                "Formation Delete removes the selected preset and persists an empty global list");
        beginFrame();
        plugin.renderInUICoords(engine.getViewport());
        screenshot("formation-empty.png");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "Formation preset panel leaves no OpenGL error");
        System.out.println("NATIVE_SMOKE formation submenu button, save/load/delete, and input isolation passed");
    }

    private static FleetMember member(String variant, String id) {
        FleetMember member = (FleetMember) Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant);
        member.setId(id);
        member.setShipName(id);
        member.getCrewComposition().setCrew(member.getMinCrew());
        member.getRepairTracker().setCR(0.7f);
        check(member.canBeDeployedForCombat(), "Synthetic reserve ship is deployable");
        return member;
    }

    private static Object deploymentDialog(CustomPanelAPI root) throws Exception {
        Class<?> type = CombatState.class.getDeclaredField("fdd").getType();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2 && parameters[0].isInstance(root) && !parameters[1].isPrimitive()) {
                constructor.setAccessible(true);
                return constructor.newInstance(root, null);
            }
        }
        throw new NoSuchMethodException("No native deployment dialog constructor matches root panel and callback");
    }

    private static UIPanelAPI newWarroom(CombatState state, UIPanelAPI root) throws Exception {
        Class<?> type = CombatState.class.getDeclaredField("warroom").getType();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2 && parameters[0].isInstance(state) && parameters[1].isInstance(root)) {
                constructor.setAccessible(true);
                return (UIPanelAPI) constructor.newInstance(state, root);
            }
        }
        throw new NoSuchMethodException("No native warroom constructor matches combat state and root panel");
    }

    private static UIComponentAPI findButton(Object panel, FleetMember member) throws Exception {
        Method getter = method(panel.getClass(), "getButtonForMember", 1);
        if (getter != null) return (UIComponentAPI) getter.invoke(panel, member);
        if (method(panel.getClass(), "getChildrenCopy", 0) != null) {
            for (Object child : (List<?>) invoke(panel, "getChildrenCopy")) {
                UIComponentAPI found = findButton(child, member);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static byte[] nativePixels(UIComponentAPI button) {
        check(button != null, "Vanilla reference contains the reserve button");
        beginFrame();
        button.render(1f);
        return region(button.getPosition().getX(), button.getPosition().getY(), TILE, TILE);
    }

    private static void setField(Object receiver, String name, Object value) throws Exception {
        Field field = CombatState.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(receiver, value);
    }

    private static Object getField(Object receiver, String name) throws Exception {
        Field field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static float[] controlsFormationsBounds() throws Exception {
        Class<?> type = Class.forName("fleetformations.ControlsPanel");
        Method method = type.getDeclaredMethod("formationsButtonBounds");
        method.setAccessible(true);
        return (float[]) method.invoke(null);
    }

    private static InputEventAPI key(int code) {
        boolean[] consumed = {false};
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(),
                new Class<?>[] {InputEventAPI.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isConsumed": return consumed[0];
                        case "consume": consumed[0] = true; return null;
                        case "isMouseEvent": return false;
                        case "isKeyDownEvent": return true;
                        case "getEventValue": return code;
                        case "isRepeat": return false;
                        default: throw new AssertionError(method.getName());
                    }
                });
    }

    private static InputEventAPI mouse(String action, int x, int y) {
        boolean[] consumed = {false};
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(),
                new Class<?>[] {InputEventAPI.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isConsumed": return consumed[0];
                        case "consume": consumed[0] = true; return null;
                        case "isMouseEvent": return true;
                        case "isKeyDownEvent": return false;
                        case "isRepeat": case "isShiftDown": case "isCtrlDown": return false;
                        case "getX": return x;
                        case "getY": return y;
                        case "getEventValue": return 1;
                        case "isLMBDownEvent": return action.equals("down");
                        case "isLMBUpEvent": return action.equals("up");
                        case "isMouseMoveEvent": return action.equals("move");
                        case "isMouseScrollEvent": return action.equals("scroll");
                        case "isRMBDownEvent": case "isRMBUpEvent": return false;
                        default: throw new AssertionError(method.getName());
                    }
                });
    }

    private static Method method(Class<?> type, String name, int count) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == count) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Object invoke(Object receiver, String name, Object... args) throws Exception {
        Method method = method(receiver.getClass(), name, args.length);
        if (method == null) throw new NoSuchMethodException(name);
        return method.invoke(receiver, args);
    }

    private static void beginFrame() {
        GL11.glViewport(0, 0, (int) (Display.getWidth() * Display.getPixelScaleFactor()),
                (int) (Display.getHeight() * Display.getPixelScaleFactor()));
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0.02f, 0.04f, 0.06f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, 0, height, -4000, 4000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static byte[] region(float x, float y, int w, int h) {
        float scaleX = Display.getWidth() * Display.getPixelScaleFactor() / width;
        float scaleY = Display.getHeight() * Display.getPixelScaleFactor() / height;
        int pw = Math.round(w * scaleX), ph = Math.round(h * scaleY);
        ByteBuffer pixels = BufferUtils.createByteBuffer(pw * ph * 4);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glReadPixels(Math.round(x * scaleX), Math.round(y * scaleY), pw, ph,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] bytes = new byte[pixels.remaining()];
        pixels.get(bytes);
        return bytes;
    }

    private static int changedPixels(byte[] first, byte[] second) {
        check(first.length == second.length, "Framebuffer dimensions match");
        int changed = 0;
        for (int i = 0; i < first.length; i += 4) {
            if (first[i] != second[i] || first[i + 1] != second[i + 1] || first[i + 2] != second[i + 2]) changed++;
        }
        return changed;
    }

    private static void screenshot(String name) throws Exception {
        int w = (int) width, h = (int) height;
        byte[] pixels = region(0, 0, w, h);
        int pw = Math.round(Display.getWidth() * Display.getPixelScaleFactor());
        int ph = Math.round(Display.getHeight() * Display.getPixelScaleFactor());
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                int offset = (x + y * pw) * 4;
                int rgb = ((pixels[offset] & 255) << 16) | ((pixels[offset + 1] & 255) << 8)
                        | (pixels[offset + 2] & 255);
                image.setRGB(x, ph - 1 - y, rgb);
            }
        }
        check(ImageIO.write(image, "png", new File(output, name)), "PNG writer is available");
        System.out.println("NATIVE_SMOKE screenshot=" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
