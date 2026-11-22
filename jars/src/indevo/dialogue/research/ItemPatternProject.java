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

// unlocks one ie colony item in ashes of the domain production, see AotdManager.PRODUCTION_ITEMS
public class ItemPatternProject extends ResearchProject {

    public static final int REQUIRED_POINTS = 200;

    private final String itemId;

    public ItemPatternProject(String itemId) {
        super(getProjectId(itemId), "Reverse Engineering: " + getItemName(itemId), REQUIRED_POINTS, false);
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
    }

    @Override
    public String getRedeemLore() {
        return "Its assembly pattern survives inside the casing. Relic components rebuild the tools needed to read it out.";
    }

    @Override
    public String getShortDesc() {
        return "Take a " + getItemName(itemId) + " apart to add it to colony item production.";
    }

    @Override
    public String getLongDesc() {
        return "The Academy takes one " + getItemName(itemId) + " apart and recovers its assembly pattern with relic components. "
                + "Deliver the item from fleet cargo when the research is complete. Afterwards, colony item production can build it.";
    }

    // shown on the item in cargo and codex while production cannot build it yet
    public static void addCargoNote(TooltipMakerAPI text, String itemId, InstallableItemDescriptionMode mode) {
        if (mode != InstallableItemDescriptionMode.CARGO_TOOLTIP || !AotdManager.isVokEnabled()) return;
        if (Global.getCurrentState() == GameState.CAMPAIGN && AotdManager.isItemKnown(itemId)) return;

        text.addPara("To build it in colony item production, finish its research project at the Galatian Academy.", Misc.getGrayColor(), 10f);
    }
}
