package industrial_evolution.campaign.rulecmd.salvage.special;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.HashMap;
import java.util.Map;

import static industrial_evolution.IndEvo_IndustryHelper.addOrIncrement;

public class IndEvo_DroneSurveyDataSpecial extends BaseSalvageSpecial {
    //level is the times it runs on the loot table, should aim for an average of 5 (0-10)

    public static final String OPEN = "open";
    public static final String NOT_NOW = "not_now";

    public static class DroneSurveyDataSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public final int level;

        public DroneSurveyDataSpecialData(int level) {
            this.level = level;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new IndEvo_DroneSurveyDataSpecial();
        }
    }

    private IndEvo_DroneSurveyDataSpecial.DroneSurveyDataSpecialData data;

    private float value = 1f;

    public IndEvo_DroneSurveyDataSpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);
        data = (IndEvo_DroneSurveyDataSpecial.DroneSurveyDataSpecialData) specialData;

        addText("While making a preliminary assessment, your salvage crews " +
                "find some functional databanks containing survey data.");

        options.clearOptions();
        options.addOption("Attempt access the databanks", OPEN);
        options.addOption("Not now", NOT_NOW);
    }

    public static final float METAL_MULT = 500f;
    public static final float RARE_METAL_MULT = 300f;

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPEN.equals(optionData)) {

            switch (data.level) {
                case 0:
                    addText("The database shorts out and you are left with nothing but scrap - but one mans trash is another mans treasure!");

                    int metalAmt = Math.round(random.nextFloat() * METAL_MULT);
                    int rareAmt = Math.round(random.nextFloat() * RARE_METAL_MULT);

                    playerFleet.getCargo().addCommodity(Commodities.RARE_METALS, rareAmt);
                    playerFleet.getCargo().addCommodity(Commodities.METALS, metalAmt);

                    AddRemoveCommodity.addCommodityGainText(Commodities.METALS, metalAmt, text);
                    AddRemoveCommodity.addCommodityGainText(Commodities.RARE_METALS, rareAmt, text);

                    break;
                default:
                    addText("Your scientists manage to goad the database into releasing its' treasure.\n" +
                            "The sounds and digital antics it throws are weirdly " +
                            "reminiscent of a stubborn animal getting whipped - but it's probably just a dying, old computer.");

                    WeightedRandomPicker<String> picker = new WeightedRandomPicker(random);
                    for (int i = 1; i <= 5; i++) {
                        String id = "survey_data_" + i;
                        picker.add(id, 1f / i);
                    }

                    Map<String, Float> lootMap = new HashMap<>();
                    for (int i = 0; i < data.level; i++) {
                        String id = picker.pick();
                        addOrIncrement(lootMap, id, 1f);
                    }

                    for (Map.Entry<String, Float> e : lootMap.entrySet()) {
                        playerFleet.getCargo().addCommodity(e.getKey(), Math.round(e.getValue()));
                        AddRemoveCommodity.addCommodityGainText(e.getKey(), Math.round(e.getValue()), text);
                    }

                    break;
                    /* chances
                    1	44%
                    2	22%
                    3	15%
                    4	11%
                    5	9%
                    */
            }


            setDone(true);
            setShowAgain(false);
        } else if (NOT_NOW.equals(optionData)) {
            setDone(true);
            setEndWithContinue(false);
            setShowAgain(true);
        }
    }
}
