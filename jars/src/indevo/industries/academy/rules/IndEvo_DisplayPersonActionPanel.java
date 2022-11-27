package indevo.industries.academy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import indevo.industries.academy.industry.IndEvo_Academy;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static indevo.industries.academy.rules.IndEvo_AcademyVariables.*;
import static indevo.industries.academy.rules.IndEvo_CreatePersonSelectionList.getMarket;

public class IndEvo_DisplayPersonActionPanel extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        OptionPanelAPI opts = dialog.getOptionPanel();

        if (memory.getString(CURRENT_PERSON_TYPE) == null || memory.getString(CURRENT_ACTION_TYPE) == null) {
            return false;
        }

        IndEvo_AcademyVariables.personTypes personType = IndEvo_AcademyVariables.personTypes.valueOf(memory.getString(CURRENT_PERSON_TYPE));
        ActionTypes actionType = ActionTypes.valueOf(memory.getString(CURRENT_ACTION_TYPE));
        PersonAPI person = (PersonAPI) memory.get(SELECTED_PERSON);

        MarketAPI market = getMarket(memoryMap);

        opts.clearOptions();

        printPersonCharacterTooltip(person, personType, dialog);
        printActionTypeText(personType, actionType, dialog, memoryMap);

        switch (actionType) {
            case TRAIN:
                switch (personType) {
                    case OFFICER:
                        opts.addOption("Increase Agression (Confirm)", "IndEvo_MovePersonOption_better");
                        opts.setEnabled("IndEvo_MovePersonOption_better", IndEvo_Academy.isOfficerTrainingDirectionAllowed(person, IndEvo_Academy.trainingDirection.BETTER));

                        if (!market.isPlayerOwned()) {
                            if (!IndEvo_Academy.playerCanAffordCost(Global.getSettings().getInt("personalityTrainingCost"))) {
                                opts.setEnabled("IndEvo_MovePersonOption_better", false);
                                opts.setTooltip("IndEvo_MovePersonOption_better", "You can't afford the training fee!");
                            }
                        }

                        opts.addOption("Decrease Agression (Confirm)", "IndEvo_MovePersonOption_worse");
                        opts.setEnabled("IndEvo_MovePersonOption_worse", IndEvo_Academy.isOfficerTrainingDirectionAllowed(person, IndEvo_Academy.trainingDirection.WEAKER));

                        if (!market.isPlayerOwned()) {
                            if (!IndEvo_Academy.playerCanAffordCost(Global.getSettings().getInt("personalityTrainingCost"))) {
                                opts.setEnabled("IndEvo_MovePersonOption_worse", false);
                                opts.setTooltip("IndEvo_MovePersonOption_worse", "You can't afford the training fee!");
                            }
                        }

                        break;
                    case ADMIN:
                        opts.addOption("Confirm", "IndEvo_MovePersonOption");

                        if (!market.isPlayerOwned()) {
                            if (!IndEvo_Academy.playerCanAffordCost(Global.getSettings().getInt("adminTrainingCost"))) {
                                opts.setEnabled("IndEvo_MovePersonOption", false);
                                opts.setTooltip("IndEvo_MovePersonOption", "You can't afford the training fee!");
                            }
                        }
                        break;
                }
                break;
            case STORE:
            case RETRIEVE:
                opts.addOption("Confirm", "IndEvo_MovePersonOption");
                break;
        }

        opts.addOption("Return", "IndEvo_personSelector_LoadCurrent");
        opts.setShortcut("IndEvo_personSelector_LoadCurrent", Keyboard.KEY_ESCAPE, false, false, false, false);
        return true;
    }

    public static void printPersonCharacterTooltip(PersonAPI person, personTypes personType, InteractionDialogAPI dialog) {
        MutableCharacterStatsAPI stats = person.getStats();
        TextPanelAPI text = dialog.getTextPanel();

        Color hl = Misc.getHighlightColor();

        text.addParagraph(person.getNameString());
        text.highlightInLastPara(hl, person.getNameString());

        text.setFontSmallInsignia();

        text.addParagraph("-----------------------------------------------------------------------------");

        if (personType.equals(personTypes.OFFICER)) {
            text.addParagraph("Level: " + (int) stats.getLevel());
            text.highlightInLastPara(hl, "" + (int) stats.getLevel());

            String personality = Misc.lcFirst(person.getPersonalityAPI().getDisplayName());
            text.addParagraph("Personality: " + personality);
            text.highlightInLastPara(hl, personality);
        }

        int printedSkills = 0;
        int totalSkills = 0;

        for (String skillId : Global.getSettings().getSortedSkillIds()) {
            int level = (int) stats.getSkillLevel(skillId);
            if (level > 0) {
                totalSkills++;

                if (printedSkills < 5) {
                    printedSkills++;
                    SkillSpecAPI spec = Global.getSettings().getSkillSpec(skillId);
                    String skillName = spec.getName();
                    if (spec.isAptitudeEffect()) {
                        skillName += " Aptitude";
                    }
                    text.addParagraph(skillName + ", level " + level);
                    text.highlightInLastPara(hl, "" + level);
                }
            }
        }

        if (printedSkills > 4) {
            text.addParagraph("... and " + (totalSkills - printedSkills) + " more.");
        } else if (printedSkills == 0) {
            text.addParagraph("... no skills of note.");
        }

        text.addParagraph("-----------------------------------------------------------------------------");

        text.setFontInsignia();
    }

    private void printActionTypeText(personTypes personType, ActionTypes actionType, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        TextPanelAPI text = dialog.getTextPanel();
        MarketAPI market = getMarket(memoryMap);

        IndEvo_Academy academy = (IndEvo_Academy) market.getIndustry(IndEvo_ids.ACADEMY);

        boolean isNotPlayerOwned = !market.isPlayerOwned();

        Color hl = Misc.getHighlightColor();
        Color red = Misc.getNegativeHighlightColor();
        Color green = Misc.getPositiveHighlightColor();

        text.setFontSmallInsignia();

        switch (actionType) {
            case TRAIN:
                switch (personType) {
                    case ADMIN:
                        text.addParagraph("Training will unlock an additional administrator skill.");
                        text.highlightInLastPara(hl, "administrator skill");

                        if (isNotPlayerOwned) {
                            String cost = Misc.getDGSCredits(Global.getSettings().getInt("adminTrainingCost"));
                            String time = Math.round(academy.getAdminTrainingDays() / 31f) + "";

                            text.addParagraph("This will cost " + cost + " and take " + time + " months.");
                            text.highlightInLastPara(hl, cost, time);
                        } else {
                            String time = Math.round(academy.getAdminTrainingDays() / 31f) + "";

                            text.addParagraph("This will take " + time + " months.");
                            text.highlightInLastPara(hl, time);
                        }

                        break;
                    case OFFICER:
                        boolean hasAlpha = market.getIndustry(IndEvo_ids.ACADEMY).getAICoreId() != null && market.getIndustry(IndEvo_ids.ACADEMY).getAICoreId().equals(Commodities.ALPHA_CORE);

                        text.addParagraph("Training will change the officer personality by one increment.");
                        text.highlightInLastPara(hl, "officer personality");

                        if (isNotPlayerOwned) {
                            String cost = Misc.getDGSCredits(Global.getSettings().getInt("personalityTrainingCost"));
                            String time = Math.round(academy.getOfficerTrainingDays() / 31f) + "";

                            text.addParagraph("This will cost " + cost + " and take " + time + " months.");
                            text.highlightInLastPara(hl, cost, time);
                        } else {
                            String time = Math.round(academy.getOfficerTrainingDays() / 31f) + "";

                            text.addParagraph("This will take " + time + " months.");
                            text.highlightInLastPara(hl, time);
                        }

                        if (hasAlpha) {
                            text.addParagraph("This Academy can enable up to two total changes to the officers' personality.");
                            text.highlightInLastPara(green, "two total changes");
                        } else {
                            text.addParagraph("This Academy can only change the officers' personality by one increment.");
                            text.highlightInLastPara(red, "by one increment");
                        }
                        break;
                }
                break;
            case STORE:
                if (isNotPlayerOwned) {
                    String cost = Misc.getDGSCredits(Global.getSettings().getInt("monthlyAIStorageCost")) + " per month";
                    text.addParagraph("Storing this person here will incur a flat cost of " + cost + " in addition to the usual reduced salary.");
                    text.highlightInLastPara(hl, cost);
                } else {
                    text.addParagraph("Storing this person here does not incur costs other than the usual reduced salary.");
                    text.highlightInLastPara(green, "not incur costs");
                }
                break;
            case RETRIEVE:
                text.addParagraph("Retrieving a person does not incur extra costs.");
                text.highlightInLastPara(green, "not incur extra costs");
                break;
        }

        text.setFontInsignia();
    }
}
