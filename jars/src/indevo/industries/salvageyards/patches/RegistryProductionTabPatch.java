package indevo.industries.salvageyards.patches;

import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.campaign.command.CustomProductionPanel;
import com.fs.starfarer.campaign.command.FighterBlueprintBrowser;
import com.fs.starfarer.campaign.command.ShipBlueprintBrowser;
import com.fs.starfarer.campaign.command.WeaponBlueprintBrowser;
import indevo.industries.salvageyards.plugins.RegistryProductionDelegate;
import org.apache.log4j.Logger;
import patchlib.api.context.AfterContext;
import patchlib.api.context.ConstructorCallContext;
import patchlib.api.context.Context;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.StaticType;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectConstructorCall;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;
import patchlib.api.store.PatchData;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// custom production picker, readable classes in an obf package (0.98a-RC8), every target here is inferred
@Patch(target = @ClassMatch(type = CustomProductionPanel.class))
public class RegistryProductionTabPatch {

    public static final Logger log = Global.getLogger(RegistryProductionTabPatch.class);

    private static final String BUTTON_KEY = "IndEvo_registryTab";
    private static final String BROWSER_KEY = "IndEvo_registryBrowser";
    private static final String PLACED_KEY = "IndEvo_registryPlaced";
    private static final String READY_KEY = "IndEvo_registryReady";

    private static final String FIGHTERS_LABEL = "Fighters";
    private static final float PAD = 10f;
    private static final float TAB_GAP = 1f;

    private static final FieldQuerySpec TAB_MAP = FieldQuery.create().type(Map.class).build();
    private static final MethodQuerySpec GET_FADER = MethodQuery.create().methodName("getFader").parameterCount(0).build();
    private static final MethodQuerySpec GET_TAGS = MethodQuery.create().methodName("getTags").parameterCount(0).build();
    private static final MethodQuerySpec CHECK_ALL = MethodQuery.create().methodName("checkAll").parameterCount(0).build();

    // the browser's tooltips are anonymous classes that only reach the browser, not the panel's PatchData
    private static final Set<Object> registryBrowsers = Collections.newSetFromMap(new WeakHashMap<>());

    public static boolean isRegistryBrowser(Object browser) {
        return browser != null && registryBrowsers.contains(browser);
    }

    // the tab buttons come from one obf static factory (label, font, Alignment, CutStyle, listener), the fighters tab is the last
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "createIfNeeded", parameterCount = 0),
            call = @MethodMatch(parameterCount = 5, staticType = StaticType.STATIC))
    public static void redirectTabButton(MethodCallContext context) {
        context.setResult(context.call());
        if (!FIGHTERS_LABEL.equals(context.getCallArg(0)) || getDelegate(context) == null) return;

        Object[] args = context.getCallArgs().clone();
        args[0] = RegistryProductionDelegate.TAB_LABEL;
        if (context.call(args) instanceof ButtonAPI tab) context.getData().put(BUTTON_KEY, tab);
        else log.error("Custom production tab factory did not return a button, no registry tab");
    }

    // the browser reads the delegate's hulls in its constructor, a second instance gets the registry hulls before createUI
    @RedirectConstructorCall(
            target = @MethodMatch(methodName = "createIfNeeded", parameterCount = 0),
            type = @ClassMatch(type = ShipBlueprintBrowser.class))
    @SuppressWarnings("unchecked")
    public static void redirectShipBrowser(ConstructorCallContext context) {
        context.setResult(context.call());

        RegistryProductionDelegate delegate = getDelegate(context);
        if (delegate == null) return;

        ShipBlueprintBrowser browser = (ShipBlueprintBrowser) context.call();
        List<Object> hulls = (List<Object>) (List<?>) browser.getKnownHulls();
        hulls.clear();
        for (String id : delegate.getRegistryHulls()) hulls.add(Global.getSettings().getHullSpec(id));

        context.getData().put(BROWSER_KEY, browser);
        registryBrowsers.add(browser);
    }

    @After(target = @MethodMatch(methodName = "createIfNeeded", parameterCount = 0))
    public static void afterCreate(AfterContext context) {
        PatchData data = context.getData();
        if (data.has(PLACED_KEY) || !data.has(BUTTON_KEY) || !data.has(BROWSER_KEY)) return;
        data.put(PLACED_KEY, true);

        CustomProductionPanel panel = context.getInferredSelf();
        ButtonAPI tab = data.get(BUTTON_KEY);
        ShipBlueprintBrowser browser = data.get(BROWSER_KEY);
        Map<Object, Object> tabs = context.<Map<Object, Object>>getField(TAB_MAP).get();

        ButtonAPI ships = null;
        ButtonAPI weapons = null;
        ButtonAPI fighters = null;
        UIComponentAPI shipBrowser = null;

        // vanilla puts the shortcut key into the button text, the browser type names the tab
        for (Map.Entry<Object, Object> e : tabs.entrySet()) {
            if (!(e.getKey() instanceof ButtonAPI button)) continue;

            if (e.getValue() instanceof ShipBlueprintBrowser b) {
                ships = button;
                shipBrowser = b;
            } else if (e.getValue() instanceof WeaponBlueprintBrowser) weapons = button;
            else if (e.getValue() instanceof FighterBlueprintBrowser) fighters = button;
        }

        if (ships == null || weapons == null || fighters == null || shipBrowser == null) {
            log.error("Custom production tabs not found, no registry tab");
            return;
        }

        PositionAPI shipsPos = ships.getPosition();
        panel.addComponent(tab).setSize(shipsPos.getWidth(), shipsPos.getHeight()).rightOfTop(ships, TAB_GAP);
        weapons.getPosition().rightOfTop(tab, TAB_GAP);
        fighters.getPosition().rightOfTop(weapons, TAB_GAP);

        PositionAPI browserPos = shipBrowser.getPosition();
        panel.addComponent(browser).setSize(browserPos.getWidth(), browserPos.getHeight()).belowLeft(ships, PAD);
        browser.createUI();
        tabs.put(tab, browser);
        data.put(READY_KEY, true);

        boolean empty = browser.getKnownHulls().isEmpty();
        tab.setEnabled(!empty);

        if (!empty && !ships.isEnabled()) panel.actionPerformed(null, tab);
        else {
            browser.getFader().forceOut();
            tab.unhighlight();
        }
    }

    // vanilla only switches its own three tabs, the map entry already hides this one when another opens
    @After(target = @MethodMatch(methodName = "actionPerformed", parameterCount = 2))
    public static void afterAction(AfterContext context) {
        PatchData data = context.getData();
        if (!data.has(READY_KEY) || context.getArg(1) != data.get(BUTTON_KEY)) return;

        Map<Object, Object> tabs = context.<Map<Object, Object>>getField(TAB_MAP).get();
        for (Map.Entry<Object, Object> e : tabs.entrySet()) {
            Fader fader = context.getMethod(GET_FADER, e.getValue()).call();
            ButtonAPI button = (ButtonAPI) e.getKey();

            if (e.getKey() == context.getArg(1)) {
                fader.forceIn();
                button.highlight();
            } else {
                fader.forceOut();
                button.unhighlight();
            }
        }
    }

    // picker mode checks every tag on the first frame, the extra browser needs the same
    @After(target = @MethodMatch(methodName = "restoreTableUIState", parameterCount = 0))
    public static void afterRestore(AfterContext context) {
        PatchData data = context.getData();
        if (!data.has(READY_KEY)) return;

        ShipBlueprintBrowser browser = data.get(BROWSER_KEY);
        Object tags = context.getMethod(GET_TAGS, browser).call();
        context.getMethod(CHECK_ALL, tags).call();
        browser.updateTable();
    }

    private static RegistryProductionDelegate getDelegate(Context context) {
        return context.getSelf() instanceof CustomProductionPanel panel && panel.getDelegate() instanceof RegistryProductionDelegate d ? d : null;
    }
}
