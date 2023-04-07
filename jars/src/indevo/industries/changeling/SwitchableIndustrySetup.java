package indevo.industries.changeling;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.loading.X;
import indevo.ids.Ids;

import static indevo.ids.Ids.SWITCHABLE_MINING;

public class SwitchableIndustrySetup {
    public static void updateIndustrySpecs(){
        Global.getSettings().getIndustrySpec(Industries.REFINING).setUpgrade(Ids.SWITCHABLE_REFINING);
        Global.getSettings().getIndustrySpec(Industries.MINING).setUpgrade(Ids.SWITCHABLE_MINING);
    }

    public static void modifyIndustryItem(){
        // TODO: 07/04/2023 dirty hack adjust for 0.96
        X spec = (X) Global.getSettings().getSpecialItemSpec(Items.PLASMA_DYNAMO);
        spec.setParams(spec.getParams() + ", " + SWITCHABLE_MINING);

        spec = (X) Global.getSettings().getSpecialItemSpec(Items.MANTLE_BORE);
        spec.setParams(spec.getParams() + ", " + SWITCHABLE_MINING);
    }
}
