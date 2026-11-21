package indevo.industries.derelicts.dialogue;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.economy.plugins.RelicConfirmDialog;
import indevo.industries.derelicts.industry.RiftGenerator;
import indevo.utils.helper.RelicHelper;

import java.util.Map;

public class RiftPreciseConfirmDialog extends RelicConfirmDialog {

    public static final String FIRED_TRIGGER = "IndEvo_RiftPreciseFired";

    private final InteractionDialogAPI dialog;
    private final Map<String, MemoryAPI> memoryMap;
    private final RiftGenerator gen;
    private final StarSystemAPI target;

    public RiftPreciseConfirmDialog(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, RiftGenerator gen, StarSystemAPI target) {
        super(gen.getMarket(), gen.getPreciseCost(), null);
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.gen = gen;
        this.target = target;
    }

    public static void fire(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, RiftGenerator gen, StarSystemAPI target) {
        gen.fire(target);
        FireBest.fire(null, dialog, memoryMap, FIRED_TRIGGER);
    }

    @Override
    protected void addDescription(TooltipMakerAPI info) {
        info.addPara("The planet jumps to the %s. The Generator then recharges as usual.", 10f, Misc.getHighlightColor(), target.getNameWithLowercaseType());
    }

    @Override
    protected void apply() {
        RelicHelper.take(market, cost);
        fire(dialog, memoryMap, gen, target);
    }
}
