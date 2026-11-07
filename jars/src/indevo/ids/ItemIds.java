package indevo.ids;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class ItemIds {

    public static final String NO_ENTRY = "no_entry";

    public static final String PARTS = "IndEvo_parts";
    public static final String RARE_PARTS = "IndEvo_rare_parts";
    public static final String PET_FOOD = "IndEvo_pet_food";

    public static final String VPC = "IndEvo_vpc";

    //items
    //consumable
    public static final String CONSUMABLE_NANITES = "IndEvo_consumable_nanites";
    public static final String CONSUMABLE_SUPERCHARGER = "IndEvo_consumable_supercharger";
    public static final String CONSUMABLE_STABILIZER = "IndEvo_consumable_stabilizer";
    public static final String CONSUMABLE_LOCATOR = "IndEvo_consumable_locator";
    public static final String CONSUMABLE_SPIKE = "IndEvo_consumable_spike";
    public static final String CONSUMABLE_SCOOP = "IndEvo_consumable_scoop";
    public static final String CONSUMABLE_DRONES = "IndEvo_consumable_drones";
    public static final String CONSUMABLE_DECOY = "IndEvo_consumable_decoy";
    public static final String CONSUMABLE_SPOOFER = "IndEvo_consumable_spoofer";
    public static final String CONSUMABLE_RELAY = "IndEvo_consumable_relay";
    public static final String CONSUMABLE_MISSILE_EXPLOSIVE = "IndEvo_consumable_missile_explosive";
    public static final String CONSUMABLE_MISSILE_CONCUSSIVE = "IndEvo_consumable_missile_concussive";
    public static final String CONSUMABLE_MISSILE_SMOKE = "IndEvo_consumable_missile_smoke";
    public static final String CONSUMABLE_MISSILE_INTERCEPT = "IndEvo_consumable_missile_intercept";
    public static final String CONSUMABLE_MISSILE_REMOTE = "IndEvo_consumable_artillery_remote";
    public static final String CONSUMABLE_BEACON = "IndEvo_consumable_warning_beacon";
    public static final String CONSUMABLE_CATAPULT = "IndEvo_consumable_catapult";

    //industry
    public static final String LOG_CORE = "IndEvo_log_core";
    public static final String NEUAL_COMPOUNDS = "IndEvo_neurals";
    public static final String ANALYSER = "IndEvo_analyser";
    public static final String INTERFACES = "IndEvo_interface";
    public static final String TRANSMITTER = "IndEvo_transmitter";
    public static final String NANITES = "IndEvo_nanites";
    public static final String SALVAGE_DRONES = "IndEvo_drones";
    public static final String SIMULATOR = "IndEvo_simulator";

    public static final String ALPHA = "IndEvo_alpha_core";
    public static final String BETA = "IndEvo_beta_core";
    public static final String GAMMA = "IndEvo_gamma_core";
    public static final String RELIC_SPECIAL_ITEM = "IndEvo_relicSpecialItem";

    //Forge Template
    public static final String FORGETEMPLATE = "IndEvo_ForgeTemplate";
    public static final String BROKENFORGETEMPLATE = "IndEvo_DegForgeTemplate";
    public static final String EMPTYFORGETEMPLATE = "IndEvo_EmptForgeTemplate";

    //Ambassador
    public static final String AMBASSADOR = "IndEvo_amb";

    //pets
    public static final String PET_CHAMBER = "IndEvo_PetBox";

    public static SpecialItemData convertAICoreToSpecial(String aiCoreId) {
        if (aiCoreId == null) return null;

        switch (aiCoreId) {
            case Commodities.ALPHA_CORE:
                return new SpecialItemData(ALPHA, null);
            case Commodities.BETA_CORE:
                return new SpecialItemData(BETA, null);
            case Commodities.GAMMA_CORE:
                return new SpecialItemData(GAMMA, null);
        }

        return null;
    }

    //VPC output descriptions
    public static String getCommodityNameString(String commodityID) {
        EconomyAPI econ = Global.getSector().getEconomy();
        return econ.getCommoditySpec(commodityID) != null ? econ.getCommoditySpec(commodityID).getName() : NO_ENTRY;
    }
}
	





