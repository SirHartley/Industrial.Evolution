package indevo.industries.petshop.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.petshop.PetLongevity;
import indevo.industries.petshop.plugins.PetTreatDialog;

import java.util.List;
import java.util.Map;

// Nomios longevity treatment: "available" condition, "option" greys the menu option without a pet, "open" shows the popup
public class IndEvo_PetTreatment extends BaseCommandPlugin {

    public static final String OPTION = "IndEvo_NomiosLongevity";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String verb = params.isEmpty() ? "" : params.get(0).getString(memoryMap);
        switch (verb) {
            case "available":
                return Setting.PETS.enabled() && PetLongevity.getNomios() != null;
            case "option":
                if (dialog == null || PetLongevity.hasPetWithFleet()) return true;
                dialog.getOptionPanel().setEnabled(OPTION, false);
                dialog.getOptionPanel().setTooltip(OPTION, "No pet with the fleet.");
                return true;
            case "open":
                if (dialog == null || !PetLongevity.hasPetWithFleet()) return false;
                PetTreatDialog.nomios(dialog, memoryMap).show();
                return true;
            default:
                throw new IllegalArgumentException("IndEvo_PetTreatment: unknown verb " + verb);
        }
    }
}
