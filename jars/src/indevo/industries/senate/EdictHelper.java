package indevo.industries.senate;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.industries.senate.conditions.EdictAPI;
import indevo.utils.helper.MiscIE;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static indevo.items.installable.SpecialItemEffectsRepo.NEURAL_COMPOUNDS_UNREST_RED;
import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;

public class EdictHelper {
    public static final String COMPOUNDS_NAME = "Neuroconditioning Compounds";
    private static final Set<String> warnedConditions = new HashSet<>();

    public static MarketAPI getSystemSenate(MarketAPI market) {
        return getSystemSenate(market, null);
    }

    public static MarketAPI getSystemSenate(MarketAPI market, MarketAPI excluded) {
        StarSystemAPI system = market.getStarSystem();
        if (system == null) return null;

        for (MarketAPI other : MiscIE.getMarketsInLocation(system, market.getFactionId())) {
            if (other == excluded) continue;

            Industry senate = other.getIndustry(Ids.SENATE);
            if (senate != null && !senate.isBuilding()) return other;
        }

        return null;
    }

    public static Pair<MarketAPI, Float> getCompoundsSenateInRange(MarketAPI market) {
        return getCompoundsSenateInRange(market, null);
    }

    public static Pair<MarketAPI, Float> getCompoundsSenateInRange(MarketAPI market, MarketAPI excluded) {
        if (market.getPrimaryEntity() == null) return null;

        Vector2f loc = market.getLocationInHyperspace();
        MarketAPI nearest = null;
        float minDist = Float.MAX_VALUE;

        for (MarketAPI other : Misc.getNearbyMarkets(loc, RANGE_LY_TWELVE)) {
            if (!other.isPlayerOwned() || other == excluded) continue;

            Industry senate = other.getIndustry(Ids.SENATE);
            if (senate == null || senate.isBuilding() || senate.getSpecialItem() == null) continue;

            float dist = Misc.getDistanceLY(loc, other.getLocationInHyperspace());
            if (dist < minDist) {
                minDist = dist;
                nearest = other;
            }
        }

        return nearest == null ? null : new Pair<>(nearest, minDist);
    }

    public static boolean isInSenateRange(MarketAPI market) {
        return getSystemSenate(market) != null || getCompoundsSenateInRange(market) != null;
    }

    public static EdictRequirement getSenateRequirement(MarketAPI market) {
        String shortText = "Senate in range";

        MarketAPI systemSenate = getSystemSenate(market);
        if (systemSenate != null) {
            return new EdictRequirement("Senate in the star system: " + systemSenate.getName(), shortText, true);
        }

        Pair<MarketAPI, Float> compounds = getCompoundsSenateInRange(market);
        if (compounds != null) {
            return new EdictRequirement("Senate with " + COMPOUNDS_NAME + " within " + RANGE_LY_TWELVE + " LY: "
                    + compounds.one.getName() + ", " + Misc.getRoundedValueMaxOneAfterDecimal(compounds.two) + " LY", shortText, true);
        }

        return new EdictRequirement("A Senate in the star system, or one with " + COMPOUNDS_NAME + " within " + RANGE_LY_TWELVE + " LY", shortText, false);
    }

    public static float getEarlyRemovalUnrestMult(MarketAPI market) {
        return getCompoundsSenateInRange(market) != null ? NEURAL_COMPOUNDS_UNREST_RED : 1f;
    }

    public static boolean senateHasBetaCore(MarketAPI market) {
        MarketAPI senate = getSystemSenate(market);
        return senate != null && Commodities.BETA_CORE.equals(senate.getIndustry(Ids.SENATE).getAICoreId());
    }

    public static MarketConditionAPI getActiveEdict(MarketAPI market) {
        for (MarketConditionAPI condition : market.getConditions()) {
            if (EdictRepo.isEdict(condition.getId())) return condition;
        }

        return null;
    }

    public static EdictAPI getActiveEdictPlugin(MarketAPI market) {
        MarketConditionAPI condition = getActiveEdict(market);
        return condition == null ? null : asEdict(condition);
    }

    public static EdictAPI asEdict(MarketConditionAPI condition) {
        if (condition.getPlugin() instanceof EdictAPI edict) return edict;

        if (warnedConditions.add(condition.getId())) {
            Global.getLogger(EdictHelper.class).warn("IndEvo: condition " + condition.getId() + " has the " + EdictRepo.EDICT_TAG + " tag but its plugin is not an EdictAPI, skipped");
        }

        return null;
    }

    public static List<MarketAPI> getColoniesCoveredBy(MarketAPI senateMarket) {
        List<MarketAPI> result = new ArrayList<>();
        Industry senate = senateMarket.getIndustry(Ids.SENATE);
        if (senate == null || senate.isBuilding() || senateMarket.getPrimaryEntity() == null) return result;

        boolean compounds = senate.getSpecialItem() != null;
        StarSystemAPI system = senateMarket.getStarSystem();

        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            if (!market.isPlayerOwned() || market.isHidden() || market.getPrimaryEntity() == null) continue;

            boolean inSystem = system != null && system == market.getStarSystem() && market.getFactionId().equals(senateMarket.getFactionId());
            boolean inCompoundsRange = compounds && senateMarket.isPlayerOwned()
                    && Misc.getDistanceLY(senateMarket.getLocationInHyperspace(), market.getLocationInHyperspace()) <= RANGE_LY_TWELVE;

            if (inSystem || inCompoundsRange) result.add(market);
        }

        result.sort(Comparator.comparing(EdictHelper::getSystemName)
                .thenComparing(m -> !m.hasIndustry(Ids.SENATE))
                .thenComparing(m -> -m.getSize())
                .thenComparing(MarketAPI::getName));

        return result;
    }

    public static void cancelEdictsOf(MarketAPI senateMarket) {
        for (MarketAPI colony : getColoniesCoveredBy(senateMarket)) {
            EdictAPI edict = getActiveEdictPlugin(colony);
            if (edict == null) continue;
            if (getSystemSenate(colony, senateMarket) != null || getCompoundsSenateInRange(colony, senateMarket) != null) continue;

            edict.removeWithPenalty();
        }
    }

    public static String getSystemName(MarketAPI market) {
        if (market.getStarSystem() != null) return market.getStarSystem().getBaseName();
        return market.getContainingLocation() != null ? market.getContainingLocation().getName() : "";
    }

    public static void enact(MarketAPI market, String edictId) {
        if (getActiveEdict(market) != null) throw new IllegalStateException("IndEvo: " + market.getName() + " already has an Edict, can not enact " + edictId);
        market.addCondition(edictId);
    }
}
