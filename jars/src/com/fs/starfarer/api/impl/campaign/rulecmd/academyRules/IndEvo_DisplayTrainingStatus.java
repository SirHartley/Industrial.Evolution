package com.fs.starfarer.api.impl.campaign.rulecmd.academyRules;

import com.fs.starfarer.api.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_Academy;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.academyRules.IndEvo_CreatePersonSelectionList.getMarket;

public class IndEvo_DisplayTrainingStatus extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = getMarket(memoryMap);
        TextPanelAPI text = dialog.getTextPanel();
        IndEvo_Academy academy = (IndEvo_Academy) market.getIndustry(IndEvo_ids.ACADEMY);

        Color hl = Misc.getHighlightColor();

        text.setFontSmallInsignia();

        text.addParagraph("-----------------------------------------------------------------------------");
        if (academy.getOfficerInTraining() == null) text.addParagraph("No officer currently in training.");
        else {
            PersonAPI officer = academy.getOfficerInTraining();
            text.addParagraph("Currently training officer: " + officer.getNameString() + " from " + Misc.ucFirst(officer.getPersonalityAPI().getDisplayName()) + " to " + Misc.ucFirst(academy.getNextPersonalityForTooltip())
                    + " for another " + academy.getRemainingOfficerTrainingDays() + " " + IndEvo_StringHelper.getDayOrDays(academy.getRemainingOfficerTrainingDays()));
            text.highlightInLastPara(hl, academy.getRemainingOfficerTrainingDays() + " " + IndEvo_StringHelper.getDayOrDays(academy.getRemainingOfficerTrainingDays()));

        }

        if (academy.getAdminInTraining() == null) text.addParagraph("No admin currently in training.");
        else {
            PersonAPI adminInTraining = academy.getAdminInTraining();
            text.addParagraph("Currently training administrator: " + adminInTraining.getNameString() + " for another " + academy.getRemainingAdminTrainingDays() + " " + IndEvo_StringHelper.getDayOrDays(academy.getRemainingAdminTrainingDays()));
            text.highlightInLastPara(hl, academy.getRemainingAdminTrainingDays() + " " + IndEvo_StringHelper.getDayOrDays(academy.getRemainingAdminTrainingDays()));
        }
        text.addParagraph("-----------------------------------------------------------------------------");

        text.setFontInsignia();

        return true;
    }
}