package indevo.industries.senate.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.senate.EdictRequirement;

import java.util.List;

public interface EdictAPI {
    String getShortDesc();

    List<EdictRequirement> getRequirements(MarketAPI market);

    boolean isPresenceConditionMet(MarketAPI market);

    void addEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad);

    void addRuntimeText(TooltipMakerAPI tooltip, MarketAPI market, float pad);

    int getMinRuntime(MarketAPI market);

    int getDaysPassed();

    int getRemainingDays();

    int getRemovalUnrest();

    int getRelicRemovalCost();

    int getRelicRemovalUnrest();

    void removeWithPenalty();

    void removeWithoutPenalty();

    void removeEarlyWithRelics();
}
