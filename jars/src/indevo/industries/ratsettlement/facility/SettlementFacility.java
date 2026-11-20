package indevo.industries.ratsettlement.facility;

import assortment_of_things.frontiers.data.SettlementFacilitySlot;
import assortment_of_things.frontiers.interactions.SettlementInteraction;
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
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

// base of every ie facility in a rat settlement; instances and their fields are saved in rat's slots, never rename
// picker instances have no facility slot, so picker calls (canBeBuild, shouldShowInPicker, getCost, tooltip) never touch it
public abstract class SettlementFacility extends BaseSettlementFacility {

    // game timestamps are milliseconds and negative in normal play; getElapsedDaysSince(0) returns Float.MAX_VALUE
    private static final long DAY_MS = 86_400_000L;
    // from this many ie facilities with options on, they move into one submenu so rat's option list fits the screen
    private static final int MENU_AT = 2;
    private static final String MENU = "menu";
    private static final String BACK = "back";

    private static final int NO_DAY = Integer.MIN_VALUE;

    private int lastDay = NO_DAY;

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

        int day = (int) Math.floorDiv(Global.getSector().getClock().getTimestamp(), DAY_MS);
        if (lastDay == NO_DAY) lastDay = day;
        if (day == lastDay) return;

        lastDay = day;
        onNewDay();
    }

    protected void onNewDay() {
    }

    // rat calls every functional facility; with a submenu only the first ie facility with options adds its entry
    @Override
    public void populateSettlementDialog(InteractionDialogAPI dialog, RATInteractionPlugin plugin) {
        if (!hasOptions()) return;

        List<SettlementFacility> grouped = withOptions();
        if (grouped.size() < MENU_AT) addOptions(dialog);
        else if (grouped.get(0) == this) addOption(dialog, "Visit the facilities", MENU);
    }

    protected boolean hasOptions() {
        return false;
    }

    protected void addOptions(InteractionDialogAPI dialog) {
    }

    private List<SettlementFacility> withOptions() {
        List<SettlementFacility> list = new ArrayList<>();
        for (SettlementFacilitySlot slot : getSettlement().getFunctionalSlots()) {
            if (slot.getPlugin() instanceof SettlementFacility facility && facility.hasOptions()) list.add(facility);
        }
        return list;
    }

    private void showMenu(InteractionDialogAPI dialog) {
        dialog.getOptionPanel().clearOptions();
        for (SettlementFacility facility : withOptions()) facility.addOptions(dialog);

        OptionKey back = new OptionKey(getID(), BACK);
        dialog.getOptionPanel().addOption("Back", back);
        dialog.getOptionPanel().setShortcut(back, Keyboard.KEY_ESCAPE, false, false, false, false);
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

        switch (key.action()) {
            case MENU -> showMenu(dialog);
            case BACK -> {
                if (!(dialog.getPlugin() instanceof SettlementInteraction settlement)) throw new IllegalStateException("settlement submenu outside rat's settlement dialog");
                settlement.populateOptions();
            }
            default -> onOption(dialog, key.action());
        }
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
