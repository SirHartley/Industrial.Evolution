package indevo.exploration.salvage.specials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.EmergencyBurnAbility;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class IndEvo_ExtraFuelSpecial extends BaseSalvageSpecial {

    public static final String OPEN = "open";
    public static final String NOT_NOW = "not_now";

    public enum ExtraFuelSpecialType {
        SUCCESS,
        EXPLOSION,
    }

    public static class ExtraFuelSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public final IndEvo_ExtraFuelSpecial.ExtraFuelSpecialType type;
        public final int min;
        public final int max;

        public ExtraFuelSpecialData(IndEvo_ExtraFuelSpecial.ExtraFuelSpecialType type, int min, int max) {
            this.type = type;
            this.min = min;
            this.max = max;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new IndEvo_ExtraFuelSpecial();
        }
    }

    private IndEvo_ExtraFuelSpecial.ExtraFuelSpecialData data;

    private int quantity = 1;

    public IndEvo_ExtraFuelSpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);

        data = (IndEvo_ExtraFuelSpecial.ExtraFuelSpecialData) specialData;

        quantity = data.min + random.nextInt(data.max - data.min + 1);

        if (data.type == ExtraFuelSpecialType.SUCCESS) {
            if (quantity < 1) quantity = 1;
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        int fuelCap = (int) (player.getCargo().getMaxFuel() - player.getCargo().getFuel());
        if (fuelCap < 0) fuelCap = 0;

        IndEvo_ExtraFuelSpecial.ExtraFuelSpecialType type = data.type;

        if (type == ExtraFuelSpecialType.SUCCESS) quantity = Math.min(fuelCap, quantity);

        switch (type) {
            case SUCCESS:
                initFuel();
                break;
            case EXPLOSION:
                initExplosion();
                break;
        }
    }


    protected void initFuel() {
        if (quantity <= 0) {
            initNothing();
        } else {
            addText("While making a preliminary assessment, your salvage crews " +
                    "reports the presence of large and seemingly intact fuel tanks.");

            options.clearOptions();
            options.addOption("Attempt to tap the tanks", OPEN);
            options.addOption("Not now", NOT_NOW);
        }
    }

    protected void initExplosion() {
        addText("While making a preliminary assessment, your salvage crews " +
                "reports the presence of large and seemingly intact fuel tanks.");

        options.clearOptions();
        options.addOption("Attempt to tap the tanks", OPEN);
        options.addOption("Not now", NOT_NOW);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPEN.equals(optionData)) {

            switch (data.type) {
                case SUCCESS:
                    addText("The stores can be tapped without problems, and your fleet resupplies as much as it can." +
                            "\nIt is a good thing antimatter does not go bad with age.\n\n");

                    playerFleet.getCargo().addFuel(quantity);
                    AddRemoveCommodity.addCommodityGainText(Commodities.FUEL, quantity, text);

                    break;
                case EXPLOSION:

                    WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>();
                    WeightedRandomPicker<FleetMemberAPI> preferNotTo = new WeightedRandomPicker<>();
                    float ebCostThresholdMult = 4f;

                    for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
                        float w = 1f;
                        if (member.isMothballed()) w *= 0.1f;

                        float ebCost = EmergencyBurnAbility.getCRCost(member, playerFleet);
                        if (ebCost * ebCostThresholdMult > member.getRepairTracker().getCR()) {
                            preferNotTo.add(member, w);
                        } else {
                            if (member.isFrigate()) w *= 0.3f;
                            picker.add(member, w);
                        }
                    }
                    if (picker.isEmpty()) {
                        picker.addAll(preferNotTo);
                    }

                    FleetMemberAPI member = picker.pick();

                    if (member == null) {
                        addText("It only took one faulty valve for " +
                                "the tanks to remove themselves in a spectacular explosion, taking a good chunk of the surroundings with it." +
                                "\nLuckily, no ship was caught in the blast.");
                        break;
                    }

                    addText("It only took one faulty valve for " +
                            "the tanks to remove themselves in a spectacular explosion, taking a good chunk of the surroundings with it." +
                            "\nUnfortunately, the " + member.getShipName() + " was caught in the explosion and suffered damage.");
                    text.highlightLastInLastPara(member.getShipName(), Misc.getNegativeHighlightColor());
                    Global.getSoundPlayer().playUISound("ui_rep_drop", 1, 1);

                    applyExplosionDamage(member);
                    break;
            }


            setDone(true);
            setShowAgain(false);
        } else if (NOT_NOW.equals(optionData)) {
            setDone(true);
            setEndWithContinue(false);
            setShowAgain(true);
        }
    }

    public static final float EXPLOSION_MIN_STRIKE_DAMAGE = 0.25f;
    public static final float EXPLOSION_MAX_STRIKE_DAMAGE = 0.95f;

    protected void applyExplosionDamage(FleetMemberAPI member) {
        float ebCostThresholdMult = 4f;
        float totalValue = member.getStats().getSuppliesToRecover().getModifiedValue();
        if (totalValue <= 0) return;

        float strikeValue = totalValue * (0.5f + (float) Math.random() * 0.5f); //applies between 50 and 100% hull damage

        float crPerDep = member.getDeployCost();
        float suppliesPerDep = member.getStats().getSuppliesToRecover().getModifiedValue();
        if (suppliesPerDep <= 0 || crPerDep <= 0) return;

        float strikeDamage = crPerDep * strikeValue / suppliesPerDep;
        if (strikeDamage < EXPLOSION_MIN_STRIKE_DAMAGE) strikeDamage = EXPLOSION_MIN_STRIKE_DAMAGE;
        if (strikeDamage > EXPLOSION_MAX_STRIKE_DAMAGE) strikeDamage = EXPLOSION_MAX_STRIKE_DAMAGE;

        float currCR = member.getRepairTracker().getBaseCR();
        float crDamage = Math.min(currCR, strikeDamage);

        float ebCost = EmergencyBurnAbility.getCRCost(member, playerFleet);
        if (currCR >= ebCost * ebCostThresholdMult) {
            crDamage = Math.min(currCR - ebCost * 1.5f, crDamage);
        }

        if (crDamage > 0) {
            member.getRepairTracker().applyCREvent(-crDamage, "indEvo_explosion", "Fuel Tank Explosion");
        }

        float hitStrength = member.getStats().getArmorBonus().computeEffective(member.getHullSpec().getArmorRating());
        hitStrength *= strikeDamage / crPerDep;
        member.getStatus().applyDamage(hitStrength);
        if (member.getStatus().getHullFraction() < 0.01f) {
            member.getStatus().setHullFraction(0.01f);
        }
    }
}
