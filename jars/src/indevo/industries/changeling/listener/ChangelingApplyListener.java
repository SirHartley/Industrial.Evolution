package indevo.industries.changeling.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.IndustryApplyListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.config.Setting;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustryAPI;

// governments and specializations live on the market, applied as deltas around the host's own apply
public class ChangelingApplyListener implements IndustryApplyListener {

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(ChangelingApplyListener.class)) manager.addListener(new ChangelingApplyListener(), true);
    }

    @Override
    public void reportIndustryAboutToBeApplied(Industry ind) {
        MarketAPI market = ind.getMarket();
        GovernmentCondition cond = GovernmentCondition.get(market);
        if (cond == null || cond.getCurrent() == null) return;

        SubIndustryAPI gov = cond.getCurrent();
        Industry pop = GovernmentCondition.getPopulation(market);
        if (!(pop instanceof BaseIndustry)) return;

        gov.init(pop);
        if (ind == pop) gov.unapply();

        if (Setting.GOVERNMENT_LARP_MODE.enabled()) gov.unapplyFromOther(ind);
        else gov.preApplyToOther(ind);
    }

    @Override
    public void reportIndustryApplied(Industry ind) {
        MarketAPI market = ind.getMarket();
        if (market == null) return;

        Specialization spec = Specialization.getFor(ind);
        if (spec != null) spec.apply(ind);

        GovernmentCondition cond = GovernmentCondition.get(market);
        if (cond == null || cond.getCurrent() == null) return;

        SubIndustryAPI gov = cond.getCurrent();
        Industry pop = GovernmentCondition.getPopulation(market);
        if (!(pop instanceof BaseIndustry)) return;

        gov.init(pop);

        if (ind == pop) {
            cond.updateOverrides(pop);
            if (!Setting.GOVERNMENT_LARP_MODE.enabled()) gov.apply();
        }

        if (!Setting.GOVERNMENT_LARP_MODE.enabled()) gov.applyToOther(ind);
    }
}
