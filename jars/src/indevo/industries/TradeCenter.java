package indevo.industries;

// kept so saves from before the patch route load; behaviour is vanilla plus CommerceBalancePatch
public class TradeCenter extends com.fs.starfarer.api.impl.campaign.econ.impl.TradeCenter {

    // saved fields of the old copy, unused
    protected boolean balanceChange = false;
    public float improveBonus = 25f;
}
