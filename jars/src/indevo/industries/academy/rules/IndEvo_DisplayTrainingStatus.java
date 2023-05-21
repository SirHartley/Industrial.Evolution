package indevo.industries.academy.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.academy.industry.Academy;
import indevo.utils.helper.StringHelper;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static indevo.industries.academy.rules.IndEvo_CreatePersonSelectionList.getMarket;

public class IndEvo_DisplayTrainingStatus extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = getMarket(memoryMap);
        TextPanelAPI text = dialog.getTextPanel();
        Academy academy = (Academy) market.getIndustry(Ids.ACADEMY);

        Color hl = Misc.getHighlightColor();

        text.setFontSmallInsignia();

        text.addParagraph("-----------------------------------------------------------------------------");
        if (academy.getOfficerInTraining() == null) text.addParagraph("No officer currently in training.");
        else {
            PersonAPI officer = academy.getOfficerInTraining();
            text.addParagraph("Currently training officer: " + officer.getNameString() + " from " + Misc.ucFirst(officer.getPersonalityAPI().getDisplayName()) + " to " + Misc.ucFirst(academy.getNextPersonalityForTooltip())
                    + " for another " + academy.getRemainingOfficerTrainingDays() + " " + StringHelper.getDayOrDays(academy.getRemainingOfficerTrainingDays()));
            text.highlightInLastPara(hl, academy.getRemainingOfficerTrainingDays() + " " + StringHelper.getDayOrDays(academy.getRemainingOfficerTrainingDays()));

        }

        if (academy.getAdminInTraining() == null) text.addParagraph("No admin currently in training.");
        else {
            PersonAPI adminInTraining = academy.getAdminInTraining();
            text.addParagraph("Currently training administrator: " + adminInTraining.getNameString() + " for another " + academy.getRemainingAdminTrainingDays() + " " + StringHelper.getDayOrDays(academy.getRemainingAdminTrainingDays()));
            text.highlightInLastPara(hl, academy.getRemainingAdminTrainingDays() + " " + StringHelper.getDayOrDays(academy.getRemainingAdminTrainingDays()));
        }
        text.addParagraph("-----------------------------------------------------------------------------");

        text.setFontInsignia();

        return true;
    }
}