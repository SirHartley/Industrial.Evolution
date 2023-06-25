package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

public abstract class SubIndustryData implements SubIndustryDataAPI {
    public static final float BASE_COST = 50000;
    public static final float BASE_BUILD_TIME = 31;

    public String id;
    public String imageName;
    public String name;
    public String descriptionID;
    public float cost;
    public float buildTime;

    public SubIndustryData(String id, String name, String imageName, String descriptionID) {
        this.id = id;
        this.imageName = imageName;
        this.name = name;
        this.descriptionID = descriptionID;
        this.cost = BASE_COST;
        this.buildTime = BASE_BUILD_TIME;
    }

    public SubIndustryData(String id, String name, String imageName, String descriptionID, float cost, float buildTime) {
        this.id = id;
        this.imageName = imageName;
        this.name = name;
        this.descriptionID = descriptionID;
        this.cost = cost;
        this.buildTime = buildTime;
    }

    @Override
    public String getImageName(MarketAPI market) {
        return imageName;
    }
}
