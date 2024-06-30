package indevo.industries.academy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.academy.industry.Academy;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static indevo.industries.academy.rules.IndEvo_AcademyVariables.*;

public class IndEvo_DisplaySubListPage extends BaseCommandPlugin {

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_DisplaySubListPage.class).info(Text);
        }
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        OptionPanelAPI opts = dialog.getOptionPanel();

        if (memory.getString(CURRENT_PERSON_TYPE) == null || memory.getString(CURRENT_ACTION_TYPE) == null) {
            return false;
        }

        int pageNumber = Integer.parseInt(params.get(0).getString(memoryMap));
        personTypes personType = personTypes.valueOf(memory.getString(CURRENT_PERSON_TYPE));
        ArrayList<ArrayList<PersonAPI>> splitList = (ArrayList<ArrayList<PersonAPI>>) memory.get(PERSON_LIST_CONTAINER);

        debugMessage("Load Page: " + pageNumber);
        debugMessage("Array has amount: " + splitList.size());

        opts.clearOptions();

        for (PersonAPI person : splitList.get(pageNumber - 1)) {
            generatePersonTooltip(person, personType, dialog);
        }

        opts.addOption("Next Page", "IndEvo_personSelector_TriggerNext");
        opts.setEnabled("IndEvo_personSelector_TriggerNext", pageNumber < splitList.size());
        opts.addOption("Previous Page", "IndEvo_personSelector_TriggerPrevious");
        opts.setEnabled("IndEvo_personSelector_TriggerPrevious", pageNumber > 1);
        opts.addOption("Return", "IndEvo_IntitiateAcademyMenu");
        opts.setShortcut("IndEvo_IntitiateAcademyMenu", Keyboard.KEY_ESCAPE, false, false, false, false);

        return true;

       /*   get lists with the required persos on it
            make tooltip line for each person
            throw tooltip line for each person with selector

            create the list
            split the list
            put the list into memory

            get the list point (1, 2, x) - if option is selected, throw out option text with "optionsStartsWith" shit and display options
        */
    }

    private void generatePersonTooltip(PersonAPI person, personTypes personType, InteractionDialogAPI dialog) {
        HashMap<String, Color> colourByPersonality = new HashMap<>();
        colourByPersonality.put(Personalities.TIMID, Color.BLUE);
        colourByPersonality.put(Personalities.CAUTIOUS, Color.CYAN);
        colourByPersonality.put(Personalities.STEADY, Color.GREEN);
        colourByPersonality.put(Personalities.AGGRESSIVE, Color.ORANGE);
        colourByPersonality.put(Personalities.RECKLESS, Color.RED);

        Color hl = Misc.getHighlightColor();
        OptionPanelAPI opts = dialog.getOptionPanel();
        String optionId = PERSON_OPTION_PREFIX + person.getId();
        String personDescriptionString;

        switch (personType) {
            case OFFICER:
                String personality = person.getPersonalityAPI().getId(); //MiscIE.lcFirst(person.getPersonalityAPI().getDisplayName());
                MutableCharacterStatsAPI stats = person.getStats();

                personDescriptionString = person.getNameString() + ", Level: " + (int) stats.getLevel() + " officer, " + personality;
                opts.addOption(personDescriptionString, optionId, colourByPersonality.get(personality), null);
                /*sadly, in-text colouring doesn't seem to work.
                opts.setTooltipHighlights(optionId, stats.getLevel() + "", personality);
                opts.setTooltipHighlightColors(optionId, hl, colourByPersonality.get(personality));*/
                break;
            case ADMIN:
                String tier = "tier " + (int) getAdminTier(person);

                personDescriptionString = person.getNameString() + ", " + tier + " administrator.";
                opts.addOption(personDescriptionString, optionId);
                /*opts.setTooltipHighlights(optionId, tier);
                opts.setTooltipHighlightColors(optionId, hl);*/
                break;
        }
    }

    public int getAdminTier(PersonAPI person) {
        int skillCount = 0;

        //Admin has less than 2 skills
        List<String> allSkillIds = Global.getSettings().getSortedSkillIds();
        for (String skillId : allSkillIds) {
            SkillSpecAPI skill = Global.getSettings().getSkillSpec(skillId);
            if (skill.isAdminSkill() && Academy.personHasSkill(person, skill)) {
                skillCount++;
            }
        }

        return skillCount;
    }
}