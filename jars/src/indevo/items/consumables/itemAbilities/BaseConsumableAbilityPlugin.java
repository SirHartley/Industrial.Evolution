package indevo.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.items.consumables.ItemAbilityHelper;
import indevo.items.consumables.fleet.FleetConsumableInventoryManager;

public abstract class BaseConsumableAbilityPlugin extends BaseDurationAbility implements SingleUseItemAbility {
    /*plugin params	desc

    todo
      IndEvo_ability_supercharger 	accelerates fleet into facing direction at burn 20 for 5 seconds

    optional (maybe don't do, kinda boring)
      IndEvo_ability_drones	        next survey requires 20% supply and crew
      IndEvo_ability_stabilizer	    all player fleet ships nearly guaranteed recoverable for next battle

    done
      IndEvo_ability_nanites	        Instantly restores a buncha armor, hull and CR on all ships in the fleet
      IndEvo_ability_spike	        Intercept mine
      IndEvo_ability_decoy            pulls all fleets to location and makes em search for like 5 days or smth
      IndEvo_ability_locator	    Hyperspace Locator for Ruins (vast or better)/research stations/orbital labs, works like ability in green for 3 minutes
      IndEvo_ability_scoop	        Creates X amount of fuel from a nearby star, nebula or gas giant
    */

    @Override
    public float getTotalDurationDays() {
        return super.getTotalDurationDays();
    }

    @Override
    public void activate() {
        super.activate();
        removeTriggerItem();
    }

    @Override
    public void removeTriggerItem() {
        if (entity instanceof CampaignFleetAPI) {
            if (entity.isPlayerFleet()) Global.getSector().getPlayerFleet().getCargo().removeItems(
                    CargoAPI.CargoItemType.SPECIAL,
                    new SpecialItemData(getItemID(), null),
                    1);
            else FleetConsumableInventoryManager.adjustInventoryAmount(getItemID(), (CampaignFleetAPI) entity, -1);
        }
    }

    @Override
    public boolean isActiveOrInProgress() {
        return super.isActiveOrInProgress() || turnedOn;
    }

    @Override
    public boolean isUsable() {
        return super.isUsable() &&
                getCargoItemAmt() > 0;
    }

    public int getCargoItemAmt() {
        if (entity instanceof CampaignFleetAPI) {
            if (entity.isPlayerFleet()) return (int) Math.floor(Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(getItemID(), null)));
            else FleetConsumableInventoryManager.hasItemOfType(getItemID(), (CampaignFleetAPI) entity);
        }

        return 0;
    }

    @Override
    public String getItemID() {
        return ItemAbilityHelper.toggle(id);
    }

    public boolean hasTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        addTooltip(tooltip, false);


    }
}
