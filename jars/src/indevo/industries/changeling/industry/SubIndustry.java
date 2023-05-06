package indevo.industries.changeling.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.Description;

public abstract class SubIndustry implements SubIndustryAPI {
    protected String id;
    protected String imageName;
    protected String name;
    protected String descriptionID;

    public SubIndustry(String id, String name, String imageName, String descriptionID) {
        this.id = id;
        this.name = name;
        this.imageName = imageName;
        this.descriptionID = descriptionID;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getImageName(MarketAPI market) {
        return imageName;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Description getDescription() {
        return Global.getSettings().getDescription(descriptionID, Description.Type.CUSTOM);
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
