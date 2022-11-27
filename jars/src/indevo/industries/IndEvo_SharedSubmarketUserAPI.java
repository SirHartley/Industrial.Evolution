package indevo.industries;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface IndEvo_SharedSubmarketUserAPI {
    void addSharedSubmarket();

    void removeSharedSubmarket();

    boolean isLegalOnSharedSubmarket(CargoStackAPI stack);

    void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded);
}
