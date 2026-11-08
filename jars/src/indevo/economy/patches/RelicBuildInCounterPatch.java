package indevo.economy.patches;

import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.coreui.refit.ModPickerDialogV3;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

// counter panel of the refit build in dialog, readable named inner class (0.98a-RC8), inferred
@Patch(target = @ClassMatch(type = ModPickerDialogV3.PermaModInfoWidget.class))
public class RelicBuildInCounterPatch {

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "advanceImpl", parameterTypes = float.class),
            call = @MethodMatch(methodName = "getStoryPoints", parameterCount = 0),
            owner = @ClassMatch(subtype = MutableCharacterStatsAPI.class))
    public static void redirectCounterStoryPoints(MethodCallContext context) {
        RelicBuildInPatch.Toggle toggle = RelicBuildInPatch.active(context.getSelf());
        context.setResult(toggle != null ? toggle.getAffordable() : context.call());
    }

    // the bonus experience readout is the only label text in advanceImpl that ends in %
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "advanceImpl", parameterTypes = float.class),
            call = @MethodMatch(methodName = "setText", parameterTypes = String.class),
            owner = @ClassMatch(subtype = LabelAPI.class))
    public static void redirectCounterText(MethodCallContext context) {
        RelicBuildInPatch.Toggle toggle = RelicBuildInPatch.active(context.getSelf());

        if (toggle == null) context.call();
        else if (toggle.counterValue != null && context.getCallReceiver() == toggle.counterValue) context.call(toggle.getRemaining() + "");
        else if (context.getCallArg(0) instanceof String text && text.endsWith("%")) context.call("0%");
        else context.call();
    }
}
