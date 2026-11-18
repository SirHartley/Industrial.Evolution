package indevo.industries.petshop.patches;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import indevo.config.Setting;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.plugins.PetCard;
import org.apache.log4j.Logger;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.MethodQuery;
import patchlib.api.ref.MethodRef;
import patchlib.api.spec.MethodQuerySpec;

// fleet screen ship card, seen as obf com.fs.starfarer.coreui.G in 0.98.5a-RC4, the only class with addTooltipsForPips
// the card builds its children lazily in renderImpl after clearChildren and calls addTooltipsForPips last, so this runs once per build
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.coreui",
        methodMatches = @MethodMatch(methodName = "addTooltipsForPips", parameterCount = 0)))
public class PetFleetCardPatch {

    public static final Logger log = Global.getLogger(PetFleetCardPatch.class);

    private static final float SIZE = 32f;
    // left of the officer portrait, 5 px above it: the portrait row is 64 high and 3 off the bottom
    private static final float LEFT = 5f;
    private static final float BOTTOM = 72f;

    private static final MethodQuerySpec GET_MEMBER = MethodQuery.create().methodName("getMember").parameterCount(0).build();
    private static final MethodQuerySpec GET_FLEET_PANEL = MethodQuery.create().methodName("getFleetPanel").parameterCount(0).build();
    private static final MethodQuerySpec UPDATE_LIST = MethodQuery.create().methodName("updateListContents").parameterCount(0).build();

    @After(target = @MethodMatch(methodName = "addTooltipsForPips", parameterCount = 0))
    public static void afterPips(AfterContext context) {
        if (Global.getCurrentState() != GameState.CAMPAIGN || !Setting.PETS.enabled()) return;

        try {
            if (!(context.getMethod(GET_MEMBER).call() instanceof FleetMemberAPI member) || member.getFleetData() == null) return;

            CampaignFleetAPI fleet = member.getFleetData().getFleet();
            if (fleet == null || !fleet.isPlayerFleet()) return;

            Pet pet = PetStatusManager.getInstance().getPet(member.getVariant());
            if (pet == null || pet.isDead()) return;

            Object panel = context.getMethod(GET_FLEET_PANEL).call();
            MethodRef update = panel != null && context.hasMethod(UPDATE_LIST, panel) ? context.getMethod(UPDATE_LIST, panel) : null;
            Runnable refresh = update != null ? () -> update.call() : null;

            UIPanelAPI card = (UIPanelAPI) context.getSelf();
            card.addComponent(PetCard.shipButton(pet, SIZE, refresh)).inBL(LEFT, BOTTOM);
        } catch (RuntimeException e) {
            log.error("Could not add the pet icon to a fleet screen ship card", e);
        }
    }
}
