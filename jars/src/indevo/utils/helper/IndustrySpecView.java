package indevo.utils.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidDangerLevel;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

import java.util.Objects;
import java.util.Set;

public class IndustrySpecView implements IndustrySpecAPI {

    private final IndustrySpecAPI spec;
    private final String upgrade;
    private final String downgrade;

    public IndustrySpecView(IndustrySpecAPI spec, String upgrade, String downgrade) {
        this.spec = spec;
        this.upgrade = upgrade;
        this.downgrade = downgrade;
    }

    public static IndustrySpecView of(IndustrySpecView cached, IndustrySpecAPI spec, String upgrade, String downgrade) {
        if (cached != null && cached.isViewOf(spec, upgrade, downgrade)) return cached;
        return new IndustrySpecView(spec, upgrade, downgrade);
    }

    private boolean isViewOf(IndustrySpecAPI spec, String upgrade, String downgrade) {
        return this.spec == spec && Objects.equals(this.upgrade, upgrade) && Objects.equals(this.downgrade, downgrade);
    }

    @Override
    public String getUpgrade() {
        return upgrade;
    }

    @Override
    public String getDowngrade() {
        return downgrade;
    }

    @Override
    public Industry getUpgradePluginInstance(MarketAPI market) {
        return getPluginInstance(upgrade, market);
    }

    @Override
    public Industry getDowngradePluginInstance(MarketAPI market) {
        return getPluginInstance(downgrade, market);
    }

    private static Industry getPluginInstance(String id, MarketAPI market) {
        if (id == null) return null;

        IndustrySpecAPI target = Global.getSettings().getIndustrySpec(id);
        return target == null ? null : target.getNewPluginInstance(market);
    }

    @Override
    public void setUpgrade(String upgrade) {
        spec.setUpgrade(upgrade);
    }

    @Override
    public void setDowngrade(String downgrade) {
        spec.setDowngrade(downgrade);
    }

    @Override
    public String getId() {
        return spec.getId();
    }

    @Override
    public void setId(String id) {
        spec.setId(id);
    }

    @Override
    public Industry getNewPluginInstance(MarketAPI market) {
        return spec.getNewPluginInstance(market);
    }

    @Override
    public String getPluginClass() {
        return spec.getPluginClass();
    }

    @Override
    public void setPluginClass(String effectClass) {
        spec.setPluginClass(effectClass);
    }

    @Override
    public Set<String> getTags() {
        return spec.getTags();
    }

    @Override
    public void addTag(String tag) {
        spec.addTag(tag);
    }

    @Override
    public boolean hasTag(String tag) {
        return spec.hasTag(tag);
    }

    @Override
    public String getName() {
        return spec.getName();
    }

    @Override
    public void setName(String name) {
        spec.setName(name);
    }

    @Override
    public String getDesc() {
        return spec.getDesc();
    }

    @Override
    public void setDesc(String desc) {
        spec.setDesc(desc);
    }

    @Override
    public float getCost() {
        return spec.getCost();
    }

    @Override
    public void setCost(float costMult) {
        spec.setCost(costMult);
    }

    @Override
    public float getBuildTime() {
        return spec.getBuildTime();
    }

    @Override
    public void setBuildTime(float buildTime) {
        spec.setBuildTime(buildTime);
    }

    @Override
    public float getUpkeep() {
        return spec.getUpkeep();
    }

    @Override
    public void setUpkeep(float upkeep) {
        spec.setUpkeep(upkeep);
    }

    @Override
    public float getIncome() {
        return spec.getIncome();
    }

    @Override
    public void setIncome(float income) {
        spec.setIncome(income);
    }

    @Override
    public String getImageName() {
        return spec.getImageName();
    }

    @Override
    public void setImageName(String imageName) {
        spec.setImageName(imageName);
    }

    @Override
    public int getOrder() {
        return spec.getOrder();
    }

    @Override
    public void setOrder(int order) {
        spec.setOrder(order);
    }

    @Override
    public String getData() {
        return spec.getData();
    }

    @Override
    public void setData(String data) {
        spec.setData(data);
    }

    @Override
    public RaidDangerLevel getDisruptDanger() {
        return spec.getDisruptDanger();
    }

    @Override
    public void setDisruptDanger(RaidDangerLevel disruptDanger) {
        spec.setDisruptDanger(disruptDanger);
    }

    @Override
    public ModSpecAPI getSourceMod() {
        return spec.getSourceMod();
    }
}
