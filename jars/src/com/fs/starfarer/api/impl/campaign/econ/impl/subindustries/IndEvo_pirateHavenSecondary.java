package com.fs.starfarer.api.impl.campaign.econ.impl.subindustries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;

public class IndEvo_pirateHavenSecondary extends BaseIndustry {

    public void apply() {
        super.apply(true);
    }

    public void unapply() {
        super.unapply();
    }

    public boolean isHidden() {
        return true;
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    public boolean showWhenUnavailable() {
        return false;
    }
}









