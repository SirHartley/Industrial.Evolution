package indevo.dialogue.research;

import com.fs.starfarer.api.campaign.CargoAPI;

public class IndEvo_RequiredItem {
    public String id;
    public CargoAPI.CargoItemType type;
    public float points;

    public IndEvo_RequiredItem(String id, CargoAPI.CargoItemType type, float pointsPerItem) {
        this.id = id;
        this.type = type;
        this.points = pointsPerItem;
    }
}
