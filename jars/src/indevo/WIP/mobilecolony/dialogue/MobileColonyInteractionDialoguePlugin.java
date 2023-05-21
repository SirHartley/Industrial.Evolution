package indevo.WIP.mobilecolony.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.lwjgl.input.Keyboard;

import java.util.Map;

public class MobileColonyInteractionDialoguePlugin implements InteractionDialogPlugin {

    private enum Option {
        MAIN,
        INITIAL_PAY_TO_RESTORE,
        PARTS_SELECTOR,
        SELECT_SHIPS,
        CONFIRM,
        LEAVE
    }

    /*
    Options:
    - Comm Dir
    - Bar
    - Market Tab
    - Repair (check RepairAll.class)
    - IndEvo special funct.
    - Nex special funct.
    - Exotica special funct.
    - back

    Sidebar:
    - Move options
    - Upgrade screen
    - Governing options (laws, customs)
    - Exploration
     */

    protected InteractionDialogAPI dialog;
    protected TextPanelAPI text;
    protected OptionPanelAPI options;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.options = dialog.getOptionPanel();
        this.text = dialog.getTextPanel();

        displayDefaultOptions(true);
    }

    public void displayDefaultOptions(boolean clearText) {
        if (clearText) text.clear();
        addDefaultTooltip();
        if (clearText)
            dialog.getVisualPanel().showImageVisual(dialog.getInteractionTarget().getCustomInteractionDialogImageVisual());

        OptionPanelAPI opts = options;
        opts.clearOptions();

        boolean isEnabled;
        Option option;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        CargoAPI cargo = playerFleet.getCargo();

       /* if (isRepaired()){
            addPostRestoreTooltip();

            option = Option.SELECT_SHIPS;
            isEnabled = playerFleet.getFleetData().getNumMembers() > 1;

            opts.addOption("Select ships to sacrifice", option);
            opts.setEnabled(option, isEnabled);
            if(isEnabled) opts.setTooltip(option, "Select ships to offer to the machine, for it may bestow a blessing of silicone and steel made anew");
            else opts.setTooltip(option, "You can not sacrifice the only ship in your fleet, as tempting as it may be");

            float partsAvailable = cargo.getCommodityQuantity(ItemIds.PARTS);
            opts.addSelector("Sacrifice Starship Components", Option.PARTS_SELECTOR, Misc.getHighlightColor(),
                    300f,
                    50f,
                    0f,
                    partsAvailable,
                    ValueDisplayMode.VALUE,
                    partsAvailable >= 1f ?
                            "Offer ship components - know that the residing mind might consider them inferior to the sanctified metal of a true ship."
                            : "You do not have any ship components to sacrifice");

            opts.setSelectorValue(Option.PARTS_SELECTOR, partsToSacrifice);

            option = Option.CONFIRM;
            isEnabled = (!selectedShips.isEmpty() || partsToSacrifice > 0) && playerFleet.getCargo().getCommodityQuantity(ItemIds.RARE_PARTS) >= RARE_PART_COST_AMT;

            opts.addOption("Start the machine", option);
            opts.setEnabled(option, isEnabled);
            if(isEnabled) opts.setTooltip(option, "Pray to the mechanic deity to grant upon you a rare print from the deepest parts of the ancient mnemonic relays");
            else {
                opts.setTooltip(option, "You do not have a sacrifice selected or do not have sufficient relic components to offer upon the altar. (Cost: " + RARE_PART_COST_AMT + " Relic Components)");
                opts.setTooltipHighlightColors(option, Misc.getHighlightColor());
                opts.setTooltipHighlights(option, "(Cost: " + RARE_PART_COST_AMT + " Relic Components)");
            }
        } else {
            addPreRestoreTooltip();

            option = Option.INITIAL_PAY_TO_RESTORE;

            isEnabled = cargo.getCommodityQuantity(Commodities.METALS) >= METALS_REPAIR_COST
                    && cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY) >= MACHINERY_REPAIR_COST
                    && cargo.getCommodityQuantity(ItemIds.PARTS) >= PARTS_REPAIR_COST;

            opts.addOption("Restore the machine to function", option);
            opts.setEnabled(option, isEnabled);
            if(isEnabled) opts.setTooltip(option, "Deliver the offerings to the monolithic drop-off zones and perform the initialization rites");
            else opts.setTooltip(option, "Your cargo contains insufficient offerings to restore this temple to its former glory");
        }*/

        option = Option.LEAVE;
        opts.addOption("Leave", option);
        opts.setShortcut(option, Keyboard.KEY_ESCAPE, false, false, false, true);
    }


    @Override
    public void optionSelected(String optionText, Object optionData) {
        options.clearOptions();
        Option opt = (Option) optionData;

        switch (opt) {
            case MAIN:
                displayDefaultOptions(true);
                break;
            case INITIAL_PAY_TO_RESTORE:
                break;
            case SELECT_SHIPS:
                break;
            case CONFIRM:
                break;
            case LEAVE:
                dialog.dismiss();
                break;
        }
    }

    @Override
    public void advance(float amount) {
        OptionPanelAPI panel = dialog.getOptionPanel();

    }

    private void addDefaultTooltip() {
        //CampaignFleetAPI f = DetachmentMemory.getDetachment(detachmentNum);
        //dialog.getVisualPanel().showFleetInfo(f.getName(), f, null, null);
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
