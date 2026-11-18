package indevo.industries.petshop.patches;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import indevo.config.Setting;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.plugins.PetCard;
import indevo.industries.petshop.plugins.PetsDialogPlugin;
import indevo.utils.helper.ReflectionUtils;
import indevo.utils.ui.Ui;
import org.apache.log4j.Logger;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.FieldMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.MethodType;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.MethodQuerySpec;

import java.util.List;

// refit hull mod row, seen as obf com.fs.starfarer.coreui.refit.ModWidget$o in 0.98.5a-RC4
// the only class there with a HullModSpecAPI field whose constructor calls createHullModTooltip, after the row is laid out
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.coreui.refit",
        fieldMatches = @FieldMatch(subtype = HullModSpecAPI.class)))
public class PetRefitRowPatch {

    public static final Logger log = Global.getLogger(PetRefitRowPatch.class);

    // vanilla puts the 34 px icon inRMid(5) and the name leftOfMid(icon, 10)
    private static final float RIGHT = 5f;
    private static final float NAME_GAP = 10f;
    private static final Object[] NO_ARGS = new Object[0];

    private static final MethodQuerySpec GET_CHILDREN = MethodQuery.create().methodName("getChildrenCopy").parameterCount(0).build();

    // args: spec, core ui, ship, member, the refit copy of the variant
    @RedirectMethodCall(
            target = @MethodMatch(methodType = MethodType.CONSTRUCTOR),
            call = @MethodMatch(methodName = "createHullModTooltip", parameterCount = 5))
    public static void afterTooltip(MethodCallContext context) {
        context.setResult(context.call());
        if (Global.getCurrentState() != GameState.CAMPAIGN || !Setting.PETS.enabled()) return;

        try {
            if (!(context.getCallArg(0) instanceof HullModSpecAPI spec) || !(context.getCallArg(4) instanceof ShipVariantAPI variant)) return;

            Pet pet = PetStatusManager.getInstance().getPet(variant);
            if (pet == null || pet.isDead() || !spec.getId().equals(pet.getData().hullmod)) return;

            if (!(context.getSelf() instanceof UIPanelAPI row)) {
                log.warn("Refit hull mod row has no self, no pet button for " + pet.name);
                return;
            }

            LabelAPI name = null;
            for (Object child : (List<?>) context.getMethod(GET_CHILDREN, row).call()) {
                if (child instanceof LabelAPI label && spec.getDisplayName().equals(label.getText())) name = label;
            }

            UIComponentAPI button = PetCard.refitButton(pet, () -> open(row, pet));
            row.addComponent(button).inRMid(RIGHT);

            if (name != null) {
                float shift = button.getPosition().getWidth() - PetCard.REFIT_ICON;
                name.setText(Ui.fit(pet.name, Ui.INS15, name.getPosition().getWidth() - shift));
                name.getPosition().leftOfMid(button, NAME_GAP);
            }
        } catch (RuntimeException e) {
            log.error("Could not add the pet button to a refit hull mod row", e);
        }
    }

    // vanilla saves refit edits before it switches ships (autoSaveRefitChanges is true in the campaign); recreateUI on close
    // reloads the member, else the refit copy of the variant would put a moved pet back on the next save
    private static void open(UIPanelAPI row, Pet pet) {
        Object refit = refitPanel(row);
        if (refit != null && Boolean.TRUE.equals(ReflectionUtils.invoke("isEditedSinceSave", refit, NO_ARGS, false))) {
            ReflectionUtils.invoke("saveCurrentVariant", refit, NO_ARGS, false);
        }

        PetsDialogPlugin.over(pet, refit == null ? null : () -> ReflectionUtils.invoke("recreateUI", refit, NO_ARGS, false));
    }

    // the refit panel is the first parent that declares all three
    private static Object refitPanel(Object node) {
        for (Object p = node; p != null; p = ReflectionUtils.invoke("getParent", p, NO_ARGS, false)) {
            if (ReflectionUtils.hasMethodOfName("recreateUI", p, false)
                    && ReflectionUtils.hasMethodOfName("saveCurrentVariant", p, false)
                    && ReflectionUtils.hasMethodOfName("isEditedSinceSave", p, false)) return p;
        }

        log.warn("No refit panel above the pet row, the refit screen is not refreshed after the Pets panel");
        return null;
    }
}
