package indevo.industries.embassy.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.embassy.industry.Embassy;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class HAAmbassadorEventFactor extends BaseEventFactor {

    public static final float AMBASSADOR_MAX_IMPACT_REDUCTION = 0.4f;
    public static final String HAA_SABOTAGE_MEMORY_KEY = "$IndEvo_HAASabotage";

    public HAAmbassadorEventFactor() {
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                Color h = Misc.getHighlightColor();
                float opad = 10f;

                tooltip.addPara("Your Political influence through ambassadorial work, based on your active Embassies and your reputation with the relevant faction.", 0f);

                boolean isSabotaging = getIsSabotaging();
                if (isSabotaging) tooltip.addPara("Your ambassadors are working to accelerate the actions taken by their faction by up to %s"+
                                ", scaling with your total reputation up to the embassy reputation cap.", 3f, Misc.getHighlightColor(),
                        StringHelper.getAbsPercentString(AMBASSADOR_MAX_IMPACT_REDUCTION, false));
                else tooltip.addPara("A positive reputation allows your ambassador to reduce the impact of the faction by up to %s"+
                                ", scaling with your total reputation up to the embassy reputation cap.", 3f, Misc.getHighlightColor(),
                        StringHelper.getAbsPercentString(AMBASSADOR_MAX_IMPACT_REDUCTION, false));

                tooltip.addPara("You can adjust this by talking to one of your ambassadors.", opad);

                for (FactionAPI faction : getFactionsWithAmbassadors()){
                    FactionAPI player = Global.getSector().getPlayerFaction();
                    RepLevel level = player.getRelationshipLevel(faction.getId());
                    int repInt = (int) Math.ceil((Math.round(player.getRelationship(faction.getId()) * 100f)));

                    String factionName = faction.getDisplayName();
                    int reductionInPts = Math.round(getReductionAmtForFaction(faction));
                    if (!isSabotaging) reductionInPts *= -1;

                    String standing = "[" + repInt + "/" + (int) (Embassy.BASE_MAX_RELATION * 100) + "] (" + level.getDisplayName().toLowerCase() + ")";
                    Color relColor = faction.getRelColor(player.getId());

                    Color[] highlightColors = {faction.getColor(), Misc.getTextColor(), relColor};

                    tooltip.addPara(BaseIntelPlugin.BULLET + "%s: %s points %s", opad, highlightColors,
                            factionName, reductionInPts + "", standing);
                }
            }
        };
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        return Settings.getBoolean(Settings.EMBASSY) || Global.getSettings().isDevMode();
    }

    public int getProgress(BaseEventIntel intel) {
        float total = 0f;
        for (FactionAPI f : getFactionsWithAmbassadors()) total += getReductionAmtForFaction(f);

        int redOrAdd = getIsSabotaging() ? 1 : -1;

        return Math.round(total) * redOrAdd;
    }

    @Override
    public String getProgressStr(BaseEventIntel intel) {
        boolean isSabotaging = getIsSabotaging();
        if (!isSabotaging && getProgress(intel) >= 0) return "";
        else if (isSabotaging && getProgress(intel) <= 0) return "";
        return super.getProgressStr(intel);
    }

    public String getDesc(BaseEventIntel intel) {
        return "Political Influence";
    }

    @Override
    public Color getDescColor(BaseEventIntel intel) {
        if (getProgress(intel) < 0) return Misc.getTextColor();
        return Misc.getGrayColor();
    }

    public static float getReductionAmtForFaction(FactionAPI alignedFaction){
        if (alignedFaction == null) return 0f;

        HostileActivityFactor factor = null;
        float redPoints = 0f;

        for (EventFactor eventFactor : HostileActivityEventIntel.get().getFactors()){
            if (eventFactor instanceof HostileActivityFactor){
                String descFaction = eventFactor.getDesc(null).toLowerCase();
                String nameForThreatList = ((HostileActivityFactor) eventFactor).getNameForThreatList(false).toLowerCase();
                String factionName = alignedFaction.getDisplayName().toLowerCase();

                if (factionName.contains(descFaction) || factionName.contains(nameForThreatList) || nameForThreatList.contains(factionName) || descFaction.contains(factionName)){
                    factor = (HostileActivityFactor) eventFactor;
                    break;
                }
            }
        }

        if (factor != null){
            float pts = 0;

            for (StarSystemAPI s : Misc.getSystemsWithPlayerColonies(false)){
                if (!s.isHyperspace()) {
                    pts+= factor.getEffectMagnitude(s);
                }
            }

            float cappedFactionRel = Math.min(Embassy.BASE_MAX_RELATION, alignedFaction.getRelationship(Factions.PLAYER));
            float relFraction = MathUtils.clamp(cappedFactionRel / Embassy.BASE_MAX_RELATION, 0, 1);
            float reductionFactor = relFraction * AMBASSADOR_MAX_IMPACT_REDUCTION;
            redPoints = pts * reductionFactor;
        }

        return Math.max(0, redPoints);
    }

    public List<FactionAPI> getFactionsWithAmbassadors(){
        List<FactionAPI> factionList = new ArrayList<>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)){
            if (m.hasIndustry(Ids.EMBASSY) && AmbassadorPersonManager.getAmbassador(m) != null) factionList.add(((Embassy) m.getIndustry(Ids.EMBASSY)).alignedFaction);
        }

        return factionList;
    }

    public boolean getIsSabotaging(){
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(HAA_SABOTAGE_MEMORY_KEY);
    }
}
