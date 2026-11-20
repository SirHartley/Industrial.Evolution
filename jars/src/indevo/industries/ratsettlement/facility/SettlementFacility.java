package indevo.industries.ratsettlement.facility;

import assortment_of_things.frontiers.plugins.facilities.BaseSettlementFacility;
import assortment_of_things.misc.RATInteractionPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.config.Setting;
import indevo.industries.ratsettlement.RatSettlements;
import indevo.utils.helper.BillingHelper;
import indevo.utils.helper.MiscIE;

import java.util.function.Consumer;
import java.util.function.Function;

// base of every ie facility in a rat settlement; instances and their fields are saved in rat's slots, never rename
// picker instances have no facility slot, so picker calls (canBeBuild, shouldShowInPicker, getCost, tooltip) never touch it
public abstract class SettlementFacility extends BaseSettlementFacility {

    private int lastDay = -1;

    public record OptionKey(String facilityId, String action) {
    }

    protected abstract Setting parentSetting();

    @Override
    public boolean shouldShowInPicker() {
        return parentSetting().enabled();
    }

    // rat has no day callback; advance runs every frame for every slot, also while paused
    @Override
    public void advance(float amount) {
        if (!isFunctional()) return;

        int day = (int) Global.getSector().getClock().getElapsedDaysSince(0L);
        if (lastDay < 0) lastDay = day;
        if (day == lastDay) return;

        lastDay = day;
        onNewDay();
    }

    protected void onNewDay() {
    }

    @Override
    public void populateSettlementDialog(InteractionDialogAPI dialog, RATInteractionPlugin plugin) {
        addOptions(dialog);
    }

    protected void addOptions(InteractionDialogAPI dialog) {
    }

    protected final void addOption(InteractionDialogAPI dialog, String label, String action) {
        dialog.getOptionPanel().addOption(label, new OptionKey(getID(), action));
    }

    // rat asks every facility in turn, so only own keys are caught
    @Override
    public boolean optionPressDetected(String optionName, Object optionData) {
        if (!(optionData instanceof OptionKey key) || !key.facilityId().equals(getID())) return false;

        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (dialog == null) throw new IllegalStateException("settlement option " + key.action() + " pressed without a dialog");

        onOption(dialog, key.action());
        return true;
    }

    protected void onOption(InteractionDialogAPI dialog, String action) {
    }

    // swaps an ie panel plugin into rat's dialog, the panel's return hands it back to rat
    protected static void openPanel(InteractionDialogAPI dialog, Function<Consumer<InteractionDialogAPI>, InteractionDialogPlugin> panel) {
        InteractionDialogPlugin rat = dialog.getPlugin();
        InteractionDialogPlugin plugin = panel.apply(d -> RatSettlements.returnTo(d, rat));
        dialog.setPlugin(plugin);
        plugin.init(dialog);
    }

    protected MarketAPI getSettlementMarket() {
        return getSettlement().getSettlementEntity().getMarket();
    }

    protected CargoAPI getStorageCargo() {
        CargoAPI cargo = MiscIE.getStorageCargo(getSettlementMarket());
        if (cargo == null) throw new IllegalStateException("rat settlement market has no storage");
        if (cargo.getMothballedShips() == null) cargo.initMothballedShips("player");
        return cargo;
    }

    protected void charge(String line, String label, float amount) {
        BillingHelper.charge(getSettlementMarket(), getID(), line, label, amount);
    }
}
