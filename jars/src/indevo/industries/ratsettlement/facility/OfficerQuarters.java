package indevo.industries.ratsettlement.facility;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.academy.plugins.AcademyDialogPlugin;
import indevo.industries.academy.plugins.AcademyView;

import java.util.ArrayList;

// storage and training through an embedded academy; the facility forwards the academy's day and economy callbacks
public class OfficerQuarters extends SettlementFacility {

    private static final String OPEN = "open";

    private QuartersAcademy academy = null;

    @Override
    protected Setting parentSetting() {
        return Setting.ACADEMY;
    }

    @Override
    protected void onApply() {
        academy = new QuartersAcademy();
        academy.init(Ids.ACADEMY, getSettlementMarket());
    }

    // officers and admins go to the fleet even over the cap, nothing is lost
    @Override
    protected void onUnapply() {
        if (academy == null) return;

        academy.abortOfficerTraining(true);
        academy.abortAdminTraining(true);

        for (PersonAPI person : new ArrayList<>(academy.getOfficerStorage())) Global.getSector().getPlayerFleet().getFleetData().addOfficer(person);
        for (PersonAPI person : new ArrayList<>(academy.getAdminStorage())) Global.getSector().getCharacterData().addAdmin(person);
        academy.getOfficerStorage().clear();
        academy.getAdminStorage().clear();
        academy = null;
    }

    @Override
    public void onNewDay() {
        getAcademy().onNewDay();
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        getAcademy().reportEconomyTick(iterIndex);
    }

    @Override
    public void reportEconomyMonthEnd() {
        getAcademy().reportEconomyMonthEnd();
    }

    @Override
    protected boolean hasOptions() {
        return true;
    }

    @Override
    protected void addOptions(InteractionDialogAPI dialog) {
        addOption(dialog, "Visit the Officer Quarters", OPEN);
    }

    @Override
    protected void onOption(InteractionDialogAPI dialog, String action) {
        openPanel(dialog, onReturn -> new AcademyDialogPlugin(getAcademy(), new AcademyView(), onReturn));
    }

    private QuartersAcademy getAcademy() {
        if (academy == null) throw new IllegalStateException("Officer Quarters used before construction finished");
        return academy;
    }

    @Override
    public void addDescriptionToTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Stores officers and administrators and trains them, at the rules of your own Academy. Offers no officers for hire and gives no officer quality bonus.", 0f);

        if (academy == null) return;
        tooltip.addPara("Officers in storage: %s, administrators: %s", 10f, Misc.getHighlightColor(),
                academy.getOfficerStorage().size() + "", academy.getAdminStorage().size() + "");
    }
}
