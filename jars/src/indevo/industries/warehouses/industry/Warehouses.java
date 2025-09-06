package indevo.industries.warehouses.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.warehouses.data.WarehouseConstants;
import indevo.industries.warehouses.data.WarehouseSubmarketData;
import indevo.submarkets.RemovablePlayerSubmarketPluginAPI;

import java.util.ArrayList;
import java.util.List;

public class Warehouses extends BaseIndustry {

    private List<WarehouseSubmarketData> archiveSubMarkets = new ArrayList<>();

    @Override
    public void apply() {

        //player installs item
        if (!getId().equals(Ids.WARPHOUSE) && getSpecialItem() != null && !isUpgrading()) startUpgrading(Ids.WARPHOUSE);

        //player removes item mid-upgrade
        if (!getId().equals(Ids.WARPHOUSE) && getSpecialItem() == null && isUpgrading()) cancelUpgrade();

        //player removes item from upgraded industry
        if (getId().equals(Ids.WARPHOUSE) && getSpecialItem() == null) startUpgrading(Ids.WAREHOUSES, 1);

        //player re-installs item into warp complex after removing it
        if (getId().equals(Ids.WARPHOUSE) && getSpecialItem() != null && isUpgrading()) cancelUpgrade();
    }

    public String getBuildOrUpgradeProgressText() {
        if (isDisrupted()) {
            int left = (int) getDisruptedDays();
            if (left < 1) left = 1;
            String days = "days";
            if (left == 1) days = "day";

            return "Disrupted: " + left + " " + days + " left";
        }

        int left = (int) (buildTime - buildProgress);
        if (left < 1) left = 1;
        String days = "days";
        if (left == 1) days = "day";

        if (isUpgrading()) {
            String pre = "Integrating: ";
            if (Ids.WAREHOUSES.equals(upgradeId)) pre = "Downgrading: ";
            return pre + left + " " + days + " left";
        } else {
            return "Building: " + left + " " + days + " left";
        }
    }

    public void startUpgrading(String target){
        IndustrySpecAPI upgrade = Global.getSettings().getIndustrySpec(target);
        startUpgrading(target, upgrade.getBuildTime());
    }

    public void startUpgrading(String target, float days){
        building = true;
        buildProgress = 0;
        upgradeId = target;
        buildTime = days;
    }

    @Override
    public void downgrade() {
        super.downgrade();
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        float opad = 10f;
        float spad = 5f;

        tooltip.addPara("Active warehouses: %s of %s", opad, Misc.getHighlightColor(), getWarehouseSubMarkets().size() + "", WarehouseConstants.MAX_ADDITIONAL_SUBMARKETS + "");

        if (isUpgrading() && getSpecialItem() != null) tooltip.addPara("Currently integrating a wormhole anchor.", Misc.getHighlightColor(), opad);
        if (isUpgrading() && getSpecialItem() == null) {
            tooltip.addPara("Downgrading: Wormhole Anchor uninstalled.", Misc.getNegativeHighlightColor(), opad);
        }
    }

    public SubmarketAPI addSubmarket(WarehouseSubmarketData data){
        archiveSubMarkets.add(data); //must be before addition, submarket plugin checks for data on init
        market.addSubmarket(data.submarketID);

        return market.getSubmarket(data.submarketID);
    }

    public void removeSubmarket(WarehouseSubmarketData data){
        ((RemovablePlayerSubmarketPluginAPI) market.getSubmarket(data.submarketID).getPlugin()).notifyBeingRemoved();
        market.removeSubmarket(data.submarketID);
        archiveSubMarkets.remove(data);
    }

    public void removeSubmarkets(){
        for (WarehouseSubmarketData data : new ArrayList<>(archiveSubMarkets)){
            ((RemovablePlayerSubmarketPluginAPI) market.getSubmarket(data.submarketID).getPlugin()).notifyBeingRemoved();
            market.removeSubmarket(data.submarketID);
            archiveSubMarkets.remove(data);
        }
    }

    public List<WarehouseSubmarketData> getWarehouseSubMarkets() {
        return archiveSubMarkets;
    }

    public WarehouseSubmarketData getData(SubmarketPlugin forPlugin){
        for (WarehouseSubmarketData data : archiveSubMarkets) if (data.submarketID.equals(forPlugin.getSubmarket().getSpecId())) return data;
        return null;
    }


    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        removeSubmarkets();
    }

    @Override
    public boolean canInstallAICores() {
        return false;
    }

    public static void adjustWormholeAnchorSpec(){
        SpecialItemSpecAPI spec =  Global.getSettings().getSpecialItemSpec(Items.WORMHOLE_ANCHOR);
        String params = spec.getParams();

        if (params != null && params.contains(Ids.WAREHOUSES)) return; //safeguard for reload

        String addonString = "";
        if (params != null && !params.isBlank()) addonString += ",";
        addonString += Ids.WAREHOUSES;

        spec.setParams(params + addonString);
    }
}
