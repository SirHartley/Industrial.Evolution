package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import static indevo.abilities.splitfleet.FleetUtils.transferCommodity;

// sets the detachment side's supplies, fuel, crew and marines, the player side keeps the rest
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
        float fuel = getFuel(detachment, player);

        float totalCrew = getTotal(Commodities.CREW, detachment, player);
        float minCrew = Math.min(detachment.totalCrewCap, Math.min(detachment.requiredCrew, getSpareCrew(totalCrew, player)));
        //marines only make way for the minimum crew
        float marines = Math.min(detachment.cargo.getMarines(), Math.max(0f, detachment.totalCrewCap - minCrew));
        float crew = getCrew(mode, minCrew, marines, totalCrew, detachment, player);

        set(Commodities.SUPPLIES, supplies, detachment.cargo, player.cargo);
        set(Commodities.FUEL, fuel, detachment.cargo, player.cargo);
        set(Commodities.MARINES, marines, detachment.cargo, player.cargo);
        set(Commodities.CREW, crew, detachment.cargo, player.cargo);
    }

    private static float getSupplies(Mode mode, FleetStatus detachment, FleetStatus player) {
        float total = getTotal(Commodities.SUPPLIES, detachment, player);
        float need = detachment.requiredSuppliesPerMonth + detachment.suppliesToRecover;
        float playerNeed = player.requiredSuppliesPerMonth + player.suppliesToRecover;
        float room = getSupplyRoom(detachment);

        //short on supplies, the detachment gets at most half
        float min = Math.min(room, Math.min(need, Math.max(total - playerNeed, total / 2f)));
        float amount = mode == Mode.MIN ? min : Math.max(min, Math.min(room, total - playerNeed));
        return addOverflow(amount, room, total, getSupplyRoom(player));
    }

    private static float getFuel(FleetStatus detachment, FleetStatus player) {
        float total = getTotal(Commodities.FUEL, detachment, player);
        return addOverflow(Math.min(detachment.totalFuelCap, total / 2f), detachment.totalFuelCap, total, player.totalFuelCap);
    }

    private static float getCrew(Mode mode, float minCrew, float marines, float totalCrew, FleetStatus detachment, FleetStatus player) {
        float room = Math.max(0f, detachment.totalCrewCap - marines);
        float amount = mode == Mode.MIN ? minCrew : Math.max(minCrew, Math.min(room, getSpareCrew(totalCrew, player)));

        float playerMarines = player.cargo.getMarines() + detachment.cargo.getMarines() - marines;
        return addOverflow(amount, room, totalCrew, player.totalCrewCap - playerMarines);
    }

    private static float getSpareCrew(float totalCrew, FleetStatus player) {
        return Math.max(0f, totalCrew - player.requiredCrew);
    }

    private static float getSupplyRoom(FleetStatus status) {
        float spacePerUnit = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES).getCargoSpace();
        float otherCargo = status.cargo.getSpaceUsed() - status.cargo.getSupplies() * spacePerUnit;
        return Math.max(0f, (status.totalCargoCap - otherCargo) / spacePerUnit);
    }

    //what the player side can not hold goes to the detachment while it has room
    private static float addOverflow(float amount, float room, float total, float playerRoom) {
        return Math.max(amount, Math.min(room, total - Math.max(0f, playerRoom)));
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
