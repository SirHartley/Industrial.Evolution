package indevo.dialogue.research.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

public class RedeemRequirement {
    public final String id;
    public final CargoAPI.CargoItemType type;
    public final int count;

    public RedeemRequirement(String id, CargoAPI.CargoItemType type, int count) {
        this.id = id;
        this.type = type;
        this.count = count;
    }

    public String getName() {
        switch (type) {
            case RESOURCES:
                return Global.getSettings().getCommoditySpec(id).getName();
            case WEAPONS:
                return Global.getSettings().getWeaponSpec(id).getWeaponName();
            case FIGHTER_CHIP:
                return Global.getSettings().getFighterWingSpec(id).getWingName();
            case SPECIAL:
                return Global.getSettings().getSpecialItemSpec(id).getName();
            default:
                throw new IllegalStateException("Unsupported redeem requirement type " + type + " for " + id);
        }
    }

    public int getAvailable(CargoAPI cargo) {
        return Math.round(ResearchProjectDialoguePlugin.getQuantity(cargo, type, id));
    }

    public boolean isMet(CargoAPI cargo) {
        return getAvailable(cargo) >= count;
    }

    public CargoAPI take(CargoAPI cargo) {
        if (!isMet(cargo)) throw new IllegalStateException("Redeem requirement " + id + " not in cargo");

        CargoAPI taken = Global.getFactory().createCargo(true);
        float remaining = count;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (remaining <= 0f) break;
            if (stack.getType() != type || !id.equals(ResearchProjectDialoguePlugin.getStackItemId(stack))) continue;

            float amt = Math.min(remaining, stack.getSize());
            taken.addItems(type, stack.getData(), amt);
            cargo.removeItems(type, stack.getData(), amt);
            remaining -= amt;
        }

        return taken;
    }
}
