package indevo.industries.artillery.rules;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.scripts.DroneNetScript;

import java.util.List;
import java.util.Map;

public class IndEvo_DroneNest extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        SectorEntityToken nest = dialog.getInteractionTarget();
        DroneNetScript net = DroneNetScript.get(nest.getContainingLocation());
        String action = params.get(0).getString(memoryMap);

        switch (action) {
            case "destroy" -> {
                if (net != null) net.destroyNest(nest, false);
                else Misc.fadeAndExpire(nest, 1f);
            }
            case "debris" -> {
                if (net != null) net.addDebris(nest);
            }
            case "salvage" -> showLoot(dialog);
            default -> throw new IllegalArgumentException("IndEvo_DroneNest: unknown action " + action);
        }

        return true;
    }

    private void showLoot(InteractionDialogAPI dialog) {
        SalvageEntityGenDataSpec.DropData drop = new SalvageEntityGenDataSpec.DropData();
        drop.group = DroneNetScript.NEST_DROP_GROUP;
        drop.chances = DroneNetScript.MIN_NEST_RELICS;
        drop.maxChances = DroneNetScript.MAX_NEST_RELICS;

        CargoAPI loot = SalvageEntity.generateSalvage(Misc.random, 1f, 1f, 1f, null, List.of(drop));

        dialog.getOptionPanel().clearOptions();
        dialog.setPromptText("");
        dialog.getVisualPanel().showLoot("Salvaged", loot, false, true, true, () -> {
            dialog.dismiss();
            dialog.hideTextPanel();
            dialog.hideVisualPanel();
        });
    }
}
