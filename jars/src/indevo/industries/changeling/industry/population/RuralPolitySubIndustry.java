package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.impl.Farming;
import com.fs.starfarer.api.impl.campaign.econ.impl.LightIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class RuralPolitySubIndustry extends SubIndustry implements MarketImmigrationModifier {

    public static class RuralPolityTooltipAdder extends BaseIndustryOptionProvider {
        public static void register() {
            ListenerManagerAPI manager = Global.getSector().getListenerManager();
            if (!manager.hasListenerOfClass(RuralPolityTooltipAdder.class))
                manager.addListener(new RuralPolityTooltipAdder(), true);
        }

        @Override
        public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
            return !isSuitable(ind);
        }

        public boolean isSuitable(Industry ind) {
            Industry pop = ind.getMarket().getIndustry(Industries.POPULATION);
            boolean isRural = pop instanceof SwitchablePopulation && ((SwitchablePopulation) pop).getCurrent() instanceof RuralPolitySubIndustry;
            return !Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE) && isRural;
        }

        @Override
        public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
            if (isUnsuitable(ind, true)) return;
            float opad = 10f;

            boolean industrial = ind.getSpec().getTags().contains("industrial");
            boolean rural = ind.getSpec().getTags().contains("rural");

            tooltip.addSectionHeading("Governance Effects: Rural Polity", Alignment.MID, opad);

            if (industrial) {
                tooltip.addPara("Industrial industry: %s increased by %s", opad, Misc.getTextColor(), Misc.getNegativeHighlightColor(), "upkeep", StringHelper.getAbsPercentString(INDUSTRIAL_UPKEEP_INCREASE, false));
                tooltip.addPara("Industrial industry: %s decreased by %s", opad, Misc.getTextColor(), Misc.getNegativeHighlightColor(), "stability", STABILITY_DECREASE_PER_INDUSTRY + "");
            } else if (rural) {
                tooltip.addPara("Rural industry: %s decreased by %s", opad, Misc.getTextColor(), Misc.getPositiveHighlightColor(), "upkeep", StringHelper.getAbsPercentString(RURAL_UPKEEP_DECREASE, false));
                tooltip.addPara("Rural industry: %s increased by %s", opad, Misc.getTextColor(), Misc.getPositiveHighlightColor(), "stability", STABILITY_INCREASE_PER_RURAL + "");
                tooltip.addPara("Rural industry: %s increased by %s", opad, Misc.getTextColor(), Misc.getPositiveHighlightColor(), "population growth", IMMIGRATION_INCREASE_PER_RURAL + "");
            } else {
                tooltip.addPara("No effect on this industry.", opad);
            }

            if (ind.getSpecialItem() != null && !ind.getId().equals(Ids.EMBASSY)) tooltip.addPara(Global.getSettings().getSpecialItemSpec(ind.getSpecialItem().getId()).getName() + ": %s decreased by %s", opad, Misc.getTextColor(), Misc.getNegativeHighlightColor(), "stability", INDUSTRY_ITEM_STABILITY_DECREASE + "");
        }
    }

    public static class RuralPolityImageChanger extends BaseIndustryOptionProvider {
        public static final Object OPTION_IMAGE_CHANGE = new Object();

        public static void register() {
            ListenerManagerAPI manager = Global.getSector().getListenerManager();
            if (!manager.hasListenerOfClass(RuralPolityImageChanger.class))
                manager.addListener(new RuralPolityImageChanger(), true);
        }

        @Override
        public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
            return super.isUnsuitable(ind, allowUnderConstruction) || !isSuitable(ind);
        }

        public boolean isSuitable(Industry ind) {
            boolean isPop = ind.getId().equals(Industries.POPULATION);
            boolean isRural = ind instanceof SwitchablePopulation && ((SwitchablePopulation) ind).getCurrent() instanceof RuralPolitySubIndustry;
            boolean playerOwned = ind.getMarket().isPlayerOwned();

            return isPop && isRural && playerOwned;
        }

        public List<IndustryOptionData> getIndustryOptions(Industry ind) {
            if (isUnsuitable(ind, false)) return null;

            List<IndustryOptionProvider.IndustryOptionData> result = new ArrayList<IndustryOptionData>();

            IndustryOptionData opt = new IndustryOptionProvider.IndustryOptionData("Change Visuals", OPTION_IMAGE_CHANGE, ind, this);
            opt.color = new Color(150, 100, 255, 255);
            result.add(opt);

            return result;
        }

        @Override
        public void createTooltip(IndustryOptionProvider.IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
            if (opt.id == OPTION_IMAGE_CHANGE) {
                tooltip.addPara("Changes the visual image to an alternate version.", 0f);
            }
        }

        @Override
        public void optionSelected(IndustryOptionProvider.IndustryOptionData opt, DialogCreatorUI ui) {
            if (opt.id == OPTION_IMAGE_CHANGE) {
                ((RuralPolitySubIndustry) ((SwitchablePopulation) opt.ind).getCurrent()).nextImage();
            }
        }
    }

    /*

Rural Polity
•	Farming provides supplies
-   Light industry provides weapons
•	Farming output +
•	Pop growth +
•	Industrial Industries massively reduce growth, stab and get high upkeep
•   "rural" industries increase growth stab and get - upkeep
•	Less likely to be targeted by raids
-   bonus to luddic suspicion
•	Requires farmland lv. 3 or temperate to be available

     */
    public static final float BASE_PATHER_INTEREST_DECREASE = 10f;
    public static final int BASE_IMMIGRATION_INCREASE = 10;

    public static final float INDUSTRIAL_UPKEEP_INCREASE = 2f;
    public static final int STABILITY_DECREASE_PER_INDUSTRY = 3;
    public static final float RURAL_UPKEEP_DECREASE = 0.5f;
    public static final int STABILITY_INCREASE_PER_RURAL = 1;
    public static final int IMMIGRATION_INCREASE_PER_RURAL = 2;
    public static final int INDUSTRY_ITEM_STABILITY_DECREASE = 1;

    public static final float FARMING_ORGANICS_PER_FOOD = 0.5f;
    public static final float FARMING_DRUGS_PER_FOOD = 0.3f;
    public static final float LI_SUPPLIES_PER_LUX_GOODS = 1f;
    public static final float FARMING_BASE_BONUS = 1f;
    public static final float LI_WEAPONS_PER_LUX_GOODS = 0.5f;

    public RuralPolitySubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();

        market.addImmigrationModifier(this);

        int i = 0;
        for (Industry ind : market.getIndustries()) {
            if (ind.getSpecialItem() != null && Global.getSettings().getSpecialItemSpec(ind.getSpecialItem().getId()).hasTag("rural")) market.getStability().modifyFlat(getId() + "_" + ind.getId(), -INDUSTRY_ITEM_STABILITY_DECREASE, getName() + " - " + Global.getSettings().getSpecialItemSpec(ind.getSpecialItem().getId()).getName());
            if (ind.getAICoreId() != null)market.getStability().modifyFlat(getId() + "_" + ind.getId() + "_ai", -INDUSTRY_ITEM_STABILITY_DECREASE, getName() + " - " + Global.getSettings().getCommoditySpec(ind.getAICoreId()).getName());

            if (ind.getSpec().getTags().contains("industrial")) {
                ind.getUpkeep().modifyMult(getId(), INDUSTRIAL_UPKEEP_INCREASE, getName());
                market.getStability().modifyFlat(getId() + "_" + i, -STABILITY_DECREASE_PER_INDUSTRY, getName() + " - " + ind.getNameForModifier());
            } else if (ind.getSpec().getTags().contains("rural")) {
                ind.getUpkeep().modifyMult(getId(), RURAL_UPKEEP_DECREASE, getName());
                market.getStability().modifyFlat(getId() + "_" + i, STABILITY_INCREASE_PER_RURAL, getName() + " - " + ind.getNameForModifier());
            }

            if (ind instanceof Farming) {
                ind.getSupplyBonusFromOther().modifyFlat(getId(), FARMING_BASE_BONUS, getName());
                ind.supply(getId(), Commodities.ORGANICS, (int) Math.ceil(ind.getSupply(Commodities.FOOD).getQuantity().getModifiedValue() * FARMING_ORGANICS_PER_FOOD), getName());
                if (market.isFreePort()) ind.supply(getId(), Commodities.DRUGS, (int) Math.ceil(ind.getSupply(Commodities.FOOD).getQuantity().getModifiedValue() * FARMING_DRUGS_PER_FOOD), getName());
            }

            if (ind instanceof LightIndustry) {
                ind.supply(getId(), Commodities.HAND_WEAPONS, (int) Math.ceil(ind.getSupply(Commodities.LUXURY_GOODS).getQuantity().getModifiedValue() * LI_WEAPONS_PER_LUX_GOODS), getName());
                ind.supply(getId(), Commodities.SUPPLIES, (int) Math.ceil(ind.getSupply(Commodities.LUXURY_GOODS).getQuantity().getModifiedValue() * LI_SUPPLIES_PER_LUX_GOODS), getName());
            }

            i++;
        }
    }

    public static void fixClosest(){
        for (PlanetAPI p : Global.getSector().getPlayerFleet().getContainingLocation().getPlanets()){
            if (Misc.getDistance(p.getLocation(), Global.getSector().getPlayerFleet().getLocation()) < 100f){
                List<String> sm = new ArrayList<>();
                for (Map.Entry<String, com.fs.starfarer.api.combat.MutableStat.StatMod> s : p.getMarket().getStability().getFlatMods().entrySet()) {
                    indevo.utils.ModPlugin.log(s.getKey() + " " + s.getValue().getDesc() + " src: " + s.getValue().getSource());
                    sm.add(s.getValue().getSource());
                }

                for (String s : sm){
                    p.getMarket().getStability().unmodify(s);
                }
            }
        }

        //indevo.industries.changeling.industry.population.RuralPolitySubIndustry.fixClosest();
    }

    @Override
    public void unapply() {
        super.unapply();

        market.removeImmigrationModifier(this);

        for (Map.Entry<String, MutableStat.StatMod> s : new HashSet<>(market.getStability().getFlatMods().entrySet()))  if (s.getValue().getSource().startsWith(getId())) market.getStability().unmodify(s.getValue().getSource()); //fuck me

        for (Industry ind : market.getIndustries()) {
            market.getStability().unmodify(getId() + "_" + ind.getId());
            market.getStability().unmodify(getId() + "_" + ind.getId() + "_ai");

            if (ind.getSpec().getTags().contains("industrial")) {
                ind.getUpkeep().unmodify(getId());
            } else if (ind.getSpec().getTags().contains("rural")) {
                ind.getUpkeep().unmodify(getId());
            }

            if (ind instanceof Farming) {
                ind.getSupplyBonusFromOther().unmodify(getId());
                ind.supply(getId(), Commodities.SUPPLIES, 0, getName());
            }

            if (ind instanceof LightIndustry) {
                ind.supply(getId(), Commodities.HAND_WEAPONS, 0, getName());
            }
        }

        for (int i = 0; i < 50; i++) {
            market.getStability().unmodify(getId() + "_" + i);
        }
    }

    @Override
    public boolean isAvailableToBuild() {
        Set<String> waterPlanetIDs = MiscIE.getCSVSetFromMemory(Ids.RURAL_LIST);

        boolean hasFarming = market.hasCondition(Conditions.FARMLAND_RICH) || market.hasCondition(Conditions.FARMLAND_BOUNTIFUL);
        boolean isPlanet = market.getPrimaryEntity() instanceof PlanetAPI;
        boolean isWater = isPlanet && waterPlanetIDs.contains(market.getPlanetEntity().getTypeId());

        return super.isAvailableToBuild() && hasFarming && isPlanet && !isWater;
    }

    @Override
    public String getUnavailableReason() {
        Set<String> waterPlanetIDs = MiscIE.getCSVSetFromMemory(Ids.RURAL_LIST);

        boolean hasFarming = market.hasCondition(Conditions.FARMLAND_RICH) || market.hasCondition(Conditions.FARMLAND_BOUNTIFUL);
        boolean isPlanet = market.getPrimaryEntity() instanceof PlanetAPI;
        boolean isWater = isPlanet && waterPlanetIDs.contains(market.getPlanetEntity().getTypeId());

        if (!isPlanet) return "Unavailable on space stations";
        if (!hasFarming) return "Requires Rich Farmland or better";
        if (isWater) return "Unavailable on water planets";

        return super.getUnavailableReason();
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyFlat(getId(), BASE_IMMIGRATION_INCREASE, getName() + " - Base");

        int i = 0;
        for (Industry ind : market.getIndustries()) {
            if (ind.getSpec().getTags().contains("rural")) {
                incoming.getWeight().modifyFlat(getId() + " " + i, IMMIGRATION_INCREASE_PER_RURAL, getName() + " - " + ind.getNameForModifier());
            }

            i++;
        }
    }

    public void nextImage() {
        int max = 5;
        String png = ".png";
        String currentName = getImageName(market);

        int endIndex = currentName.length() - png.length();
        int beginIndex = endIndex - 1;
        int current = Integer.parseInt(currentName.substring(beginIndex, endIndex));

        int next;
        if (current == max) next = 1;
        else next = current + 1;

        imageName = new StringBuilder(currentName).delete(beginIndex, endIndex).insert(beginIndex, next).toString();
        try {
            Global.getSettings().loadTexture(imageName);
        } catch (IOException e) {
            Global.getLogger(RuralPolitySubIndustry.class).warn("Tried to load a faulty texture path at " + imageName);
        }
    }

    @Override
    public float getPatherInterest(Industry industry) {
        return -BASE_PATHER_INTEREST_DECREASE;
    }
}
