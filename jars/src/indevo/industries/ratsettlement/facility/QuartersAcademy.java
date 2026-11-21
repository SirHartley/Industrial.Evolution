package indevo.industries.ratsettlement.facility;

import indevo.industries.academy.industry.Academy;

// academy instance held by Officer Quarters, never added to the market and never applied, so no officer quality bonus
public class QuartersAcademy extends Academy {

    public static final String NAME = "Officer Quarters";

    @Override
    protected boolean spawnsHireables() {
        return false;
    }

    @Override
    public boolean ownedByPlayer() {
        return true;
    }

    @Override
    public String getCurrentName() {
        return NAME;
    }
}
