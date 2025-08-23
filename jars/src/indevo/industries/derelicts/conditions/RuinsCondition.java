package indevo.industries.derelicts.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.utils.ModPlugin;
import org.apache.log4j.Logger;

import static indevo.industries.derelicts.industry.Ruins.INDUSTRY_ID_MEMORY_KEY;
import static indevo.industries.derelicts.utils.RuinsManager.setUpgradeSpec;

public class RuinsCondition extends BaseHazardCondition {

    public static final Logger log = Global.getLogger(RuinsCondition.class);

    @Deprecated
    protected final String pickerSeed = "$IndEvo_ruinsPickerSeed";

    public void apply(String id) {
        super.apply(id);

        if (market.getMemoryWithoutUpdate() != null //some weird mod conflict causing markets without memory to appear?
                && !market.getMemoryWithoutUpdate().getBoolean("$isPlanetConditionMarketOnly")
                && !market.isPlanetConditionMarketOnly()
                && !isRuinsConditionSet()
                && market.getFaction() != null
                && !market.getFactionId().equals("neutral")) {

            if (!market.getMemoryWithoutUpdate().contains(INDUSTRY_ID_MEMORY_KEY))
                setUpgradeSpec(market.getPlanetEntity());
            addRuinsIfNeeded();
        }
    }

    public void unapply(String id) {
        super.unapply(id);
    }

    @Override
    public void advance(float amount) {
        removeConditionIfRuinsNotPresent();
    }

    private boolean isRuinsConditionSet() {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String ruinsConditionSet = "$IndEvo_ruinsPlaced_" + market.getId();

        return memory.getBoolean(ruinsConditionSet);
    }

    public void addRuinsIfNeeded() {
        if (market == null || isRuinsConditionSet()) return;

        log.info("Adding ruins to " + market.getName());
        market.addIndustry(Ids.RUINS);
        setRuinsCondition();
    }

    private void setRuinsCondition() {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String ruinsConditionSet = "$IndEvo_ruinsPlaced_" + market.getId();

        memory.set(ruinsConditionSet, true);
    }

    public static void setCurrentInteractionTargetIndustryType(String industryType){
        try {
            Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget().getMarket().getMemoryWithoutUpdate().set(INDUSTRY_ID_MEMORY_KEY, industryType);
        } catch (NullPointerException e){
            ModPlugin.log("Nullpointer when trying to override industry type");
        }

        //runcode indevo.industries.derelicts.conditions.RuinsCondition.setCurrentInteractionTargetIndustryType("IndEvo_HullForge");
    }

    private void removeConditionIfRuinsNotPresent() {
        if (!market.getMemoryWithoutUpdate().getBoolean("$isPlanetConditionMarketOnly")
                && isRuinsConditionSet()
                && !market.hasIndustry(Ids.RUINS)
                && !market.getFactionId().equals("neutral")
                && !market.isPlanetConditionMarketOnly()) {

            log.info("Removing RuinsCondition on " + market.getName());
            market.removeSpecificCondition(condition.getIdForPluginModifications());
        }
    }

    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        MemoryAPI memory = market.getMemoryWithoutUpdate();

        tooltip.addPara("Restoring them to working order could give %s unlike any others.",
                10f,
                Misc.getTextColor(),
                Misc.getHighlightColor(),
                new String[]{"access to exotic technologies"});

        if (memory.contains(INDUSTRY_ID_MEMORY_KEY) && market.getSurveyLevel().equals(MarketAPI.SurveyLevel.FULL)) {
            String s = "Scans of the site strangely report nothing of note.";

            switch (memory.getString(INDUSTRY_ID_MEMORY_KEY)) {
                case Ids.LAB:
                    s = "Initial scans show a massive, largely underground complex filled with arcane energy signatures.";
                    break;
                case Ids.DECONSTRUCTOR:
                    s = "The first scan results misreported the presence of an enormous weapons array - but it seems to be pointed at itself for some reason.";
                    break;
                case Ids.HULLFORGE:
                    s = "Deep scans report a strange similarity to energy patterns usually emitted by a dormant nanoforge.";
                    break;
                case Ids.RIFTGEN:
                    s = "All scans of the site come back with nonsensical results - some even entirely misreport the location of the ruins.";
                    break;
            }

            tooltip.addPara(s,
                    10f,
                    Misc.getTextColor());
        }
    }
}