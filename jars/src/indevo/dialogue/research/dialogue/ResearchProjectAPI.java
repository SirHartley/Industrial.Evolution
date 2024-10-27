package indevo.dialogue.research.dialogue;

import com.fs.starfarer.api.campaign.CargoAPI;

import java.util.List;

public interface ResearchProjectAPI {
    boolean display();

    CargoAPI getRewards();

    List<RequiredItem> getRequiredItems();

    String getShortDesc();

    String getLongDesc();
}
