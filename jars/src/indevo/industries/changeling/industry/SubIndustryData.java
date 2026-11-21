package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class SubIndustryData implements SubIndustryDataAPI {
    public static final float BASE_BUILD_TIME = 31;
    public static final String UNLOCK_PREFIX = "IndEvo_unlock_";

    public String id;
    public String imageName;
    public String name;
    public String descriptionID;
    public float cost;
    public float buildTime = BASE_BUILD_TIME;
    public final List<EffectLine> effects = new ArrayList<>();
    public String requires;

    private final Function<SubIndustryData, SubIndustry> factory;

    public SubIndustryData(String id, String name, String imageName, String descriptionID, float cost, Function<SubIndustryData, SubIndustry> factory) {
        this.id = id;
        this.imageName = imageName;
        this.name = name;
        this.descriptionID = descriptionID;
        this.cost = cost;
        this.factory = factory;
    }

    @Override
    public SubIndustry newInstance() {
        return factory.apply(this);
    }

    public SubIndustryData gain(String text, String... highlights) {
        effects.add(new EffectLine(true, text, highlights));
        return this;
    }

    public SubIndustryData cost(String text, String... highlights) {
        effects.add(new EffectLine(false, text, highlights));
        return this;
    }

    public SubIndustryData requires(String text) {
        requires = text;
        return this;
    }

    @Override
    public String getImageName(MarketAPI market) {
        return imageName;
    }

    // the never built industry that ashes of the domain research rewards for this variant
    public String getUnlockId() {
        return UNLOCK_PREFIX + id;
    }
}
