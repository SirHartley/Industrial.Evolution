package indevo.industries.courierport.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.MarkerData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingCostCalculator;
import indevo.industries.courierport.ShippingTargetHelper;
import indevo.utils.helper.MiscIE;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// vanilla planet picker for the origin or the destination of a contract; onClose gets the pick or null
public class ShippingPlanetPicker implements CampaignEntityPickerListener {

    private final ShippingContract contract;
    private final boolean origin;
    private final Consumer<SectorEntityToken> onClose;

    private ShippingPlanetPicker(ShippingContract contract, boolean origin, Consumer<SectorEntityToken> onClose) {
        this.contract = contract;
        this.origin = origin;
        this.onClose = onClose;
    }

    public static void show(InteractionDialogAPI dialog, ShippingContract contract, boolean origin, Consumer<SectorEntityToken> onClose) {
        List<SectorEntityToken> targets = origin ? ShippingTargetHelper.getValidOriginPlanets() : ShippingTargetHelper.getValidTargetPlanets(contract);
        String title = origin ? "Select a planet to ship from" : "Select a planet to ship to";
        dialog.showCampaignEntityPicker(title, "Selected: ", "Confirm", Global.getSector().getPlayerFaction(), targets,
                new ShippingPlanetPicker(contract, origin, onClose));
    }

    @Override
    public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
        MarketAPI m = entity.getMarket();
        return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
    }

    @Override
    public void pickedEntity(SectorEntityToken entity) {
        onClose.accept(entity);
    }

    @Override
    public void cancelledEntityPicking() {
        onClose.accept(null);
    }

    @Override
    public String getSelectedTextOverrideFor(SectorEntityToken entity) {
        MarketAPI m = entity.getMarket();
        if (!origin) return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
        return m.getName() + " (" + m.getFaction().getDisplayName() + ", " + getStoredAmount(m) + " items in storage)";
    }

    @Override
    public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
        float opad = 10f;
        MarketAPI m = entity.getMarket();

        if (origin) {
            info.addPara(m.getName() + " (" + m.getFaction().getDisplayName() + ", size " + m.getSize() + ", " + getStoredAmount(m) + " items in storage)", opad);
            return;
        }

        info.addPara(m.getName() + " (" + m.getFaction().getDisplayName() + ", size " + m.getSize() + ")", opad);
        MarketAPI from = contract.getFromMarket();
        if (from != null) info.addPara("Distance cost multiplicator: %s", opad, Misc.getHighlightColor(),
                Misc.getRoundedValueMaxOneAfterDecimal(ShippingCostCalculator.getLYMult(from.getPrimaryEntity(), entity)) + "x");
    }

    private static int getStoredAmount(MarketAPI m) {
        CargoAPI cargo = MiscIE.getStorageCargo(m);
        return cargo == null ? 0 : (int) cargo.getSpaceUsed();
    }

    @Override
    public boolean canConfirmSelection(SectorEntityToken entity) {
        return entity != null && entity.getMarket() != null;
    }

    @Override
    public float getFuelColorAlphaMult() {
        return 0;
    }

    @Override
    public float getFuelRangeMult() {
        return 0;
    }

    @Override
    public List<IntelInfoPlugin.ArrowData> getArrows() {
        return null;
    }

    @Override
    public List<MarkerData> getMarkers() {
        return null;
    }

    @Override
    public Set<StarSystemAPI> getStarSystemsToShow() {
        return null;
    }
}
