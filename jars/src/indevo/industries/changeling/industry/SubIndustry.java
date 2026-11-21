package indevo.industries.changeling.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class SubIndustry implements SubIndustryAPI {
    public static final float BASE_COST = 50000;
    public static final float BASE_BUILD_TIME = 31;

    protected String id;
    protected String imageName;
    protected String name;
    protected String descriptionID;
    protected float cost;
    protected float buildTime;

    protected Industry industry;
    protected MarketAPI market;

    public SubIndustry(SubIndustryData data){
        this.id = data.id;
        this.name = data.name;
        this.descriptionID = data.descriptionID;
        this.cost = data.cost;
        this.buildTime = data.buildTime;
        this.imageName = data.imageName;
    }

    public SubIndustry(String id, String imageName, String name, String descriptionID) {
        this.id = id;
        this.imageName = imageName;
        this.name = name;
        this.descriptionID = descriptionID;
        this.cost = BASE_COST;
        this.buildTime = BASE_BUILD_TIME;
    }

    public SubIndustry(String id, String imageName, String name, String descriptionID, float cost, float buildTime) {
        this.id = id;
        this.imageName = imageName;
        this.name = name;
        this.descriptionID = descriptionID;
        this.cost = cost;
        this.buildTime = buildTime;
    }

    @Override
    public void init(Industry industry) {
        this.industry = industry;
        this.market = industry.getMarket();
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

    @Override
    public float getCost() {
        return cost;
    }

    @Override
    public float getBuildTime() {
        return buildTime;
    }

    @Override
    public boolean isAvailableToBuild() {
        return true;
    }

    @Override
    public String getUnavailableReason() {
        return "Can not be built here";
    }

    @Override
    public float getPatherInterest(Industry industry) {
        return Float.MAX_VALUE;
    }

    @Override
    public void advance(float amt) {

    }

    @Override
    public void unapply() {

    }

    @Override
    public void preApplyToOther(Industry ind) {

    }

    @Override
    public void applyToOther(Industry ind) {

    }

    @Override
    public void unapplyFromOther(Industry ind) {

    }

    // the host's own BaseIndustry mod ids, BaseIndustry.getModId is protected
    protected String modId() {
        return "ind_" + industry.getSpec().getId();
    }

    protected String modId(int index) {
        return modId() + "_" + index;
    }

    private static final Set<String> LOADED_TEXTURES = new HashSet<>();

    // variant sprites are not preloaded, load each once per session before it is shown
    public static void ensureTexture(String image) {
        if (image == null || !LOADED_TEXTURES.add(image)) return;

        try {
            Global.getSettings().loadTexture(image);
        } catch (IOException e) {
            Global.getLogger(SubIndustry.class).error("Could not load image " + image);
        }
    }

    public boolean isInit(){
        return market != null;
    }

    @Override
    public void addRightAfterDescription(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {

    }
}
