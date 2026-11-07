package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import static indevo.abilities.splitfleet.FleetUtils.transferCommodity;

// sets the detachment side's supplies, fuel and crew, the player side keeps the rest
public class DetachmentCargoLoad {

    public enum Mode {
        MIN,
        MAX
    }

    public interface Picker {
        void load(Mode mode);
    }

    public static void apply(Mode mode, FleetStatus detachment, FleetStatus player) {
        float supplies = getSupplies(mode, detachment, player);
        float fuel = Math.min(detachment.totalFuelCap, getTotal(Commodities.FUEL, detachment, player) / 2f);
        float crew = getCrew(mode, detachment, player);

        set(Commodities.SUPPLIES, supplies, detachment.cargo, player.cargo);
        set(Commodities.FUEL, fuel, detachment.cargo, player.cargo);
        set(Commodities.CREW, crew, detachment.cargo, player.cargo);
    }

    private static float getSupplies(Mode mode, FleetStatus detachment, FleetStatus player) {
        float total = getTotal(Commodities.SUPPLIES, detachment, player);
        float need = detachment.requiredSuppliesPerMonth + detachment.suppliesToRecover;
        float playerNeed = player.requiredSuppliesPerMonth + player.suppliesToRecover;

        float spacePerUnit = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES).getCargoSpace();
        float otherCargo = detachment.cargo.getSpaceUsed() - detachment.cargo.getSupplies() * spacePerUnit;
        float room = Math.max(0f, (detachment.totalCargoCap - otherCargo) / spacePerUnit);

        //short on supplies, the detachment gets at most half
        float min = Math.min(room, Math.min(need, Math.max(total - playerNeed, total / 2f)));
        return mode == Mode.MIN ? min : Math.max(min, Math.min(room, total - playerNeed));
    }

    private static float getCrew(Mode mode, FleetStatus detachment, FleetStatus player) {
        float spare = Math.max(0f, getTotal(Commodities.CREW, detachment, player) - player.requiredCrew);
        float room = Math.max(0f, detachment.totalCrewCap - detachment.cargo.getMarines());

        float min = Math.min(room, Math.min(detachment.requiredCrew, spare));
        return mode == Mode.MIN ? min : Math.max(min, Math.min(room, spare));
    }

    private static float getTotal(String id, FleetStatus detachment, FleetStatus player) {
        return detachment.cargo.getCommodityQuantity(id) + player.cargo.getCommodityQuantity(id);
    }

    private static void set(String id, float amount, CargoAPI detachment, CargoAPI player) {
        float delta = (float) Math.floor(amount) - detachment.getCommodityQuantity(id);
        if (delta > 0f) transferCommodity(player, detachment, id, delta);
        else if (delta < 0f) transferCommodity(detachment, player, id, -delta);
    }
}
