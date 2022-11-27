package indevo.exploration.salvage.specials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import indevo.utils.helper.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import indevo.industries.embassy.listeners.IndEvo_ambassadorPersonManager;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.awt.*;
import java.util.List;

public class IndEvo_JuicyRumorsSpecial extends BaseSalvageSpecial {

    public static final String FACTION_1 = "1";
    public static final String FACTION_2 = "2";
    public static final String NOT_NOW = "not_now";

    public static class JuicyRumorsSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public final Pair<String, String> factionPair;
        public final float repGainAmt;

        public JuicyRumorsSpecialData(String factionId, float repGainAmt) {
            FactionAPI faction1 = Global.getSector().getFaction(factionId);

            this.factionPair = new Pair<>(factionId, getWorstEnemy(faction1, IndEvo_ambassadorPersonManager.getListOfIncativeFactions()).getId());
            this.repGainAmt = repGainAmt;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new IndEvo_JuicyRumorsSpecial();
        }
    }

    private IndEvo_JuicyRumorsSpecial.JuicyRumorsSpecialData data;

    public IndEvo_JuicyRumorsSpecial() {
    }

    private FactionAPI faction_1 = null;
    private FactionAPI faction_2 = null;

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);

        data = (IndEvo_JuicyRumorsSpecial.JuicyRumorsSpecialData) specialData;
        if (data.repGainAmt < 0.01f) initNothing();

        initAndFixFactions();

        addText("Sifting through what appears to be mostly ancient and useless files on a barely working dataslate, " +
                "one of your salvage workers comes across some very interesting information pertaining to " + faction_1.getDisplayName() + " machinations." +
                "\n\nSending the dataslate back to them will surely raise relations - but their competitors might value it even more than them.");

        options.clearOptions();
        options.addOption("Send the Intel to " + faction_1.getDisplayNameWithArticle(), FACTION_1, faction_1.getColor(),
                "Will raise relations with " + faction_1.getDisplayNameLongWithArticle());
        options.addOption("Send the Intel to " + faction_2.getDisplayNameWithArticle(), FACTION_2, faction_2.getColor(),
                "Will raise relations with " + faction_2.getDisplayNameLongWithArticle() + " and reduce relations with " + faction_1.getDisplayNameLongWithArticle());
        options.addOption("Not now", NOT_NOW);
    }

    public static final float HOSTILE_INCREASE_FACT = 1.5f;
    public static final float HOSTILE_DECREASE_FACT = 0.5f;

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData.equals(FACTION_1)) {

            addText("You send the slate towards " + faction_1.getDisplayName() + " space in an emergency courier drone. " +
                    "You do not have to wait long for the response - a short receipt confirmation, and a formal expression of gratitude.");

            IndEvo_ambassadorPersonManager.adjustRelationship(Global.getSector().getPlayerFaction(), faction_1, data.repGainAmt);
            Pair<String, Color> repInt = IndEvo_StringHelper.getRepIntTooltipPair(faction_1);
            text.setFontSmallInsignia();
            text.addPara("Gained " + IndEvo_StringHelper.getFloatToIntStrx100(data.repGainAmt) + " reputation with " + faction_1.getDisplayNameWithArticle() + " - Now at " + repInt.one);

            Highlights h = new Highlights();
            h.setText(IndEvo_StringHelper.getFloatToIntStrx100(data.repGainAmt), faction_1.getDisplayNameLongWithArticle(), repInt.one);
            h.setColors(Misc.getPositiveHighlightColor(), faction_1.getColor(), repInt.two);

            text.setHighlightsInLastPara(h);
            text.setFontInsignia();

            setDone(true);
            setShowAgain(false);
        } else if (optionData.equals(FACTION_2)) {
            addText("You send the slate towards " + faction_2.getDisplayName() + " space in an emergency courier drone. " +
                    "The response comes surprisingly fast - a receipt note, and a rather long commendation for your services in formal script.\n\n" +
                    "However, " + faction_1.getDisplayName() + " comm channels light up at the same time - you get the sense that your little stunt might not have gone unnoticed.");

            float repIncrease = data.repGainAmt * HOSTILE_INCREASE_FACT;
            float repReduce = Math.max(data.repGainAmt * HOSTILE_DECREASE_FACT, 0.01f);
            IndEvo_ambassadorPersonManager.adjustRelationship(Global.getSector().getPlayerFaction(), faction_1, repReduce);
            IndEvo_ambassadorPersonManager.adjustRelationship(Global.getSector().getPlayerFaction(), faction_2, repIncrease);

            text.setFontSmallInsignia();

            //faction 1 rep loss
            Pair<String, Color> repInt = IndEvo_StringHelper.getRepIntTooltipPair(faction_1);
            text.addPara("Lost " + IndEvo_StringHelper.getFloatToIntStrx100(repReduce) + " reputation with " + faction_1.getDisplayNameWithArticle() + " - Now at " + repInt.one);

            Highlights h = new Highlights();
            h.setText(IndEvo_StringHelper.getFloatToIntStrx100(repReduce), faction_1.getDisplayNameLongWithArticle(), repInt.one);
            h.setColors(Misc.getNegativeHighlightColor(), faction_1.getColor(), repInt.two);
            text.setHighlightsInLastPara(h);

            //faction 2 rep gain
            Pair<String, Color> repInt2 = IndEvo_StringHelper.getRepIntTooltipPair(faction_2);
            text.addPara("Gained " + IndEvo_StringHelper.getFloatToIntStrx100(repIncrease) + " reputation with " + faction_2.getDisplayNameWithArticle() + " - Now at " + repInt2.one);

            Highlights h2 = new Highlights();
            h2.setText(IndEvo_StringHelper.getFloatToIntStrx100(repIncrease), faction_2.getDisplayNameLongWithArticle(), repInt2.one);
            h2.setColors(Misc.getPositiveHighlightColor(), faction_2.getColor(), repInt2.two);
            text.setHighlightsInLastPara(h2);

            text.setFontInsignia();

            setDone(true);
            setShowAgain(false);
        } else if (optionData.equals(NOT_NOW)) {
            setDone(true);
            setEndWithContinue(false);
            setShowAgain(true);
        }
    }

    public static FactionAPI getWorstEnemy(FactionAPI forFaction, List<FactionAPI> excluding) {
        Pair<FactionAPI, Float> factionRepPair = new Pair<>(Global.getSector().getPlayerFaction(), 2f);
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (excluding.contains(faction)
                    || faction.getId().equals(forFaction.getId())
                    || faction.isPlayerFaction()) continue;

            float rep = forFaction.getRelationship(faction.getId());
            if (rep < factionRepPair.two) {
                factionRepPair.one = faction;
                factionRepPair.two = rep;
            }
        }

        return factionRepPair.one;
    }

    private void initAndFixFactions() {
        List<FactionAPI> inactiveFactions = IndEvo_ambassadorPersonManager.getListOfIncativeFactions();
        faction_1 = Global.getSector().getFaction(data.factionPair.one);
        faction_2 = Global.getSector().getFaction(data.factionPair.two);

        //fix inactive factions
        if (inactiveFactions.contains(faction_1)
                || inactiveFactions.contains(faction_2)
                || faction_1.getId().equals(faction_2.getId())
                || faction_1.isPlayerFaction()
                || faction_2.isPlayerFaction()) {
            WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>(random);
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (inactiveFactions.contains(faction)
                        || faction.isPlayerFaction()) continue;

                picker.add(faction);
            }

            faction_1 = picker.pick();
            data.factionPair.one = faction_1.getId();

            faction_2 = getWorstEnemy(faction_1, inactiveFactions);
            data.factionPair.two = faction_2.getId();
        }
    }
}