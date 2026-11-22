package indevo.dialogue.research;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.research.dialogue.RedeemRequirement;
import indevo.dialogue.research.dialogue.RequiredItem;
import indevo.dialogue.research.dialogue.ResearchProject;
import indevo.dialogue.research.listeners.ItemPatternChecker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.compatibility.AotdManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// unlocks one ie colony item in ashes of the domain production, see AotdManager.PRODUCTION_ITEMS
public class ItemPatternProject extends ResearchProject {

    public static final int REQUIRED_POINTS = 200;

    // the card shows whole sentences on one line, keep each under about 60 characters
    private static final Map<String, String> SHORT_DESC = Map.of(
            ItemIds.SIMULATOR, "If it can simulate a star system, it can simulate itself.",
            ItemIds.LOG_CORE, "Outlawed by the Domain for a reason. Let's make more.",
            ItemIds.NEUAL_COMPOUNDS, "Chemistry swears it won't sample the product this time.",
            ItemIds.SALVAGE_DRONES, "It replicates drones. Time to replicate the replicator.",
            ItemIds.TRANSMITTER, "Nobody knows how it works. Physics brought a screwdriver.",
            ItemIds.HOLOBENCH, "Neuroscience has volunteered an intern to plug in first.");

    private final String itemId;

    public ItemPatternProject(String itemId) {
        super(getProjectId(itemId), "Project Knockoff: " + getItemName(itemId), REQUIRED_POINTS, false);
        this.itemId = itemId;
    }

    public static String getProjectId(String itemId) {
        return Ids.PROJ_PATTERN_PREFIX + itemId;
    }

    public String getItemId() {
        return itemId;
    }

    private static String getItemName(String itemId) {
        return Global.getSettings().getSpecialItemSpec(itemId).getName();
    }

    @Override
    public boolean display() {
        return AotdManager.hasItemResearch() && ItemPatternChecker.isFound(itemId) && !AotdManager.isItemKnown(itemId);
    }

    @Override
    public CargoAPI getRewards() {
        return Global.getFactory().createCargo(true);
    }

    @Override
    public List<RequiredItem> getRequiredItems() {
        List<RequiredItem> list = new ArrayList<>();
        list.add(new RequiredItem(ItemIds.RARE_PARTS, CargoAPI.CargoItemType.RESOURCES, 1f));
        return list;
    }

    @Override
    public RedeemRequirement getRedeemRequirement() {
        return new RedeemRequirement(itemId, CargoAPI.CargoItemType.SPECIAL, 1);
    }

    @Override
    public void onRedeem() {
        AotdManager.unlockItem(itemId);
    }

    @Override
    public void addTooltipOutputOnCompletion(TooltipMakerAPI tooltip) {
        tooltip.addPara("Colony item production can now build the %s.", 10f, Misc.getHighlightColor(), getItemName(itemId));
        tooltip.addPara("There were a few screws left over. Probably not important ones.", 10f);
        tooltip.addPara("  - Junior Engineer, Department of Taking Things Apart", Misc.getGrayColor(), 3f);
    }

    @Override
    public String getRedeemLore() {
        return "The engineers promise to put it back together afterwards. They will not.";
    }

    @Override
    public String getShortDesc() {
        return SHORT_DESC.getOrDefault(itemId, "Why keep hunting for these when the Academy can take one apart and copy it?");
    }

    @Override
    public String getLongDesc() {
        return "A one-off project in which the engineering department takes your perfectly good " + getItemName(itemId) + " apart to see what makes it tick. "
                + "Relic components pay for the tools. The item will not survive, and the Academy does not do refunds. "
                + "Once done, colony item production can build more.";
    }

    // shown on the item in cargo and codex while production cannot build it yet
    public static void addCargoNote(TooltipMakerAPI text, String itemId, InstallableItemDescriptionMode mode) {
        if (mode != InstallableItemDescriptionMode.CARGO_TOOLTIP || !AotdManager.isVokEnabled()) return;
        if (Global.getCurrentState() == GameState.CAMPAIGN && AotdManager.isItemKnown(itemId)) return;

        text.addPara("If you can spare one, the Galatian Academy can work out how to build it in colony item production.", Misc.getGrayColor(), 10f);
    }
}
