package indevo.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.itemPlugins.SpooferConsumableItemPlugin;

import java.awt.*;

public class RelayAbilityPlugin extends BaseConsumableAbilityPlugin {

    @Override
    protected void activateImpl() {
        Global.getSector().getPlayerFleet().addTag(Tags.COMM_RELAY);
    }

    @Override
    protected void applyEffect(float amount, float level) {

    }

    @Override
    protected void deactivateImpl() {
        Global.getSector().getPlayerFleet().removeTag(Tags.COMM_RELAY);
    }

    @Override
    protected void cleanupImpl() {
        Global.getSector().getPlayerFleet().removeTag(Tags.COMM_RELAY);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (getFleet() != null && !getFleet().isInHyperspace()) deactivate();
    }

    @Override
    public boolean isUsable() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        return super.isUsable() && fleet.isInHyperspace();  //only in hyperspace
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, boolean forItem) {
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        float opad = 10f;

        if (!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }

        String id = isActiveOrInProgress() ? entity.getFaction().getId() : SpooferConsumableItemPlugin.getCurrentFaction();
        FactionAPI faction = Global.getSector().getFaction(id);

        tooltip.addPara("Establishes a direct link to the closest comm relay.", opad, Misc.getHighlightColor(),

                Math.round(getDurationDays()) + " days");
        tooltip.addPara("The link will stay active for %s or until the fleet %s.", opad, highlight,
                "7 days", "exits hyperspace");

        if (!getFleet().isInHyperspace()) tooltip.addPara("%s",
                opad, Misc.getNegativeHighlightColor(),
                "Only usable in Hyperspace!");
    }
}
