package indevo.industries.academy.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import indevo.industries.academy.industry.IndEvo_Academy;
import indevo.ids.Ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static indevo.industries.academy.rules.IndEvo_AcademyVariables.*;
import static indevo.industries.academy.rules.IndEvo_CreatePersonSelectionList.getMarket;

public class IndEvo_PrepareForTrainingAbort extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        IndEvo_AcademyVariables.personTypes personType = IndEvo_AcademyVariables.personTypes.valueOf(params.get(0).getString(memoryMap));

        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        OptionPanelAPI opts = dialog.getOptionPanel();
        TextPanelAPI text = dialog.getTextPanel();

        Color red = Misc.getNegativeHighlightColor();

        MarketAPI market = getMarket(memoryMap);

        IndEvo_Academy academy = (IndEvo_Academy) market.getIndustry(Ids.ACADEMY);
        PersonAPI person;

        memory.set(CURRENT_ACTION_TYPE, ActionTypes.ABORT.toString(), EXPIRE_TIME);
        memory.set(CURRENT_PERSON_TYPE, personType.toString(), EXPIRE_TIME);

        opts.clearOptions();


        switch (personType) {
            case OFFICER:
                person = academy.getOfficerInTraining();

                if (academy.getCurrentDirection().equals(IndEvo_Academy.trainingDirection.WEAKER)) {
                    text.addParagraph("Currently training to be less aggressive:");
                } else {
                    text.addParagraph("Currently training to be more aggressive:");
                }

                IndEvo_DisplayPersonActionPanel.printPersonCharacterTooltip(person, personType, dialog);

                opts.addOption("Abort, move to fleet", "IndEvo_MovePersonOption_fleet");
                opts.setEnabled("IndEvo_MovePersonOption_fleet", IndEvo_Academy.playerHasOfficerCapacity());

                if (!IndEvo_Academy.playerHasOfficerCapacity()) {
                    opts.setTooltip("IndEvo_MovePersonOption_fleet", "You do not have any free officer slots.");
                }

                opts.addOption("Abort, move to storage", "IndEvo_MovePersonOption_storage");
                break;
            case ADMIN:
                person = academy.getAdminInTraining();
                text.addParagraph("Currently training:");

                IndEvo_DisplayPersonActionPanel.printPersonCharacterTooltip(person, personType, dialog);

                opts.addOption("Abort, move to fleet", "IndEvo_MovePersonOption_fleet");
                opts.addOption("Abort, move to storage", "IndEvo_MovePersonOption_storage");
                break;
        }

        text.addParagraph("Aborting the training will not refund any credits, and no progress will be saved.", red);

        opts.addOption("Return", "IndEvo_IntitiateAcademyMenu");
        opts.setShortcut("IndEvo_IntitiateAcademyMenu", Keyboard.KEY_ESCAPE, false, false, false, false);

        return true;
    }
}