package industrial_evolution.campaign.rulecmd.researchProjects;

import com.fs.starfarer.api.campaign.CargoAPI;

import java.util.List;

public interface IndEvo_ResearchProjectAPI {
    public boolean display();

    public CargoAPI getRewards();

    public List<IndEvo_RequiredItem> getRequiredItems();

    public String getShortDesc();
}
