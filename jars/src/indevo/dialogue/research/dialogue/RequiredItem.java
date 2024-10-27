package indevo.dialogue.research.dialogue;

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
}
