package indevo.abilities.splitfleet.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.dialogue.DetachmentCargoLoad;
import org.apache.log4j.Logger;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.FieldMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.MethodType;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;

import java.util.List;

// cargo picker dialog, seen as obf com.fs.starfarer.ui.newui._ in 0.98a-RC8, the only class there with a CargoPickerListener field
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.ui.newui",
        fieldMatches = @FieldMatch(type = CargoPickerListener.class)))
public class DetachmentCargoPickerPatch {

    public static final Logger log = Global.getLogger(DetachmentCargoPickerPatch.class);

    private static final String DATA_KEY = "IndEvo_detachmentLoadButtons";
    private static final float BUTTON_WIDTH = 150f;
    private static final float BUTTON_HEIGHT = 25f;
    private static final float BUTTON_GAP = 3f;
    private static final float PAD = 10f;
    private static final float GROUP_GAP = 10f;

    private static final FieldQuerySpec LISTENER_FIELD = FieldQuery.create().type(CargoPickerListener.class).build();
    private static final MethodQuerySpec GET_INNER_PANEL = MethodQuery.create().methodName("getInnerPanel").parameterCount(0).build();
    private static final MethodQuerySpec GET_CHILDREN = MethodQuery.create().methodName("getChildrenCopy").parameterCount(0).build();
    private static final MethodQuerySpec UPDATE_CARGO_VIEWS = MethodQuery.create().methodName("updateCargoViews").parameterCount(0).build();
    private static final MethodQuerySpec GET_TRANSFER_HANDLER = MethodQuery.create().methodName("getTransferHandler").parameterCount(0).build();
    private static final MethodQuerySpec GET_PICKED_UP_STACK = MethodQuery.create().methodName("getPickedUpStack").parameterCount(0).build();
    private static final MethodQuerySpec RESET_TRANSACTION = MethodQuery.create().methodName("resetTransaction").parameterCount(0).build();

    @After(target = @MethodMatch(methodType = MethodType.CONSTRUCTOR))
    public static void afterCreate(AfterContext context) {
        if (!(context.getField(LISTENER_FIELD).get() instanceof DetachmentCargoLoad.Picker picker)) return;

        try {
            if (!(context.getMethod(GET_INNER_PANEL).call() instanceof UIPanelAPI inner)) throw new IllegalStateException("Cargo picker inner panel is not a UIPanelAPI");

            LoadButtons buttons = new LoadButtons(picker);
            // vanilla cancel sits bottom right with confirm left of it, both 150 wide and 3 apart
            inner.addComponent(buttons.create()).inBR(PAD + 2 * BUTTON_WIDTH + BUTTON_GAP + GROUP_GAP, PAD);
            context.getData().putWeak(DATA_KEY, buttons);
        } catch (RuntimeException e) {
            log.error("Could not add the load buttons to the detachment cargo picker", e);
        }
    }

    // a press runs here once the cursor holds no stack
    @After(target = @MethodMatch(methodName = "advance", parameterTypes = float.class))
    public static void afterAdvance(AfterContext context) {
        LoadButtons buttons = context.getData().get(DATA_KEY);
        if (buttons == null || buttons.pending == null) return;

        try {
            Object cargoPanel = getCargoPanel(context);
            Object transfers = context.getMethod(GET_TRANSFER_HANDLER, cargoPanel).call();

            if (transfers != null) {
                if (context.getMethod(GET_PICKED_UP_STACK, transfers).call() != null) return;
                // the picker's undo list would replay against the changed cargo on cancel
                context.getMethod(RESET_TRANSACTION, transfers).call();
            }

            buttons.picker.load(buttons.pending);
            context.getMethod(UPDATE_CARGO_VIEWS, cargoPanel).call();
        } catch (RuntimeException e) {
            log.error("Could not load the detachment cargo in the cargo picker", e);
        }

        buttons.done();
    }

    private static Object getCargoPanel(AfterContext context) {
        Object inner = context.getMethod(GET_INNER_PANEL).call();
        List<?> children = context.getMethod(GET_CHILDREN, inner).call();

        for (Object child : children) {
            if (context.hasMethod(UPDATE_CARGO_VIEWS, child)) return child;
        }

        throw new IllegalStateException("No cargo panel in the cargo picker");
    }

    private static class LoadButtons extends BaseCustomUIPanelPlugin {
        private final DetachmentCargoLoad.Picker picker;
        private final ButtonAPI[] buttons = new ButtonAPI[2];
        private DetachmentCargoLoad.Mode pending;

        LoadButtons(DetachmentCargoLoad.Picker picker) {
            this.picker = picker;
        }

        CustomPanelAPI create() {
            CustomPanelAPI panel = Global.getSettings().createCustom(BUTTON_WIDTH * 2 + BUTTON_GAP, BUTTON_HEIGHT, this);

            buttons[0] = addButton(panel, 0f, "Load min", DetachmentCargoLoad.Mode.MIN,
                    "Sets the detachment's supplies to repairs plus one month and its crew to the minimum, moving out marines that take the room. Its fuel tanks are filled, up to half of all fuel. Anything your ships can not hold goes to the detachment if it has room.");
            buttons[1] = addButton(panel, BUTTON_WIDTH + BUTTON_GAP, "Load max", DetachmentCargoLoad.Mode.MAX,
                    "Fills the detachment's supplies and crew up to its capacity. You keep repairs plus one month of supplies and your minimum crew. Its fuel tanks are filled, up to half of all fuel. Anything your ships can not hold goes to the detachment if it has room.");

            return panel;
        }

        private ButtonAPI addButton(CustomPanelAPI panel, float x, String text, DetachmentCargoLoad.Mode mode, String help) {
            TooltipMakerAPI el = panel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            el.setButtonFontOrbitron20();
            ButtonAPI button = el.addButton(text, mode, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            el.addTooltipToPrevious(new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 300f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara(help, 0f);
                }
            }, TooltipMakerAPI.TooltipLocation.ABOVE);

            panel.addUIElement(el).inTL(x, 0f);
            return button;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (!(buttonId instanceof DetachmentCargoLoad.Mode mode) || pending != null) return;

            pending = mode;
            for (ButtonAPI b : buttons) b.setEnabled(false);
        }

        void done() {
            pending = null;
            for (ButtonAPI b : buttons) b.setEnabled(true);
        }
    }
}
