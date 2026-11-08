package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.academy.industry.Academy;

import java.awt.*;

public class AcademyAdminTrainingDelegate extends BaseCustomDialogDelegate {

    public static final float WIDTH = 500f;
    public static final float HEIGHT = 200f;

    private static final float PAD = 10f;

    private final Academy academy;
    private final AcademyDialogPlugin plugin;
    private final PersonAPI admin;

    private CustomDialogCallback callback;
    private boolean open = false;

    public AcademyAdminTrainingDelegate(Academy academy, AcademyDialogPlugin plugin, PersonAPI admin) {
        this.academy = academy;
        this.plugin = plugin;
        this.admin = admin;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        this.open = true;

        Color hl = Misc.getHighlightColor();
        float fee = academy.getAdminTrainingFee();
        TooltipMakerAPI text = panel.createUIElement(panel.getPosition().getWidth(), panel.getPosition().getHeight(), false);

        text.addSectionHeading("Administrator training - " + admin.getNameString(), Alignment.MID, 0f);
        text.addPara("Learns one new administrator skill (currently %s of 2).", PAD, hl, Academy.getAdminSkillCount(admin) + "");
        text.addPara("Time: %s", PAD, hl, academy.getAdminTrainingDays() + " days");
        if (fee > 0) text.addPara("Cost: %s", 3f, hl, Misc.getDGSCredits(fee));
        else text.addPara("Cost: %s", 3f, Misc.getPositiveHighlightColor(), "free");

        panel.addUIElement(text).inTL(0f, 0f);
    }

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return "Train";
    }

    @Override
    public String getCancelText() {
        return "Cancel";
    }

    @Override
    public void customDialogConfirm() {
        open = false;
        academy.setAdminInTraining(admin);
        plugin.showPanelNextFrame();
    }

    @Override
    public void customDialogCancel() {
        open = false;
        plugin.showPanelNextFrame();
    }
}
