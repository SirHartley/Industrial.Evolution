package indevo.industries.senate.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.senate.EdictEffect;
import indevo.industries.senate.EdictRequirement;
import indevo.industries.senate.EdictSupplyChange;

import java.util.List;

public interface EdictAPI {
    String getShortDesc();

    List<EdictRequirement> getRequirements(MarketAPI market);

    boolean isPresenceConditionMet(MarketAPI market);

    List<EdictEffect> getEffects(MarketAPI market);

    List<EdictSupplyChange> getSupplyChanges(MarketAPI market);

    void addRuntimeText(TooltipMakerAPI tooltip, MarketAPI market, float pad);

    int getMinRuntime(MarketAPI market);

    boolean endsOnItsOwn();

    int getFinishUnrest(MarketAPI market);

    int getMaxEarlyRemovalUnrest(MarketAPI market);

    int getDaysPassed();

    int getRemainingDays();

    int getRemovalUnrest();

    int getRelicRemovalCost();

    int getRelicRemovalUnrest();

    void removeWithPenalty();

    void removeWithoutPenalty();

    void removeEarlyWithRelics();
}
