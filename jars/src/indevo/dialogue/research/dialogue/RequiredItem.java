package indevo.dialogue.research.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;

public class RequiredItem {
    public String id;
    public CargoAPI.CargoItemType type;
    public float points;

    public RequiredItem(String id, CargoAPI.CargoItemType type, float pointsPerItem) {
        this.id = id;
        this.type = type;
        this.points = pointsPerItem;
    }

    public String getName() {
        return getName(type, id);
    }

    public String getSprite() {
        return getSprite(type, id);
    }

    public static String getName(CargoAPI.CargoItemType type, String id) {
        return switch (type) {
            case RESOURCES -> Global.getSettings().getCommoditySpec(id).getName();
            case WEAPONS -> Global.getSettings().getWeaponSpec(id).getWeaponName();
            case FIGHTER_CHIP -> Global.getSettings().getFighterWingSpec(id).getWingName();
            case SPECIAL -> Global.getSettings().getSpecialItemSpec(id).getName();
            default -> throw new IllegalStateException("Unsupported item type " + type + " for " + id);
        };
    }

    // weapons show the turret sprite, the vanilla cargo icon is a composite the api does not expose
    public static String getSprite(CargoAPI.CargoItemType type, String id) {
        return switch (type) {
            case RESOURCES -> Global.getSettings().getCommoditySpec(id).getIconName();
            case WEAPONS -> Global.getSettings().getWeaponSpec(id).getTurretSpriteName();
            case FIGHTER_CHIP -> Global.getSettings().getFighterWingSpec(id).getVariant().getHullSpec().getSpriteName();
            case SPECIAL -> Global.getSettings().getSpecialItemSpec(id).getIconName();
            default -> null;
        };
    }
}
