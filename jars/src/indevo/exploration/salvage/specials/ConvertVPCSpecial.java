package indevo.exploration.salvage.specials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.StringHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.util.*;

import static indevo.ids.ItemIds.NO_ENTRY;
import static indevo.industries.assembler.industry.VariableAssembler.DUAL_OUTPUT_REDUCTION_MULT;

public class ConvertVPCSpecial extends BaseSalvageSpecial {
    //convert a VPC into ressources

    public static final Logger log = Global.getLogger(ConvertVPCSpecial.class);

    public static final String SELECT = "select";
    public static final String NOT_NOW = "not_now";
    public static final String VPC_IDENT = "option_item_";

    public static class ConvertVPCSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {

        public final float qualityModifier;

        public ConvertVPCSpecialData(float efficiencyModifier) {
            this.qualityModifier = efficiencyModifier;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new ConvertVPCSpecial();
        }
    }

    private ConvertVPCSpecial.ConvertVPCSpecialData data;

    public ConvertVPCSpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);
        data = (ConvertVPCSpecial.ConvertVPCSpecialData) specialData;

        options.clearOptions();

        String shape = "surprisingly good";
        if (data.qualityModifier < 0.75f) shape = "passable";
        if (data.qualityModifier < 0.4f) shape = "rather bad";

        String s = entity instanceof PlanetAPI ? "facility" : "$shortName";

        addText("Deep within the bowels of the " + s + " your crew finds an experimental Variable Commodity Forge.\n\n" +
                "It seems to be in " + shape + " shape, and is missing a VPC.");

        if (!playercargoHasVPC()) {
            addText("You do not have a VPC to slot into the Forge.\nConsider returning when you have found one.");
            text.highlightLastInLastPara("You do not have a VPC to slot into the Forge.", Misc.getNegativeHighlightColor());

            setDone(true);
            setEndWithContinue(true);
            setShowAgain(true);
        } else {
            options.addOption("Slot in a VPC", SELECT);
            options.addOption("Not now", NOT_NOW);
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (SELECT.equals(optionData)) {
            options.clearOptions();

            List<String> vpcList = getVPCFromPlayerCargoList();

            for (String id : vpcList) {
                String ident = VPC_IDENT + id;
                options.addOption(ItemIds.getItemNameString(id), ident);
                options.addOptionConfirmation(ident,
                        "Are you sure? This will convert the VPC into " + ItemIds.getVPCOutputString(id) + ".",
                        "Confirm",
                        "Return");
            }

            options.addOption("Not now", NOT_NOW);

        } else if (optionData.toString().contains(VPC_IDENT)) {
            String prct = StringHelper.getAbsPercentString(data.qualityModifier, false);
            String id = optionData.toString().substring(VPC_IDENT.length());

            addText("The Forge accepts the chip without incident, and starts churning out " + ItemIds.getVPCOutputString(id) + ".\n" +
                    "After producing " + prct + " of the predicted amount, the entire assembly grinds to a sudden halt.\n\n" +
                    "Judging by the ridiculous amounts of smoke and the concerning noises, it is probably done for good.");

            text.highlightLastInLastPara(prct, Misc.getHighlightColor());

            CargoAPI cargo = playerFleet.getCargo();
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(id, null), 1);

            for (Map.Entry<String, Integer> e : getDepositList(id).entrySet()) {
                cargo.addCommodity(e.getKey(), e.getValue());
                AddRemoveCommodity.addCommodityGainText(e.getKey(), e.getValue(), text);
            }

            setShowAgain(false);
            setDone(true);

        } else if (NOT_NOW.equals(optionData)) {
            addText("You leave. " +
                    "The Commodity Forge remains behind, to be used or disassembled for parts.");

            setShowAgain(true);
            setDone(true);
        }
    }

    public Map<String, Integer> getDepositList(String id) {
        Map<String, Integer> map = new LinkedHashMap<>();
        Pair<String, String> p = ItemIds.getVPCCommodityIds(id);
        boolean dual = !p.two.equals(NO_ENTRY);

        if (p.one != null) map.put(p.one, getDepositAmount(p.one, dual));
        if (dual) map.put(p.two, getDepositAmount(p.two, true));

        return map;
    }

    public int getDepositAmount(String commodityId, boolean dual) {
        int amt;
        int sizeMult = 7;
        float logIncrease;
        float dualReduction = dual ? DUAL_OUTPUT_REDUCTION_MULT : 1f;

        try {
            amt = Global.getSettings().getJSONObject(Ids.COMFORGE).getInt(commodityId);
            logIncrease = Global.getSettings().getJSONObject(Ids.COMFORGE).getInt("logIncrease");
        } catch (JSONException e) {
            log.error(e.toString());
            amt = 0;
            logIncrease = 0;
        }

        int total = Math.round((Math.round(((logIncrease * Math.log(sizeMult)) + amt) / 10) * 10) * dualReduction * data.qualityModifier);
        return Math.max(0, total);
    }

    private boolean playercargoHasVPC() {
        CargoAPI cargo = playerFleet.getCargo();
        Set<SpecialItemData> vpcIdSet = IndustryHelper.getVPCItemSet();

        for (SpecialItemData data : vpcIdSet) {
            if (cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, data) > 0) return true;
        }

        return false;
    }

    private List<String> getVPCFromPlayerCargoList() {
        CargoAPI cargo = playerFleet.getCargo();
        List<String> l = new ArrayList<>();

        Set<SpecialItemData> vpcIdSet = IndustryHelper.getVPCItemSet();

        for (SpecialItemData data : vpcIdSet) {
            if (cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, data) > 0) l.add(data.getId());
        }

        return l;
    }
}
