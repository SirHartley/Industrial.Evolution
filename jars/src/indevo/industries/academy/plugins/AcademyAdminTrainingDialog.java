package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.industries.academy.industry.Academy;

import java.awt.*;
import java.util.Map;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

public class AcademyAdminTrainingDialog implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    private static final String TRAIN = "train";
    private static final String CANCEL = "cancel";

    private final Academy academy;
    private final PersonAPI admin;
    private InteractionDialogAPI dialog;

    public AcademyAdminTrainingDialog(Academy academy, PersonAPI admin) {
        this.academy = academy;
        this.admin = admin;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        Color hl = Misc.getHighlightColor();
        float fee = academy.getAdminTrainingFee();
        TextPanelAPI text = dialog.getTextPanel();

        dialog.getVisualPanel().showPersonInfo(admin, true, false);
        text.addPara("%s learns one new administrator skill (currently %s of 2).", hl, admin.getNameString(), Academy.getAdminSkillCount(admin) + "");
        text.addPara("Time: %s", hl, academy.getAdminTrainingDays() + " days");
        if (fee > 0) text.addPara("Cost: %s", hl, Misc.getDGSCredits(fee));
        else text.addPara("Cost: %s", Misc.getPositiveHighlightColor(), "free");

        dialog.getOptionPanel().addOption("Train", TRAIN);
        dialog.getOptionPanel().addOption("Cancel", CANCEL);
        dialog.setOptionOnEscape("Cancel", CANCEL);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (TRAIN.equals(optionData)) academy.setAdminInTraining(admin);
        dialog.dismiss();
    }

    @Override
    public void close() {
        dialog.dismiss();
    }

    @Override
    public void advance(float amount) {
        Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, this, 1f);
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
