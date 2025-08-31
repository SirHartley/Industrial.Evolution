package indevo.industries.warehouses.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Items;
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
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);

        tooltip.addPara("Active warehouses: %s of %s", 10f, Misc.getHighlightColor(), getWarehouseSubMarkets().size() + "", WarehouseConstants.MAX_ADDITIONAL_SUBMARKETS + "");
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
    public String getCurrentName() {
        return getSpecialItem() != null ? "Wormhole Complex" : super.getCurrentName();
    }

    @Override
    public String getCurrentImage() {
        return getSpecialItem() != null ? "graphics/industry/warphouse.png" : super.getCurrentImage();
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
