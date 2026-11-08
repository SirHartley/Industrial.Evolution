package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.academy.industry.Academy;

import java.awt.*;

public class AcademyAdminTrainingConfirm extends BaseCustomDialogDelegate {

    public static final float WIDTH = 450f;

    private static final float PAD = 10f;

    private final Academy academy;
    private final AcademyDialogPlugin plugin;
    private final PersonAPI admin;

    private CustomDialogCallback callback;
    private boolean open = false;

    public AcademyAdminTrainingConfirm(Academy academy, AcademyDialogPlugin plugin, PersonAPI admin) {
        this.academy = academy;
        this.plugin = plugin;
        this.admin = admin;
    }

    public float getHeight() {
        CustomPanelAPI scratch = Global.getSettings().createCustom(WIDTH, 1000f, null);
        TooltipMakerAPI info = scratch.createUIElement(WIDTH, 1000f, false);
        addBody(info);
        return info.getHeightSoFar() + PAD;
    }

    private void addBody(TooltipMakerAPI info) {
        Color hl = Misc.getHighlightColor();
        float fee = academy.getAdminTrainingFee();
        String days = academy.getAdminTrainingDays() + " days";

        info.setParaInsigniaLarge();
        info.addPara("Train %s?", 0f, hl, admin.getNameString());
        info.setParaFontDefault();

        info.addPara("Learns one new administrator skill (has %s of 2).", PAD, hl, Academy.getAdminSkillCount(admin) + "");
        if (fee > 0) info.addPara("Takes %s and costs %s.", PAD, hl, days, Misc.getDGSCredits(fee));
        else info.addPara("Takes %s.", PAD, hl, days);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        this.open = true;

        TooltipMakerAPI info = panel.createUIElement(WIDTH, panel.getPosition().getHeight(), false);
        addBody(info);
        panel.addUIElement(info).inTL(0f, 0f);
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
