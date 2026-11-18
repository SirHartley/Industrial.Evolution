package indevo.industries.petshop.patches;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import indevo.config.Setting;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.plugins.PetCard;
import org.apache.log4j.Logger;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.spec.FieldQuerySpec;

// full ship tooltip, the anonymous StandardTooltipV2 of createFleetMemberTooltipPreDeploy (0.98.5a-RC4)
// its beforeShown rebuilds the content and sends the ship picture frame to the bottom once, the call is the anchor
@Patch(target = @ClassMatch(subtypeName = "com.fs.starfarer.ui.impl.StandardTooltipV2", packageName = "com.fs.starfarer.ui.impl"))
public class PetShipTooltipPatch {

    public static final Logger log = Global.getLogger(PetShipTooltipPatch.class);

    private static final float SIZE = 48f;
    private static final float INSET = 4f;

    // the captured member, declared on the anonymous class, found before the inherited codexFleetMember
    private static final FieldQuerySpec MEMBER = FieldQuery.create().subtype(FleetMemberAPI.class).build();

    @RedirectMethodCall(
            target = @MethodMatch(methodName = "beforeShown", parameterCount = 0),
            call = @MethodMatch(methodName = "sendToBottomWithinItself", parameterCount = 1))
    public static void afterFrame(MethodCallContext context) {
        context.call();
        if (Global.getCurrentState() != GameState.CAMPAIGN || !Setting.PETS.enabled()) return;

        try {
            if (!context.hasField(MEMBER) || !(context.getField(MEMBER).get() instanceof FleetMemberAPI member)) return;
            if (!(context.getCallReceiver() instanceof UIPanelAPI content) || !(context.getCallArg(0) instanceof UIComponentAPI frame)) return;

            Pet pet = PetStatusManager.getInstance().getPet(member.getVariant());
            if (pet == null || pet.isDead()) return;

            content.addComponent(PetCard.shipIcon(pet, SIZE)).rightOfBottom(frame, -(SIZE + INSET)).setYAlignOffset(INSET);
        } catch (RuntimeException e) {
            log.error("Could not add the pet icon to a ship tooltip", e);
        }
    }
}
