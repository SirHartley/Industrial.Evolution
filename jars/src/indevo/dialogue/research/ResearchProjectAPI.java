package indevo.dialogue.research;

import com.fs.starfarer.api.campaign.CargoAPI;

import java.util.List;

public interface ResearchProjectAPI {
    public boolean display();

    public CargoAPI getRewards();

    public List<RequiredItem> getRequiredItems();

    public String getShortDesc();

    public String getLongDesc();
}
