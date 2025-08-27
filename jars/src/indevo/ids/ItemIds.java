package indevo.ids;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;
import indevo.utils.helper.StringHelper;

public class ItemIds {

    public static final String NO_ENTRY = "no_entry";

    public static final String PARTS = "IndEvo_parts";
    public static final String RARE_PARTS = "IndEvo_rare_parts";
    public static final String PET_FOOD = "IndEvo_pet_food";

    //VPC
    public static final String VPC_PARTS = "IndEvo_vpc_IndEvo_parts";
    public static final String VPC_SUPPLIES = "IndEvo_vpc_supplies";
    public static final String VPC_MARINES = "IndEvo_vpc_marines";
    public static final String VPC_HEAVY_MACHINERY = "IndEvo_vpc_heavy_machinery";
    public static final String VPC_DOMESTIC_GOODS = "IndEvo_vpc_domestic_goods";
    public static final String VPC_DRUGS = "IndEvo_vpc_drugs";
    public static final String VPC_HAND_WEAPONS = "IndEvo_vpc_hand_weapons";
    public static final String VPC_LUXURY_GOODS = "IndEvo_vpc_luxury_goods";
    public static final String VPC_MARINES_HAND_WEAPONS = "IndEvo_vpc_marines_hand_weapons";
    public static final String VPC_SUPPLIES_FUEL = "IndEvo_vpc_supply_fuel";

    public static final String TAG_VPC_COMMON = "IndEvo_VPC_common";
    public static final String TAG_VPC_UNCOMMON = "IndEvo_VPC_uncommon";
    public static final String TAG_VPC_RARE = "IndEvo_VPC_rare";

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

    public static String getVPCOutputString(String itemId) {
        Pair<String, String> p = getVPCCommodityIds(itemId);
        boolean dual = !p.two.equals(NO_ENTRY);

        EconomyAPI econ = Global.getSector().getEconomy();

        return dual ? econ.getCommoditySpec(p.one).getName().toLowerCase()
                + StringHelper.getString("IndEvo_misc", "andWithSpaces")
                + econ.getCommoditySpec(p.two).getName().toLowerCase()
                : getCommodityNameString(p.one).toLowerCase();
    }

    public static Pair<String, String> getVPCCommodityIds(String itemId) {
        String ident = "IndEvo_vpc_";
        Pair<String, String> p = new Pair<>();

        switch (itemId) {
            case VPC_MARINES_HAND_WEAPONS:
                p.one = Commodities.MARINES;
                p.two = Commodities.HAND_WEAPONS;
                return p;
            case VPC_SUPPLIES_FUEL:
                p.one = Commodities.SUPPLIES;
                p.two = Commodities.FUEL;
                return p;
            default:
                p.one = itemId.substring(ident.length());
                p.two = NO_ENTRY;
                return p;
        }
    }

    public static String getVPCDescriptionString(String itemId) {
        SpecialItemSpecAPI commSpec = Global.getSettings().getSpecialItemSpec(itemId);
        if (commSpec.getTags().contains(TAG_VPC_COMMON))
            return StringHelper.getString("IndEvo_VarInd", "vpc_common_desc");
        if (commSpec.getTags().contains(TAG_VPC_UNCOMMON))
            return StringHelper.getString("IndEvo_VarInd", "vpc_uncommon_desc");
        if (commSpec.getTags().contains(TAG_VPC_RARE))
            return StringHelper.getString("IndEvo_VarInd", "vpc_rare_desc");

        return NO_ENTRY;
    }

    public static String getItemNameString(String itemId) {
        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(itemId);
        return spec != null ? spec.getName() : NO_ENTRY;
    }
}
	





