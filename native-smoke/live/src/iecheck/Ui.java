package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// reads what a tooltip really says, so text fixes can be checked without a screenshot
public final class Ui {
    private Ui() {
    }

    // for code that only wants a dialog to write to
    public static final class Bare implements InteractionDialogPlugin {
        private final Map<String, MemoryAPI> memory = new HashMap<>();

        @Override
        public void init(InteractionDialogAPI dialog) {
            memory.put(MemKeys.LOCAL, dialog.getInteractionTarget().getMemoryWithoutUpdate());
            memory.put(MemKeys.ENTITY, dialog.getInteractionTarget().getMemoryWithoutUpdate());
        }

        @Override
        public void optionSelected(String text, Object data) {
        }

        @Override
        public void optionMousedOver(String text, Object data) {
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public void backFromEngagement(EngagementResultAPI result) {
        }

        @Override
        public Object getContext() {
            return null;
        }

        @Override
        public Map<String, MemoryAPI> getMemoryMap() {
            return memory;
        }
    }

    public static TooltipMakerAPI tooltip() {
        CustomPanelAPI root = Global.getSettings().createCustom(500f, 3000f, null);
        return root.createUIElement(500f, 3000f, false);
    }

    public static String text(Object root) {
        StringBuilder out = new StringBuilder();
        walk(root, out);
        return out.toString();
    }

    // an option panel has no getter for the enabled state, its buttons do
    public static ButtonAPI button(Object node, String text) {
        if (node instanceof ButtonAPI && String.valueOf(((ButtonAPI) node).getText()).contains(text)) return (ButtonAPI) node;
        for (Object child : children(node)) {
            ButtonAPI found = button(child, text);
            if (found != null) return found;
        }
        return null;
    }

    private static List<?> children(Object node) {
        try {
            MethodHandle getter = MethodHandles.privateLookupIn(node.getClass(), MethodHandles.lookup()).findVirtual(node.getClass(), "getChildrenCopy", MethodType.methodType(List.class));
            return (List<?>) getter.invoke(node);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static void walk(Object node, StringBuilder out) {
        if (node instanceof LabelAPI) out.append(((LabelAPI) node).getText()).append('\n');
        for (Object child : children(node)) walk(child, out);
    }
}
