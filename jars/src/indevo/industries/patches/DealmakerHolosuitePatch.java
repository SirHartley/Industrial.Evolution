package indevo.industries.patches;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import patchlib.api.context.FieldReadContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.FieldMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectFieldRead;

// the vanilla holosuite effect is an anonymous class in ItemEffectsRepo, so match its base class
@Patch(target = @ClassMatch(subtype = BaseInstallableItemEffect.class))
public class DealmakerHolosuitePatch {

    public static final int ALT_INCOME_PERCENT_BONUS = 25;

    @RedirectFieldRead(field = @FieldMatch(fieldName = "DEALMAKER_INCOME_PERCENT_BONUS"), owner = @ClassMatch(type = ItemEffectsRepo.class))
    public static void redirectIncomeBonus(FieldReadContext context) {
        context.setResult(CommerceBalancePatch.isActive() ? ALT_INCOME_PERCENT_BONUS : context.read());
    }
}
